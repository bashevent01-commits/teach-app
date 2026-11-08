from fastapi import APIRouter, Depends, HTTPException, Request, status
from fastapi.responses import JSONResponse
from sqlalchemy.orm import Session

from app.core.activity_log import log_activity
from app.core.database import get_db
from app.core.deps import get_current_user, require_super_admin
from app.core.limiter import limiter
from app.models.product_category import ProductCategory
from app.models.stock_item import StockItem
from app.models.user import User
from app.utils.names import find_similar
from app.schemas.product_category import ProductCategoryCreate, ProductCategoryUpdate, ProductCategoryOut

router = APIRouter(prefix="/api/product-categories", tags=["product-categories"])


def _already_there(existing: ProductCategory) -> JSONResponse:
    """Categories are shared by everyone, so a new one that means the same as an old one is turned back with the old one named."""
    return JSONResponse(status_code=status.HTTP_409_CONFLICT, content={
        "detail": f'"{existing.name}" is already in the list. Please use it instead.',
        "existing": {"id": existing.id, "name": existing.name},
    })


@router.get("", response_model=list[ProductCategoryOut])
def list_categories(db: Session = Depends(get_db), _: User = Depends(get_current_user)):
    """Open to any authenticated user — staff need this list to populate the Add/Edit Stock Item category picker."""
    return db.query(ProductCategory).order_by(ProductCategory.name).all()


@router.post("", response_model=ProductCategoryOut, status_code=status.HTTP_201_CREATED)
@limiter.limit("10/minute")
def create_category(payload: ProductCategoryCreate, request: Request, db: Session = Depends(get_db), current_user: User = Depends(get_current_user)):
    """Open to any authenticated user — staff can add a category inline from the Stock page instead of waiting on a super"""
    existing = find_similar(payload.name, db.query(ProductCategory).all(), lambda c: c.name)
    if existing:
        return _already_there(existing)
    category = ProductCategory(name=payload.name)
    db.add(category)
    db.commit()
    db.refresh(category)
    log_activity(db, action="product_category_created", actor=current_user, target_type="product_category", target_id=category.id,
                 detail=f"Added product category '{category.name}'", request=request)
    return category


@router.patch("/{category_id}", response_model=ProductCategoryOut)
@limiter.limit("20/minute")
def update_category(category_id: int, payload: ProductCategoryUpdate, request: Request, db: Session = Depends(get_db), admin: User = Depends(require_super_admin)):
    category = db.query(ProductCategory).filter(ProductCategory.id == category_id).first()
    if not category:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Category not found")
    if payload.name != category.name:
        existing = find_similar(payload.name, db.query(ProductCategory).filter(ProductCategory.id != category_id).all(), lambda c: c.name)
        if existing:
            return _already_there(existing)
    category.name = payload.name
    db.commit()
    db.refresh(category)
    log_activity(db, action="product_category_updated", actor=admin, target_type="product_category", target_id=category.id,
                 detail=f"Renamed product category to '{category.name}'", request=request)
    return category


@router.delete("/{category_id}", status_code=status.HTTP_204_NO_CONTENT)
@limiter.limit("10/minute")
def delete_category(category_id: int, request: Request, db: Session = Depends(get_db), admin: User = Depends(require_super_admin)):
    category = db.query(ProductCategory).filter(ProductCategory.id == category_id).first()
    if not category:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Category not found")
    if db.query(StockItem).filter(StockItem.category_id == category_id).first():
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="This category is in use by stock items and can't be deleted — reassign or remove those items first",
        )
    db.delete(category)
    db.commit()
    log_activity(db, action="product_category_deleted", actor=admin, target_type="product_category", target_id=category_id,
                 detail=f"Deleted product category '{category.name}'", request=request)
