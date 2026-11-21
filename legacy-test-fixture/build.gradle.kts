plugins { alias(libs.plugins.android.application) }

android {
    namespace = "com.linzin.legacy.fixture"
    enableKotlin = false
    compileSdk { version = release(37) }
    defaultConfig {
        applicationId = "com.linzin.legacy.fixture"
        minSdk = 14
        targetSdk = 22
        versionCode = 1
        versionName = "test-only"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}
