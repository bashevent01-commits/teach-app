from decimal import Decimal

from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.models.accounting import Account, AccountType, JournalEntry, JournalLine
from app.models.transaction import Transaction, TransactionType, TransactionMethod, TransactionCategoryType

# Fixed chart of accounts every institution gets; per-category income/expense accounts are added on demand
SYSTEM_ACCOUNTS = {
    "cash": (1000, "Cash", AccountType.ASSET),
    "mpesa": (1010, "M-Pesa", AccountType.ASSET),
    "bank": (1020, "Bank", AccountType.ASSET),
    "equity": (3000, "Owner's equity", AccountType.EQUITY),
    "sales": (4000, "Sales", AccountType.INCOME),
    "purchases": (5000, "Stock purchases", AccountType.EXPENSE),
}

# Code ranges for accounts created from free-text categories
_CATEGORY_RANGES = {AccountType.INCOME: (4100, 4999), AccountType.EXPENSE: (5100, 5999)}

_METHOD_KEY = {
    TransactionMethod.CASH: "cash",
    TransactionMethod.MPESA: "mpesa",
    TransactionMethod.BANK: "bank",
}


def _get_or_create(db: Session, institution_id: int, key: str, name: str, type_: AccountType, code: int | None) -> Account:
    account = db.query(Account).filter(Account.institution_id == institution_id, Account.key == key).first()
    if account:
        return account
    if code is None:
        low, high = _CATEGORY_RANGES[type_]
        used = [c for (c,) in db.query(Account.code).filter(Account.institution_id == institution_id, Account.code >= low, Account.code <= high).all()]
        code = (max(used) + 1) if used else low
        if code > high:
            # Range exhausted: fall back to the shared catch-all rather than failing a transaction
            return _get_or_create(db, institution_id, "inc:other" if type_ == AccountType.INCOME else "exp:other", "Other income" if type_ == AccountType.INCOME else "Other expenses", type_, low if type_ == AccountType.INCOME else high)
    try:
        with db.begin_nested():
            account = Account(institution_id=institution_id, key=key, code=code, name=name, type=type_)
            db.add(account)
            db.flush()
        return account
    except IntegrityError:
        return db.query(Account).filter(Account.institution_id == institution_id, Account.key == key).one()


def system_account(db: Session, institution_id: int, key: str) -> Account:
    code, name, type_ = SYSTEM_ACCOUNTS[key]
    return _get_or_create(db, institution_id, key, name, type_, code)


def ensure_chart(db: Session, institution_id: int) -> None:
    for key in SYSTEM_ACCOUNTS:
        system_account(db, institution_id, key)


def _category_account(db: Session, institution_id: int, txn: Transaction) -> Account:
    if txn.category_type == TransactionCategoryType.STOCK:
        return system_account(db, institution_id, "sales" if txn.type == TransactionType.INCOME else "purchases")
    name = (txn.category or "Uncategorised").strip()[:120] or "Uncategorised"
    is_income = txn.type == TransactionType.INCOME
    prefix = "inc:" if is_income else "exp:"
    return _get_or_create(db, institution_id, (prefix + name.lower())[:120], name, AccountType.INCOME if is_income else AccountType.EXPENSE, None)


def post_transaction(db: Session, txn: Transaction) -> JournalEntry:
    # Receiving: money account up (debit), income up (credit). Paying: expense up (debit), money account down (credit).
    # Replaces any existing entry so edits to a transaction keep the books in step.
    if txn.journal_entry is not None:
        db.delete(txn.journal_entry)
        db.flush()
        txn.journal_entry = None

    amount = Decimal(txn.amount)
    money = system_account(db, txn.institution_id, _METHOD_KEY[txn.method])
    other = _category_account(db, txn.institution_id, txn)
    if txn.type == TransactionType.INCOME:
        debit_account, credit_account = money, other
    else:
        debit_account, credit_account = other, money

    entry = JournalEntry(
        institution_id=txn.institution_id,
        transaction_id=txn.id,
        entry_date=txn.transaction_date,
        memo=txn.description or txn.category,
    )
    entry.lines.append(JournalLine(account_id=debit_account.id, debit=amount, credit=Decimal("0")))
    entry.lines.append(JournalLine(account_id=credit_account.id, debit=Decimal("0"), credit=amount))
    db.add(entry)
    db.flush()
    return entry


def backfill_institution(db: Session, institution_id: int) -> int:
    # Posts any transaction that has no journal entry yet (history from before the ledger existed)
    ensure_chart(db, institution_id)
    missing = (
        db.query(Transaction)
        .outerjoin(JournalEntry, JournalEntry.transaction_id == Transaction.id)
        .filter(Transaction.institution_id == institution_id, JournalEntry.id.is_(None))
        .order_by(Transaction.id)
        .all()
    )
    for txn in missing:
        post_transaction(db, txn)
    if missing:
        db.commit()
    return len(missing)
