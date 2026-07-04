package com.dhanuk.debtbro.data.network

import retrofit2.Response
import retrofit2.http.GET

/**
 * Open Exchange Rate API (https://open.exchangerate-api.com/v6/latest)
 * — free, no auth, no API key, no rate limit (the upstream provider is
 * an open mirror of exchangerate-api.com's free plan). Used by the
 * in-app currency converter to render Dashboard / Analytics totals in
 * the user's `defaultCurrency` even when individual debts were entered
 * in a different currency.
 *
 * Network config: HTTPS-only; the base URL hard-codes https. The
 * existing `INTERNET` permission (already declared in AndroidManifest
 * for Gemini calls) covers this call.
 *
 * Response fields:
 *   - result: "success" / "error"
 *   - base_code: "USD" (always; the endpoint is USD-quoted)
 *   - rates: Map<String, Double> (ISO code → 1 USD = N units of that
 *     currency; USD = 1.0)
 */
data class FxRateResponse(
    val result: String?,
    val base_code: String?,
    val rates: Map<String, Double>?
)

interface FxApiService {
    /**
     * Fetches the latest USD-quoted FX rate table. Each entry is the
     * rate FROM 1 USD TO the keyed ISO currency (e.g. "INR" -> 83.45
     * means 1 USD = 83.45 INR). The endpoint is `https://` so the URL
     * is fully-qualified — Retrofit's baseUrl is ignored when @GET
     * uses an absolute URL.
     */
    @GET("https://open.exchangerate-api.com/v6/latest")
    suspend fun getLatestRates(): Response<FxRateResponse>
}
