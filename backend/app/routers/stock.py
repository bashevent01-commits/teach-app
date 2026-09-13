from fastapi import APIRouter, Depends, HTTPException, Request, status
from sqlalchemy.orm import Session

from app.core.activity_log import log_activity
from app.core.database import get_db
from app.core.deps import get_current_user, require_institution_scope
from app.core.limiter import limiter
from app.models.stock_item import StockItem
from app.models.transaction import Transaction
from app.models.user import User, UserRole
from app.schemas.stock import StockItemCreate, StockItemUpdate, StockItemOut

router = APIRouter(prefix="/api/stock", tags=["stock"])


def _scoped_institution_id(current_user: User, requested_institution_id: int | None) -> int:
    if current_user.role == UserRole.STAFF:
        return current_user.institution_id
    if requested_institution_id is None:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="institution_id is required")
    return requested_institution_id


@router.post("", response_model=StockItemOut, status_code=status.HTTP_201_CREATED)
@limiter.limit("20/minute")
def create_stock_item(payload: StockItemCreate, request: Request, db: Session = Depends(get_db), current_user: User = Depends(require_institution_scope)):
    if current_user.role != UserRole.STAFF:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Only staff manage stock")

    if db.query(StockItem).filter(StockItem.institution_id == current_user.institution_id, StockItem.name == payload.name).first():
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="A stock item with this name already exists")

    item = StockItem(
        institution_id=current_user.institution_id,
        name=payload.name,
        description=payload.description,
        unit_price=payload.unit_price,
        quantity=payload.quantity,
    )
    db.add(item)
    db.commit()
    db.refresh(item)
    log_activity(db, action="stock_item_created", actor=current_user, target_type="stock_item", target_id=item.id,
                 detail=f"Added stock item '{item.name}' (qty {item.quantity})", request=request)
    return item


@router.get("", response_model=list[StockItemOut])
def list_stock_items(institution_id: int | None = None, db: Session = Depends(get_db), current_user: User = Depends(get_current_user)):
    scoped_institution_id = _scoped_institution_id(current_user, institution_id)
    return db.query(StockItem).filter(StockItem.institution_id == scoped_institution_id).order_by(StockItem.name).all()


def _get_stock_item_scoped(item_id: int, current_user: User, db: Session) -> StockItem:
    item = db.query(StockItem).filter(StockItem.id == item_id).first()
    if not item:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Stock item not found")
    if item.institution_id != current_user.institution_id:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Not permitted to access this stock item")
    return item


@router.get("/{item_id}", response_model=StockItemOut)
def get_stock_item(item_id: int, db: Session = Depends(get_db), current_user: User = Depends(get_current_user)):
    item = db.query(StockItem).filter(StockItem.id == item_id).first()
    if not item:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Stock item not found")
    if current_user.role == UserRole.STAFF and item.institution_id != current_user.institution_id:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Not permitted to view this stock item")
    return item


@router.patch("/{item_id}", response_model=StockItemOut)
@limiter.limit("20/minute")
def update_stock_item(item_id: int, payload: StockItemUpdate, request: Request, db: Session = Depends(get_db), current_user: User = Depends(require_institution_scope)):
    """Edits catalog info only (name/description/price) — quantity is never
    set directly here; it only moves through recorded transactions, so
    every change to it has a transaction behind it."""
    if current_user.role != UserRole.STAFF:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Only staff manage stock")

    item = _get_stock_item_scoped(item_id, current_user, db)
    if payload.name is not None and payload.name != item.name:
        if db.query(StockItem).filter(StockItem.institution_id == item.institution_id, StockItem.name == payload.name, StockItem.id != item_id).first():
            raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="A stock item with this name already exists")
        item.name = payload.name
    if payload.description is not None:
        item.description = payload.description
    if payload.unit_price is not None:
        item.unit_price = payload.unit_price

    db.commit()
    db.refresh(item)
    log_activity(db, action="stock_item_updated", actor=current_user, target_type="stock_item", target_id=item.id,
                 detail=f"Edited stock item '{item.name}'", request=request)
    return item


@router.delete("/{item_id}", status_code=status.HTTP_204_NO_CONTENT)
@limiter.limit("10/minute")
def delete_stock_item(item_id: int, request: Request, db: Session = Depends(get_db), current_user: User = Depends(require_institution_scope)):
    if current_user.role != UserRole.STAFF:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Only staff manage stock")

    item = _get_stock_item_scoped(item_id, current_user, db)
    if db.query(Transaction).filter(Transaction.stock_item_id == item_id).first():
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="This item has recorded transactions and can't be deleted — its sales/restock history must stay intact",
        )
    db.delete(item)
    db.commit()
    log_activity(db, action="stock_item_deleted", actor=current_user, target_type="stock_item", target_id=item_id,
                 detail=f"Deleted stock item '{item.name}'", request=request)
