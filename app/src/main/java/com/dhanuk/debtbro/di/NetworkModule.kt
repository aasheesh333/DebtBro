package com.dhanuk.debtbro.di

import com.dhanuk.debtbro.BuildConfig
import com.dhanuk.debtbro.data.network.FxApiService
import com.dhanuk.debtbro.data.network.GeminiApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides @Singleton fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder().apply {
        if (BuildConfig.DEBUG) {
            // NONE — avoids leaking API key in URL via BASIC level logging.
            // Gemini uses `?key=` query param which BASIC prints full URL for.
            // Devs needing request tracing should attach a debugger, not logcat.
            addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.NONE })
        }
        // FX calls have no secrets, so BASIC logging for them is harmless.
        // But the same OkHttpClient is shared with Gemini; keep NONE globally.
    }.build()

    @Provides @Singleton fun provideRetrofit(client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl("https://generativelanguage.googleapis.com/")
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    @Provides @Singleton fun provideGeminiApi(retrofit: Retrofit): GeminiApiService =
        retrofit.create(GeminiApiService::class.java)

    // FxApiService uses absolute @GET URLs, so it ignores the Retrofit
    // baseUrl and posts directly to https://open.er-api.com/. Reusing the
    // singleton OkHttpClient keeps the connection pool shared.
    @Provides @Singleton fun provideFxApi(retrofit: Retrofit): FxApiService =
        retrofit.create(FxApiService::class.java)
}