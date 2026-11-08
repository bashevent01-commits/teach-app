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
at = lambda *a: datetime(*a, tzinfo=timezone.utc)
def clock(t): bill.utcnow = lambda: t
bh = lambda t: {"authorization": "Bearer " + t}
db = S(); db.add(User(username="boss", full_name="Boss", hashed_password=hash_password(pw), role=UserRole.SUPER_ADMIN)); db.commit(); db.close()
login = lambda u: bh(c.post("/api/auth/login", data={"username": u, "password": pw}).json()["access_token"])
hs = login("boss")
ag = c.post("/api/admin/partners", json={"full_name": "Agent One", "username": "agent1", "email": "a1@x.test", "password": pw, "commission_rate": "20"}, headers=hs)
code = ag.json()["code"]
loc = {"county": "Nairobi", "constituency": "Westlands"}
def owner(name, username, partner=None):
    r = c.post("/api/auth/signup", json={"username": username, "full_name": name, "password": pw, "account_type": "owner", "business_name": name, "partner_code": partner, **loc})
    assert r.status_code == 201, r.text
    return bh(r.json()["access_token"]), r.json()["institution_id"]
ho, iid = owner("Mama Shop", "mamaowner", code)
db = S(); inst = db.get(Institution, iid); inst.created_at = at(2026, 10, 1, 10, 0); db.commit(); db.close()
clock(at(2026, 10, 2, 9, 0))
mk = c.post("/api/users", json={"username": "mamastaff", "full_name": "Staff", "password": pw, "role": "staff", "staff_type": "general", **loc}, headers=ho)
hst = login("mamastaff")
st = lambda h: c.get("/api/billing/status", headers=h).json()
clock(at(2026, 10, 8, 12, 0))
ok("trial: no reminder yet", st(ho)["state"] == "trial" and not st(ho)["popup_due"])
clock(at(2026, 10, 8, 13, 30))
s = st(ho)
ok("after 4pm on the due day the reminder shows", s["popup_due"] and not s["locked"] and s["total"] == 500 and s["can_pay"] and s["agent_registered"])
ok("status carries the till number and name", s["till"] == "4396353" and s["till_name"] == "Obsidian Labs" and s["paybill"] == "4396353")
ok("staff are not shown the reminder", not st(hst)["popup_due"] and not st(hst)["can_pay"])
clock(at(2026, 10, 9, 2, 0))
ok("still open before 6am", c.get("/api/stock", headers=ho).status_code == 200)
clock(at(2026, 10, 9, 3, 30))
S0 = lambda: None
import app.utils.billing as _b; _b._CACHE.clear()
ok("owner is closed out at 6am", c.get("/api/stock", headers=ho).status_code == 402)
ok("staff are closed out too", c.get("/api/stock", headers=hst).status_code == 402)
ok("closed workspace can still read billing", st(ho)["locked"] and st(hst)["locked"])
ok("closed workspace can still load its profile", c.get("/api/users/me", headers=ho).status_code == 200)
good = "SJK4X9PQLM Confirmed. Ksh500.00 sent to OBSIDIAN LABS for account mamaowner on 9/10/26 at 6:30 AM. New M-PESA balance is Ksh1,000.00. Transaction cost, Ksh0.00."
ok("garbage message refused", c.post("/api/billing/proof", json={"message": "I paid already please open"}, headers=ho).status_code == 422)
ok("wrong recipient refused", c.post("/api/billing/proof", json={"message": good.replace("OBSIDIAN LABS", "JUMIA STORE")}, headers=ho).status_code == 422)
ok("short payment refused", c.post("/api/billing/proof", json={"message": good.replace("Ksh500.00", "Ksh300.00")}, headers=ho).status_code == 422)
ok("staff cannot send proof", c.post("/api/billing/proof", json={"message": good}, headers=hst).status_code == 403)
r = c.post("/api/billing/proof", json={"message": good}, headers=ho)
ok("proof accepted", r.status_code == 201)
ok("app opens while the proof waits", c.get("/api/stock", headers=ho).status_code == 200 and st(ho)["proof_status"] == "pending" and not st(ho)["locked"])
ok("staff open too", c.get("/api/stock", headers=hst).status_code == 200)
ok("second proof while waiting refused", c.post("/api/billing/proof", json={"message": good}, headers=ho).status_code == 409)
pid = c.get("/api/admin/payment-proofs", headers=hs).json()[0]["id"]
ok("super admin sees it", c.get("/api/admin/payment-proofs", headers=hs).json()[0]["business"] == "Mama Shop")
ok("owner cannot review", c.post(f"/api/admin/payment-proofs/{pid}/approve", json={}, headers=ho).status_code == 403)
ap = c.post(f"/api/admin/payment-proofs/{pid}/approve", json={}, headers=hs)
ok("approved", ap.status_code == 200 and ap.json()["status"] == "approved")
ok("paid up until next month", st(ho)["state"] == "paid" and not st(ho)["popup_due"])
ha = login("agent1")
dash = c.get("/api/partner/dashboard", headers=ha).json()
ok("agent is told and can ask for that amount", len(dash["notices"]) == 1 and dash["notices"][0]["amount"] == "100.00" and dash["totals"]["requestable"] == "100.00")
nid = dash["notices"][0]["id"]
pr = c.post("/api/partner/payout-requests", json={"notice_id": nid}, headers=ha)
ok("agent asks for the payout", pr.status_code == 201 and pr.json()["amount"] == "100.00")
ok("cannot ask twice for the same money", c.post("/api/partner/payout-requests", json={}, headers=ha).status_code == 409)
lst = c.get("/api/admin/payout-requests", headers=hs).json()
ok("super admin sees the request", len(lst) == 1 and lst[0]["amount"] == "100.00")
pay = c.post(f"/api/admin/payout-requests/{lst[0]['id']}/pay", json={"reference": "QWE123"}, headers=hs)
ok("payout recorded", pay.status_code == 200 and pay.json()["status"] == "paid")
d2 = c.get("/api/partner/dashboard", headers=ha).json()
ok("agent balance is clear", d2["totals"]["balance_due"] == "0.00" and d2["totals"]["paid_out"] == "100.00")
ok("reused M-Pesa code refused", c.post("/api/billing/proof", json={"message": good}, headers=ho).status_code in (409, 422))
# next month: due again, then a rejected proof
clock(at(2026, 11, 8, 13, 30)); _b._CACHE.clear()
ok("next month the reminder returns", st(ho)["popup_due"])
second = "SKL5Y0QRMN Confirmed. Ksh500.00 paid to OBSIDIAN LABS. on 8/11/26 at 4:10 PM.New M-PESA balance is Ksh1,000.00. Transaction cost, Ksh0.00."
ok("second proof accepted", c.post("/api/billing/proof", json={"message": second}, headers=ho).status_code == 201)
pid2 = c.get("/api/admin/payment-proofs", headers=hs).json()[0]["id"]
rj = c.post(f"/api/admin/payment-proofs/{pid2}/reject", json={"note": "Not on the statement"}, headers=hs)
ok("rejected", rj.status_code == 200)
s2 = st(ho)
ok("owner is told it was rejected and can try again", s2["proof_status"] == "rejected" and s2["proof_note"] == "Not on the statement" and s2["popup_due"])
# preview button only for the account called Obsidian
ob, _ = owner("Obsidian Shop", "Obsidian")
ok("Obsidian owner gets the preview", st(ob)["preview_allowed"] is True)
ok("other owners do not", st(ho)["preview_allowed"] is False)
db = S(); n = db.query(FeePayment).filter(FeePayment.institution_id == iid).count(); db.close()
ok("one fee booked with the agent share", n == 1)
