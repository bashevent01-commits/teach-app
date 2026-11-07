import json
from datetime import date, datetime

from fastapi import APIRouter, Depends, HTTPException, Request, status
from pydantic import BaseModel, Field, field_validator
from sqlalchemy.orm import Session

from app.core.activity_log import log_activity
from app.core.database import get_db
from app.core.deps import get_current_user
from app.core.limiter import limiter
from app.models.billing import PaymentProof
from app.models.institution import Institution
from app.core.security import hash_password
from app.models.user import StaffType, User, UserRole
from app.schemas.user import check_password, clean_email
from app.utils import billing as bill
from app.utils.billing import (UPGRADE_PRICE, EXTRA_ACCOUNT_PRICE, INDIVIDUAL_PRICE, OWNER_INCLUDED_ACCOUNTS, OWNER_PRICE, PAYBILL, PAYBILL_NAME, TRIAL_DAYS)

router = APIRouter(prefix="/api/billing", tags=["billing"])


class BillingStatus(BaseModel):
    applies: bool
    plan: str | None = None
    plan_label: str = ""
    state: str = "none"
    trial_days: int = TRIAL_DAYS
    trial_ends_at: datetime | None = None
    days_left: int = 0
    base_amount: int = 0
    included_accounts: int = 0
    accounts_used: int = 0
    extra_accounts: int = 0
    extra_rate: int = EXTRA_ACCOUNT_PRICE
    extra_amount: int = 0
    total: int = 0
    note: str = ""
    # The payment reminder and the closing rule
    due_date: date | None = None
    popup_from: datetime | None = None
    lock_at: datetime | None = None
    popup_due: bool = False
    locked: bool = False
    can_pay: bool = False
    proof_status: str = "none"
    proof_note: str | None = None
    paybill: str = PAYBILL
    paybill_name: str = PAYBILL_NAME
    account_ref: str = ""
    agent_registered: bool = False
    preview_allowed: bool = False
    individual: bool = False
    institution_type: str | None = None
    can_upgrade_business: bool = False
    # "free" or "paid"; premium is true when the paid features are open (including while an upgrade is being checked)
    tier: str = "paid"
    premium: bool = True
    upgrade_price: int = UPGRADE_PRICE


class ProofIn(BaseModel):
    message: str = Field(min_length=10, max_length=1000)


class UpgradeBusinessIn(BaseModel):
    business_name: str = Field(min_length=2, max_length=120)
    business_type: str = "shop"
    owner_username: str = Field(min_length=3, max_length=40, pattern=r"^[A-Za-z0-9_.-]+$")
    owner_password: str
    owner_full_name: str | None = Field(default=None, max_length=120)
    owner_email: str | None = None
    message: str | None = Field(default=None, max_length=1000)

    @field_validator("business_name")
    @classmethod
    def tidy_name(cls, v: str) -> str:
        return " ".join(v.split())

    @field_validator("business_type")
    @classmethod
    def type_ok(cls, v: str) -> str:
        v = v.strip().lower()
        return v if v in ("shop", "supermarket", "school", "gym", "other") else "shop"

    @field_validator("owner_password")
    @classmethod
    def password_ok(cls, v: str) -> str:
        return check_password(v)

    @field_validator("owner_email")
    @classmethod
    def email_ok(cls, v: str | None) -> str | None:
        return clean_email(v) or None


def _is_payer(user: User, institution: Institution) -> bool:
    return institution.type == "individual" or user.role == UserRole.INSTITUTION_ADMIN


@router.get("/status", response_model=BillingStatus)
def billing_status(db: Session = Depends(get_db), current_user: User = Depends(get_current_user)):
    result = _billing_status(db, current_user)
    result.individual = current_user.individual
    result.institution_type = current_user.institution_type
    inst = db.get(Institution, current_user.institution_id) if current_user.institution_id else None
    result.can_upgrade_business = bool(inst and inst.type == "individual" and not bill.is_free(inst) and current_user.role == UserRole.STAFF and current_user.staff_type == StaffType.GENERAL)
    return result


