from datetime import date

from fastapi import APIRouter, Depends, HTTPException, Request, status
from pydantic import BaseModel, Field, field_validator
from sqlalchemy import or_
from sqlalchemy.orm import Session

from app.core.activity_log import log_activity
from app.core.database import get_db
from app.core.deps import require_institution_scope
from app.models.activity_log import ActivityLog
from app.models.certificate import Certificate
from app.models.institution import Institution
from app.models.user import User, UserRole
from app.utils.members import today_local

router = APIRouter(prefix="/api/certificates", tags=["certificates"])

CATEGORIES = ("staff_training", "background_check", "handler_authorisation", "security_programme", "equipment", "station_clearance", "other")


class CertIn(BaseModel):
    category: str = "other"
    title: str = Field(min_length=2, max_length=160)
    holder: str = Field(min_length=2, max_length=160)
    reference_no: str | None = Field(default=None, max_length=80)
    issuer: str | None = Field(default=None, max_length=120)
    station: str | None = Field(default=None, max_length=60)
    issued_on: date | None = None
    expires_on: date | None = None
    notes: str | None = Field(default=None, max_length=300)

    @field_validator("category")
    @classmethod
    def category_ok(cls, v: str) -> str:
        if v not in CATEGORIES:
            raise ValueError("Unknown category")
        return v

    @field_validator("title", "holder", "reference_no", "issuer", "station", "notes")
    @classmethod
    def tidy(cls, v: str | None) -> str | None:
        return (" ".join(v.split()) or None) if v is not None else None


class CertUpdate(BaseModel):
    category: str | None = None
    title: str | None = Field(default=None, min_length=2, max_length=160)
    holder: str | None = Field(default=None, min_length=2, max_length=160)
    reference_no: str | None = Field(default=None, max_length=80)
    issuer: str | None = Field(default=None, max_length=120)
    station: str | None = Field(default=None, max_length=60)
    issued_on: date | None = None
    expires_on: date | None = None
    notes: str | None = Field(default=None, max_length=300)
    is_active: bool | None = None

    @field_validator("category")
    @classmethod
    def category_ok(cls, v: str | None) -> str | None:
        if v is not None and v not in CATEGORIES:
            raise ValueError("Unknown category")
        return v


def status_of(is_active: bool, expires_on: date | None, today: date) -> tuple[str, int | None]:
    """Expiry bands match the 60/30/15 day alert windows."""
    if not is_active:
        return "withdrawn", None
    if expires_on is None:
        return "no_expiry", None
    days = (expires_on - today).days
    if days < 0:
        return "expired", days
    if days <= 15:
        return "critical", days
    if days <= 30:
        return "warning", days
    if days <= 60:
        return "notice", days
    return "valid", days


def _out(c: Certificate, today: date) -> dict:
    state, days = status_of(c.is_active, c.expires_on, today)
    return {"id": c.id, "category": c.category, "title": c.title, "holder": c.holder, "reference_no": c.reference_no, "issuer": c.issuer,
            "station": c.station, "issued_on": c.issued_on.isoformat() if c.issued_on else None,
            "expires_on": c.expires_on.isoformat() if c.expires_on else None, "notes": c.notes, "is_active": c.is_active,
            "branch_id": c.branch_id, "status": state, "days_to_expiry": days}


def _aviation(db: Session, user: User) -> Institution:
    inst = db.get(Institution, user.institution_id) if user.institution_id else None
    if not inst or inst.kind != "aviation":
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Certificates are only for aviation workspaces")
    return inst


def _visible(db: Session, user: User):
    q = db.query(Certificate).filter(Certificate.institution_id == user.institution_id)
    # A station officer sees their own station's records and the shared HQ ones
    if user.role == UserRole.STAFF:
        q = q.filter(or_(Certificate.branch_id.is_(None), Certificate.branch_id == user.id))
    return q


def _get(db: Session, user: User, cert_id: int) -> Certificate:
    _aviation(db, user)
    cert = _visible(db, user).filter(Certificate.id == cert_id).first()
    if not cert:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Certificate not found")
    return cert


