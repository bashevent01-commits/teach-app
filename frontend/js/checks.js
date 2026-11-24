(async function () {
  const session = await initShell("checks", ["staff", "institution_admin"]);
  const isHq = session.role === "institution_admin";
  let checks = [];
  let template = [];
  let waiting = [];
  const niceTime = (iso) => new Date(iso).toLocaleString("en-KE", { day: "numeric", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit", timeZone: "Africa/Nairobi" });
  const ACTIONS = { flight_check_submitted: "Submitted and signed", flight_check_viewed: "Viewed", flight_check_approved: "Approved", flight_check_rejected: "Rejected" };
  const STATUS = { pending: ["Awaiting review", "badge-draft"], approved: ["Approved", "badge-finalized"], rejected: ["Rejected", "badge-inactive"] };
  const resultBadge = (c) => c.result === "clear" ? `<span class="badge badge-active">Clear</span>` : `<span class="badge badge-inactive">${c.issue_count} issue${c.issue_count === 1 ? "" : "s"}</span>`;
  const statusBadge = (c) => `<span class="badge ${STATUS[c.status][1]}">${STATUS[c.status][0]}</span>`;

  if (!isHq) $("#newCheckBtn").hidden = false; else $("#verifyBtn").hidden = false;
  $("#newCheckBtn").addEventListener("click", openForm);
  $("#verifyBtn").addEventListener("click", verifyChain);
  $("#chkSearch").addEventListener("input", draw);
  $("#chkFilter").addEventListener("change", draw);
  window.addEventListener("online", () => { syncWaiting(); });
  await load();
  onFreshData(load);
  syncWaiting();

  async function syncWaiting() {
    if (isHq || !navigator.onLine) return;
    try {
      const r = await Offline.syncPendingChecks();
      if (r.synced) { toast(`${r.synced} saved check${r.synced === 1 ? "" : "s"} uploaded`); await load(); }
      if (r.failed) toast(`${r.failed} saved check${r.failed === 1 ? " was" : "s were"} rejected by the server`);
    } catch (err) { /* stays queued */ }
  }

  async function load() {
    try {
      const [rows, sum] = await Promise.all([Api.flightChecks.list(), Api.flightChecks.summary()]);
      checks = rows;
      drawSummary(sum);
      await Offline.cacheSet("flight_checks", rows);
      await Offline.cacheSet("flight_summary", sum);
    } catch (err) {
      if (err.status === 0) {
        checks = (await Offline.cacheGet("flight_checks")) || [];
        const sum = await Offline.cacheGet("flight_summary");
        if (sum) drawSummary(sum);
      } else toast(err.message || "Could not load the checks.");
    }
    waiting = isHq ? [] : await Offline.listPendingChecks();
    draw();
  }

  function drawSummary(sum) {
    $("#chkCounters").innerHTML = [["", "Today", sum.today, ""], ["pending", "To review", sum.pending, "mem-today"], ["issue", "Open issues", sum.issues_open, "mem-overdue"]]
      .map(([f, label, n, cls]) => `<button type="button" class="mem-counter" data-filter="${f}"><strong class="${n && cls ? cls : ""}">${n}</strong><small>${label}</small></button>`).join("");
    $$(".mem-counter").forEach((b) => b.addEventListener("click", () => { $("#chkFilter").value = b.dataset.filter || "all"; draw(); }));
    $("#chkStations").innerHTML = sum.stations.length ? sum.stations.map((s) => `<span class="chk-chip ${s.issues ? "has-issue" : ""}">${escapeHtml(s.station)}: ${s.checks} today${s.issues ? `, ${s.issues} with issues` : ""}</span>`).join("") : `<span class="subtle">No checks signed today yet.</span>`;
  }

  function draw() {
    const filter = $("#chkFilter").value;
    const q = $("#chkSearch").value.trim().toLowerCase();
    const rows = checks.filter((c) => (filter === "all" || (filter === "issue" ? c.result === "issue" : c.status === filter)) && (!q || [c.tail_number, c.flight_no || "", c.station, c.signed_name].some((v) => v.toLowerCase().includes(q))));
    const waitingRows = waiting.map((w) => `<tr class="chk-waiting">
      <td><strong>${escapeHtml(w.payload.tail_number.toUpperCase())}</strong><br><small>${escapeHtml(w.payload.flight_no || "No flight")} · ${w.payload.check_type === "search" ? "Search" : "Check"}</small></td>
      <td>${escapeHtml(w.payload.station.toUpperCase())}</td>
      <td>${w.payload.items.every((i) => i.ok) ? `<span class="badge badge-active">Clear</span>` : `<span class="badge badge-inactive">Issues</span>`}</td>
      <td>${w.status === "failed" ? `<span class="badge badge-inactive">Rejected</span>` : `<span class="badge badge-draft">Waiting to upload</span>`}</td>
      <td>${escapeHtml(session.full_name || "")}<br><small>${niceTime(w.payload.performed_at)}</small></td>
      <td>${w.status === "failed" ? `<button class="ghost-btn" data-discard="${w.localId}" title="${escapeHtml(w.errorMessage || "")}">Discard</button>` : ""}</td>
    </tr>`).join("");
    $("#chkBody").innerHTML = waitingRows + (rows.length ? rows.map((c) => `<tr>
      <td><strong>${escapeHtml(c.tail_number)}</strong><br><small>${escapeHtml(c.flight_no || "No flight")} · ${c.check_type === "search" ? "Search" : "Check"}</small></td>
      <td>${escapeHtml(c.station)}</td>
      <td>${resultBadge(c)}</td>
      <td>${statusBadge(c)}</td>
      <td>${escapeHtml(c.signed_name)}<br><small>${niceTime(c.submitted_at)}</small></td>
      <td><button class="ghost-btn" data-open="${c.id}">Open</button></td>
    </tr>`).join("") : (waiting.length ? "" : `<tr class="empty-row"><td colspan="6">${checks.length ? "Nothing matches." : isHq ? "No checks yet." : "No checks yet. Tap New check."}</td></tr>`));
    $$("[data-discard]").forEach((b) => b.addEventListener("click", async () => {
      if (!confirm("Discard this rejected check? It will be lost from this device.")) return;
      await Offline.removePendingCheck(Number(b.dataset.discard));
      waiting = await Offline.listPendingChecks();
      draw();
    }));
    $$("[data-open]").forEach((b) => b.addEventListener("click", () => openDetail(Number(b.dataset.open))));
  }

  async function verifyChain() {
    try {
      const r = await Api.flightChecks.verify();
      if (r.ok) toast(`Records intact: ${r.checked} verified`);
      else alert(`Record #${r.broken_at} no longer matches what was signed. Everything from that record on cannot be trusted.`);
    } catch (err) { toast(err.message || "Could not verify."); }
  }

  async function openForm() {
    if (!template.length) {
      try { template = await Api.flightChecks.template(); await Offline.cacheSet("flight_template", template); } catch (err) {
        template = (err.status === 0 ? await Offline.cacheGet("flight_template") : null) || [];
        if (!template.length) { toast(err.status === 0 ? "Open Checks once with a connection so the checklist is saved on this device." : (err.message || "Could not load the checklist.")); return; }
      }
    }
    let lastGroup = "";
    const itemsHtml = template.map((t) => {
      const head = t.group !== lastGroup ? `<p class="chk-group">${escapeHtml(t.group)}</p>` : "";
      lastGroup = t.group;
      return `${head}<div class="chk-item" data-key="${t.key}"><p>${escapeHtml(t.label)}</p>
        <div class="chk-choice"><label data-v="ok"><input type="radio" name="i_${t.key}" value="ok" />OK</label><label data-v="issue"><input type="radio" name="i_${t.key}" value="issue" />Issue</label></div>
        <input type="text" class="chk-note" maxlength="200" placeholder="What did you find?" hidden /></div>`;
    }).join("");
    Sheet.open("New security check", `
      <p class="form-error" id="chkMsg"></p>
      <form id="chkForm">
        <label class="field"><span>Aircraft registration</span><input type="text" id="kTail" required maxlength="12" placeholder="5Y-KZG" autocapitalize="characters" /></label>
        <label class="field"><span>Flight number (optional)</span><input type="text" id="kFlight" maxlength="12" placeholder="KQ100" autocapitalize="characters" /></label>
        <label class="field"><span>Station</span><input type="text" id="kStation" required maxlength="20" placeholder="NBO" autocapitalize="characters" value="${escapeHtml(localStorage.getItem("know_last_station") || "")}" /></label>
        <label class="field"><span>Type</span><select id="kType"><option value="check">Security check</option><option value="search">Security search</option></select></label>
        <button type="button" class="ghost-btn" id="kAllOk">Mark everything OK</button>
        ${itemsHtml}
        <label class="field"><span>Remarks (optional)</span><input type="text" id="kRemarks" maxlength="300" /></label>
        <div class="field"><span>Sign here</span><canvas id="kSig" class="sig-pad" width="600" height="160"></canvas>
          <button type="button" class="ghost-btn" id="kSigClear">Clear signature</button></div>
        <p class="file-hint">Signing as ${escapeHtml(session.full_name || session.username || "you")}. Once submitted the check cannot be changed.</p>
        <div class="form-actions"><button type="submit" class="primary-btn" id="kSubmit">Sign and submit</button></div>
      </form>`);
    bindChoices();
    const pad = bindSignature();
    $("#kAllOk").addEventListener("click", () => $$(".chk-item").forEach((row) => setChoice(row, "ok")));
    $("#chkForm").addEventListener("submit", async (e) => {
      e.preventDefault();
      const items = [];
      for (const row of $$(".chk-item")) {
        const v = row.dataset.value;
        if (!v) return showFormMessage($("#chkMsg"), "Answer every checklist item.");
        const note = row.querySelector(".chk-note").value.trim();
        if (v === "issue" && !note) return showFormMessage($("#chkMsg"), "Add a note for every item marked as an issue.");
        items.push({ key: row.dataset.key, ok: v === "ok", note: v === "issue" ? note : null });
      }
      if (!pad.signed()) return showFormMessage($("#chkMsg"), "Sign in the box before submitting.");
      const btn = $("#kSubmit");
      btn.disabled = true;
      const station = $("#kStation").value.trim();
      const payload = { tail_number: $("#kTail").value.trim(), flight_no: $("#kFlight").value.trim() || null, station, check_type: $("#kType").value, items, remarks: $("#kRemarks").value.trim() || null, signature: pad.data(), performed_at: new Date().toISOString() };
      localStorage.setItem("know_last_station", station);
      try {
        if (!navigator.onLine) throw Object.assign(new Error("offline"), { status: 0 });
        await Api.flightChecks.submit(payload);
        Sheet.close();
        toast("Check signed and submitted");
        await load();
      } catch (err) {
        if (err && err.status === 0) {
          await Offline.queueCheck(payload);
          Sheet.close();
          toast("No connection. Check saved on this device and will upload when you are back online.");
          await load();
        } else {
          btn.disabled = false;
          showFormMessage($("#chkMsg"), err.message || "Could not submit the check.");
        }
      }
    });
  }

  function setChoice(row, v) {
    row.dataset.value = v;
    row.querySelectorAll(".chk-choice label").forEach((l) => { l.className = l.dataset.v === v ? (v === "ok" ? "on-ok" : "on-issue") : ""; });
    row.querySelector(`input[value="${v}"]`).checked = true;
    row.querySelector(".chk-note").hidden = v !== "issue";
  }

  function bindChoices() {
    $$(".chk-item").forEach((row) => row.querySelectorAll("input[type=radio]").forEach((r) => r.addEventListener("change", () => setChoice(row, r.value))));
  }

  function bindSignature() {
    const canvas = $("#kSig");
    const ctx = canvas.getContext("2d");
    ctx.lineWidth = 3; ctx.lineCap = "round"; ctx.lineJoin = "round"; ctx.strokeStyle = "#111";
    ctx.fillStyle = "#fff"; ctx.fillRect(0, 0, canvas.width, canvas.height);
    let drawing = false; let inked = false;
    const pos = (e) => { const r = canvas.getBoundingClientRect(); return [(e.clientX - r.left) * canvas.width / r.width, (e.clientY - r.top) * canvas.height / r.height]; };
    canvas.addEventListener("pointerdown", (e) => { drawing = true; canvas.setPointerCapture(e.pointerId); const [x, y] = pos(e); ctx.beginPath(); ctx.moveTo(x, y); ctx.lineTo(x + 0.1, y + 0.1); ctx.stroke(); inked = true; });
    canvas.addEventListener("pointermove", (e) => { if (!drawing) return; const [x, y] = pos(e); ctx.lineTo(x, y); ctx.stroke(); });
    ["pointerup", "pointercancel"].forEach((n) => canvas.addEventListener(n, () => { drawing = false; }));
    $("#kSigClear").addEventListener("click", () => { ctx.fillStyle = "#fff"; ctx.fillRect(0, 0, canvas.width, canvas.height); inked = false; });
    return { signed: () => inked, data: () => canvas.toDataURL("image/png") };
  }

  async function openDetail(id) {
    Sheet.open("Security check", `<p class="subtle">Loading</p>`);
    let c;
    try { c = await Api.flightChecks.get(id); } catch (err) { $("#sheetBody").innerHTML = `<p class="subtle">${escapeHtml(err.message || "Could not open the check.")}</p>`; return; }
    const labels = Object.fromEntries(template.map((t) => [t.key, t.label]));
    if (!template.length) { try { template = await Api.flightChecks.template(); template.forEach((t) => { labels[t.key] = t.label; }); } catch (e) { /* labels fall back to keys */ } }
    const rows = c.items.map((i) => `<div class="ex-row"><div><strong>${escapeHtml(labels[i.key] || i.key)}</strong>${i.note ? `<small>${escapeHtml(i.note)}</small>` : ""}</div><span class="badge ${i.ok ? "badge-active" : "badge-inactive"}">${i.ok ? "OK" : "Issue"}</span></div>`).join("");
    const review = c.status === "pending" ? (isHq ? `
      <label class="field"><span>Review note (needed to reject)</span><input type="text" id="rvNote" maxlength="300" /></label>
      <div class="form-actions"><button class="primary-btn" id="rvApprove">Approve</button><button class="ghost-btn" id="rvReject">Reject</button></div>` : `<p class="subtle">Waiting for HQ review.</p>`)
      : `<p class="subtle">${c.status === "approved" ? "Approved" : "Rejected"} by ${escapeHtml(c.reviewed_name || "HQ")}${c.reviewed_at ? " on " + niceTime(c.reviewed_at) : ""}${c.review_note ? ": " + escapeHtml(c.review_note) : ""}</p>`;
    $("#sheetTitle").textContent = `${c.tail_number} at ${c.station}`;
    $("#sheetBody").innerHTML = `
      <p>${resultBadge(c)} ${statusBadge(c)}</p>
      <p class="subtle">${escapeHtml(c.flight_no || "No flight number")} · ${c.check_type === "search" ? "Security search" : "Security check"}</p>
      ${rows}
      ${c.remarks ? `<p class="subtle">Remarks: ${escapeHtml(c.remarks)}</p>` : ""}
      <p class="chk-group">Signature</p>
      <img class="chk-sig-img" src="${escapeHtml(c.signature)}" alt="Signature of ${escapeHtml(c.signed_name)}" />
      <p class="subtle">${escapeHtml(c.signed_name)} · ${niceTime(c.submitted_at)}</p>
      <p class="chk-group">Record seal</p>
      <p class="chk-hash">${c.record_hash}</p>
      <p class="chk-group">Review</p>
      ${review}
      <p class="chk-group">History</p>
      <div id="chkHist"><p class="subtle">Loading</p></div>`;
    const decide = async (decision) => {
      const note = $("#rvNote").value.trim();
      if (decision === "reject" && !note) { toast("Say why you are rejecting it."); return; }
      try { await Api.flightChecks.review(id, { decision, note: note || null }); Sheet.close(); toast(decision === "approve" ? "Check approved" : "Check rejected"); await load(); } catch (err) { toast(err.message || "Could not save the review."); }
    };
    $("#rvApprove")?.addEventListener("click", () => decide("approve"));
    $("#rvReject")?.addEventListener("click", () => decide("reject"));
    try {
      const hist = await Api.flightChecks.history(id);
      $("#chkHist").innerHTML = hist.length ? hist.map((r) => `<div class="ex-row"><div><strong>${escapeHtml(ACTIONS[r.action] || r.action)}</strong><small>${escapeHtml(r.by || "Unknown")} · ${r.at ? niceTime(r.at) : ""}${r.ip ? " · " + escapeHtml(r.ip) : ""}</small></div></div>`).join("") : `<p class="subtle">No history yet.</p>`;
    } catch (err) { $("#chkHist").innerHTML = `<p class="subtle">${escapeHtml(err.message || "Could not load the history.")}</p>`; }
  }
})();
