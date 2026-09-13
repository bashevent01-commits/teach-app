import re
from decimal import Decimal, InvalidOperation


# Every Safaricom M-Pesa confirmation SMS (Buy Goods, Paybill, or Send
# Money received) starts with a ~10-character alphanumeric code followed
# by "Confirmed." — this is the one part of the format that's completely
# consistent across variants, so it's the most reliable signal that the
# pasted text is actually an M-Pesa message at all.
_CODE_RE = re.compile(r"\b([A-Z0-9]{8,12})\s+Confirmed\b", re.IGNORECASE)

# "Ksh" or "KES" followed by a number, first occurrence in the message —
# in every observed format the transaction amount always appears before
# any balance figure ("New M-PESA balance is Ksh...", "Utility balance
# is Ksh...").
_AMOUNT_RE = re.compile(r"(?:Ksh|KES)\s*([\d,]+(?:\.\d{1,2})?)", re.IGNORECASE)

# Payer name sits between "from"/"received from" and the next run of
# digits (their phone number). Stops at the first digit so it doesn't
# swallow the phone number itself.
_PAYER_RE = re.compile(r"from\s+([A-Za-z][A-Za-z .'-]*?)(?=\s+\d{6,})", re.IGNORECASE)


def parse_mpesa_message(text: str) -> dict:
    """
    Best-effort extraction only. M-Pesa messages never contain item or
    quantity information — only amount, transaction code, and payer name
    can ever come from this. Returns matched=False (not an error) when
    the text doesn't look like an M-Pesa confirmation at all.
    """
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
