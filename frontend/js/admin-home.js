(async function () {
  const session = await initShell("admin-home", ["institution_admin"]);

  try {
    const [users, stockItems] = await Promise.all([
      Api.users.list(),
      Api.stock.list(),
    ]);
    $("#kpiStaff").textContent = users.filter((u) => u.role === "staff").length;
    $("#kpiActive").textContent = users.filter((u) => u.is_active).length;
    $("#kpiTeacherCount").textContent = users.filter((u) => u.role === "staff" && u.staff_type === "teacher").length;
    $("#kpiStockItems").textContent = stockItems.length;
  } catch (err) {
    toast(err.message || "Could not load the overview.");
  }
})();
