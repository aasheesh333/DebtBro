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
 *      File output to Downloads dir; runtime-requested by the caller
 *      (SettingsScreen's csvPermissionLauncher) before invoking export.
 *  2026-07-04 added a denial-safe cache fallback: if the user refuses
 *  WRITE_EXTERNAL_STORAGE on API ≤ 28, write the CSV into the app's
 *  cache directory (share_csv/) and return a FileProvider URI for the
 *  share sheet — the export still works, it just isn't persisted in
 *  the public Downloads folder. NEVER throw on permission denial; the
 *  user should never be blocked out of their own data.
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
        // manifest with maxSdkVersion=28. As of 2026-07-04 we ALSO
        // runtime-request it from SettingsScreen before reaching here
        // (see csvPermissionLauncher in SettingsScreen.kt). If the user
        // denied the prompt, fall back to writing the CSV into app cache
        // and sharing it via FileProvider — the export still works, it
        // just lands in the app's cache directory instead of the public
        // Downloads folder. We never block the user out of their data.
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
        return if (hasPermission) {
            writeCsvToPublicDownloads(context, fileName, debts)
        } else {
            writeCsvToCache(context, fileName, debts)
        }
    }

    private fun writeCsvToPublicDownloads(
        context: Context, fileName: String, debts: List<DebtEntity>
    ): Uri {
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

    private fun writeCsvToCache(
        context: Context, fileName: String, debts: List<DebtEntity>
    ): Uri {
        // When the user denies WRITE_EXTERNAL_STORAGE on API ≤ 28 we
        // can't write to the public Downloads dir. Write into a cache
        // subdir ("share_csv/") instead and hand back a FileProvider URI
        // for the share sheet. The file stays available for the lifetime
        // of the share intent (Android keeps cache files between launches
        // on a "best effort" basis; user can re-export any time).
        val cacheDir = File(context.cacheDir, "share_csv").apply { if (!exists()) mkdirs() }
        val file = File(cacheDir, fileName)
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
