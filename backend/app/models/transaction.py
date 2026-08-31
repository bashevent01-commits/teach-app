import enum

from sqlalchemy import Column, Integer, String, DateTime, ForeignKey, Enum, Numeric, Text, func
from sqlalchemy.orm import relationship

from app.core.database import Base


class TransactionType(str, enum.Enum):
    INCOME = "income"
    EXPENSE = "expense"


class TransactionMethod(str, enum.Enum):
    CASH = "cash"
    MPESA = "mpesa"
    BANK = "bank"


class Transaction(Base):
    __tablename__ = "transactions"

    id = Column(Integer, primary_key=True, index=True)
    school_id = Column(Integer, ForeignKey("schools.id"), nullable=False, index=True)

    # `type` is the original column — it already stores "INCOME"/"EXPENSE"
    # (the enum member NAME, SQLAlchemy's default) from before this
    # feature existed, so it stays on that default behavior.
    type = Column(Enum(TransactionType), nullable=False)

    # `method` is a new column whose data was written as lowercase text
    # ("cash"/"mpesa"/"bank") by the migration, so — unlike `type` — it
    # needs values_callable to map by .value instead of .name.
    method = Column(Enum(TransactionMethod, values_callable=lambda obj: [e.value for e in obj]), nullable=False, default=TransactionMethod.CASH)

    category = Column(String(100), nullable=False)   # e.g. "tuition fees", "utilities"
    description = Column(Text, nullable=True)
    amount = Column(Numeric(12, 2), nullable=False)

    # Always set server-side at creation time — never accepted from the client.
    transaction_date = Column(DateTime(timezone=True), nullable=False, server_default=func.now())

    # Optional evidence photo (receipt, till slip, etc.)
    image_path = Column(String(500), nullable=True)

    recorded_by_id = Column(Integer, ForeignKey("users.id"), nullable=False)
    created_at = Column(DateTime(timezone=True), server_default=func.now())

    school = relationship("School", back_populates="transactions")
    recorded_by = relationship("User", back_populates="transactions_recorded", foreign_keys=[recorded_by_id])