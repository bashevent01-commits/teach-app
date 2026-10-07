from sqlalchemy import Column, Integer, DateTime, ForeignKey, Text, func
from sqlalchemy.orm import relationship

from app.core.database import Base


class PostComment(Base):
    """A comment a member of the institution leaves under a news post."""
    __tablename__ = "post_comments"

    id = Column(Integer, primary_key=True, index=True)
    post_id = Column(Integer, ForeignKey("posts.id", ondelete="CASCADE"), nullable=False, index=True)
    author_id = Column(Integer, ForeignKey("users.id"), nullable=False)
    body = Column(Text, nullable=False)
    created_at = Column(DateTime(timezone=True), server_default=func.now())

    post = relationship("Post", back_populates="comments")
    author = relationship("User")

    @property
    def author_name(self) -> str | None:
        return self.author.full_name if self.author else None

    @property
    def author_avatar_path(self) -> str | None:
        return self.author.avatar_path if self.author else None
