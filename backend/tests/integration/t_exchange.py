import os
os.environ.update(DATABASE_URL="sqlite:///:memory:", SECRET_KEY="x"*40)
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
configure_mappers(); Base.metadata.create_all(eng)
S = sessionmaker(bind=eng)
app.dependency_overrides[get_db] = lambda: (yield S())
c = TestClient(app, raise_server_exceptions=False)
ok = lambda l, v: print(("PASS " if v else "FAIL ") + l)
pw = "Strong#Pass99"
ro = c.post("/api/auth/signup", json={"username": "ownerx", "full_name": "Owner X", "password": pw, "account_type": "owner", "business_name": "Shop X", "business_type": "shop"})
ho = {"authorization": "Bearer " + ro.json()["access_token"]}
for u in ("branchA", "branchB"):
    r = c.post("/api/users", json={"username": u, "full_name": u, "password": pw, "role": "staff", "staff_type": "general"}, headers=ho)
    assert r.status_code in (200, 201), r.text
tok = lambda u: {"authorization": "Bearer " + c.post("/api/auth/login", data={"username": u, "password": pw}).json()["access_token"]}
ha, hb = tok("branchA"), tok("branchB")
cat = c.post("/api/product-categories", json={"name": "Drinks"}, headers=ha).json()["id"]
it = c.post("/api/stock", json={"name": "Soda", "category_id": cat, "quantity": "10", "unit_price": "50"}, headers=ha)
print("item", it.status_code, it.text[:200])
br = c.get("/api/exchange/branches", headers=ha); print("branches", br.status_code, br.text[:200])
bid = [b["id"] for b in br.json() if b["name"] == "branchB"][0]
tr = c.post("/api/exchange/transfers", json={"item_id": it.json()["id"], "quantity": "3", "to_branch_id": bid, "note": "restock"}, headers=ha)
print("send", tr.status_code, tr.text[:300])
ok("transfer is sent", tr.status_code == 201)
if tr.status_code == 201:
    tid = tr.json()["id"]
    inc = c.get("/api/exchange/transfers", headers=hb).json()
    ok("recipient sees it as incoming", len(inc) == 1 and inc[0]["direction"] == "incoming")
    rc = c.post(f"/api/exchange/transfers/{tid}/receive", headers=hb); print("receive", rc.status_code, rc.text[:200])
    ok("recipient can confirm", rc.status_code == 200)
