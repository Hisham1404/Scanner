/* Offline shell.
   You may be doing this in a hotel room on a network you have deliberately not
   joined, or with no signal at all. The tool has to work anyway, so everything
   is cached on first visit and served cache-first afterwards. */

const CACHE = 'scanner-v1';
const ASSETS = [
  'index.html',
  'scan.html',
  'sweep.html',
  'guide.html',
  'legal.html',
  'report.html',
  'css/app.css',
  'js/data.js',
  'js/app.js',
  'js/scanner.js',
  'js/sweep.js',
  'js/report.js',
  'assets/icon.svg',
  'manifest.webmanifest'
];

self.addEventListener('install', (e) => {
  e.waitUntil(
    caches.open(CACHE)
      .then((c) => c.addAll(ASSETS))
      .then(() => self.skipWaiting())
      .catch(() => {})
  );
});

self.addEventListener('activate', (e) => {
  e.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (e) => {
  const req = e.request;
  if (req.method !== 'GET') return;
  const url = new URL(req.url);
  if (url.origin !== location.origin) return;

  e.respondWith(
    caches.match(req).then((hit) => {
      if (hit) return hit;
      return fetch(req)
        .then((res) => {
          if (res && res.ok) {
            const copy = res.clone();
            caches.open(CACHE).then((c) => c.put(req, copy)).catch(() => {});
          }
          return res;
        })
        .catch(() => caches.match('index.html'));
    })
  );
});
