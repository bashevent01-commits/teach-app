from sqlalchemy import Boolean, Column, Date, DateTime, ForeignKey, Integer, String, func

from app.core.database import Base


class Certificate(Base):
    """A security certificate, clearance or programme document that expires and must be renewed."""
    __tablename__ = "certificates"

    id = Column(Integer, primary_key=True, index=True)
    institution_id = Column(Integer, ForeignKey("institutions.id", ondelete="CASCADE"), nullable=False, index=True)
    # The station account that looks after this record; empty means HQ / any station
    branch_id = Column(Integer, ForeignKey("users.id", ondelete="SET NULL"), nullable=True, index=True)
    category = Column(String(30), nullable=False, default="other")
    title = Column(String(160), nullable=False)
    holder = Column(String(160), nullable=False)
    reference_no = Column(String(80), nullable=True)
    issuer = Column(String(120), nullable=True)
    station = Column(String(60), nullable=True)
    issued_on = Column(Date, nullable=True)
    # Empty means the document does not expire
    expires_on = Column(Date, nullable=True, index=True)
    notes = Column(String(300), nullable=True)
    # Records are withdrawn, never deleted, so the history stays complete
    is_active = Column(Boolean, nullable=False, default=True)
    created_by_id = Column(Integer, ForeignKey("users.id", ondelete="SET NULL"), nullable=True)
    created_at = Column(DateTime(timezone=True), server_default=func.now())


class CertificateImage(Base):
    """An optional photo or scan kept with a certificate; a certificate can have several."""
    __tablename__ = "certificate_images"

    id = Column(Integer, primary_key=True, index=True)
    certificate_id = Column(Integer, ForeignKey("certificates.id", ondelete="CASCADE"), nullable=False, index=True)
    institution_id = Column(Integer, ForeignKey("institutions.id", ondelete="CASCADE"), nullable=False, index=True)
    image_path = Column(String(500), nullable=False)
    uploaded_by_id = Column(Integer, ForeignKey("users.id", ondelete="SET NULL"), nullable=True)
    created_at = Column(DateTime(timezone=True), server_default=func.now())
