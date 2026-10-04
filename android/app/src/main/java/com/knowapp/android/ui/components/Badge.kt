package com.knowapp.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.knowapp.android.ui.theme.Brand
import com.knowapp.android.ui.theme.BrandSoft
import com.knowapp.android.ui.theme.PillShape

/** Matches .badge: brand-soft background, brand-colored text, pill shape. */
@Composable
fun Badge(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier
            .background(BrandSoft, PillShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        color = Brand,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
    )
}
