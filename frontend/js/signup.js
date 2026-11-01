const err = document.getElementById("authError");
const stepPlan = document.getElementById("stepPlan");
const stepForm = document.getElementById("stepForm");
let plan = "individual";

// Owners and sales-and-stock accounts say where they trade; personal accounts do not
function showLocation() {
  const needs = plan === "owner" || document.getElementById("signupType").value === "staff";
  document.getElementById("locationFields").hidden = !needs;
  document.getElementById("signupCounty").required = needs;
  document.getElementById("signupConstituency").required = needs;
  document.getElementById("locationHint").textContent = plan === "owner" ? "Where your business is based. You choose a location for each branch you add later." : "Where you trade. This keeps the market prices accurate.";
}

const countyPick = document.getElementById("signupCounty");
countyPick.innerHTML = `<option value="">Choose county</option>` + KENYA_COUNTIES.map((c) => `<option value="${c}">${c}</option>`).join("");
document.getElementById("signupConstituency").innerHTML = constituencyOptions("");
countyPick.addEventListener("change", () => { document.getElementById("signupConstituency").innerHTML = constituencyOptions(countyPick.value); });
document.getElementById("signupType").addEventListener("change", showLocation);

function choosePlan(next) {
  plan = next;
  const owner = plan === "owner";
  document.getElementById("ownerFields").hidden = !owner;
  document.getElementById("useForField").hidden = owner;
  document.getElementById("signupBusiness").required = owner;
  document.getElementById("formTitle").textContent = owner ? "Create your owner account" : "Create your individual account";
  document.getElementById("formHint").textContent = owner
    ? "KES 500/month after your free week. Includes 5 individual accounts, then KES 200 each."
    : "KES 200/month after your free week. This account cannot create other accounts.";
  showLocation();
  stepPlan.hidden = true;
  stepForm.hidden = false;
  err.classList.remove("visible");
}

document.querySelectorAll(".plan-card").forEach((card) => card.addEventListener("click", () => choosePlan(card.dataset.plan)));
document.getElementById("changePlan").addEventListener("click", () => { stepForm.hidden = true; stepPlan.hidden = false; });

document.getElementById("signupForm").addEventListener("submit", async (e) => {
  e.preventDefault();
  err.classList.remove("visible");
  const btn = document.getElementById("signupSubmit");
  btn.disabled = true;
  const owner = plan === "owner";
  try {
    const data = await Api.signup({
      account_type: owner ? "owner" : document.getElementById("signupType").value,
      business_name: owner ? document.getElementById("signupBusiness").value.trim() : null,
      business_type: owner ? document.getElementById("signupBusinessType").value : null,
      full_name: document.getElementById("signupName").value.trim(),
      username: document.getElementById("signupUsername").value.trim(),
      email: document.getElementById("signupEmail").value.trim() || null,
      partner_code: document.getElementById("signupPartner").value.trim() || null,
      county: document.getElementById("signupCounty").value || null,
      constituency: document.getElementById("signupConstituency").value || null,
      password: document.getElementById("signupPassword").value,
    });
    const { user_id, role, staff_type, full_name, institution_id, access_token } = data;
    TokenStore.set(access_token);
    Session.set({ user_id, role, staff_type, full_name, institution_id });
    location.href = "welcome.html";
  } catch (error) {
    err.textContent = error.status === 429 ? "Too many sign-ups from this connection. Please try again later." : (error.message || "Could not create the account. Try again.");
    err.classList.add("visible");
    btn.disabled = false;
  }
});
