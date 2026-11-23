import base64
import binascii
import hashlib
import json
from datetime import date, datetime, timezone

from fastapi import APIRouter, Depends, HTTPException, Request, status
from pydantic import BaseModel, Field, field_validator
from sqlalchemy import case, func, or_
from sqlalchemy.orm import Session

from app.core.activity_log import log_activity
from app.core.database import get_db
from app.core.deps import require_institution_scope
from app.models.activity_log import ActivityLog
from app.models.flight_check import FlightCheck
from app.models.institution import Institution
from app.models.user import User, UserRole
from app.utils.members import today_local

router = APIRouter(prefix="/api/flight-checks", tags=["flight-checks"])

# The checklist officers complete; keys are stored with every record so old records stay readable if wording changes
TEMPLATE = [
    {"group": "Cabin", "key": "seats", "label": "Seats, seat pockets and under seats"},
    {"group": "Cabin", "key": "bins", "label": "Overhead bins and crew stowages"},
    {"group": "Cabin", "key": "toilets", "label": "Toilets: panels, bins and behind mirrors"},
    {"group": "Cabin", "key": "galleys", "label": "Galleys, catering carts and supplies"},
    {"group": "Cabin", "key": "crew_rest", "label": "Crew rest areas and stowages"},
    {"group": "Flight deck", "key": "flight_deck", "label": "Flight deck, when accessible"},
    {"group": "Hold and exterior", "key": "hold", "label": "Cargo hold and bulk areas"},
    {"group": "Hold and exterior", "key": "exterior", "label": "Landing gear bays and exterior panels"},
    {"group": "Access", "key": "doors", "label": "Doors and access points secured"},
    {"group": "Access", "key": "persons_items", "label": "No unauthorised persons or unattended items found"},
]
TEMPLATE_KEYS = [t["key"] for t in TEMPLATE]
TYPES = ("check", "search")


class ItemIn(BaseModel):
    key: str
    ok: bool
    note: str | None = Field(default=None, max_length=200)


class CheckIn(BaseModel):
    tail_number: str = Field(min_length=2, max_length=12)
    flight_no: str | None = Field(default=None, max_length=12)
    station: str = Field(min_length=2, max_length=20)
    check_type: str = "check"
    items: list[ItemIn]
    remarks: str | None = Field(default=None, max_length=300)
    signature: str = Field(max_length=150000)

    @field_validator("tail_number", "station", "flight_no")
    @classmethod
    def upper(cls, v: str | None) -> str | None:
        return (" ".join(v.split()).upper() or None) if v is not None else None

    @field_validator("check_type")
    @classmethod
    def type_ok(cls, v: str) -> str:
        if v not in TYPES:
            raise ValueError("check_type must be check or search")
        return v

    @field_validator("remarks")
    @classmethod
    def tidy(cls, v: str | None) -> str | None:
        return (" ".join(v.split()) or None) if v is not None else None

    @field_validator("signature")
    @classmethod
    def signature_ok(cls, v: str) -> str:
        prefix = "data:image/png;base64,"
        if not v.startswith(prefix):
            raise ValueError("The signature must be a PNG image")
        try:
            raw = base64.b64decode(v[len(prefix):], validate=True)
        except (binascii.Error, ValueError):
            raise ValueError("The signature is not valid")
        if not raw.startswith(b"\x89PNG"):
            raise ValueError("The signature must be a PNG image")
        return v


class ReviewIn(BaseModel):
    decision: str
    note: str | None = Field(default=None, max_length=300)

    @field_validator("decision")
    @classmethod
    def decision_ok(cls, v: str) -> str:
        if v not in ("approve", "reject"):
            raise ValueError("decision must be approve or reject")
        return v


def compute_hash(rec: dict, prev_hash: str) -> str:
    """Hash of the signed content plus the previous record's hash; changing any earlier record breaks every later one."""
    body = json.dumps(rec, sort_keys=True, separators=(",", ":"), ensure_ascii=True)
    return hashlib.sha256((prev_hash + body).encode()).hexdigest()


def _hash_input(c: FlightCheck) -> dict:
    return {"institution_id": c.institution_id, "branch_id": c.branch_id, "tail_number": c.tail_number, "flight_no": c.flight_no, "station": c.station,
            "check_type": c.check_type, "items": c.items, "result": c.result, "remarks": c.remarks, "signed_by_id": c.signed_by_id,
            "signed_name": c.signed_name, "signature_sha256": hashlib.sha256(c.signature.encode()).hexdigest(), "submitted_at": c.submitted_at}


