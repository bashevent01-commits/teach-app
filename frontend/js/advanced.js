(async function () {
  const session = await initShell("settings", ["staff", "institution_admin"]);
  if (session.role === "staff" && !session.individual) location.href = homePageFor(session.role);
})();
