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
db = S(); db.add(User(username="boss", full_name="Boss", hashed_password=hash_password(pw), role=UserRole.SUPER_ADMIN)); db.commit(); db.close()
login = lambda u: bh(c.post("/api/auth/login", data={"username": u, "password": pw}).json()["access_token"])
hs = login("boss")
agent = c.post("/api/admin/partners", json={"full_name": "Agent One", "username": "agent1", "email": "a1@x.test", "password": pw, "commission_rate": "20"}, headers=hs).json()
loc = {"county": "Nairobi", "constituency": "Westlands"}
free = c.post("/api/auth/signup", json={"username": "freeuser", "full_name": "Free User", "password": pw, "account_type": "personal", "plan": "free", "partner_code": agent["code"]})
ok("a free personal account signs up", free.status_code == 201)
hf = bh(free.json()["access_token"])
st = c.get("/api/billing/status", headers=hf).json()
ok("status says free, nothing is due and nothing locks", st["tier"] == "free" and st["premium"] is False and st["applies"] is False and st["locked"] is False and st["total"] == 200)
bill.utcnow = lambda: datetime(2030, 1, 1, tzinfo=timezone.utc)
bill._CACHE.clear()
ok("a free account is never closed, however long it has been", c.get("/api/transactions", headers=hf).status_code == 200)
ok("owners cannot be free", c.post("/api/auth/signup", json={"username": "ownerx", "full_name": "Owner X", "password": pw, "account_type": "owner", "business_name": "Shop X", "plan": "free", **loc}).status_code == 422)
paid = c.post("/api/auth/signup", json={"username": "paiduser", "full_name": "Paid User", "password": pw, "account_type": "staff", "plan": "paid", **loc})
ok("a paid individual is not free", c.get("/api/billing/status", headers=bh(paid.json()["access_token"])).json()["tier"] == "paid")
png = b"\x89PNG\r\n\x1a\n" + b"0" * 64
ok("free: a transaction photo is refused with an upgrade message", (lambda r: r.status_code == 403 and r.json()["detail"].startswith("Upgrade"))(c.post("/api/transactions", data={"type": "income", "method": "cash", "category_type": "OTHER", "category": "Gift", "amount": "100"}, files={"image": ("a.png", png, "image/png")}, headers=hf)))
ok("free: a plain transaction works", c.post("/api/transactions", data={"type": "income", "method": "cash", "category_type": "OTHER", "category": "Gift", "amount": "100"}, headers=hf).status_code == 201)
ok("free: statements cannot be printed", (lambda r: r.status_code == 403 and r.json()["detail"].startswith("Upgrade"))(c.get("/api/reports/statements.pdf", params={"start": "2029-01-01T00:00:00", "end": "2031-01-01T00:00:00"}, headers=hf)))
ok("free: documents cannot be stored", c.post("/api/documents", data={"doc_type": "receipt", "document_date": "2030-01-01"}, headers=hf).status_code == 403)
ok("paid: statements print", c.get("/api/reports/statements.pdf", params={"start": "2029-01-01T00:00:00", "end": "2031-01-01T00:00:00"}, headers=bh(paid.json()["access_token"])).status_code != 403)
good = "SJK4X9PQLM Confirmed. Ksh200.00 sent to OBSIDIAN LABS for account freeuser on 1/1/30 at 2:30 AM. New M-PESA balance is Ksh1,000.00. Transaction cost, Ksh0.00."
ok("an upgrade message that is too small is refused", c.post("/api/billing/proof", json={"message": good.replace("Ksh200.00", "Ksh100.00")}, headers=hf).status_code == 422)
ok("an upgrade message is accepted", c.post("/api/billing/proof", json={"message": good}, headers=hf).status_code == 201)
st = c.get("/api/billing/status", headers=hf).json()
ok("paid features open while the upgrade is checked", st["tier"] == "free" and st["premium"] is True and st["proof_status"] == "pending")
ok("statements print while pending", c.get("/api/reports/statements.pdf", params={"start": "2029-01-01T00:00:00", "end": "2031-01-01T00:00:00"}, headers=hf).status_code != 403)
proof = c.get("/api/admin/payment-proofs", headers=hs).json()[0]
ok("super admin sees it as an upgrade", proof["purpose"] == "upgrade" and proof["months"] == 1)
ap = c.post(f"/api/admin/payment-proofs/{proof['id']}/approve", json={}, headers=hs)
ok("approved", ap.status_code == 200)
st = c.get("/api/billing/status", headers=hf).json()
ok("now a paid workspace, billed from today, next payment a month away", st["tier"] == "paid" and st["applies"] is True and st["state"] == "paid" and st["premium"] is True and st["due_date"].startswith("2030-02"))
dash = c.get("/api/partner/dashboard", headers=bh(c.post("/api/auth/login", data={"username": "agent1", "password": pw}).json()["access_token"])).json()
ok("the agent earns on the upgrade", dash["notices"] and dash["notices"][0]["amount"] == "40.00" and "upgraded" in dash["notices"][0]["text"])
# a rejected upgrade leaves the account free
free2 = c.post("/api/auth/signup", json={"username": "freetwo", "full_name": "Free Two", "password": pw, "account_type": "staff", "plan": "free", **loc})
h2 = bh(free2.json()["access_token"])
c.post("/api/billing/proof", json={"message": good.replace("SJK4X9PQLM", "SKL5Y0QRMN").replace("freeuser", "freetwo")}, headers=h2)
pid = c.get("/api/admin/payment-proofs", headers=hs).json()[0]["id"]
c.post(f"/api/admin/payment-proofs/{pid}/reject", json={"note": "Not on the statement"}, headers=hs)
st2 = c.get("/api/billing/status", headers=h2).json()
ok("a rejected upgrade keeps the account free and says why", st2["tier"] == "free" and st2["premium"] is False and st2["proof_status"] == "rejected" and st2["proof_note"] == "Not on the statement")
ok("and the paid features close again", c.get("/api/reports/statements.pdf", params={"start": "2029-01-01T00:00:00", "end": "2031-01-01T00:00:00"}, headers=h2).status_code == 403)
