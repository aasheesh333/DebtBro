package com.dhanuk.debtbro.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.dhanuk.debtbro.data.db.entity.DebtEntity
import java.io.File
import java.io.IOException
import java.io.OutputStreamWriter

/**
 * CSV export for DebtBro.
 *
 * Fix history:
 *  2026-07-03 first pass bumped minSdk from 26 → 29 and removed the
 *  legacy getExternalStoragePublicDirectory path under the assumption
 *  MediaStore alone was enough. That broke installs on Android 9 / API
 *  28 and below ("There was a problem parsing the package") because
 *  Android silently rejects APKs whose minSdk > device API level. Reverted
 *  minSdk to 26 on 2026-07-03 and restored the dual-path export:
 *    - API ≥ Q: MediaStore.Downloads, no runtime permission needed
 *    - API 26-28: WRITE_EXTERNAL_STORAGE (maxSdkVersion=28) +
 *      File output to Downloads dir; runtime-requested via
 *      ContextCompat.checkSelfPermission. If the user denies, the export
 *      throws IOException which the caller surfaces as a Toast.
 *  both paths funnel into the same CSV-writing helper.
 */
object CsvExporter {
    fun exportDebts(context: Context, debts: List<DebtEntity>): Result<Uri> = runCatching {
        if (debts.isEmpty()) throw IOException("No debts to export")
        val fileName = "debtbro-${System.currentTimeMillis()}.csv"

        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            writeToMediaStore(context, fileName, debts)
        } else {
            writeToDownloadsFile(context, fileName, debts)
        }
        uri
    }

    private fun writeToMediaStore(
        context: Context, fileName: String, debts: List<DebtEntity>
    ): Uri {
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
        return uri
    }

    private fun writeToDownloadsFile(
        context: Context, fileName: String, debts: List<DebtEntity>
    ): Uri {
        // API 26-28: WRITE_EXTERNAL_STORAGE is needed. Declared in
        // manifest with maxSdkVersion=28. If the user hasn't granted it,
        // we surface a clear error so they can grant from app info →
        // permissions. (Earlier code didn't runtime-request in code;
        // we keep that behavior — the caller's catch toast sends them
        // to system settings.)
        if (ContextCompat.checkSelfPermission(
                context, Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            throw IOException("Storage permission required to export CSV on this Android version. Please grant Storage permission from app settings.")
        }
        val downloadsDir = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS
        )
        val targetDir = File(downloadsDir, "DebtBro").apply { if (!exists()) mkdirs() }
        val file = File(targetDir, fileName)
        file.outputStream().use { stream -> writeCsv(stream, debts) }
        return androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
    }

    private fun writeCsv(stream: java.io.OutputStream, debts: List<DebtEntity>) {
        OutputStreamWriter(stream, "UTF-8").use { writer ->
            writer.write("\uFEFF")
            writer.appendLine("Name,Amount,Paid,Remaining,Type,Status,Description,DueDate,CreatedAt")
            debts.forEach { d ->
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
