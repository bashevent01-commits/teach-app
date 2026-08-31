from datetime import datetime, timezone

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from app.core.database import get_db
from app.core.deps import require_super_admin
from app.models.post import Post
from app.models.post_report import PostReport, ReportStatus
from app.models.user import User

router = APIRouter(prefix="/api/moderation", tags=["moderation"])


def _post_summary(post: Post) -> dict:
    return {
        "id": post.id,
        "school_id": post.school_id,
        "school_name": post.school.name if post.school else None,
        "author_id": post.author_id,
        "author_name": post.author.full_name if post.author else None,
        "title": post.title,
        "body": post.body,
        "image_path": post.image_path,
        "created_at": post.created_at,
        "open_report_count": sum(1 for r in post.reports if r.status == ReportStatus.OPEN),
    }


@router.get("/posts")
def list_all_posts(
    school_id: int | None = None,
    has_image: bool | None = None,
    db: Session = Depends(get_db),
    _: User = Depends(require_super_admin),
):
    """Every teacher post, across every school, for the super admin review queue."""
    query = db.query(Post)
    if school_id is not None:
        query = query.filter(Post.school_id == school_id)
    if has_image is not None:
        query = query.filter(Post.image_path.isnot(None)) if has_image else query.filter(Post.image_path.is_(None))
    posts = query.order_by(Post.created_at.desc()).all()
    return [_post_summary(p) for p in posts]


@router.get("/reports")
def list_reports(
    status_filter: ReportStatus | None = None,
    db: Session = Depends(get_db),
    _: User = Depends(require_super_admin),
):
    """Reports teachers have filed against posts, with the reported post attached, for review."""
    query = db.query(PostReport)
    if status_filter is not None:
        query = query.filter(PostReport.status == status_filter)
    reports = query.order_by(PostReport.created_at.desc()).all()
    return [
        {
            "id": r.id,
            "post_id": r.post_id,
            "post": _post_summary(r.post) if r.post else None,
            "reporter_id": r.reporter_id,
            "reporter_name": r.reporter.full_name if r.reporter else None,
            "reason": r.reason,
            "status": r.status,
            "created_at": r.created_at,
            "resolved_at": r.resolved_at,
            "resolution": r.resolution,
        }
        for r in reports
    ]


@router.post("/reports/{report_id}/resolve")
def resolve_report(
    report_id: int,
    action: str,
    db: Session = Depends(get_db),
    current_user: User = Depends(require_super_admin),
):
    """
    action is "dismiss" (report reviewed, post kept) or "remove_post"
    (report reviewed, the reported post is deleted).
    """
    if action not in ("dismiss", "remove_post"):
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="action must be 'dismiss' or 'remove_post'")

    report = db.query(PostReport).filter(PostReport.id == report_id).first()
    if not report:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Report not found")

    if action == "remove_post" and report.post:
        db.delete(report.post)

    report.status = ReportStatus.RESOLVED
    report.resolved_at = datetime.now(timezone.utc)
    report.resolved_by_id = current_user.id
    report.resolution = "dismissed" if action == "dismiss" else "post_removed"
    db.commit()
    return {"ok": True}