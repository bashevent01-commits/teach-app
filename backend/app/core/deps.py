from fastapi import Depends, HTTPException, Request, status
from sqlalchemy.orm import Session

from app.core.database import get_db
from app.core.security import decode_access_token
from app.models.user import User, UserRole

ACCESS_TOKEN_COOKIE = "access_token"


def _extract_token(request: Request) -> str | None:
    """
    Accepts either an httpOnly session cookie (used by the frontend, subject
    to CSRF protection for state-changing requests — see main.py) or a
    Bearer Authorization header (used by scripts/API clients; browsers never
    attach this automatically cross-site, so it doesn't need CSRF checks).
    Header takes precedence when both are present.
    """
    auth_header = request.headers.get("authorization")
    if auth_header and auth_header.lower().startswith("bearer "):
        return auth_header[7:].strip()
    return request.cookies.get(ACCESS_TOKEN_COOKIE)


def get_current_user(request: Request, db: Session = Depends(get_db)) -> User:
    credentials_exception = HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail="Could not validate credentials",
        headers={"WWW-Authenticate": "Bearer"},
    )
    token = _extract_token(request)
    if not token:
        raise credentials_exception

    payload = decode_access_token(token)
    if payload is None:
        raise credentials_exception
    user_id = payload.get("sub")
    if user_id is None:
        raise credentials_exception

    user = db.query(User).filter(User.id == int(user_id)).first()
    if user is None or not user.is_active:
        raise credentials_exception

    token_version = payload.get("tv")
    if token_version is None or int(token_version) != user.token_version:
        # Token was issued before a password reset / deactivation bumped
        # the version — treat it the same as an invalid token.
        raise credentials_exception

    return user


def require_super_admin(current_user: User = Depends(get_current_user)) -> User:
    if current_user.role != UserRole.SUPER_ADMIN:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Only a super admin can perform this action",
        )
    return current_user


def require_school_scope(current_user: User = Depends(get_current_user)) -> User:
    """
    Any authenticated, active user whose account is tied to a school
    (i.e. teachers). Super admins manage accounts/schools but don't
    submit audits/transactions/posts themselves.
    """
    if current_user.role == UserRole.TEACHER and current_user.school_id is None:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Account is not assigned to a school",
        )
    return current_user
