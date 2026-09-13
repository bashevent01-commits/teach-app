from datetime import datetime

from pydantic import BaseModel, ConfigDict, field_validator


class InstitutionCreate(BaseModel):
    name: str
    type: str
    address: str | None = None

    @field_validator("type")
    @classmethod
    def type_not_empty(cls, v: str) -> str:
        v = v.strip().lower()
        if not v:
            raise ValueError("type is required (e.g. school, shop, supermarket)")
        return v


class InstitutionOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    name: str
    type: str
    address: str | None
    logo_path: str | None
    created_at: datetime
