from sqlalchemy import Column, Integer, String, DateTime, ForeignKey, func
from sqlalchemy.orm import relationship

from app.core.database import Base


class ActivityLog(Base):
    """
    Security/accountability trail — who did what, when, from where.
    Distinct from the school "Audit" model (financial audits teachers
    submit) — this is the app's own activity log.
    """
    __tablename__ = "activity_logs"

    id = Column(Integer, primary_key=True, index=True)

    actor_id = Column(Integer, ForeignKey("users.id"), nullable=True)
    actor_username = Column(String(80), nullable=True)  # kept even if the actor is later deleted

    action = Column(String(60), nullable=False, index=True)
    target_type = Column(String(40), nullable=True)
    target_id = Column(Integer, nullable=True)
    detail = Column(String(500), nullable=True)

    ip_address = Column(String(64), nullable=True)
    created_at = Column(DateTime(timezone=True), server_default=func.now(), index=True)

    actor = relationship("User", foreign_keys=[actor_id])
