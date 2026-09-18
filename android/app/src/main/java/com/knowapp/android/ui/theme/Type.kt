package com.knowapp.android.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// The web app uses --font: "Plus Jakarta Sans" (body) and --display:
// "Space Grotesk" (h1/h2/h3, greeting, amounts) — see styles.css's :root.
//
// These default to FontFamily.Default so the project builds and runs with
// zero setup. To match the web app exactly:
//   1. In Android Studio: Resource Manager (View > Tool Windows) > "+" >
//      "Font" > search "Plus Jakarta Sans" > pick weights 400/500/600/700
//      > "Add font to project". Repeat for "Space Grotesk" (weights 500/600/700).
//   2. Studio generates res/font/*.xml and downloadable-font resources for you.
//   3. Uncomment the two FontFamily blocks below and point BodyFontFamily /
//      DisplayFontFamily at them instead of FontFamily.Default.
// Doing it via the wizard (rather than hand-writing font resource XML) avoids
// any risk of an invalid downloadable-font certificate reference.

// import com.knowapp.android.R
// private val PlusJakartaSans = FontFamily(
//     Font(R.font.plus_jakarta_sans, FontWeight.Normal),
//     Font(R.font.plus_jakarta_sans_medium, FontWeight.Medium),
//     Font(R.font.plus_jakarta_sans_semibold, FontWeight.SemiBold),
//     Font(R.font.plus_jakarta_sans_bold, FontWeight.Bold),
// )
// private val SpaceGrotesk = FontFamily(
//     Font(R.font.space_grotesk_medium, FontWeight.Medium),
//     Font(R.font.space_grotesk_semibold, FontWeight.SemiBold),
//     Font(R.font.space_grotesk_bold, FontWeight.Bold),
// )

val BodyFontFamily = FontFamily.Default // swap for PlusJakartaSans once added
val DisplayFontFamily = FontFamily.Default // swap for SpaceGrotesk once added

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
