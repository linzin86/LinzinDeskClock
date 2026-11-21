# Legacy implementation

This isolated implementation targets **API 14** and **API 19** with one minSdk 14
APK. Clock, layout and native settings integration follow the feasibility slice;
the remaining features below are still in progress. This is not an API14-FULL
or API19-CONSERVATIVE product release.
The modern `app` keeps minSdk 21. The two renderers actually share Java 8
`clock-core` hand-angle calculations, persisted layout names and month-grid rules.

API19 debug fixtures at 2026-02-28 13:05:09 UTC (uniform cells, not device layouts):

![Eight styles in 24-hour format](../docs/images/legacy-eight-styles-24h.png)
![Eight styles in 12-hour format](../docs/images/legacy-eight-styles-12h.png)

Native settings on API19 (480x800):

![Native settings on API19](../docs/images/legacy-native-settings-api19.png)

Original feasibility release screenshots (before the eight-style integration):

![API14 at 320x480](../docs/images/legacy-api14-spike.png)
![API19 at 480x800](../docs/images/legacy-api19-spike.png)

The experimental application ID is `com.linzin.linzindeskclock.legacy.spike`.
It does not replace the original app. Release probes use an explicitly local
test signature, not an upstream or production signing identity. Version 1 is
an experimental identity; final signing and upgrade policy remain release work.

## Scope

| Capability | This slice | Full implementation still required |
| --- | --- | --- |
| Clock | Original three digital, three analog and two flip styles; animated seconds/flip; wall time, date; fixed-time fixtures | Final hardware acceptance |
| Settings | Persistent time/style/seconds, five language choices, units, window brightness, 180-degree content rotation, timeout and first-run/reopenable guide | Final integration and hardware acceptance |
| Layout | Single and mirrored 70/30 dual panes, sidebar pages, touch lock, tap/double-tap/two-finger/vertical swipe controls | Remaining weather/update content |
| Calendar | Month grid and three upcoming entries in seven days; explicit off/permission/empty/error states; read-only visible-page worker; runtime permission flow on API23+ | Final data integration |
| Weather | Opt-in manual HTTPS current temperature from saved valid coordinates; cached Celsius/Fahrenheit conversion, data time and stale/error labeling | Live location, geocoding integration, four forecasts, complete persistence |
| Other | Foreground animation/receiver cleanup, battery level/charging status and explicit capability notices | Update UI and full lifecycle matrix |
| Quality | API14/19 emulator, R8/v1, network trust boundary probes | Old ARM hardware budgets, upgrades, soak and final release gates |

RuntimeShader and fold/cutout capabilities are not provided by this slice.
Do not treat a missing general feature as an accepted platform downgrade.
Fresh installs start with a single clock and recent events/weather off. Enabling
either data feature in settings exposes a dual pane. Calendar access requires
installation permission on API14/19 and user approval on API23+. Hidden pages
cancel their work and reject late results. A cancelled
weather request returns to its cached or idle state.

Language changes recreate Activity resources without changing the process or
application locale. Brightness applies only to the clock window and is released
on pause. A timeout shorter than the system timeout uses a black wakeable cover;
longer choices hand control back to the system for real display sleep. The first
wake touch is consumed. Never-timeout mode restores the display flag on return
from Home. The 180-degree option rotates content while retaining the clock's
display orientation, including reverse portrait and landscape.

Clock artwork comes directly from the modern module's 96 vector sources.
The Gradle Variant API registers the generated resources; Android's build-time
rasterizer supplies mdpi/hdpi/xhdpi PNG fallbacks below API21. Only the selected
style's drawables and tint filters are cached. No runtime vector parser is added.
Two-finger taps change the current family's style; double taps toggle its seconds;
vertical swipes change clock families or non-scrolling sidebar pages. Slow drags
work without fling velocity. The visible page button also supports long content.
Controls hide after five seconds; a single tap exposes them, including unlock.
A standard seconds button also supports keyboard and screen-reader operation;
the clock's accessible readout includes the date whenever the date is visible.

## Build and test

Use the repository's configured JDK and Android SDK. Build and unit tests must
run separately on Windows:

```powershell
.\gradlew.bat :clock-core:test :app-legacy:testDebugUnitTest :app:testDebugUnitTest
.\gradlew.bat :app-legacy:assembleDebug :app-legacy:assembleDebugAndroidTest :legacy-test-fixture:assembleDebug :app-legacy:assembleRelease :app-legacy:lintDebug
```

Sign `app-legacy/build/outputs/apk/release/app-legacy-release-unsigned.apk` with
your local test key using `apksigner sign`, then run
`apksigner verify --min-sdk-version 14 --verbose --print-certs`.
Use the same test certificate as the debug instrumentation APK.
Do not check a keystore or its passwords into Git.

```powershell
.\scripts\Test-Legacy.ps1 -Serial 127.0.0.1:5561 -Apk C:\absolute\legacy-signed.apk
.\scripts\Test-Legacy.ps1 -Serial emulator-5562 -Apk C:\absolute\legacy-signed.apk
```

