// ============================================================ KNOW — shared page chrome Auth guarding, role-based nav

const $ = (sel, root = document) => root.querySelector(sel);
const $$ = (sel, root = document) => [...root.querySelectorAll(sel)];



function requireAuth(fallbackLoginPage = "login.html") {
  if (!Session.isAuthenticated()) {
    location.href = fallbackLoginPage;
    throw new Error("redirecting to login");
  }
  return Session.get();
}

function homePageFor(role) {
  if (role === "super_admin") return "dashboard.html";
  if (role === "institution_admin") return "admin-home.html";
  if (role === "agent") return "partner.html";
  return "home.html";
}

function requireRole(...roles) {
  const fallbackLoginPage = roles.length === 1 ? Session.loginPageFor(roles[0]) : "login.html";
  const session = requireAuth(fallbackLoginPage);
  if (!roles.includes(session.role)) {
    location.href = homePageFor(session.role);
    throw new Error("redirecting: insufficient role");
  }
  return session;
}

async function logout() {
  const loginPage = Session.loginPageFor(Session.get()?.role);
  try { await Api.logout(); } catch { /* clear local state regardless */ }
  Session.clear();
  location.href = loginPage;
}



function initials(name) {
  if (!name) return "?";
  return name.trim().split(/\s+/).slice(0, 2).map((w) => w[0]?.toUpperCase() || "").join("");
}

