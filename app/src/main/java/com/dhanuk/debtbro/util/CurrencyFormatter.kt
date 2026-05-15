package com.dhanuk.debtbro.util

import java.text.NumberFormat
import java.util.Locale

fun formatIndian(amount: Double): String {
    val format = NumberFormat.getNumberInstance(Locale("en", "IN"))
    format.maximumFractionDigits = if (amount % 1.0 == 0.0) 0 else 2
    format.minimumFractionDigits = 0
    return format.format(amount)
}

fun formatCurrency(amount: Double, currency: String = "₹"): String = "$currency${formatIndian(amount)}"
