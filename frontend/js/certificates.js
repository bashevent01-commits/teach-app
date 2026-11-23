(async function () {
  const session = await initShell("certificates", ["staff", "institution_admin"]);
  let certs = [];
  const CATS = [["staff_training", "Staff training"], ["background_check", "Background check"], ["handler_authorisation", "Handler authorisation"], ["security_programme", "Security programme"], ["equipment", "Equipment"], ["station_clearance", "Station clearance"], ["other", "Other"]];
  const catLabel = (k) => (CATS.find((c) => c[0] === k) || [k, k])[1];
  const niceDay = (iso) => new Date(iso + "T00:00:00").toLocaleDateString("en-KE", { day: "numeric", month: "short", year: "numeric" });
  const niceTime = (iso) => new Date(iso).toLocaleString("en-KE", { day: "numeric", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit", timeZone: "Africa/Nairobi" });
  const ACTIONS = { certificate_created: "Created", certificate_updated: "Edited", certificate_renewed: "Renewed", certificate_withdrawn: "Withdrawn", certificate_viewed: "Viewed" };
  const stateText = (c) => {
    if (c.status === "withdrawn") return ["Withdrawn", "mem-paused"];
    if (c.status === "no_expiry") return ["No expiry", "mem-ok"];
    if (c.status === "expired") return [`Expired ${-c.days_to_expiry} day${c.days_to_expiry === -1 ? "" : "s"} ago`, "mem-overdue"];
    if (c.status === "critical") return [c.days_to_expiry === 0 ? "Expires today" : `${c.days_to_expiry} day${c.days_to_expiry === 1 ? "" : "s"} left`, "mem-overdue"];
    if (c.status === "warning") return [`${c.days_to_expiry} days left`, "mem-today"];
    if (c.status === "notice") return [`${c.days_to_expiry} days left`, "mem-tomorrow"];
    return [niceDay(c.expires_on), "mem-ok"];
  };
  const today = () => new Date().toLocaleDateString("en-CA", { timeZone: "Africa/Nairobi" });

  await load();
  onFreshData(load);
  $("#newCertBtn").addEventListener("click", () => openSheet(null));
  $("#certSearch").addEventListener("input", draw);
  $("#certFilter").addEventListener("change", draw);

  async function load() {
    try { certs = await Api.certificates.list(); } catch (err) { toast(err.message || "Could not load certificates."); }
    draw();
  }

  function draw() {
    const count = (s) => certs.filter((c) => c.status === s).length;
    $("#certCounters").innerHTML = [["expired", "Expired", "mem-overdue"], ["critical", "15 days", "mem-overdue"], ["warning", "30 days", "mem-today"], ["notice", "60 days", "mem-tomorrow"]]
      .map(([s, label, cls]) => `<button type="button" class="mem-counter" data-filter="${s}"><strong class="${count(s) ? cls : ""}">${count(s)}</strong><small>${label}</small></button>`).join("");
    $$(".mem-counter").forEach((b) => b.addEventListener("click", () => { $("#certFilter").value = b.dataset.filter; draw(); }));
    const filter = $("#certFilter").value;
    const q = $("#certSearch").value.trim().toLowerCase();
    const rows = certs.filter((c) => (filter === "all" || c.status === filter) && (!q || [c.title, c.holder, c.reference_no || "", c.station || ""].some((v) => v.toLowerCase().includes(q))));
    $("#certBody").innerHTML = rows.length ? rows.map((c) => {
      const [text, cls] = stateText(c);
      return `<tr>
        <td><strong>${escapeHtml(c.title)}</strong><br><small>${escapeHtml(c.holder)}${c.reference_no ? " · " + escapeHtml(c.reference_no) : ""}</small></td>
        <td>${escapeHtml(catLabel(c.category))}</td>
        <td>${escapeHtml(c.station || "HQ")}</td>
        <td class="${cls}"><strong>${text}</strong>${c.expires_on ? `<br><small>${niceDay(c.expires_on)}</small>` : ""}</td>
        <td>${c.is_active ? `<button class="primary-btn" data-renew="${c.id}">Renew</button>` : ""} <button class="ghost-btn" data-hist="${c.id}">History</button> <button class="ghost-btn" data-edit="${c.id}">Edit</button></td>
      </tr>`;
    }).join("") : `<tr class="empty-row"><td colspan="5">${certs.length ? "Nothing matches." : "No certificates yet. Add the first one."}</td></tr>`;
    const find = (b, key) => certs.find((c) => c.id === Number(b.dataset[key]));
    $$("[data-renew]").forEach((b) => b.addEventListener("click", () => renewSheet(find(b, "renew"))));
    $$("[data-hist]").forEach((b) => b.addEventListener("click", () => historySheet(find(b, "hist"))));
    $$("[data-edit]").forEach((b) => b.addEventListener("click", () => openSheet(find(b, "edit"))));
  }

  function openSheet(c) {
    Sheet.open(c ? "Certificate" : "Add a certificate", `
      <p class="form-error" id="certMsg"></p>
      <form id="certForm">
        <label class="field"><span>Document title</span><input type="text" id="cTitle" required placeholder="Aviation security awareness certificate" value="${c ? escapeHtml(c.title) : ""}" /></label>
        <label class="field"><span>Holder (person, company or aircraft)</span><input type="text" id="cHolder" required value="${c ? escapeHtml(c.holder) : ""}" /></label>
        <label class="field"><span>Category</span><select id="cCat">${CATS.map(([k, l]) => `<option value="${k}" ${c && c.category === k ? "selected" : ""}>${l}</option>`).join("")}</select></label>
        <label class="field"><span>Reference number (optional)</span><input type="text" id="cRef" value="${c && c.reference_no ? escapeHtml(c.reference_no) : ""}" /></label>
        <label class="field"><span>Issuer (optional)</span><input type="text" id="cIssuer" placeholder="KCAA" value="${c && c.issuer ? escapeHtml(c.issuer) : ""}" /></label>
        <label class="field"><span>Station (optional)</span><input type="text" id="cStation" placeholder="NBO" value="${c && c.station ? escapeHtml(c.station) : ""}" /></label>
        <label class="field"><span>Issued on (optional)</span><input type="date" id="cIssued" value="${c && c.issued_on ? c.issued_on : ""}" /></label>
        <label class="field"><span>Expires on (leave empty if it never expires)</span><input type="date" id="cExpires" value="${c && c.expires_on ? c.expires_on : ""}" /></label>
        <label class="field"><span>Notes (optional)</span><input type="text" id="cNotes" maxlength="300" value="${c && c.notes ? escapeHtml(c.notes) : ""}" /></label>
        <div class="form-actions">
          <button type="submit" class="primary-btn" id="certSave">${c ? "Save" : "Add certificate"}</button>
          ${c && c.is_active ? `<button type="button" class="ghost-btn" id="certWithdraw">Withdraw</button>` : ""}
        </div>
      </form>`);
    $("#certForm").addEventListener("submit", async (e) => {
      e.preventDefault();
      const body = {
        title: $("#cTitle").value.trim(), holder: $("#cHolder").value.trim(), category: $("#cCat").value, reference_no: $("#cRef").value.trim() || null,
        issuer: $("#cIssuer").value.trim() || null, station: $("#cStation").value.trim() || null, issued_on: $("#cIssued").value || null,
        expires_on: $("#cExpires").value || null, notes: $("#cNotes").value.trim() || null,
      };
      try {
        if (c) await Api.certificates.update(c.id, body); else await Api.certificates.add(body);
        Sheet.close();
        toast(c ? "Certificate saved" : "Certificate added");
        await load();
      } catch (err) { showFormMessage($("#certMsg"), err.message || "Could not save the certificate."); }
    });
    $("#certWithdraw")?.addEventListener("click", async () => {
      if (!confirm("Withdraw this certificate? It stays in the records and its history is kept.")) return;
      try { await Api.certificates.update(c.id, { is_active: false }); Sheet.close(); toast("Certificate withdrawn"); await load(); } catch (err) { toast(err.message || "Could not withdraw."); }
    });
  }

  function renewSheet(c) {
    Sheet.open("Renew certificate", `
      <p class="form-error" id="renMsg"></p>
      <p class="subtle">${escapeHtml(c.title)} for ${escapeHtml(c.holder)}${c.expires_on ? ", currently expiring " + niceDay(c.expires_on) : ""}.</p>
      <form id="renForm">
        <label class="field"><span>New issue date</span><input type="date" id="rIssued" value="${today()}" required /></label>
        <label class="field"><span>New expiry date</span><input type="date" id="rExpires" required /></label>
        <div class="form-actions"><button type="submit" class="primary-btn">Save renewal</button></div>
      </form>`);
    $("#renForm").addEventListener("submit", async (e) => {
      e.preventDefault();
      try {
        await Api.certificates.update(c.id, { issued_on: $("#rIssued").value, expires_on: $("#rExpires").value });
        Sheet.close();
        toast("Certificate renewed");
        await load();
      } catch (err) { showFormMessage($("#renMsg"), err.message || "Could not renew."); }
    });
  }

  async function historySheet(c) {
    Sheet.open("History", `<p class="subtle">${escapeHtml(c.title)}</p><div id="histBody"><p class="subtle">Loading</p></div>`);
    try {
      const rows = await Api.certificates.history(c.id);
      $("#histBody").innerHTML = rows.length ? rows.map((r) => `<div class="ex-row"><div><strong>${escapeHtml(ACTIONS[r.action] || r.action)}</strong><small>${escapeHtml(r.by || "Unknown")} · ${r.at ? niceTime(r.at) : ""}${r.ip ? " · " + escapeHtml(r.ip) : ""}</small>${r.detail ? `<small>${escapeHtml(r.detail)}</small>` : ""}</div></div>`).join("") : `<p class="subtle">No history yet.</p>`;
    } catch (err) { $("#histBody").innerHTML = `<p class="subtle">${escapeHtml(err.message || "Could not load the history.")}</p>`; }
  }
})();
