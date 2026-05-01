package com.dhanuk.debtbro.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun Long.toReadableDate(): String = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(this))
fun Long.daysAgo(): Int = ((System.currentTimeMillis() - this) / 86400000).toInt()
fun Long.daysUntil(): Int = ((this - System.currentTimeMillis()) / 86400000).toInt()
fun Long.toTimeAgo(): String {
    val diff = System.currentTimeMillis() - this
    return when {
        diff < 60000 -> "just now"
        diff < 3600000 -> "${diff / 60000}m ago"
        diff < 86400000 -> "${diff / 3600000}h ago"
        else -> "${diff / 86400000}d ago"
    }
}
