import calendar
import re
import time
from dataclasses import dataclass
from datetime import date, datetime, timedelta, timezone
from decimal import Decimal
from zoneinfo import ZoneInfo

from sqlalchemy.orm import Session

from app.models.activity_log import ActivityLog
from app.models.billing import PaymentProof
from app.models.institution import Institution
from app.models.partner import FeePayment
from app.models.user import User, UserRole
from app.utils.mpesa_parser import parse_message

LOCAL = ZoneInfo("Africa/Nairobi")
TRIAL_DAYS = 7
INDIVIDUAL_PRICE = 200
OWNER_PRICE = 500
OWNER_INCLUDED_ACCOUNTS = 5
EXTRA_ACCOUNT_PRICE = 200
# The reminder appears from 4pm Nairobi time; an unpaid workspace closes at 6am the next morning
POPUP_HOUR = 16
LOCK_HOUR = 6
PAYBILL = "4396353"
PAYBILL_NAME = "Obsidian Labs"
PREVIEW_USERNAME = "obsidian"
CACHE_SECONDS = 45


def utcnow() -> datetime:
    return datetime.now(timezone.utc)


def add_months(moment: datetime, months: int) -> datetime:
    index = moment.year * 12 + (moment.month - 1) + months
    year, month = divmod(index, 12)
    month += 1
    return moment.replace(year=year, month=month, day=min(moment.day, calendar.monthrange(year, month)[1]))


def _first_popup(moment: datetime) -> datetime:
    candidate = moment.replace(hour=POPUP_HOUR, minute=0, second=0, microsecond=0)
    return candidate if candidate >= moment else candidate + timedelta(days=1)


def _aware(value: datetime) -> datetime:
    return value if value.tzinfo else value.replace(tzinfo=timezone.utc)


UPGRADE_PRICE = INDIVIDUAL_PRICE


def is_free(institution: Institution | None) -> bool:
    return institution is not None and institution.tier == "free"


def pending_upgrade(db: Session, institution_id: int) -> bool:
    return db.query(PaymentProof.id).filter(PaymentProof.institution_id == institution_id, PaymentProof.purpose == "upgrade", PaymentProof.status == "pending").first() is not None


def has_premium(db: Session, institution: Institution | None) -> bool:
    """The paid features: open to paying workspaces, and to a free one while its upgrade payment is waiting to be checked."""
    return not is_free(institution) or pending_upgrade(db, institution.id)


def require_premium(db: Session, user: User, what: str) -> None:
    """Refuses a free workspace a paid feature; the message starts with Upgrade so apps can show the banner."""
    from fastapi import HTTPException, status

    inst = db.get(Institution, user.institution_id) if user.institution_id else None
    if user.role in (UserRole.STAFF, UserRole.INSTITUTION_ADMIN) and not has_premium(db, inst):
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail=f"Upgrade to the paid plan to {what}. It is KES {UPGRADE_PRICE} a month.")


def is_billed(db: Session, institution: Institution) -> bool:
    """Only workspaces that signed themselves up are billed; ones a super admin set up are handled by hand."""
    if is_free(institution):
        return False
    if institution.type == "individual":
        return True
    return db.query(ActivityLog.id).filter(
        ActivityLog.action == "self_signup", ActivityLog.target_type == "institution", ActivityLog.target_id == institution.id,
    ).first() is not None


@dataclass
class Cycle:
    billed: bool = False
    individual: bool = False
    plan: str | None = None
    base: int = 0
    used: int = 0
    extra: int = 0
    total: int = 0
    state: str = "none"
    trial_ends: datetime | None = None
    popup_from: datetime | None = None
    lock_at: datetime | None = None
    due_date: date | None = None
    paid_months: int = 0
    proof_status: str = "none"
    proof_note: str | None = None
    period: str | None = None
    paid_until: datetime | None = None


def plan_amounts(db: Session, institution: Institution) -> tuple[str, int, int, int, int]:
    if institution.type == "individual":
        return "individual", INDIVIDUAL_PRICE, 0, 0, INDIVIDUAL_PRICE
    used = db.query(User).filter(User.institution_id == institution.id, User.role == UserRole.STAFF, User.is_active.is_(True)).count()
    extra = max(0, used - OWNER_INCLUDED_ACCOUNTS)
    return "owner", OWNER_PRICE, used, extra, OWNER_PRICE + extra * EXTRA_ACCOUNT_PRICE


def cycle(db: Session, institution: Institution | None, now: datetime | None = None) -> Cycle:
    """Where a workspace stands: trial, paid, due (reminder showing), pending (proof waiting), or locked."""
    if not institution or not institution.created_at or not is_billed(db, institution):
        return Cycle()
    now = now or utcnow()
    plan, base, used, extra, total = plan_amounts(db, institution)
    paid = db.query(FeePayment).filter(FeePayment.institution_id == institution.id, FeePayment.voided_at.is_(None), FeePayment.id > (institution.fee_floor or 0)).count()
    # A workspace that upgraded from free is billed from the day it upgraded, with no trial
    trial_ends = _aware(institution.billing_start) if institution.billing_start else _aware(institution.created_at) + timedelta(days=TRIAL_DAYS)
    due_moment = add_months(trial_ends.astimezone(LOCAL), paid)
    popup_from = _first_popup(due_moment)
    lock_at = (popup_from + timedelta(days=1)).replace(hour=LOCK_HOUR, minute=0, second=0, microsecond=0)
    out = Cycle(billed=True, individual=plan == "individual", plan=plan, base=base, used=used, extra=extra, total=total,
                trial_ends=trial_ends, popup_from=popup_from, lock_at=lock_at, due_date=popup_from.date(), paid_months=paid, paid_until=due_moment,
                period=add_months(trial_ends.astimezone(LOCAL), paid).strftime("%Y-%m"))
    latest = db.query(PaymentProof).filter(PaymentProof.institution_id == institution.id).order_by(PaymentProof.id.desc()).first()
    pending = latest is not None and latest.status == "pending"
    if latest is not None and latest.status == "rejected":
        out.proof_status, out.proof_note = "rejected", latest.review_note
    if pending:
        out.proof_status = "pending"
    if now < popup_from.astimezone(timezone.utc):
        out.state = "trial" if paid == 0 else "paid"
    elif pending:
        out.state = "pending"
    elif now >= lock_at.astimezone(timezone.utc):
        out.state = "locked"
    else:
        out.state = "due"
    return out