def _billing_status(db: Session, current_user: User) -> BillingStatus:
    """Where this account's workspace stands on its fee; staff of an owner see only whether the workspace is closed."""
    institution = db.get(Institution, current_user.institution_id) if current_user.institution_id else None
    preview = current_user.role == UserRole.INSTITUTION_ADMIN and current_user.username.lower() == bill.PREVIEW_USERNAME
    c = bill.cycle(db, institution)
    if bill.is_free(institution):
        latest = db.query(PaymentProof).filter(PaymentProof.institution_id == institution.id).order_by(PaymentProof.id.desc()).first()
        pending = latest is not None and latest.status == "pending"
        return BillingStatus(
            applies=False, state="free", tier="free", premium=bill.has_premium(db, institution), total=UPGRADE_PRICE, can_pay=True,
            proof_status="pending" if pending else ("rejected" if latest is not None and latest.status == "rejected" else "none"),
            proof_note=latest.review_note if latest is not None and latest.status == "rejected" else None, account_ref=current_user.username,
            agent_registered=institution.partner_id is not None, preview_allowed=preview,
            note="Your payment is waiting to be checked. The paid features are open meanwhile." if pending else "You are on the free plan. Upgrade to store documents and print statements.",
        )
    if not c.billed:
        return BillingStatus(applies=False, preview_allowed=preview)
    payer = _is_payer(current_user, institution)
    locked = c.state == "locked"
    if not payer:
        return BillingStatus(applies=False, state=c.state, locked=locked, preview_allowed=preview, note="Your owner needs to pay the monthly fee." if locked else "")
    remaining = c.trial_ends - bill.utcnow()
    days_left = max(0, -(-int(remaining.total_seconds()) // 86400)) if c.paid_months == 0 else 0
    note = {
        "trial": "Your free week is running. Nothing is due yet.",
        "paid": "Your fee is paid up.",
        "due": "Your fee is due. The app stays open until 6am tomorrow, then closes until it is paid.",
        "pending": "Your payment message is waiting to be checked. You can keep using the app.",
        "locked": "Your fee is overdue. Pay and paste the M-Pesa message to open the app again.",
    }.get(c.state, "")
    return BillingStatus(
        applies=True, plan=c.plan, plan_label=c.plan.capitalize(), state="trial_ended" if c.state in ("due", "pending", "locked") and c.paid_months == 0 else c.state,
        trial_ends_at=c.trial_ends, days_left=days_left, base_amount=c.base, included_accounts=OWNER_INCLUDED_ACCOUNTS if c.plan == "owner" else 0,
        accounts_used=c.used, extra_accounts=c.extra, extra_amount=c.extra * EXTRA_ACCOUNT_PRICE, total=c.total, note=note,
        due_date=c.due_date, popup_from=c.popup_from, lock_at=c.lock_at, popup_due=c.state == "due", locked=locked, can_pay=True,
        proof_status=c.proof_status, proof_note=c.proof_note, account_ref=current_user.username,
        agent_registered=institution.partner_id is not None, preview_allowed=preview,
    )


@router.post("/proof", status_code=status.HTTP_201_CREATED)
@limiter.limit("10/hour")
def submit_proof(payload: ProofIn, request: Request, db: Session = Depends(get_db), current_user: User = Depends(get_current_user)):
    """The paying account pastes its M-Pesa message; a readable one keeps the app open while a super admin confirms it."""
    institution = db.get(Institution, current_user.institution_id) if current_user.institution_id else None
    c = bill.cycle(db, institution)
    free = bill.is_free(institution)
    if (not c.billed and not free) or institution is None or not _is_payer(current_user, institution):
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Only the account that pays the fee can send a payment message")
    pending = db.query(PaymentProof.id).filter(PaymentProof.institution_id == institution.id, PaymentProof.status == "pending").first() is not None
    if pending:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="Your last payment message is still being checked")
    total = UPGRADE_PRICE if free else c.total
    try:
        found = bill.check_proof(payload.message, total)
    except ValueError as err:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(err))
    if db.query(PaymentProof.id).filter(PaymentProof.code == found["code"], PaymentProof.status != "rejected").first():
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="That M-Pesa message has already been used")
    proof = PaymentProof(institution_id=institution.id, submitted_by_id=current_user.id, message=" ".join(payload.message.split())[:1000], amount_due=total, purpose="upgrade" if free else "fee", **found)
    db.add(proof)
    db.commit()
    bill.forget(institution.id)
    log_activity(db, action="payment_proof_sent", actor=current_user, target_type="institution", target_id=institution.id, detail=f"Sent M-Pesa proof {found['code']}", request=request)
    return {"status": "pending", "message": "Thank you. Your upgrade is being checked and the paid features are open meanwhile." if free else "Thank you. Your payment is being checked and the app stays open."}


