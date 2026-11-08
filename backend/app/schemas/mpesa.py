from decimal import Decimal

from pydantic import BaseModel, field_validator


class MpesaParseRequest(BaseModel):
    message: str

    @field_validator("message")
    @classmethod
    def not_empty(cls, v: str) -> str:
        v = v.strip()
        if not v:
            raise ValueError("message is required")
        return v


class MpesaParseResult(BaseModel):
    """Best-effort extraction from a pasted Safaricom M-Pesa confirmation SMS."""
    matched: bool
    code: str | None = None
    amount: Decimal | None = None
    payer_name: str | None = None
