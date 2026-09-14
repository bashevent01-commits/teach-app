(async function () {
  const session = await initShell("home", ["staff"]);

  let transactions = [];
  let stockItems = [];

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
          <strong>${escapeHtml(t.category)}${t.category_type === "STOCK" && t.quantity ? ` &times; ${t.quantity}` : ""}</strong>
          <span class="subtle">${escapeHtml(t.description || formatDate(t.transaction_date, true))} &middot; ${t.method.toUpperCase()}${t.mpesa_code ? ` &middot; ${escapeHtml(t.mpesa_code)}` : ""}</span>
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

  async function openTxnSheet(flow) {
    const title = flow === "income" ? "Receiving" : "Paying";
    const stockVerb = flow === "income" ? "Sale (removes from stock)" : "Restock (adds to stock)";
    const isTeacher = session.staff_type === "teacher";

    if (!isTeacher) {
      try { stockItems = await Api.stock.list(); } catch { stockItems = []; }
    }

    const stockOptions = stockItems.map((s) =>
      `<option value="${s.id}" data-price="${s.unit_price || 0}" data-qty="${s.quantity}">${escapeHtml(s.name)} (${s.quantity} in stock)</option>`
    ).join("");

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

        ${isTeacher ? "" : `
        <div class="segmented" id="categoryTypeSegmented">
          <button type="button" data-type="OTHER" class="is-active">Other</button>
          <button type="button" data-type="STOCK">Stock item</button>
        </div>`}

        <div id="mpesaPasteBlock" hidden>
          <label class="field"><span>Paste M-Pesa message</span><textarea id="mpesaMessage" rows="3" placeholder="Paste the confirmation SMS here to auto-fill amount, code, and payer name"></textarea></label>
          <button type="button" class="ghost-btn" id="mpesaParseBtn">Auto-fill from message</button>
          <p class="file-hint" id="mpesaParseResult"></p>
        </div>

        <div id="otherCategoryField">
          <label class="field"><span>Category</span><input type="text" id="txnCategory" placeholder="e.g. rent, utilities" /></label>
        </div>

        <div id="stockCategoryFields" hidden>
          ${stockItems.length ? `
            <label class="field"><span>Item</span><select id="txnStockItem">${stockOptions}</select></label>
            <p class="file-hint" id="stockTypeHint">${stockVerb}</p>
            <label class="field"><span>Quantity</span><input type="number" id="txnQuantity" min="0.01" step="0.01" value="1" /></label>
          ` : `<p class="file-hint">No stock items yet — add one on the Stock page first, or use "Other" for this entry.</p>`}
        </div>

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

    let categoryType = "OTHER";
    let mpesaCode = null;
    let mpesaPayerName = null;

    // --- category type toggle ---
    $$("#categoryTypeSegmented button").forEach((btn) => {
      btn.addEventListener("click", () => {
        categoryType = btn.dataset.type;
        $$("#categoryTypeSegmented button").forEach((b) => b.classList.toggle("is-active", b === btn));
        $("#otherCategoryField").hidden = categoryType !== "OTHER";
        $("#stockCategoryFields").hidden = categoryType !== "STOCK";
        if (categoryType === "STOCK") applyStockPricing();
      });
    });

    function applyStockPricing() {
      const select = $("#txnStockItem");
      if (!select) return;
      const opt = select.selectedOptions[0];
      const price = parseFloat(opt?.dataset.price || 0);
      const qty = parseFloat($("#txnQuantity").value || 1);
      if (price > 0) $("#txnAmount").value = (price * qty).toFixed(2);
    }
    if ($("#txnStockItem")) {
      $("#txnStockItem").addEventListener("change", applyStockPricing);
      $("#txnQuantity").addEventListener("input", applyStockPricing);
    }

    // --- method toggle shows/hides the M-Pesa paste block ---
    function applyMethodVisibility() {
      $("#mpesaPasteBlock").hidden = $("#txnMethod").value !== "mpesa";
    }
    $("#txnMethod").addEventListener("change", applyMethodVisibility);
    applyMethodVisibility();

    // --- M-Pesa paste-to-fill ---
    $("#mpesaParseBtn").addEventListener("click", async () => {
      const message = $("#mpesaMessage").value.trim();
      const resultEl = $("#mpesaParseResult");
      if (!message) return;
      resultEl.textContent = "Reading message…";
      try {
        const result = await Api.mpesa.parse(message);
        if (!result.matched) {
          resultEl.textContent = "Couldn't recognize that as an M-Pesa message — fields left as-is.";
          return;
        }
        mpesaCode = result.code;
        mpesaPayerName = result.payer_name;
        if (result.amount != null && categoryType !== "STOCK") $("#txnAmount").value = result.amount;
        const parts = [];
        if (result.code) parts.push(`Code ${result.code}`);
        if (result.amount != null) parts.push(`KES ${result.amount}`);
        if (result.payer_name) parts.push(`from ${result.payer_name}`);
        resultEl.textContent = parts.length
          ? `Filled in: ${parts.join(" · ")}. Item and quantity still need picking manually — M-Pesa doesn't carry that.`
          : "Recognized as M-Pesa, but couldn't read the details clearly.";
      } catch (err) {
        resultEl.textContent = err.message || "Could not read that message.";
      }
    });

    $("#txnImage").addEventListener("change", () => {
      const file = $("#txnImage").files[0];
      $("#txnImageName").textContent = file ? `Selected: ${file.name}` : "Receipt, till slip, or similar evidence — not required.";
    });

    $("#txnForm").addEventListener("submit", async (e) => {
      e.preventDefault();
      const msg = $("#txnMsg");
      hideFormMessage(msg);
      const submitBtn = $("#txnSubmit");

      if (categoryType === "OTHER" && !$("#txnCategory").value.trim()) {
        showFormMessage(msg, "Category is required.");
        return;
      }
      if (categoryType === "STOCK" && !stockItems.length) {
        showFormMessage(msg, "Add a stock item first, or switch to \"Other\".");
        return;
      }

      submitBtn.disabled = true;
      submitBtn.textContent = "Saving…";

      try {
        await Api.transactions.create({
          type: flow,
          method: $("#txnMethod").value,
          category_type: categoryType,
          category: categoryType === "OTHER" ? $("#txnCategory").value.trim() : undefined,
          stock_item_id: categoryType === "STOCK" ? parseInt($("#txnStockItem").value, 10) : undefined,
          quantity: categoryType === "STOCK" ? parseFloat($("#txnQuantity").value) : undefined,
          amount: parseFloat($("#txnAmount").value),
          description: $("#txnDescription").value.trim() || null,
          mpesa_code: mpesaCode,
          mpesa_payer_name: mpesaPayerName,
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
