(async function () {
  const session = await initShell("stock", ["staff", "institution_admin"]);
  if (session.role === "staff" && session.staff_type === "teacher") {
    location.href = "home.html";
    return;
  }

  let items = [];

  await loadItems();

  async function loadItems() {
    const body = $("#stockBody");
    try {
      items = await Api.stock.list();
      body.innerHTML = items.length ? items.map((s) => `
        <tr>
          <td><strong>${escapeHtml(s.name)}</strong></td>
          <td>${escapeHtml(s.description || "—")}</td>
          <td>${s.unit_price != null ? money(s.unit_price) : "—"}</td>
          <td>${s.quantity}</td>
          <td>
            <div class="row-actions">
              <button class="ghost-btn" data-edit="${s.id}">Edit</button>
              <button class="danger-btn" data-delete="${s.id}">Delete</button>
            </div>
          </td>
        </tr>
      `).join("") : `<tr class="empty-row"><td colspan="5">No stock items yet. Add one to start recording sales and restocks against it.</td></tr>`;

      $$("[data-edit]", body).forEach((btn) => btn.addEventListener("click", () => openEditSheet(btn.dataset.edit)));
      $$("[data-delete]", body).forEach((btn) => btn.addEventListener("click", () => deleteItem(btn.dataset.delete)));
    } catch (err) {
      body.innerHTML = `<tr class="empty-row"><td colspan="5">${escapeHtml(err.message)}</td></tr>`;
    }
  }

  async function deleteItem(id) {
    const item = items.find((s) => String(s.id) === String(id));
    if (!item) return;
    if (!confirm(`Delete "${item.name}"? This only works if it has no recorded transactions yet.`)) return;
    try {
      await Api.stock.remove(id);
      toast("Stock item deleted.");
      await loadItems();
    } catch (err) {
      toast(err.message || "Could not delete this item.");
    }
  }

  /* ---------------- Add item sheet ---------------- */

  $("#newStockBtn").addEventListener("click", () => {
    Sheet.open("Add item", `
      <p class="form-error" id="stockMsg"></p>
      <form id="stockForm">
        <label class="field"><span>Name</span><input type="text" id="stockName" required /></label>
        <label class="field"><span>Description (optional)</span><input type="text" id="stockDescription" /></label>
        <label class="field"><span>Unit price (optional, KES)</span><input type="number" id="stockPrice" min="0" step="0.01" /></label>
        <label class="field"><span>Starting quantity</span><input type="number" id="stockQuantity" min="0" step="0.01" value="0" /></label>
        <p class="file-hint">After this, quantity only moves through Receiving (sale) and Paying (restock) entries on Home.</p>
        <div class="form-actions">
          <button type="submit" class="primary-btn" id="stockSubmit">Add item</button>
        </div>
      </form>
    `);

    $("#stockForm").addEventListener("submit", async (e) => {
      e.preventDefault();
      const msg = $("#stockMsg");
      hideFormMessage(msg);
      const submitBtn = $("#stockSubmit");
      submitBtn.disabled = true;
      submitBtn.textContent = "Adding…";
      try {
        await Api.stock.create({
          name: $("#stockName").value.trim(),
          description: $("#stockDescription").value.trim() || null,
          unit_price: $("#stockPrice").value ? parseFloat($("#stockPrice").value) : null,
          quantity: parseFloat($("#stockQuantity").value || 0),
        });
        Sheet.close();
        toast("Stock item added.");
        await loadItems();
      } catch (err) {
        showFormMessage(msg, err.message || "Could not add the item.");
        submitBtn.disabled = false;
        submitBtn.textContent = "Add item";
      }
    });
  });

  /* ---------------- Edit item sheet ---------------- */

  function openEditSheet(id) {
    const item = items.find((s) => String(s.id) === String(id));
    if (!item) return;

    Sheet.open("Edit item", `
      <p class="form-error" id="editStockMsg"></p>
      <form id="editStockForm">
        <label class="field"><span>Name</span><input type="text" id="editStockName" value="${escapeHtml(item.name)}" required /></label>
        <label class="field"><span>Description (optional)</span><input type="text" id="editStockDescription" value="${escapeHtml(item.description || "")}" /></label>
        <label class="field"><span>Unit price (optional, KES)</span><input type="number" id="editStockPrice" min="0" step="0.01" value="${item.unit_price ?? ""}" /></label>
        <p class="file-hint">Current quantity: ${item.quantity} — edit this via a Receiving/Paying entry on Home, not here.</p>
        <div class="form-actions">
          <button type="submit" class="primary-btn" id="editStockSubmit">Save changes</button>
        </div>
      </form>
    `);

    $("#editStockForm").addEventListener("submit", async (e) => {
      e.preventDefault();
      const msg = $("#editStockMsg");
      hideFormMessage(msg);
      const submitBtn = $("#editStockSubmit");
      submitBtn.disabled = true;
      submitBtn.textContent = "Saving…";
      try {
        await Api.stock.update(id, {
          name: $("#editStockName").value.trim(),
          description: $("#editStockDescription").value.trim() || null,
          unit_price: $("#editStockPrice").value ? parseFloat($("#editStockPrice").value) : null,
        });
        Sheet.close();
        toast("Stock item updated.");
        await loadItems();
      } catch (err) {
        showFormMessage(msg, err.message || "Could not update the item.");
        submitBtn.disabled = false;
        submitBtn.textContent = "Save changes";
      }
    });
  }
})();
