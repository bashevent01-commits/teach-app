import enum

from sqlalchemy import Column, Integer, String, DateTime, ForeignKey, Enum, Text, func
from sqlalchemy.orm import relationship

from app.core.database import Base


class AuditStatus(str, enum.Enum):
    DRAFT = "draft"
    FINALIZED = "finalized"


class Audit(Base):
    """
    A financial audit entered by a teacher: a titled period (e.g. "Term 1
    2026 Financial Audit") with a summary and a set of transactions
    (statements) attached. Reports are generated from a finalized audit.
    """
    __tablename__ = "audits"

    id = Column(Integer, primary_key=True, index=True)
    school_id = Column(Integer, ForeignKey("schools.id"), nullable=False, index=True)

    title = Column(String(200), nullable=False)
    period_start = Column(DateTime(timezone=True), nullable=False)
    period_end = Column(DateTime(timezone=True), nullable=False)
    summary = Column(Text, nullable=True)   # auditor's notes / details section of the report
    status = Column(Enum(AuditStatus), nullable=False, default=AuditStatus.DRAFT)

    submitted_by_id = Column(Integer, ForeignKey("users.id"), nullable=False)
    created_at = Column(DateTime(timezone=True), server_default=func.now())
    finalized_at = Column(DateTime(timezone=True), nullable=True)

    school = relationship("School", back_populates="audits")
    submitted_by = relationship("User")
    # Transactions are no longer individually attached to an audit — a
    # finalized audit's "statements" are whatever transactions fall inside
    # its period_start/period_end for its school (see routers/audits.py
    # and routers/reports.py), computed on read rather than stored.