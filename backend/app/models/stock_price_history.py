from sqlalchemy import Column, Integer, Numeric, DateTime, ForeignKey, func
from sqlalchemy.orm import relationship

from app.core.database import Base


class StockPriceHistory(Base):
    """
    One row per price snapshot for a stock item — written on item creation
    and on every unit_price change. Feeds the market-analysis price-trend
    charts; never edited or deleted after the fact.
    """
    __tablename__ = "stock_price_history"

    id = Column(Integer, primary_key=True, index=True)
    stock_item_id = Column(Integer, ForeignKey("stock_items.id", ondelete="CASCADE"), nullable=False, index=True)
    price = Column(Numeric(12, 2), nullable=False)
    recorded_at = Column(DateTime(timezone=True), server_default=func.now(), index=True)

    stock_item = relationship("StockItem", back_populates="price_history")
