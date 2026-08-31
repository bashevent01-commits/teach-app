(async function () {
  await initShell("activity", ["super_admin"]);

  const ACTION_LABELS = {
    login_success: "Signed in",
    login_failed: "Failed sign-in",
    login_blocked_locked: "Sign-in blocked (locked)",
    login_blocked_inactive: "Sign-in blocked (disabled)",
    account_locked: "Account locked",
    user_created: "Account created",
    user_deactivated: "Account deactivated",
    user_reactivated: "Account reactivated",
    password_reset: "Password reset",
    school_created: "School added",
    school_logo_updated: "School icon changed",
    audit_updated: "Audit edited",
    audit_deleted: "Audit deleted",
    audit_finalized: "Audit finalized",
    transaction_updated: "Transaction edited",
    transaction_deleted: "Transaction deleted",
  };

  try {
    const entries = await Api.activityLog.list({ limit: "200" });
    $("#activityCountPill").textContent = `${entries.length} entries`;

    const body = $("#activityBody");
    body.innerHTML = entries.length ? entries.map((a) => `
      <tr>
        <td class="subtle" style="white-space:nowrap;">${formatDate(a.created_at, true)}</td>
        <td>${escapeHtml(a.actor_username || "—")}</td>
        <td>${escapeHtml(ACTION_LABELS[a.action] || a.action)}</td>
        <td>${escapeHtml(a.detail || "—")}</td>
        <td class="subtle">${escapeHtml(a.ip_address || "—")}</td>
      </tr>
    `).join("") : `<tr class="empty-row"><td colspan="5">No activity recorded yet.</td></tr>`;
  } catch (err) {
    $("#activityBody").innerHTML = `<tr class="empty-row"><td colspan="5">${escapeHtml(err.message)}</td></tr>`;
  }
})();
