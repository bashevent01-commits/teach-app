// Uploads signed checks that were saved offline, from whichever aviation page is open
(function syncWaitingChecks() {
  const run = async () => {
    if (!navigator.onLine || typeof Offline === "undefined" || !Offline.syncPendingChecks) return;
    try { const r = await Offline.syncPendingChecks(); if (r.synced) toast(`${r.synced} saved check${r.synced === 1 ? "" : "s"} uploaded`); } catch (e) { /* retried on the next page or when back online */ }
  };
  const start = () => { run(); window.addEventListener("online", run); };
  if (typeof Offline !== "undefined") return start();
  const loader = document.createElement("script");
  loader.src = "./js/offline.js";
  loader.onload = start;
  document.head.appendChild(loader);
})();

// Once a day, the first time someone opens the app: certificates that are expired or close to expiry
(async () => {
  const key = "know_cert_due_" + (Session.get()?.user_id || "");
  const day = new Date().toLocaleDateString("en-CA", { timeZone: "Africa/Nairobi" });
  if (localStorage.getItem(key) === day) return;
  let sum;
  try { sum = await Api.certificates.summary(); } catch (e) { return; }
  if (!sum.urgent.length) return;
  localStorage.setItem(key, day);
  const left = (c) => c.days_to_expiry < 0 ? `expired ${-c.days_to_expiry}d ago` : c.days_to_expiry === 0 ? "expires today" : `${c.days_to_expiry}d left`;
  const rows = sum.urgent.slice(0, 8).map((c) => `<p class="md-row">${escapeHtml(c.title)} <span>${left(c)}</span></p>`).join("");
  const more = sum.urgent.length > 8 ? `<p class="md-row">and ${sum.urgent.length - 8} more</p>` : "";
  const el = document.createElement("div");
  el.className = "payflow-backdrop";
  el.innerHTML = `<div class="payflow-card" role="dialog" aria-modal="true"><h2>Certificates needing action</h2>${rows}${more}
    <button class="primary-btn" id="cdOpen">Open certificates</button><button class="ghost-btn" id="cdClose">Close</button></div>`;
  document.body.appendChild(el);
  el.querySelector("#cdOpen").addEventListener("click", () => { location.href = "certificates.html"; });
  el.querySelector("#cdClose").addEventListener("click", () => el.remove());
})();
