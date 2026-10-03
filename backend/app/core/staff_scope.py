from fastapi import HTTPException, status
from sqlalchemy.orm import Session

from app.models.user import User, UserRole


def resolve_staff_filter(db: Session, current_user: User, institution_id: int, staff_id: int | None) -> int | None:
    # Staff are always limited to themselves; admins may narrow to one staff member of the institution
    if current_user.role == UserRole.STAFF:
        return current_user.id
    if staff_id is None:
        return None
    target = db.query(User).filter(User.id == staff_id, User.institution_id == institution_id, User.role == UserRole.STAFF).first()
    if not target:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Staff member not found in this institution")
    return target.id
