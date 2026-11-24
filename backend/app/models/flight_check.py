from sqlalchemy import JSON, Column, Date, DateTime, ForeignKey, Integer, String, Text, func

from app.core.database import Base


class FlightCheck(Base):
    """One signed aircraft security check or search; the submitted content never changes and is hash-chained."""
    __tablename__ = "flight_checks"

    id = Column(Integer, primary_key=True, index=True)
    institution_id = Column(Integer, ForeignKey("institutions.id", ondelete="CASCADE"), nullable=False, index=True)
    # The station officer account that submitted it
    branch_id = Column(Integer, ForeignKey("users.id", ondelete="SET NULL"), nullable=True, index=True)
    tail_number = Column(String(12), nullable=False, index=True)
    flight_no = Column(String(12), nullable=True)
    station = Column(String(20), nullable=False, index=True)
    # "check" is a cabin and hold check, "search" is a full interior and exterior search
    check_type = Column(String(10), nullable=False, default="check")
    items = Column(JSON, nullable=False)
    result = Column(String(10), nullable=False, default="clear")
    remarks = Column(String(300), nullable=True)
    signed_by_id = Column(Integer, ForeignKey("users.id", ondelete="SET NULL"), nullable=True)
    signed_name = Column(String(150), nullable=False)
    signature = Column(Text, nullable=False)
    # ISO time in UTC with seconds; kept as text so the hash never depends on a database round trip
    submitted_at = Column(String(32), nullable=False)
    # When the officer did it by the device clock; differs from submitted_at only for checks saved offline first
    performed_at = Column(String(32), nullable=True)
    check_date = Column(Date, nullable=False, index=True)
    prev_hash = Column(String(64), nullable=False, default="")
    record_hash = Column(String(64), nullable=False, default="")

    # Review is kept apart from the signed content so approving never alters what was signed
    status = Column(String(12), nullable=False, default="pending", index=True)
    reviewed_by_id = Column(Integer, ForeignKey("users.id", ondelete="SET NULL"), nullable=True)
    reviewed_name = Column(String(150), nullable=True)
    reviewed_at = Column(DateTime(timezone=True), nullable=True)
    review_note = Column(String(300), nullable=True)
    created_at = Column(DateTime(timezone=True), server_default=func.now())
