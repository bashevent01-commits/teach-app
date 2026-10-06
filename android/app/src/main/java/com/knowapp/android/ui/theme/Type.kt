package com.knowapp.android.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import com.knowapp.android.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// The website's own fonts: Plus Jakarta Sans for text, Space Grotesk for headings and big numbers
@OptIn(ExperimentalTextApi::class)
private fun variable(resId: Int, weight: FontWeight) = Font(
    resId = resId,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val BodyFontFamily = FontFamily(
    variable(R.font.plus_jakarta_sans, FontWeight.Normal),
    variable(R.font.plus_jakarta_sans, FontWeight.Medium),
    variable(R.font.plus_jakarta_sans, FontWeight.SemiBold),
    variable(R.font.plus_jakarta_sans, FontWeight.Bold),
    variable(R.font.plus_jakarta_sans, FontWeight.ExtraBold),
)

val DisplayFontFamily = FontFamily(
    variable(R.font.space_grotesk, FontWeight.Medium),
    variable(R.font.space_grotesk, FontWeight.SemiBold),
    variable(R.font.space_grotesk, FontWeight.Bold),
)

val KnowTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        letterSpacing = (-0.02).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        letterSpacing = (-0.02).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
    ),
)
