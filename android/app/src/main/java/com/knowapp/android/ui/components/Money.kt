package com.knowapp.android.ui.components

import java.math.BigDecimal
import java.text.DecimalFormat

private val kesFormat = DecimalFormat("#,##0.00")

private fun plain(value: BigDecimal): String = kesFormat.format(value.abs())

// Non-breaking space so "KES" never wraps away from its amount; a real minus sign for negatives
fun kes(value: BigDecimal): String = (if (value.signum() < 0) "\u2212" else "") + "KES\u00A0" + plain(value)

fun kes(raw: String?): String = kes(raw?.toBigDecimalOrNull() ?: BigDecimal.ZERO)

fun kesNumber(value: BigDecimal): String = (if (value.signum() < 0) "\u2212" else "") + plain(value)
