from datetime import datetime

from pydantic import BaseModel, ConfigDict


class SchoolCreate(BaseModel):
    name: str
    address: str | None = None


class SchoolOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    name: str
    address: str | None
    logo_path: str | None
    created_at: datetime
