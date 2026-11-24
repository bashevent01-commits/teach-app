(function () {
  const $ = (s) => document.querySelector(s);
  const $$ = (s) => Array.from(document.querySelectorAll(s));
  const esc = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
  const DAY = 86400000;
  const TEMPLATE = [
    ["Cabin", "seats", "Seats, seat pockets and under seats"], ["Cabin", "bins", "Overhead bins and crew stowages"], ["Cabin", "toilets", "Toilets: panels, bins and behind mirrors"],
    ["Cabin", "galleys", "Galleys, catering carts and supplies"], ["Cabin", "crew_rest", "Crew rest areas and stowages"], ["Flight deck", "flight_deck", "Flight deck, when accessible"],
    ["Hold and exterior", "hold", "Cargo hold and bulk areas"], ["Hold and exterior", "exterior", "Landing gear bays and exterior panels"],
    ["Access", "doors", "Doors and access points secured"], ["Access", "persons_items", "No unauthorised persons or unattended items found"],
  ];
  const LABEL = Object.fromEntries(TEMPLATE.map((t) => [t[1], t[2]]));
  const CATS = { staff_training: "Staff training", background_check: "Background check", handler_authorisation: "Handler authorisation", security_programme: "Security programme", equipment: "Equipment", station_clearance: "Station clearance", other: "Other" };
  const STATUS = { pending: ["Awaiting review", "badge-draft"], approved: ["Approved", "badge-finalized"], rejected: ["Rejected", "badge-inactive"] };
  const state = { role: "hq", online: true, view: "dashboard", certs: [], checks: [], queue: [], nextCheck: 1, nextCert: 1 };

  const fmtTime = (d) => new Date(d).toLocaleString("en-KE", { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit", timeZone: "Africa/Nairobi" });
  const fmtDay = (d) => new Date(d).toLocaleDateString("en-KE", { day: "numeric", month: "short", year: "numeric", timeZone: "Africa/Nairobi" });
  const todayKey = () => new Date().toLocaleDateString("en-CA", { timeZone: "Africa/Nairobi" });
  const dayKey = (d) => new Date(d).toLocaleDateString("en-CA", { timeZone: "Africa/Nairobi" });

  function toast(msg) {
    const el = $("#toast");
    el.textContent = msg; el.hidden = false;
    clearTimeout(toast.t); toast.t = setTimeout(() => { el.hidden = true; }, 3200);
  }
  function openSheet(title, html) { $("#sheetTitle").textContent = title; $("#sheetBody").innerHTML = html; $("#sheetBackdrop").hidden = false; }
  function closeSheet() { $("#sheetBackdrop").hidden = true; }
  $("#sheetClose").addEventListener("click", closeSheet);
  $("#sheetBackdrop").addEventListener("click", (e) => { if (e.target.id === "sheetBackdrop") closeSheet(); });
  document.addEventListener("keydown", (e) => { if (e.key === "Escape") closeSheet(); });

  // ---------- hashing ----------
  async function sha(text) {
    const buf = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
    return Array.from(new Uint8Array(buf)).map((b) => b.toString(16).padStart(2, "0")).join("");
  }
  const canon = (c) => JSON.stringify({ tail: c.tail, flight: c.flight, station: c.station, type: c.type, items: c.items, remarks: c.remarks, signed: c.signed, at: c.at });
  async function seal(c) {
    const last = state.checks[state.checks.length - 1];
    c.prev = last ? last.hash : "";
    c.hash = await sha(c.prev + canon(c));
  }

  function sigSvg(seed) {
    let h = 0; for (const ch of seed) h = (h * 31 + ch.charCodeAt(0)) >>> 0;
    const r = (n) => { h = (h * 1664525 + 1013904223) >>> 0; return h % n; };
    let d = `M10 ${60 + r(20)}`;
    for (let i = 0; i < 7; i++) d += ` q ${12 + r(14)} ${-30 + r(60)} ${18 + r(10)} ${-10 + r(20)}`;
    const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 300 100"><path d="${d}" fill="none" stroke="#1c1517" stroke-width="3" stroke-linecap="round"/></svg>`;
    return "data:image/svg+xml;utf8," + encodeURIComponent(svg);
  }

  // ---------- seed data ----------
  const certSeed = [
    ["staff_training", "AVSEC awareness training", "Officer 014", "NBO", -12, "KCAA"], ["background_check", "Background check clearance", "Officer 022", "MBA", 6, "Security vetting"],
    ["handler_authorisation", "Ground handler authorisation", "Sample Ground Handler Ltd", "NBO", 14, "KCAA"], ["staff_training", "Recurrent security training", "Officer 031", "KSM", 22, "KQ Training"],
    ["handler_authorisation", "Catering supplier screening approval", "Demo Catering Co", "MBA", 29, "KCAA"], ["equipment", "Hand-held metal detector calibration", "Detector HHMD-07", "EDL", 41, "Approved vendor"],
    ["staff_training", "AVSEC awareness training", "Officer 009", "NBO", 55, "KCAA"], ["station_clearance", "Station security clearance", "Kisumu station", "KSM", 80, "KCAA"],
    ["security_programme", "Operator security programme, current revision", "KQ HQ", null, 120, "KCAA"], ["background_check", "Background check clearance", "Officer 040", "NBO", 200, "Security vetting"],
    ["staff_training", "Aircraft search procedures course", "Officer 017", "MBA", 300, "KQ Training"], ["security_programme", "Security programme amendment", "KQ HQ", null, null, "KCAA"],
  ];
  async function seedData() {
    state.certs = certSeed.map((c, i) => ({ id: i + 1, category: c[0], title: c[1], holder: c[2], station: c[3], expires: c[4] === null ? null : Date.now() + c[4] * DAY, issuer: c[5], active: true, history: [{ what: "Created", by: "HQ compliance", at: Date.now() - 40 * DAY }] }));
    state.nextCert = state.certs.length + 1;
    state.checks = []; state.queue = []; state.nextCheck = 1;
    const ok = () => TEMPLATE.map((t) => ({ key: t[1], ok: true, note: null }));
    const withIssue = (key, note) => ok().map((i) => (i.key === key ? { key, ok: false, note } : i));
    const rows = [
      ["5Y-KZA", "KQ100", "NBO", "check", ok(), "Amina W.", 2.0, "approved"], ["5Y-KZB", "KQ310", "KSM", "check", ok(), "Peter O.", 3.5, "approved"],
      ["5Y-KYA", "KQ202", "MBA", "search", withIssue("toilets", "Loose panel in aft toilet, reported to engineering"), "Salim K.", 4.5, "pending"],
      ["5Y-KZC", "KQ412", "NBO", "check", ok(), "James M.", 5.5, "pending"], ["5Y-KZD", "KQ604", "EDL", "check", ok(), "Grace N.", 7.0, "pending"],
      ["5Y-KYB", "KQ118", "NBO", "check", withIssue("persons_items", "Unattended bag in row 14, removed and logged"), "James M.", 9.0, "rejected"],
      ["5Y-KZE", "KQ320", "KSM", "check", ok(), "Peter O.", 27.0, "approved"], ["5Y-KZA", "KQ101", "MBA", "check", ok(), "Salim K.", 28.5, "approved"],
    ].reverse();
    for (const r of rows) {
      const at = Date.now() - r[6] * 3600000;
      const c = { id: state.nextCheck++, tail: r[0], flight: r[1], station: r[2], type: r[3], items: r[4], remarks: null, signed: r[5], at, status: "pending", reviewedBy: null, reviewNote: null, performed: null, events: [{ what: "Submitted and signed", by: r[5], at }] };
      c.result = c.items.every((i) => i.ok) ? "clear" : "issue";
      await seal(c);
      if (r[7] !== "pending") {
        c.status = r[7]; c.reviewedBy = "HQ compliance"; c.reviewNote = r[7] === "rejected" ? "Re-check the cabin and attach the incident note" : null;
        c.events.push({ what: r[7] === "approved" ? "Approved" : "Rejected", by: "HQ compliance", at: at + 1800000 });
      }
      state.checks.push(c);
    }
  }

  // ---------- helpers ----------
  function certState(c) {
    if (!c.active) return ["withdrawn", null];
    if (c.expires === null) return ["no_expiry", null];
    const days = Math.ceil((c.expires - Date.now()) / DAY);
    if (days < 0) return ["expired", days]; if (days <= 15) return ["critical", days]; if (days <= 30) return ["warning", days]; if (days <= 60) return ["notice", days];
    return ["valid", days];
  }
  function certBadge(c) {
    const [s, d] = certState(c);
    if (s === "no_expiry") return ["No expiry", "mem-ok"];
    if (s === "expired") return [`Expired ${-d} day${d === -1 ? "" : "s"} ago`, "mem-overdue"];
    if (s === "critical") return [d === 0 ? "Expires today" : `${d} day${d === 1 ? "" : "s"} left`, "mem-overdue"];
    if (s === "warning") return [`${d} days left`, "mem-today"];
    if (s === "notice") return [`${d} days left`, "mem-tomorrow"];
    return [fmtDay(c.expires), "mem-ok"];
  }
  const resultBadge = (c) => c.result === "clear" ? `<span class="badge badge-active">Clear</span>` : `<span class="badge badge-inactive">${c.items.filter((i) => !i.ok).length} issue${c.items.filter((i) => !i.ok).length === 1 ? "" : "s"}</span>`;
  const statusBadge = (c) => `<span class="badge ${STATUS[c.status][1]}">${STATUS[c.status][0]}</span>`;
  const visibleChecks = () => (state.role === "officer" ? state.checks.filter((c) => c.station === "NBO") : state.checks);

  // ---------- navigation ----------
  const NAV = [["dashboard", "Overview"], ["certificates", "Certificates"], ["checks", "Flight checks"]];
  function renderNav() {
    $("#sideNav").innerHTML = NAV.map(([k, l]) => `<a class="side-link ${state.view === k ? "is-active" : ""}" href="#${k}" data-view="${k}">${l}</a>`).join("");
    $("#tabbar").style.gridTemplateColumns = `repeat(${NAV.length}, 1fr)`;
    $("#tabbar").innerHTML = NAV.map(([k, l]) => `<a class="tab ${state.view === k ? "is-active" : ""}" href="#${k}" data-view="${k}">${l}</a>`).join("");
  }
  function show(view) {
    state.view = NAV.some((n) => n[0] === view) ? view : "dashboard";
    $$(".page").forEach((p) => p.classList.toggle("is-active", p.dataset.page === state.view));
    renderNav(); window.scrollTo(0, 0);
  }
  window.addEventListener("hashchange", () => show(location.hash.slice(1)));

  // ---------- dashboard ----------
  function drawDashboard() {
    const today = todayKey(), all = visibleChecks();
    const todays = all.filter((c) => dayKey(c.at) === today).length;
    const pending = all.filter((c) => c.status === "pending").length;
    const issues = all.filter((c) => c.result === "issue" && c.status === "pending").length;
    const expiring = state.certs.filter((c) => ["expired", "critical", "warning"].includes(certState(c)[0])).length;
    $("#kpis").innerHTML = [[todays, "Checks today", ""], [pending, "Awaiting review", "mem-today"], [issues, "Open issues", "mem-overdue"], [expiring, "Certificates need action", "mem-overdue"]]
      .map(([n, l, cls]) => `<div class="mem-counter"><strong class="${n && cls ? cls : ""}">${n}</strong><small>${l}</small></div>`).join("");
    const by = {};
    all.filter((c) => dayKey(c.at) === today).forEach((c) => { by[c.station] = by[c.station] || { n: 0, i: 0 }; by[c.station].n++; if (c.result === "issue") by[c.station].i++; });
    const max = Math.max(1, ...Object.values(by).map((v) => v.n));
    $("#stationBars").innerHTML = Object.keys(by).length ? Object.keys(by).sort().map((s) => `<div class="demo-bar-row"><strong>${esc(s)}</strong><div class="demo-bar-track"><i style="width:${((by[s].n - by[s].i) / max) * 100}%"></i><i class="issue" style="width:${(by[s].i / max) * 100}%"></i></div><span>${by[s].n}${by[s].i ? ` (${by[s].i} issue)` : ""}</span></div>`).join("") : `<p class="subtle">No checks signed today yet.</p>`;
    const urgentCerts = state.certs.filter((c) => ["expired", "critical", "warning"].includes(certState(c)[0])).sort((a, b) => certState(a)[1] - certState(b)[1]).slice(0, 4);
    const urgentChecks = all.filter((c) => c.status === "pending" && c.result === "issue").slice(0, 3);
    const rows = [...urgentChecks.map((c) => `<button class="demo-attn" data-open-check="${c.id}"><span><strong>${esc(c.tail)} at ${esc(c.station)}</strong><small>Check with issues waiting for review</small></span><span class="badge badge-inactive">Review</span></button>`),
      ...urgentCerts.map((c) => { const [t, cls] = certBadge(c); return `<button class="demo-attn" data-goto="certificates"><span><strong>${esc(c.title)}</strong><small>${esc(c.holder)}</small></span><span class="${cls}"><strong>${t}</strong></span></button>`; })];
    $("#attention").innerHTML = rows.length ? rows.join("") : `<p class="subtle">Nothing needs attention.</p>`;
    $$("[data-open-check]").forEach((b) => b.addEventListener("click", () => { location.hash = "checks"; openDetail(Number(b.dataset.openCheck)); }));
    $$("[data-goto]").forEach((b) => b.addEventListener("click", () => { location.hash = b.dataset.goto; }));
    $("#tamperBtn").hidden = state.role !== "hq"; $("#verifyBtn").hidden = state.role !== "hq";
  }

  async function verifyChain() {
    let prev = "";
    for (let i = 0; i < state.checks.length; i++) {
      const c = state.checks[i];
      if (c.prev !== prev || (await sha(c.prev + canon(c))) !== c.hash) { return { ok: false, count: i + 1, id: c.id }; }
      prev = c.hash;
    }
    return { ok: true, count: state.checks.length };
  }
  $("#verifyBtn").addEventListener("click", async () => {
    const r = await verifyChain(); const el = $("#verifyResult");
    el.className = "demo-verify " + (r.ok ? "ok" : "bad");
    el.textContent = r.ok ? `Records intact: all ${r.count} signed checks verified.` : `Record #${r.id} no longer matches what was signed. Every record from #${r.id} onward cannot be trusted.`;
  });
  $("#tamperBtn").addEventListener("click", () => {
    const target = state.checks.find((c) => c.result === "clear" && c.status === "approved");
    if (!target) return;
    target.items = target.items.map((i, n) => (n === 2 ? { ...i, ok: false, note: "edited afterwards" } : i));
    toast(`Record #${target.id} was edited behind the scenes. Now press Verify records.`);
    drawAll();
  });

  // ---------- certificates ----------
  function drawCerts() {
    const count = (s) => state.certs.filter((c) => certState(c)[0] === s).length;
    $("#certCounters").innerHTML = [["expired", "Expired", "mem-overdue"], ["critical", "15 days", "mem-overdue"], ["warning", "30 days", "mem-today"], ["notice", "60 days", "mem-tomorrow"]]
      .map(([s, l, cls]) => `<button type="button" class="mem-counter" data-cfilter="${s}"><strong class="${count(s) ? cls : ""}">${count(s)}</strong><small>${l}</small></button>`).join("");
    $$("[data-cfilter]").forEach((b) => b.addEventListener("click", () => { $("#certFilter").value = b.dataset.cfilter; drawCerts(); }));
    const f = $("#certFilter").value, q = $("#certSearch").value.trim().toLowerCase();
    const rows = state.certs.filter((c) => (f === "all" || certState(c)[0] === f) && (!q || [c.title, c.holder, c.station || ""].some((v) => v.toLowerCase().includes(q))))
      .sort((a, b) => (a.expires ?? Infinity) - (b.expires ?? Infinity));
    $("#certBody").innerHTML = rows.length ? rows.map((c) => { const [t, cls] = certBadge(c); return `<tr><td><strong>${esc(c.title)}</strong><br><small>${esc(c.holder)}</small></td><td>${esc(CATS[c.category])}</td><td>${esc(c.station || "HQ")}</td><td class="${cls}"><strong>${t}</strong>${c.expires ? `<br><small>${fmtDay(c.expires)}</small>` : ""}</td><td><button class="primary-btn" data-renew="${c.id}">Renew</button> <button class="ghost-btn" data-chist="${c.id}">History</button></td></tr>`; }).join("") : `<tr class="empty-row"><td colspan="5">Nothing matches.</td></tr>`;
    $$("[data-renew]").forEach((b) => b.addEventListener("click", () => {
      const c = state.certs.find((x) => x.id === Number(b.dataset.renew));
      c.expires = Date.now() + 365 * DAY; c.history.unshift({ what: "Renewed for one year", by: state.role === "hq" ? "HQ compliance" : "Officer (NBO)", at: Date.now() });
      toast("Renewed. The new expiry is a year from today."); drawAll();
    }));
    $$("[data-chist]").forEach((b) => b.addEventListener("click", () => {
      const c = state.certs.find((x) => x.id === Number(b.dataset.chist));
      openSheet("History", `<p class="subtle">${esc(c.title)} for ${esc(c.holder)}</p>` + c.history.map((h) => `<div class="ex-row"><div><strong>${esc(h.what)}</strong><small>${esc(h.by)} · ${fmtTime(h.at)}</small></div></div>`).join(""));
    }));
  }
  $("#certSearch").addEventListener("input", drawCerts);
  $("#certFilter").addEventListener("change", drawCerts);
  $("#addCertBtn").addEventListener("click", () => {
    openSheet("Add a certificate", `<form id="addCertForm">
      <label class="field"><span>Document title</span><input type="text" id="acTitle" required value="Aviation security awareness certificate" /></label>
      <label class="field"><span>Holder (person, company or aircraft)</span><input type="text" id="acHolder" required placeholder="Officer 052" /></label>
      <label class="field"><span>Category</span><select id="acCat">${Object.entries(CATS).map(([k, v]) => `<option value="${k}">${v}</option>`).join("")}</select></label>
      <label class="field"><span>Station</span><input type="text" id="acStation" placeholder="NBO" /></label>
      <label class="field"><span>Expires on</span><input type="date" id="acExp" required /></label>
      <div class="form-actions"><button class="primary-btn" type="submit">Add certificate</button></div></form>`);
    $("#addCertForm").addEventListener("submit", (e) => {
      e.preventDefault();
      state.certs.push({ id: state.nextCert++, category: $("#acCat").value, title: $("#acTitle").value.trim(), holder: $("#acHolder").value.trim(), station: $("#acStation").value.trim().toUpperCase() || null, expires: new Date($("#acExp").value + "T00:00:00").getTime(), issuer: null, active: true, history: [{ what: "Created", by: state.role === "hq" ? "HQ compliance" : "Officer (NBO)", at: Date.now() }] });
      closeSheet(); toast("Certificate added"); drawAll();
    });
  });

  // ---------- checks ----------
  function drawChecks() {
    const all = visibleChecks(), today = todayKey();
    const n = (fn) => all.filter(fn).length;
    $("#chkCounters").innerHTML = [["", "Today", n((c) => dayKey(c.at) === today), ""], ["pending", "To review", n((c) => c.status === "pending"), "mem-today"], ["issue", "Open issues", n((c) => c.result === "issue" && c.status === "pending"), "mem-overdue"]]
      .map(([f, l, v, cls]) => `<button type="button" class="mem-counter" data-cf="${f}"><strong class="${v && cls ? cls : ""}">${v}</strong><small>${l}</small></button>`).join("");
    $$("[data-cf]").forEach((b) => b.addEventListener("click", () => { $("#chkFilter").value = b.dataset.cf || "all"; drawChecks(); }));
    const by = {}; all.filter((c) => dayKey(c.at) === today).forEach((c) => { by[c.station] = by[c.station] || { n: 0, i: 0 }; by[c.station].n++; if (c.result === "issue") by[c.station].i++; });
    $("#chkStations").innerHTML = Object.keys(by).length ? Object.keys(by).sort().map((s) => `<span class="chk-chip ${by[s].i ? "has-issue" : ""}">${esc(s)}: ${by[s].n} today${by[s].i ? `, ${by[s].i} with issues` : ""}</span>`).join("") : `<span class="subtle">No checks signed today yet.</span>`;
    $("#newCheckBtn").hidden = state.role !== "officer";
    const f = $("#chkFilter").value, q = $("#chkSearch").value.trim().toLowerCase();
    const rows = all.filter((c) => (f === "all" || (f === "issue" ? c.result === "issue" : c.status === f)) && (!q || [c.tail, c.flight, c.station, c.signed].some((v) => v.toLowerCase().includes(q)))).slice().reverse();
    const waiting = state.role === "officer" ? state.queue.map((w) => `<tr class="chk-waiting"><td><strong>${esc(w.tail)}</strong><br><small>${esc(w.flight || "No flight")} · ${w.type === "search" ? "Search" : "Check"}</small></td><td>${esc(w.station)}</td><td>${w.items.every((i) => i.ok) ? `<span class="badge badge-active">Clear</span>` : `<span class="badge badge-inactive">Issues</span>`}</td><td><span class="badge badge-draft">Waiting to upload</span></td><td>${esc(w.signed)}<br><small>${fmtTime(w.at)}</small></td><td></td></tr>`).join("") : "";
    $("#chkBody").innerHTML = waiting + (rows.length ? rows.map((c) => `<tr><td><strong>${esc(c.tail)}</strong><br><small>${esc(c.flight || "No flight")} · ${c.type === "search" ? "Search" : "Check"}</small></td><td>${esc(c.station)}</td><td>${resultBadge(c)}</td><td>${statusBadge(c)}</td><td>${esc(c.signed)}<br><small>${fmtTime(c.at)}</small></td><td><button class="ghost-btn" data-open="${c.id}">Open</button></td></tr>`).join("") : (waiting ? "" : `<tr class="empty-row"><td colspan="6">Nothing matches.</td></tr>`));
    $$("[data-open]").forEach((b) => b.addEventListener("click", () => openDetail(Number(b.dataset.open))));
  }
  $("#chkSearch").addEventListener("input", drawChecks);
  $("#chkFilter").addEventListener("change", drawChecks);

  function openDetail(id) {
    const c = state.checks.find((x) => x.id === id); if (!c) return;
    c.events.push({ what: "Viewed", by: state.role === "hq" ? "HQ compliance" : "Officer (NBO)", at: Date.now() });
    const items = c.items.map((i) => `<div class="ex-row"><div><strong>${esc(LABEL[i.key])}</strong>${i.note ? `<small>${esc(i.note)}</small>` : ""}</div><span class="badge ${i.ok ? "badge-active" : "badge-inactive"}">${i.ok ? "OK" : "Issue"}</span></div>`).join("");
    const review = c.status === "pending" ? (state.role === "hq" ? `<label class="field"><span>Review note (needed to reject)</span><input type="text" id="rvNote" maxlength="300" /></label><div class="form-actions"><button class="primary-btn" id="rvApprove">Approve</button><button class="ghost-btn" id="rvReject">Reject</button></div>` : `<p class="subtle">Waiting for HQ review.</p>`)
      : `<p class="subtle">${c.status === "approved" ? "Approved" : "Rejected"} by ${esc(c.reviewedBy)}${c.reviewNote ? ": " + esc(c.reviewNote) : ""}</p>`;
    openSheet(`${c.tail} at ${c.station}`, `<p>${resultBadge(c)} ${statusBadge(c)}</p>
      <p class="subtle">${esc(c.flight || "No flight number")} · ${c.type === "search" ? "Security search" : "Security check"}</p>${items}
      ${c.remarks ? `<p class="subtle">Remarks: ${esc(c.remarks)}</p>` : ""}
      <p class="chk-group">Signature</p><img class="chk-sig-img" src="${c.sigImg || sigSvg(c.signed + c.id)}" alt="Signature of ${esc(c.signed)}" />
      <p class="subtle">${esc(c.signed)} · ${fmtTime(c.at)}${c.performed ? ` (done ${fmtTime(c.performed)} offline)` : ""}</p>
      <p class="chk-group">Record seal</p><p class="chk-hash">${c.hash}</p>
      <p class="chk-group">Review</p>${review}
      <p class="chk-group">History</p>${c.events.slice().reverse().map((e) => `<div class="ex-row"><div><strong>${esc(e.what)}</strong><small>${esc(e.by)} · ${fmtTime(e.at)}</small></div></div>`).join("")}`);
    const decide = (decision) => {
      const note = $("#rvNote").value.trim();
      if (decision === "reject" && !note) { toast("Say why you are rejecting it."); return; }
      c.status = decision === "approve" ? "approved" : "rejected"; c.reviewedBy = "HQ compliance"; c.reviewNote = note || null;
      c.events.push({ what: decision === "approve" ? "Approved" : "Rejected", by: "HQ compliance", at: Date.now() });
      closeSheet(); toast(decision === "approve" ? "Check approved" : "Check rejected"); drawAll();
    };
    $("#rvApprove")?.addEventListener("click", () => decide("approve"));
    $("#rvReject")?.addEventListener("click", () => decide("reject"));
  }

  $("#newCheckBtn").addEventListener("click", () => {
    let last = "", html = "";
    TEMPLATE.forEach((t) => {
      if (t[0] !== last) { html += `<p class="chk-group">${esc(t[0])}</p>`; last = t[0]; }
      html += `<div class="chk-item" data-key="${t[1]}"><p>${esc(t[2])}</p><div class="chk-choice"><label data-v="ok"><input type="radio" name="i_${t[1]}" value="ok" />OK</label><label data-v="issue"><input type="radio" name="i_${t[1]}" value="issue" />Issue</label></div><input type="text" class="chk-note" maxlength="200" placeholder="What did you find?" hidden /></div>`;
    });
    openSheet("New security check", `<p class="form-error" id="chkMsg"></p><form id="chkForm">
      <label class="field"><span>Aircraft registration</span><input type="text" id="kTail" required maxlength="12" value="5Y-KZF" autocapitalize="characters" /></label>
      <label class="field"><span>Flight number (optional)</span><input type="text" id="kFlight" maxlength="12" value="KQ104" autocapitalize="characters" /></label>
      <label class="field"><span>Station</span><input type="text" id="kStation" value="NBO" readonly /></label>
      <label class="field"><span>Type</span><select id="kType"><option value="check">Security check</option><option value="search">Security search</option></select></label>
      <button type="button" class="ghost-btn" id="kAllOk">Mark everything OK</button>${html}
      <div class="field"><span>Sign here</span><canvas id="kSig" class="sig-pad" width="600" height="160"></canvas><button type="button" class="ghost-btn" id="kSigClear">Clear signature</button></div>
      <p class="file-hint">${state.online ? "Signing as Officer (NBO). Once submitted the check cannot be changed." : "No signal: the check will be saved on this device and uploaded when the signal returns."}</p>
      <div class="form-actions"><button type="submit" class="primary-btn">Sign and submit</button></div></form>`);
    const setChoice = (row, v) => { row.dataset.value = v; row.querySelectorAll(".chk-choice label").forEach((l) => { l.className = l.dataset.v === v ? (v === "ok" ? "on-ok" : "on-issue") : ""; }); row.querySelector(`input[value="${v}"]`).checked = true; row.querySelector(".chk-note").hidden = v !== "issue"; };
    $$(".chk-item").forEach((row) => row.querySelectorAll("input[type=radio]").forEach((r) => r.addEventListener("change", () => setChoice(row, r.value))));
    $("#kAllOk").addEventListener("click", () => $$(".chk-item").forEach((row) => setChoice(row, "ok")));
    const canvas = $("#kSig"), ctx = canvas.getContext("2d");
    ctx.lineWidth = 3; ctx.lineCap = "round"; ctx.lineJoin = "round"; ctx.strokeStyle = "#111"; ctx.fillStyle = "#fff"; ctx.fillRect(0, 0, 600, 160);
    let drawing = false, inked = false;
    const pos = (e) => { const r = canvas.getBoundingClientRect(); return [(e.clientX - r.left) * 600 / r.width, (e.clientY - r.top) * 160 / r.height]; };
    canvas.addEventListener("pointerdown", (e) => { drawing = true; canvas.setPointerCapture(e.pointerId); const [x, y] = pos(e); ctx.beginPath(); ctx.moveTo(x, y); ctx.lineTo(x + 0.1, y + 0.1); ctx.stroke(); inked = true; });
    canvas.addEventListener("pointermove", (e) => { if (!drawing) return; const [x, y] = pos(e); ctx.lineTo(x, y); ctx.stroke(); });
    ["pointerup", "pointercancel"].forEach((ev) => canvas.addEventListener(ev, () => { drawing = false; }));
    $("#kSigClear").addEventListener("click", () => { ctx.fillStyle = "#fff"; ctx.fillRect(0, 0, 600, 160); inked = false; });
    $("#chkForm").addEventListener("submit", async (e) => {
      e.preventDefault();
      const items = [];
      for (const row of $$(".chk-item")) {
        const v = row.dataset.value;
        if (!v) { $("#chkMsg").textContent = "Answer every checklist item."; return; }
        const note = row.querySelector(".chk-note").value.trim();
        if (v === "issue" && !note) { $("#chkMsg").textContent = "Add a note for every item marked as an issue."; return; }
        items.push({ key: row.dataset.key, ok: v === "ok", note: v === "issue" ? note : null });
      }
      if (!inked) { $("#chkMsg").textContent = "Sign in the box before submitting."; return; }
      const rec = { tail: $("#kTail").value.trim().toUpperCase(), flight: $("#kFlight").value.trim().toUpperCase() || null, station: "NBO", type: $("#kType").value, items, remarks: null, signed: "Officer (NBO)", at: Date.now(), sigImg: canvas.toDataURL("image/png") };
      closeSheet();
      if (!state.online) { state.queue.push(rec); toast("No signal. Check saved on this device and will upload when the signal returns."); drawAll(); return; }
      await submitCheck(rec); toast("Check signed and submitted"); drawAll();
    });
  });

  async function submitCheck(rec) {
    const c = { id: state.nextCheck++, ...rec, result: rec.items.every((i) => i.ok) ? "clear" : "issue", status: "pending", reviewedBy: null, reviewNote: null, performed: rec.performedAt || null, events: [] };
    c.at = Date.now(); c.events.push({ what: "Submitted and signed", by: c.signed, at: c.at });
    await seal(c); state.checks.push(c);
  }
  async function flushQueue() {
    if (!state.queue.length) return;
    const waiting = state.queue.splice(0);
    for (const w of waiting) await submitCheck({ ...w, performedAt: w.at });
    toast(`${waiting.length} saved check${waiting.length === 1 ? "" : "s"} uploaded`); drawAll();
  }

  // ---------- controls ----------
  $("#demoRole").addEventListener("change", (e) => { state.role = e.target.value; $("#verifyResult").textContent = ""; drawAll(); });
  $("#demoSignal").addEventListener("click", async () => {
    state.online = !state.online;
    $("#demoSignal").textContent = "Signal: " + (state.online ? "on" : "off"); $("#demoSignal").setAttribute("aria-pressed", String(state.online));
    toast(state.online ? "Signal restored" : "Signal lost. Switch to Station officer and try a new check.");
    if (state.online) await flushQueue(); else drawAll();
  });
  $("#demoReset").addEventListener("click", async () => { await seedData(); state.online = true; $("#demoSignal").textContent = "Signal: on"; $("#verifyResult").textContent = ""; toast("Demo reset"); drawAll(); });

  function drawAll() { drawDashboard(); drawCerts(); drawChecks(); }
  (async function init() { await seedData(); show(location.hash.slice(1)); drawAll(); })();
})();
