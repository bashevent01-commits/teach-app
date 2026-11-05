from sqlalchemy import Boolean, Column, Date, DateTime, ForeignKey, Integer, Numeric, String, func

from app.core.database import Base


class Member(Base):
    """A gym member who pays a subscription fee on a repeating schedule."""
    __tablename__ = "members"

    id = Column(Integer, primary_key=True, index=True)
    institution_id = Column(Integer, ForeignKey("institutions.id", ondelete="CASCADE"), nullable=False, index=True)
    # The branch (staff account) that looks after this member; empty means any branch
    branch_id = Column(Integer, ForeignKey("users.id", ondelete="SET NULL"), nullable=True, index=True)
    name = Column(String(120), nullable=False)
    phone = Column(String(40), nullable=True)
    plan = Column(String(60), nullable=True)
    fee = Column(Numeric(12, 2), nullable=False)
    # weekly, monthly, quarterly or yearly
    period = Column(String(10), nullable=False, default="monthly")
    next_due = Column(Date, nullable=False)
    last_paid_on = Column(Date, nullable=True)
    is_active = Column(Boolean, nullable=False, default=True)
    notes = Column(String(200), nullable=True)
    created_at = Column(DateTime(timezone=True), server_default=func.now())
