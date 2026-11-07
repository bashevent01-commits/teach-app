from sqlalchemy import Column, DateTime, ForeignKey, Integer, Numeric, String, Text, func

from app.core.database import Base


class PaymentProof(Base):
    """The M-Pesa message a business pasted to show it paid; a super admin checks it against the paybill statement."""
    __tablename__ = "payment_proofs"

    id = Column(Integer, primary_key=True, index=True)
    institution_id = Column(Integer, ForeignKey("institutions.id", ondelete="CASCADE"), nullable=False, index=True)
    submitted_by_id = Column(Integer, ForeignKey("users.id", ondelete="SET NULL"), nullable=True)
    message = Column(Text, nullable=False)
    code = Column(String(20), nullable=False, index=True)
    amount = Column(Numeric(12, 2), nullable=False)
    amount_due = Column(Numeric(12, 2), nullable=False)
    paid_at = Column(DateTime(timezone=True), nullable=True)
    recipient = Column(String(120), nullable=True)
    account_ref = Column(String(80), nullable=True)
    # "fee" for the monthly fee, "upgrade" for a free workspace moving to the paid plan
    purpose = Column(String(10), nullable=False, default="fee", server_default="fee")
    status = Column(String(12), nullable=False, default="pending", index=True)
    review_note = Column(String(200), nullable=True)
    # Details of a business upgrade, kept until the payment is checked (the password is already hashed)
    payload = Column(Text, nullable=True)
    reviewed_by_id = Column(Integer, ForeignKey("users.id", ondelete="SET NULL"), nullable=True)
    reviewed_at = Column(DateTime(timezone=True), nullable=True)
    created_at = Column(DateTime(timezone=True), server_default=func.now())


class PartnerNotice(Base):
    """A message for an agent, such as a business of theirs having paid."""
    __tablename__ = "partner_notices"

    id = Column(Integer, primary_key=True, index=True)
    partner_id = Column(Integer, ForeignKey("partners.id", ondelete="CASCADE"), nullable=False, index=True)
    institution_name = Column(String(200), nullable=True)
    text = Column(String(300), nullable=False)
    # The commission this notice is about, so one tap can ask for exactly that payout
    amount = Column(Numeric(12, 2), nullable=False, default=0)
    created_at = Column(DateTime(timezone=True), server_default=func.now())
    read_at = Column(DateTime(timezone=True), nullable=True)


class PayoutRequest(Base):
    """An agent asking to be paid some of what they have earned."""
    __tablename__ = "payout_requests"

    id = Column(Integer, primary_key=True, index=True)
    partner_id = Column(Integer, ForeignKey("partners.id", ondelete="CASCADE"), nullable=False, index=True)
    amount = Column(Numeric(12, 2), nullable=False)
    status = Column(String(12), nullable=False, default="pending", index=True)
    note = Column(String(200), nullable=True)
    requested_at = Column(DateTime(timezone=True), server_default=func.now())
    handled_by_id = Column(Integer, ForeignKey("users.id", ondelete="SET NULL"), nullable=True)
    handled_at = Column(DateTime(timezone=True), nullable=True)
    payout_id = Column(Integer, ForeignKey("partner_payouts.id", ondelete="SET NULL"), nullable=True)
