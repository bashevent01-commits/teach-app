const CACHE = "teach-v2";
const ASSETS = [
  "./",
  "./index.html",
  "./login.html",
  "./admin-login.html",
  "./home.html",
  "./news.html",
  "./audit.html",
  "./dashboard.html",
  "./settings.html",
  "./css/styles.css",
  "./js/api.js",
  "./js/ui.js",
  "./js/home.js",
  "./js/audit.js",
  "./js/news.js",
  "./js/dashboard.js",
  "./js/settings.js",
  "./assets/logo.svg",
  "./manifest.webmanifest",
];

self.addEventListener("install", (e) => {
  e.waitUntil(caches.open(CACHE).then((c) => c.addAll(ASSETS)).then(() => self.skipWaiting()));
});

self.addEventListener("activate", (e) => {
  e.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener("fetch", (e) => {
  if (e.request.method !== "GET") return;

  // Never intercept the backend API (a different origin — see API_BASE
  // in js/api.js). Let those requests succeed or fail on their own so a
  // real network error isn't masked by a cached HTML page.
  const url = new URL(e.request.url);
  if (url.origin !== self.location.origin) return;

  e.respondWith(
    caches.match(e.request).then((hit) => hit || fetch(e.request).catch(() => caches.match("./login.html")))
  );
});