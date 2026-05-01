package com.dhanuk.debtbro.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "debts")
data class DebtEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val firebaseId: String? = null,
    val personName: String,
    val personEmoji: String,
    val amount: Double,
    val currency: String = "₹",
    val description: String = "",
    val type: String,
    val status: String = "PENDING",
    val createdAt: Long = System.currentTimeMillis(),
    val dueDate: Long? = null,
    val amountPaid: Double = 0.0,
    val lastNudgedAt: Long? = null,
    val aiRoastGenerated: String? = null,
    val notes: String? = null,
    val isSynced: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)
