from datetime import datetime

from pydantic import BaseModel


class ActivityLogOut(BaseModel):
    id: int
    actor_id: int | None = None
    actor_username: str | None = None
    action: str
    target_type: str | None = None
    target_id: int | None = None
    detail: str | None = None
    ip_address: str | None = None
    created_at: datetime

    model_config = {"from_attributes": True}
