import enum

from sqlalchemy import Column, Integer, String, DateTime, ForeignKey, Enum, Boolean, func
from sqlalchemy.orm import relationship

from app.core.database import Base


class UserRole(str, enum.Enum):
    SUPER_ADMIN = "super_admin"   # creates schools + accounts, not tied to one school
    TEACHER = "teacher"           # enters audits, views transactions, posts news


class User(Base):
    __tablename__ = "users"

    id = Column(Integer, primary_key=True, index=True)
    username = Column(String(80), nullable=False, unique=True, index=True)
    full_name = Column(String(150), nullable=False)
    hashed_password = Column(String(255), nullable=False)
    role = Column(Enum(UserRole), nullable=False, default=UserRole.TEACHER)
    is_active = Column(Boolean, nullable=False, default=True)

    # Brute-force protection: incremented on each wrong password, reset on
    # success. locked_until blocks login attempts entirely while in the future.
    failed_login_attempts = Column(Integer, nullable=False, default=0)
    locked_until = Column(DateTime(timezone=True), nullable=True)

    # Bumped on password reset / deactivation so previously-issued JWTs for
    # this user stop being accepted immediately, without needing a token
    # blocklist. Embedded in the JWT as "tv" and checked on every request.
    token_version = Column(Integer, nullable=False, default=0)

    # Nullable because a super_admin is not scoped to a single school.
    school_id = Column(Integer, ForeignKey("schools.id"), nullable=True)

    created_at = Column(DateTime(timezone=True), server_default=func.now())
    # Track who issued the credentials, for accountability.
    created_by_id = Column(Integer, ForeignKey("users.id"), nullable=True)

    school = relationship("School", back_populates="users")
    transactions_recorded = relationship("Transaction", back_populates="recorded_by", foreign_keys="Transaction.recorded_by_id")
    posts = relationship("Post", back_populates="author")
