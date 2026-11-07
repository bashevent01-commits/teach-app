import os
os.environ.update(DATABASE_URL="sqlite:///:memory:", SECRET_KEY="x"*40)
from datetime import datetime, timezone
from sqlalchemy import create_engine
from sqlalchemy.pool import StaticPool
from sqlalchemy.orm import sessionmaker, configure_mappers
from fastapi.testclient import TestClient
from app.core import database
eng = create_engine("sqlite://", connect_args={"check_same_thread": False}, poolclass=StaticPool)
database.engine = eng; database.SessionLocal = sessionmaker(bind=eng)
from app.main import app
from app.core.limiter import limiter
limiter.enabled = False
from app.core.database import Base, get_db
from app.models import *
from app.core.security import hash_password
from app.utils import billing as bill
configure_mappers(); Base.metadata.create_all(eng)
S = sessionmaker(bind=eng)
app.dependency_overrides[get_db] = lambda: (yield S())
c = TestClient(app, raise_server_exceptions=False)
ok = lambda l, v: print(("PASS " if v else "FAIL ") + l)
pw = "Strong#Pass99"
bh = lambda t: {"authorization": "Bearer " + t}
at = lambda *a: datetime(*a, tzinfo=timezone.utc)
def clock(t): bill.utcnow = lambda: t; bill._CACHE.clear()
db = S(); db.add(User(username="boss", full_name="Boss", hashed_password=hash_password(pw), role=UserRole.SUPER_ADMIN)); db.commit(); db.close()
login = lambda u: bh(c.post("/api/auth/login", data={"username": u, "password": pw}).json()["access_token"])
hs = login("boss")
agent = c.post("/api/admin/partners", json={"full_name": "Agent One", "username": "agent1", "email": "a1@x.test", "password": pw, "commission_rate": "20"}, headers=hs).json()
loc = {"county": "Nairobi", "constituency": "Westlands"}
def individual(username, **extra):
    r = c.post("/api/auth/signup", json={"username": username, "full_name": username.title(), "password": pw, "account_type": "staff", "plan": "paid", **loc, **extra})
    assert r.status_code == 201, r.text
    return r.json()
