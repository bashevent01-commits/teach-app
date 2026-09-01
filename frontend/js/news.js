(async function () {
  const session = await initShell("news", ["teacher"]);

  await loadPosts();

  async function loadPosts() {
    const wrap = $("#feed");
    try {
      const posts = await Api.posts.list();
      wrap.innerHTML = posts.length ? posts.map((p) => `
        <div class="card">
          <div class="post-head">
            <div class="who">
              <strong>${escapeHtml(p.title)}</strong>
              <p class="subtle">${formatDate(p.created_at, true)}</p>
            </div>
          </div>
          <div class="post-body">${escapeHtml(p.body)}</div>
          ${p.image_path ? `<img class="post-img" src="${Api.posts.imageUrl(p)}" alt="Photo attached to ${escapeHtml(p.title)}" />` : ""}
          <div class="post-actions">
            ${p.author_id === session.user_id ? `<button class="danger-btn" data-delete="${p.id}">Delete</button>` : `<button class="ghost-btn" data-report="${p.id}">Report</button>`}
          </div>
        </div>
      `).join("") : `<div class="empty-state"><div class="display">No news yet</div><p>Post the first update for your school portal.</p></div>`;

      $$("[data-delete]", wrap).forEach((btn) => {
        btn.addEventListener("click", async () => {
          if (!confirm("Delete this post?")) return;
          try {
            await Api.posts.remove(btn.dataset.delete);
            await loadPosts();
          } catch (err) {
            toast(err.message || "Could not delete the post.");
          }
        });
      });

      $$("[data-report]", wrap).forEach((btn) => {
        btn.addEventListener("click", () => openReportSheet(btn.dataset.report));
      });
    } catch (err) {
      wrap.innerHTML = `<div class="empty-state">${escapeHtml(err.message)}</div>`;
    }
  }

  /* ---------------- Report post sheet ---------------- */

  function openReportSheet(postId) {
    Sheet.open("Report post", `
      <p class="form-error" id="reportMsg"></p>
      <form id="reportForm">
        <label class="field"><span>What's wrong with this post? (optional)</span><textarea id="reportReason" rows="3" placeholder="Let the super admins know what to look at"></textarea></label>
        <div class="form-actions">
          <button type="submit" class="primary-btn" id="reportSubmit">Send report</button>
        </div>
      </form>
    `);

    $("#reportForm").addEventListener("submit", async (e) => {
      e.preventDefault();
      const msg = $("#reportMsg");
      hideFormMessage(msg);
      const submitBtn = $("#reportSubmit");
      submitBtn.disabled = true;
      submitBtn.textContent = "Sending…";
      try {
        await Api.posts.report(postId, $("#reportReason").value.trim());
        Sheet.close();
        toast("Reported to the super admins for review.");
      } catch (err) {
        showFormMessage(msg, err.message || "Could not send the report.");
        submitBtn.disabled = false;
        submitBtn.textContent = "Send report";
      }
    });
  }

  /* ---------------- Compose ---------------- */

  const form = $("#postForm");
  const msg = $("#postMsg");

  $("#newPostBtn").addEventListener("click", () => {
    form.style.display = form.style.display === "none" ? "block" : "none";
    hideFormMessage(msg);
    if (form.style.display === "block") paintIcons(form);
  });
  $("#postCancelBtn").addEventListener("click", () => {
    form.style.display = "none";
    form.reset();
    $("#postImageLabel").textContent = "Add a photo (optional, images only)";
  });

  $("#postImage").addEventListener("change", () => {
    const file = $("#postImage").files[0];
    $("#postImageLabel").textContent = file ? file.name : "Add a photo (optional, images only)";
  });

  form.addEventListener("submit", async (e) => {
    e.preventDefault();
    hideFormMessage(msg);
    const submitBtn = $("#postSubmitBtn");
    submitBtn.disabled = true;
    submitBtn.textContent = "Publishing…";

    const imageFile = $("#postImage").files[0] || null;

    try {
      await Api.posts.create({
        title: $("#postTitle").value.trim(),
        body: $("#postBody").value.trim(),
        image: imageFile,
      });
      form.reset();
      form.style.display = "none";
      $("#postImageLabel").textContent = "Add a photo (optional, images only)";
      await loadPosts();
    } catch (err) {
      showFormMessage(msg, err.message || "Could not publish the post.");
    } finally {
      submitBtn.disabled = false;
      submitBtn.textContent = "Publish";
    }
  });
})();