from datetime import date, datetime
from decimal import Decimal

from pydantic import BaseModel, ConfigDict


class SourceDocumentOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    doc_type: str
    doc_type_name: str
    reference_no: str | None = None
    document_date: date
    party_name: str | None = None
    stock_item_id: int | None = None
    stock_item_name: str | None = None
    quantity: Decimal | None = None
    amount: Decimal | None = None
    notes: str | None = None
    image_path: str | None = None
    transaction_id: int | None = None
    recorded_by_id: int
    recorded_by_name: str | None = None
    created_at: datetime
