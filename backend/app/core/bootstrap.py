import logging

from sqlalchemy.orm import Session

from app.core.config import settings
from app.core.security import hash_password
from app.models.user import User, UserRole

logger = logging.getLogger("teach.bootstrap")


def ensure_super_admin(db: Session) -> None:
    """
    Creates the very first super_admin account from env vars if no
    super_admin exists yet. This is the one bootstrap exception to
    "credentials only come from a super admin" — someone has to create
    the first one. Emits a loud warning so the operator changes the
    password immediately.
    """
    existing = db.query(User).filter(User.role == UserRole.SUPER_ADMIN).first()
    if existing:
        return

    if not settings.BOOTSTRAP_SUPER_ADMIN_USERNAME or not settings.BOOTSTRAP_SUPER_ADMIN_PASSWORD:
        logger.warning(
            "No super_admin account exists and BOOTSTRAP_SUPER_ADMIN_USERNAME/"
            "PASSWORD are not set. Set them in .env and restart to create one."
        )
        return

    admin = User(
        username=settings.BOOTSTRAP_SUPER_ADMIN_USERNAME,
        full_name="Super Admin",
        hashed_password=hash_password(settings.BOOTSTRAP_SUPER_ADMIN_PASSWORD),
        role=UserRole.SUPER_ADMIN,
        school_id=None,
    )
    db.add(admin)
    db.commit()
    logger.warning(
        "Created bootstrap super_admin '%s'. Log in and change this password "
        "immediately, then remove the BOOTSTRAP_SUPER_ADMIN_* values from .env.",
        settings.BOOTSTRAP_SUPER_ADMIN_USERNAME,
    )
