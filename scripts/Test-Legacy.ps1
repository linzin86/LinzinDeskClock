param(
    [Parameter(Mandatory=$true)][string]$Serial,
    [Parameter(Mandatory=$true)][string]$Apk,
    [string]$Adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
)
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
$testApk = Join-Path $repo 'app-legacy/build/outputs/apk/androidTest/debug/app-legacy-debug-androidTest.apk'
$fixtureApk = Join-Path $repo 'legacy-test-fixture/build/outputs/apk/debug/legacy-test-fixture-debug.apk'
$package = 'com.linzin.linzindeskclock.legacy.spike'

function Invoke-Adb([string[]]$Arguments) {
    $output = & $Adb -s $Serial @Arguments 2>&1
    $exitCode = $LASTEXITCODE
    $output | ForEach-Object { Write-Host $_ }
    if ($exitCode -ne 0) { throw "ADB failed ($exitCode)" }
    return ($output -join "`n")
}
function Install-Apk([string]$Path) {
    $output = Invoke-Adb @('install', '-r', '-t', (Resolve-Path -LiteralPath $Path).Path)
    if ($output -notmatch '(?m)^Success\s*$') { throw "Installation failed: $Path" }
}
function Invoke-Case([string]$Mode) {
    $output = Invoke-Adb @('shell', 'am', 'instrument', '-w', '-e', 'mode', $Mode,
        "$package.test/com.linzin.legacy.LegacyInstrumentation")
    if ($output -notmatch 'OK:' -or $output -match 'FAIL:|INSTRUMENTATION_FAILED|Process crashed') {
        throw "Legacy case failed: $Mode"
    }
}
function Invoke-Fixture([string]$Mode) {
    $output = Invoke-Adb @('shell', 'am', 'instrument', '-w', '-e', 'mode', $Mode,
        'com.linzin.legacy.fixture/com.linzin.legacy.fixture.CalendarFixture')
    if ($output -notmatch 'OK:' -or $output -match 'FAIL:') { throw "Calendar fixture failed: $Mode" }
}

foreach ($path in @($Apk, $testApk, $fixtureApk, $Adb)) {
    if (!(Test-Path -LiteralPath $path)) { throw "Required input missing: $path" }
}
$api = (Invoke-Adb @('shell', 'getprop', 'ro.build.version.sdk')).Trim()
if ($api -notin @('14', '19')) { throw "This matrix requires API14 or API19; got $api" }
Write-Host "Candidate SHA256: $((Get-FileHash -LiteralPath $Apk -Algorithm SHA256).Hash)"
Write-Host "Test SHA256: $((Get-FileHash -LiteralPath $testApk -Algorithm SHA256).Hash)"
Install-Apk $Apk
Install-Apk $testApk
Install-Apk $fixtureApk
try {
    Invoke-Case 'clock'
    $null = Invoke-Adb @('shell', 'am', 'force-stop', $package)
    Invoke-Case 'saved'
    Invoke-Fixture 'create'
    Invoke-Case 'calendar'
    Invoke-Case 'network'
    Invoke-Case 'tls'
} finally { Invoke-Fixture 'cleanup' }
Write-Host "PASS: Legacy external matrix on $Serial (API$api)"
