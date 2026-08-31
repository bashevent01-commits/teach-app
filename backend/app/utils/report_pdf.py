"""
Builds PDF reports: the signed-off Audit report (title/period), and the
Statements feature (Cash / M-Pesa / Bank / Combined movement statements).

Both use the same grouped, coloured-band table layout: single-method
statements are split into an Income band and an Expenses band (each
with its own subtotal, like a standard income statement); the Combined
statement bands by payment method instead, so consecutive entries for
the same method always read as one solid coloured block rather than
being interleaved by date. Every row's optional evidence photo is
embedded inline, in a small bordered box.
"""
import io
from datetime import datetime
from decimal import Decimal
from pathlib import Path

from reportlab.lib import colors
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.units import mm
from reportlab.platypus import (
    SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle, Image as RLImage,
)

from app.models.audit import Audit
from app.models.school import School
from app.models.transaction import Transaction, TransactionType, TransactionMethod

INCOME_COLOR = colors.HexColor("#0f766e")
EXPENSE_COLOR = colors.HexColor("#b45309")
METHOD_COLOR = {
    TransactionMethod.CASH: colors.HexColor("#0f766e"),
    TransactionMethod.MPESA: colors.HexColor("#15803d"),
    TransactionMethod.BANK: colors.HexColor("#1d4ed8"),
}
METHOD_LABEL = {
    TransactionMethod.CASH: "Cash",
    TransactionMethod.MPESA: "M-Pesa",
    TransactionMethod.BANK: "Bank",
}
GRAND_NET_COLOR = colors.HexColor("#1f2937")
SUBTOTAL_BG = colors.HexColor("#f3f4f6")

_COL_WIDTHS = [20 * mm, 30 * mm, None, 20 * mm, 24 * mm]


def _money(value) -> str:
    return f"{Decimal(value):,.2f}"


def _evidence_cell(txn: Transaction):
    """A small bordered thumbnail if the row has an evidence photo, else a dash."""
    if txn.image_path and Path(txn.image_path).exists():
        try:
            img = RLImage(txn.image_path, width=14 * mm, height=14 * mm)
            cell = Table([[img]], colWidths=[16 * mm], rowHeights=[16 * mm])
            cell.setStyle(TableStyle([
                ("ALIGN", (0, 0), (-1, -1), "CENTER"),
                ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
                ("BOX", (0, 0), (-1, -1), 1.1, colors.HexColor("#9ca3af")),
                ("TOPPADDING", (0, 0), (-1, -1), 1),
                ("BOTTOMPADDING", (0, 0), (-1, -1), 1),
            ]))
            return cell
        except Exception:
            pass
    return Paragraph("—", ParagraphStyle("dash", fontSize=9, textColor=colors.HexColor("#bbbbbb"), alignment=1))


def _data_row(txn: Transaction, body_style, signed: bool = False):
    amount = Decimal(txn.amount)
    if signed:
        sign = "+" if txn.type == TransactionType.INCOME else "\u2212"
        amount_text = f"{sign} {_money(amount)}"
    else:
        amount_text = _money(amount)
    return [
        txn.transaction_date.strftime("%d %b %Y"),
        Paragraph(txn.category, body_style),
        Paragraph(txn.description or "-", body_style),
        _evidence_cell(txn),
        amount_text,
    ]


def _band_header_row(title: str):
    return [title, "", "", "", ""]


def _subtotal_row(label: str, value: Decimal):
    return [label, "", "", "", _money(value)]


def _build_statement_table(transactions: list[Transaction], body_style, combined: bool):
    """
    Returns (table_data, style_commands) — row-by-row data plus the
    TableStyle commands needed to colour the band/subtotal rows and
    span their labels across the row, since band + subtotal rows are
    assembled dynamically and their row indices aren't known up front.
    """
    header = ["Date", "Category", "Description", "Evidence", "Amount"]
    data = [header]
    style = [
        ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#1f2937")),
        ("TEXTCOLOR", (0, 0), (-1, 0), colors.white),
        ("FONTSIZE", (0, 0), (-1, -1), 9),
        ("ALIGN", (-1, 0), (-1, -1), "RIGHT"),
        ("ALIGN", (3, 0), (3, -1), "CENTER"),
        ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
        ("GRID", (0, 0), (-1, -1), 0.4, colors.HexColor("#e5e7eb")),
        ("TOPPADDING", (0, 0), (-1, -1), 5),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 5),
    ]

    def add_band(title: str, color, rows: list[Transaction], signed: bool):
        nonlocal style
        if not rows:
            return Decimal("0")
        r = len(data)
        data.append(_band_header_row(title))
        style += [
            ("BACKGROUND", (0, r), (-1, r), color),
            ("TEXTCOLOR", (0, r), (-1, r), colors.white),
            ("SPAN", (0, r), (-1, r)),
            ("FONTSIZE", (0, r), (-1, r), 10),
            ("FONTNAME", (0, r), (-1, r), "Helvetica-Bold"),
        ]
        for t in rows:
            data.append(_data_row(t, body_style, signed=signed))
        subtotal = sum((Decimal(t.amount) if t.type == TransactionType.INCOME else -Decimal(t.amount)) for t in rows) if signed \
            else sum(Decimal(t.amount) for t in rows)
        sr = len(data)
        data.append(_subtotal_row(f"{title} total" if signed else f"Total {title.lower()}", subtotal))
        style += [
            ("BACKGROUND", (0, sr), (-1, sr), SUBTOTAL_BG),
            ("SPAN", (0, sr), (3, sr)),
            ("FONTNAME", (0, sr), (-1, sr), "Helvetica-Bold"),
            ("LINEABOVE", (0, sr), (-1, sr), 0.75, colors.HexColor("#333333")),
        ]
        return subtotal

    if not combined:
        income = [t for t in transactions if t.type == TransactionType.INCOME]
        expense = [t for t in transactions if t.type == TransactionType.EXPENSE]
        inc_total = add_band("Income", INCOME_COLOR, income, signed=False)
        exp_total = add_band("Expenses", EXPENSE_COLOR, expense, signed=False)
        net = inc_total - exp_total
        nr = len(data)
        data.append(_subtotal_row("Net", net))
        style += [
            ("BACKGROUND", (0, nr), (-1, nr), INCOME_COLOR),
            ("TEXTCOLOR", (0, nr), (-1, nr), colors.white),
            ("SPAN", (0, nr), (3, nr)),
            ("FONTNAME", (0, nr), (-1, nr), "Helvetica-Bold"),
            ("FONTSIZE", (0, nr), (-1, nr), 10.5),
        ]
    else:
        grand_net = Decimal("0")
        for method in (TransactionMethod.CASH, TransactionMethod.MPESA, TransactionMethod.BANK):
            method_rows = [t for t in transactions if t.method == method]
            net = add_band(METHOD_LABEL[method], METHOD_COLOR[method], method_rows, signed=True)
            grand_net += net
        nr = len(data)
        data.append(_subtotal_row("Grand net", grand_net))
        style += [
            ("BACKGROUND", (0, nr), (-1, nr), GRAND_NET_COLOR),
            ("TEXTCOLOR", (0, nr), (-1, nr), colors.white),
            ("SPAN", (0, nr), (3, nr)),
            ("FONTNAME", (0, nr), (-1, nr), "Helvetica-Bold"),
            ("FONTSIZE", (0, nr), (-1, nr), 10.5),
        ]

    if len(data) == 1:
        data.append(["No transactions in this range.", "", "", "", ""])
        style += [("SPAN", (0, 1), (-1, 1)), ("ALIGN", (0, 1), (-1, 1), "CENTER"), ("TEXTCOLOR", (0, 1), (-1, 1), colors.HexColor("#999999"))]

    return data, style


