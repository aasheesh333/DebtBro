package com.dhanuk.debtbro.data.repository

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.dhanuk.debtbro.data.datastore.dataStore
import com.dhanuk.debtbro.data.network.FxApiService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Caches the latest USD-quoted FX rate table in DataStore and exposes a
 * synchronous-looking `convert()` for the Dashboard / Analytics
 * ViewModels. The fetch is fire-and-forget on app start; if the network
 * is unavailable or has never succeeded, `convert()` returns the
 * original amount untouched (i.e. totals fall back to the pre-feature
 * behavior of naive summation, never 0.0 or NaN).
 *
 * Cache policy: refresh at most once every 12 hours, OR when the
 * in-memory cache is empty on first launch / after process death.
 * Successful responses overwrite all cached rate keys atomically.
 *
 * The rate table is persisted to DataStore (not just in memory) so
 * cold-started ViewModels (e.g. DashboardViewModel constructed before
 * the refresh completes on app launch) can still convert totals using
 * the last-known-good rates instead of showing unconverted sums for
 * the first ~500ms after a cold launch.
 */
@Singleton
class CurrencyRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: FxApiService
) {
    private val _rates = MutableStateFlow<Map<String, Double>>(emptyMap())
    val rates: StateFlow<Map<String, Double>> = _rates.asStateFlow()

    private val refreshMutex = Mutex()

    suspend fun refreshIfNeeded(force: Boolean = false) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        // Read-once prefs snapshot. `Flow.first { true }` returns the
        // first emitted Preferences object — equivalent to a sync get
        // on a StateFlow-backed prefs DataStore. We use the import-aliased
        // extension function rather than the top-level `kotlinx.coroutines
        // .flow.first(flow)` form so the call reads naturally.
        val snapshot = context.dataStore.data.first { true }
        val lastTs = snapshot[Keys.FX_LAST_UPDATED] ?: 0L
        // Rehydrate in-memory cache from DataStore when empty (cold start).
        if (_rates.value.isEmpty()) {
            _rates.value = snapshot.asMap()
                .mapNotNull { (key, value) ->
                    val name = key.name
                    if (name.startsWith("fx_rate_") && value is Double && value > 0.0) {
                        name.removePrefix("fx_rate_") to value
                    } else null
                }.toMap()
        }
        if (!force && now - lastTs < TWELVE_HOURS_MS && _rates.value.isNotEmpty()) {
            return@withContext
        }
        refreshMutex.withLock {
            if (!force && _rates.value.isNotEmpty() && now - lastTs < TWELVE_HOURS_MS) return@withLock
            runCatching {
                val resp = api.getLatestRates()
                if (resp.isSuccessful) {
                    val body = resp.body() ?: return@runCatching
                    val r = body.rates ?: emptyMap()
                    if (r.isNotEmpty()) {
                        context.dataStore.edit { prefs ->
                            // Wipe old keys first so the table never
                            // accumulates stale entries when a currency
                            // disappears from the upstream response.
                            prefs.keys.filter { it.name.startsWith("fx_rate_") }.forEach { prefs.remove(it) }
                            r.forEach { (iso, rate) ->
                                prefs[doublePreferencesKey("fx_rate_$iso")] = rate
                            }
                            prefs[Keys.FX_LAST_UPDATED] = now
                            prefs[Keys.FX_BASE] = body.base ?: "USD"
                        }
                        _rates.value = r
                    }
                } else {
                    Log.w(TAG, "FX refresh failed: HTTP ${resp.code()}")
                }
            }.onFailure { Log.w(TAG, "FX refresh threw: ${it.message}", it) }
        }
    }

    /**
     * Returns the [amount] converted from [fromSymbol] to [toSymbol]
     * using the latest cached rates. Falls back to identity (returns
     * [amount] unchanged) if:
     *   - both symbols are equal,
     *   - either symbol is unknown,
     *   - no rates are loaded yet.
     *
     * Callers MUST treat the returned value as approximate; Dashboard
     * renders it rounded to the currency's standard precision via
     * `CurrencyFormatter.formatCurrency`.
     */
    fun convert(amount: Double, fromSymbol: String, toSymbol: String): Double {
        if (fromSymbol == toSymbol) return amount
        val table = _rates.value.takeIf { it.isNotEmpty() } ?: return amount
        val from = symbolToIso(fromSymbol) ?: return amount
        val to = symbolToIso(toSymbol) ?: return amount
        val fromRate = table[from] ?: return amount
        val toRate = table[to] ?: return amount
        if (fromRate <= 0.0 || toRate <= 0.0) return amount
        // USD-quoted: amount_in_from * (1 / fromRate_per_usd) = amount_in_usd
        // amount_in_usd * toRate_per_usd = amount_in_to
        return amount * (toRate / fromRate)
    }

    private fun symbolToIso(symbol: String): String? = when (symbol) {
        "₹" -> "INR"
        "$" -> "USD"
        "€" -> "EUR"
        "£" -> "GBP"
        "¥" -> "JPY"
        "₩" -> "KRW"
        else -> null
    }

    companion object {
        private const val TAG = "CurrencyRepository"
        private const val TWELVE_HOURS_MS = 12L * 60 * 60 * 1000
        private object Keys {
            val FX_LAST_UPDATED = longPreferencesKey("fx_last_updated")
            val FX_BASE = stringPreferencesKey("fx_base")
        }
    }
}
