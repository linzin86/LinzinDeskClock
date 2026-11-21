import java.security.MessageDigest

plugins { alias(libs.plugins.android.application) }

val legacyApplicationId = providers.gradleProperty("legacyApplicationId")
if (legacyApplicationId.isPresent) {
    layout.buildDirectory.set(layout.projectDirectory.dir("build/alternate-id"))
}

android {
    namespace = "com.linzin.legacy"
    enableKotlin = false
    compileSdk { version = release(37) }
    defaultConfig {
        applicationId = legacyApplicationId.orElse("com.linzin.linzindeskclock.legacy.spike").get()
        minSdk = 14
        targetSdk = 37
        versionCode = 2
        versionName = "0.2-spike"
        testInstrumentationRunner = "com.linzin.legacy.LegacyInstrumentation"
        vectorDrawables {
            useSupportLibrary = false
            generatedDensities("mdpi", "hdpi", "xhdpi")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

abstract class ClockArtworkTask : DefaultTask() {
    @get:InputDirectory abstract val artwork: DirectoryProperty
    @get:OutputDirectory abstract val output: DirectoryProperty
    @get:javax.inject.Inject abstract val files: FileSystemOperations
    @TaskAction fun copyArtwork() {
        files.sync {
            from(artwork) {
                include("tk_*.xml", "ttn_*.xml", "rafm_*.xml", "abw_*.xml", "oet_*.xml", "zmg_*.xml", "pp_*.xml", "fft_*.xml")
            }
            into(output.dir("drawable"))
        }
    }
}
val clockArtwork = tasks.register<ClockArtworkTask>("copyClockArtwork") {
    artwork.set(rootProject.layout.projectDirectory.dir("app/src/main/res/drawable"))
    output.set(layout.buildDirectory.dir("generated/clock-artwork/res"))
}
androidComponents.onVariants { variant ->
    variant.sources.res?.addGeneratedSourceDirectory(clockArtwork, ClockArtworkTask::output)
}

dependencies {
    implementation(project(":clock-core"))
    implementation("org.bouncycastle:bctls-jdk15to18:1.87")
    implementation("org.bouncycastle:bcprov-jdk15to18:1.87")
    implementation("org.bouncycastle:bcutil-jdk15to18:1.87")
    testImplementation(libs.junit)
}

dependencyLocking { lockAllConfigurations() }

val pinnedChecksums = layout.projectDirectory.file("dependency-checksums.sha256")
val pinnedRoots = layout.projectDirectory.file("src/main/assets/mozilla-roots.bks")
val verifyLegacyInputs = tasks.register("verifyLegacyInputs") {
    val checksums = pinnedChecksums.asFile
    inputs.file(pinnedChecksums)
    inputs.file(pinnedRoots)
    doLast {
        val expected = checksums.readLines().filter { it.isNotBlank() }.associate { line ->
            val parts = line.trim().split(Regex("\\s+"))
            require(parts.size == 2) { "Invalid Legacy checksum entry" }
            parts[1] to parts[0]
        }
        val files = inputs.files.files.filter { it.name != "dependency-checksums.sha256" }
        require(files.map { it.name }.toSet() == expected.keys) { "Unexpected Legacy dependency inputs" }
        for (file in files) {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { stream ->
                val buffer = ByteArray(8192)
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            require(actual == expected[file.name]) { "Legacy checksum mismatch: ${file.name}" }
        }
        println("Verified ${files.size} pinned Legacy inputs")
    }
}
configurations.matching { it.name == "releaseRuntimeClasspath" }.all {
    val pinnedFiles = filter { (it.name.startsWith("bc") || it.name.startsWith("threetenbp-")) && it.extension == "jar" }
    verifyLegacyInputs.configure { inputs.files(pinnedFiles) }
}
tasks.named("preBuild") { dependsOn(verifyLegacyInputs) }
