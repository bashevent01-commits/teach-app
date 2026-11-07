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
    const base = status || { plan: "owner", total: 500, account_ref: "obsidian" };
    return { ...base, total: base.total > 0 ? base.total : 500, can_pay: true, proof_status: "none" };
  }

  function close() {
    if (el) { el.remove(); el = null; }
    document.body.classList.remove("payflow-open");
  }

  function copyBtn(text, label) {
    return `<button type="button" class="pf-copy" data-copy="${escapeHtml(String(text))}" data-label="${label}">Copy</button>`;
  }

  function body(s) {
    const preview = mode === "preview";
    if (!s.can_pay && !preview) {
      return `<h2>This workspace is closed</h2><p>The monthly fee has not been paid. Ask your owner to pay it and the app opens again for everyone.</p>
        <button class="primary-btn" id="pfRecheck">Check again</button><button class="ghost-btn" id="pfOut">Sign out</button>`;
    }
    const first = s.state === "trial_ended" || preview;
    const commission = Math.floor((s.total * 20) / 100);
    const toggle = preview ? `<label class="pf-toggle"><span>Pretend an agent registered this account</span><input type="checkbox" id="pfAgent" ${previewAgent ? "checked" : ""}></label>` : "";
    if (step === 0) {
      return `<h2>${mode === "locked" ? "Your KNOW fee is overdue" : first ? "Your free week has ended" : "Your KNOW fee is due"}</h2>
        <p class="pf-amount">${kes(s.total)} a month</p>
        <p>${mode === "locked" ? "The app is closed until the fee is paid. Pay, then paste your M-Pesa message and it opens again straight away." : "Pay to keep KNOW running for your business. You can keep using the app until 6am tomorrow. After that it closes until the fee is paid."}</p>
        ${s.proof_status === "rejected" ? `<p class="pf-error">Your last payment message could not be confirmed${s.proof_note ? ": " + escapeHtml(s.proof_note) : ""}.</p>` : ""}
        ${toggle}
        <button class="primary-btn" id="pfPay">Pay now</button>
        ${mode === "reminder" ? `<button class="ghost-btn" id="pfLater">Remind me later</button>` : ""}
        ${preview ? `<button class="ghost-btn" id="pfClose">Close preview</button>` : ""}
        ${mode === "locked" ? `<button class="ghost-btn" id="pfOut">Sign out</button>` : ""}`;
    }
    if (step === 1) {
      return `<h2>Pay with M-Pesa</h2><p>On your phone: M-Pesa, Lipa na M-Pesa, Pay Bill. Then enter these details.</p>
        <div class="pf-card">
          <div><small>PAYBILL NUMBER</small><strong>${s.paybill}</strong>${copyBtn(s.paybill, "Paybill copied")}</div>
          <div><small>ACCOUNT NAME</small><strong>${escapeHtml(s.paybill_name)}</strong></div>
          <div><small>ACCOUNT NUMBER</small><strong>${escapeHtml(s.account_ref || "your username")}</strong>${copyBtn(s.account_ref, "Account number copied")}</div>
          <div><small>AMOUNT</small><strong>${kes(s.total)}</strong>${copyBtn(s.total, "Amount copied")}</div>
        </div><p class="pf-ok" id="pfCopied"></p>
        <button class="primary-btn" id="pfPaid">I have paid</button><button class="ghost-btn" id="pfBack0">Back</button>`;
    }
    if (step === 2) {
      return `<h2>Show us your payment</h2><p>Paste the whole M-Pesa message you got from Safaricom. The app stays open while we check it.</p>
        <textarea id="pfMsg" rows="5" placeholder="M-Pesa message"></textarea><p class="pf-error" id="pfErr" hidden></p>
        <button class="primary-btn" id="pfSend">${preview ? "Send (preview only)" : "Send message"}</button><button class="ghost-btn" id="pfBack1">Back</button>`;
    }
    return `<h2 class="pf-ok">Thank you</h2><p>${escapeHtml(thanks || "Your payment message is being checked and the app stays open.")}</p>
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
      btn.disabled = true; btn.textContent = "Sending...";
      try {
        const r = await apiFetch("/api/billing/proof", { method: "POST", body: { message: text } });
        thanks = r.message;
        step = 3;
        status = await Api.billingStatus();
        decide();
      } catch (e) {
        err.textContent = e.message || "Could not send the message."; err.hidden = false;
        btn.disabled = false; btn.textContent = "Send message";
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
