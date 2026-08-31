import io
import uuid

import httpx
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


def _process_and_upload(contents: bytes, folder: str, max_bytes: int, max_dim: tuple[int, int]) -> str:
    """
    Validate size/content and re-encode with Pillow (rather than trusting
    the uploaded bytes) so a mismatched/malicious file can't slip through
    under an image content-type. Uploads the result to Supabase Storage
    (Render's local disk gets wiped on every restart) and returns the
    public URL to store on the record.
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

    buf = io.BytesIO()
    image.save(buf, format="PNG")
    png_bytes = buf.getvalue()

    if not settings.SUPABASE_URL or not settings.SUPABASE_SERVICE_ROLE_KEY:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Image storage is not configured",
        )

    object_path = f"{folder}/{uuid.uuid4().hex}.png"
    upload_url = f"{settings.SUPABASE_URL}/storage/v1/object/{settings.SUPABASE_STORAGE_BUCKET}/{object_path}"
    try:
        res = httpx.post(
            upload_url,
            content=png_bytes,
            headers={
                "Authorization": f"Bearer {settings.SUPABASE_SERVICE_ROLE_KEY}",
                "apikey": settings.SUPABASE_SERVICE_ROLE_KEY,
                "Content-Type": "image/png",
                "x-upsert": "true",
            },
            timeout=20,
        )
        res.raise_for_status()
    except httpx.HTTPError:
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail="Could not upload image to storage",
        )

    return f"{settings.SUPABASE_URL}/storage/v1/object/public/{settings.SUPABASE_STORAGE_BUCKET}/{object_path}"


def save_school_logo(file: UploadFile, contents: bytes) -> str:
    _validate_content_type(file)
    return _process_and_upload(contents, "logos", settings.MAX_LOGO_SIZE_BYTES, (1024, 1024))


def save_transaction_image(file: UploadFile, contents: bytes) -> str:
    """Optional evidence photo attached to a Receiving/Paying entry."""
    _validate_content_type(file)
    return _process_and_upload(contents, "transactions", settings.MAX_TRANSACTION_IMAGE_SIZE_BYTES, (1600, 1600))


def save_post_image(file: UploadFile, contents: bytes) -> str:
    """Optional image attached to a news post."""
    _validate_content_type(file)
    return _process_and_upload(contents, "posts", settings.MAX_POST_IMAGE_SIZE_BYTES, (1600, 1600))