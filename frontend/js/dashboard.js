(async function () {
  const session = await initShell("dashboard", ["super_admin"]);

  try {
    const [schools, users, reports] = await Promise.all([
      Api.schools.list(),
      Api.users.list(),
      Api.moderation.reports(),
    ]);
    $("#kpiSchools").textContent = schools.length;
    $("#kpiTeachers").textContent = users.filter((u) => u.role === "teacher").length;
    $("#kpiActive").textContent = users.filter((u) => u.is_active).length;
    $("#kpiReports").textContent = reports.filter((r) => !r.resolved_at).length;
  } catch (err) {
    toast(err.message || "Could not load the overview.");
  }
})();