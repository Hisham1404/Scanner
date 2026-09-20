# From the website to the app

The website does everything the web platform allows. This is what it cannot do,
why, and what the phone app is for.

Target device: **Galaxy S25 Ultra**, Android first.

---

## 1. Why an app at all

Four capabilities are unreachable from any browser on any platform. They are not
missing features in this codebase — the APIs do not exist for web pages, for good
security reasons.

| Capability | Why the web cannot | What it buys |
|---|---|---|
| Wi-Fi scanning | No web API exposes nearby SSIDs or BSSIDs | Camera modules broadcast their own setup APs with recognisable names |
| Device enumeration on a network | No raw sockets, no ARP, no ICMP, no arbitrary TCP | The single most reliable automated signal for networked cameras |
| MAC / OUI lookup | MAC addresses are not exposed | Vendor identification — Hikvision, Dahua, Tapo, Ezviz, Reolink, Wyze |
| Bluetooth LE scanning | `requestLEScan` is flag-gated and unusable in practice | Cameras that advertise over BLE during pairing |

There is also a quality argument for the scanner itself. The browser gives you a
`MediaStreamTrack` with auto-exposure, auto-focus and auto-white-balance fighting
you, and a torch toggle with unpredictable latency. Camera2 gives you manual
control of all of it. That is a real accuracy gain, not a nicety — see phase 1.

---

## 2. Platform choice

**Kotlin + Jetpack Compose, native.** Not React Native, not Flutter.

Almost the entire value of the app is platform API surface: `CameraX`/`Camera2`,
`WifiManager`, `ConnectivityManager`, `NsdManager`, `BluetoothLeScanner`, raw
sockets. In a cross-platform framework every one of those needs a native module,
so you pay the bridge cost and still write the Kotlin. The shared-code argument
does not apply when there is no meaningful shared code.

**Android first is not arbitrary.** iOS has no public Wi-Fi scanning API at all —
`NEHotspotHelper` needs a special entitlement Apple does not grant for this. Local
network access is gated behind a permission prompt and Bonjour only. An iOS
version can carry the scanner, the sweep, the guide and the legal kit, but phases
2–4 below are largely not possible there. Ship Android, then decide.

---

## 3. Phases

### Phase 1 — Port the scanner properly

The web version is a good demonstration of the method. The native version is the
method done right.

- **CameraX + Camera2 interop.** Lock AE, AF and AWB during a pulse cycle so the
  torch-on and torch-off frames are genuinely comparable. This is the single
  biggest accuracy win available — the web version has to tolerate the camera
  re-metering between frames and pads its settle time to compensate.
- **Precise torch timing** via `CameraControl.enableTorch()` with capture-result
  callbacks, instead of `applyConstraints` and a fixed 190 ms wait. Cuts cycle
  time roughly in half and removes the AE drift.
- **Analyse at native resolution** on the 200 MP main sensor rather than a 240 px
  downscale, in a `RenderScript`/`Vulkan` compute pass or on the NNAPI/GPU path.
  A lens at 4 m is a handful of pixels; resolution is exactly what you need.
- **Use the 5× periscope** for ceiling corners and high fittings. Optical zoom on
  a retroreflective target is far better than walking closer, because the torch
  beam geometry stays constant.
- **RAW capture** (`ImageFormat.RAW_SENSOR`) to get linear sensor values with no
  tone curve. Retroreflection detection is a brightness-ratio problem and a
  display gamma curve actively destroys the ratio you are measuring.
- Keep the confidence model, the persistence tracking and the blob filter — they
  are already validated by the test suite and port directly.

**On the S25 Ultra's laser autofocus specifically:** it is a 1-D ranging sensor,
not the imaging time-of-flight array LAPD used (that was the S20+/Note10+
generation), and Android exposes no API for it regardless. A LAPD port is not
available on this hardware. The RGB-plus-torch approach above is the right call,
and RAW plus locked exposure is what closes most of the gap.

