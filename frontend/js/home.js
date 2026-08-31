(async function () {
  const session = await initShell("home", ["teacher"]);

  let transactions = [];

  await loadData();

  async function loadData() {
    try {
      transactions = await Api.transactions.list();
      renderOverview();
      renderRecent();
    } catch (err) {
      $("#recentList").innerHTML = `<li class="empty-state">${escapeHtml(err.message)}</li>`;
    }
  }

  function renderOverview() {
    const income = transactions.filter((t) => t.type === "income").reduce((s, t) => s + parseFloat(t.amount), 0);
    const expense = transactions.filter((t) => t.type === "expense").reduce((s, t) => s + parseFloat(t.amount), 0);
    const net = income - expense;

    $("#kpiIncome").textContent = money(income);
    $("#kpiExpense").textContent = money(expense);
    $("#kpiNet").textContent = money(net);
    $("#totalPill").textContent = `Net · ${money(net)}`;
  }

  function renderRecent() {
    const list = $("#recentList");
    const recent = [...transactions]
      .sort((a, b) => new Date(b.transaction_date) - new Date(a.transaction_date))
      .slice(0, 6);

    list.innerHTML = recent.length ? recent.map((t) => `
      <li class="txn">
        <span class="txn-badge ${t.type === "income" ? "in" : "out"}">
          <span class="ico" data-ico="${t.type === "income" ? "in" : "out"}"></span>
        </span>
        <span class="txn-info">
          <strong>${escapeHtml(t.category)}</strong>
          <span class="subtle">${escapeHtml(t.description || formatDate(t.transaction_date, true))} &middot; ${t.method.toUpperCase()}</span>
        </span>
        ${t.image_path ? `<a class="ghost-btn" href="${Api.transactions.imageUrl(t)}" target="_blank" rel="noopener">Photo</a>` : ""}
        <span class="txn-amount ${t.type === "income" ? "in" : "out"}">${t.type === "income" ? "+" : "−"}${money(t.amount)}</span>
      </li>
    `).join("") : `<li class="empty-state">No transactions recorded yet.</li>`;

    paintIcons(list);
  }

  /* ---------------- Receiving / Paying sheet ---------------- */

  $$(".action-card[data-flow]").forEach((btn) => {
    btn.addEventListener("click", () => openTxnSheet(btn.dataset.flow));
  });

  function openTxnSheet(flow) {
    const title = flow === "income" ? "Receiving" : "Paying";

    Sheet.open(title, `
      <p class="form-error" id="txnMsg"></p>
      <form id="txnForm">
        <label class="field"><span>Method</span>
          <select id="txnMethod">
            <option value="cash">Cash</option>
            <option value="mpesa">M-Pesa</option>
            <option value="bank">Bank</option>
          </select>
        </label>
        <label class="field"><span>Category</span><input type="text" id="txnCategory" placeholder="e.g. tuition fees, utilities" required /></label>
        <label class="field"><span>Amount (KES)</span><input type="number" id="txnAmount" min="0.01" step="0.01" required /></label>
        <label class="field"><span>Description (optional)</span><input type="text" id="txnDescription" /></label>
        <label class="chip-input wide">
          <span class="ico" data-ico="image"></span> Add a photo (optional)
          <input type="file" id="txnImage" accept="image/png,image/jpeg,image/webp" hidden />
        </label>
        <p class="file-hint" id="txnImageName">Receipt, till slip, or similar evidence — not required.</p>
        <p class="file-hint">Date and time are recorded automatically.</p>
        <div class="form-actions">
          <button type="submit" class="primary-btn" id="txnSubmit">Save ${title.toLowerCase()}</button>
        </div>
      </form>
    `);

    $("#txnImage").addEventListener("change", () => {
      const file = $("#txnImage").files[0];
      $("#txnImageName").textContent = file ? `Selected: ${file.name}` : "Receipt, till slip, or similar evidence — not required.";
    });

    $("#txnForm").addEventListener("submit", async (e) => {
      e.preventDefault();
      const msg = $("#txnMsg");
      hideFormMessage(msg);
      const submitBtn = $("#txnSubmit");
      submitBtn.disabled = true;
      submitBtn.textContent = "Saving…";

      try {
        await Api.transactions.create({
          type: flow,
          method: $("#txnMethod").value,
          category: $("#txnCategory").value.trim(),
          amount: parseFloat($("#txnAmount").value),
          description: $("#txnDescription").value.trim() || null,
          image: $("#txnImage").files[0] || null,
        });
        Sheet.close();
        toast(`${title} recorded.`);
        await loadData();
      } catch (err) {
        showFormMessage(msg, err.message || "Could not save the transaction.");
        submitBtn.disabled = false;
        submitBtn.textContent = `Save ${title.toLowerCase()}`;
      }
    });
  }
})();