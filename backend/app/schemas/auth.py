from pydantic import BaseModel


class LoginRequest(BaseModel):
    username: str
    password: str


class TokenResponse(BaseModel):
    """
    Includes the raw access token for non-browser clients (scripts, CI,
    Postman, other API integrations) that authenticate with a Bearer
    header instead of a cookie. The bundled frontend still receives this
    field but no longer needs to decode the JWT for user_id — it's
    provided directly — and never persists the token itself, relying on
    the httpOnly cookie set alongside this response instead.
    """
    access_token: str
    token_type: str = "bearer"
    user_id: int
    role: str
    full_name: str
    school_id: int | None = None
