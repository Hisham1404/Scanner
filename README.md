# Scanner

A browser tool for sweeping a room for hidden cameras: lens-glint scanning with a
phone camera and torch, a guided physical search, and what to do under Indian law
if you actually find one.

**Live: <https://scanner-sigma-wine.vercel.app>**

**Android app: [`android/`](android/)** — adds the network, Wi-Fi and Bluetooth
scanning the web platform makes impossible, and locks camera exposure during
torch pulsing. Install the APK from the Actions tab; see [android/README.md](android/README.md).

Static HTML, CSS and JavaScript. No build step, no dependencies, no server, no
analytics. Everything runs in the browser and nothing leaves the device.

## Deployment

Deployed on Vercel as a static site — no framework, no build command, no output
directory. The project is `scanner`; the production URL is above.

The project is **not** connected to the repository for automatic deploys. The
deployment was created from the GitHub source directly, which does not install
the webhook, so pushing to this branch will not rebuild it. To turn that on,
open the project in the Vercel dashboard and connect the Git repository under
Settings → Git; after that every push to the default branch deploys on its own.

## Run it locally

```
python3 -m http.server 8000
```

Then open `http://localhost:8000`.

Camera access needs a secure context, so on a phone you need **HTTPS** or
`localhost`. Any static host works — Vercel, Netlify, GitHub Pages, Cloudflare
Pages. Drop the folder in and it runs.

## What is here

| File | |
|---|---|
| `index.html` | What the tool does and, more usefully, what it cannot do |
| `scan.html` | Live lens-glint scanner |
| `sweep.html` | Seven-zone guided physical search, 43 checkpoints |
| `guide.html` | Six device types, detection coverage, reported cases |
| `legal.html` | What to do if you find one — evidence, who to call, which law |
| `report.html` | Findings log → timestamped record + complaint draft |
| `js/scanner.js` | The detection engine |
| `js/data.js` | Device taxonomy, case file, sweep checkpoints |
| `sw.js` | Offline cache — the tool works with no network at all |
| `assets/fonts/` | Self-hosted Inter + JetBrains Mono subsets |

## Design

Dark-only, on purpose: this gets used in dim rooms at night, and a second theme
would be two half-committed designs instead of one.

Four rules hold throughout:

- **The interface is monochrome until it finds something.** Colour is information,
  not decoration. Red means a detection or a danger, amber means uncertain, and
  nothing else anywhere is coloured. When colour appears, it means something.
- **No cards.** Hairline rules and space do the separating.
- **Square geometry**, radii 0-3px. This is an instrument, not a dashboard.
- **Prose is Inter, every number and label is mono.** That split is what makes
  readings read as readings.

Type is Inter and JetBrains Mono, latin subsets self-hosted in `assets/fonts`
(80KB total, both SIL Open Font License) so the site stays offline-capable and
makes no third-party requests.

## How the detection actually works

A camera is a lens focused onto a sensor. Light entering the lens is focused to a
point and a large share of it returns along the path it came in. That is
retroreflection, and it is why a hidden lens throws back a small, very bright,
roughly circular spot when lit from beside your own camera. Walls, plastic and
fabric scatter light in every direction instead, so they never do this.

Seeing bright spots is easy. Telling a lens from a chrome tap is the actual
problem, and two things do most of that work.

**Torch modulation** (Pulse mode) captures one frame with the torch on and one
with it off and analyses the difference. Room lights, standby LEDs, windows and
screens produce their own light, so they subtract to nothing. Only what the torch
lit up survives, and a retroreflector dominates that.

**Persistence.** A specular highlight on a flat glossy surface slides away and
dies the moment your angle changes. A retroreflector keeps returning light to the
source across a wide range of angles. Every candidate is tracked across detection
cycles and has to be re-seen before it scores highly, so a one-frame flash never
reaches a high confidence.

The blob filter then requires a candidate to be small, compact, roughly round, and
well clear of its own local background — a bright but evenly lit wall fails the
local-contrast test even though it passes the absolute one.

Published support: **LAPD**, *Hidden Spy Camera Detection using Smartphone
Time-of-Flight Sensors*, ACM SenSys 2021 (NUS / Yonsei) — 88.9% detection from
lens retroreflection against 46% for the naked eye. LAPD drove an imaging
time-of-flight array, which current phones no longer expose; this runs the same
optical idea on the ordinary camera and the torch.

## What is deliberately not here

**No magnetometer mode.** Compass-based "detectors" react to wiring, pipes,
hinges and speakers. A published test had one flag 47 anomalies in a single hotel
room, none of which were cameras.

**Infrared is included with a warning, not as a test.** Night-vision cameras do
emit IR at 850/940 nm, but modern phone cameras filter it out; measured detection
rates on current Samsung and Apple flagships are under 10%.

**No network scanning, because no website can do it.** Scanning Wi-Fi, listing
devices on a network and reading MAC addresses are not available to any browser on
any platform. That is a hard limit of the web, not a gap in this code, and it is
the main reason to build a native app. See `ROADMAP.md`.

## Limits worth stating plainly

A clean sweep means you did not find a camera. It does not mean there isn't one.
A camera recording to an SD card transmits nothing and is invisible to every app,
detector and network scan in existence — optics and a physical search are the only
things that reach those.

This is a search aid, not a guarantee, and nothing in it is legal advice.

## Privacy

There is no server. Findings live in `localStorage` under the `scanner:` prefix
and never leave the browser. Camera frames are analysed in memory and are never
stored or transmitted. Clearing site data erases everything.

## Tests

```
python3 -m http.server 8099 &
cd /path/to/scratch && npm install playwright
node test.mjs      # pages, detection algorithm, sweep → report flow, layout
node camtest.mjs   # live camera pipeline against a synthetic video device
```

`test.mjs` exercises the detector against synthetic frames: it must find a small
compact return, find two separate ones, reject a long thin streak, reject an
oversized blob, ignore a uniformly bright frame and a smooth gradient, cancel a
standby LED under torch modulation while keeping the torch-lit return, and raise
confidence only as a candidate is re-seen.
