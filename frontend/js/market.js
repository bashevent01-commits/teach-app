(async function () {
  await initShell("market", ["super_admin"]);

  let categories = [];
  let allCategories = []; // for the manage-categories sheet, unfiltered by the 5-institution threshold
  let chart = null;

  await loadCategories();
  await loadAllCategoriesForManage();

  async function loadCategories() {
    const body = $("#categoriesBody");
    try {
      categories = await Api.marketAnalysis.categories();
      body.innerHTML = categories.length ? categories.map((c) => `
        <tr>
          <td><strong>${escapeHtml(c.category_name)}</strong></td>
          <td>${c.institution_count}</td>
          <td>${money(c.average_price)}</td>
          <td>${money(c.median_price)}</td>
          <td>${money(c.min_price)}</td>
          <td>${money(c.max_price)}</td>
          <td>${c.total_quantity_sold}</td>
          <td><button class="ghost-btn" data-view="${c.category_id}">View trend</button></td>
        </tr>
      `).join("") : `<tr class="empty-row"><td colspan="8">No category has reached the 5-institution minimum yet — insights will appear here as more institutions add priced stock items.</td></tr>`;

      $$("[data-view]", body).forEach((btn) => btn.addEventListener("click", () => openDetail(btn.dataset.view)));
    } catch (err) {
      body.innerHTML = `<tr class="empty-row"><td colspan="8">${escapeHtml(err.message)}</td></tr>`;
    }
  }

  async function loadAllCategoriesForManage() {
    try {
      allCategories = await Api.productCategories.list();
    } catch {
      allCategories = [];
    }
  }

  /* ---------------- Detail / drill-down view ---------------- */

  async function openDetail(categoryId) {
    const listView = $("#listView");
    const detailView = $("#detailView");
    listView.hidden = true;
    detailView.hidden = false;
    $("#detailTitle").textContent = "Loading…";

    try {
      const d = await Api.marketAnalysis.categoryDetail(categoryId);
      $("#detailTitle").textContent = d.category_name;
      $("#kpiInstitutions").textContent = d.institution_count;
      $("#kpiAvg").textContent = money(d.average_price);
      $("#kpiMinMax").textContent = `${money(d.min_price)} / ${money(d.max_price)}`;
      $("#kpiQty").textContent = d.total_quantity_sold;

      renderTrend(d.trend);
      renderRegions(d.regional_breakdown);
    } catch (err) {
      $("#detailTitle").textContent = "Couldn't load this category";
      toast(err.message || "Couldn't load category detail.");
    }
  }

  function renderTrend(trend) {
    const hint = $("#trendHint");
    const canvas = $("#trendChart");
    if (chart) {
      chart.destroy();
      chart = null;
    }
    if (!trend.length) {
      hint.textContent = "Not enough months with 5+ contributing institutions yet to show a trend.";
      canvas.style.display = "none";
      return;
    }
    hint.textContent = "Each point only shown where at least 5 institutions contributed that month.";
    canvas.style.display = "";
    chart = new Chart(canvas.getContext("2d"), {
      type: "line",
      data: {
        labels: trend.map((t) => t.month),
        datasets: [{
          label: "Average price (KES)",
          data: trend.map((t) => t.average_price),
          borderColor: "#0f766e",
          backgroundColor: "rgba(15,118,110,0.12)",
          tension: 0.25,
          fill: true,
        }],
      },
      options: {
        responsive: true,
        plugins: { legend: { display: false } },
        scales: { y: { beginAtZero: false } },
      },
    });
  }

  function renderRegions(regions) {
    const body = $("#regionBody");
    body.innerHTML = regions.length ? regions.map((r) => `
      <tr>
        <td>${escapeHtml(r.region)}</td>
        <td>${r.institution_count}</td>
        <td>${money(r.average_price)}</td>
        <td>${money(r.min_price)}</td>
        <td>${money(r.max_price)}</td>
      </tr>
    `).join("") : `<tr class="empty-row"><td colspan="5">No region has reached the 5-institution minimum for this category yet.</td></tr>`;
  }

  $("#backToListBtn").addEventListener("click", () => {
    $("#detailView").hidden = true;
    $("#listView").hidden = false;
    loadCategories(); // pick up anything that's changed since
  });

  /* ---------------- Manage categories sheet ---------------- */

  $("#manageCategoriesBtn").addEventListener("click", async () => {
    await loadAllCategoriesForManage();
    renderManageSheet();
  });

  function renderManageSheet() {
    Sheet.open("Manage categories", `
      <p class="form-error" id="categoryMsg"></p>
      <form id="addCategoryForm" class="inline-form">
        <label class="field wide"><span>New category name</span><input type="text" id="newCategoryName" placeholder="e.g. Pencils" required /></label>
        <button type="submit" class="primary-btn">Add</button>
      </form>
      <div class="table-scroll">
      <table class="data-table">
        <thead><tr><th>Category</th><th></th></tr></thead>
        <tbody id="manageCategoriesBody">
          ${allCategories.length ? allCategories.map((c) => `
            <tr>
              <td><input type="text" class="cat-rename" data-id="${c.id}" value="${escapeHtml(c.name)}" /></td>
              <td>
                <div class="row-actions">
                  <button class="ghost-btn" data-rename="${c.id}">Save</button>
                  <button class="danger-btn" data-remove="${c.id}">Delete</button>
                </div>
              </td>
            </tr>
          `).join("") : `<tr class="empty-row"><td colspan="2">No categories yet — add the first one above.</td></tr>`}
        </tbody>
      </table>
      </div>
    `);

    $("#addCategoryForm").addEventListener("submit", async (e) => {
      e.preventDefault();
      const msg = $("#categoryMsg");
      hideFormMessage(msg);
      const name = $("#newCategoryName").value.trim();
      if (!name) return;
      try {
        await Api.productCategories.create(name);
        toast("Category added.");
        await loadAllCategoriesForManage();
        renderManageSheet();
      } catch (err) {
        showFormMessage(msg, err.message || "Could not add category.");
      }
    });

    $$("[data-rename]").forEach((btn) => btn.addEventListener("click", async () => {
      const id = btn.dataset.rename;
      const input = $(`.cat-rename[data-id="${id}"]`);
      const name = input.value.trim();
      if (!name) return;
      try {
        await Api.productCategories.update(id, name);
        toast("Category renamed.");
        await loadAllCategoriesForManage();
      } catch (err) {
        toast(err.message || "Could not rename category.");
      }
    }));

    $$("[data-remove]").forEach((btn) => btn.addEventListener("click", async () => {
      const id = btn.dataset.remove;
      if (!confirm("Delete this category? Only possible if no stock item uses it.")) return;
      try {
        await Api.productCategories.remove(id);
        toast("Category deleted.");
        await loadAllCategoriesForManage();
        renderManageSheet();
      } catch (err) {
        toast(err.message || "Could not delete category.");
      }
    }));
  }
})();