def _out(c: FlightCheck, full: bool = False) -> dict:
    row = {"id": c.id, "tail_number": c.tail_number, "flight_no": c.flight_no, "station": c.station, "check_type": c.check_type, "result": c.result,
           "status": c.status, "signed_name": c.signed_name, "submitted_at": c.submitted_at, "check_date": c.check_date.isoformat(),
           "reviewed_name": c.reviewed_name, "review_note": c.review_note, "issue_count": sum(1 for i in c.items if not i.get("ok"))}
    if full:
        row.update({"items": c.items, "remarks": c.remarks, "signature": c.signature, "record_hash": c.record_hash, "prev_hash": c.prev_hash,
                    "reviewed_at": c.reviewed_at.isoformat() if c.reviewed_at else None})
    return row


def _aviation(db: Session, user: User) -> Institution:
    inst = db.get(Institution, user.institution_id) if user.institution_id else None
    if not inst or inst.kind != "aviation":
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Flight checks are only for aviation workspaces")
    return inst


def _visible(db: Session, user: User):
    q = db.query(FlightCheck).filter(FlightCheck.institution_id == user.institution_id)
    # A station officer sees only the checks they signed; HQ sees everything
    if user.role == UserRole.STAFF:
        q = q.filter(FlightCheck.branch_id == user.id)
    return q


def _get(db: Session, user: User, check_id: int) -> FlightCheck:
    _aviation(db, user)
    check = _visible(db, user).filter(FlightCheck.id == check_id).first()
    if not check:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Check not found")
    return check


@router.get("/template")
def template(db: Session = Depends(get_db), user: User = Depends(require_institution_scope)):
    _aviation(db, user)
    return TEMPLATE


@router.get("")
def list_checks(state: str = "all", q: str | None = None, day: date | None = None, limit: int = 200, db: Session = Depends(get_db), user: User = Depends(require_institution_scope)):
    _aviation(db, user)
    query = _visible(db, user)
    if q:
        like = f"%{q.strip()}%"
        query = query.filter(or_(FlightCheck.tail_number.ilike(like), FlightCheck.flight_no.ilike(like), FlightCheck.station.ilike(like), FlightCheck.signed_name.ilike(like)))
    if day:
        query = query.filter(FlightCheck.check_date == day)
    if state in ("pending", "approved", "rejected"):
        query = query.filter(FlightCheck.status == state)
    elif state == "issue":
        query = query.filter(FlightCheck.result == "issue")
    rows = query.order_by(FlightCheck.id.desc()).limit(min(max(limit, 1), 500)).all()
    return [_out(c) for c in rows]


@router.get("/summary")
def summary(db: Session = Depends(get_db), user: User = Depends(require_institution_scope)):
    """Today's picture for the dashboard: checks by station, what waits for review and what raised an issue."""
    _aviation(db, user)
    today = today_local()
    base = _visible(db, user)
    stations = base.filter(FlightCheck.check_date == today).with_entities(FlightCheck.station, func.count(FlightCheck.id), func.sum(case((FlightCheck.result == "issue", 1), else_=0))).group_by(FlightCheck.station).order_by(FlightCheck.station).all()
    return {
        "date": today.isoformat(),
        "today": base.filter(FlightCheck.check_date == today).count(),
        "pending": base.filter(FlightCheck.status == "pending").count(),
        "issues_open": base.filter(FlightCheck.result == "issue", FlightCheck.status == "pending").count(),
        "stations": [{"station": s, "checks": int(n), "issues": int(i or 0)} for s, n, i in stations],
    }


