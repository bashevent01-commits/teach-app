(async function () {
  await initShell("subscription", ["staff", "institution_admin"]);
  const root = $("#subRoot");
  try {
    const s = await Api.billingStatus();
    root.innerHTML = s.tier === "free"
      ? BillingView.renderFree(s)
      : s.applies
      ? BillingView.render(s)
      : "<div class=\"sub-panel\"><h3>Subscription</h3><p>Your account is managed by your institution. There is nothing for you to pay here.</p></div>";
    $("#payNowBtn")?.addEventListener("click", () => PayFlow.open());
    $("#previewPayBtn")?.addEventListener("click", () => PayFlow.preview());
    if (s.can_upgrade_business) {
      root.insertAdjacentHTML("beforeend", `<div class="sub-panel"><h3>Opened another branch?</h3><p>Become a business to add branches. You keep everything you have and only pay the difference.</p><button class="ghost-btn" id="businessBtn">Become a business</button></div>`);
      $("#businessBtn").addEventListener("click", () => openBusiness(s));
    }
  } catch (e) {
    root.innerHTML = "<p class=\"form-error visible\">Could not load your subscription. Try again.</p>";
  }

  // An individual who opened another branch becomes a business: details, what is due, then the M-Pesa message
  async function openBusiness(s) {
    let quote;
    try { quote = await apiFetch("/api/billing/upgrade-quote"); } catch (err) { toast(err.message || "Could not load the price."); return; }
    const pass = () => {
      const pick = (set, n) => Array.from({ length: n }, () => set[Math.floor(Math.random() * set.length)]).join("");
      return (pick("ABCDEFGHJKLMNPQRSTUVWXYZ", 4) + pick("abcdefghijkmnpqrstuvwxyz", 5) + pick("23456789", 3) + pick("#$%&*", 2)).split("").sort(() => Math.random() - 0.5).join("");
    };
    const el = document.createElement("div");
    el.className = "payflow-backdrop";
    document.body.appendChild(el);
    const note = quote.due_now === 0
      ? `You are still in your free week, so nothing is due now. The business plan starts at KES ${quote.price} a month after it.`
      : `The business plan is KES ${quote.price} a month. We credit KES ${quote.credit} for the unused days you already paid, so you pay KES ${quote.due_now} now and your next payment is a month from today.`;
    const close = () => el.remove();
    function details() {
      el.innerHTML = `<div class="payflow-card"><h2>Become a business</h2>
        <p>Your sales, stock and history stay exactly as they are. Your current login becomes your first branch, and you get a new owner login to add more branches.</p>
        <label class="field"><span>Business name</span><input type="text" id="bName" /></label>
        <label class="field"><span>Business type</span><select id="bType"><option value="shop">Shop</option><option value="supermarket">Supermarket</option><option value="school">School</option><option value="gym">Gym</option><option value="other">Other</option></select></label>
        <label class="field"><span>New owner username</span><input type="text" id="bUser" /></label>
        <label class="field"><span>New owner password</span><input type="text" id="bPass" value="${pass()}" /></label>
        <label class="field"><span>Email (optional)</span><input type="email" id="bEmail" /></label>
        <p><strong>${note}</strong></p><p class="pf-error" id="bErr" hidden></p>
        <button class="primary-btn" id="bGo">${quote.due_now === 0 ? "Become a business" : "Continue to payment"}</button><button class="ghost-btn" id="bCancel">Cancel</button></div>`;
      el.querySelector("#bCancel").addEventListener("click", close);
      el.querySelector("#bGo").addEventListener("click", () => {
        const f = { business_name: el.querySelector("#bName").value.trim(), business_type: el.querySelector("#bType").value, owner_username: el.querySelector("#bUser").value.trim(), owner_password: el.querySelector("#bPass").value.trim(), owner_email: el.querySelector("#bEmail").value.trim() || null };
        if (f.business_name.length < 2 || f.owner_username.length < 3) { const e = el.querySelector("#bErr"); e.textContent = "Enter the business name and an owner username."; e.hidden = false; return; }
        if (quote.due_now === 0) send(f, null); else payment(f);
      });
    }
    function payment(f) {
      el.innerHTML = `<div class="payflow-card"><h2>Pay KES ${quote.due_now}</h2>
        <p>M-Pesa paybill <strong>${s.paybill}</strong>, account name <strong>${escapeHtml(s.paybill_name)}</strong>, account number <strong>${escapeHtml(s.account_ref)}</strong>.</p>
        <textarea id="bMsg" rows="5" placeholder="Paste the M-Pesa message"></textarea><p class="pf-error" id="bErr" hidden></p>
        <button class="primary-btn" id="bSend">Send message</button><button class="ghost-btn" id="bBack">Back</button></div>`;
      el.querySelector("#bBack").addEventListener("click", details);
      el.querySelector("#bSend").addEventListener("click", () => send(f, el.querySelector("#bMsg").value.trim()));
    }
    async function send(f, message) {
      const err = el.querySelector("#bErr");
      try {
        const r = await apiFetch("/api/billing/upgrade-to-business", { method: "POST", body: { ...f, message } });
        el.innerHTML = `<div class="payflow-card"><h2 class="pf-ok">Thank you</h2><p>${escapeHtml(r.message)}</p><button class="primary-btn" id="bDone">Done</button></div>`;
        el.querySelector("#bDone").addEventListener("click", () => { close(); location.reload(); });
      } catch (e) { err.textContent = e.message || "Could not send."; err.hidden = false; }
    }
    details();
  }
})();
