package com.dhanuk.debtbro.data.db.entity

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "splits",
    indices = [Index(value = ["firebaseId"], unique = true)]
)
data class SplitEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val firebaseId: String? = null,
    val title: String,
    val totalAmount: Double,
    val participants: String,
    val perPersonAmount: Double,
    val aiSummary: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    /** Currency symbol (e.g. "₹", "$") in which [totalAmount] and
     *  [perPersonAmount] were entered. Splits are NEVER currency-converted;
     *  the bill is settled in the original currency. Persisted via
     *  v3→v4 Room migration (the column is added with default '₹' to
     *  match the historical behavior of pre-migration splits). */
    val currency: String = "₹",
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
