from decimal import Decimal

from pydantic import BaseModel


class CategoryInsightOut(BaseModel):
    category_id: int
    category_name: str
    institution_count: int
    average_price: float
    median_price: float
    min_price: float
    max_price: float
    total_quantity_sold: Decimal


class RegionBreakdownEntry(BaseModel):
    region: str
    institution_count: int
    average_price: float
    median_price: float
    min_price: float
    max_price: float


class TrendPointOut(BaseModel):
    month: str  # "YYYY-MM"
    average_price: float
    institution_count: int


class CategoryDetailOut(BaseModel):
    category_id: int
    category_name: str
    institution_count: int
    average_price: float
    median_price: float
    min_price: float
    max_price: float
    total_quantity_sold: Decimal
    regional_breakdown: list[RegionBreakdownEntry]
    trend: list[TrendPointOut]
