from datetime import datetime, timezone
from decimal import Decimal

from fastapi import APIRouter, Depends, File, Form, HTTPException, Request, UploadFile, status
from sqlalchemy.orm import Session

from app.core.activity_log import log_activity
from app.core.database import get_db
from app.core.deps import get_current_user, require_school_scope
from app.models.audit import Audit, AuditStatus
from app.models.transaction import Transaction, TransactionType, TransactionMethod
from app.models.user import User, UserRole
from app.schemas.transaction import TransactionOut
from app.utils.uploads import save_transaction_image

router = APIRouter(prefix="/api/transactions", tags=["transactions"])


def _scoped_school_id(current_user: User, requested_school_id: int | None) -> int:
    """
    Teachers are locked to their own school. Super admins may specify
    school_id explicitly (e.g. via ?school_id=) since they aren't tied
    to one school.
    """
    if current_user.role == UserRole.TEACHER:
        return current_user.school_id
    if requested_school_id is None:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="school_id is required")
    return requested_school_id


@router.post("", response_model=TransactionOut, status_code=status.HTTP_201_CREATED)
def create_transaction(
    type: TransactionType = Form(...),
    method: TransactionMethod = Form(...),
    category: str = Form(...),
    description: str | None = Form(None),
    amount: Decimal = Form(...),
    image: UploadFile | None = File(None),
    db: Session = Depends(get_db),
    current_user: User = Depends(require_school_scope),
):
    """
    Records a Receiving (income) or Paying (expense) entry. The evidence
    photo is optional; the date is never taken from the client — it's
    always "now" on the server, so it can't be backdated or mismatched.
    """
    if current_user.role != UserRole.TEACHER:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Only teachers record transactions")

    category = category.strip()
    if not category:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="category is required")
    if amount <= 0:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="amount must be greater than 0")

    image_path = None
    if image is not None and image.filename:
        contents = image.file.read()
        image_path = save_transaction_image(image, contents)

    txn = Transaction(
        school_id=current_user.school_id,
        type=type,
        method=method,
        category=category,
        description=(description.strip() if description else None) or None,
        amount=amount,
        transaction_date=datetime.now(timezone.utc),
        image_path=image_path,
        recorded_by_id=current_user.id,
    )
    db.add(txn)
    db.commit()
    db.refresh(txn)
    return txn


@router.get("", response_model=list[TransactionOut])
def list_transactions(
    school_id: int | None = None,
    method: TransactionMethod | None = None,
    start_date: datetime | None = None,
    end_date: datetime | None = None,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    scoped_school_id = _scoped_school_id(current_user, school_id)
    query = db.query(Transaction).filter(Transaction.school_id == scoped_school_id)
    if method is not None:
        query = query.filter(Transaction.method == method)
    if start_date is not None:
        query = query.filter(Transaction.transaction_date >= start_date)
    if end_date is not None:
        query = query.filter(Transaction.transaction_date <= end_date)
    return query.order_by(Transaction.transaction_date.desc()).all()


@router.get("/{transaction_id}", response_model=TransactionOut)
def get_transaction(transaction_id: int, db: Session = Depends(get_db), current_user: User = Depends(get_current_user)):
    txn = db.query(Transaction).filter(Transaction.id == transaction_id).first()
    if not txn:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Transaction not found")
    if current_user.role == UserRole.TEACHER and txn.school_id != current_user.school_id:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Not permitted to view this transaction")
    return txn


def _get_transaction_scoped(transaction_id: int, current_user: User, db: Session) -> Transaction:
    txn = db.query(Transaction).filter(Transaction.id == transaction_id).first()
    if not txn:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Transaction not found")
    if txn.school_id != current_user.school_id:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Not permitted to modify this transaction")
    return txn


def _transaction_is_locked(txn: Transaction, db: Session) -> bool:
    """
    Transactions aren't linked to an audit by foreign key — a finalized
    audit's "statements" are whatever fell inside its period_start/
    period_end for its school (see routers/audits.py). So a transaction is
    locked once ANY finalized audit for its school covers its date, since
    editing it afterward would silently change a report that's already
    been signed off.
    """
    return (
        db.query(Audit)
        .filter(
            Audit.school_id == txn.school_id,
            Audit.status == AuditStatus.FINALIZED,
            Audit.period_start <= txn.transaction_date,
            Audit.period_end >= txn.transaction_date,
        )
        .first()
        is not None
    )


@router.patch("/{transaction_id}", response_model=TransactionOut)
def update_transaction(
    transaction_id: int,
    request: Request,
    type: TransactionType | None = Form(None),
    method: TransactionMethod | None = Form(None),
    category: str | None = Form(None),
    description: str | None = Form(None),
    amount: Decimal | None = Form(None),
    image: UploadFile | None = File(None),
    db: Session = Depends(get_db),
    current_user: User = Depends(require_school_scope),
):
    """
    transaction_date is never editable here — it's set once, server-side,
    at creation (see create_transaction) and stays that way; changing "when
    this happened" isn't something this form exposes at all.
    """
    if current_user.role != UserRole.TEACHER:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Only teachers edit transactions")

    txn = _get_transaction_scoped(transaction_id, current_user, db)
    if _transaction_is_locked(txn, db):
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="This transaction falls inside a finalized audit and can no longer be changed",
        )

    if type is not None:
        txn.type = type
    if method is not None:
        txn.method = method
    if category is not None:
        category = category.strip()
        if not category:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="category cannot be blank")
        txn.category = category
    if description is not None:
        txn.description = description.strip() or None
    if amount is not None:
        if amount <= 0:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="amount must be greater than 0")
        txn.amount = amount
    if image is not None and image.filename:
        contents = image.file.read()
        txn.image_path = save_transaction_image(image, contents)

    db.commit()
    db.refresh(txn)
    log_activity(db, action="transaction_updated", actor=current_user, target_type="transaction", target_id=txn.id,
                 detail=f"Updated {txn.type.value} of {txn.amount} ({txn.category})", request=request)
    return txn


@router.delete("/{transaction_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_transaction(
    transaction_id: int,
    request: Request,
    db: Session = Depends(get_db),
    current_user: User = Depends(require_school_scope),
):
    if current_user.role != UserRole.TEACHER:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Only teachers delete transactions")

    txn = _get_transaction_scoped(transaction_id, current_user, db)
    if _transaction_is_locked(txn, db):
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="This transaction falls inside a finalized audit and can no longer be deleted",
        )

    detail = f"Deleted {txn.type.value} of {txn.amount} ({txn.category})"
    db.delete(txn)
    db.commit()
    log_activity(db, action="transaction_deleted", actor=current_user, target_type="transaction", target_id=transaction_id,
                 detail=detail, request=request)
