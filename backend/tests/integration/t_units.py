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
from app.core.security import hash_password
from app.core.units import normalize_unit
from app.utils.names import same_thing
configure_mappers(); Base.metadata.create_all(eng)
S = sessionmaker(bind=eng)
app.dependency_overrides[get_db] = lambda: (yield S())
c = TestClient(app, raise_server_exceptions=False)
ok = lambda l, v: print(("PASS " if v else "FAIL ") + l)
pw = "Strong#Pass99"
ok("Good, goods and GOOD are the same", same_thing("Good", "goods") and same_thing("GOODS", "good ") and same_thing("good", "Good"))
ok("plurals and punctuation", same_thing("Tomatoes", "tomato") and same_thing("T-shirts", "tshirt") and same_thing("Cooking  Oil!", "cooking oils"))
ok("word order and small typos", same_thing("Oil cooking", "cooking oil") and same_thing("Cookng oil", "Cooking oil"))
ok("sizes stay different", not same_thing("Bread (400g)", "Bread (800g)") and not same_thing("Milk 500ml", "Milk 1l"))
ok("different things stay different", not same_thing("Soap", "Soup") and not same_thing("Pen", "Pencil") and not same_thing("Rice", "Rich"))
ok("units normalise", normalize_unit("Kgs") == "kg" and normalize_unit("KILO") == "kg" and normalize_unit("pcs") == "pieces" and normalize_unit("Liters") == "litres" and normalize_unit("") == "pieces")
ok("milligrams normalise", normalize_unit("Milligrams") == "mg" and normalize_unit("MG") == "mg")
ok("custom unit is tidied", normalize_unit("  Debe  ") == "debe" and normalize_unit("gunia") == "sacks")
db = S(); db.add(User(username="boss", full_name="Boss", hashed_password=hash_password(pw), role=UserRole.SUPER_ADMIN)); db.commit(); db.close()
login = lambda u: {"authorization": "Bearer " + c.post("/api/auth/login", data={"username": u, "password": pw}).json()["access_token"]}
hs = login("boss")
r = c.post("/api/product-categories", json={"name": "Goods"}, headers=hs)
ok("category made", r.status_code == 201)
gid = r.json()["id"]
ho = {"authorization": "Bearer " + c.post("/api/auth/signup", json={"username": "owner1", "full_name": "Owner One", "password": pw, "account_type": "owner", "business_name": "Shop One", "county": "Nairobi", "constituency": "Westlands"}).json()["access_token"]}
for variant in ["good", "GOOD", "goods", " Good "]:
    d = c.post("/api/product-categories", json={"name": variant}, headers=ho)
    ok(f"'{variant}' is sent to the existing category", d.status_code == 409 and d.json()["existing"]["id"] == gid and "already in the list" in d.json()["detail"])
ok("a really new category is accepted", c.post("/api/product-categories", json={"name": "Bread (400g)"}, headers=ho).status_code == 201)
ok("a different size is accepted", c.post("/api/product-categories", json={"name": "Bread (800g)"}, headers=ho).status_code == 201)
cats = c.get("/api/product-categories", headers=ho).json()
ok("the list is shared with everyone", len(cats) == 3)
a = c.post("/api/stock", json={"name": "Sugar", "category_id": gid, "quantity": "12.5", "unit": "Kgs", "unit_price": "190"}, headers=ho)
ok("stock saved with a unit", a.status_code == 201 and a.json()["unit"] == "kg" and a.json()["quantity"] == "12.50")
b = c.post("/api/stock", json={"name": "Eggs", "category_id": gid, "quantity": "30"}, headers=ho)
ok("unit defaults to pieces", b.json()["unit"] == "pieces")
u = c.patch(f"/api/stock/{b.json()['id']}", json={"unit": "Trays"}, headers=ho)
ok("unit can be changed", u.status_code == 200 and u.json()["unit"] == "trays")
c.post("/api/users", json={"username": "staff1", "full_name": "Staff One", "password": pw, "role": "staff", "staff_type": "general", "county": "Nairobi", "constituency": "Westlands"}, headers=ho)
hst = login("staff1")
sa = c.post("/api/stock", json={"name": "Flour", "category_id": gid, "quantity": "20", "unit": "kilos", "unit_price": "100"}, headers=hst).json()
tx = c.post("/api/transactions", data={"type": "income", "method": "cash", "category_type": "STOCK", "amount": "250", "stock_item_id": sa["id"], "quantity": "2.5"}, headers=hst)
ok("a sale shows its unit", tx.status_code == 201 and tx.json()["unit"] == "kg" and tx.json()["quantity"] == "2.50")
