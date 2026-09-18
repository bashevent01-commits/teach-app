(async function () {
  const session = await initShell("stock", ["staff", "institution_admin"]);
  if (session.role === "staff" && session.staff_type === "teacher") {
    location.href = "home.html";
    return;
  }

  let items = [];
  let categories = [];

  await loadCategories();
  await loadItems();

  async function loadCategories() {
    try {
      categories = await Api.productCategories.list();
    } catch {
      categories = [];
    }
  }

  function categoryOptions(selectedId) {
    if (!categories.length) return `<option value="">No categories yet — ask a super admin to add one</option>`;
    return categories.map((c) => `<option value="${c.id}" ${String(c.id) === String(selectedId) ? "selected" : ""}>${escapeHtml(c.name)}</option>`).join("");
  }

  async function loadItems() {
    const body = $("#stockBody");
    try {
      items = await Api.stock.list();
      body.innerHTML = items.length ? items.map((s) => `
        <tr>
          <td><strong>${escapeHtml(s.name)}</strong></td>
          <td>${s.category_name ? `<span class="badge">${escapeHtml(s.category_name)}</span>` : "—"}</td>
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
      `).join("") : `<tr class="empty-row"><td colspan="6">No stock items yet. Add one to start recording sales and restocks against it.</td></tr>`;

      $$("[data-edit]", body).forEach((btn) => btn.addEventListener("click", () => openEditSheet(btn.dataset.edit)));
      $$("[data-delete]", body).forEach((btn) => btn.addEventListener("click", () => deleteItem(btn.dataset.delete)));
    } catch (err) {
      body.innerHTML = `<tr class="empty-row"><td colspan="6">${escapeHtml(err.message)}</td></tr>`;
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

  $("#newStockBtn").addEventListener("click", async () => {
    await loadCategories();
    Sheet.open("Add item", `
      <p class="form-error" id="stockMsg"></p>
      <form id="stockForm">
        <label class="field"><span>Name</span><input type="text" id="stockName" required /></label>
        <label class="field"><span>Product category</span>
          <select id="stockCategory" required>${categoryOptions()}</select>
        </label>
        <p class="file-hint">Categorizes this item for cross-institution market insights. Pick the closest match, or ask a super admin to add a new category.</p>
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
        const categoryId = $("#stockCategory").value;
        if (!categoryId) throw new Error("Please pick a product category.");
        await Api.stock.create({
          name: $("#stockName").value.trim(),
          category_id: parseInt(categoryId, 10),
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

  async function openEditSheet(id) {
    const item = items.find((s) => String(s.id) === String(id));
    if (!item) return;
    await loadCategories();

    Sheet.open("Edit item", `
      <p class="form-error" id="editStockMsg"></p>
      <form id="editStockForm">
        <label class="field"><span>Name</span><input type="text" id="editStockName" value="${escapeHtml(item.name)}" required /></label>
        <label class="field"><span>Product category</span>
          <select id="editStockCategory" required>${categoryOptions(item.category_id)}</select>
        </label>
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
        const categoryId = $("#editStockCategory").value;
        await Api.stock.update(id, {
          name: $("#editStockName").value.trim(),
          category_id: categoryId ? parseInt(categoryId, 10) : undefined,
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
