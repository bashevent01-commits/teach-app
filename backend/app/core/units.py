import re

from app.utils.names import key_of

# Units of measure shops commonly use; a business can also type its own
UNITS = [
    ("pieces", "Pieces"), ("kg", "Kilograms (kg)"), ("g", "Grams (g)"), ("litres", "Litres (L)"), ("ml", "Millilitres (ml)"),
    ("dozen", "Dozen"), ("packets", "Packets"), ("bottles", "Bottles"), ("cartons", "Cartons"), ("boxes", "Boxes"),
    ("bags", "Bags"), ("sacks", "Sacks"), ("crates", "Crates"), ("trays", "Trays"), ("bundles", "Bundles"),
    ("pairs", "Pairs"), ("metres", "Metres (m)"), ("rolls", "Rolls"), ("tins", "Tins"), ("sachets", "Sachets"),
    ("plates", "Plates"), ("cups", "Cups"), ("loaves", "Loaves"), ("bales", "Bales"), ("gallons", "Gallons"), ("tonnes", "Tonnes"),
]
DEFAULT_UNIT = "pieces"

_ALIASES = {
    "pieces": ["piece", "pc", "pcs", "unit", "each", "item", "no", "nos", "number"],
    "kg": ["kilo", "kilogram", "kgs", "kilograms", "kilos"],
    "g": ["gram", "gm", "gms", "grams"],
    "litres": ["litre", "liter", "liters", "ltr", "ltrs", "lt", "l"],
    "ml": ["millilitre", "milliliter", "mls", "millilitres", "milliliters"],
    "dozen": ["doz", "dozens"],
    "packets": ["packet", "pkt", "pkts", "pack", "packs", "paket"],
    "bottles": ["bottle", "btl", "btls"],
    "cartons": ["carton", "ctn", "ctns"],
    "boxes": ["box"],
    "bags": ["bag"],
    "sacks": ["sack", "gunia", "gunias"],
    "crates": ["crate"],
    "trays": ["tray"],
    "bundles": ["bundle", "bunch", "bunches"],
    "pairs": ["pair", "pr"],
    "metres": ["metre", "meter", "meters", "m", "mtr"],
    "rolls": ["roll"],
    "tins": ["tin", "can", "cans"],
    "sachets": ["sachet"],
    "plates": ["plate"],
    "cups": ["cup"],
    "loaves": ["loaf"],
    "bales": ["bale"],
    "gallons": ["gallon", "gal"],
    "tonnes": ["tonne", "ton", "tons"],
}
_LOOKUP = {}
for _key, _label in UNITS:
    for _word in [_key, _label.split(" (")[0], *_ALIASES.get(_key, [])]:
        _LOOKUP[key_of(_word)] = _key
        _LOOKUP[_word.casefold()] = _key


def normalize_unit(text: str | None) -> str:
    """Maps what someone typed (Kgs, kilo, PCS) to one of the standard units, or keeps a short custom unit tidied up."""
    raw = (text or "").strip()
    if not raw:
        return DEFAULT_UNIT
    found = _LOOKUP.get(raw.casefold()) or _LOOKUP.get(key_of(raw))
    if found:
        return found
    custom = re.sub(r"[^a-z0-9 .'/-]", "", " ".join(raw.casefold().split()))[:20].strip()
    return custom or DEFAULT_UNIT
