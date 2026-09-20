/* Lens-glint scanner.
 *
 * Physics: a camera is a lens focused onto a sensor. Light entering the lens is
 * focused to a point on the sensor and a large share of it bounces straight back
 * out along the path it came in. That is retroreflection, and it is why a hidden
 * lens throws back a small, very bright, roughly circular spot when you light it
 * from beside your own camera. Painted walls, plastic and fabric scatter light in
 * all directions instead, so they never produce that spot.
 *
 * The hard part is not seeing bright spots. It is telling a lens apart from a
 * chrome tap, a glossy tile or a ceiling light. Two things do most of that work:
 *
 *   1. Torch modulation (Pulse mode). Capture one frame with the torch on and one
 *      with it off, and analyse the difference. Anything that glows on its own -
 *      room lights, standby LEDs, a window, a TV - subtracts to nothing. What
 *      survives is only what the torch lit up, and a retroreflector dominates it.
 *
 *   2. Persistence. A specular highlight on a flat glossy surface slides away and
 *      dies the moment your angle changes. A retroreflector keeps returning light
 *      to the source across a wide range of angles, so it survives being tracked
 *      over several cycles while you move. Every candidate here has to be seen
 *      repeatedly before it is scored highly.
 *
 * This method is the one with published support behind it: LAPD (NUS / Yonsei,
 * ACM SenSys 2021) reported 88.9% detection from lens retroreflection against
 * 46% for the naked eye. LAPD drove an imaging time-of-flight array, which recent
 * phones - the S25 Ultra included - no longer expose. This runs the same optical
 * idea on the ordinary camera and the torch instead.
 */

const WORK_W = 240;          // analysis width in px; height follows the aspect
const SETTLE_MS = 190;       // let auto-exposure settle after a torch change
const LIVE_INTERVAL = 130;   // ms between frames in continuous modes
const MATCH_DIST = 0.09;     // candidate match radius, as a fraction of width
const MAX_MISSES = 4;        // cycles a candidate survives without being re-seen

const MODE_HINTS = {
  pulse: 'Strobes the torch and analyses only the light the torch put there. Room lights, standby LEDs and windows cancel out. Slowest but by far the most trustworthy — use this one.',
  live: 'Torch stays on and every frame is analysed. Faster and smoother for covering a whole room, but it will react to chrome, glass and glossy tile. Confirm anything it finds in Pulse mode.',
  ir: 'Front camera, torch off, lights off. Looks for infrared LEDs on night-vision cameras. Treat a negative result as meaningless — flagship IR-cut filters block most of this, with measured detection rates under 10%.'
};

const MODE_LABELS = { pulse: 'Pulse', live: 'Continuous', ir: 'Infrared' };