### Phase 2 — Network discovery

The headline feature, and the reason the app exists.

- **Active subnet sweep.** Derive the subnet from the current interface, then
  probe every host. Note that `/proc/net/arp` is restricted on modern Android, so
  this has to be active probing rather than reading the neighbour table.
- **mDNS / DNS-SD** via `NsdManager` — `_rtsp._tcp`, `_onvif._tcp`, `_http._tcp`,
  `_dahua._tcp`, `_hap._tcp`. Requires a multicast lock.
- **SSDP / UPnP** M-SEARCH discovery on 239.255.255.250:1900.
- **ONVIF `WS-Discovery` probe** — the standard cameras answer even when their
  web UI is locked down.
- **Port fingerprinting** on hits: 554 and 8554 (RTSP), 80/443/8080/8000 (web UI),
  37777 (Dahua), 8000 (Hikvision).
- **Bundled OUI database** mapping MAC prefixes to vendors, flagging the known
  camera manufacturers. Ship it offline; it is a few hundred KB.

Permissions: `ACCESS_FINE_LOCATION` (Android still gates Wi-Fi scan results
behind it), `NEARBY_WIFI_DEVICES` on Android 13+, `CHANGE_WIFI_STATE`,
`INTERNET`. Respect Wi-Fi scan throttling — four scans per two minutes for
foreground apps since Android 9 — and design the UI around that cadence rather
than fighting it.

### Phase 3 — Traffic-pattern detection

This is the one that finds a camera the owner has hidden well, and it has real
research behind it: DeWiCam (AsiaCCS 2018) and later traffic-pattern work show
that wireless cameras are identifiable from the *shape* of their traffic even
when the payload is encrypted.

- **Motion correlation.** Watch per-device throughput, then have the user wave a
  hand or switch the lights in front of a suspected object. A motion-triggered
  camera answers with a bitrate spike a second or two later. Correlating induced
  motion against a specific device's traffic is close to conclusive, and it works
  on an encrypted stream.
- **Idle-pattern classification.** Cameras keep a low, regular keepalive even
  when nothing moves, which looks nothing like a phone or a laptop.
- Works only for devices on a network you are on. State that plainly in the UI.

### Phase 4 — Radio sweep

- **Wi-Fi AP scan** for SSIDs matching camera patterns, without joining anything.
  Useful precisely when you do not want to join the hotel network.
- **BLE scan** for camera setup beacons and known manufacturer service UUIDs.
- **Signal-strength proximity.** As you walk, RSSI rises towards the transmitter.
  Crude direction-finding, but it narrows a room to a wall.

### Phase 5 — Evidence and reporting

- **Evidence package export**: photos and video with EXIF intact, device and
  timestamp metadata, a SHA-256 manifest over the files, and the written record
  the web version already generates, zipped together.
- **Pre-filled cybercrime.gov.in submission** and a printable complaint.
- **Offline everything.** The app must work in airplane mode in a hotel room. The
  web version already does; do not regress it.

---

## 4. Explicitly not building

- **A magnetometer "detector".** It does not work. A published test had one flag
  47 anomalies in a single hotel room, none of which were cameras. Including it
  would be the single fastest way to make the rest of the app untrustworthy.
- **IR as a headline feature.** Keep it as a clearly-labelled secondary mode with
  its sub-10% flagship detection rate stated, exactly as the web version does.
- **A subscription, an account, or a cloud backend.** There is nothing this tool
  needs a server for, and a privacy tool that uploads your room is a bad joke.

---

## 5. What the app still will not do

Worth writing into the UI, not just the docs.

A camera that records to an SD card and transmits nothing is invisible to every
phase above except phase 1. In the Delhi rented-home case the landlord's son
simply pulled the memory cards by hand. Optics and a physical search remain the
only methods that reach those — which is why the scanner and the sweep come first
in the website and should stay first in the app.

A clean result means you did not find a camera. Any product that lets a user come
away believing more than that is lying to them.
