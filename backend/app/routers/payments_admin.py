import json
from datetime import datetime, timezone
from decimal import Decimal

from fastapi import APIRouter, Depends, HTTPException, Request, status
from pydantic import BaseModel, Field
from sqlalchemy import func
from sqlalchemy.orm import Session

from app.core.activity_log import log_activity
from app.core.database import get_db
from app.core.deps import require_super_admin
from app.core.limiter import limiter
from app.models.billing import PartnerNotice, PayoutRequest, PaymentProof
from app.models.institution import Institution
from app.models.partner import FeePayment, Partner, PartnerPayout
from app.models.user import User
from app.utils import billing as bill
from app.utils.partner import CENT, commission_for, display_code, money

router = APIRouter(prefix="/api/admin", tags=["payments"])


class Approve(BaseModel):
    months: int | None = Field(default=None, ge=1, le=12)


class Reject(BaseModel):
    note: str | None = Field(default=None, max_length=200)


class PayRequest(BaseModel):
    reference: str | None = Field(default=None, max_length=80)


def _proof_row(db: Session, p: PaymentProof) -> dict:
    inst = db.get(Institution, p.institution_id)
    sender = db.get(User, p.submitted_by_id) if p.submitted_by_id else None
    partner = db.get(Partner, inst.partner_id) if inst and inst.partner_id else None
    agent = db.get(User, partner.user_id) if partner else None
    return {"id": p.id, "business": inst.name if inst else "Removed business", "sender": sender.full_name if sender else None, "sender_username": sender.username if sender else None,
            "message": p.message, "code": p.code, "amount": money(p.amount), "amount_due": money(p.amount_due), "paid_at": p.paid_at.isoformat() if p.paid_at else None,
            "recipient": p.recipient, "account_ref": p.account_ref, "status": p.status, "purpose": p.purpose, "note": p.review_note, "agent": agent.full_name if agent else None,
            "months": 1 if p.purpose == "upgrade" else (max(1, min(12, int(p.amount // p.amount_due))) if p.amount_due else 1), "created_at": p.created_at.isoformat() if p.created_at else None}


@router.get("/payment-proofs")
def list_proofs(state: str = "pending", db: Session = Depends(get_db), admin: User = Depends(require_super_admin)):
    q = db.query(PaymentProof)
    if state != "all":
        q = q.filter(PaymentProof.status == state)
    return [_proof_row(db, p) for p in q.order_by(PaymentProof.id.desc()).limit(100)]


@router.post("/payment-proofs/{proof_id}/approve")
@limiter.limit("60/hour")
def approve_proof(proof_id: int, payload: Approve, request: Request, db: Session = Depends(get_db), admin: User = Depends(require_super_admin)):
    """Confirms a payment against the till statement: books the fee, fixes the agent's share and tells the agent."""
    proof = db.get(PaymentProof, proof_id)
    if not proof or proof.status != "pending":
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="No waiting payment message with that id")
    inst = db.get(Institution, proof.institution_id)
    upgrading = proof.purpose == "upgrade"
    if proof.purpose == "business":
        # Becoming a business: the person's login stays as the first branch and a new owner login is created
        try:
            bill.convert_to_business(db, inst, json.loads(proof.payload or "{}"), restart_billing=True)
        except bill.UpgradeConflict as err:
            db.rollback()
            raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail=f"{err}. Decline this message and ask them to choose another.")
        start = bill.utcnow().astimezone(bill.LOCAL)
        months = 1
    # A free workspace that pays becomes a paid one, billed from today with no trial
    elif upgrading:
        inst.tier, inst.billing_start = "paid", bill.utcnow()
        start = bill.utcnow().astimezone(bill.LOCAL)
        months = 1
    else:
        c = bill.cycle(db, inst)
        months = payload.months or max(1, min(12, int(proof.amount // proof.amount_due)))
        start = bill.add_months(c.trial_ends.astimezone(bill.LOCAL), c.paid_months)
    each = (Decimal(proof.amount) / months).quantize(CENT)
    partner = db.get(Partner, inst.partner_id) if inst.partner_id else None
    today = datetime.now(bill.LOCAL).date()
    commission = Decimal("0.00")
    for i in range(months):
        share = commission_for(each, partner.commission_rate) if partner else Decimal("0.00")
        commission += share
        db.add(FeePayment(institution_id=inst.id, period=bill.add_months(start, i).strftime("%Y-%m"), amount=each, paid_on=today, partner_id=partner.id if partner else None,
                          commission_rate=partner.commission_rate if partner else None, commission_amount=share, note=f"M-Pesa {proof.code}"[:200], recorded_by_id=admin.id))
    proof.status, proof.reviewed_by_id, proof.reviewed_at = "approved", admin.id, datetime.now(timezone.utc)
    if partner and commission > 0:
        db.add(PartnerNotice(partner_id=partner.id, institution_name=inst.name, amount=commission,
                             text=f"{inst.name} {'became a business and paid' if proof.purpose == 'business' else 'upgraded and paid' if upgrading else 'paid'} KES {Decimal(proof.amount):,.2f}. You earned KES {commission:,.2f} and can ask for a payout of that amount."))
    db.commit()
    bill.forget(inst.id)
    log_activity(db, action="payment_proof_approved", actor=admin, target_type="institution", target_id=inst.id, detail=f"Approved M-Pesa {proof.code} for {months} month(s)", request=request)
    return _proof_row(db, proof)


@router.post("/payment-proofs/{proof_id}/reject")
@limiter.limit("60/hour")
def reject_proof(proof_id: int, payload: Reject, request: Request, db: Session = Depends(get_db), admin: User = Depends(require_super_admin)):
    proof = db.get(PaymentProof, proof_id)
    if not proof or proof.status != "pending":
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="No waiting payment message with that id")
    proof.status, proof.review_note = "rejected", (payload.note or "").strip() or "We could not find this payment"
    proof.reviewed_by_id, proof.reviewed_at = admin.id, datetime.now(timezone.utc)
    db.commit()
    bill.forget(proof.institution_id)
    log_activity(db, action="payment_proof_rejected", actor=admin, target_type="institution", target_id=proof.institution_id, detail=f"Rejected M-Pesa {proof.code}", request=request)
    return _proof_row(db, proof)


def _request_row(db: Session, r: PayoutRequest) -> dict:
    partner = db.get(Partner, r.partner_id)
    agent = db.get(User, partner.user_id) if partner else None
    earned = Decimal(str(db.query(func.coalesce(func.sum(FeePayment.commission_amount), 0)).filter(FeePayment.partner_id == r.partner_id, FeePayment.voided_at.is_(None)).scalar()))
    paid = Decimal(str(db.query(func.coalesce(func.sum(PartnerPayout.amount), 0)).filter(PartnerPayout.partner_id == r.partner_id, PartnerPayout.voided_at.is_(None)).scalar()))
    return {"id": r.id, "partner_id": r.partner_id, "agent": agent.full_name if agent else "Removed agent", "code": display_code(partner.code) if partner else "", "phone": partner.phone if partner else None,
            "amount": money(r.amount), "status": r.status, "balance_due": money(earned - paid), "requested_at": r.requested_at.isoformat() if r.requested_at else None}


@router.get("/payout-requests")
def list_requests(state: str = "pending", db: Session = Depends(get_db), admin: User = Depends(require_super_admin)):
    q = db.query(PayoutRequest)
    if state != "all":
        q = q.filter(PayoutRequest.status == state)
    return [_request_row(db, r) for r in q.order_by(PayoutRequest.id.desc()).limit(100)]


@router.post("/payout-requests/{request_id}/pay")
@limiter.limit("60/hour")
def pay_request(request_id: int, payload: PayRequest, request: Request, db: Session = Depends(get_db), admin: User = Depends(require_super_admin)):
    """Records the payout the agent asked for once the money has been sent."""
    req = db.get(PayoutRequest, request_id)
    if not req or req.status != "pending":
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="No waiting payout request with that id")
    row = _request_row(db, req)
    if Decimal(req.amount) > Decimal(row["balance_due"]):
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail=f"That is more than the balance due ({row['balance_due']})")
    payout = PartnerPayout(partner_id=req.partner_id, amount=Decimal(req.amount).quantize(CENT), paid_on=datetime.now(bill.LOCAL).date(), reference=(payload.reference or "").strip() or None, note="Requested by the agent", recorded_by_id=admin.id)
    db.add(payout)
    db.flush()
    req.status, req.handled_by_id, req.handled_at, req.payout_id = "paid", admin.id, datetime.now(timezone.utc), payout.id
    db.commit()
    log_activity(db, action="partner_payout_recorded", actor=admin, target_type="partner", target_id=req.partner_id, detail="Payout recorded from a request", request=request)
    return _request_row(db, req)


@router.post("/payout-requests/{request_id}/reject")
@limiter.limit("60/hour")
def reject_request(request_id: int, payload: Reject, request: Request, db: Session = Depends(get_db), admin: User = Depends(require_super_admin)):
    req = db.get(PayoutRequest, request_id)
    if not req or req.status != "pending":
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="No waiting payout request with that id")
    req.status, req.note, req.handled_by_id, req.handled_at = "rejected", (payload.note or "").strip() or None, admin.id, datetime.now(timezone.utc)
    db.commit()
    return _request_row(db, req)
