from datetime import timezone
from decimal import Decimal

from datetime import datetime

from fastapi import APIRouter, Depends, HTTPException, Query, status
from pydantic import BaseModel, Field
from sqlalchemy import func
from sqlalchemy.orm import Session

from app.core.database import get_db
from app.core.deps import get_current_user
from app.models.billing import PartnerNotice, PayoutRequest
from app.models.institution import Institution
from app.models.partner import FeePayment, Partner, PartnerPayout
from app.models.user import User, UserRole
from app.utils.partner import current_period, display_code, fee_status, money, shift_period, valid_period

router = APIRouter(prefix="/api/partner", tags=["partner"])


def _agent(current_user: User = Depends(get_current_user)) -> User:
    if current_user.role != UserRole.AGENT:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="This dashboard is for agents")
    return current_user


@router.get("/dashboard")
def dashboard(month: str | None = Query(default=None, description="YYYY-MM"), db: Session = Depends(get_db), user: User = Depends(_agent)):
    """An agent's own businesses and earnings; it exposes names, fee status and commission, never a business's records."""
    partner = db.query(Partner).filter(Partner.user_id == user.id).first()
    if not partner:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="No agent profile is set up for this account")
    now_period = current_period()
    chosen = month if month and valid_period(month) else now_period
    live = FeePayment.voided_at.is_(None)
    mine = (FeePayment.partner_id == partner.id) & live

    def total(column, *conds):
        return db.query(func.coalesce(func.sum(column), 0)).filter(mine, *conds).scalar()

    earned_total = Decimal(str(total(FeePayment.commission_amount)))
    earned_month = Decimal(str(total(FeePayment.commission_amount, FeePayment.period == chosen)))
    fees_month = Decimal(str(total(FeePayment.amount, FeePayment.period == chosen)))
    paid_out = Decimal(str(db.query(func.coalesce(func.sum(PartnerPayout.amount), 0)).filter(PartnerPayout.partner_id == partner.id, PartnerPayout.voided_at.is_(None)).scalar()))

    institutions = db.query(Institution).filter(Institution.partner_id == partner.id).order_by(Institution.created_at.desc()).all()
    ids = [i.id for i in institutions]
    owners = {}
    if ids:
        for o in db.query(User).filter(User.institution_id.in_(ids), User.role == UserRole.INSTITUTION_ADMIN).order_by(User.id):
            owners.setdefault(o.institution_id, o.full_name)
    per_business = {}
    last_period = {}
    if ids:
        for inst_id, earned, last in db.query(FeePayment.institution_id, func.coalesce(func.sum(FeePayment.commission_amount), 0), func.max(FeePayment.period)).filter(FeePayment.institution_id.in_(ids), mine).group_by(FeePayment.institution_id):
            per_business[inst_id] = Decimal(str(earned))
            last_period[inst_id] = last
        month_earned = {r[0]: Decimal(str(r[1])) for r in db.query(FeePayment.institution_id, func.coalesce(func.sum(FeePayment.commission_amount), 0)).filter(FeePayment.institution_id.in_(ids), mine, FeePayment.period == chosen).group_by(FeePayment.institution_id)}
    else:
        month_earned = {}
    businesses = []
    for i in institutions:
        status_value = fee_status(i.created_at, last_period.get(i.id), now_period)
        businesses.append({
            "name": i.name, "type": i.type, "region": i.region, "owner_name": owners.get(i.id),
            "registered_at": i.created_at.astimezone(timezone.utc).isoformat() if i.created_at else None,
            "monthly_fee": money(i.monthly_fee) if i.monthly_fee is not None else None, "status": status_value,
            "last_paid_period": last_period.get(i.id), "earned_month": money(month_earned.get(i.id, 0)), "earned_total": money(per_business.get(i.id, 0)),
        })
    names = {i.id: i.name for i in db.query(Institution).filter(Institution.id.in_({p.institution_id for p in db.query(FeePayment).filter(mine).order_by(FeePayment.id.desc()).limit(20)} or {0})).all()}
    recent = [{"business": names.get(p.institution_id, "Removed business"), "period": p.period, "paid_on": p.paid_on.isoformat(), "fee": money(p.amount), "rate": money(p.commission_rate), "commission": money(p.commission_amount)}
              for p in db.query(FeePayment).filter(mine).order_by(FeePayment.paid_on.desc(), FeePayment.id.desc()).limit(20)]
    payouts = [{"paid_on": p.paid_on.isoformat(), "amount": money(p.amount), "reference": p.reference}
               for p in db.query(PartnerPayout).filter(PartnerPayout.partner_id == partner.id, PartnerPayout.voided_at.is_(None)).order_by(PartnerPayout.paid_on.desc(), PartnerPayout.id.desc()).limit(20)]
    pending_requested = Decimal(str(db.query(func.coalesce(func.sum(PayoutRequest.amount), 0)).filter(PayoutRequest.partner_id == partner.id, PayoutRequest.status == "pending").scalar()))
    notices = [{"id": n.id, "text": n.text, "business": n.institution_name, "amount": money(n.amount), "created_at": n.created_at.isoformat() if n.created_at else None}
               for n in db.query(PartnerNotice).filter(PartnerNotice.partner_id == partner.id, PartnerNotice.read_at.is_(None)).order_by(PartnerNotice.id.desc()).limit(10)]
    requests = [{"id": r.id, "amount": money(r.amount), "status": r.status, "requested_at": r.requested_at.isoformat() if r.requested_at else None}
                for r in db.query(PayoutRequest).filter(PayoutRequest.partner_id == partner.id).order_by(PayoutRequest.id.desc()).limit(10)]
    series_periods = [shift_period(now_period, -n) for n in range(5, -1, -1)]
    by_month = {r[0]: Decimal(str(r[1])) for r in db.query(FeePayment.period, func.coalesce(func.sum(FeePayment.commission_amount), 0)).filter(mine, FeePayment.period.in_(series_periods)).group_by(FeePayment.period)}
    return {
        "profile": {"name": user.full_name, "code": display_code(partner.code), "commission_rate": money(partner.commission_rate), "active": partner.is_active},
        "month": chosen, "current_month": now_period,
        "totals": {"businesses": len(institutions), "paying_this_month": sum(1 for b in businesses if b["last_paid_period"] == chosen),
                   "fees_collected_month": money(fees_month), "earned_month": money(earned_month), "earned_total": money(earned_total),
                   "paid_out": money(paid_out), "balance_due": money(earned_total - paid_out),
                   "requested": money(pending_requested), "requestable": money(max(Decimal("0"), earned_total - paid_out - pending_requested))},
        "notices": notices, "payout_requests": requests,
        "businesses": businesses, "recent_commissions": recent, "payouts": payouts,
        "series": [{"period": p, "earned": money(by_month.get(p, 0))} for p in series_periods],
    }


