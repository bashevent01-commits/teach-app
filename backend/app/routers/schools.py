from fastapi import APIRouter, Depends, HTTPException, Request, UploadFile, File, status
from fastapi.responses import FileResponse
from sqlalchemy.orm import Session

from app.core.activity_log import log_activity
from app.core.database import get_db
from app.core.deps import require_super_admin, get_current_user
from app.models.school import School
from app.models.user import User
from app.schemas.school import SchoolCreate, SchoolOut
from app.utils.uploads import save_school_logo

router = APIRouter(prefix="/api/schools", tags=["schools"])


@router.post("", response_model=SchoolOut, status_code=status.HTTP_201_CREATED)
def create_school(payload: SchoolCreate, request: Request, db: Session = Depends(get_db), admin: User = Depends(require_super_admin)):
    if db.query(School).filter(School.name == payload.name).first():
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="A school with this name already exists")
    school = School(name=payload.name, address=payload.address)
    db.add(school)
    db.commit()
    db.refresh(school)
    log_activity(db, action="school_created", actor=admin, target_type="school", target_id=school.id,
                 detail=f"Created school '{school.name}'", request=request)
    return school


@router.get("", response_model=list[SchoolOut])
def list_schools(db: Session = Depends(get_db), _: User = Depends(get_current_user)):
    return db.query(School).order_by(School.name).all()


@router.get("/{school_id}", response_model=SchoolOut)
def get_school(school_id: int, db: Session = Depends(get_db), _: User = Depends(get_current_user)):
    school = db.query(School).filter(School.id == school_id).first()
    if not school:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="School not found")
    return school


@router.post("/{school_id}/logo", response_model=SchoolOut)
def upload_school_logo(
    school_id: int,
    request: Request,
    file: UploadFile = File(...),
    db: Session = Depends(get_db),
    admin: User = Depends(require_super_admin),
):
    """
    Sets the icon shown across the portal (and later, on generated reports)
    for this school. Only a super admin can change a school's icon.
    """
    school = db.query(School).filter(School.id == school_id).first()
    if not school:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="School not found")

    contents = file.file.read()
    path = save_school_logo(file, contents)
    school.logo_path = path
    db.commit()
    db.refresh(school)
    log_activity(db, action="school_logo_updated", actor=admin, target_type="school", target_id=school.id,
                 detail=f"Updated icon for '{school.name}'", request=request)
    return school


@router.get("/{school_id}/logo")
def get_school_logo(school_id: int, db: Session = Depends(get_db), _: User = Depends(get_current_user)):
    school = db.query(School).filter(School.id == school_id).first()
    if not school or not school.logo_path:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="No logo set for this school")
    return FileResponse(school.logo_path)
