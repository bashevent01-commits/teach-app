package com.knowapp.android.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.knowapp.android.ui.theme.DisplayFontFamily
import com.knowapp.android.ui.theme.ExpenseOrange
import com.knowapp.android.ui.theme.IncomeGreen

/** Matches .txn-amount styling + the web app's --in (green) / --out (orange) tokens. */
@Composable
fun AmountText(amountKes: String, isIncome: Boolean, modifier: Modifier = Modifier) {
    Text(
        text = "${if (isIncome) "+" else "-"} KES $amountKes",
        modifier = modifier,
        color = if (isIncome) IncomeGreen else ExpenseOrange,
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.bodyLarge,
    )
}
