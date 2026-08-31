from sqlalchemy import Column, Integer, String, DateTime, func
from sqlalchemy.orm import relationship

from app.core.database import Base


class School(Base):
    __tablename__ = "schools"

    id = Column(Integer, primary_key=True, index=True)
    name = Column(String(200), nullable=False, unique=True)
    address = Column(String(300), nullable=True)

    # Path (relative to UPLOAD_DIR's parent) to the school's icon/logo file.
    # This is what makes the portal UI and generated PDF reports show a
    # different icon per school.
    logo_path = Column(String(300), nullable=True)

    created_at = Column(DateTime(timezone=True), server_default=func.now())

    users = relationship("User", back_populates="school", cascade="all, delete-orphan")
    transactions = relationship("Transaction", back_populates="school", cascade="all, delete-orphan")
    audits = relationship("Audit", back_populates="school", cascade="all, delete-orphan")
    posts = relationship("Post", back_populates="school", cascade="all, delete-orphan")
