(async function () {
  const session = await initShell("books", ["institution_admin"]);

  let staff = [];
  const filterEl = $("#staffFilter");

  try {
    staff = (await Api.users.list()).filter((u) => u.role === "staff");
    filterEl.innerHTML = `<option value="">All staff (collective)</option>` +
      staff.map((u) => `<option value="${u.id}">${escapeHtml(u.full_name)} (${escapeHtml(u.username)})</option>`).join("");
  } catch (err) {
    toast(err.message || "Could not load staff.");
  }

  const nameOf = (id) => staff.find((u) => u.id === id)?.full_name || "—";
  const methodName = (m) => (m === "mpesa" ? "M-Pesa" : m[0].toUpperCase() + m.slice(1));

  function params() {
    const p = {};
    if (filterEl.value) p.staff_id = filterEl.value;
    if ($("#booksFrom").value) p.start_date = new Date($("#booksFrom").value + "T00:00:00").toISOString();
    if ($("#booksTo").value) p.end_date = new Date($("#booksTo").value + "T23:59:59").toISOString();
    return p;
  }

  async function load() {
    const p = params();
    try {
      const [summary, tb, txns] = await Promise.all([
        Api.accounting.summary(p),
        Api.accounting.trialBalance(p),
        Api.transactions.list(p),
      ]);
      $("#bkIncome").textContent = money(summary.income);
      $("#bkExpense").textContent = money(summary.expenses);
      $("#bkNet").textContent = money(summary.net);
      const held = Object.fromEntries(summary.money.map((m) => [m.key, m.balance]));
      $("#bkCash").textContent = money(held.cash || 0);
      $("#bkMpesa").textContent = money(held.mpesa || 0);
      $("#bkBank").textContent = money(held.bank || 0);

      $("#tbBody").innerHTML = tb.accounts.length
        ? tb.accounts.map((a) => `<tr><td>${a.code}</td><td>${escapeHtml(a.name)}</td><td>${parseFloat(a.debit) ? money(a.debit) : ""}</td><td>${parseFloat(a.credit) ? money(a.credit) : ""}</td></tr>`).join("") +
          `<tr><td></td><td><strong>Total</strong></td><td><strong>${money(tb.total_debit)}</strong></td><td><strong>${money(tb.total_credit)}</strong></td></tr>`
        : `<tr class="empty-row"><td colspan="4">No entries yet.</td></tr>`;
      $("#tbStatus").textContent = tb.balanced ? "Balanced" : "Out of balance";
      $("#tbStatus").className = tb.balanced ? "pill" : "pill warn";

      $("#recBody").innerHTML = txns.length
        ? txns.map((t) => `<tr><td>${formatDate(t.transaction_date, true)}</td><td>${escapeHtml(nameOf(t.recorded_by_id))}</td><td><span class="badge badge-${t.type}">${t.type}</span></td><td>${methodName(t.method)}</td><td>${escapeHtml(t.category)}</td><td>${money(t.amount)}</td></tr>`).join("")
        : `<tr class="empty-row"><td colspan="6">No records for this selection.</td></tr>`;
    } catch (err) {
      toast(err.message || "Could not load the books.");
    }
  }

  [filterEl, $("#booksFrom"), $("#booksTo")].forEach((el) => el.addEventListener("change", load));
  await load();
})();
