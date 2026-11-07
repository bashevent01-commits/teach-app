from datetime import date
from decimal import Decimal

from fastapi import APIRouter, Depends, HTTPException, Request, status
from pydantic import BaseModel, Field, field_validator
from sqlalchemy import func, or_
from sqlalchemy.orm import Session

from app.core.activity_log import log_activity
from app.core.database import get_db
from app.core.deps import require_institution_scope
from app.models.institution import Institution
from app.models.member import Member
from app.models.transaction import Transaction
from app.models.user import StaffType, User, UserRole
from app.utils.members import PERIODS, status_of, today_local

router = APIRouter(prefix="/api/members", tags=["members"])


class MemberIn(BaseModel):
    name: str = Field(min_length=2, max_length=120)
    phone: str | None = Field(default=None, max_length=40)
    plan: str | None = Field(default=None, max_length=60)
    fee: Decimal = Field(gt=0, le=Decimal("100000000"))
    period: str = "monthly"
    next_due: date | None = None
    notes: str | None = Field(default=None, max_length=200)
    branch_id: int | None = None

    @field_validator("name", "plan", "phone", "notes")
    @classmethod
    def tidy(cls, v: str | None) -> str | None:
        return " ".join(v.split()) or None if v is not None else None

    @field_validator("period")
    @classmethod
    def period_ok(cls, v: str) -> str:
        if v not in PERIODS:
            raise ValueError("period must be weekly, monthly, quarterly or yearly")
        return v


class MemberUpdate(BaseModel):
    name: str | None = Field(default=None, min_length=2, max_length=120)
    phone: str | None = Field(default=None, max_length=40)
    plan: str | None = Field(default=None, max_length=60)
    fee: Decimal | None = Field(default=None, gt=0, le=Decimal("100000000"))
    period: str | None = None
    next_due: date | None = None
    notes: str | None = Field(default=None, max_length=200)
    is_active: bool | None = None

    @field_validator("period")
    @classmethod
    def period_ok(cls, v: str | None) -> str | None:
        if v is not None and v not in PERIODS:
            raise ValueError("period must be weekly, monthly, quarterly or yearly")
        return v


def _out(m: Member, today: date) -> dict:
    state, days = status_of(m.is_active, m.next_due, today)
    return {"id": m.id, "name": m.name, "phone": m.phone, "plan": m.plan, "fee": str(m.fee.quantize(Decimal("0.01"))), "period": m.period,
            "next_due": m.next_due.isoformat(), "last_paid_on": m.last_paid_on.isoformat() if m.last_paid_on else None, "is_active": m.is_active,
            "notes": m.notes, "branch_id": m.branch_id, "status": state, "days_to_due": days}


def _gym(db: Session, user: User) -> Institution:
    if user.role == UserRole.STAFF and user.staff_type == StaffType.TEACHER:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Personal accounts don't have members")
    inst = db.get(Institution, user.institution_id) if user.institution_id else None
    if not inst or inst.kind != "gym":
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Members are only for gym workspaces")
    return inst


def _visible(db: Session, user: User):
    q = db.query(Member).filter(Member.institution_id == user.institution_id)
    if user.role == UserRole.STAFF:
        q = q.filter(or_(Member.branch_id.is_(None), Member.branch_id == user.id))
    return q


def member_for(db: Session, user: User, member_id: int) -> Member:
    """A member this account may see, locked for a payment; used by the transaction endpoint too."""
    _gym(db, user)
    member = _visible(db, user).filter(Member.id == member_id).with_for_update().first()
    if not member:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Member not found")
    return member


@router.get("")
def list_members(state: str = "all", q: str | None = None, db: Session = Depends(get_db), user: User = Depends(require_institution_scope)):
    _gym(db, user)
    today = today_local()
    query = _visible(db, user)
    if q:
        query = query.filter(or_(Member.name.ilike(f"%{q.strip()}%"), Member.phone.ilike(f"%{q.strip()}%")))
    rows = [_out(m, today) for m in query.order_by(Member.next_due, Member.name).all()]
    if state != "all":
        rows = [r for r in rows if r["status"] == state]
    return rows


@router.get("/due")
def due_members(db: Session = Depends(get_db), user: User = Depends(require_institution_scope)):
    """Who is behind, who pays today and who pays tomorrow; the day-before and same-day reminders read this."""
    _gym(db, user)
    today = today_local()
    rows = [_out(m, today) for m in _visible(db, user).filter(Member.is_active.is_(True)).order_by(Member.next_due, Member.name).all()]
    pick = lambda s: [r for r in rows if r["status"] == s]
    return {"date": today.isoformat(), "overdue": pick("overdue"), "today": pick("due_today"), "tomorrow": pick("due_tomorrow")}


@router.post("", status_code=status.HTTP_201_CREATED)
def add_member(payload: MemberIn, request: Request, db: Session = Depends(get_db), user: User = Depends(require_institution_scope)):
    _gym(db, user)
    branch_id = user.id if user.role == UserRole.STAFF else None
    if user.role == UserRole.INSTITUTION_ADMIN and payload.branch_id is not None:
        branch = db.query(User).filter(User.id == payload.branch_id, User.institution_id == user.institution_id, User.role == UserRole.STAFF).first()
        if not branch:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Branch not found")
        branch_id = branch.id
    member = Member(institution_id=user.institution_id, branch_id=branch_id, name=payload.name, phone=payload.phone, plan=payload.plan, fee=payload.fee,
                    period=payload.period, next_due=payload.next_due or today_local(), notes=payload.notes)
    db.add(member)
    db.commit()
    db.refresh(member)
    log_activity(db, action="member_added", actor=user, target_type="member", target_id=member.id, detail=f"Added member {member.name}", request=request)
    return _out(member, today_local())


@router.patch("/{member_id}")
def update_member(member_id: int, payload: MemberUpdate, request: Request, db: Session = Depends(get_db), user: User = Depends(require_institution_scope)):
    member = member_for(db, user, member_id)
    for field, value in payload.model_dump(exclude_unset=True).items():
        if value is None and field in ("name", "fee", "period", "next_due", "is_active"):
            continue
        setattr(member, field, " ".join(value.split()) or None if isinstance(value, str) else value)
    db.commit()
    db.refresh(member)
    return _out(member, today_local())


@router.delete("/{member_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_member(member_id: int, request: Request, db: Session = Depends(get_db), user: User = Depends(require_institution_scope)):
    """A member with payments on record is paused instead, so the books keep their history."""
    member = member_for(db, user, member_id)
    if db.query(func.count(Transaction.id)).filter(Transaction.member_id == member.id).scalar():
        member.is_active = False
    else:
        db.delete(member)
    db.commit()
