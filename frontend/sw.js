const CACHE = "teach-v3";
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
  "./accounts.html",
  "./activity.html",
  "./review.html",
  "./schools.html",
  "./config.js",
  "./css/styles.css",
  "./js/api.js",
  "./js/ui.js",
  "./js/home.js",
  "./js/audit.js",
  "./js/news.js",
  "./js/dashboard.js",
  "./js/settings.js",
  "./js/accounts.js",
  "./js/activity.js",
  "./js/review.js",
  "./js/schools.js",
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

  const url = new URL(e.request.url);
  if (url.origin !== self.location.origin) return;

  e.respondWith(
    caches.match(e.request).then((hit) => {
      if (hit) return hit;
      return fetch(e.request)
        .then((res) => {
          if (res.ok) {
            const copy = res.clone();
            caches.open(CACHE).then((c) => c.put(e.request, copy));
          }
          return res;
        })
        .catch(() => caches.match("./login.html"));
    })
  );
});