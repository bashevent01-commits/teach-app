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
    """
    Best-effort extraction from a pasted Safaricom M-Pesa confirmation
    SMS. Only amount, transaction code, and payer name are ever present
    in these messages — M-Pesa has no concept of items, so it can never
    tell you what was bought. matched=False means the text didn't look
    like an M-Pesa message at all (still returns 200 — this is an
    everyday "no result" case, not an error).
    """
    matched: bool
    code: str | None = None
    amount: Decimal | None = None
    payer_name: str | None = None
