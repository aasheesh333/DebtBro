package com.dhanuk.debtbro.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.dhanuk.debtbro.data.db.dao.DebtDao
import com.dhanuk.debtbro.data.db.dao.PaymentDao
import com.dhanuk.debtbro.data.db.dao.SplitDao
import com.dhanuk.debtbro.data.db.entity.DebtEntity
import com.dhanuk.debtbro.data.db.entity.PaymentEntity
import com.dhanuk.debtbro.data.db.entity.SplitEntity

@Database(entities = [DebtEntity::class, PaymentEntity::class, SplitEntity::class], version = 1, exportSchema = false)
abstract class DebtBroDB : RoomDatabase() {
    abstract fun debtDao(): DebtDao
    abstract fun paymentDao(): PaymentDao
    abstract fun splitDao(): SplitDao
}
