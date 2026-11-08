import re
from decimal import Decimal, InvalidOperation


# Every Safaricom M-Pesa confirmation SMS (Buy Goods, Paybill, or Send Money received) starts with a ~10-character
_CODE_RE = re.compile(r"\b([A-Z0-9]{8,12})\s+Confirmed\b", re.IGNORECASE)

# "Ksh" or "KES" followed by a number, first occurrence in the message — in every observed format the transaction amount
_AMOUNT_RE = re.compile(r"(?:Ksh|KES)\s*([\d,]+(?:\.\d{1,2})?)", re.IGNORECASE)

# Payer name sits between "from"/"received from" and the next run of digits (their phone number).
_PAYER_RE = re.compile(r"from\s+([A-Za-z][A-Za-z .'-]*?)(?=\s+\d{6,})", re.IGNORECASE)


def parse_mpesa_message(text: str) -> dict:
    """Best-effort extraction only."""
    code_match = _CODE_RE.search(text)
    if not code_match:
        return {"matched": False, "code": None, "amount": None, "payer_name": None}

    amount = None
    amount_match = _AMOUNT_RE.search(text)
    if amount_match:
        try:
            amount = Decimal(amount_match.group(1).replace(",", ""))
        except InvalidOperation:
            amount = None

    payer_name = None
    payer_match = _PAYER_RE.search(text)
    if payer_match:
        payer_name = payer_match.group(1).strip().title()

    return {
        "matched": True,
        "code": code_match.group(1).upper(),
        "amount": amount,
        "payer_name": payer_name,
    }
