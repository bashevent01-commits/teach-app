(async function () {
  const session = await initShell("members", ["staff", "institution_admin"]);
  if (session.institution_type !== "gym") {
    location.href = homePageFor(session.role);
    return;
  }
  const canRecord = session.role === "staff";
  let members = [];
  const PERIODS = [["weekly", "Weekly"], ["monthly", "Monthly"], ["quarterly", "Every 3 months"], ["yearly", "Yearly"]];
  const periodLabel = (k) => (PERIODS.find((p) => p[0] === k) || [k, k])[1];
  const niceDay = (iso) => new Date(iso + "T00:00:00").toLocaleDateString("en-KE", { day: "numeric", month: "short", year: "numeric" });
  const dueText = (m) => {
    if (!m.is_active) return ["Paused", "mem-paused"];
    if (m.days_to_due < 0) return [`Overdue by ${-m.days_to_due} day${m.days_to_due === -1 ? "" : "s"}`, "mem-overdue"];
    if (m.days_to_due === 0) return ["Due today", "mem-today"];
    if (m.days_to_due === 1) return ["Due tomorrow", "mem-tomorrow"];
    return [niceDay(m.next_due), "mem-ok"];
  };

  await load();
  onFreshData(load);
  $("#newMemberBtn").addEventListener("click", () => openSheet(null));
  $("#memSearch").addEventListener("input", draw);
  $("#memFilter").addEventListener("change", draw);

  async function load() {
    try { members = await Api.members.list(); } catch (err) { toast(err.message || "Could not load members."); }
    draw();
  }

  function draw() {
    const count = (s) => members.filter((m) => m.status === s).length;
    $("#memCounters").innerHTML = [["overdue", "Behind", "mem-overdue"], ["due_today", "Due today", "mem-today"], ["due_tomorrow", "Due tomorrow", "mem-tomorrow"]]
      .map(([s, label, cls]) => `<button type="button" class="mem-counter" data-filter="${s}"><strong class="${count(s) ? cls : ""}">${count(s)}</strong><small>${label}</small></button>`).join("");
    $$(".mem-counter").forEach((b) => b.addEventListener("click", () => { $("#memFilter").value = b.dataset.filter; draw(); }));
    const filter = $("#memFilter").value;
    const q = $("#memSearch").value.trim().toLowerCase();
    const rows = members.filter((m) => (filter === "all" || m.status === filter) && (!q || m.name.toLowerCase().includes(q) || (m.phone || "").includes(q)));
    $("#memBody").innerHTML = rows.length ? rows.map((m) => {
      const [text, cls] = dueText(m);
      return `<tr>
        <td><strong>${escapeHtml(m.name)}</strong>${m.phone ? `<br><small>${escapeHtml(m.phone)}</small>` : ""}</td>
        <td>${escapeHtml(m.plan || periodLabel(m.period))}</td>
        <td>${money(m.fee)} <small>${periodLabel(m.period).toLowerCase()}</small></td>
        <td class="${cls}"><strong>${text}</strong></td>
        <td>${canRecord && m.is_active ? `<button class="primary-btn" data-pay="${m.id}">Record payment</button>` : ""} <button class="ghost-btn" data-edit="${m.id}">Edit</button></td>
      </tr>`;
    }).join("") : `<tr class="empty-row"><td colspan="5">${members.length ? "No one matches." : "No members yet. Add the first one."}</td></tr>`;
    $$("[data-pay]").forEach((b) => b.addEventListener("click", () => { location.href = `home.html?pay=${b.dataset.pay}`; }));
    $$("[data-edit]").forEach((b) => b.addEventListener("click", () => openSheet(members.find((m) => m.id === Number(b.dataset.edit)))));
  }

  function openSheet(m) {
    const today = new Date().toLocaleDateString("en-CA", { timeZone: "Africa/Nairobi" });
    Sheet.open(m ? "Member" : "Add a member", `
      <p class="form-error" id="memMsg"></p>
      <form id="memForm">
        <label class="field"><span>Full name</span><input type="text" id="mName" required value="${m ? escapeHtml(m.name) : ""}" /></label>
        <label class="field"><span>Phone (optional)</span><input type="tel" id="mPhone" value="${m && m.phone ? escapeHtml(m.phone) : ""}" /></label>
        <label class="field"><span>Plan name (optional)</span><input type="text" id="mPlan" placeholder="Monthly, Student, Couples" value="${m && m.plan ? escapeHtml(m.plan) : ""}" /></label>
        <label class="field"><span>Subscription fee (KES)</span><input type="number" id="mFee" min="1" step="0.01" required value="${m ? Number(m.fee) : ""}" /></label>
        <label class="field"><span>Pays</span><select id="mPeriod">${PERIODS.map(([k, l]) => `<option value="${k}" ${m && m.period === k || (!m && k === "monthly") ? "selected" : ""}>${l}</option>`).join("")}</select></label>
        <label class="field"><span>Next payment due</span><input type="date" id="mDue" value="${m ? m.next_due : today}" /></label>
        <p class="file-hint">Leave the date as today if they are paying now, then record the payment so it moves on.</p>
        ${m ? `<label class="field"><span>Status</span><select id="mActive"><option value="1" ${m.is_active ? "selected" : ""}>Active</option><option value="0" ${m.is_active ? "" : "selected"}>Paused</option></select></label>` : ""}
        <div class="form-actions">
          <button type="submit" class="primary-btn" id="memSave">${m ? "Save" : "Add member"}</button>
          ${m ? `<button type="button" class="ghost-btn" id="memRemove">Remove member</button>` : ""}
        </div>
      </form>`);
    $("#memForm").addEventListener("submit", async (e) => {
      e.preventDefault();
      const body = {
        name: $("#mName").value.trim(), phone: $("#mPhone").value.trim() || null, plan: $("#mPlan").value.trim() || null,
        fee: $("#mFee").value, period: $("#mPeriod").value, next_due: $("#mDue").value || today,
      };
      if (m) body.is_active = $("#mActive").value === "1";
      try {
        if (m) await Api.members.update(m.id, body); else await Api.members.add(body);
        Sheet.close();
        toast(m ? "Member saved" : "Member added");
        await load();
      } catch (err) { showFormMessage($("#memMsg"), err.message || "Could not save the member."); }
    });
    $("#memRemove")?.addEventListener("click", async () => {
      if (!confirm("Remove this member? One who has paid before is paused instead, so your books keep their history.")) return;
      try { await Api.members.remove(m.id); Sheet.close(); toast("Member removed"); await load(); } catch (err) { toast(err.message || "Could not remove."); }
    });
  }
})();
