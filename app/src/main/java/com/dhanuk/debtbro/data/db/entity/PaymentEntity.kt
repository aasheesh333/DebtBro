package com.dhanuk.debtbro.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "payments",
    foreignKeys = [ForeignKey(entity = DebtEntity::class, parentColumns = ["id"], childColumns = ["debtId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("debtId")]
)
data class PaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val firebaseId: String? = null,
    val debtId: Int,
    val amount: Double,
    val note: String? = null,
    val paidAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false
)
