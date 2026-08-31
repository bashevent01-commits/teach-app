(async function () {
  const session = await initShell("settings", ["teacher", "super_admin"]);

  $("#acctName").textContent = session.full_name;
  $("#acctRole").textContent = session.role === "super_admin" ? "Super admin" : "Teacher";

  // Username isn't in the login response — pull it from /api/users if we
  // have access (super admin only); teachers just see role + school.
  const usernameEl = $("#acctUsername");
  usernameEl.textContent = "—";
  if (session.role === "super_admin") {
    try {
      const users = await Api.users.list();
      const me = users.find((u) => u.id === session.user_id);
      if (me) usernameEl.textContent = me.username;
    } catch { /* non-fatal */ }
  }

  const schoolRow = $("#acctSchoolRow");
  if (session.role === "super_admin") {
    schoolRow.style.display = "none";
  } else {
    try {
      const school = await Api.schools.get(session.school_id);
      $("#acctSchool").textContent = school.name;
    } catch {
      $("#acctSchool").textContent = "—";
    }
  }

  $("#logoutBtn").addEventListener("click", logout);

  // Theme segmented control
  $$("#modeSegmented button").forEach((btn) => {
    btn.addEventListener("click", () => setTheme(btn.dataset.mode));
  });
  applyTheme();
})();
