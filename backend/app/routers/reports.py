from datetime import datetime

from fastapi import APIRouter, Depends, HTTPException, status
from fastapi.responses import Response
from sqlalchemy.orm import Session

from app.core.database import get_db
from app.core.deps import get_current_user
from app.models.audit import Audit
from app.models.school import School
from app.models.transaction import Transaction, TransactionMethod
from app.models.user import User, UserRole
from app.utils.report_pdf import build_audit_report_pdf, build_statement_pdf

router = APIRouter(prefix="/api/reports", tags=["reports"])


@router.get("/audits/{audit_id}.pdf")
def generate_audit_report(audit_id: int, db: Session = Depends(get_db), current_user: User = Depends(get_current_user)):
    audit = db.query(Audit).filter(Audit.id == audit_id).first()
    if not audit:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Audit not found")
    if current_user.role == UserRole.TEACHER and audit.school_id != current_user.school_id:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Not permitted to access this audit")

    school = db.query(School).filter(School.id == audit.school_id).first()
    # Computed from the audit's period rather than a stored link — see
    # routers/audits.py's /transactions endpoint for the same query.
    transactions = (
        db.query(Transaction)
        .filter(
            Transaction.school_id == audit.school_id,
            Transaction.transaction_date >= audit.period_start,
            Transaction.transaction_date <= audit.period_end,
        )
        .all()
    )

    pdf_bytes = build_audit_report_pdf(school, audit, transactions)

    filename = f"audit-report-{audit.id}.pdf"
    return Response(
        content=pdf_bytes,
        media_type="application/pdf",
        headers={"Content-Disposition": f'inline; filename="{filename}"'},
    )


_METHOD_LABELS = {
    TransactionMethod.CASH: "Cash",
    TransactionMethod.MPESA: "M-Pesa",
    TransactionMethod.BANK: "Bank",
}


@router.get("/statements.pdf")
def generate_statement_report(
    start: datetime,
    end: datetime,
    method: TransactionMethod | None = None,
    school_id: int | None = None,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """
    A printable movement statement for one method (or every method
    combined) over a date range — the Audit page's "Statements" feature.
    """
    if current_user.role == UserRole.TEACHER:
        scoped_school_id = current_user.school_id
    else:
        if school_id is None:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="school_id is required")
        scoped_school_id = school_id

    school = db.query(School).filter(School.id == scoped_school_id).first()
    if not school:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="School not found")

    query = db.query(Transaction).filter(
        Transaction.school_id == scoped_school_id,
        Transaction.transaction_date >= start,
        Transaction.transaction_date <= end,
    )
    if method is not None:
        query = query.filter(Transaction.method == method)
    transactions = query.all()

    method_label = _METHOD_LABELS.get(method, "Combined")
    pdf_bytes = build_statement_pdf(school, method_label, start, end, transactions)

    filename = f"{method_label.lower().replace(' ', '-').replace('-pesa','pesa')}-statement.pdf"
    return Response(
        content=pdf_bytes,
        media_type="application/pdf",
        headers={"Content-Disposition": f'inline; filename="{filename}"'},
    )