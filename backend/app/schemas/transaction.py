from datetime import datetime
from decimal import Decimal

from pydantic import BaseModel, ConfigDict

from app.models.transaction import TransactionType, TransactionMethod


class TransactionOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    school_id: int
    type: TransactionType
    method: TransactionMethod
    category: str
    description: str | None
    amount: Decimal
    transaction_date: datetime
    image_path: str | None
    recorded_by_id: int
    created_at: datetime