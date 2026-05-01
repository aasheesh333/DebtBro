package com.dhanuk.debtbro.di

import android.content.Context
import androidx.credentials.CredentialManager
import com.dhanuk.debtbro.data.datastore.AppPreferences
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    @Provides @Singleton fun providePrefs(@ApplicationContext context: Context): AppPreferences = AppPreferences(context)
    @Provides @Singleton fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()
    @Provides @Singleton fun provideFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()
    @Provides @Singleton fun provideCredentialManager(@ApplicationContext context: Context): CredentialManager = CredentialManager.create(context)
}
