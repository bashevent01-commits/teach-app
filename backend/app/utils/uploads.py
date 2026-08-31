import io
import uuid
from pathlib import Path

from fastapi import HTTPException, UploadFile, status
from PIL import Image

from app.core.config import settings

ALLOWED_CONTENT_TYPES = {"image/png", "image/jpeg", "image/jpg", "image/webp"}


def _validate_content_type(file: UploadFile) -> None:
    if file.content_type not in ALLOWED_CONTENT_TYPES:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="File must be a PNG, JPEG, or WEBP image",
        )


def _process_and_save(contents: bytes, upload_dir: Path, max_bytes: int, max_dim: tuple[int, int]) -> str:
    """
    Validate size/content and re-encode with Pillow (rather than trusting
    the uploaded bytes) so a mismatched/malicious file can't slip through
    under an image content-type. Returns the relative path to store.
    """
    if len(contents) > max_bytes:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"Image must be under {max_bytes // (1024 * 1024)}MB",
        )

    try:
        image = Image.open(io.BytesIO(contents))
        image.verify()
        # Re-open after verify() (which leaves the file unusable for further ops)
        image = Image.open(io.BytesIO(contents)).convert("RGBA")
    except Exception:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Uploaded file is not a valid image",
        )

    image.thumbnail(max_dim)

    upload_dir.mkdir(parents=True, exist_ok=True)
    filename = f"{uuid.uuid4().hex}.png"
    dest = upload_dir / filename
    image.save(dest, format="PNG")

    return str(dest)


def save_school_logo(file: UploadFile, contents: bytes) -> str:
    _validate_content_type(file)
    return _process_and_save(contents, Path(settings.UPLOAD_DIR), settings.MAX_LOGO_SIZE_BYTES, (1024, 1024))


def save_transaction_image(file: UploadFile, contents: bytes) -> str:
    """Optional evidence photo attached to a Receiving/Paying entry."""
    _validate_content_type(file)
    return _process_and_save(contents, Path("uploads/transactions"), settings.MAX_LOGO_SIZE_BYTES, (1600, 1600))


def save_post_image(file: UploadFile, contents: bytes) -> str:
    """Optional image attached to a news post."""
    _validate_content_type(file)
    return _process_and_save(contents, Path("uploads/posts"), settings.MAX_LOGO_SIZE_BYTES, (1600, 1600))