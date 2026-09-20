# Scanner — Android

The phone app. Everything the web build does, plus the thing no browser on any
platform can do: ask the network what is on it.

Kotlin + Jetpack Compose, minSdk 26, targetSdk 35. No analytics, no backend, no
account. Findings live in app-private storage and are excluded from cloud backup
and device transfer.

## Install

**From CI, no toolchain needed.** Every push runs the `Android` workflow, which
attaches `scanner-release-apk` to the run. Download it from the Actions tab,
open the `.apk` on the phone, and allow installing from that source when Android
asks.

**From source:**

```
cd android
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The release build is signed with the debug key so `assembleRelease` produces
something installable straight away. Replace `signingConfig` in
`app/build.gradle.kts` with a real keystore before distributing it anywhere.

## What the app adds over the website

| | Web | App |
|---|---|---|
| Lens-glint scan | yes | yes, with exposure and white balance locked |
| Torch pulse modulation | yes, ~190 ms settle | yes, ~140 ms settle |
| Luminance source | RGBA read back from a canvas | the YUV Y plane directly |
| Subnet TCP sweep | **impossible** | 254 hosts × 10 ports |
| UPnP / SSDP listen | **impossible** | yes |
| ONVIF WS-Discovery | **impossible** | yes |
| mDNS / DNS-SD | **impossible** | yes |
| Wi-Fi access point scan | **impossible** | yes, with BSSID vendor lookup |
| Bluetooth LE scan | **impossible** | yes |
| Works offline | yes | yes |

The exposure lock is the substantive scanning improvement. Pulse mode compares a
torch-on frame against a torch-off frame, and a browser cannot stop the camera
re-metering between the two, so the web build pads its settle time to absorb the
drift. `CONTROL_AE_LOCK` and `CONTROL_AWB_LOCK` remove the drift instead of
waiting it out.

## Layout

```
detect/GlintDetector.kt   Detection maths. No Android imports, so it unit tests.
detect/ScanEngine.kt      Torch-pulse state machine, YUV sampling, rotation.
net/Heuristics.kt         Camera scoring, SSID patterns, OUI table. Also pure.
net/NetworkScanner.kt     TCP sweep, SSDP, ONVIF WS-Discovery, reverse DNS.
net/MdnsScanner.kt        DNS-SD discovery (API 34 path and the legacy one).
net/RadioScanner.kt       Wi-Fi AP scan and BLE scan, with permission gating.
data/Content.kt           Zones, checkpoints, device types, case files.
data/Findings.kt          Findings store and the written-record generator.
ui/                       Compose screens and the design system.
```

`GlintDetector` and `Heuristics` are deliberately free of Android imports. That
is what lets 29 JVM unit tests cover the parts that decide whether a result is
reported, without a device or an emulator in the loop.

## Tests

```
./gradlew testDebugUnitTest lintDebug
```

29 tests. The detector suite is the same set of synthetic-frame cases the web
build passes, ported, so the two engines stay comparable: it must find a small
compact return and two separate ones, reject a thin streak and an oversized
blob, ignore a uniformly bright frame and a smooth gradient, cancel a standby
LED under torch modulation while keeping the torch-lit return, and raise
confidence only as a candidate is re-seen. The heuristics suite checks that an
ONVIF reply reads as strong, that a bare web interface does **not** get flagged,
that real pairing SSIDs match and ordinary home SSIDs do not, and that the
blind-spot list leads with the offline-camera case.

Lint runs with `abortOnError`, at zero errors and zero warnings.

## Permissions, and why each one

| Permission | For |
|---|---|
| `CAMERA` | The lens-glint scan. |
| `INTERNET`, `ACCESS_NETWORK_STATE` | Probing hosts, and reading the subnet off the active interface. |
| `ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE` | Access-point scan. |
| `CHANGE_WIFI_MULTICAST_STATE` | Without a multicast lock the Wi-Fi chip drops SSDP and ONVIF replies. This is the most common reason a hand-rolled scanner "finds no cameras" on a network that has them. |
| `NEARBY_WIFI_DEVICES` (33+) / `ACCESS_FINE_LOCATION` + `COARSE` (≤32) | Android gates Wi-Fi scan results behind these. Declared `neverForLocation`; no location is derived. |
| `BLUETOOTH_SCAN` (31+) | BLE advertisements. Also `neverForLocation`. |

## What it still cannot do

Stated in the app next to every network result, not only here.

A camera that records to an SD card and transmits nothing is invisible to every
probe in this app except the lens scan. In the Delhi rented-home case the
landlord's son simply pulled the memory cards by hand. The most common concealed
camera across the reported Indian cases was a switched-on mobile phone — also
invisible to a network scan.

A network scan sees devices on the network you joined, that are powered on, and
that transmit. Nothing else. The lens scan and the physical sweep are not the
consolation prize here; they are the part that reaches the cases that actually
happened.

## Not built, on purpose

No magnetometer mode. Compass-based "detectors" react to wiring, pipes, hinges
and speakers; a published test had one flag 47 anomalies in a single hotel room,
none of which were cameras. Including it would be the fastest way to make
everything else in the app untrustworthy.
