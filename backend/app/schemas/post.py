from datetime import datetime

from pydantic import BaseModel, ConfigDict, field_validator

from app.models.post_report import ReportStatus


class PostCreate(BaseModel):
    """
    The actual create endpoint accepts multipart/form-data (to allow an
    optional image upload alongside these fields) rather than this model
    directly, but the same field names/validation apply.
    """
    title: str
    body: str

    @field_validator("title", "body")
    @classmethod
    def not_empty(cls, v: str) -> str:
        v = v.strip()
        if not v:
            raise ValueError("field cannot be empty")
        return v


class PostOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    school_id: int
    author_id: int
    title: str
    body: str
    image_path: str | None
    created_at: datetime


class PostReportCreate(BaseModel):
    reason: str | None = None

    @field_validator("reason")
    @classmethod
    def trim(cls, v: str | None) -> str | None:
        if v is None:
            return None
        v = v.strip()
        return v or None


class PostReportOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    post_id: int
    reporter_id: int
    reason: str | None
    status: ReportStatus
    created_at: datetime
    resolved_at: datetime | None
    resolved_by_id: int | None
    resolution: str | None