from datetime import datetime

from pydantic import BaseModel, ConfigDict, field_validator

from app.models.audit import AuditStatus


class AuditCreate(BaseModel):
    title: str
    period_start: datetime
    period_end: datetime
    summary: str | None = None

    @field_validator("title")
    @classmethod
    def title_not_empty(cls, v: str) -> str:
        v = v.strip()
        if not v:
            raise ValueError("title is required")
        return v

    @field_validator("period_end")
    @classmethod
    def end_after_start(cls, v: datetime, info):
        start = info.data.get("period_start")
        if start and v < start:
            raise ValueError("period_end must be on or after period_start")
        return v


class AuditUpdate(BaseModel):
    title: str | None = None
    summary: str | None = None
    period_start: datetime | None = None
    period_end: datetime | None = None

    @field_validator("title")
    @classmethod
    def title_not_empty_if_given(cls, v: str | None) -> str | None:
        if v is None:
            return v
        v = v.strip()
        if not v:
            raise ValueError("title cannot be blank")
        return v


class AuditOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    school_id: int
    title: str
    period_start: datetime
    period_end: datetime
    summary: str | None
    status: AuditStatus
    submitted_by_id: int
    created_at: datetime
    finalized_at: datetime | None