def _individual_account(db: Session, user: User) -> Institution:
    """The workspace of a paid sales-and-stock individual, the only kind that can become a business."""
    inst = db.get(Institution, user.institution_id) if user.institution_id else None
    if not inst or inst.type != "individual" or user.role != UserRole.STAFF or user.staff_type != StaffType.GENERAL:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Only a sales and stock individual account can become a business")
    if bill.is_free(inst):
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Upgrade to the paid plan first, then you can become a business")
    return inst


@router.get("/upgrade-quote")
def upgrade_quote(db: Session = Depends(get_db), current_user: User = Depends(get_current_user)):
    inst = _individual_account(db, current_user)
    return bill.upgrade_quote(db, inst)


@router.post("/upgrade-to-business", status_code=status.HTTP_201_CREATED)
@limiter.limit("10/hour")
def upgrade_to_business(payload: UpgradeBusinessIn, request: Request, db: Session = Depends(get_db), current_user: User = Depends(get_current_user)):
    """A paid individual who opened another branch becomes a business; nothing changes until the payment is confirmed (or at once during the free week)."""
    inst = _individual_account(db, current_user)
    quote = bill.upgrade_quote(db, inst)
    if quote["pending"]:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="Your last payment message is still being checked")
    details = {
        "business_name": payload.business_name, "business_type": payload.business_type, "owner_username": payload.owner_username,
        "owner_full_name": payload.owner_full_name or current_user.full_name, "owner_email": payload.owner_email,
        "owner_password_hash": hash_password(payload.owner_password),
    }
    if quote["due_now"] == 0:
        try:
            bill.convert_to_business(db, inst, details, restart_billing=False)
            db.commit()
        except bill.UpgradeConflict as err:
            db.rollback()
            raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail=str(err))
        log_activity(db, action="upgraded_to_business", actor=current_user, target_type="institution", target_id=inst.id, detail=f"Became business '{inst.name}'", request=request)
        return {"status": "converted", "message": "Your business is ready. Sign in with your new owner login to add branches. Your current login stays as your first branch."}
    if not payload.message:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=f"Pay KES {quote['due_now']:,} and paste the M-Pesa message to upgrade")
    from sqlalchemy import func as sa_func
    if db.query(Institution.id).filter(sa_func.lower(Institution.name) == payload.business_name.lower(), Institution.id != inst.id).first():
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="A business with this name already exists")
    if db.query(User.id).filter(sa_func.lower(User.username) == payload.owner_username.lower()).first():
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="That owner username is already taken")
    try:
        found = bill.check_proof(payload.message, quote["due_now"])
    except ValueError as err:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(err))
    if db.query(PaymentProof.id).filter(PaymentProof.code == found["code"], PaymentProof.status != "rejected").first():
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="That M-Pesa message has already been used")
    db.add(PaymentProof(institution_id=inst.id, submitted_by_id=current_user.id, message=" ".join(payload.message.split())[:1000], amount_due=quote["due_now"],
                        purpose="business", payload=json.dumps(details), **found))
    db.commit()
    bill.forget(inst.id)
    log_activity(db, action="business_upgrade_requested", actor=current_user, target_type="institution", target_id=inst.id, detail=f"Asked to become business '{payload.business_name}'", request=request)
    return {"status": "pending", "message": "Thank you. Your payment is being checked. When it is confirmed your business is set up and you can sign in with the new owner login."}
