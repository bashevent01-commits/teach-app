// Once a day, the first time a gym owner opens the app: who pays today and who pays tomorrow
(async () => {
  const key = "know_member_due_" + (Session.get()?.user_id || "");
  const day = new Date().toLocaleDateString("en-CA", { timeZone: "Africa/Nairobi" });
  if (localStorage.getItem(key) === day) return;
  let due;
  try { due = await Api.members.due(); } catch (e) { return; }
  if (!due.today.length && !due.tomorrow.length) return;
  localStorage.setItem(key, day);
  const block = (label, list) => !list.length ? "" : `<h3 class="md-title">${label} (${list.length})</h3>${list.slice(0, 8).map((m) => `<p class="md-row">${escapeHtml(m.name)} <span>KES ${Number(m.fee).toLocaleString("en-KE")}</span></p>`).join("")}${list.length > 8 ? `<p class="md-row">and ${list.length - 8} more</p>` : ""}`;
  const el = document.createElement("div");
  el.className = "payflow-backdrop";
  el.innerHTML = `<div class="payflow-card" role="dialog" aria-modal="true"><h2>Members to chase</h2>${block("Due today", due.today)}${block("Due tomorrow", due.tomorrow)}
    <button class="primary-btn" id="mdOpen">Open members</button><button class="ghost-btn" id="mdClose">Close</button></div>`;
  document.body.appendChild(el);
  el.querySelector("#mdOpen").addEventListener("click", () => { location.href = "members.html"; });
  el.querySelector("#mdClose").addEventListener("click", () => el.remove());
})();