@router.get("")
def list_certificates(state: str = "all", q: str | None = None, db: Session = Depends(get_db), user: User = Depends(require_institution_scope)):
    _aviation(db, user)
    today = today_local()
    query = _visible(db, user)
    if q:
        like = f"%{q.strip()}%"
        query = query.filter(or_(Certificate.title.ilike(like), Certificate.holder.ilike(like), Certificate.reference_no.ilike(like), Certificate.station.ilike(like)))
    rows = [_out(c, today) for c in query.order_by(Certificate.expires_on.asc().nullslast(), Certificate.title).all()]
    if state != "all":
        rows = [r for r in rows if r["status"] == state]
    return rows


@router.get("/summary")
def summary(db: Session = Depends(get_db), user: User = Depends(require_institution_scope)):
    """Counts per expiry band plus the records that need action first; the alert popup reads this."""
    _aviation(db, user)
    today = today_local()
    rows = [_out(c, today) for c in _visible(db, user).filter(Certificate.is_active.is_(True)).all()]
    counts = {k: 0 for k in ("expired", "critical", "warning", "notice", "valid", "no_expiry")}
    for r in rows:
        counts[r["status"]] += 1
    urgent = sorted([r for r in rows if r["status"] in ("expired", "critical", "warning")], key=lambda r: r["days_to_expiry"])[:20]
    return {"date": today.isoformat(), "counts": counts, "total": len(rows), "urgent": urgent}


@router.post("", status_code=status.HTTP_201_CREATED)
def add_certificate(payload: CertIn, request: Request, db: Session = Depends(get_db), user: User = Depends(require_institution_scope)):
    _aviation(db, user)
    if payload.issued_on and payload.expires_on and payload.expires_on < payload.issued_on:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="The expiry date is before the issue date")
    cert = Certificate(institution_id=user.institution_id, branch_id=user.id if user.role == UserRole.STAFF else None, created_by_id=user.id, **payload.model_dump())
    db.add(cert)
    db.commit()
    db.refresh(cert)
    log_activity(db, action="certificate_created", actor=user, target_type="certificate", target_id=cert.id, detail=f"{cert.title} / {cert.holder}"[:480], request=request)
    return _out(cert, today_local())


@router.patch("/{cert_id}")
def update_certificate(cert_id: int, payload: CertUpdate, request: Request, db: Session = Depends(get_db), user: User = Depends(require_institution_scope)):
    cert = _get(db, user, cert_id)
    changes = payload.model_dump(exclude_unset=True)
    old_expiry = cert.expires_on
    for key, value in changes.items():
        setattr(cert, key, value)
    if cert.issued_on and cert.expires_on and cert.expires_on < cert.issued_on:
        db.rollback()
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="The expiry date is before the issue date")
    db.commit()
    if "expires_on" in changes and changes["expires_on"] != old_expiry:
        action, detail = "certificate_renewed", f"{cert.title}: expiry {old_expiry} to {cert.expires_on}"
    elif changes.get("is_active") is False:
        action, detail = "certificate_withdrawn", cert.title
    else:
        action, detail = "certificate_updated", f"{cert.title}: changed {', '.join(sorted(changes))}"
    log_activity(db, action=action, actor=user, target_type="certificate", target_id=cert.id, detail=detail[:480], request=request)
    return _out(cert, today_local())


@router.get("/{cert_id}/history")
def history(cert_id: int, request: Request, db: Session = Depends(get_db), user: User = Depends(require_institution_scope)):
    """Everything done to one record; opening it is itself written to the trail."""
    cert = _get(db, user, cert_id)
    rows = db.query(ActivityLog).filter(ActivityLog.target_type == "certificate", ActivityLog.target_id == cert.id).order_by(ActivityLog.created_at.desc()).limit(100).all()
    out = [{"action": r.action, "by": r.actor_username, "detail": r.detail, "at": r.created_at.isoformat() if r.created_at else None, "ip": r.ip_address} for r in rows]
    log_activity(db, action="certificate_viewed", actor=user, target_type="certificate", target_id=cert.id, detail=cert.title[:480], request=request)
    return out
