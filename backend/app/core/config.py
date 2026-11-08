"""Centralized application settings, loaded from environment variables / .env."""
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    DATABASE_URL: str
    SECRET_KEY: str
    ALGORITHM: str = "HS256"
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 60
    ANDROID_TOKEN_EXPIRE_DAYS: int = 90
    ENABLE_DOCS: bool = False
    # Password reset email: an email service with a web API (free hosts block SMTP); provider is "resend" or "brevo"
    EMAIL_PROVIDER: str = ""
    EMAIL_API_KEY: str = ""
    EMAIL_FROM: str = ""
    FRONTEND_URL: str = ""
    PASSWORD_RESET_MINUTES: int = 30
    MAX_REQUEST_BYTES: int = 12 * 1024 * 1024
    # When true, a workspace whose fee is overdue closes at 6am the morning after its first reminder
    BILLING_ENFORCED: bool = True

    BOOTSTRAP_SUPER_ADMIN_USERNAME: str | None = None
    BOOTSTRAP_SUPER_ADMIN_PASSWORD: str | None = None

    UPLOAD_DIR: str = "uploads/logos"
    MAX_LOGO_SIZE_BYTES: int = 2 * 1024 * 1024  # 2 MB

    POST_UPLOAD_DIR: str = "uploads/posts"
    MAX_POST_IMAGE_SIZE_BYTES: int = 5 * 1024 * 1024  # 5 MB

    TRANSACTION_UPLOAD_DIR: str = "uploads/transactions"
    MAX_TRANSACTION_IMAGE_SIZE_BYTES: int = 5 * 1024 * 1024  # 5 MB

    # Uploaded images (logos, post/transaction photos) are stored in Supabase Storage rather than local disk — Render's free
    SUPABASE_URL: str | None = None
    SUPABASE_SERVICE_ROLE_KEY: str | None = None
    SUPABASE_STORAGE_BUCKET: str = "uploads"

    CORS_ORIGINS: str = "http://localhost:5500"

    # Brute-force protection: after this many consecutive failed logins for one account, it's locked for
    LOGIN_MAX_ATTEMPTS: int = 5
    LOGIN_LOCKOUT_MINUTES: int = 15

    # Session cookie behavior.
    COOKIE_SECURE: bool = True
    COOKIE_SAMESITE: str = "lax"

    @property
    def frontend_base(self) -> str:
        """Where the website lives: FRONTEND_URL if set, otherwise the first public origin allowed to call the API."""
        if self.FRONTEND_URL.strip():
            return self.FRONTEND_URL.strip().rstrip("/")
        public = [o for o in self.cors_origins_list if o.startswith("https://")]
        return (public or self.cors_origins_list or ["http://localhost:5500"])[0].rstrip("/")

    @property
    def cors_origins_list(self) -> list[str]:
        return [o.strip() for o in self.CORS_ORIGINS.split(",") if o.strip()]


settings = Settings()