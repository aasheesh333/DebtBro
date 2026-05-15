package com.dhanuk.debtbro.util

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.dhanuk.debtbro.data.db.entity.DebtEntity
import java.io.File
import java.io.IOException
import java.io.OutputStreamWriter

object CsvExporter {
    fun exportDebts(context: Context, debts: List<DebtEntity>): Uri {
        val fileName = "debtbro-${System.currentTimeMillis()}.csv"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ — use MediaStore Downloads collection
            val values = android.content.ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/DebtBro")
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("Unable to create CSV in MediaStore")
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                writeCsv(stream, debts)
            } ?: throw IOException("Unable to open output stream for CSV")
            return uri
        } else {
            // Android 9 and below — use FileProvider to avoid permission issues on MIUI/other ROMs
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val file = File(downloadsDir, fileName)
            file.outputStream().use { writeCsv(it, debts) }
            return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }
    }

    private fun writeCsv(stream: java.io.OutputStream, debts: List<DebtEntity>) {
        OutputStreamWriter(stream).use { writer ->
            writer.appendLine("Name,Amount,Paid,Remaining,Type,Status,Description,DueDate,CreatedAt")
            debts.forEach { d ->
                val fields = listOf(
                    d.personName, d.amount.toString(), d.amountPaid.toString(),
                    (d.amount - d.amountPaid).toString(), d.type, d.status,
                    d.description, d.dueDate?.toReadableDate().orEmpty(),
                    d.createdAt.toReadableDate()
                )
                val csvLine = fields.joinToString(",") { field ->
                    val escaped = field
                        .replace("\"", "\"\"")
                        .replace("\n", " ")
                        .replace("\r", " ")
                    "\"$escaped\""
                }
                writer.appendLine(csvLine)
            }
        }
    }
}
