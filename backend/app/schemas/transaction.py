from datetime import datetime
from decimal import Decimal

from pydantic import BaseModel, ConfigDict

from app.models.transaction import TransactionType, TransactionMethod, TransactionCategoryType


class TransactionOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    institution_id: int
    type: TransactionType
    method: TransactionMethod
    category_type: TransactionCategoryType
    category: str
    description: str | None
    amount: Decimal
    stock_item_id: int | None
    quantity: Decimal | None
    mpesa_code: str | None
    mpesa_payer_name: str | None
    transaction_date: datetime
    image_path: str | None
    recorded_by_id: int
    created_at: datetime
