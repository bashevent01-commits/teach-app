# Teach

A school financial audit and communication portal.

- **Teachers** record income/expense transactions (Cash / M-Pesa / Bank, with
  optional photo evidence), group periods into audits, generate branded PDF
  audit reports and statements, and post news to a school-wide feed.
- **Super admins** onboard schools (setting each school's portal icon), issue
  teacher login credentials directly, and review/moderate reported posts.

## 1. Backend setup

### Requirements
- Python 3.11+
- PostgreSQL 14+

### Steps

```bash
cd backend
python3 -m venv venv
venv\Scripts\activate          # macOS/Linux: source venv/bin/activate
pip install -r requirements.txt

cp .env.example .env
```

Edit `.env`:
- `DATABASE_URL` — your Postgres connection string
- `SECRET_KEY` — generate one: `python -c "import secrets; print(secrets.token_urlsafe(64))"`
- Leave `BOOTSTRAP_SUPER_ADMIN_USERNAME` / `PASSWORD` as-is for your first login,
  then change that password immediately and remove those two lines.

**If you already have a database from an earlier version of this app**, run
`migration_security.sql` against it first (see below) — starting the server
against an existing database won't add the new columns/table on its own.

**If you're starting fresh**, just run:

```bash
uvicorn app.main:app --reload --port 8000
```

Table creation (including the new `activity_logs` table and the lockout
columns on `users`) happens automatically on first startup for a database
that doesn't have these tables yet.

API docs: `http://localhost:8000/docs`

### Migrating an existing database

If your database already has schools/users/transactions in it from before
this update, run `migration_security.sql` once, manually:

```bash
psql -d your_database -f migration_security.sql
```

This adds `failed_login_attempts`, `locked_until`, and `token_version` to
`users`, and creates the `activity_logs` table. It's idempotent (`IF NOT
EXISTS` throughout), so running it twice is harmless.

### Note on `bcrypt`

`requirements.txt` pins `bcrypt==4.0.1` alongside `passlib==1.7.4`. Newer
`bcrypt` releases (4.1+) removed an internal attribute this version of
passlib reads at startup, which crashes password hashing entirely. Keep the
pin unless you also upgrade passlib.

## 2. Frontend setup

Static — no build step. Serve the `frontend/` folder with any static file
server:

```bash
cd frontend
python -m http.server 5500
```

Open `http://localhost:5500/login.html` (teachers) or
`http://localhost:5500/admin-login.html` (super admins).

If your backend runs somewhere other than `http://localhost:8000`, set
`window.TEACH_API_BASE` before the other scripts load, and add your
frontend's origin to `CORS_ORIGINS` in the backend `.env`.

## Security

- **Session auth:** the frontend authenticates via an httpOnly session
  cookie set on login — the JWT itself is never persisted in `localStorage`,
  closing off token theft via XSS. A parallel `csrf_token` cookie (readable)
  is echoed back as an `X-CSRF-Token` header on every state-changing request
  (double-submit pattern) and checked server-side. Script/API clients can
  instead authenticate with a plain `Authorization: Bearer <token>` header
  — returned in the login response body — and are exempt from the CSRF
  check, since browsers never attach a custom header like that
  automatically on a cross-site request.
  - Set `COOKIE_SECURE=true` for any real deployment (HTTPS only); `false`
    is for local `http://` development only.
- **Brute-force protection:** `POST /api/auth/login` is rate limited to
  10/minute per IP, and independently each *account* locks for
  `LOGIN_LOCKOUT_MINUTES` (default 15) after `LOGIN_MAX_ATTEMPTS` (default
  5) consecutive failures.
- **Session revocation:** every JWT embeds the user's `token_version`. A
  password reset or account deactivation bumps that counter, invalidating
  previously-issued tokens immediately rather than waiting for expiry.
- **Activity log:** `GET /api/activity-log` (super admin only, also in the
  frontend nav as "Activity") records logins, lockouts, account/school
  changes, and audit/transaction edits — each with actor, IP, and timestamp.
- **Security headers + CSP** on every response; Swagger docs are exempted
  from CSP since they need a CDN script.

## Editing and deleting audits/transactions

Unlike an earlier version of this app, transactions aren't linked to an
audit by a stored foreign key — a **finalized** audit's statements are
computed from whatever transactions fall inside its `period_start`/
`period_end` for that school. This has one important consequence:

- A transaction is **locked** (can't be edited or deleted) the moment its
  date falls inside *any* finalized audit's period for its school — not
  because it was explicitly attached to that audit, but simply because it's
  in range. Editing it afterward would silently change a report that's
  already been signed off.
- A **draft** audit can have its title, summary, and period dates edited,
  or be deleted outright — deleting it doesn't touch any transactions,
  since none were ever linked to it in the first place.
- `transaction_date` itself is never editable — it's set once, server-side,
  at creation, and the edit form doesn't expose it at all.

## Production notes

- `SECRET_KEY` and the bootstrap super admin password in `.env` are dev
  placeholders — replace both before going live.
- `Base.metadata.create_all()` runs on startup for fast local setup. Move to
  Alembic migrations (already a dependency) before you have production data
  you can't afford to lose.
- Uploaded images (school logos, transaction evidence, post photos) are
  stored on local disk under `backend/uploads/` and served as static files.
  For a multi-instance deployment, move this to object storage.