class PayoutAsk(BaseModel):
    amount: Decimal | None = Field(default=None, gt=0, le=Decimal("100000000"))
    notice_id: int | None = None


def _requestable(db: Session, partner: Partner) -> Decimal:
    earned = Decimal(str(db.query(func.coalesce(func.sum(FeePayment.commission_amount), 0)).filter(FeePayment.partner_id == partner.id, FeePayment.voided_at.is_(None)).scalar()))
    paid = Decimal(str(db.query(func.coalesce(func.sum(PartnerPayout.amount), 0)).filter(PartnerPayout.partner_id == partner.id, PartnerPayout.voided_at.is_(None)).scalar()))
    waiting = Decimal(str(db.query(func.coalesce(func.sum(PayoutRequest.amount), 0)).filter(PayoutRequest.partner_id == partner.id, PayoutRequest.status == "pending").scalar()))
    return max(Decimal("0"), earned - paid - waiting)


@router.post("/payout-requests", status_code=status.HTTP_201_CREATED)
def ask_for_payout(payload: PayoutAsk, db: Session = Depends(get_db), user: User = Depends(_agent)):
    """An agent asks to be paid; with a notice id the amount is that payment's commission, with none it is everything not yet asked for."""
    partner = db.query(Partner).filter(Partner.user_id == user.id).first()
    if not partner:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="No agent profile is set up for this account")
    available = _requestable(db, partner)
    notice = db.get(PartnerNotice, payload.notice_id) if payload.notice_id else None
    if payload.notice_id and (not notice or notice.partner_id != partner.id):
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Notice not found")
    amount = payload.amount or (min(Decimal(notice.amount), available) if notice else available)
    amount = Decimal(amount).quantize(Decimal("0.01"))
    if amount <= 0 or amount > available:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail=f"You can ask for up to KES {available:,.2f} right now")
    request_row = PayoutRequest(partner_id=partner.id, amount=amount)
    db.add(request_row)
    if notice:
        notice.read_at = datetime.utcnow()
    db.commit()
    return {"id": request_row.id, "amount": money(amount), "status": "pending"}


@router.post("/notices/{notice_id}/read", status_code=status.HTTP_204_NO_CONTENT)
def mark_notice_read(notice_id: int, db: Session = Depends(get_db), user: User = Depends(_agent)):
    partner = db.query(Partner).filter(Partner.user_id == user.id).first()
    notice = db.get(PartnerNotice, notice_id)
    if partner and notice and notice.partner_id == partner.id and notice.read_at is None:
        notice.read_at = datetime.utcnow()
        db.commit()
