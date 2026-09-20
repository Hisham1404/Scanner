/* Guided room sweep: walks the zones in the order that finds things fastest. */

const Sweep = {
  zone: 0,
  state: {},   // "zoneId:index" -> "ok" | "flag"

  init() {
    this.state = Store.get('sweep', {}) || {};
    this.zone = Number(Store.get('sweepZone', 0)) || 0;
    if (this.zone >= ZONES.length) this.zone = 0;

    this.renderZoneNav();
    this.renderZone();

    $('#btnPrev').addEventListener('click', () => this.go(this.zone - 1));
    $('#btnNext').addEventListener('click', () => this.go(this.zone + 1));
    $('#btnReset').addEventListener('click', () => this.reset());
  },

  key(zoneId, i) { return zoneId + ':' + i; },

  total() {
    return ZONES.reduce((n, z) => n + z.checks.length, 0);
  },

  answered() {
    return Object.keys(this.state).length;
  },

  go(i) {
    if (i < 0 || i >= ZONES.length) return;
    this.zone = i;
    Store.set('sweepZone', i);
    this.renderZoneNav();
    this.renderZone();
    window.scrollTo({ top: 0, behavior: 'smooth' });
  },

  zoneDone(z) {
    return z.checks.every((_, i) => this.state[this.key(z.id, i)]);
  },

  renderZoneNav() {
    const nav = $('#zoneNav');
    nav.innerHTML = '';
    let active = null;
    ZONES.forEach((z, i) => {
      const b = document.createElement('button');
      b.className = 'zone-chip' + (this.zoneDone(z) ? ' done' : '');
      b.type = 'button';
      b.textContent = (i + 1) + '. ' + z.name;
      b.setAttribute('aria-pressed', String(i === this.zone));
      b.addEventListener('click', () => this.go(i));
      nav.appendChild(b);
      if (i === this.zone) active = b;
    });
    /* keep the zone you are on visible in the strip */
    if (active && active.scrollIntoView) {
      active.scrollIntoView({ block: 'nearest', inline: 'center' });
    }
    this.renderProgress();
  },

  renderProgress() {
    const pct = Math.round((this.answered() / this.total()) * 100);
    $('#progBar').style.width = pct + '%';
    $('#progText').textContent = this.answered() + ' of ' + this.total() + ' checkpoints · ' + pct + '%';
  },

  renderZone() {
    const z = ZONES[this.zone];
    const host = $('#zoneBody');
    host.innerHTML = '';

    const head = document.createElement('div');
    head.innerHTML =
      '<p class="eyebrow">Zone ' + (this.zone + 1) + ' of ' + ZONES.length + '</p>' +
      '<h1>' + z.name + '</h1>' +
      '<p class="lede">' + z.blurb + '</p>';
    host.appendChild(head);

    if (z.web === false) {
      const n = document.createElement('div');
      n.className = 'note warn';
      n.innerHTML = '<strong>This zone is manual for now</strong>' +
        'Wi-Fi scanning, device enumeration and MAC-address lookups are not available to any website on any browser. ' +
        'They are the headline feature of the phone app in the roadmap. Until then, do these by hand — they are still worth doing.';
      host.appendChild(n);
    }

    const card = document.createElement('div');
    card.className = 'card';

    z.checks.forEach((c, i) => {
      const k = this.key(z.id, i);
      const cur = this.state[k];

      const row = document.createElement('div');
      row.className = 'check' + (cur === 'flag' ? ' flagged' : '');

      const body = document.createElement('div');
      body.className = 'check-body';
      body.innerHTML =
        '<div class="check-title">' + c.t + '</div>' +
        '<div class="check-hint">' + c.h + '</div>' +
        (c.c ? '<div class="check-case">' + c.c + '</div>' : '');

      const acts = document.createElement('div');
      acts.className = 'check-acts';

      const ok = document.createElement('button');
      ok.type = 'button';
      ok.dataset.act = 'ok';
      ok.title = 'Checked, looks clear';
      ok.setAttribute('aria-label', 'Mark clear: ' + c.t);
      ok.textContent = '✓';
      ok.setAttribute('aria-pressed', String(cur === 'ok'));

      const flag = document.createElement('button');
      flag.type = 'button';
      flag.dataset.act = 'flag';
      flag.title = 'Suspicious — add to report';
      flag.setAttribute('aria-label', 'Flag as suspicious: ' + c.t);
      flag.textContent = '⚠';
      flag.setAttribute('aria-pressed', String(cur === 'flag'));

      ok.addEventListener('click', () => this.mark(z, i, 'ok'));
      flag.addEventListener('click', () => this.mark(z, i, 'flag'));

      acts.appendChild(ok);
      acts.appendChild(flag);
      row.appendChild(body);
      row.appendChild(acts);
      card.appendChild(row);
    });

    host.appendChild(card);

    $('#btnPrev').disabled = this.zone === 0;
    $('#btnNext').disabled = this.zone === ZONES.length - 1;

    const done = $('#zoneDone');
    if (this.zone === ZONES.length - 1) {
      done.hidden = false;
      done.innerHTML =
        '<div class="note"><strong>That is the full sweep</strong>' +
        'You have walked every zone. If you flagged anything, the Report tab turns it into a written record with timestamps, ' +
        'plus a complaint draft you can take to a police station.</div>' +
        '<a class="btn btn-primary btn-block" href="report.html">Open the report</a>';
    } else {
      done.hidden = true;
      done.innerHTML = '';
    }
  },

  mark(z, i, act) {
    const k = this.key(z.id, i);
    const check = z.checks[i];

    if (this.state[k] === act) {
      delete this.state[k];
      if (act === 'flag') this.removeFinding(z, check);
    } else {
      this.state[k] = act;
      if (act === 'flag') this.addFinding(z, check);
      else this.removeFinding(z, check);
    }

    Store.set('sweep', this.state);
    this.renderZone();
    this.renderZoneNav();
    markFindingsBadge();
  },

  addFinding(z, check) {
    const exists = Findings.all().some(
      (f) => f.kind === 'sweep' && f.zone === z.name && f.title === check.t
    );
    if (exists) return;
    Findings.add({ kind: 'sweep', zone: z.name, title: check.t, note: check.h });
  },

  removeFinding(z, check) {
    const hit = Findings.all().find(
      (f) => f.kind === 'sweep' && f.zone === z.name && f.title === check.t
    );
    if (hit) Findings.remove(hit.id);
  },

  reset() {
    if (!confirm('Clear all sweep progress? Findings you logged will also be removed.')) return;
    this.state = {};
    Store.set('sweep', {});
    Store.set('sweepZone', 0);
    Findings.all()
      .filter((f) => f.kind === 'sweep')
      .forEach((f) => Findings.remove(f.id));
    this.zone = 0;
    this.renderZoneNav();
    this.renderZone();
    markFindingsBadge();
  }
};

document.addEventListener('DOMContentLoaded', () => {
  if ($('#zoneBody')) Sweep.init();
});
