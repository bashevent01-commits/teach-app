from pydantic import BaseModel


class LoginRequest(BaseModel):
    username: str
    password: str


class TokenResponse(BaseModel):
    """Includes the raw access token for non-browser clients (scripts, CI, Postman, other API integrations) that authenticate"""
    access_token: str
    token_type: str = "bearer"
    user_id: int
    role: str
    staff_type: str | None = None
    full_name: str
    institution_id: int | None = None
    institution_type: str | None = None
    individual: bool = False
