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
    version = 2,
    exportSchema = false
)
abstract class DebtBroDB : RoomDatabase() {
    abstract fun debtDao(): DebtDao
    abstract fun paymentDao(): PaymentDao
    abstract fun splitDao(): SplitDao

    companion object {
        /**
         * Migrations from version 1 → 2.
         * v1 added the `firebaseId` index on `debts` (with unique constraint).
         * v2 added `amountPaid`/`lastNudgedAt`/`notes` columns to debts (already present in code).
         * If Room sees a v1 DB that is missing these columns, we add them defensively.
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Defensive ALTERs; SQLite ignores duplicate ADD COLUMNs only if missing.
                try {
                    db.execSQL("ALTER TABLE debts ADD COLUMN amountPaid REAL NOT NULL DEFAULT 0")
                } catch (_: Exception) { /* column already exists — ignore */ }

                try {
                    db.execSQL("ALTER TABLE debts ADD COLUMN lastNudgedAt INTEGER DEFAULT NULL")
                } catch (_: Exception) { /* column already exists — ignore */ }

                try {
                    db.execSQL("ALTER TABLE debts ADD COLUMN notes TEXT DEFAULT NULL")
                } catch (_: Exception) { /* column already exists — ignore */ }
            }
        }
    }
}
