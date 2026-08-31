(async function () {
  await initShell("schools", ["super_admin"]);

  let schools = [];

  await loadSchools();

  async function loadSchools() {
    const body = $("#schoolsBody");
    try {
      schools = await Api.schools.list();
      body.innerHTML = schools.length ? schools.map((s) => `
        <tr>
          <td>
            <div class="seal">
              ${s.logo_path
                ? `<img src="${Api.schools.logoUrl(s)}" alt="${escapeHtml(s.name)} icon">`
                : `<span class="seal-fallback">${escapeHtml(initials(s.name))}</span>`}
            </div>
          </td>
          <td>${escapeHtml(s.name)}</td>
          <td>${escapeHtml(s.address || "—")}</td>
          <td>${formatDate(s.created_at)}</td>
          <td><button class="ghost-btn" data-logo-for="${s.id}">Change icon</button></td>
        </tr>
      `).join("") : `<tr class="empty-row"><td colspan="5">No schools yet.</td></tr>`;

      $$("[data-logo-for]", body).forEach((btn) => {
        btn.addEventListener("click", () => openLogoSheet(parseInt(btn.dataset.logoFor, 10)));
      });
    } catch (err) {
      body.innerHTML = `<tr class="empty-row"><td colspan="5">${escapeHtml(err.message)}</td></tr>`;
    }
  }

  /* ---------------- Add school sheet ---------------- */

  $("#newSchoolBtn").addEventListener("click", () => {
    Sheet.open("Add school", `
      <p class="form-error" id="schoolMsg"></p>
      <form id="schoolForm">
        <label class="field"><span>School name</span><input type="text" id="schoolName" required /></label>
        <label class="field"><span>Address (optional)</span><input type="text" id="schoolAddress" /></label>
        <label class="chip-input wide">
          <span class="ico" data-ico="image"></span> Upload portal icon (PNG, JPG, WebP)
          <input type="file" id="schoolLogo" accept="image/png,image/jpeg,image/webp" hidden />
        </label>
        <p class="file-hint">Shown in the sidebar and on generated audit reports for this school. You can add it later too.</p>
        <div class="form-actions">
          <button type="submit" class="primary-btn" id="schoolSubmit">Add school</button>
        </div>
      </form>
    `);

    $("#schoolForm").addEventListener("submit", async (e) => {
      e.preventDefault();
      const msg = $("#schoolMsg");
      hideFormMessage(msg);
      const submitBtn = $("#schoolSubmit");
      submitBtn.disabled = true;
      submitBtn.textContent = "Adding…";

      try {
        const created = await Api.schools.create({
          name: $("#schoolName").value.trim(),
          address: $("#schoolAddress").value.trim() || null,
        });
        const file = $("#schoolLogo").files[0];
        if (file) await Api.schools.uploadLogo(created.id, file);
        Sheet.close();
        toast("School added.");
        await loadSchools();
      } catch (err) {
        showFormMessage(msg, err.message || "Could not add the school.");
        submitBtn.disabled = false;
        submitBtn.textContent = "Add school";
      }
    });
  });

  /* ---------------- Change icon sheet ---------------- */

  function openLogoSheet(schoolId) {
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
        await Api.schools.uploadLogo(schoolId, file);
        Sheet.close();
        toast("Portal icon updated.");
        await loadSchools();
      } catch (err) {
        showFormMessage(msg, err.message || "Could not upload the icon.");
        submitBtn.disabled = false;
        submitBtn.textContent = "Upload";
      }
    });
  }
})();