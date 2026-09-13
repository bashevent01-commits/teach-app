from fastapi import APIRouter, Depends, File, Form, HTTPException, Request, UploadFile, status
from sqlalchemy.orm import Session

from app.core.database import get_db
from app.core.deps import get_current_user, require_institution_scope
from app.core.limiter import limiter
from app.models.post import Post
from app.models.post_report import PostReport
from app.models.user import User, UserRole
from app.schemas.post import PostOut, PostReportCreate, PostReportOut
from app.utils.uploads import save_post_image, delete_storage_object

router = APIRouter(prefix="/api/posts", tags=["posts"])


@router.post("", response_model=PostOut, status_code=status.HTTP_201_CREATED)
@limiter.limit("10/minute")
def create_post(
    request: Request,
    title: str = Form(...),
    body: str = Form(...),
    image: UploadFile | None = File(None),
    db: Session = Depends(get_db),
    current_user: User = Depends(require_institution_scope),
):
    """
    Accepts multipart/form-data (rather than a JSON body) so an optional
    photo can be attached to the post — images only, no video.
    """
    if current_user.role != UserRole.STAFF:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Only staff post news")

    title = title.strip()
    body = body.strip()
    if not title or not body:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="title and body are required")

    image_path = None
    if image is not None and image.filename:
        contents = image.file.read()
        image_path = save_post_image(image, contents)

    post = Post(
        institution_id=current_user.institution_id,
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
    institution_id: int | None = None,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """
    Staff see posts for their own institution only — this is the
    'other staff can access it' portal feed, scoped per institution.
    """
    if current_user.role == UserRole.STAFF:
        scoped_institution_id = current_user.institution_id
    else:
        if institution_id is None:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="institution_id is required")
        scoped_institution_id = institution_id
    return db.query(Post).filter(Post.institution_id == scoped_institution_id).order_by(Post.created_at.desc()).all()


@router.delete("/{post_id}", status_code=status.HTTP_204_NO_CONTENT)
@limiter.limit("20/minute")
def delete_post(post_id: int, request: Request, db: Session = Depends(get_db), current_user: User = Depends(require_institution_scope)):
    post = db.query(Post).filter(Post.id == post_id).first()
    if not post:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Post not found")
    if post.institution_id != current_user.institution_id and current_user.role != UserRole.SUPER_ADMIN:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Not permitted to delete this post")
    if post.author_id != current_user.id and current_user.role != UserRole.SUPER_ADMIN:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Only the author or a super admin can delete this post")
    image_path = post.image_path
    db.delete(post)
    db.commit()
    delete_storage_object(image_path)


@router.post("/{post_id}/report", response_model=PostReportOut, status_code=status.HTTP_201_CREATED)
@limiter.limit("10/minute")
def report_post(
    post_id: int,
    request: Request,
    payload: PostReportCreate,
    db: Session = Depends(get_db),
    current_user: User = Depends(require_institution_scope),
):
    """Flags a post for the super admins to review. Available to any staff member who can see the post."""
    if current_user.role != UserRole.STAFF:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Only staff can report posts")

    post = db.query(Post).filter(Post.id == post_id).first()
    if not post:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Post not found")
    if post.institution_id != current_user.institution_id:
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