package com.dhanuk.debtbro.util

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.dhanuk.debtbro.BuildConfig
import com.dhanuk.debtbro.data.db.entity.DebtEntity
import java.io.File
import java.io.IOException
import java.io.OutputStreamWriter

object CsvExporter {
    fun exportDebts(context: Context, debts: List<DebtEntity>): Result<Uri> = runCatching {
        if (debts.isEmpty()) throw IOException("No debts to export")
        val fileName = "debtbro-${System.currentTimeMillis()}.csv"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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
            uri
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val file = File(downloadsDir, fileName)
            file.outputStream().use { writeCsv(it, debts) }
            FileProvider.getUriForFile(context, "${BuildConfig.PACKAGE_NAME}.fileprovider", file)
        }
    }

    private fun writeCsv(stream: java.io.OutputStream, debts: List<DebtEntity>) {
        OutputStreamWriter(stream, "UTF-8").use { writer ->
            writer.write("\uFEFF")
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
                    "\"${sanitizeCell(escaped)}\""
                }
                writer.appendLine(csvLine)
            }
        }
    }

    private fun sanitizeCell(value: String): String {
        if (value.startsWith("=") || value.startsWith("+") || value.startsWith("-") || value.startsWith("@") || value.startsWith("\t") || value.startsWith("\r")) {
            return "'$value"
        }
        return value
    }
}