@router.post("", status_code=status.HTTP_201_CREATED)
def submit_check(payload: CheckIn, request: Request, db: Session = Depends(get_db), user: User = Depends(require_institution_scope)):
    _aviation(db, user)
    if user.role != UserRole.STAFF:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Checks are signed by the station officer who did them")
    keys = [i.key for i in payload.items]
    if sorted(keys) != sorted(TEMPLATE_KEYS):
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Every checklist item must be answered once")
    items = []
    for item in payload.items:
        note = (" ".join((item.note or "").split()))[:200] or None
        if not item.ok and not note:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Add a note for every item marked as an issue")
        items.append({"key": item.key, "ok": item.ok, "note": note})
    items.sort(key=lambda i: TEMPLATE_KEYS.index(i["key"]))
    now = datetime.now(timezone.utc).replace(microsecond=0)
    check = FlightCheck(
        institution_id=user.institution_id, branch_id=user.id, tail_number=payload.tail_number, flight_no=payload.flight_no, station=payload.station,
        check_type=payload.check_type, items=items, result="clear" if all(i["ok"] for i in items) else "issue", remarks=payload.remarks,
        signed_by_id=user.id, signed_name=user.full_name, signature=payload.signature, submitted_at=now.isoformat(), check_date=today_local(),
    )
    last = db.query(FlightCheck).filter(FlightCheck.institution_id == user.institution_id).order_by(FlightCheck.id.desc()).with_for_update().first()
    check.prev_hash = last.record_hash if last else ""
    check.record_hash = compute_hash(_hash_input(check), check.prev_hash)
    db.add(check)
    db.commit()
    db.refresh(check)
    log_activity(db, action="flight_check_submitted", actor=user, target_type="flight_check", target_id=check.id,
                 detail=f"{check.tail_number} at {check.station}, result {check.result}"[:480], request=request)
    return _out(check, full=True)


@router.get("/verify")
def verify_chain(request: Request, db: Session = Depends(get_db), user: User = Depends(require_institution_scope)):
    """Recomputes every hash in order; reports the first record that no longer matches what was signed."""
    _aviation(db, user)
    if user.role == UserRole.STAFF:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Only HQ can verify the record chain")
    prev, count = "", 0
    for c in db.query(FlightCheck).filter(FlightCheck.institution_id == user.institution_id).order_by(FlightCheck.id).all():
        count += 1
        if c.prev_hash != prev or compute_hash(_hash_input(c), c.prev_hash) != c.record_hash:
            log_activity(db, action="flight_check_chain_broken", actor=user, target_type="flight_check", target_id=c.id, detail="Verification failed", request=request)
            return {"ok": False, "checked": count, "broken_at": c.id}
        prev = c.record_hash
    log_activity(db, action="flight_check_chain_verified", actor=user, detail=f"{count} records verified", request=request)
    return {"ok": True, "checked": count, "broken_at": None}


@router.get("/{check_id}")
def get_check(check_id: int, request: Request, db: Session = Depends(get_db), user: User = Depends(require_institution_scope)):
    check = _get(db, user, check_id)
    log_activity(db, action="flight_check_viewed", actor=user, target_type="flight_check", target_id=check.id, detail=f"{check.tail_number} at {check.station}"[:480], request=request)
    return _out(check, full=True)


@router.post("/{check_id}/review")
def review_check(check_id: int, payload: ReviewIn, request: Request, db: Session = Depends(get_db), user: User = Depends(require_institution_scope)):
    check = _get(db, user, check_id)
    if user.role != UserRole.INSTITUTION_ADMIN:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Only HQ can approve or reject a check")
    if check.status != "pending":
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="This check has already been reviewed")
    note = (" ".join((payload.note or "").split()))[:300] or None
    if payload.decision == "reject" and not note:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Say why the check is rejected")
    check.status = "approved" if payload.decision == "approve" else "rejected"
    check.reviewed_by_id = user.id
    check.reviewed_name = user.full_name
    check.reviewed_at = datetime.now(timezone.utc)
    check.review_note = note
    db.commit()
    log_activity(db, action=f"flight_check_{check.status}", actor=user, target_type="flight_check", target_id=check.id,
                 detail=f"{check.tail_number} at {check.station}"[:480], request=request)
    return _out(check, full=True)


@router.get("/{check_id}/history")
def history(check_id: int, db: Session = Depends(get_db), user: User = Depends(require_institution_scope)):
    check = _get(db, user, check_id)
    rows = db.query(ActivityLog).filter(ActivityLog.target_type == "flight_check", ActivityLog.target_id == check.id).order_by(ActivityLog.created_at.desc()).limit(100).all()
    return [{"action": r.action, "by": r.actor_username, "detail": r.detail, "at": r.created_at.isoformat() if r.created_at else None, "ip": r.ip_address} for r in rows]
