package com.dhanuk.debtbro.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.dhanuk.debtbro.data.db.entity.DebtEntity
import java.io.OutputStreamWriter

object CsvExporter {
    fun exportDebts(context: Context, debts: List<DebtEntity>): Uri {
        val fileName = "debtbro-${System.currentTimeMillis()}.csv"
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "text/csv")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) put(MediaStore.Downloads.RELATIVE_PATH, "Download/DebtBro")
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("Unable to create CSV")
        context.contentResolver.openOutputStream(uri)?.use { stream ->
            OutputStreamWriter(stream).use { writer ->
                writer.appendLine("Name,Amount,Paid,Remaining,Type,Status,Description,DueDate,CreatedAt")
                debts.forEach { d ->
                    writer.appendLine(listOf(d.personName, d.amount, d.amountPaid, d.amount - d.amountPaid, d.type, d.status, d.description, d.dueDate?.toReadableDate().orEmpty(), d.createdAt.toReadableDate()).joinToString(",") { "\"${it.toString().replace("\"", "\"\"")}\"" })
                }
            }
        }
        return uri
    }
}