_CACHE: dict[int, tuple[float, bool]] = {}


def forget(institution_id: int | None) -> None:
    _CACHE.pop(institution_id, None)


def workspace_locked(db: Session, institution_id: int) -> bool:
    hit = _CACHE.get(institution_id)
    if hit and hit[0] > time.monotonic():
        return hit[1]
    locked = cycle(db, db.get(Institution, institution_id)).state == "locked"
    _CACHE[institution_id] = (time.monotonic() + CACHE_SECONDS, locked)
    return locked


def check_proof(message: str, total_due: int, now: datetime | None = None) -> dict:
    """Reads a pasted M-Pesa message and refuses one that does not look like this payment; never trusts it as final."""
    now = now or utcnow()
    parsed = parse_message(message)
    if not parsed["matched"] or parsed["amount"] is None or parsed["when"] is None or parsed["direction"] != "out":
        raise ValueError("We couldn't read that message. Paste the whole M-Pesa confirmation exactly as it arrived.")
    recipient = parsed["counterparty"] or ""
    if "OBSIDIAN" not in re.sub(r"[^A-Z]", "", recipient.upper()):
        raise ValueError(f"That payment went to {recipient or 'someone else'}, not {PAYBILL_NAME}. Pay paybill {PAYBILL} and paste that message.")
    if parsed["amount"] < Decimal(total_due):
        raise ValueError(f"That message shows KES {parsed['amount']:,.2f} but KES {total_due:,} is due.")
    when = parsed["when"].replace(tzinfo=LOCAL)
    if when > now.astimezone(LOCAL) + timedelta(hours=1) or when < now.astimezone(LOCAL) - timedelta(days=14):
        raise ValueError("That payment is more than 2 weeks old. Paste the message from your latest payment.")
    return {"code": parsed["code"], "amount": parsed["amount"], "paid_at": when, "recipient": recipient[:120], "account_ref": (parsed["account_ref"] or "")[:80] or None}


class UpgradeConflict(Exception):
    pass


def upgrade_quote(db: Session, institution: Institution, now: datetime | None = None) -> dict:
    """What it costs a paid individual to become a business: unused days of the KES 200 month are credited toward the first KES 500 month."""
    now = now or utcnow()
    c = cycle(db, institution, now)
    out = {"price": OWNER_PRICE, "credit": 0, "due_now": OWNER_PRICE, "state": c.state, "pending": c.proof_status == "pending"}
    if c.state == "trial":
        # Nothing has been paid yet, so the trial simply carries on at the business price
        out.update(due_now=0)
    elif c.state == "paid" and c.paid_until is not None:
        length = (c.paid_until - add_months(c.paid_until, -1)).total_seconds()
        remaining = max(0.0, (c.paid_until.astimezone(timezone.utc) - now).total_seconds())
        credit = min(INDIVIDUAL_PRICE, int(INDIVIDUAL_PRICE * remaining / length)) if length > 0 else 0
        out.update(credit=credit, due_now=OWNER_PRICE - credit)
    return out


def convert_to_business(db: Session, institution: Institution, details: dict, restart_billing: bool, now: datetime | None = None) -> User:
    """Turns an individual workspace into a business: it keeps all its data, the person's login stays as the first branch, and a new owner login is added."""
    now = now or utcnow()
    from sqlalchemy import func as sa_func
    if db.query(Institution.id).filter(sa_func.lower(Institution.name) == details["business_name"].lower(), Institution.id != institution.id).first():
        raise UpgradeConflict("A business with this name already exists")
    if db.query(User.id).filter(sa_func.lower(User.username) == details["owner_username"].lower()).first():
        raise UpgradeConflict("That owner username is already taken")
    branch = db.query(User).filter(User.institution_id == institution.id, User.role == UserRole.STAFF).first()
    institution.name = details["business_name"]
    institution.type = details["business_type"]
    institution.focus = None
    institution.region = branch.county if branch else None
    institution.constituency = branch.constituency if branch else None
    owner = User(username=details["owner_username"], full_name=details["owner_full_name"], email=details.get("owner_email"),
                 hashed_password=details["owner_password_hash"], role=UserRole.INSTITUTION_ADMIN, institution_id=institution.id)
    db.add(owner)
    if restart_billing:
        from app.models.partner import FeePayment as FP
        institution.billing_start = now
        institution.fee_floor = db.query(sa_func.coalesce(sa_func.max(FP.id), 0)).scalar() or 0
    db.flush()
    forget(institution.id)
    return owner
