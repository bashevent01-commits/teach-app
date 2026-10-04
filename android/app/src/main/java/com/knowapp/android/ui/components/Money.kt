package com.knowapp.android.ui.components

import java.math.BigDecimal
import java.text.DecimalFormat

private val kesFormat = DecimalFormat("#,##0.00")

// Non-breaking space so "KES" never wraps away from its amount
fun kes(value: BigDecimal): String = "KES\u00A0${kesFormat.format(value)}"

fun kes(raw: String?): String = kes(raw?.toBigDecimalOrNull() ?: BigDecimal.ZERO)

fun kesNumber(value: BigDecimal): String = kesFormat.format(value)
