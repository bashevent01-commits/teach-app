from fastapi import APIRouter, Depends, Query
from sqlalchemy.orm import Session

from app.core.database import get_db
from app.core.deps import require_super_admin
from app.models.activity_log import ActivityLog
from app.models.user import User
from app.schemas.activity_log import ActivityLogOut

router = APIRouter(prefix="/api/activity-log", tags=["activity-log"])


@router.get("", response_model=list[ActivityLogOut])
def list_activity(
    limit: int = Query(default=100, le=500),
    action: str | None = None,
    db: Session = Depends(get_db),
    _: User = Depends(require_super_admin),
):
    """
    Security/accountability trail — logins, lockouts, account and school
    changes, and audit/transaction edits. Super admin only.
    """
    q = db.query(ActivityLog)
    if action:
        q = q.filter(ActivityLog.action == action)
    return q.order_by(ActivityLog.created_at.desc()).limit(limit).all()
