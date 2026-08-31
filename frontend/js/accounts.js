(async function () {
  const session = await initShell("accounts", ["super_admin"]);

  let schools = [];
  let users = [];

  await loadAll();

  async function loadAll() {
    const body = $("#usersBody");
    try {
      [schools, users] = await Promise.all([Api.schools.list(), Api.users.list()]);
      renderUsers();
    } catch (err) {
      body.innerHTML = `<tr class="empty-row"><td colspan="6">${escapeHtml(err.message)}</td></tr>`;
    }
  }

  function renderUsers() {
    const schoolNameById = Object.fromEntries(schools.map((s) => [s.id, s.name]));
    const body = $("#usersBody");
    body.innerHTML = users.length ? users.map((u) => `
      <tr>
        <td>${escapeHtml(u.full_name)}</td>
        <td><code>${escapeHtml(u.username)}</code></td>
        <td><span class="badge badge-${u.role}">${u.role === "super_admin" ? "Super admin" : "Teacher"}</span></td>
        <td>${u.role === "teacher" ? escapeHtml(schoolNameById[u.school_id] || "—") : "—"}</td>
        <td><span class="badge badge-${u.is_active ? "active" : "inactive"}">${u.is_active ? "Active" : "Deactivated"}</span></td>
        <td>
          <div class="row-actions">
            <button class="ghost-btn" data-edit="${u.id}">Edit</button>
            <button class="ghost-btn" data-reset="${u.id}">Reset password</button>
            ${u.id === session.user_id ? "" : (u.is_active
              ? `<button class="danger-btn" data-deactivate="${u.id}">Deactivate</button>`
              : `<button class="ghost-btn" data-reactivate="${u.id}">Reactivate</button>`)}
          </div>
        </td>
      </tr>
    `).join("") : `<tr class="empty-row"><td colspan="6">No accounts yet. Issue credentials to get started.</td></tr>`;

    $$("[data-deactivate]", body).forEach((btn) => btn.addEventListener("click", async () => {
      if (!confirm("Deactivate this account? They will no longer be able to sign in.")) return;
      try { await Api.users.deactivate(btn.dataset.deactivate); await loadAll(); toast("Account deactivated."); }
      catch (err) { toast(err.message); }
    }));
    $$("[data-reactivate]", body).forEach((btn) => btn.addEventListener("click", async () => {
      try { await Api.users.reactivate(btn.dataset.reactivate); await loadAll(); toast("Account reactivated."); }
      catch (err) { toast(err.message); }
    }));
    $$("[data-reset]", body).forEach((btn) => btn.addEventListener("click", () => openResetSheet(btn.dataset.reset)));
    $$("[data-edit]", body).forEach((btn) => btn.addEventListener("click", () => openEditSheet(btn.dataset.edit)));
  }

  /* ---------------- Edit account sheet ---------------- */

  function openEditSheet(userId) {
    const user = users.find((u) => String(u.id) === String(userId));
    if (!user) return;
    const isTeacher = user.role === "teacher";
    const schoolOptions = schools.map((s) =>
      `<option value="${s.id}" ${s.id === user.school_id ? "selected" : ""}>${escapeHtml(s.name)}</option>`
    ).join("");

    Sheet.open("Edit account", `
      <p class="form-error" id="editMsg"></p>
      <form id="editForm">
        <label class="field"><span>Full name</span><input type="text" id="editFullname" value="${escapeHtml(user.full_name)}" required /></label>
        <label class="field"><span>Username</span><input type="text" id="editUsername" value="${escapeHtml(user.username)}" required /></label>
        ${isTeacher ? `<label class="field"><span>School</span><select id="editSchool">${schoolOptions}</select></label>` : ""}
        <div class="form-actions">
          <button type="submit" class="primary-btn" id="editSubmit">Save changes</button>
        </div>
      </form>
    `);

    $("#editForm").addEventListener("submit", async (e) => {
      e.preventDefault();
      const msg = $("#editMsg");
      hideFormMessage(msg);
      const submitBtn = $("#editSubmit");
      submitBtn.disabled = true;
      submitBtn.textContent = "Saving…";
      try {
        const payload = {
          full_name: $("#editFullname").value.trim(),
          username: $("#editUsername").value.trim(),
        };
        if (isTeacher) payload.school_id = parseInt($("#editSchool").value, 10);
        await Api.users.update(userId, payload);
        Sheet.close();
        toast("Account updated.");
        await loadAll();
      } catch (err) {
        showFormMessage(msg, err.message || "Could not update the account.");
        submitBtn.disabled = false;
        submitBtn.textContent = "Save changes";
      }
    });
  }

  /* ---------------- Issue credentials sheet ---------------- */

  $("#newUserBtn").addEventListener("click", () => {
    const schoolOptions = schools.length
      ? schools.map((s) => `<option value="${s.id}">${escapeHtml(s.name)}</option>`).join("")
      : `<option value="">Add a school first</option>`;

    Sheet.open("Issue credentials", `
      <p class="form-error" id="userMsg"></p>
      <form id="userForm">
        <label class="field"><span>Account type</span>
          <select id="userRole">
            <option value="teacher">Teacher</option>
            <option value="super_admin">Super admin</option>
          </select>
        </label>
        <p class="file-hint" id="roleHint">Scoped to one school — records transactions, audits, and news for that school only.</p>
        <label class="field"><span>Full name</span><input type="text" id="userFullname" required /></label>
        <label class="field"><span>Username</span><input type="text" id="userUsername" required /></label>
        <label class="field"><span>Temporary password</span><input type="text" id="userPassword" required minlength="8" /></label>
        <p class="file-hint">Share this with them directly. A new one can be issued anytime from Reset password.</p>
        <label class="field" id="userSchoolField"><span>School</span><select id="userSchool">${schoolOptions}</select></label>
        <div class="form-actions">
          <button type="submit" class="primary-btn" id="userSubmit">Create account</button>
        </div>
      </form>
    `);

    function applyRoleFieldVisibility() {
      const role = $("#userRole").value;
      const schoolField = $("#userSchoolField");
      const hint = $("#roleHint");
      if (role === "super_admin") {
        schoolField.style.display = "none";
        hint.textContent = "Full access: manages every school, its portal icon, and all account credentials.";
      } else {
        schoolField.style.display = "";
        hint.textContent = "Scoped to one school — records transactions, audits, and news for that school only.";
      }
    }
    $("#userRole").addEventListener("change", applyRoleFieldVisibility);
    applyRoleFieldVisibility();

    $("#userForm").addEventListener("submit", async (e) => {
      e.preventDefault();
      const msg = $("#userMsg");
      hideFormMessage(msg);
      const submitBtn = $("#userSubmit");
      submitBtn.disabled = true;
      submitBtn.textContent = "Creating…";

      try {
        const role = $("#userRole").value;
        await Api.users.create({
          full_name: $("#userFullname").value.trim(),
          username: $("#userUsername").value.trim(),
          password: $("#userPassword").value,
          role,
          school_id: role === "teacher" ? parseInt($("#userSchool").value, 10) : null,
        });
        Sheet.close();
        toast("Account created.");
        await loadAll();
      } catch (err) {
        showFormMessage(msg, err.message || "Could not create the account.");
        submitBtn.disabled = false;
        submitBtn.textContent = "Create account";
      }
    });
  });

  /* ---------------- Reset password sheet ---------------- */

  function openResetSheet(userId) {
    Sheet.open("Reset password", `
      <p class="form-error" id="resetMsg"></p>
      <form id="resetForm">
        <label class="field"><span>New password</span><input type="text" id="resetPassword" required minlength="8" /></label>
        <div class="form-actions">
          <button type="submit" class="primary-btn" id="resetSubmit">Set new password</button>
        </div>
      </form>
    `);

    $("#resetForm").addEventListener("submit", async (e) => {
      e.preventDefault();
      const msg = $("#resetMsg");
      hideFormMessage(msg);
      const submitBtn = $("#resetSubmit");
      submitBtn.disabled = true;
      submitBtn.textContent = "Saving…";
      try {
        await Api.users.resetPassword(userId, $("#resetPassword").value);
        Sheet.close();
        toast("Password updated. Share the new password with them directly.");
      } catch (err) {
        showFormMessage(msg, err.message || "Could not reset the password.");
        submitBtn.disabled = false;
        submitBtn.textContent = "Set new password";
      }
    });
  }
})();