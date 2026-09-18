package com.knowapp.android.ui.theme

import androidx.compose.ui.graphics.Color

// Pulled directly from frontend/css/styles.css's :root token block — keep
// these two files in sync if the web app's palette ever changes.

// Brand
val Brand = Color(0xFF0F766E)
val BrandSoft = Color(0x1F0F766E) // rgba(15,118,110,0.12)
val BrandInk = Color(0xFFFFFFFF)

// Light surfaces
val BgLight = Color(0xFFF5F7F6)
val SurfaceLight = Color(0xFFFFFFFF)
val Surface2Light = Color(0xFFF0F3F2)
val InkLight = Color(0xFF10201D)
val InkSoftLight = Color(0xFF5D6F6B)
val LineLight = Color(0xFFE2E8E6)

// Dark surfaces
val BgDark = Color(0xFF0B1413)
val SurfaceDark = Color(0xFF111E1C)
val Surface2Dark = Color(0xFF172624)
val InkDark = Color(0xFFEAF3F1)
val InkSoftDark = Color(0xFF94A9A5)
val LineDark = Color(0xFF21322F)

// Semantic (transaction income/expense + status)
val IncomeGreen = Color(0xFF16A34A)
val IncomeGreenSoft = Color(0x1F16A34A) // rgba(22,163,74,0.12)
val ExpenseOrange = Color(0xFFEA7317)
val ExpenseOrangeSoft = Color(0x1FEA7317) // rgba(234,115,23,0.12)
val WarnAmber = Color(0xFFD97706)
val DangerRed = Color(0xFFDC2626)
