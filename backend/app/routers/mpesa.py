from fastapi import APIRouter, Depends, Request
from sqlalchemy.orm import Session

from app.core.database import get_db
from app.core.deps import require_institution_scope
from app.core.limiter import limiter
from app.models.user import User
from app.schemas.mpesa import MpesaParseRequest, MpesaParseResult
from app.utils.mpesa import parse_mpesa_message

router = APIRouter(prefix="/api/mpesa", tags=["mpesa"])


@router.post("/parse", response_model=MpesaParseResult)
@limiter.limit("30/minute")
def parse_message(payload: MpesaParseRequest, request: Request, db: Session = Depends(get_db), current_user: User = Depends(require_institution_scope)):
    """
    Stateless — nothing is persisted here. The staff member pastes an
    M-Pesa confirmation SMS; this extracts amount/code/payer name to
    prefill the transaction form. Item and quantity are never in the
    message itself and are always picked manually afterward.
    """
    return parse_mpesa_message(payload.message)
