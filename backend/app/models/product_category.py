from sqlalchemy import Column, Integer, String, DateTime, func
from sqlalchemy.orm import relationship

from app.core.database import Base


class ProductCategory(Base):
    """
    Shared, curated taxonomy stock items are matched into (e.g. "Pencils",
    "Bread (400g)") — super_admin managed only. Staff pick from this list
    rather than typing a free-text category, so cross-institution market
    analysis can group items accurately instead of relying on fuzzy
    text matching.
    """
    __tablename__ = "product_categories"

    id = Column(Integer, primary_key=True, index=True)
    name = Column(String(150), nullable=False, unique=True)
    created_at = Column(DateTime(timezone=True), server_default=func.now())

    stock_items = relationship("StockItem", back_populates="category")
