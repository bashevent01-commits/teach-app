(async function () {
  await initShell("review", ["super_admin"]);

  let currentPostFilter = "all";

  await loadReports();
  await loadPosts();

  function postImageUrl(imagePath) {
    return imagePath ? `${API_BASE}/${imagePath}` : null;
  }

  /* ---------------- Reports queue ---------------- */

  async function loadReports() {
    const wrap = $("#reportsList");
    try {
      const reports = await Api.moderation.reports();
      const open = reports.filter((r) => !r.resolved_at);
      $("#openReportsPill").textContent = `${open.length} open report${open.length === 1 ? "" : "s"}`;

      wrap.innerHTML = reports.length ? reports.map((r) => `
        <div class="card">
          <div class="post-head">
            <div class="avatar">${initials(r.reporter_name || "?")}</div>
            <div class="who">
              <strong>Reported by ${escapeHtml(r.reporter_name || "Unknown")}</strong>
              <p class="subtle">${formatDate(r.created_at, true)} &middot; ${r.post ? escapeHtml(r.post.school_name || "Unknown school") : "Post no longer exists"}</p>
            </div>
            <span class="pill ${r.resolved_at ? "ok" : "warn"}" style="margin-left:auto;">${r.resolved_at ? "Resolved" : "Open"}</span>
          </div>
          ${r.reason ? `<p class="post-body"><em>Reason: ${escapeHtml(r.reason)}</em></p>` : ""}
          ${r.post ? `
            <div class="card" style="background: var(--surface-2); box-shadow:none;">
              <strong>${escapeHtml(r.post.title)}</strong>
              <p class="post-body">${escapeHtml(r.post.body)}</p>
              ${r.post.image_path ? `<img src="${postImageUrl(r.post.image_path)}" alt="" style="width:100%;border-radius:10px;margin:8px 0;display:block;max-height:340px;object-fit:cover;" />` : ""}
              <p class="subtle">By ${escapeHtml(r.post.author_name || "Unknown teacher")}</p>
            </div>
          ` : `<p class="hint">The reported post has already been removed.</p>`}
          ${!r.resolved_at ? `
            <div class="post-actions">
              <button class="ghost-btn" data-dismiss="${r.id}">Dismiss report</button>
              ${r.post ? `<button class="danger-btn" data-remove="${r.id}">Remove post</button>` : ""}
            </div>
          ` : `<p class="hint">Resolution: ${escapeHtml(r.resolution || "—")}</p>`}
        </div>
      `).join("") : `<div class="empty-state"><div class="display">No reports yet</div><p>Anything a teacher flags will show up here.</p></div>`;

      $$("[data-dismiss]", wrap).forEach((btn) => btn.addEventListener("click", () => resolve(btn.dataset.dismiss, "dismiss")));
      $$("[data-remove]", wrap).forEach((btn) => btn.addEventListener("click", () => {
        if (confirm("Remove this post permanently? This cannot be undone.")) resolve(btn.dataset.remove, "remove_post");
      }));
    } catch (err) {
      wrap.innerHTML = `<div class="empty-state">${escapeHtml(err.message)}</div>`;
    }
  }

  async function resolve(reportId, action) {
    try {
      await Api.moderation.resolveReport(reportId, action);
      toast(action === "dismiss" ? "Report dismissed." : "Post removed.");
      await loadReports();
      await loadPosts();
    } catch (err) {
      toast(err.message || "Could not resolve the report.");
    }
  }

  /* ---------------- All posts browser ---------------- */

  async function loadPosts() {
    const wrap = $("#postsList");
    try {
      const params = currentPostFilter === "images" ? { has_image: "true" } : {};
      const posts = await Api.moderation.posts(params);

      wrap.innerHTML = posts.length ? posts.map((p) => `
        <div class="card">
          <div class="post-head">
            <div class="avatar">${initials(p.author_name || "?")}</div>
            <div class="who">
              <strong>${escapeHtml(p.title)}</strong>
              <p class="subtle">${escapeHtml(p.author_name || "Unknown")} &middot; ${escapeHtml(p.school_name || "Unknown school")} &middot; ${formatDate(p.created_at, true)}</p>
            </div>
            ${p.open_report_count > 0 ? `<span class="badge badge-inactive" style="margin-left:auto;">${p.open_report_count} report${p.open_report_count === 1 ? "" : "s"}</span>` : ""}
          </div>
          <div class="post-body">${escapeHtml(p.body)}</div>
          ${p.image_path ? `<img src="${postImageUrl(p.image_path)}" alt="" style="width:100%;border-radius:10px;margin:8px 0;display:block;max-height:340px;object-fit:cover;" />` : ""}
        </div>
      `).join("") : `<div class="empty-state"><div class="display">No posts</div><p>Nothing matches this filter yet.</p></div>`;
    } catch (err) {
      wrap.innerHTML = `<div class="empty-state">${escapeHtml(err.message)}</div>`;
    }
  }

  $$(".filter[data-filter]", $("#postFilters")).forEach((btn) => {
    btn.addEventListener("click", () => {
      currentPostFilter = btn.dataset.filter;
      $$(".filter[data-filter]", $("#postFilters")).forEach((b) => b.classList.toggle("is-active", b === btn));
      loadPosts();
    });
  });
})();