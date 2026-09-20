/* Shared plumbing: storage, findings log, nav state. */

const $  = (sel, root = document) => root.querySelector(sel);
const $$ = (sel, root = document) => Array.from(root.querySelectorAll(sel));

const Store = {
  key: (k) => 'scanner:' + k,
  get(k, fallback) {
    try {
      const raw = localStorage.getItem(Store.key(k));
      return raw === null ? fallback : JSON.parse(raw);
    } catch (e) {
      return fallback;
    }
  },
  set(k, v) {
    try { localStorage.setItem(Store.key(k), JSON.stringify(v)); return true; }
    catch (e) { return false; }
  },
  del(k) {
    try { localStorage.removeItem(Store.key(k)); } catch (e) {}
  }
};

/* ---- findings log ----
   Every flagged checkpoint and every scanner hit the user chooses to keep
   lands here. report.html turns it into something you can hand to police. */

const Findings = {
  all() {
    const list = Store.get('findings', []);
    return Array.isArray(list) ? list : [];
  },
  add(entry) {
    const list = Findings.all();
    list.push(Object.assign({
      id: 'f' + Date.now().toString(36) + Math.random().toString(36).slice(2, 6),
      at: new Date().toISOString()
    }, entry));
    Store.set('findings', list);
    return list;
  },
  remove(id) {
    const list = Findings.all().filter((f) => f.id !== id);
    Store.set('findings', list);
    return list;
  },
  clear() { Store.set('findings', []); },
  count() { return Findings.all().length; }
};

function fmtTime(iso) {
  try {
    const d = new Date(iso);
    return d.toLocaleString(undefined, {
      year: 'numeric', month: 'short', day: '2-digit',
      hour: '2-digit', minute: '2-digit'
    });
  } catch (e) { return iso; }
}

/* mark the active tab */
function markNav() {
  const here = location.pathname.split('/').pop() || 'index.html';
  $$('.nav a').forEach((a) => {
    const target = a.getAttribute('href');
    if (target === here) a.setAttribute('aria-current', 'page');
    else a.removeAttribute('aria-current');
  });
}

/* badge the report tab with the finding count */
function markFindingsBadge() {
  const n = Findings.count();
  const tab = $('.nav a[href="report.html"]');
  if (tab) tab.textContent = n > 0 ? 'Report ' + n : 'Report';
}

document.addEventListener('DOMContentLoaded', () => {
  markNav();
  markFindingsBadge();
  if ('serviceWorker' in navigator && location.protocol !== 'file:') {
    navigator.serviceWorker.register('sw.js').catch(() => {});
  }
});
