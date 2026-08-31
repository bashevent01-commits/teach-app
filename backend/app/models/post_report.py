import enum

from sqlalchemy import Column, Integer, String, DateTime, ForeignKey, Enum, Text, func
from sqlalchemy.orm import relationship

from app.core.database import Base


class ReportStatus(str, enum.Enum):
    OPEN = "open"          # awaiting super admin review
    RESOLVED = "resolved"  # reviewed — dismissed or acted on


class PostReport(Base):
    """A teacher's report against a news post, queued for super admin review."""
    __tablename__ = "post_reports"

    id = Column(Integer, primary_key=True, index=True)
    post_id = Column(Integer, ForeignKey("posts.id"), nullable=False, index=True)
    reporter_id = Column(Integer, ForeignKey("users.id"), nullable=False)

    reason = Column(Text, nullable=True)
    status = Column(Enum(ReportStatus), nullable=False, default=ReportStatus.OPEN, index=True)

    created_at = Column(DateTime(timezone=True), server_default=func.now())
    resolved_at = Column(DateTime(timezone=True), nullable=True)
    resolved_by_id = Column(Integer, ForeignKey("users.id"), nullable=True)
    # What the super admin did about it: "dismissed" or "post_removed".
    resolution = Column(String(30), nullable=True)

    post = relationship("Post", back_populates="reports")
    reporter = relationship("User", foreign_keys=[reporter_id])
    resolved_by = relationship("User", foreign_keys=[resolved_by_id])