from sqlalchemy import Column, ForeignKey, Integer, Numeric, String, DateTime, func
from sqlalchemy.orm import relationship

from app.core.database import Base


class Institution(Base):
    __tablename__ = "institutions"

    id = Column(Integer, primary_key=True, index=True)
    name = Column(String(200), nullable=False, unique=True)
    # Free text on purpose (school, shop, supermarket, ...) rather than a
    # fixed enum, so new institution types don't need a migration to add.
    type = Column(String(50), nullable=False)
    address = Column(String(300), nullable=True)
    # One of app.core.constants.KENYA_COUNTIES — nullable for existing rows
    # created before regional market-analysis breakdowns existed.
    region = Column(String(50), nullable=True)
    # The head office constituency; stock without a branch uses it
    constituency = Column(String(80), nullable=True)

    # Path (relative to UPLOAD_DIR's parent) to the institution's icon/logo.
    logo_path = Column(String(300), nullable=True)

    created_at = Column(DateTime(timezone=True), server_default=func.now())

    # The agent who registered this business (if any) and its agreed monthly fee
    partner_id = Column(Integer, ForeignKey("partners.id", ondelete="SET NULL"), nullable=True, index=True)
    monthly_fee = Column(Numeric(12, 2), nullable=True)
    # "free" for the free plan; empty or "paid" for a paying workspace
    tier = Column(String(10), nullable=True)
    # What an individual account runs: "shop" or "gym"; empty for everything else
    focus = Column(String(10), nullable=True)
    # Fee payments up to this id belong to an earlier plan and no longer count toward the current cycle
    fee_floor = Column(Integer, nullable=True)
    # When billing began for a workspace that upgraded from free; empty means the 7-day trial ran from sign-up
    billing_start = Column(DateTime(timezone=True), nullable=True)

    users = relationship("User", back_populates="institution", cascade="all, delete-orphan")
    transactions = relationship("Transaction", back_populates="institution", cascade="all, delete-orphan")
    audits = relationship("Audit", back_populates="institution", cascade="all, delete-orphan")
    posts = relationship("Post", back_populates="institution", cascade="all, delete-orphan")
    stock_items = relationship("StockItem", back_populates="institution", cascade="all, delete-orphan")

    @property
    def kind(self) -> str:
        """What the workspace does day to day: a gym if it is one or an individual chose gym, otherwise its type."""
        return "gym" if self.type == "gym" or self.focus == "gym" else self.type