gym = individual("gymsolo", business_type="gym")
ok("an individual can choose gym", gym["institution_type"] == "gym" and gym["individual"] is True)
hg = bh(gym["access_token"])
ok("and gets members", c.post("/api/members", json={"name": "Wanjiku", "fee": "2000"}, headers=hg).status_code == 201)
shop = individual("shopsolo")
ok("an individual shop is plain individual", shop["institution_type"] == "individual" and shop["individual"] is True)
ok("an individual shop has no members", c.get("/api/members", headers=bh(shop["access_token"])).status_code == 403)
ok("owners are never individual", c.post("/api/auth/signup", json={"username": "ownerone", "full_name": "Owner One", "password": pw, "account_type": "owner", "business_name": "Big Shop", **loc}).json()["individual"] is False)
# upgrade during the free week: no payment, converts at once
clock(at(2026, 10, 2, 9, 0))
db = S(); db.get(Institution, shop["institution_id"]).created_at = at(2026, 10, 1, 10, 0); db.commit(); db.close()
hsh = bh(shop["access_token"])
q = c.get("/api/billing/upgrade-quote", headers=hsh).json()
ok("in the free week nothing is due", q["due_now"] == 0 and q["state"] == "trial")
details = {"business_name": "Solo Traders", "business_type": "shop", "owner_username": "soloowner", "owner_password": pw, "owner_email": "o@x.test"}
r = c.post("/api/billing/upgrade-to-business", json=details, headers=hsh)
ok("converted at once", r.status_code == 201 and r.json()["status"] == "converted")
ho = login("soloowner")
st = c.get("/api/billing/status", headers=ho).json()
ok("the new owner pays the business plan and is not individual", st["plan"] == "owner" and st["total"] == 500 and st["individual"] is False and st["institution_type"] == "shop")
st0 = c.get("/api/billing/status", headers=hsh).json()
ok("the old login is now a branch of the business", st0["individual"] is False and st0["can_pay"] is False and st0["can_upgrade_business"] is False)
ok("the old login keeps working as staff", c.get("/api/stock", headers=hsh).status_code == 200)
ok("the owner sees the old login as a branch", any(u["username"] == "shopsolo" for u in c.get("/api/users", headers=ho).json()))
# upgrade after paying: credit the unused days
paid = individual("paidsolo", partner_code=agent["code"])
db = S(); db.get(Institution, paid["institution_id"]).created_at = at(2026, 10, 1, 10, 0); db.commit(); db.close()
hp = bh(paid["access_token"])
clock(at(2026, 10, 9, 9, 0))
fee = "SJK4X9PQLM Confirmed. Ksh200.00 sent to OBSIDIAN LABS for account paidsolo on 9/10/26 at 12:00 PM. New M-PESA balance is Ksh1,000.00. Transaction cost, Ksh0.00."
c.post("/api/billing/proof", json={"message": fee}, headers=hp)
fid = c.get("/api/admin/payment-proofs", headers=hs).json()[0]["id"]
c.post(f"/api/admin/payment-proofs/{fid}/approve", json={}, headers=hs)
clock(at(2026, 11, 1, 10, 0))
q = c.get("/api/billing/upgrade-quote", headers=hp).json()
ok("7 of 31 days left credits KES 45 and KES 455 is due", q["state"] == "paid" and q["credit"] == 45 and q["due_now"] == 455)
upg = {"business_name": "Paid Traders", "business_type": "shop", "owner_username": "paidowner", "owner_password": pw}
ok("a payment is required", c.post("/api/billing/upgrade-to-business", json=upg, headers=hp).status_code == 422)
short = "SKL5Y0QRMN Confirmed. Ksh300.00 sent to OBSIDIAN LABS for account paidsolo on 1/11/26 at 1:00 PM. New M-PESA balance is Ksh1,000.00. Transaction cost, Ksh0.00."
ok("too little is refused", c.post("/api/billing/upgrade-to-business", json={**upg, "message": short}, headers=hp).status_code == 422)
ok("a name already taken is refused", c.post("/api/billing/upgrade-to-business", json={**upg, "business_name": "solo traders", "message": short.replace("300", "455")}, headers=hp).status_code == 409)
good = short.replace("Ksh300.00", "Ksh455.00")
ok("the payment is accepted for checking", c.post("/api/billing/upgrade-to-business", json={**upg, "message": good}, headers=hp).status_code == 201)
ok("nothing changes until it is confirmed", c.get("/api/billing/status", headers=hp).json()["individual"] is True)
pid = c.get("/api/admin/payment-proofs", headers=hs).json()[0]
ok("super admin sees a business upgrade", pid["purpose"] == "business" and pid["amount"] == "455.00")
ap = c.post(f"/api/admin/payment-proofs/{pid['id']}/approve", json={}, headers=hs)
ok("approved", ap.status_code == 200)
hpo = login("paidowner")
st = c.get("/api/billing/status", headers=hpo).json()
ok("the business is billed KES 500 and paid up for a month from today", st["plan"] == "owner" and st["total"] == 500 and st["state"] == "paid" and st["due_date"].startswith("2026-12-01"))
ok("the agent earns on what was actually paid", (lambda d: d["notices"] and d["notices"][0]["amount"] == "91.00" and "became a business" in d["notices"][0]["text"])(c.get("/api/partner/dashboard", headers=login("agent1")).json()))
# a rejected upgrade changes nothing
paid2 = individual("paidtwo")
db = S(); db.get(Institution, paid2["institution_id"]).created_at = at(2026, 10, 1, 10, 0); db.commit(); db.close()
h2 = bh(paid2["access_token"])
clock(at(2026, 10, 9, 9, 0))
c.post("/api/billing/proof", json={"message": fee.replace("SJK4X9PQLM", "SMN6Z1RSOP").replace("paidsolo", "paidtwo")}, headers=h2)
fid = c.get("/api/admin/payment-proofs", headers=hs).json()[0]["id"]
c.post(f"/api/admin/payment-proofs/{fid}/approve", json={}, headers=hs)
clock(at(2026, 11, 1, 10, 0))
c.post("/api/billing/upgrade-to-business", json={**upg, "business_name": "Two Traders", "owner_username": "twoowner", "message": good.replace("SKL5Y0QRMN", "STO7A2STQR").replace("paidsolo", "paidtwo")}, headers=h2)
rid = c.get("/api/admin/payment-proofs", headers=hs).json()[0]["id"]
c.post(f"/api/admin/payment-proofs/{rid}/reject", json={"note": "Not on the statement"}, headers=hs)
st = c.get("/api/billing/status", headers=h2).json()
ok("after a rejection the account is still an individual", st["individual"] is True and st["can_upgrade_business"] is True)
free = c.post("/api/auth/signup", json={"username": "freesolo", "full_name": "Free Solo", "password": pw, "account_type": "staff", "plan": "free", **loc}).json()
ok("a free account cannot convert", c.post("/api/billing/upgrade-to-business", json=upg, headers=bh(free["access_token"])).status_code == 403)
pers = c.post("/api/auth/signup", json={"username": "perso", "full_name": "Perso Nal", "password": pw, "account_type": "personal", "plan": "paid"}).json()
ok("a personal account cannot convert", c.get("/api/billing/upgrade-quote", headers=bh(pers["access_token"])).status_code == 403)
