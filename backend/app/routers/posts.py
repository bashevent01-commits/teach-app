from fastapi import APIRouter, Depends, File, Form, HTTPException, UploadFile, status
from sqlalchemy.orm import Session

from app.core.database import get_db
from app.core.deps import get_current_user, require_school_scope
from app.models.post import Post
from app.models.post_report import PostReport
from app.models.user import User, UserRole
from app.schemas.post import PostOut, PostReportCreate, PostReportOut
from app.utils.uploads import save_post_image

router = APIRouter(prefix="/api/posts", tags=["posts"])


@router.post("", response_model=PostOut, status_code=status.HTTP_201_CREATED)
def create_post(
    title: str = Form(...),
    body: str = Form(...),
    image: UploadFile | None = File(None),
    db: Session = Depends(get_db),
    current_user: User = Depends(require_school_scope),
):
    """
    Accepts multipart/form-data (rather than a JSON body) so an optional
    photo can be attached to the post — images only, no video.
    """
    if current_user.role != UserRole.TEACHER:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Only teachers post news")

    title = title.strip()
    body = body.strip()
    if not title or not body:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="title and body are required")

    image_path = None
    if image is not None and image.filename:
        contents = image.file.read()
        image_path = save_post_image(image, contents)

    post = Post(
        school_id=current_user.school_id,
        author_id=current_user.id,
        title=title,
        body=body,
        image_path=image_path,
    )
    db.add(post)
    db.commit()
    db.refresh(post)
    return post


@router.get("", response_model=list[PostOut])
def list_posts(
    school_id: int | None = None,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """
    Teachers see posts for their own school only — this is the
    'other teachers can access it' portal feed, scoped per school.
    """
    if current_user.role == UserRole.TEACHER:
        scoped_school_id = current_user.school_id
    else:
        if school_id is None:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="school_id is required")
        scoped_school_id = school_id
    return db.query(Post).filter(Post.school_id == scoped_school_id).order_by(Post.created_at.desc()).all()


@router.delete("/{post_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_post(post_id: int, db: Session = Depends(get_db), current_user: User = Depends(require_school_scope)):
    post = db.query(Post).filter(Post.id == post_id).first()
    if not post:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Post not found")
    if post.school_id != current_user.school_id and current_user.role != UserRole.SUPER_ADMIN:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Not permitted to delete this post")
    if post.author_id != current_user.id and current_user.role != UserRole.SUPER_ADMIN:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Only the author or a super admin can delete this post")
    db.delete(post)
    db.commit()


@router.post("/{post_id}/report", response_model=PostReportOut, status_code=status.HTTP_201_CREATED)
def report_post(
    post_id: int,
    payload: PostReportCreate,
    db: Session = Depends(get_db),
    current_user: User = Depends(require_school_scope),
):
    """Flags a post for the super admins to review. Available to any teacher who can see the post."""
    if current_user.role != UserRole.TEACHER:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Only teachers can report posts")

    post = db.query(Post).filter(Post.id == post_id).first()
    if not post:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Post not found")
    if post.school_id != current_user.school_id:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Not permitted to report this post")

    report = PostReport(
        post_id=post.id,
        reporter_id=current_user.id,
        reason=payload.reason,
    )
    db.add(report)
    db.commit()
    db.refresh(report)
    return report