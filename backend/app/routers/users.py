from fastapi import APIRouter, Depends, HTTPException, Request, status
from sqlalchemy.orm import Session

from app.core.activity_log import log_activity
from app.core.database import get_db
from app.core.deps import require_super_admin
from app.core.security import hash_password
from app.models.school import School
from app.models.user import User, UserRole
from app.schemas.user import UserCreate, UserOut, PasswordReset, UserUpdate

router = APIRouter(prefix="/api/users", tags=["users"])


@router.post("", response_model=UserOut, status_code=status.HTTP_201_CREATED)
def create_user(payload: UserCreate, request: Request, db: Session = Depends(get_db), admin: User = Depends(require_super_admin)):
    """
    A super admin issues login credentials for a new user directly here.
    There is no self-registration path in this system by design.
    """
    if db.query(User).filter(User.username == payload.username).first():
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="Username already taken")

    if payload.role == UserRole.TEACHER:
        if payload.school_id is None:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="school_id is required for a teacher account")
        if not db.query(School).filter(School.id == payload.school_id).first():
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="School not found")

    user = User(
        username=payload.username,
        full_name=payload.full_name,
        hashed_password=hash_password(payload.password),
        role=payload.role,
        school_id=payload.school_id if payload.role == UserRole.TEACHER else None,
        created_by_id=admin.id,
    )
    db.add(user)
    db.commit()
    db.refresh(user)
    log_activity(db, action="user_created", actor=admin, target_type="user", target_id=user.id,
                 detail=f"Created {user.role.value} account '{user.username}'", request=request)
    return user


@router.get("", response_model=list[UserOut])
def list_users(db: Session = Depends(get_db), _: User = Depends(require_super_admin)):
    return db.query(User).order_by(User.created_at.desc()).all()


@router.patch("/{user_id}", response_model=UserOut)
def update_user(user_id: int, payload: UserUpdate, request: Request, db: Session = Depends(get_db), admin: User = Depends(require_super_admin)):
    """Super admin edits an existing account's username, display name, or school assignment."""
    user = db.query(User).filter(User.id == user_id).first()
    if not user:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="User not found")

    changes = []

    if payload.username is not None and payload.username != user.username:
        existing = db.query(User).filter(User.username == payload.username, User.id != user_id).first()
        if existing:
            raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="Username already taken")
        changes.append(f"username '{user.username}' -> '{payload.username}'")
        user.username = payload.username

    if payload.full_name is not None and payload.full_name != user.full_name:
        changes.append(f"full name '{user.full_name}' -> '{payload.full_name}'")
        user.full_name = payload.full_name

    if payload.school_id is not None and payload.school_id != user.school_id:
        if user.role != UserRole.TEACHER:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Only teacher accounts have a school assignment")
        school = db.query(School).filter(School.id == payload.school_id).first()
        if not school:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="School not found")
        changes.append(f"school {user.school_id} -> {payload.school_id}")
        user.school_id = payload.school_id

    if changes:
        db.commit()
        db.refresh(user)
        log_activity(db, action="user_updated", actor=admin, target_type="user", target_id=user.id,
                     detail="; ".join(changes), request=request)
    return user


@router.patch("/{user_id}/deactivate", response_model=UserOut)
def deactivate_user(user_id: int, request: Request, db: Session = Depends(get_db), admin: User = Depends(require_super_admin)):
    user = db.query(User).filter(User.id == user_id).first()
    if not user:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="User not found")
    if user.id == admin.id:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Cannot deactivate your own account")
    user.is_active = False
    # Belt-and-suspenders: is_active is already re-checked on every request,
    # but bumping the token version too means this account's sessions die
    # immediately even in a code path that only checked the token, not the
    # live DB row.
    user.token_version += 1
    db.commit()
    db.refresh(user)
    log_activity(db, action="user_deactivated", actor=admin, target_type="user", target_id=user.id,
                 detail=f"Deactivated '{user.username}'", request=request)
    return user


@router.patch("/{user_id}/reactivate", response_model=UserOut)
def reactivate_user(user_id: int, request: Request, db: Session = Depends(get_db), admin: User = Depends(require_super_admin)):
    user = db.query(User).filter(User.id == user_id).first()
    if not user:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="User not found")
    user.is_active = True
    db.commit()
    db.refresh(user)
    log_activity(db, action="user_reactivated", actor=admin, target_type="user", target_id=user.id,
                 detail=f"Reactivated '{user.username}'", request=request)
    return user


@router.post("/{user_id}/reset-password", response_model=UserOut)
def reset_password(user_id: int, payload: PasswordReset, request: Request, db: Session = Depends(get_db), admin: User = Depends(require_super_admin)):
    """Super admin issues a new password for a user (e.g. after a reset request)."""
    user = db.query(User).filter(User.id == user_id).first()
    if not user:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="User not found")
    user.hashed_password = hash_password(payload.new_password)
    # Any session started with the old password stops working immediately,
    # not just at its natural expiry — important if the reset was prompted
    # by a compromised or carelessly-shared password.
    user.token_version += 1
    user.failed_login_attempts = 0
    user.locked_until = None
    db.commit()
    db.refresh(user)
    log_activity(db, action="password_reset", actor=admin, target_type="user", target_id=user.id,
                 detail=f"Password reset for '{user.username}'", request=request)
    return user