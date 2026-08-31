from datetime import datetime

from pydantic import BaseModel, ConfigDict, field_validator

from app.models.user import UserRole


class UserCreate(BaseModel):
    """Used by super_admin to issue credentials to a new teacher/user."""
    username: str
    full_name: str
    password: str
    role: UserRole = UserRole.TEACHER
    school_id: int | None = None

    @field_validator("password")
    @classmethod
    def password_strength(cls, v: str) -> str:
        if len(v) < 8:
            raise ValueError("password must be at least 8 characters")
        return v

    @field_validator("username")
    @classmethod
    def username_format(cls, v: str) -> str:
        v = v.strip()
        if len(v) < 3:
            raise ValueError("username must be at least 3 characters")
        return v


class UserOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    username: str
    full_name: str
    role: UserRole
    is_active: bool
    school_id: int | None
    created_at: datetime


class PasswordReset(BaseModel):
    new_password: str

    @field_validator("new_password")
    @classmethod
    def password_strength(cls, v: str) -> str:
        if len(v) < 8:
            raise ValueError("password must be at least 8 characters")
        return v
