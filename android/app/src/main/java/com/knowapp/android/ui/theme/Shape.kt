package com.knowapp.android.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// --radius: 18px, --radius-sm: 12px, and the 999px "pill" used for
// badges/buttons/chips in frontend/css/styles.css.
val CardRadius = RoundedCornerShape(18.dp)
val SmallRadius = RoundedCornerShape(12.dp)
val PillShape = RoundedCornerShape(50)

val KnowShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = SmallRadius,
    medium = CardRadius,
    large = CardRadius,
    extraLarge = RoundedCornerShape(24.dp),
)
