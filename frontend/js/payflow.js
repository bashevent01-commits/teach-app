// The monthly fee reminder (4pm Nairobi time), the closed screen (6am the next morning), the pay steps and the owner-only preview
const PayFlow = (() => {
  const kes = (n) => "KES " + Number(n).toLocaleString("en-KE");
  const day = () => new Date().toLocaleDateString("en-CA", { timeZone: "Africa/Nairobi" });
  let session = null;
  let status = null;
  let mode = null;
  let step = 0;
  let thanks = "";
  let previewAgent = false;
  let timer = null;
  let el = null;

  const laterKey = () => "know_later_" + session.user_id;

  function nextCheck() {
    const now = Date.now();
    const next = [status?.popup_from, status?.lock_at].map((v) => (v ? new Date(v).getTime() : 0)).filter((t) => t > now).sort((a, b) => a - b)[0];
    return next ? Math.min(300000, Math.max(5000, next - now + 2000)) : 300000;
  }

  async function check() {
    clearTimeout(timer);
    try { status = await Api.billingStatus(); } catch (e) { status = status || null; }
    const known = Session.get();
    if (status && known && (known.individual !== status.individual || known.institution_type !== status.institution_type)) {
      Session.set({ ...known, individual: status.individual, institution_type: status.institution_type });
    }
    decide();
    timer = setTimeout(check, nextCheck());
  }

  function decide() {
    let next = null;
    if (mode === "preview") next = "preview";
    else if (status?.locked) next = "locked";
    else if (mode === "pay" || thanks) next = "pay";
    else if (status?.popup_due && localStorage.getItem(laterKey()) !== day()) next = "reminder";
    if (next !== mode || !next) {
      if (!mode && next) step = next === "pay" ? 1 : 0;
      if (!next) { step = 0; thanks = ""; }
      mode = next;
    }
    render();
  }

  function shown() {
    if (mode !== "preview") return status;
    const base = status || { plan: "owner", total: 500, till: "4396353", till_name: "Obsidian Labs" };
    return { ...base, total: base.total > 0 ? base.total : 500, can_pay: true, proof_status: "none" };
  }

  function close() {
    if (el) { el.remove(); el = null; }
    document.body.classList.remove("payflow-open");
  }

  const ICONS = {
    lock: '<svg viewBox="0 0 24 24" width="30" height="30" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="5" y="11" width="14" height="9" rx="2"/><path d="M8 11V8a4 4 0 0 1 8 0v3"/></svg>',
    receipt: '<svg viewBox="0 0 24 24" width="30" height="30" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M6 3h12v18l-3-2-3 2-3-2-3 2z"/><path d="M9 8h6M9 12h6"/></svg>',
    star: '<svg viewBox="0 0 24 24" width="30" height="30" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="m12 3 2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1L3.2 9.5l6.1-.9z"/></svg>',
    check: '<svg viewBox="0 0 24 24" width="30" height="30" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="9"/><path d="m8 12.5 2.8 2.8L16 9.8"/></svg>',
  };

  function header(icon, tone, title, subtitle) {
    return `<div class="pf-head">${icon ? `<span class="pf-icon pf-${tone}">${ICONS[icon]}</span>` : ""}<h2>${title}</h2><p>${subtitle}</p></div>`;
  }

  function steps(current) {
    return `<div class="pf-steps"><span class="${current === 1 ? "on" : ""}">1  Pay</span><span class="${current === 2 ? "on" : ""}">2  Confirm</span></div>`;
  }

  function body(s) {
    const preview = mode === "preview";
    const locked = mode === "locked";
    const upgrade = s.tier === "free";
    const firstBill = s.state === "trial_ended" || preview;
    const amount = kes(s.total);
    const plan = upgrade ? "Paid plan" : s.plan === "owner" ? "Business plan" : "Individual plan";
    const commission = Math.floor((s.total * 20) / 100);
    const toggle = preview ? `<label class="pf-toggle"><span>Pretend an agent registered this account</span><input type="checkbox" id="pfAgent" ${previewAgent ? "checked" : ""}></label>` : "";
    if (!s.can_pay && !preview) {
      return `${header("lock", "amber", "Your workspace is paused", "Your business fee hasn't been paid yet. Please ask your owner to pay it. As soon as it's paid, KNOW opens again for everyone.")}
        <button class="primary-btn" id="pfRecheck">Check again</button><button class="pf-link" id="pfOut">Sign out</button>`;
    }
    if (step === 0) {
      const head = locked ? header("lock", "amber", "Your workspace is paused", "This month's fee isn't paid yet, so we've paused KNOW for now. Your records are safe. Pay in a minute and carry on.")
        : upgrade ? header("star", "teal", "Upgrade your plan", "Store documents and photos, and print your statements.")
        : header("receipt", "teal", firstBill ? "Your free week is over" : "Time to renew", "You can carry on until 6am tomorrow. After that KNOW pauses until the fee is paid.");
      return `${head}
        <div class="pf-amountbox"><small>${plan.toUpperCase()}</small><strong>${amount}</strong><span>${upgrade ? "per month" : "every month"}</span></div>
        ${s.proof_status === "rejected" ? `<p class="pf-error">We couldn't confirm your last payment${s.proof_note ? ": " + escapeHtml(s.proof_note) : ""}. Please check the message you pasted and try again.</p>` : ""}
        ${toggle}
        <button class="primary-btn" id="pfPay">${locked ? "Pay with M-Pesa" : upgrade ? "Upgrade now" : "Pay now"}</button>
        ${locked ? `<button class="ghost-btn" id="pfPaid2">I've already paid</button>` : ""}
        ${mode === "reminder" ? `<button class="pf-link" id="pfLater">Remind me tomorrow</button>` : ""}
        ${preview ? `<button class="pf-link" id="pfClose">Close preview</button>` : ""}
        ${locked ? `<button class="pf-link" id="pfOut">Sign out</button>` : ""}`;
    }
    if (step === 1) {
      return `${steps(1)}${header(null, "teal", `Pay ${amount} with M-Pesa`, "It only takes a minute.")}
        <ol class="pf-howto"><li>Open M-Pesa and choose Lipa na M-Pesa</li><li>Choose Buy Goods and Services</li><li>Enter the till number below</li><li>Enter ${amount}, then your M-Pesa PIN</li></ol>
        <div class="pf-card">
          <div><small>TILL NUMBER</small><strong class="pf-till">${s.till}</strong><button type="button" class="pf-copy" data-copy="${escapeHtml(String(s.till))}" data-label="Till number copied">Copy</button></div>
          <div class="pf-row2"><span><small>BUSINESS NAME</small><b>${escapeHtml(s.till_name.toUpperCase())}</b></span><span><small>AMOUNT</small><b>${amount}</b></span></div>
        </div><p class="pf-ok" id="pfCopied"></p>
        <p class="pf-note">Check that the name shows as ${escapeHtml(s.till_name)} before you enter your PIN.</p>
        <button class="primary-btn" id="pfPaid">I've paid</button><button class="pf-link" id="pfBack0">Back</button>`;
    }
    if (step === 2) {
      return `${steps(2)}${header(null, "teal", "Confirm your payment", "M-Pesa sends you a confirmation message. Paste it here so we can match your payment. It starts with a code like SJK4X9PQLM.")}
        <textarea id="pfMsg" rows="5" placeholder="M-Pesa confirmation message"></textarea>
        <button type="button" class="pf-link" id="pfPaste">Paste from clipboard</button>
        <p class="pf-error" id="pfErr" hidden></p>
        <button class="primary-btn" id="pfSend">Confirm payment</button><button class="pf-link" id="pfBack1">Back</button>`;
    }
    return `${header("check", "green", "Thank you!", escapeHtml(thanks || "We're checking your payment. You can keep using KNOW in the meantime."))}
      ${toggle}
      ${preview && previewAgent ? `<div class="pf-agent"><small>WHAT THE AGENT SEES</small><p>Your business paid ${kes(s.total)}. You earned ${kes(commission)} and can ask for a payout of that amount.</p><button class="primary-btn" disabled>Ask for payout of ${kes(commission)}</button></div>` : ""}
      <button class="primary-btn" id="pfDone">Done</button>`;
  }

  function render() {
    if (!mode) return close();
    const s = shown();
    if (!s) return close();
    if (!el) {
      el = document.createElement("div");
      el.className = "payflow-backdrop";
      document.body.appendChild(el);
      document.body.classList.add("payflow-open");
    }
    el.classList.toggle("is-locked", mode === "locked");
    el.innerHTML = `<div class="payflow-card" role="dialog" aria-modal="true">${mode === "preview" ? `<p class="pf-tag">PREVIEW. Nothing is sent or charged.</p>` : ""}${body(s)}</div>`;
    bind(s);
  }

  function leave() {
    thanks = "";
    if (mode === "preview" || mode === "pay") mode = null;
    decide();
  }

  function bind(s) {
    const on = (id, fn) => el.querySelector(id)?.addEventListener("click", fn);
    on("#pfPay", () => { step = 1; render(); });
    on("#pfLater", () => { localStorage.setItem(laterKey(), day()); mode = null; decide(); });
    on("#pfClose", () => { mode = null; previewAgent = false; decide(); });
    on("#pfDone", () => { if (mode === "preview") previewAgent = false; leave(); });
    on("#pfBack0", () => { step = 0; render(); });
    on("#pfBack1", () => { step = 1; render(); });
    on("#pfPaid", () => { step = 2; render(); });
    on("#pfPaid2", () => { step = 2; render(); });
    on("#pfPaste", async () => {
      try { el.querySelector("#pfMsg").value = await navigator.clipboard.readText(); } catch (e) { el.querySelector("#pfMsg").focus(); }
    });
    on("#pfRecheck", check);
    on("#pfOut", () => logout());
    el.querySelector("#pfAgent")?.addEventListener("change", (e) => { previewAgent = e.target.checked; render(); });
    el.querySelectorAll(".pf-copy").forEach((b) => b.addEventListener("click", () => {
      navigator.clipboard?.writeText(b.dataset.copy);
      el.querySelector("#pfCopied").textContent = b.dataset.label;
    }));
    on("#pfSend", async () => {
      const err = el.querySelector("#pfErr");
      if (mode === "preview") { step = 3; return render(); }
      const text = el.querySelector("#pfMsg").value.trim();
      if (text.length < 20) { err.textContent = "Paste the whole M-Pesa message."; err.hidden = false; return; }
      const btn = el.querySelector("#pfSend");
      btn.disabled = true; btn.textContent = "Checking...";
      try {
        const r = await apiFetch("/api/billing/proof", { method: "POST", body: { message: text } });
        thanks = r.message;
        step = 3;
        status = await Api.billingStatus();
        decide();
      } catch (e) {
        err.textContent = e.message || "Could not send the message."; err.hidden = false;
        btn.disabled = false; btn.textContent = "Confirm payment";
      }
    });
  }

  // The banner shown when someone on the free plan tries a paid feature
  function banner(message) {
    document.getElementById("upgradeBanner")?.remove();
    const el2 = document.createElement("div");
    el2.id = "upgradeBanner";
    el2.className = "upgrade-banner";
    el2.innerHTML = `<div><strong>Upgrade to continue</strong><p>${escapeHtml(message)}</p></div><button type="button" class="primary-btn" id="upgradeBannerBtn">Upgrade for KES 200 a month</button>`;
    (document.querySelector("main") || document.body).insertAdjacentElement("afterbegin", el2);
    el2.querySelector("#upgradeBannerBtn").addEventListener("click", () => { el2.remove(); api.open(); });
    el2.scrollIntoView({ behavior: "smooth", block: "start" });
  }

  const api = {
    isFree() { return !!status && status.tier === "free" && !status.premium; },
    blocked(message) {
      if (!api.isFree()) return false;
      banner(message);
      return true;
    },
    start(sess) {
      if (!sess || !["staff", "institution_admin"].includes(sess.role)) return;
      session = sess;
      window.addEventListener("know-closed", check);
      document.addEventListener("visibilitychange", () => { if (!document.hidden) check(); });
      check();
    },
    open() { mode = "pay"; step = 1; render(); },
    preview() { mode = "preview"; step = 0; previewAgent = false; render(); },
  };
  return api;
})();
