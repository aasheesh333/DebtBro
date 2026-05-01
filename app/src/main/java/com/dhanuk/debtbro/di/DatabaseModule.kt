package com.dhanuk.debtbro.di

import android.content.Context
import androidx.room.Room
import com.dhanuk.debtbro.data.db.DebtBroDB
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton fun provideDb(@ApplicationContext context: Context): DebtBroDB = Room.databaseBuilder(context, DebtBroDB::class.java, "debtbro.db").fallbackToDestructiveMigration().build()
    @Provides fun provideDebtDao(db: DebtBroDB) = db.debtDao()
    @Provides fun providePaymentDao(db: DebtBroDB) = db.paymentDao()
    @Provides fun provideSplitDao(db: DebtBroDB) = db.splitDao()
}
