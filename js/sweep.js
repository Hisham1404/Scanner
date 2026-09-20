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

  total() { return ZONES.reduce((n, z) => n + z.checks.length, 0); },

  answered() { return Object.keys(this.state).length; },

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
      b.type = 'button';
      b.textContent = z.name;
      if (this.zoneDone(z)) b.className = 'is-done';
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
    const done = this.answered();
    const all = this.total();
    $('#progBar').style.width = Math.round((done / all) * 100) + '%';
    $('#progText').textContent = done + ' / ' + all;
  },

  renderZone() {
    const z = ZONES[this.zone];
    const host = $('#zoneBody');
    host.innerHTML = '';

    const head = document.createElement('div');
    head.innerHTML =
      '<p class="label">Zone ' + (this.zone + 1) + ' of ' + ZONES.length + '</p>' +
      '<h1 class="display">' + z.name + '</h1>' +
      '<p class="lead">' + z.blurb + '</p>';
    host.appendChild(head);

    if (z.web === false) {
      const n = document.createElement('div');
      n.className = 'aside aside--caution';
      n.innerHTML =
        '<span class="label">Manual for now</span>' +
        '<p>Wi-Fi scanning, device enumeration and MAC-address lookups are not available to any website on any browser. ' +
        'They are the headline feature of the phone app in the roadmap. Until then, do these by hand &mdash; they are still worth doing.</p>';
      host.appendChild(n);
    }

    const list = document.createElement('div');
    list.className = 'checks';

    z.checks.forEach((c, i) => {
      const cur = this.state[this.key(z.id, i)];

      const row = document.createElement('div');
      row.className = 'check' + (cur === 'flag' ? ' is-flagged' : '');

      const body = document.createElement('div');
      body.innerHTML =
        '<div class="check__t">' + c.t + '</div>' +
        '<div class="check__h">' + c.h + '</div>' +
        (c.c ? '<div class="check__c">' + c.c + '</div>' : '');

      const acts = document.createElement('div');
      acts.className = 'check__acts';

      [['ok', 'Clear', 'Mark clear'], ['flag', 'Flag', 'Flag as suspicious']].forEach(([act, text, aria]) => {
        const b = document.createElement('button');
        b.type = 'button';
        b.dataset.act = act;
        b.textContent = text;
        b.setAttribute('aria-label', aria + ': ' + c.t);
        b.setAttribute('aria-pressed', String(cur === act));
        b.addEventListener('click', () => this.mark(z, i, act));
        acts.appendChild(b);
      });

      row.appendChild(body);
      row.appendChild(acts);
      list.appendChild(row);
    });

    host.appendChild(list);

    $('#btnPrev').disabled = this.zone === 0;
    $('#btnNext').disabled = this.zone === ZONES.length - 1;

    const done = $('#zoneDone');
    if (this.zone === ZONES.length - 1) {
      done.hidden = false;
      done.innerHTML =
        '<div class="aside" style="margin-top:34px">' +
        '<span class="label">That is the full sweep</span>' +
        '<p>You have walked every zone. If you flagged anything, the report turns it into a written record with timestamps, ' +
        'plus a complaint draft you can take to a police station.</p></div>' +
        '<a class="btn btn--primary btn--block" href="report.html">Open the report</a>';
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
    Findings.all().filter((f) => f.kind === 'sweep').forEach((f) => Findings.remove(f.id));
    this.zone = 0;
    this.renderZoneNav();
    this.renderZone();
    markFindingsBadge();
  }
};

document.addEventListener('DOMContentLoaded', () => {
  if ($('#zoneBody')) Sweep.init();
});
