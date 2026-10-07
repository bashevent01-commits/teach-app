from sqlalchemy import Column, Integer, String, Date, DateTime, ForeignKey, Numeric, Text, func
from sqlalchemy.orm import relationship

from app.core.database import Base
from app.core.document_types import DOCUMENT_NAMES


class SourceDocument(Base):
    """Evidence for stock activity: an invoice, delivery note, count sheet and so on, with a photo attached."""
    __tablename__ = "source_documents"

    id = Column(Integer, primary_key=True, index=True)
    institution_id = Column(Integer, ForeignKey("institutions.id"), nullable=False, index=True)
    recorded_by_id = Column(Integer, ForeignKey("users.id"), nullable=False, index=True)
    doc_type = Column(String(40), nullable=False, index=True)
    reference_no = Column(String(80), nullable=True)
    document_date = Column(Date, nullable=False)
    party_name = Column(String(150), nullable=True)
    stock_item_id = Column(Integer, ForeignKey("stock_items.id", ondelete="SET NULL"), nullable=True)
    quantity = Column(Numeric(12, 3), nullable=True)
    amount = Column(Numeric(12, 2), nullable=True)
    notes = Column(Text, nullable=True)
    image_path = Column(String(500), nullable=True)
    transaction_id = Column(Integer, ForeignKey("transactions.id", ondelete="SET NULL"), nullable=True)
    created_at = Column(DateTime(timezone=True), server_default=func.now())

    stock_item = relationship("StockItem")
    recorded_by = relationship("User")

    @property
    def doc_type_name(self) -> str:
        return DOCUMENT_NAMES.get(self.doc_type, self.doc_type)

    @property
    def stock_item_name(self) -> str | None:
        return self.stock_item.name if self.stock_item else None

    @property
    def recorded_by_name(self) -> str | None:
        return self.recorded_by.full_name if self.recorded_by else None
