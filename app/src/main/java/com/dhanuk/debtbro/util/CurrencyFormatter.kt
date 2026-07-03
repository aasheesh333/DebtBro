package com.dhanuk.debtbro.util

import java.text.NumberFormat
import java.util.Locale

/**
 * Per-currency locale map.
 *
 * Fix history (2026-07-03, offline-mode audit):
 *  Previously the formatter hard-coded `Locale("en", "IN")` for every
 *  currency — a USD debt rendered as "$1,00,000" instead of "$100,000",
 *  a GBP debt rendered as "£1,00,000" instead of "£100,000". Indian
 *  grouping uses lakh/crore (two-digit terminal grouping); EUR/USD/GBP
 *  use three-digit terminal grouping. Mapping each currency symbol to
 *  the right Locale + ISO code now produces locale-correct output for
 *  all six currencies the user can select in `AddDebtBottomSheet` /
 *  `Settings`.
 *
 *  `formatCurrency` falls back to the Indian locale for unknown symbols
 *  (preserves prior behavior for any code path I missed); the new
 *  `formatCurrencyLocale` always uses the proper mapping.
 */
private val CURRENCY_LOCALE_MAP: Map<String, Pair<Locale, String>> = mapOf(
    "₹" to (Locale("en", "IN") to "INR"),
    "$" to (Locale.US to "USD"),
    "€" to (Locale.GERMANY to "EUR"),
    "£" to (Locale.UK to "GBP"),
    "¥" to (Locale.JAPAN to "JPY"),
    "₩" to (Locale.KOREA to "KRW")
)

private fun localeFor(symbol: String): Pair<Locale, String> =
    CURRENCY_LOCALE_MAP[symbol] ?: (Locale("en", "IN") to "INR")

/**
 * Formats [amount] using the digit-grouping and decimal rules of the
 * locale associated with [currency]. Trailing zeros are dropped for
 * whole numbers (so "₹500" not "₹500.00") and two decimals are shown
 * when the amount has fractional currency — matching the historical UX.
 */
fun formatCurrencyLocale(amount: Double, currency: String = "₹"): String {
    val (locale, isoCode) = localeFor(currency)
    val format = NumberFormat.getCurrencyInstance(locale)
    return try {
        format.minimumFractionDigits = if (amount % 1.0 == 0.0) 0 else 2
        format.maximumFractionDigits = if (amount % 1.0 == 0.0) 0 else 2
        try {
            format.currency = java.util.Currency.getInstance(isoCode)
        } catch (_: IllegalArgumentException) {
            // Currency code not recognized on this device — fall back to
            // the number formatter and prepend the symbol manually below.
            return symbolPrefixed(amount, currency, locale)
        }
        // The currency formatter prepends the locale's currency symbol
        // (e.g. "₹", "$", "€", "£", "¥", "₩"); for the user-facing emoji
        // we override with the literal symbol they selected, because on
        // some locales Android emits "US$" or "JP¥" — confusing.
        val formatted = format.format(amount)
        val nativeSymbol = try {
            java.util.Currency.getInstance(isoCode).getSymbol(locale)
        } catch (_: Exception) { null }
        if (nativeSymbol != null && formatted.contains(nativeSymbol)) {
            formatted.replace(nativeSymbol, currency)
        } else {
            // No symbol replacement possible — best-effort: prepend the
            // selected symbol so the user sees the right glyph at all.
            "$currency$formatted"
        }
    } catch (_: Exception) {
        symbolPrefixed(amount, currency, locale)
    }
}

private fun symbolPrefixed(amount: Double, currency: String, locale: Locale): String {
    val nf = NumberFormat.getNumberInstance(locale)
    nf.minimumFractionDigits = if (amount % 1.0 == 0.0) 0 else 2
    nf.maximumFractionDigits = if (amount % 1.0 == 0.0) 0 else 2
    return "$currency${nf.format(amount)}"
}

/** Number-only Indian grouping for legacy call sites; preserved for compat. */
fun formatIndian(amount: Double): String {
    val format = NumberFormat.getNumberInstance(Locale("en", "IN"))
    format.maximumFractionDigits = if (amount % 1.0 == 0.0) 0 else 2
    format.minimumFractionDigits = 0
    return format.format(amount)
}

/**
 * Default-formatter that uses the locale-aware path. The previous
 * implementation hard-coded INR grouping; this alias now routes through
 * [formatCurrencyLocale] so call sites that pass a currency symbol get
 * correct grouping for that symbol (USD → 1,000,000 / INR → 10,00,000).
 */
fun formatCurrency(amount: Double, currency: String = "₹"): String =
    formatCurrencyLocale(amount, currency)

fun Double.toReadableAmount(): String = formatIndian(this)
