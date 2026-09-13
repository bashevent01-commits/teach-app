(async function () {
  const session = await initShell("settings", ["staff", "super_admin"]);

  $("#acctName").textContent = session.full_name;
  $("#acctRole").textContent = session.role === "super_admin" ? "Super admin" : "Staff";

  // Username isn't in the login response — pull it from /api/users if we
  // have access (super admin only); staff just see role + institution.
  const usernameEl = $("#acctUsername");
  usernameEl.textContent = "—";
  if (session.role === "super_admin") {
    try {
      const users = await Api.users.list();
      const me = users.find((u) => u.id === session.user_id);
      if (me) usernameEl.textContent = me.username;
    } catch { /* non-fatal */ }
  }

  const institutionRow = $("#acctSchoolRow");
  if (session.role === "super_admin") {
    institutionRow.style.display = "none";
  } else {
    try {
      const institution = await Api.institutions.get(session.institution_id);
      $("#acctSchool").textContent = institution.name;
    } catch {
      $("#acctSchool").textContent = "—";
    }
  }

  if (session.role === "staff") {
    const card = $("#auditSharingCard");
    card.hidden = false;
    const segmented = $("#auditSharingSegmented");

    function paintSharing(shareAudits) {
      $$("button", segmented).forEach((btn) => {
        btn.classList.toggle("is-active", (btn.dataset.share === "on") === shareAudits);
      });
    }

    try {
      const me = await Api.users.me();
      paintSharing(me.share_audits);
    } catch { /* leave at default paint */ }

    $$("button", segmented).forEach((btn) => {
      btn.addEventListener("click", async () => {
        const shareAudits = btn.dataset.share === "on";
        try {
          const updated = await Api.users.updateAuditSharing(shareAudits);
          paintSharing(updated.share_audits);
          toast(shareAudits ? "Your audits are now visible to other staff at your institution." : "Your audits are now private again.");
        } catch (err) {
          toast(err.message || "Could not update audit sharing.");
        }
      });
    });
  }

  $("#logoutBtn").addEventListener("click", logout);

  $("#pwForm").addEventListener("submit", async (e) => {
    e.preventDefault();
    const msg = $("#pwMsg");
    hideFormMessage(msg);
    const submitBtn = $("#pwSubmit");
    submitBtn.disabled = true;
    submitBtn.textContent = "Updating…";
    try {
      await Api.users.changeMyPassword($("#pwCurrent").value, $("#pwNew").value);
      toast("Password updated. Please log back in.");
      // The server just invalidated this session's token as part of the
      // change, so the cookie/bearer token this page is holding is
      // already dead — send them to log in fresh rather than leaving
      // them on a page that will 401 on the next action.
      await logout();
    } catch (err) {
      showFormMessage(msg, err.message || "Could not update your password.");
      submitBtn.disabled = false;
      submitBtn.textContent = "Update password";
    }
  });

  // Theme segmented control
  $$("#modeSegmented button").forEach((btn) => {
    btn.addEventListener("click", () => setTheme(btn.dataset.mode));
  });
  applyTheme();
})();
