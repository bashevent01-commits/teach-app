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
from app.core.constants import KENYA_CONSTITUENCIES
configure_mappers(); Base.metadata.create_all(eng)
S = sessionmaker(bind=eng)
app.dependency_overrides[get_db] = lambda: (yield S())
c = TestClient(app, raise_server_exceptions=False)
ok = lambda l, v: print(("PASS " if v else "FAIL ") + l)
pw = "Strong#Pass99"
ok("290 constituencies in 47 counties", sum(len(v) for v in KENYA_CONSTITUENCIES.values()) == 290 and len(KENYA_CONSTITUENCIES) == 47)
base = {"username": "owner1", "full_name": "Owner One", "password": pw, "account_type": "owner", "business_name": "Shop 1", "business_type": "shop"}
ok("owner sign-up needs a location", c.post("/api/auth/signup", json=base).status_code == 422)
ok("wrong constituency rejected", c.post("/api/auth/signup", json={**base, "county": "Nairobi", "constituency": "Tiaty"}).status_code == 422)
r = c.post("/api/auth/signup", json={**base, "county": "Nairobi", "constituency": "Westlands"})
ok("owner signs up with a location", r.status_code == 201)
ho = {"authorization": "Bearer " + r.json()["access_token"]}
mk = lambda u, extra: c.post("/api/users", json={"username": u, "full_name": u, "password": pw, "role": "staff", "staff_type": "general", **extra}, headers=ho)
ok("branch without a location is refused", mk("branch0", {}).status_code == 422)
ok("branch with a mismatched location is refused", mk("branch0", {"county": "Nairobi", "constituency": "Tiaty"}).status_code == 422)
rb = mk("branch1", {"county": "Mombasa", "constituency": "Nyali"})
ok("branch with a location is created", rb.status_code == 201 and rb.json()["county"] == "Mombasa" and rb.json()["constituency"] == "Nyali")
bid = rb.json()["id"]
up = c.patch(f"/api/users/{bid}", json={"county": "Kisumu", "constituency": "Kisumu East"}, headers=ho)
ok("owner moves a branch to another location", up.status_code == 200 and up.json()["county"] == "Kisumu")
ok("county alone is refused on update", c.patch(f"/api/users/{bid}", json={"county": "Nakuru"}, headers=ho).status_code == 422)
ind = c.post("/api/auth/signup", json={"username": "indy1", "full_name": "Indy One", "password": pw, "account_type": "staff"})
ok("individual with stock needs a location", ind.status_code == 422)
ok("personal account needs none", c.post("/api/auth/signup", json={"username": "pat1", "full_name": "Pat One", "password": pw, "account_type": "personal"}).status_code == 201)
# Market data places stock where its branch trades, not at the head office
from app.core.security import hash_password
from app.models.user import User, UserRole
db = S(); db.add(User(username="boss", full_name="Boss", hashed_password=hash_password(pw), role=UserRole.SUPER_ADMIN)); db.commit(); db.close()
hs = {"authorization": "Bearer " + c.post("/api/auth/login", data={"username": "boss", "password": pw}).json()["access_token"]}
cat = c.post("/api/product-categories", json={"name": "Sugar"}, headers=hs).json()["id"]
def shop(n, branch_loc):
    ro_ = c.post("/api/auth/signup", json={"username": f"own{n}", "full_name": f"Owner {n}", "password": pw, "account_type": "owner", "business_name": f"Corner Shop {n}", "county": "Nairobi", "constituency": "Westlands"})
    assert ro_.status_code == 201, ro_.text
    h = {"authorization": "Bearer " + ro_.json()["access_token"]}
    rb_ = c.post("/api/users", json={"username": f"br{n}", "full_name": f"Branch {n}", "password": pw, "role": "staff", "staff_type": "general", **branch_loc}, headers=h)
    hb_ = {"authorization": "Bearer " + c.post("/api/auth/login", data={"username": f"br{n}", "password": pw}).json()["access_token"]}
    c.post("/api/stock", json={"name": "Sugar 1kg", "category_id": cat, "quantity": "10", "unit_price": str(190 + n)}, headers=hb_)
for n in range(5): shop(n, {"county": "Mombasa", "constituency": "Nyali"})
d = c.get(f"/api/market-analysis/categories/{cat}", headers=hs)
ok("market detail loads", d.status_code == 200)
rg = {x["region"]: x for x in d.json()["regional_breakdown"]}
cg = {x["region"]: x for x in d.json()["constituency_breakdown"]}
ok("5 shops with branches in Mombasa show under Mombasa", "Mombasa" in rg and rg["Mombasa"]["institution_count"] == 5)
ok("head office county Nairobi is not counted", "Nairobi" not in rg)
ok("constituency Nyali shows with its county", "Nyali" in cg and cg["Nyali"]["county"] == "Mombasa")
shop(5, {"county": "Kisumu", "constituency": "Kisumu East"})
d2 = c.get(f"/api/market-analysis/categories/{cat}", headers=hs).json()
ok("a county with fewer than 5 shops stays hidden", all(x["region"] != "Kisumu" for x in d2["regional_breakdown"]))
