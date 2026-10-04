package com.knowapp.android.ui.components

import java.math.BigDecimal
import java.text.DecimalFormat

private val kesFormat = DecimalFormat("#,##0.00")

fun kes(value: BigDecimal): String = "KES ${kesFormat.format(value)}"

fun kes(raw: String?): String = kes(raw?.toBigDecimalOrNull() ?: BigDecimal.ZERO)