The runner checks the real API number and APK hashes, then exercises all eight
public styles, persisted settings, single/dual layouts, rotation, gestures/lock,
language selection, brightness-dialog rotation, Home/timeout restoration,
first-run help, battery/month content and cached temperature units,
an owned CalendarProvider fixture and its off state, real Open-Meteo JSON,
TLS positive/negative controls and cancellation after hiding weather.
It fails on unavailable live services and
cleans up its own calendar in `finally`. It resets **this experimental app's**
preferences. Use isolated AVDs. Every ADB command includes the supplied serial.

The `legacy-test-fixture` APK is test-only, has no launcher, and is the only
module with calendar write permission. It records its own calendar ID and
checks ownership before deletion. The product requests only READ_CALENDAR.
API14 has no CancellationSignal query API: late cursors are closed and discarded;
an in-flight CalendarProvider Binder operation cannot be forcibly interrupted.

For debug-only internal boundary probes, use the same native instrumentation
with `-e mode clockStyles`, `displayChecks`, `dateReadout`, `calendarChecks`,
`batteryChecks`, `monthChecks` or `socketChecks`.
`directTls12` and `directTls13` isolate a verified provider handshake from the
platform URLConnection adapter; they do not change the product protocol policy.
Clock fixtures use the same UTC time in all eight styles, both time formats,
seconds visibility, time-zone change and month rollover; two PNG grids are written
to the app's private files directory. Display checks use real OS rotation plus
a fontScale 2 native view context, checking real 48dp touch targets without
changing the emulator's global font setting. Socket checks connect to
a silent loopback TLS peer and checks deadline/cancellation socket closure.
These modes reference unminified implementation classes; the external matrix
above also works against R8 release builds.
`certificateChecks` decodes the 118 bundled root keys and validates a frozen,
normally verified public Open-Meteo chain at its capture date. It also works
on release because BC registration classes are retained. This broad key audit
is slow on the API14 ARM emulator and is a diagnostic, not a per-change gate.
`frozenChainStress` repeats that frozen chain's PKIX validation 100 times without
the broad root-key audit; it isolates certificate processing from live transport.

## TLS and frozen inputs

BC 1.87 `jdk15to18` supplies crypto, PKIX and TLS through explicit provider
objects. No global provider replacement, JNI, AndroidX or Play Services is used
in Legacy. TLS 1.2 is enforced against old HttpConnection/OkHttp SSLv3 rewrites.
BC HTTPS endpoint identification validates the hostname during the handshake;
the platform's default verifier is retained as an additional check.
Untrusted and wrong-host peers must fail before any HTTP response. Redirects
are rejected and there is no HTTP fallback. Responses are capped at 64 KiB.
Each request has a 30-second transport watchdog; cancellation closes the raw
layered socket without waiting for the TLS/URLConnection monitor.

`dependency-checksums.sha256` pins the three jars and certificate-only BKS asset.
`verifyLegacyInputs` runs before builds, including release. Maven Central's
official `repo1.maven.org` endpoint is used only for the BC group.
R8 retains BC's dynamic registrations. Optional BC LDAP/JNDI stores are unused
and unavailable on Android; the JNDI missing-class suppression is scoped to
`javax.naming`. Local PKIX uses the bundled root store, not LDAP discovery.

The trust bundle comes from curl/Mozilla NSS, dated 2026-11-05, with 118 roots.
Source and hashes are in `src/main/assets/third_party/Mozilla-CA-NOTICE.txt`;
BC and MPL-2.0 notices ship in the APK. The empty public BKS password protects
no secret; APK signing protects the bundled bytes. `/dev/urandom` is the explicit
entropy source, and an unavailable source fails closed.

Root updates require a reviewed app update: obtain the official Mozilla bundle
from `https://curl.se/ca/cacert.pem` over verified HTTPS, review certificate and
license changes, and run `tools/legacy/FreezeRoots.java` on the host with the
pinned BC provider jar (`FreezeRoots input.pem output.bks`). Record the new
source date/hash, BKS hash and certificate count; update the notice and checksum
file together. Re-run API14/19 release positive and negative controls. Never
add a failing server's leaf certificate to make a connection pass.
Builds use the checked-in frozen BKS; they never download or regenerate roots.

## Environment and limits

API14 was Android 4.0.2, official default ARM v7a image r2, with the archived
official Emulator 26.1.4 classic engine. Modern QEMU2 rejects that ARM image.
API19 was Android 4.4.2, official default x86 image r6, on Emulator 36.1.9/WHPX.
Keep the classic engine in a separate directory; do not replace the current SDK
emulator. An API15 image is not an API14 test substitute.

Emulators establish OS compatibility for exercised paths. They do not establish
old ARM performance, battery life, frame budgets, full feature parity or final
signing/upgrade/soak acceptance. Those remain separate gates.

An API19 live run rejected a trusted chain during BC ECDSA verification with
`IllegalStateException: Invalid result`. The identical frozen chain passed 100
offline checks and a later live request succeeded; the cause remains unresolved.
Earlier handshake timeouts also remain unexplained. Validation stays strict,
and these successful controls do not establish that either fault is fixed.
