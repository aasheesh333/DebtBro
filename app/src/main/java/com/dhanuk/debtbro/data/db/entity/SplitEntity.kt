package com.dhanuk.debtbro.data.db.entity

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "splits")
data class SplitEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val firebaseId: String? = null,
    val title: String,
    val totalAmount: Double,
    val participants: String,
    val perPersonAmount: Double,
    val aiSummary: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false
) {
    @Keep
    constructor() : this(
        title = "",
        totalAmount = 0.0,
        participants = "",
        perPersonAmount = 0.0
    )
}
