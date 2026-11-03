const BillingView = (() => {
  const kes = (n) => "KES " + Number(n).toLocaleString("en-KE");
  const fmt = (iso, plusDays = 0) => {
    const d = new Date(iso);
    d.setDate(d.getDate() + plusDays);
    return d.toLocaleDateString("en-KE", { day: "numeric", month: "long", year: "numeric", timeZone: "Africa/Nairobi" });
  };

  function hero(s, headline) {
    const trial = s.state === "trial";
    const paid = s.state === "paid";
    const pct = trial ? Math.round((s.days_left / s.trial_days) * 100) : 0;
    return `<div class="sub-hero">
      <p class="sub-eyebrow">${trial ? "FREE TRIAL" : paid ? "PAID UP" : "FEE DUE"}</p>
      ${headline ? `<h2 class="sub-headline">${headline}</h2>` : ""}
      <div class="sub-ring" style="--p:${pct}"><div class="sub-ring-in"><strong>${trial ? s.days_left : 0}</strong><span>${s.days_left === 1 ? "day left" : "days left"}</span></div></div>
      <p class="sub-lead">${trial ? `Enjoy everything free for ${s.trial_days} days` : s.note || "Your fee is due."}</p>
      <span class="sub-pill">${trial ? "Billing begins on " + fmt(s.trial_ends_at) : paid ? "Next payment " + fmt(s.due_date) : "Fee due since " + fmt(s.due_date)}</span>
    </div>`;
  }

  function timeline(s) {
    const steps = [
      ["Trial starts", fmt(s.trial_ends_at, -s.trial_days), true],
      ["Trial ends", fmt(s.trial_ends_at), s.state !== "trial"],
      ["Payments begin", `${kes(s.total)} / month from ${fmt(s.trial_ends_at)}`, false],
    ];
    return `<div class="sub-panel"><h3>How billing works</h3><ol class="sub-steps">${steps.map(([t, d, done]) =>
      `<li class="${done ? "is-done" : ""}"><span class="sub-dot">${done ? "&#10003;" : ""}</span><div><strong>${t}</strong><small>${d}</small></div></li>`).join("")}</ol></div>`;
  }

  function plan(s) {
    const owner = s.plan === "owner";
    const feats = owner
      ? ["Sales, stock, audits and finance records", "Create individual accounts for your team", `${s.included_accounts} individual accounts included`, `${kes(s.extra_rate)} for each extra account`]
      : ["Sales, stock and finance records", "Works offline, syncs when you are back online", "Daily summaries and audit trail", "Cannot create other accounts"];
    return `<div class="sub-panel sub-plan"><span class="sub-chip">${s.plan_label.toUpperCase()} PLAN</span>
      <p class="sub-amount">${kes(s.base_amount)}<small> / month</small></p>
      <ul class="sub-checks">${feats.map((f) => `<li>${f}</li>`).join("")}</ul></div>`;
  }

  function accounts(s) {
    if (s.plan !== "owner") return "";
    const fill = Math.min(100, Math.round((s.accounts_used / Math.max(1, s.included_accounts)) * 100));
    return `<div class="sub-panel"><h3>Your accounts</h3>
      <div class="sub-row"><span>Individual accounts</span><strong>${s.accounts_used} of ${s.included_accounts} included</strong></div>
      <div class="sub-bar"><i style="width:${fill}%"></i></div>
      ${s.extra_accounts ? `<div class="sub-row"><span>${s.extra_accounts} extra x ${kes(s.extra_rate)}</span><strong>${kes(s.extra_amount)}</strong></div>` : `<p class="hint">${s.included_accounts - s.accounts_used} more included before extra charges apply</p>`}
      <div class="sub-row sub-total"><span>Total per month</span><strong>${kes(s.total)}</strong></div></div>`;
  }

  function pay(s) {
    return `<div class="sub-panel"><h3>Pay with</h3>
      <div class="sub-method"><div><strong>M-Pesa paybill ${s.paybill}</strong><small>Account name ${s.paybill_name}</small></div></div>
      <p class="hint">${s.note}</p>
      ${s.can_pay && s.state !== "trial" && s.proof_status !== "pending" ? `<button class="primary-btn" id="payNowBtn">Pay now</button>` : ""}
      ${s.preview_allowed ? `<button class="ghost-btn" id="previewPayBtn">Preview the payment reminder</button>` : ""}</div>`;
  }

  return { render: (s, headline) => hero(s, headline) + timeline(s) + plan(s) + accounts(s) + pay(s), hero, timeline, plan, fmt, kes };
})();
