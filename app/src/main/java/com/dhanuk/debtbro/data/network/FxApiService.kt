package com.dhanuk.debtbro.data.network

import retrofit2.Response
import retrofit2.http.GET

/**
 * Open ER-API (https://open.er-api.com/v1/latest) — free, no auth, no
 * rate limit posted. Returns USD-based FX rates updated daily (the
 * upstream provider is an open mirror of Open Exchange Rates' free
 * plan; that's why no API key is required). Used by the in-app currency
 * converter to render Dashboard / Analytics totals in the user's
 * `defaultCurrency` even when individual debts were entered in a
 * different currency.
 *
 * Network config: `open.er-api.com` is allow-listed in
 * `res/xml/network_security_config.xml` (HTTPS-only). No additions
 * needed there — `cleartextTrafficPermitted="false"` is the global
 * rule and HTTPS is enforced by the base URL.
 */
data class FxRateResponse(
    val result: String?,
    val provider: String?,
    val base: String?,
    val date: String?,
    val time_last_update_unix: Long?,
    val rates: Map<String, Double>?
)

interface FxApiService {
    /**
     * Fetches the latest USD-quoted FX rate table. Each entry is the
     * rate FROM 1 USD TO the keyed ISO currency (e.g. "INR" -> 83.45
     * means 1 USD = 83.45 INR).
     */
    @GET("https://open.er-api.com/v1/latest/quotes/USD.json")
    suspend fun getLatestRates(): Response<FxRateResponse>
}
