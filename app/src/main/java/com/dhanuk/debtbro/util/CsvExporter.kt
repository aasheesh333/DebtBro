package com.dhanuk.debtbro.util

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.dhanuk.debtbro.data.db.entity.DebtEntity
import java.io.IOException
import java.io.OutputStreamWriter

/**
 * CSV export for DebtBro.
 *
 * Fix history (2026-07-03, offline-mode audit):
 *  Previously this exporter had a pre-Q branch that wrote to
 *  `Environment.getExternalStoragePublicDirectory(DIRECTORY_DOWNLOADS)`
 *  via `file.outputStream()`. On API 26-28 the WRITE_EXTERNAL_STORAGE
 *  permission was declared in the manifest with `maxSdkVersion=28`, but
 *  the app never asked the user for it at runtime — so the pre-Q path
 *  always threw SecurityException and the catch in SettingsViewModel
 *  surfaced as "Export failed". With `minSdk` bumped to 29 we can drop
 *  the entire pre-Q path; MediaStore handles Downloads on every supported
 *  device and needs no runtime permission.
 */
object CsvExporter {
    fun exportDebts(context: Context, debts: List<DebtEntity>): Result<Uri> = runCatching {
        if (debts.isEmpty()) throw IOException("No debts to export")
        val fileName = "debtbro-${System.currentTimeMillis()}.csv"

        val values = android.content.ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/DebtBro")
        }
        val uri = context.contentResolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
        ) ?: throw IOException("Unable to create CSV in MediaStore")
        context.contentResolver.openOutputStream(uri)?.use { stream ->
            writeCsv(stream, debts)
        } ?: throw IOException("Unable to open output stream for CSV")
        uri
    }

    private fun writeCsv(stream: java.io.OutputStream, debts: List<DebtEntity>) {
        OutputStreamWriter(stream, "UTF-8").use { writer ->
            writer.write("\uFEFF")
            writer.appendLine("Name,Amount,Paid,Remaining,Type,Status,Description,DueDate,CreatedAt")
            debts.forEach { d ->
                // Use %.2f so 1234.56 isn't truncated to 1234. Whole
                // numbers still render as "500.00" — consistent and
                // spreadsheet-friendly.
                val fields = listOf(
                    d.personName,
                    "%.2f".format(d.amount),
                    "%.2f".format(d.amountPaid),
                    "%.2f".format(d.amount - d.amountPaid),
                    d.type,
                    d.status,
                    d.description,
                    d.dueDate?.toReadableDate().orEmpty(),
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
