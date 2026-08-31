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


class UserUpdate(BaseModel):
    """Used by super_admin to edit an existing user's identity/assignment.
    All fields optional so a caller can patch just one at a time."""
    username: str | None = None
    full_name: str | None = None
    school_id: int | None = None

    @field_validator("username")
    @classmethod
    def username_format(cls, v: str | None) -> str | None:
        if v is None:
            return v
        v = v.strip()
        if len(v) < 3:
            raise ValueError("username must be at least 3 characters")
        return v

    @field_validator("full_name")
    @classmethod
    def full_name_not_blank(cls, v: str | None) -> str | None:
        if v is not None and not v.strip():
            raise ValueError("full_name cannot be blank")
        return v


class PasswordReset(BaseModel):
    new_password: str

    @field_validator("new_password")
    @classmethod
    def password_strength(cls, v: str) -> str:
        if len(v) < 8:
            raise ValueError("password must be at least 8 characters")
        return v