def _header_block(story, styles, school: School, title: str, meta_line: str):
    title_style = ParagraphStyle("ReportTitle", parent=styles["Title"], fontSize=18, spaceAfter=4)
    school_style = ParagraphStyle("SchoolName", parent=styles["Normal"], fontSize=13, textColor=colors.HexColor("#1a1a1a"))
    meta_style = ParagraphStyle("Meta", parent=styles["Normal"], fontSize=9, textColor=colors.HexColor("#555555"))

    logo_cell = ""
    if school.logo_path and Path(school.logo_path).exists():
        try:
            logo_cell = RLImage(school.logo_path, width=22 * mm, height=22 * mm)
        except Exception:
            logo_cell = ""

    identity_block = [Paragraph(title, title_style), Paragraph(school.name, school_style), Paragraph(meta_line, meta_style)]
    header_table = Table([[logo_cell, identity_block]], colWidths=[26 * mm, None])
    header_table.setStyle(TableStyle([
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("LEFTPADDING", (0, 0), (0, 0), 0),
        ("RIGHTPADDING", (0, 0), (0, 0), 8),
    ]))
    story.append(header_table)
    story.append(Spacer(1, 4 * mm))
    story.append(Table([[""]], colWidths=[170 * mm], style=TableStyle([
        ("LINEBELOW", (0, 0), (-1, -1), 0.75, colors.HexColor("#cccccc")),
    ])))
    story.append(Spacer(1, 6 * mm))


def build_audit_report_pdf(school: School, audit: Audit, transactions: list[Transaction]) -> bytes:
    buffer = io.BytesIO()
    doc = SimpleDocTemplate(buffer, pagesize=A4, topMargin=18 * mm, bottomMargin=18 * mm, leftMargin=18 * mm, rightMargin=18 * mm)
    styles = getSampleStyleSheet()
    body_style = ParagraphStyle("Body", parent=styles["Normal"], fontSize=9, leading=12)

    story = []
    period = f"{audit.period_start.strftime('%d %b %Y')} &ndash; {audit.period_end.strftime('%d %b %Y')}"
    _header_block(story, styles, school, audit.title, f"{period} &middot; {audit.status.value.title()}")

    if audit.summary:
        story.append(Paragraph(audit.summary, body_style))
        story.append(Spacer(1, 5 * mm))

    data, style_cmds = _build_statement_table(transactions, body_style, combined=True)
    table = Table(data, colWidths=_COL_WIDTHS, repeatRows=1)
    table.setStyle(TableStyle(style_cmds))
    story.append(table)

    doc.build(story)
    return buffer.getvalue()


def build_statement_pdf(school: School, method_label: str, start: datetime, end: datetime, transactions: list[Transaction]) -> bytes:
    buffer = io.BytesIO()
    doc = SimpleDocTemplate(buffer, pagesize=A4, topMargin=18 * mm, bottomMargin=18 * mm, leftMargin=18 * mm, rightMargin=18 * mm)
    styles = getSampleStyleSheet()
    body_style = ParagraphStyle("Body", parent=styles["Normal"], fontSize=9, leading=12)

    story = []
    title = "Combined Statement" if method_label == "Combined" else f"{method_label} Movement Statement"
    meta = f"{start.strftime('%d %b %Y')} &ndash; {end.strftime('%d %b %Y')}"
    _header_block(story, styles, school, title, meta)

    data, style_cmds = _build_statement_table(transactions, body_style, combined=(method_label == "Combined"))
    table = Table(data, colWidths=_COL_WIDTHS, repeatRows=1)
    table.setStyle(TableStyle(style_cmds))
    story.append(table)

    doc.build(story)
    return buffer.getvalue()