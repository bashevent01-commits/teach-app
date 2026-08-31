from fastapi import Request
from sqlalchemy.orm import Session

from app.models.activity_log import ActivityLog
from app.models.user import User


def client_ip(request: Request | None) -> str | None:
    if request is None:
        return None
    forwarded = request.headers.get("x-forwarded-for")
    if forwarded:
        return forwarded.split(",")[0].strip()
    return request.client.host if request.client else None


def log_activity(
    db: Session,
    *,
    action: str,
    actor: User | None = None,
    actor_username: str | None = None,
    target_type: str | None = None,
    target_id: int | None = None,
    detail: str | None = None,
    request: Request | None = None,
) -> None:
    """
    Records one activity log entry and commits it immediately. Call this
    AFTER your own db.commit() for the primary operation, not before — it
    commits the session it's given, so calling it mid-transaction would
    prematurely commit whatever else is pending on that session too.
    """
    entry = ActivityLog(
        actor_id=actor.id if actor else None,
        actor_username=actor_username or (actor.username if actor else None),
        action=action,
        target_type=target_type,
        target_id=target_id,
        detail=detail,
        ip_address=client_ip(request),
    )
    db.add(entry)
    db.commit()