function formatMoney(amount) {
  const n = typeof amount === "string" ? parseFloat(amount) : amount;
  if (Number.isNaN(n)) return "0.00";
  return n.toLocaleString("en-US", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function money(amount) {
  return "KES " + formatMoney(amount);
}

function formatDate(iso, withTime = false) {
  if (!iso) return "—";
  const d = new Date(iso);
  const opts = { day: "2-digit", month: "short", year: "numeric" };
  if (withTime) { opts.hour = "2-digit"; opts.minute = "2-digit"; }
  return d.toLocaleDateString("en-GB", opts);
}

function escapeHtml(str) {
  const div = document.createElement("div");
  div.textContent = str ?? "";
  return div.innerHTML;
}

function showFormMessage(el, message, isError = true) {
  el.textContent = message;
  el.classList.remove("form-error", "form-success");
  el.classList.add(isError ? "form-error" : "form-success", "visible");
}

function hideFormMessage(el) {
  el.classList.remove("visible");
}



function toast(msg) {
  const el = $("#toast");
  if (!el) return;
  el.textContent = msg;
  el.hidden = false;
  clearTimeout(toast._t);
  toast._t = setTimeout(() => (el.hidden = true), 2600);
}



function ensureOfflineBanner() {
  let el = document.getElementById("offlineBanner");
  if (!el) {
    el = document.createElement("div");
    el.id = "offlineBanner";
    el.className = "offline-banner";
    el.hidden = true;
    document.body.prepend(el);
  }
  return el;
}

function setOfflineBanner() {
  // The top banner was too distracting; pending rows are tinted in the list instead
  const el = document.getElementById("offlineBanner");
  if (el) el.hidden = true;
}



const ICONS = {
  home: '<path d="M3 10.5 12 3l9 7.5"/><path d="M5 9.8V21h14V9.8"/>',
  news: '<rect x="3" y="4" width="18" height="16" rx="2"/><path d="M7 9h7M7 13h10M7 17h6"/>',
  audit: '<path d="M4 20V10M10 20V4M16 20v-7M22 20H2"/>',
  settings: '<circle cx="12" cy="12" r="3.2"/><path d="M19.4 13.5a1.7 1.7 0 0 0 .3 1.9l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-2.9 1.2v.2a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-2.9-1.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0-1.2-2.9H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.3-2.9l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 2.9-1.2V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 2.9 1.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0 1.2 2.9h.2a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.6 1.4z"/>',
  dashboard: '<path d="M12 2 3 6.5V12c0 5.2 3.6 9.4 9 10.5 5.4-1.1 9-5.3 9-10.5V6.5L12 2z"/><path d="m9 12 2 2 4-4"/>',
  institution: '<path d="M12 3 2 8l10 5 10-5-10-5z"/><path d="M6 10.5V16c0 1.4 2.7 3 6 3s6-1.6 6-3v-5.5"/><path d="M22 8v6"/>',
  stock: '<path d="M3 7l9-4 9 4-9 4-9-4z"/><path d="M3 7v10l9 4 9-4V7"/><path d="M12 11v10"/>',
  privacy: '<path d="M12 3 4 6.5v5c0 5 3.4 8.9 8 10 4.6-1.1 8-5 8-10v-5L12 3z"/><path d="m9.5 12 1.8 1.8L15 10"/>',
  accounts: '<circle cx="9" cy="8" r="3.2"/><path d="M3 20c0-3.3 2.7-6 6-6s6 2.7 6 6"/><circle cx="17.5" cy="9" r="2.6"/><path d="M15.5 14.2c2.6.4 4.5 2.6 4.5 5.3"/>',
  flag: '<path d="M5 3v18"/><path d="M5 4h13l-3 4.5L18 13H5"/>',
  activity: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3.5 2"/>',
  cash: '<rect x="2.5" y="6" width="19" height="12" rx="2"/><circle cx="12" cy="12" r="3"/><path d="M6.5 9v0M17.5 15v0"/>',
  mpesa: '<rect x="6" y="2.5" width="12" height="19" rx="2"/><path d="M9 6h6M11 19h2"/>',
  bank: '<path d="M3 10 12 4l9 6"/><path d="M5 10v9M9 10v9M15 10v9M19 10v9"/><path d="M3 19h18"/>',
  mode: '<circle cx="12" cy="12" r="4.2"/><path d="M12 2v2M12 20v2M4.2 4.2l1.5 1.5M18.3 18.3l1.5 1.5M2 12h2M20 12h2M4.2 19.8l1.5-1.5M18.3 5.7l1.5-1.5"/>',
  in: '<path d="M12 5v14M6 13l6 6 6-6"/>',
  out: '<path d="M12 19V5M6 11l6-6 6 6"/>',
  image: '<rect x="3" y="4" width="18" height="16" rx="2"/><circle cx="8.5" cy="9.5" r="1.5"/><path d="m4 17 5-4.5 4 3.5 3-2.5 4 3.5"/>',
  market: '<path d="M4 4h16l-1.5 9h-13L4 4z"/><path d="M4 4 3 2H1"/><circle cx="9.5" cy="19" r="1.5"/><circle cx="16.5" cy="19" r="1.5"/><path d="M8 17h9"/>',
  download: '<path d="M12 3v12"/><path d="m7 10 5 5 5-5"/><path d="M4 19h16"/>',
};

// Fixed list so institution region stays a controlled value (needed for regional market-analysis breakdowns) rather than
const KENYA_COUNTIES = [
  "Mombasa", "Kwale", "Kilifi", "Tana River", "Lamu", "Taita-Taveta",
  "Garissa", "Wajir", "Mandera", "Marsabit", "Isiolo", "Meru",
  "Tharaka-Nithi", "Embu", "Kitui", "Machakos", "Makueni", "Nyandarua",
  "Nyeri", "Kirinyaga", "Murang'a", "Kiambu", "Turkana", "West Pokot",
  "Samburu", "Trans Nzoia", "Uasin Gishu", "Elgeyo-Marakwet", "Nandi",
  "Baringo", "Laikipia", "Nakuru", "Narok", "Kajiado", "Kericho",
  "Bomet", "Kakamega", "Vihiga", "Bungoma", "Busia", "Siaya",
  "Kisumu", "Homa Bay", "Migori", "Kisii", "Nyamira", "Nairobi",
];

// The 290 constituencies by county (IEBC). Keep in sync with backend/app/core/constants.py.
const KENYA_CONSTITUENCIES = {
  "Mombasa": ["Changamwe", "Jomvu", "Kisauni", "Likoni", "Mvita", "Nyali"],
  "Kwale": ["Kinango", "Lungalunga", "Matuga", "Msambweni"],
  "Kilifi": ["Ganze", "Kaloleni", "Kilifi North", "Kilifi South", "Magarini", "Malindi", "Rabai"],
  "Tana River": ["Bura", "Galole", "Garsen"],
  "Lamu": ["Lamu East", "Lamu West"],
  "Taita-Taveta": ["Mwatate", "Taveta", "Voi", "Wundanyi"],
  "Garissa": ["Balambala", "Dadaab", "Fafi", "Garissa Township", "Ijara", "Lagdera"],
  "Wajir": ["Eldas", "Tarbaj", "Wajir East", "Wajir North", "Wajir South", "Wajir West"],
  "Mandera": ["Banissa", "Lafey", "Mandera East", "Mandera North", "Mandera South", "Mandera West"],
  "Marsabit": ["Laisamis", "Moyale", "North Horr", "Saku"],
  "Isiolo": ["Isiolo North", "Isiolo South"],
  "Meru": ["Buuri", "Central Imenti", "Igembe Central", "Igembe North", "Igembe South", "North Imenti", "South Imenti", "Tigania East", "Tigania West"],
  "Tharaka-Nithi": ["Chuka/Igambang'ombe", "Maara", "Tharaka"],
  "Embu": ["Manyatta", "Mbeere North", "Mbeere South", "Runyenjes"],
  "Kitui": ["Kitui Central", "Kitui East", "Kitui Rural", "Kitui South", "Kitui West", "Mwingi Central", "Mwingi North", "Mwingi West"],
  "Machakos": ["Kangundo", "Kathiani", "Machakos Town", "Masinga", "Matungulu", "Mavoko", "Mwala", "Yatta"],
  "Makueni": ["Kaiti", "Kibwezi East", "Kibwezi West", "Kilome", "Makueni", "Mbooni"],
  "Nyandarua": ["Kinangop", "Kipipiri", "Ndaragwa", "Ol Jorok", "Ol Kalou"],
  "Nyeri": ["Kieni", "Mathira", "Mukurweini", "Nyeri Town", "Othaya", "Tetu"],
  "Kirinyaga": ["Gichugu", "Kirinyaga Central", "Mwea", "Ndia"],
  "Murang'a": ["Gatanga", "Kandara", "Kangema", "Kigumo", "Kiharu", "Maragwa", "Mathioya"],
  "Kiambu": ["Gatundu North", "Gatundu South", "Githunguri", "Juja", "Kabete", "Kiambaa", "Kiambu", "Kikuyu", "Lari", "Limuru", "Ruiru", "Thika Town"],
  "Turkana": ["Loima", "Turkana Central", "Turkana East", "Turkana North", "Turkana South", "Turkana West"],
  "West Pokot": ["Kacheliba", "Kapenguria", "Pokot South", "Sigor"],
  "Samburu": ["Samburu East", "Samburu North", "Samburu West"],
  "Trans Nzoia": ["Cherangany", "Endebess", "Kiminini", "Kwanza", "Saboti"],
  "Uasin Gishu": ["Ainabkoi", "Kapseret", "Kesses", "Moiben", "Soy", "Turbo"],
  "Elgeyo-Marakwet": ["Keiyo North", "Keiyo South", "Marakwet East", "Marakwet West"],
  "Nandi": ["Aldai", "Chesumei", "Emgwen", "Mosop", "Nandi Hills", "Tinderet"],
  "Baringo": ["Baringo Central", "Baringo North", "Baringo South", "Eldama Ravine", "Mogotio", "Tiaty"],
  "Laikipia": ["Laikipia East", "Laikipia North", "Laikipia West"],
  "Nakuru": ["Bahati", "Gilgil", "Kuresoi North", "Kuresoi South", "Molo", "Naivasha", "Nakuru Town East", "Nakuru Town West", "Njoro", "Rongai", "Subukia"],
  "Narok": ["Emurua Dikirr", "Kilgoris", "Narok East", "Narok North", "Narok South", "Narok West"],
  "Kajiado": ["Kajiado Central", "Kajiado East", "Kajiado North", "Kajiado South", "Kajiado West"],
  "Kericho": ["Ainamoi", "Belgut", "Bureti", "Kipkelion East", "Kipkelion West", "Sigowet/Soin"],
  "Bomet": ["Bomet Central", "Bomet East", "Chepalungu", "Konoin", "Sotik"],
  "Kakamega": ["Butere", "Ikolomani", "Khwisero", "Likuyani", "Lugari", "Lurambi", "Malava", "Matungu", "Mumias East", "Mumias West", "Navakholo", "Shinyalu"],
  "Vihiga": ["Emuhaya", "Hamisi", "Luanda", "Sabatia", "Vihiga"],
  "Bungoma": ["Bumula", "Kabuchai", "Kanduyi", "Kimilili", "Mt. Elgon", "Sirisia", "Tongaren", "Webuye East", "Webuye West"],
  "Busia": ["Budalangi", "Butula", "Funyula", "Matayos", "Nambale", "Teso North", "Teso South"],
  "Siaya": ["Alego Usonga", "Bondo", "Gem", "Rarieda", "Ugenya", "Ugunja"],
  "Kisumu": ["Kisumu Central", "Kisumu East", "Kisumu West", "Muhoroni", "Nyakach", "Nyando", "Seme"],
  "Homa Bay": ["Homa Bay Town", "Kabondo Kasipul", "Karachuonyo", "Kasipul", "Mbita", "Ndhiwa", "Rangwe", "Suba"],
  "Migori": ["Awendo", "Kuria East", "Kuria West", "Nyatike", "Rongo", "Suna East", "Suna West", "Uriri"],
  "Kisii": ["Bobasi", "Bomachoge Borabu", "Bomachoge Chache", "Bonchari", "Kitutu Chache North", "Kitutu Chache South", "Nyaribari Chache", "Nyaribari Masaba", "South Mugirango"],
  "Nyamira": ["Borabu", "Kitutu Masaba", "North Mugirango", "West Mugirango"],
  "Nairobi": ["Dagoretti North", "Dagoretti South", "Embakasi Central", "Embakasi East", "Embakasi North", "Embakasi South", "Embakasi West", "Kamukunji", "Kasarani", "Kibra", "Langata", "Makadara", "Mathare", "Roysambu", "Ruaraka", "Starehe", "Westlands"],
};

// Units a business can count stock in; the key is what the server stores
const STOCK_UNITS = [
  ["pieces", "Pieces"], ["kg", "Kilograms (kg)"], ["g", "Grams (g)"], ["mg", "Milligrams (mg)"], ["litres", "Litres (L)"], ["ml", "Millilitres (ml)"],
  ["dozen", "Dozen"], ["packets", "Packets"], ["bottles", "Bottles"], ["cartons", "Cartons"], ["boxes", "Boxes"],
  ["bags", "Bags"], ["sacks", "Sacks"], ["crates", "Crates"], ["trays", "Trays"], ["bundles", "Bundles"],
  ["pairs", "Pairs"], ["metres", "Metres (m)"], ["rolls", "Rolls"], ["tins", "Tins"], ["sachets", "Sachets"],
  ["plates", "Plates"], ["cups", "Cups"], ["loaves", "Loaves"], ["bales", "Bales"], ["gallons", "Gallons"], ["tonnes", "Tonnes"],
];
const UNIT_SINGULAR = { pieces: "piece", litres: "litre", packets: "packet", bottles: "bottle", cartons: "carton", boxes: "box", bags: "bag", sacks: "sack", crates: "crate", trays: "tray", bundles: "bundle", pairs: "pair", metres: "metre", rolls: "roll", tins: "tin", sachets: "sachet", plates: "plate", cups: "cup", loaves: "loaf", bales: "bale", gallons: "gallon", tonnes: "tonne" };

// "12.5 kg", "1 piece", "30 pieces"
function qtyText(qty, unit) {
  const n = Number(qty);
  const u = unit || "pieces";
  return `${Number.isFinite(n) ? n : qty} ${n === 1 ? UNIT_SINGULAR[u] || u : u}`;
}

// Measures that can be swapped for each other, each with its size in the family's base unit
const UNIT_FAMILIES = {
  mg: ["weight", 0.001], g: ["weight", 1], kg: ["weight", 1000], tonnes: ["weight", 1000000],
  ml: ["volume", 1], litres: ["volume", 1000], gallons: ["volume", 3785.41],
  pieces: ["count", 1], dozen: ["count", 12], pairs: ["count", 2],
};

function compatibleUnits(unit) {
  const family = UNIT_FAMILIES[unit];
  if (!family) return [unit];
  return STOCK_UNITS.map(([k]) => k).filter((k) => UNIT_FAMILIES[k] && UNIT_FAMILIES[k][0] === family[0]);
}

// The quantity expressed in the item's own unit, to two decimals, or NaN when the measures do not convert
function convertQty(qty, from, to) {
  if (from === to) return Math.round(qty * 100) / 100;
  const f = UNIT_FAMILIES[from];
  const t = UNIT_FAMILIES[to];
  if (!f || !t || f[0] !== t[0]) return NaN;
  return Math.round(((qty * f[1]) / t[1]) * 100) / 100;
}

function unitWord(unit) {
  return qtyText(1, unit).replace(/^1 /, "");
}

function unitFieldHtml(prefix, unit = "pieces") {
  const known = STOCK_UNITS.some(([k]) => k === unit);
  const options = STOCK_UNITS.map(([k, label]) => `<option value="${k}" ${k === unit ? "selected" : ""}>${label}</option>`).join("") + `<option value="__other__" ${known ? "" : "selected"}>Other (type your own)</option>`;
  return `<label class="field"><span>Counted in</span><select id="${prefix}Unit">${options}</select></label>
    <label class="field" id="${prefix}UnitOtherBox" ${known ? "hidden" : ""}><span>Your unit, such as debe or tray of 30</span><input type="text" id="${prefix}UnitOther" maxlength="20" value="${known ? "" : unit}" /></label>`;
}

function attachUnitField(prefix) {
  const select = document.getElementById(prefix + "Unit");
  select.addEventListener("change", () => { document.getElementById(prefix + "UnitOtherBox").hidden = select.value !== "__other__"; });
}

function readUnit(prefix) {
  const select = document.getElementById(prefix + "Unit");
  return select.value === "__other__" ? (document.getElementById(prefix + "UnitOther").value.trim() || "pieces") : select.value;
}

// County and constituency pickers; the constituency list follows the chosen county
function constituencyOptions(county, selected = "") {
  const list = KENYA_CONSTITUENCIES[county] || [];
  return `<option value="">${county ? "Choose constituency" : "Choose a county first"}</option>` + list.map((n) => `<option value="${n}" ${n === selected ? "selected" : ""}>${n}</option>`).join("");
}

function locationFieldsHtml(prefix, county = "", constituency = "") {
  const counties = `<option value="">Choose county</option>` + KENYA_COUNTIES.map((c) => `<option value="${c}" ${c === county ? "selected" : ""}>${c}</option>`).join("");
  return `<label class="field"><span>County</span><select id="${prefix}County">${counties}</select></label>
    <label class="field"><span>Constituency</span><select id="${prefix}Constituency">${constituencyOptions(county, constituency)}</select></label>`;
}

function attachLocation(prefix) {
  const county = document.getElementById(prefix + "County");
  const constituency = document.getElementById(prefix + "Constituency");
  county.addEventListener("change", () => { constituency.innerHTML = constituencyOptions(county.value); });
}

function paintIcons(root = document) {
  $$(".ico[data-ico]", root).forEach((el) => {
    if (el.dataset.painted) return;
    const name = el.dataset.ico;
    if (!ICONS[name]) return;
    el.innerHTML = `<svg viewBox="0 0 24 24">${ICONS[name]}</svg>`;
    el.dataset.painted = "1";
  });
}



const THEME_KEY = "know.theme";

function getTheme() {
  return localStorage.getItem(THEME_KEY) || "light";
}

function applyTheme(mode = getTheme()) {
  document.documentElement.dataset.mode = mode;
  $$("#modeSegmented button").forEach((b) => {
    b.classList.toggle("is-active", b.dataset.mode === mode);
  });
}

function setTheme(mode) {
  localStorage.setItem(THEME_KEY, mode);
  applyTheme(mode);
}

function bindThemeToggle() {
  const btn = $("#modeToggle");
  if (btn) {
    btn.addEventListener("click", () => setTheme(getTheme() === "dark" ? "light" : "dark"));
  }
}

// ---------------- nav (sidebar + tabbar), per role ----------------

const NAV_ITEMS = {
  staff: [
    { href: "home.html", nav: "home", label: "Home", icon: "home" },
    { href: "stock.html", nav: "stock", label: "Stock", icon: "stock" },
    { href: "news.html", nav: "news", label: "News", icon: "news" },
    { href: "audit.html", nav: "audit", label: "Audit", icon: "audit" },
    { href: "subscription.html", nav: "subscription", label: "Plan", icon: "dashboard" },
    { href: "settings.html", nav: "settings", label: "Settings", icon: "settings" },
  ],
  institution_admin: [
    { href: "admin-home.html", nav: "admin-home", label: "Overview", icon: "dashboard" },
    { href: "books.html", nav: "books", label: "Books", icon: "audit" },
    { href: "audits.html", nav: "audits", label: "Audits", icon: "audit" },
    { href: "accounts.html", nav: "accounts", label: "Accounts", icon: "accounts" },
    { href: "stock.html", nav: "stock", label: "Stock", icon: "stock" },
    { href: "profit.html", nav: "profit", label: "Profit", icon: "audit" },
    { href: "settings.html", nav: "settings", label: "Settings", icon: "settings" },
  ],
  agent: [
    { href: "partner.html", nav: "partner", label: "Dashboard", icon: "dashboard" },
    { href: "settings.html", nav: "settings", label: "Settings", icon: "settings" },
  ],
  super_admin: [
    { href: "dashboard.html", nav: "dashboard", label: "Dashboard", icon: "dashboard" },
    { href: "institutions.html", nav: "institutions", label: "Institutions", icon: "institution" },
    { href: "partners.html", nav: "partners", label: "Partners", icon: "accounts" },
    { href: "accounts.html", nav: "accounts", label: "Accounts", icon: "accounts" },
    { href: "market.html", nav: "market", label: "Market", icon: "market" },
    { href: "review.html", nav: "review", label: "Review", icon: "flag" },
    { href: "activity.html", nav: "activity", label: "Activity", icon: "activity" },
    { href: "privacy.html", nav: "privacy", label: "Data & Privacy", icon: "privacy" },
    { href: "settings.html", nav: "settings", label: "Settings", icon: "settings" },
  ],
};

function renderNav(session, activePage) {
  let items = NAV_ITEMS[session.role] || NAV_ITEMS.staff;
  // A gym counts members where other businesses count stock
  if (session.institution_type === "gym") {
    items = items.map((item) => (item.nav === "stock" ? { href: "members.html", nav: "members", label: "Members", icon: "accounts" } : item));
  }
  // An individual account keeps to a few pages; plan, books and audits sit under Settings
  if (session.role === "staff" && session.individual) {
    items = items.filter((item) => item.nav !== "subscription").map((item) => (item.nav === "audit" ? { ...item, label: "Statements" } : item));
  }
  if (session.role === "staff" && session.staff_type === "teacher") {
    items = items.filter((item) => item.nav !== "stock");
  }

  const sideNav = $(".side-nav");
  if (sideNav) {
    sideNav.innerHTML = items.map((item) => `
      <a class="side-link ${item.nav === activePage ? "is-active" : ""}" data-nav="${item.nav}" href="${item.href}">
        <span class="ico" data-ico="${item.icon}"></span>${item.label}
      </a>
    `).join("");
  }

  const tabbar = $("#tabbar");
  if (tabbar) {
    tabbar.style.gridTemplateColumns = `repeat(${items.length}, 1fr)`;
    tabbar.innerHTML = items.map((item) => `
      <a class="tab ${item.nav === activePage ? "is-active" : ""}" data-nav="${item.nav}" href="${item.href}">
        <span class="ico" data-ico="${item.icon}"></span><span>${item.label}</span>
      </a>
    `).join("");
  }

  paintIcons();
}

function greetingFor(fullName) {
  const hour = new Date().getHours();
  const part = hour < 12 ? "Good morning" : hour < 18 ? "Good afternoon" : "Good evening";
  const first = (fullName || "").trim().split(/\s+/)[0] || "";
  return first ? `${part}, ${first}` : part;
}

// Fills in the topbar/sidebar identity (greeting, institution name + logo) for the current session.
async function renderIdentity(session) {
  const greetingEl = $("#greeting");
  const institutionLineEl = $("#schoolLine");
  const topLogo = $("#topLogo");
  const sidebarLogo = $("#sidebarLogo");
  const sidebarInstitution = $("#sidebarSchool");

  if (greetingEl) greetingEl.textContent = greetingFor(session.full_name);

  if (session.role === "agent") {
    if (institutionLineEl) institutionLineEl.textContent = "Agent dashboard";
    if (sidebarInstitution) sidebarInstitution.textContent = "KNOW Partner";
    return;
  }

  if (session.role === "super_admin") {
    if (institutionLineEl) institutionLineEl.textContent = "Super admin console";
    if (sidebarInstitution) sidebarInstitution.textContent = "KNOW";
    return;
  }

  let institution = null;
  if (session.institution_id) {
    try { institution = await Api.institutions.get(session.institution_id); } catch { /* non-fatal */ }
  }

  if (institutionLineEl) institutionLineEl.textContent = institution?.name || "KNOW";
  if (sidebarInstitution) sidebarInstitution.textContent = institution?.name || "KNOW";

  const logoUrl = institution ? Api.institutions.logoUrl(institution) : null;
  if (logoUrl) {
    if (topLogo) topLogo.src = logoUrl;
    if (sidebarLogo) sidebarLogo.src = logoUrl;
  }
}

// Standard bootstrap for every app page (not login pages): enforce the role guard, paint chrome (nav, identity, theme,
const Splash = (() => {
  const SEEN = "know_splash_seen";
  let el = null;
  let shownAt = 0;
  function show() {
    try { if (sessionStorage.getItem(SEEN)) return; } catch { /* storage blocked */ }
    el = document.createElement("div");
    el.className = "ks-splash";
    el.setAttribute("role", "status");
    el.setAttribute("aria-label", "A pen writing lines into a ledger");
    el.innerHTML = '<div class="ks-wrap"><div class="ks-book"><i class="ks-rule r1"></i><i class="ks-rule r2"></i><i class="ks-rule r3"></i><i class="ks-rule r4"></i><i class="ks-rule r5"></i><i class="ks-entry e1"></i><i class="ks-entry e2"></i><i class="ks-pen"></i></div><h1>Preparing your accounts</h1><p>Bringing every entry into balance</p></div>';
    document.body.appendChild(el);
    shownAt = Date.now();
  }
  function hide() {
    if (!el) return;
    try { sessionStorage.setItem(SEEN, "1"); } catch { /* storage blocked */ }
    const node = el;
    el = null;
    setTimeout(() => {
      node.classList.add("is-done");
      setTimeout(() => node.remove(), 300);
    }, Math.max(0, 700 - (Date.now() - shownAt)));
  }
  return { show, hide };
})();

async function initShell(page, allowedRoles) {
  Splash.show();
  const session = requireRole(...allowedRoles);
  // Sessions saved before workspaces had a type learn it once
  if (session && (session.role === "staff" || session.role === "institution_admin") && (session.institution_type === undefined || session.individual === undefined)) {
    try {
      const me = await Api.users.me();
      session.institution_type = me.institution_type || null;
      session.individual = !!me.individual;
      Session.set({ ...session });
    } catch (e) { /* offline: try again next page */ }
  }
  applyTheme();
  renderNav(session, page);
  await renderIdentity(session);
  bindThemeToggle();
  paintIcons();

  const logoutBtn = $("#logoutBtn");
  if (logoutBtn) logoutBtn.addEventListener("click", logout);

  Splash.hide();
  if (session && (session.role === "staff" || session.role === "institution_admin")) {
    const loader = document.createElement("script");
    loader.src = "./js/payflow.js";
    loader.onload = () => PayFlow.start(session);
    document.head.appendChild(loader);
  }
  if (session && session.institution_type === "gym" && (session.role === "institution_admin" || (session.role === "staff" && session.individual))) {
    const due = document.createElement("script");
    due.src = "./js/memberdue.js";
    document.head.appendChild(due);
  }
  return session;
}



const Sheet = {
  el: null,
  init() {
    this.el = $("#sheetBackdrop");
    if (!this.el) return;
    $("#sheetClose")?.addEventListener("click", () => this.close());
    this.el.addEventListener("click", (e) => { if (e.target === this.el) this.close(); });
  },
  open(title, bodyHtml) {
    if (!this.el) return;
    $("#sheetTitle").textContent = title;
    $("#sheetBody").innerHTML = bodyHtml;
    this.el.hidden = false;
    paintIcons(this.el);
  },
  close() {
    if (!this.el) return;
    this.el.hidden = true;
    $("#sheetBody").innerHTML = "";
    const done = this.onClose;
    this.onClose = null;
    if (done) done();
  },
};

// ui.js is loaded near the end of <body>, after #sheetBackdrop already exists in the DOM, so this can run immediately
Sheet.init();

// "Today", "Yesterday", "3 Oct": a short way to say when something last happened
function lastSeenLabel(iso) {
  if (!iso) return "Never";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return "—";
  const days = Math.floor((Date.now() - d.getTime()) / 86400000);
  if (days <= 0) return "Today";
  if (days === 1) return "Yesterday";
  if (days < 7) return `${days} days ago`;
  return d.toLocaleDateString(undefined, { day: "numeric", month: "short", year: d.getFullYear() === new Date().getFullYear() ? undefined : "numeric" });
}

function daysSinceIso(iso) {
  if (!iso) return Infinity;
  return Math.floor((Date.now() - new Date(iso).getTime()) / 86400000);
}

function describeActivity(a) {
  const who = a.actor_username || "Someone";
  const map = {
    login_success: `${who} signed in`,
    login_failed: `Failed sign-in for ${who}`,
    account_locked: `${who}'s account was locked`,
    login_blocked_locked: `Blocked: ${who} is locked out`,
    login_blocked_inactive: `Blocked: ${who} is deactivated`,
  };
  if (map[a.action]) return map[a.action];
  const base = a.action.replace(/_/g, " ");
  return base.charAt(0).toUpperCase() + base.slice(1) + (a.detail ? `: ${a.detail}` : "");
}