const Scanner = {
  els: {},
  stream: null,
  track: null,
  caps: {},
  hasTorch: false,
  torchOn: false,
  mode: 'pulse',
  sensitivity: 3,
  running: false,
  phase: 'on',
  phaseAt: 0,
  lastLive: 0,
  frameOn: null,
  frameOff: null,
  work: null,
  wctx: null,
  dims: { w: 0, h: 0 },
  candidates: [],
  raf: 0,

  async init() {
    this.els = {
      stage: $('#stage'),
      video: $('#video'),
      overlay: $('#overlay'),
      msg: $('#stageMsg'),
      verdict: $('#verdict'),
      vState: $('#verdictState'),
      vPct: $('#verdictPct'),
      vMeter: $('#verdictMeter'),
      vTitle: $('#verdictTitle'),
      vSub: $('#verdictSub'),
      pillMode: $('#pillMode'),
      pillState: $('#pillState'),
      start: $('#btnStart'),
      stop: $('#btnStop'),
      shot: $('#btnShot'),
      log: $('#btnLog'),
      sens: $('#sens'),
      sensOut: $('#sensOut'),
      zoomRow: $('#zoomRow'),
      zoom: $('#zoom'),
      zoomOut: $('#zoomOut'),
      modes: $$('#modeSwitch button'),
      hint: $('#modeHint')
    };

    this.work = document.createElement('canvas');
    this.wctx = this.work.getContext('2d', { willReadFrequently: true });

    this.els.modes.forEach((b) => {
      b.addEventListener('click', () => this.setMode(b.dataset.mode));
    });
    this.els.start.addEventListener('click', () => this.start());
    this.els.stop.addEventListener('click', () => this.stop());
    this.els.shot.addEventListener('click', () => this.snapshot());
    this.els.log.addEventListener('click', () => this.logFinding());
    this.els.sens.addEventListener('input', () => {
      this.sensitivity = Number(this.els.sens.value);
      this.els.sensOut.textContent = this.sensitivity;
    });
    this.els.zoom.addEventListener('input', () => this.applyZoom());

    window.addEventListener('pagehide', () => this.stop());
    document.addEventListener('visibilitychange', () => {
      if (document.hidden) this.stop();
    });

    this.setMode('pulse');
    this.setVerdict('idle', null, 'Camera off', 'Pick a mode and start the scan.');
  },

  setMode(mode) {
    this.mode = mode;
    this.els.modes.forEach((b) => {
      b.setAttribute('aria-pressed', String(b.dataset.mode === mode));
    });
    this.els.pillMode.textContent = MODE_LABELS[mode];
    this.candidates = [];
    this.frameOn = this.frameOff = null;

    this.els.hint.textContent = MODE_HINTS[mode];

    if (this.running) this.restart();
  },

  async start() {
    if (this.running) return;
    this.setMsg('Requesting camera…');
    const facing = this.mode === 'ir' ? 'user' : 'environment';

    try {
      this.stream = await navigator.mediaDevices.getUserMedia({
        video: {
          facingMode: { ideal: facing },
          width: { ideal: 1280 },
          height: { ideal: 960 }
        },
        audio: false
      });
    } catch (err) {
      const why = err && err.name === 'NotAllowedError'
        ? 'Camera permission was denied. Allow camera access for this site and try again.'
        : 'Could not open the camera: ' + (err && err.message ? err.message : String(err));
      this.setMsg(why);
      this.setVerdict('idle', null, 'Camera unavailable', why);
      return;
    }

    this.els.video.srcObject = this.stream;
    try { await this.els.video.play(); } catch (e) {}

    this.track = this.stream.getVideoTracks()[0];
    this.caps = (this.track.getCapabilities && this.track.getCapabilities()) || {};
    this.hasTorch = !!this.caps.torch;

    this.setupZoom();

    if (!this.hasTorch && this.mode !== 'ir') {
      this.setMsg('');
      this.toast('This browser will not let the page control the torch. Pulse mode needs it — switching to a torch-free scan. Use a separate torch held right next to the phone camera and the same physics still applies.');
      if (this.mode === 'pulse') this.mode = 'live';
      this.els.modes.forEach((b) => b.setAttribute('aria-pressed', String(b.dataset.mode === this.mode)));
      this.els.pillMode.textContent = MODE_LABELS[this.mode];
      this.els.hint.textContent = MODE_HINTS[this.mode];
    }

    await this.waitForVideo();
    this.sizeCanvases();

    this.running = true;
    this.candidates = [];
    this.frameOn = this.frameOff = null;
    this.phase = 'on';
    this.phaseAt = 0;
    this.setMsg('');
    this.els.start.disabled = true;
    this.els.stop.disabled = false;
    this.els.shot.disabled = false;
    this.els.log.disabled = false;
    this.els.pillState.textContent = 'Scanning';
    this.els.pillState.className = 'is-live';

    if (this.mode === 'live' && this.hasTorch) await this.setTorch(true);
    if (this.mode === 'ir') await this.setTorch(false);

    this.loop();
  },

  async restart() {
    this.stop();
    await new Promise((r) => setTimeout(r, 180));
    this.start();
  },

  stop() {
    if (!this.running && !this.stream) return;
    this.running = false;
    cancelAnimationFrame(this.raf);
    if (this.track && this.hasTorch && this.torchOn) {
      try { this.track.applyConstraints({ advanced: [{ torch: false }] }); } catch (e) {}
    }
    this.torchOn = false;
    if (this.stream) this.stream.getTracks().forEach((t) => t.stop());
    this.stream = null;
    this.track = null;
    this.els.video.srcObject = null;
    this.clearOverlay();
    this.candidates = [];
    if (this.els.start) {
      this.els.start.disabled = false;
      this.els.stop.disabled = true;
      this.els.shot.disabled = true;
      this.els.log.disabled = true;
      this.els.pillState.textContent = 'Idle';
      this.els.pillState.className = '';
      this.setMsg('Camera off.');
      this.setVerdict('idle', null, 'Camera off', 'Pick a mode and start the scan.');
    }
  },

  waitForVideo() {
    const v = this.els.video;
    if (v.videoWidth) return Promise.resolve();
    return new Promise((res) => {
      const on = () => { v.removeEventListener('loadedmetadata', on); res(); };
      v.addEventListener('loadedmetadata', on);
      setTimeout(res, 2500);
    });
  },

  sizeCanvases() {
    const v = this.els.video;
    const vw = v.videoWidth || 640;
    const vh = v.videoHeight || 480;
    const w = WORK_W;
    const h = Math.max(2, Math.round((vh / vw) * WORK_W));
    this.work.width = w;
    this.work.height = h;
    this.dims = { w: w, h: h };
    this.els.overlay.width = vw;
    this.els.overlay.height = vh;
    this.octx = this.els.overlay.getContext('2d');
  },

  setupZoom() {
    const z = this.caps.zoom;
    if (!z || typeof z.max !== 'number' || z.max <= z.min) {
      this.els.zoomRow.hidden = true;
      return;
    }
    this.els.zoomRow.hidden = false;
    this.els.zoom.min = z.min;
    this.els.zoom.max = Math.min(z.max, z.min + (z.max - z.min));
    this.els.zoom.step = z.step || 0.1;
    this.els.zoom.value = z.min;
    this.els.zoomOut.textContent = Number(z.min).toFixed(1) + '×';
  },

  applyZoom() {
    const v = Number(this.els.zoom.value);
    this.els.zoomOut.textContent = v.toFixed(1) + '×';
    if (!this.track) return;
    try { this.track.applyConstraints({ advanced: [{ zoom: v }] }); } catch (e) {}
  },

  async setTorch(on) {
    if (!this.track || !this.hasTorch) return;
    if (this.torchOn === on) return;
    try {
      await this.track.applyConstraints({ advanced: [{ torch: on }] });
      this.torchOn = on;
    } catch (e) {
      this.hasTorch = false;
    }
  },

  /* ---- frame loop ---- */

  loop() {
    if (!this.running) return;
    this.raf = requestAnimationFrame((t) => this.loop());
    const now = performance.now();

    if (this.mode === 'pulse' && this.hasTorch) this.tickPulse(now);
    else this.tickLive(now);
  },

  tickLive(now) {
    if (now - this.lastLive < LIVE_INTERVAL) return;
    this.lastLive = now;
    const luma = this.grabLuma();
    if (!luma) return;
    const blobs = this.analyse(luma, this.mode === 'ir' ? 'ir' : 'direct');
    this.track_(blobs);
    this.render();
  },

  tickPulse(now) {
    if (!this.phaseAt) this.phaseAt = now;
    const elapsed = now - this.phaseAt;

    switch (this.phase) {
      case 'on':
        this.setTorch(true);
        this.phase = 'wait-on';
        this.phaseAt = now;
        break;

      case 'wait-on':
        if (elapsed >= SETTLE_MS) {
          this.frameOn = this.grabLuma();
          this.phase = 'off';
          this.phaseAt = now;
        }
        break;

      case 'off':
        this.setTorch(false);
        this.phase = 'wait-off';
        this.phaseAt = now;
        break;

      case 'wait-off':
        if (elapsed >= SETTLE_MS) {
          this.frameOff = this.grabLuma();
          if (this.frameOn && this.frameOff) {
            const diff = this.difference(this.frameOn, this.frameOff);
            const blobs = this.analyse(diff, 'pulse');
            this.track_(blobs);
            this.render();
          }
          this.phase = 'on';
          this.phaseAt = now;
        }
        break;
    }
  },

  /* Downscale the current video frame and reduce it to luminance. */
  grabLuma() {
    const v = this.els.video;
    if (!v.videoWidth) return null;
    const { w, h } = this.dims;
    this.wctx.drawImage(v, 0, 0, w, h);
    let img;
    try { img = this.wctx.getImageData(0, 0, w, h); }
    catch (e) { return null; }
    const d = img.data;
    const out = new Float32Array(w * h);
    for (let i = 0, p = 0; i < out.length; i++, p += 4) {
      out[i] = 0.2126 * d[p] + 0.7152 * d[p + 1] + 0.0722 * d[p + 2];
    }
    return out;
  },

  /* Torch-on minus torch-off, clamped at zero.
     Anything that produces its own light cancels out here. */
  difference(on, off) {
    const out = new Float32Array(on.length);
    for (let i = 0; i < on.length; i++) {
      const d = on[i] - off[i];
      out[i] = d > 0 ? d : 0;
    }
    return out;
  },

  /* ---- detection ----
     Two conditions, both required:
       absolute  - the pixel is genuinely bright for this frame
       local     - it stands well clear of its own surroundings
     A bright but evenly lit wall passes the first and fails the second. */
  analyse(src, kind) {
    const { w, h } = this.dims;
    const n = w * h;

    let sum = 0, sumSq = 0, peak = 0;
    for (let i = 0; i < n; i++) {
      const v = src[i];
      sum += v; sumSq += v * v;
      if (v > peak) peak = v;
    }
    const mean = sum / n;
    const variance = Math.max(0, sumSq / n - mean * mean);
    const std = Math.sqrt(variance);

    /* sensitivity 1 (strict) .. 5 (loose) */
    const s = this.sensitivity;
    const kSigma = [5.2, 4.4, 3.6, 2.9, 2.3][s - 1];
    const localDelta = [46, 38, 31, 25, 19][s - 1];

    let absFloor;
    if (kind === 'pulse') absFloor = [46, 38, 31, 25, 20][s - 1];
    else if (kind === 'ir') absFloor = [150, 135, 120, 105, 92][s - 1];
    else absFloor = [238, 232, 224, 214, 204][s - 1];

    const thresh = Math.max(absFloor, mean + kSigma * std);
    if (peak < thresh) return [];

    const integral = this.integralImage(src, w, h);
    const radius = Math.max(4, Math.round(w * 0.045));

    const mask = new Uint8Array(n);
    for (let y = 0; y < h; y++) {
      for (let x = 0; x < w; x++) {
        const i = y * w + x;
        const v = src[i];
        if (v < thresh) continue;
        const local = this.boxMean(integral, w, h, x, y, radius);
        if (v - local >= localDelta) mask[i] = 1;
      }
    }

    return this.blobs(mask, src, w, h, thresh);
  },

  integralImage(src, w, h) {
    const iw = w + 1;
    const out = new Float64Array(iw * (h + 1));
    for (let y = 0; y < h; y++) {
      let rowSum = 0;
      for (let x = 0; x < w; x++) {
        rowSum += src[y * w + x];
        out[(y + 1) * iw + (x + 1)] = out[y * iw + (x + 1)] + rowSum;
      }
    }
    return out;
  },

  boxMean(integral, w, h, cx, cy, r) {
    const iw = w + 1;
    const x0 = Math.max(0, cx - r), y0 = Math.max(0, cy - r);
    const x1 = Math.min(w, cx + r + 1), y1 = Math.min(h, cy + r + 1);
    const area = (x1 - x0) * (y1 - y0);
    if (area <= 0) return 0;
    const sum = integral[y1 * iw + x1] - integral[y0 * iw + x1]
              - integral[y1 * iw + x0] + integral[y0 * iw + x0];
    return sum / area;
  },

  /* Flood-fill the mask into connected blobs, then keep only the ones
     shaped like a lens return: small, compact and roughly round. */
  blobs(mask, src, w, h, thresh) {
    const seen = new Uint8Array(w * h);
    const stack = new Int32Array(w * h);
    const out = [];

    const minArea = 2;
    const maxArea = Math.round(w * h * 0.018);

    for (let start = 0; start < mask.length; start++) {
      if (!mask[start] || seen[start]) continue;

      let sp = 0;
      stack[sp++] = start;
      seen[start] = 1;

      let area = 0, sx = 0, sy = 0, peak = 0, sumV = 0;
      let minX = w, maxX = 0, minY = h, maxY = 0;

      while (sp > 0) {
        const i = stack[--sp];
        const x = i % w, y = (i / w) | 0;
        const v = src[i];

        area++; sx += x; sy += y; sumV += v;
        if (v > peak) peak = v;
        if (x < minX) minX = x;
        if (x > maxX) maxX = x;
        if (y < minY) minY = y;
        if (y > maxY) maxY = y;

        for (let dy = -1; dy <= 1; dy++) {
          for (let dx = -1; dx <= 1; dx++) {
            if (dx === 0 && dy === 0) continue;
            const nx = x + dx, ny = y + dy;
            if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
            const ni = ny * w + nx;
            if (mask[ni] && !seen[ni]) { seen[ni] = 1; stack[sp++] = ni; }
          }
        }
      }

      if (area < minArea || area > maxArea) continue;

      const bw = maxX - minX + 1;
      const bh = maxY - minY + 1;
      const aspect = bw / bh;
      if (aspect < 0.4 || aspect > 2.5) continue;

      const fill = area / (bw * bh);
      if (fill < 0.35) continue;

      /* how far above the detection floor this blob actually sits */
      const avg = sumV / area;
      const excess = Math.min(1, (avg - thresh) / Math.max(20, thresh * 0.45));

      const roundness = 1 - Math.min(1, Math.abs(1 - aspect));
      const compactness = Math.min(1, fill / 0.8);
      const sizeFit = area <= maxArea * 0.35 ? 1 : 0.6;

      const score = Math.max(0, Math.min(1,
        0.50 * Math.max(0, excess) +
        0.22 * roundness +
        0.18 * compactness +
        0.10 * sizeFit
      ));

      out.push({
        x: sx / area, y: sy / area,
        r: Math.max(3, Math.sqrt(area / Math.PI) * 1.9),
        area: area, peak: peak, score: score
      });
    }

    out.sort((a, b) => b.score - a.score);
    return out.slice(0, 8);
  },

  /* Match this cycle's blobs onto persistent candidates.
     A reflection that only ever shows up once never scores highly. */
  track_(blobs) {
    const { w } = this.dims;
    const maxDist = w * MATCH_DIST;

    this.candidates.forEach((c) => { c.matched = false; });

    blobs.forEach((b) => {
      let best = null, bestD = Infinity;
      for (const c of this.candidates) {
        if (c.matched) continue;
        const d = Math.hypot(c.x - b.x, c.y - b.y);
        if (d < bestD) { bestD = d; best = c; }
      }
      if (best && bestD <= maxDist) {
        best.matched = true;
        best.misses = 0;
        best.hits++;
        best.x = best.x * 0.55 + b.x * 0.45;
        best.y = best.y * 0.55 + b.y * 0.45;
        best.r = best.r * 0.6 + b.r * 0.4;
        best.score = best.score * 0.6 + b.score * 0.4;
      } else {
        this.candidates.push({
          x: b.x, y: b.y, r: b.r, score: b.score,
          hits: 1, misses: 0, matched: true
        });
      }
    });

    this.candidates = this.candidates.filter((c) => {
      if (!c.matched) c.misses++;
      return c.misses <= MAX_MISSES;
    });

    this.candidates.forEach((c) => {
      const persistence = Math.min(1, (c.hits - 1) / 2);
      c.confidence = Math.round(c.score * (0.55 + 0.45 * persistence) * 100);
    });

    this.candidates.sort((a, b) => b.confidence - a.confidence);
    if (this.candidates.length > 6) this.candidates.length = 6;
  },

  /* ---- drawing ---- */

  clearOverlay() {
    if (this.octx) this.octx.clearRect(0, 0, this.els.overlay.width, this.els.overlay.height);
  },

  render() {
    if (!this.octx) return;
    const cw = this.els.overlay.width;
    const ch = this.els.overlay.height;
    const sx = cw / this.dims.w;
    const sy = ch / this.dims.h;
    const ctx = this.octx;

    ctx.clearRect(0, 0, cw, ch);

    const shown = this.candidates.filter((c) => c.confidence >= 30 && c.hits >= 2);

    shown.forEach((c) => {
      const x = c.x * sx, y = c.y * sy;
      const r = Math.max(18, c.r * sx * 2.2);
      const color = c.confidence >= 70 ? '#ff4a38'
                  : c.confidence >= 48 ? '#d9a441'
                  : 'rgba(237, 235, 230, 0.5)';

      ctx.strokeStyle = color;
      ctx.lineWidth = Math.max(1.4, cw / 560);
      ctx.lineCap = 'butt';

      ctx.beginPath();
      ctx.arc(x, y, r, 0, Math.PI * 2);
      ctx.stroke();

      /* four outward ticks, so the mark reads as a sight rather than a bubble */
      const t0 = r * 1.4, t1 = r * 1.85;
      [[1, 0], [-1, 0], [0, 1], [0, -1]].forEach(([dx, dy]) => {
        ctx.beginPath();
        ctx.moveTo(x + dx * t0, y + dy * t0);
        ctx.lineTo(x + dx * t1, y + dy * t1);
        ctx.stroke();
      });

      ctx.fillStyle = color;
      ctx.font = '500 ' + Math.round(cw / 42) + 'px "JetBrains Mono", ui-monospace, monospace';
      ctx.textAlign = 'left';
      ctx.textBaseline = 'middle';
      ctx.fillText(String(c.confidence).padStart(2, '0'), x + t1 + cw / 80, y);
    });

    this.updateVerdict(shown);
  },

  updateVerdict(shown) {
    const top = shown[0];

    if (!top) {
      this.setVerdict('clear', null, 'Nothing standing out',
        this.mode === 'pulse'
          ? 'Keep moving slowly and re-cross the same spots from two or three angles before you call it clear.'
          : 'Keep sweeping. Switch to Pulse mode to rule out glossy surfaces.');
      this.setPill('Scanning', 'is-live');
      return;
    }

    if (top.confidence >= 70) {
      this.setVerdict('hit', top.confidence, 'Strong retroreflection',
        'Something at the mark is bouncing light straight back. Hold still, move a step left and right, and see whether it stays bright. If it does, go and look at that spot with your hands.');
      this.setPill('Detected', 'is-hot');
    } else if (top.confidence >= 48) {
      this.setVerdict('maybe', top.confidence, 'Possible reflector',
        'Could be a lens, could be chrome or glass. Re-scan it in Pulse mode from a different angle before deciding.');
      this.setPill('Checking', 'is-live');
    } else {
      this.setVerdict('clear', top.confidence, 'Weak return',
        'Probably an ordinary shiny surface. Keep sweeping.');
      this.setPill('Scanning', 'is-live');
    }
  },

  setPill(text, cls) {
    this.els.pillState.textContent = text;
    this.els.pillState.className = cls || '';
  },

  setVerdict(kind, pct, title, sub) {
    if (!this.els.verdict) return;

    const states = { idle: 'Standby', clear: 'Clear', maybe: 'Uncertain', hit: 'Detected' };
    const mod = (kind === 'maybe' || kind === 'hit') ? ' is-' + kind : '';

    this.els.verdict.className = 'readout' + mod;
    this.els.vState.textContent = states[kind] || states.idle;
    this.els.vPct.textContent = pct === null || pct === undefined
      ? '\u2014\u2014'
      : String(pct).padStart(2, '0');
    this.els.vMeter.style.width = (pct === null || pct === undefined ? 0 : pct) + '%';
    this.els.vTitle.textContent = title;
    this.els.vSub.textContent = sub;
  },

  setMsg(text) {
    if (!this.els.msg) return;
    this.els.msg.textContent = text || '';
    this.els.msg.style.display = text ? 'flex' : 'none';
  },

  toast(text) {
    const box = $('#toast');
    if (!box) return;
    box.textContent = text;
    box.hidden = false;
  },

  /* ---- outputs ---- */

  snapshot() {
    const v = this.els.video;
    if (!v.videoWidth) return;
    const c = document.createElement('canvas');
    c.width = v.videoWidth;
    c.height = v.videoHeight;
    const ctx = c.getContext('2d');
    ctx.drawImage(v, 0, 0, c.width, c.height);
    ctx.drawImage(this.els.overlay, 0, 0, c.width, c.height);

    const stamp = new Date().toISOString();
    const bar = Math.round(c.height * 0.055);
    ctx.fillStyle = 'rgba(8, 8, 10, 0.82)';
    ctx.fillRect(0, c.height - bar, c.width, bar);
    ctx.strokeStyle = 'rgba(237, 235, 230, 0.18)';
    ctx.lineWidth = 1;
    ctx.beginPath();
    ctx.moveTo(0, c.height - bar + 0.5);
    ctx.lineTo(c.width, c.height - bar + 0.5);
    ctx.stroke();
    ctx.fillStyle = 'rgba(237, 235, 230, 0.75)';
    ctx.font = '500 ' + Math.round(c.width / 58) + 'px "JetBrains Mono", ui-monospace, monospace';
    ctx.textAlign = 'left';
    ctx.textBaseline = 'middle';
    ctx.fillText('SCANNER \u2014 ' + stamp, Math.round(c.width * 0.025), c.height - bar / 2);

    c.toBlob((blob) => {
      if (!blob) return;
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = 'scanner-' + stamp.replace(/[:.]/g, '-') + '.png';
      a.click();
      setTimeout(() => URL.revokeObjectURL(url), 4000);
    }, 'image/png');
  },

  logFinding() {
    const top = this.candidates.filter((c) => c.hits >= 2)[0];
    const where = prompt('Where in the room is this? (e.g. "bathroom ceiling above shower")');
    if (where === null) return;

    Findings.add({
      kind: 'scan',
      zone: 'Scanner',
      title: where.trim() || 'Unlabelled scanner hit',
      note: top
        ? 'Lens-glint scan in ' + this.mode + ' mode returned a candidate at ' + top.confidence +
          '% confidence, seen across ' + top.hits + ' detection cycles.'
        : 'Logged manually during a ' + this.mode + ' mode scan with no automatic candidate on screen.'
    });

    markFindingsBadge();
    this.toast('Logged. Open the Report tab to see everything you have flagged.');
  }
};

document.addEventListener('DOMContentLoaded', () => {
  if ($('#stage')) Scanner.init();
});
