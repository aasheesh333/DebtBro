package com.dhanuk.debtbro.di

import com.dhanuk.debtbro.BuildConfig
import com.dhanuk.debtbro.data.network.GroqApiService
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
            addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
                redactHeader("Authorization")
            })
        }
    }.build()
    @Provides @Singleton fun provideRetrofit(client: OkHttpClient): Retrofit = Retrofit.Builder().baseUrl("https://api.groq.com/openai/v1/").client(client).addConverterFactory(GsonConverterFactory.create()).build()
    @Provides @Singleton fun provideGroqApi(retrofit: Retrofit): GroqApiService = retrofit.create(GroqApiService::class.java)
}
