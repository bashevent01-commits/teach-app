import re
import unicodedata

_ARTICLES = {"the", "a", "an"}


def _singular(word: str) -> str:
    """Folds a plural to its singular so Good and Goods read the same; short or odd words are left alone."""
    if len(word) <= 3 or any(ch.isdigit() for ch in word):
        return word
    if word.endswith("ies") and len(word) > 4:
        return word[:-3] + "y"
    if word.endswith(("sses", "shes", "ches", "xes", "zes")):
        return word[:-2]
    if word.endswith("oes") and len(word) > 4:
        return word[:-2]
    if word.endswith(("ss", "us", "is")):
        return word
    if word.endswith("s"):
        return word[:-1]
    return word


def tokens(text: str) -> list[str]:
    """Lower-cased words with accents, punctuation, articles and plurals removed."""
    plain = unicodedata.normalize("NFKD", text or "").encode("ascii", "ignore").decode().casefold().replace("&", " and ")
    words = re.sub(r"[^a-z0-9]+", " ", plain).split()
    return [_singular(w) for w in words if w not in _ARTICLES]


def key_of(text: str) -> str:
    return " ".join(tokens(text))


def _distance(a: str, b: str) -> int:
    if abs(len(a) - len(b)) > 2:
        return 3
    prev = list(range(len(b) + 1))
    for i, ca in enumerate(a, 1):
        cur = [i]
        for j, cb in enumerate(b, 1):
            cur.append(min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (ca != cb)))
        prev = cur
    return prev[-1]


def same_thing(a: str, b: str) -> bool:
    """True when two names mean the same: case, spacing, plurals, word order and a small typo do not matter, but sizes and numbers do."""
    ta, tb = tokens(a), tokens(b)
    if not ta or not tb:
        return False
    if sorted(ta) == sorted(tb) or "".join(ta) == "".join(tb):
        return True
    # Sizes such as 400g and 800g are different things, so numbers must agree
    if sorted(t for t in ta if any(c.isdigit() for c in t)) != sorted(t for t in tb if any(c.isdigit() for c in t)):
        return False
    ka, kb = "".join(sorted(ta)), "".join(sorted(tb))
    limit = 1 if min(len(ka), len(kb)) >= 6 else 0
    if min(len(ka), len(kb)) >= 12:
        limit = 2
    return limit > 0 and _distance(ka, kb) <= limit


def find_similar(name: str, existing: list, get=lambda x: x):
    """The first existing entry that means the same as name, or None."""
    for item in existing:
        if same_thing(name, get(item)):
            return item
    return None
