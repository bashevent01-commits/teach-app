(async function () {
  await initShell("institutions", ["super_admin"]);

  let institutions = [];

  const COMMON_TYPES = ["school", "shop", "supermarket", "canteen", "pharmacy", "other"];

  await loadInstitutions();

  async function loadInstitutions() {
    const body = $("#schoolsBody");
    try {
      institutions = await Api.admin.institutions();
      renderInstitutions();
    } catch (err) {
      body.innerHTML = `<tr class="empty-row"><td colspan="7">${escapeHtml(err.message)}</td></tr>`;
    }
  }

  function renderInstitutions() {
    const body = $("#schoolsBody");
    const q = $("#instSearch").value.trim().toLowerCase();
    const sort = $("#instSort").value;
    let rows = institutions.filter((s) => !q || [s.name, s.type, s.region || ""].some((v) => v.toLowerCase().includes(q)));
    const byName = (a, b) => a.name.localeCompare(b.name);
    if (sort === "name") rows.sort(byName);
    if (sort === "newest") rows.sort((a, b) => new Date(b.created_at) - new Date(a.created_at));
    if (sort === "active") rows.sort((a, b) => b.entries_30d - a.entries_30d);
    if (sort === "quiet") rows.sort((a, b) => daysSinceIso(b.last_activity_at) - daysSinceIso(a.last_activity_at));
    $("#instCount").textContent = `${rows.length} ${rows.length === 1 ? "institution" : "institutions"}`;

    body.innerHTML = rows.length ? rows.map((s) => {
      const quiet = daysSinceIso(s.last_activity_at) > 14;
      return `
        <tr>
          <td>
            <div class="cell-main">
              <div class="seal">
                ${s.logo_path
                  ? `<img src="${Api.institutions.logoUrl(s)}" alt="${escapeHtml(s.name)} icon">`
                  : `<span class="seal-fallback">${escapeHtml(initials(s.name))}</span>`}
              </div>
              <div><strong>${escapeHtml(s.name)}</strong><small>${escapeHtml(s.type)}</small></div>
            </div>
          </td>
          <td>${escapeHtml(s.region || "—")}</td>
          <td class="num">${s.staff_count}</td>
          <td class="num">${s.admin_count}</td>
          <td class="num">${s.entries_30d}</td>
          <td><span class="status-dot ${quiet ? "quiet" : ""}"></span>${lastSeenLabel(s.last_activity_at)}</td>
          <td>
            <div class="row-actions">
              <a class="ghost-btn" href="accounts.html?institution=${s.id}">People</a>
              <button class="ghost-btn" data-logo-for="${s.id}">Change icon</button>
            </div>
          </td>
        </tr>`;
    }).join("") : `<tr class="empty-row"><td colspan="7">No institutions match.</td></tr>`;

    $$("[data-logo-for]", body).forEach((btn) => {
      btn.addEventListener("click", () => openLogoSheet(parseInt(btn.dataset.logoFor, 10)));
    });
  }

  $("#instSearch").addEventListener("input", renderInstitutions);
  $("#instSort").addEventListener("change", renderInstitutions);

  /* ---------------- Add institution sheet ---------------- */

  $("#newSchoolBtn").addEventListener("click", () => {
    const typeOptions = COMMON_TYPES.map((t) => `<option value="${t}">${t[0].toUpperCase() + t.slice(1)}</option>`).join("");
    const regionOptions = `<option value="">Not set</option>` + KENYA_COUNTIES.map((c) => `<option value="${c}">${c}</option>`).join("");

    Sheet.open("Add institution", `
      <p class="form-error" id="schoolMsg"></p>
      <form id="schoolForm">
        <label class="field"><span>Institution name</span><input type="text" id="schoolName" required /></label>
        <label class="field"><span>Type</span>
          <select id="schoolType">${typeOptions}</select>
        </label>
        <label class="field" id="schoolTypeOtherField" hidden><span>Type (custom)</span><input type="text" id="schoolTypeOther" placeholder="e.g. clinic, warehouse" /></label>
        <label class="field"><span>Region (county)</span>
          <select id="schoolRegion">${regionOptions}</select>
        </label>
        <p class="file-hint">Used for regional breakdowns in Market insights. You can add it later too.</p>
        <label class="field"><span>Address (optional)</span><input type="text" id="schoolAddress" /></label>
        <label class="chip-input wide">
          <span class="ico" data-ico="image"></span> Upload portal icon (PNG, JPG, WebP)
          <input type="file" id="schoolLogo" accept="image/png,image/jpeg,image/webp" hidden />
        </label>
        <p class="file-hint">Shown in the sidebar and on generated audit reports for this institution. You can add it later too.</p>
        <div class="form-actions">
          <button type="submit" class="primary-btn" id="schoolSubmit">Add institution</button>
        </div>
      </form>
    `);

    $("#schoolType").addEventListener("change", () => {
      $("#schoolTypeOtherField").hidden = $("#schoolType").value !== "other";
    });

    $("#schoolForm").addEventListener("submit", async (e) => {
      e.preventDefault();
      const msg = $("#schoolMsg");
      hideFormMessage(msg);
      const submitBtn = $("#schoolSubmit");
      submitBtn.disabled = true;
      submitBtn.textContent = "Adding…";

      try {
        const typeValue = $("#schoolType").value === "other"
          ? $("#schoolTypeOther").value.trim()
          : $("#schoolType").value;
        if (!typeValue) throw new Error("Please enter a type.");

        const created = await Api.institutions.create({
          name: $("#schoolName").value.trim(),
          type: typeValue,
          address: $("#schoolAddress").value.trim() || null,
          region: $("#schoolRegion").value || null,
        });
        const file = $("#schoolLogo").files[0];
        if (file) await Api.institutions.uploadLogo(created.id, file);
        Sheet.close();
        toast("Institution added.");
        await loadInstitutions();
      } catch (err) {
        showFormMessage(msg, err.message || "Could not add the institution.");
        submitBtn.disabled = false;
        submitBtn.textContent = "Add institution";
      }
    });
  });

  /* ---------------- Change icon sheet ---------------- */

  function openLogoSheet(institutionId) {
    Sheet.open("Update portal icon", `
      <p class="form-error" id="logoMsg"></p>
      <form id="logoForm">
        <label class="field"><span>Image file</span><input type="file" id="logoFile" accept="image/png,image/jpeg,image/webp" required /></label>
        <div class="form-actions">
          <button type="submit" class="primary-btn" id="logoSubmit">Upload</button>
        </div>
      </form>
    `);

    $("#logoForm").addEventListener("submit", async (e) => {
      e.preventDefault();
      const msg = $("#logoMsg");
      hideFormMessage(msg);
      const file = $("#logoFile").files[0];
      if (!file) return;
      const submitBtn = $("#logoSubmit");
      submitBtn.disabled = true;
      submitBtn.textContent = "Uploading…";
      try {
        await Api.institutions.uploadLogo(institutionId, file);
        Sheet.close();
        toast("Portal icon updated.");
        await loadInstitutions();
      } catch (err) {
        showFormMessage(msg, err.message || "Could not upload the icon.");
        submitBtn.disabled = false;
        submitBtn.textContent = "Upload";
      }
    });
  }
})();
