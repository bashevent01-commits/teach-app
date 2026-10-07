from sqlalchemy import Column, Integer, String, DateTime, ForeignKey, Text, func
from sqlalchemy.orm import relationship

from app.core.database import Base


class Post(Base):
    """A news item a staff member posts to their institution's portal."""
    __tablename__ = "posts"

    id = Column(Integer, primary_key=True, index=True)
    institution_id = Column(Integer, ForeignKey("institutions.id"), nullable=False, index=True)
    author_id = Column(Integer, ForeignKey("users.id"), nullable=False)

    title = Column(String(200), nullable=False)
    body = Column(Text, nullable=False)
    image_path = Column(String(255), nullable=True)  # optional photo, images only (no video)
    created_at = Column(DateTime(timezone=True), server_default=func.now())

    institution = relationship("Institution", back_populates="posts")
    author = relationship("User", back_populates="posts")
    reports = relationship("PostReport", back_populates="post", cascade="all, delete-orphan")
    comments = relationship("PostComment", back_populates="post", cascade="all, delete-orphan", order_by="PostComment.created_at")

    @property
    def author_name(self) -> str | None:
        return self.author.full_name if self.author else None

    @property
    def comment_count(self) -> int:
        return len(self.comments)

    @property
    def author_avatar_path(self) -> str | None:
        return self.author.avatar_path if self.author else None

    @property
    def author_bio(self) -> str | None:
        return self.author.bio if self.author else None

    @property
    def author_institution(self) -> str | None:
        return self.author.institution.name if self.author and self.author.institution else None
