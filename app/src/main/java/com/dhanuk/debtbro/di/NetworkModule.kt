package com.dhanuk.debtbro.di

import com.dhanuk.debtbro.BuildConfig
import com.dhanuk.debtbro.data.network.AccountDeletionApiService
import com.dhanuk.debtbro.data.network.GeminiApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides @Singleton fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder().apply {
        // SECURITY: disable automatic redirects. The account-deletion Cloud
        // Function call performs an HTTPS+host allowlist check before POSTing,
        // but OkHttp follows 3xx by default — if the Cloud Function ever
        // returns a 302 to https://attacker.example/, the guard never fires
        // on the redirected hop. Disabling redirects globally is safe because
        // Gemini never redirects and the Cloud Function shouldn't either.
        // (followSslRedirects is implicitly disabled once followRedirects=false.)
        followRedirects(false)
        if (BuildConfig.DEBUG) {
            // NONE — avoids leaking API key in URL via BASIC level logging.
            // Gemini uses `?key=` query param which BASIC prints full URL for.
            // Devs needing request tracing should attach a debugger, not logcat.
            addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.NONE })
        }
    }.build()

    @Provides @Singleton fun provideRetrofit(client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl("https://generativelanguage.googleapis.com/")
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    @Provides @Singleton fun provideGeminiApi(retrofit: Retrofit): GeminiApiService =
        retrofit.create(GeminiApiService::class.java)

    // ── Account deletion Cloud Function URL ────────────────────────────────────
    // ACCOUNT_DELETION_URL is wired from the GH secret via build.yml +
    // buildConfigField. Exposes as a @Named "accountDeletionUrl" string so
    // AuthManager can pass it to AccountDeletionApiService via @Url at request
    // time. Empty string means "not configured" — the AuthManager skips the
    // HTTP call and falls back to local-only deletion bookkeeping.
    @Provides @Singleton @Named("accountDeletionUrl")
    fun provideAccountDeletionUrl(): String = BuildConfig.ACCOUNT_DELETION_URL

    // Shares the same Retrofit instance as Gemini (Retrofit allows multiple
    // services with @Url-absolute endpoints to coexist on a single client).
    @Provides @Singleton fun provideAccountDeletionApi(retrofit: Retrofit): AccountDeletionApiService =
        retrofit.create(AccountDeletionApiService::class.java)
}