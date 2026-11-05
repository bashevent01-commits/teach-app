import calendar
from datetime import date, datetime, timedelta

from app.utils.billing import LOCAL

PERIODS = ("weekly", "monthly", "quarterly", "yearly")
_MONTHS = {"monthly": 1, "quarterly": 3, "yearly": 12}


def today_local() -> date:
    return datetime.now(LOCAL).date()


def add_period(start: date, period: str) -> date:
    """The date one subscription period after start."""
    if period == "weekly":
        return start + timedelta(days=7)
    index = start.year * 12 + (start.month - 1) + _MONTHS.get(period, 1)
    year, month = divmod(index, 12)
    month += 1
    return start.replace(year=year, month=month, day=min(start.day, calendar.monthrange(year, month)[1]))


def advance(next_due: date, today: date, period: str) -> date:
    """Paying on time or early runs on from the old due date; paying late starts again from today."""
    return add_period(next_due if next_due >= today else today, period)


def status_of(is_active: bool, next_due: date, today: date) -> tuple[str, int]:
    days = (next_due - today).days
    if not is_active:
        return "paused", days
    if days < 0:
        return "overdue", days
    if days == 0:
        return "due_today", days
    if days == 1:
        return "due_tomorrow", days
    return "active", days
