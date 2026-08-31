from sqlalchemy import Column, Integer, String, DateTime, ForeignKey, Text, func
from sqlalchemy.orm import relationship

from app.core.database import Base


class Post(Base):
    """A news item a teacher posts to their school's portal."""
    __tablename__ = "posts"

    id = Column(Integer, primary_key=True, index=True)
    school_id = Column(Integer, ForeignKey("schools.id"), nullable=False, index=True)
    author_id = Column(Integer, ForeignKey("users.id"), nullable=False)

    title = Column(String(200), nullable=False)
    body = Column(Text, nullable=False)
    image_path = Column(String(255), nullable=True)  # optional photo, images only (no video)
    created_at = Column(DateTime(timezone=True), server_default=func.now())

    school = relationship("School", back_populates="posts")
    author = relationship("User", back_populates="posts")
    reports = relationship("PostReport", back_populates="post", cascade="all, delete-orphan")