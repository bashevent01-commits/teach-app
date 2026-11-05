import os
os.environ.update(DATABASE_URL="sqlite:///:memory:", SECRET_KEY="x"*40)
from datetime import date
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
import app.routers.members as rm
import app.routers.transactions as rt
from app.utils.members import add_period, advance, status_of
configure_mappers(); Base.metadata.create_all(eng)
S = sessionmaker(bind=eng)
app.dependency_overrides[get_db] = lambda: (yield S())
c = TestClient(app, raise_server_exceptions=False)
ok = lambda l, v: print(("PASS " if v else "FAIL ") + l)
pw = "Strong#Pass99"
loc = {"county": "Nairobi", "constituency": "Westlands"}
def day(d): rm.today_local = lambda: d; rt.today_local = lambda: d
day(date(2026, 11, 10))
ok("monthly clamps to month end", add_period(date(2026, 1, 31), "monthly") == date(2026, 2, 28))
ok("weekly, quarterly, yearly", add_period(date(2026, 11, 10), "weekly") == date(2026, 11, 17) and add_period(date(2026, 11, 10), "quarterly") == date(2027, 2, 10) and add_period(date(2026, 11, 10), "yearly") == date(2027, 11, 10))
ok("on time runs on from the due date, late restarts from today", advance(date(2026, 11, 12), date(2026, 11, 10), "monthly") == date(2026, 12, 12) and advance(date(2026, 11, 1), date(2026, 11, 10), "monthly") == date(2026, 12, 10))
ok("status words", status_of(True, date(2026, 11, 9), date(2026, 11, 10))[0] == "overdue" and status_of(True, date(2026, 11, 10), date(2026, 11, 10))[0] == "due_today" and status_of(True, date(2026, 11, 11), date(2026, 11, 10))[0] == "due_tomorrow")
r = c.post("/api/auth/signup", json={"username": "gymowner", "full_name": "Gym Owner", "password": pw, "account_type": "owner", "business_name": "Iron House", "business_type": "gym", **loc})
ok("gym owner signs up and the token names the type", r.status_code == 201 and r.json()["institution_type"] == "gym")
ho = {"authorization": "Bearer " + r.json()["access_token"]}
ok("me carries the type", c.get("/api/users/me", headers=ho).json()["institution_type"] == "gym")
c.post("/api/users", json={"username": "frontdesk", "full_name": "Front Desk", "password": pw, "role": "staff", "staff_type": "general", **loc}, headers=ho)
hs = {"authorization": "Bearer " + c.post("/api/auth/login", data={"username": "frontdesk", "password": pw}).json()["access_token"]}
m1 = c.post("/api/members", json={"name": "Wanjiku", "phone": "0712345678", "plan": "Monthly", "fee": "3000", "next_due": "2026-11-11"}, headers=hs)
ok("staff adds a member", m1.status_code == 201 and m1.json()["status"] == "due_tomorrow")
m2 = c.post("/api/members", json={"name": "Otieno", "fee": "1500", "period": "weekly", "next_due": "2026-11-10"}, headers=hs).json()
m3 = c.post("/api/members", json={"name": "Achieng", "fee": "3000", "next_due": "2026-11-05"}, headers=hs).json()
m4 = c.post("/api/members", json={"name": "Kamau", "fee": "3000", "next_due": "2026-12-20"}, headers=hs).json()
due = c.get("/api/members/due", headers=ho).json()
ok("owner sees who is due tomorrow, today and behind", [m["name"] for m in due["tomorrow"]] == ["Wanjiku"] and [m["name"] for m in due["today"]] == ["Otieno"] and [m["name"] for m in due["overdue"]] == ["Achieng"])
ok("members not due are left out", all(m["name"] != "Kamau" for k in ("today", "tomorrow", "overdue") for m in due[k]))
ok("bad period refused", c.post("/api/members", json={"name": "Zed Zed", "fee": "100", "period": "daily"}, headers=hs).status_code == 422)
pay = c.post("/api/transactions", data={"type": "income", "method": "cash", "category_type": "OTHER", "amount": "3000", "member_id": m1.json()["id"]}, headers=hs)
ok("a payment is recorded as a subscription", pay.status_code == 201 and pay.json()["category"] == "Subscription" and pay.json()["member_name"] == "Wanjiku")
after = {m["name"]: m for m in c.get("/api/members", headers=hs).json()}
ok("paying on time moves the due date on a month", after["Wanjiku"]["next_due"] == "2026-12-11" and after["Wanjiku"]["last_paid_on"] == "2026-11-10")
late = c.post("/api/transactions", data={"type": "income", "method": "cash", "category_type": "OTHER", "amount": "3000", "member_id": m3["id"]}, headers=hs)
ok("paying late restarts from today", {m["name"]: m for m in c.get("/api/members", headers=hs).json()}["Achieng"]["next_due"] == "2026-12-10")
ok("owner cannot record payments", c.post("/api/transactions", data={"type": "income", "method": "cash", "category_type": "OTHER", "amount": "3000", "member_id": m2["id"]}, headers=ho).status_code == 403)
ok("expenses cannot carry a member", c.post("/api/transactions", data={"type": "expense", "method": "cash", "category_type": "OTHER", "category": "Rent", "amount": "500", "member_id": m2["id"]}, headers=hs).status_code == 400)
ok("deleting a member with payments pauses them", c.delete(f"/api/members/{m3['id']}", headers=hs).status_code == 204 and {m["name"]: m for m in c.get("/api/members", headers=hs).json()}["Achieng"]["status"] == "paused")
ok("paused members are not chased", all(m["name"] != "Achieng" for k in ("today", "tomorrow", "overdue") for m in c.get("/api/members/due", headers=ho).json()[k]))
ok("deleting one with no payments removes them", c.delete(f"/api/members/{m4['id']}", headers=hs).status_code == 204 and all(m["name"] != "Kamau" for m in c.get("/api/members", headers=hs).json()))
shop = c.post("/api/auth/signup", json={"username": "shopowner", "full_name": "Shop Owner", "password": pw, "account_type": "owner", "business_name": "Plain Shop", "business_type": "shop", **loc}).json()
ok("a shop has no members", c.get("/api/members", headers={"authorization": "Bearer " + shop["access_token"]}).status_code == 403)
c.post("/api/users", json={"username": "frontdesk2", "full_name": "Front Desk Two", "password": pw, "role": "staff", "staff_type": "general", **loc}, headers=ho)
h2 = {"authorization": "Bearer " + c.post("/api/auth/login", data={"username": "frontdesk2", "password": pw}).json()["access_token"]}
ok("another branch does not see this branch's members", c.get("/api/members", headers=h2).json() == [])
