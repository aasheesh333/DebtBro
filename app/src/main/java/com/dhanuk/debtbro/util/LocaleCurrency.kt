package com.dhanuk.debtbro.util

import android.content.res.Resources
import java.util.Currency
import java.util.Locale

/**
 * Infers the user's default currency symbol from the device locale.
 *
 * The app never overrides the Android system locale (verified: zero
 * `attachBaseContext` / `updateConfiguration` calls anywhere in the
 * codebase), so `Locale.getDefault()` always reflects the device's
 * actual configured locale — NOT the in-app `selectedLanguage` choice
 * (which only flips an in-memory Compose state in `LocalizedString`).
 *
 * That's the behavior we want: an Indian user who picked English UI
 * in-app still resolves to `en_IN` → `₹`; a US-user gets `$`; a German
 * user gets `€`. The map restricts resolution to the 6 currencies
 * AddDebtBottomSheet / Settings know about; everything else falls back
 * to `₹` (the historical default of this app before this feature
 * existed — preserves existing installs' UX).
 */
private val SUPPORTED_SYMBOLS: Set<String> = setOf("₹", "$", "€", "£", "¥", "₩")

private val ISO_TO_SYMBOL: Map<String, String> = mapOf(
    "INR" to "₹",
    "USD" to "$",
    "EUR" to "€",
    "GBP" to "£",
    "JPY" to "¥",
    "KRW" to "₩"
)

fun autoCurrencyForLocale(): String {
    return try {
        // `Resources.getSystem().configuration.locales[0]` is the same
        // Locale the system used to format this process — Play-approved
        // way to read the device's locale. (LocaleList.size >= 1 in
        // practice; the safety `takeIf` guards the empty-list edge case.)
        @Suppress("DEPRECATION")
        val localeList = Resources.getSystem().configuration.locales
        val locale = if (localeList.isEmpty) Locale.getDefault() else localeList[0]
        val iso = try {
            Currency.getInstance(locale).currencyCode
        } catch (_: IllegalArgumentException) {
            // Some locales (e.g. "en" without a country) have no ISO
            // currency mapping — fall back to "₹" (this app's legacy
            // default for unrecognized locales).
            return "₹"
        }
        ISO_TO_SYMBOL[iso] ?: "₹"
    } catch (_: Throwable) {
        "₹"
    }
}

/** True if the symbol is one the chip-rows know how to format. */
fun isSupportedCurrencySymbol(symbol: String): Boolean = symbol in SUPPORTED_SYMBOLS
