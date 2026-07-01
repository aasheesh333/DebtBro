package com.dhanuk.debtbro.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.dhanuk.debtbro.data.db.dao.DebtDao
import com.dhanuk.debtbro.data.db.dao.PaymentDao
import com.dhanuk.debtbro.data.db.dao.SplitDao
import com.dhanuk.debtbro.data.db.entity.DebtEntity
import com.dhanuk.debtbro.data.db.entity.PaymentEntity
import com.dhanuk.debtbro.data.db.entity.SplitEntity

@Database(
    entities = [DebtEntity::class, PaymentEntity::class, SplitEntity::class],
    version = 3,
    exportSchema = false
)
abstract class DebtBroDB : RoomDatabase() {
    abstract fun debtDao(): DebtDao
    abstract fun paymentDao(): PaymentDao
    abstract fun splitDao(): SplitDao

    companion object {
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE debts ADD COLUMN amountPaid REAL NOT NULL DEFAULT 0")
                } catch (_: Exception) { }
                try {
                    db.execSQL("ALTER TABLE debts ADD COLUMN lastNudgedAt INTEGER DEFAULT NULL")
                } catch (_: Exception) { }
                try {
                    db.execSQL("ALTER TABLE debts ADD COLUMN notes TEXT DEFAULT NULL")
                } catch (_: Exception) { }
            }
        }

        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE splits ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                } catch (_: Exception) { }
            }
        }

        val ALL_MIGRATIONS = listOf(MIGRATION_1_2, MIGRATION_2_3)
    }
}
