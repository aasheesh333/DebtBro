package com.dhanuk.debtbro.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import android.provider.MediaStore
import com.dhanuk.debtbro.data.db.entity.DebtEntity

object CanvasExporter {
    fun createDebtCard(context: Context, debt: DebtEntity, aiMessage: String): Bitmap {
        val bitmap = Bitmap.createBitmap(1080, 1080, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, 1080f, 1080f, Color.rgb(13, 13, 13), Color.rgb(0, 184, 122), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, 1080f, 1080f, paint)
        paint.shader = null
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 130f
        canvas.drawText(debt.personEmoji, 540f, 220f, paint)
        paint.textSize = 62f
        paint.isFakeBoldText = true
        canvas.drawText(debt.personName, 540f, 330f, paint)
        paint.textSize = 86f
        canvas.drawText(formatCurrency(debt.amount - debt.amountPaid, debt.currency), 540f, 470f, paint)
        paint.textSize = 36f
        paint.isFakeBoldText = false
        canvas.drawText(if (debt.type == "THEY_OWE_ME") "owes you" else "you owe them", 540f, 540f, paint)
        paint.color = Color.argb(225, 20, 20, 20)
        canvas.drawRoundRect(90f, 650f, 990f, 880f, 36f, 36f, paint)
        paint.color = Color.WHITE
        paint.textSize = 40f
        val message = aiMessage.ifBlank { debt.description.ifBlank { "DebtBro keeps score so you do not have to." } }
        message.chunked(34).take(4).forEachIndexed { index, line -> canvas.drawText(line, 540f, 730f + index * 48f, paint) }
        paint.textSize = 30f
        canvas.drawText("DebtBro", 540f, 1000f, paint)
        return bitmap
    }
    fun saveDebtCard(context: Context, bitmap: Bitmap): Uri {
        val fileName = "debtbro-card-${System.currentTimeMillis()}.png"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/DebtBro")
            }
        }
        val uri: Uri
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("Unable to save card")
        } else {
            val picsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES)
            val debtBroDir = java.io.File(picsDir, "DebtBro")
            if (!debtBroDir.exists()) debtBroDir.mkdirs()
            val file = java.io.File(debtBroDir, fileName)
            file.createNewFile()

            val fileValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DATA, file.absolutePath)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            }
            uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, fileValues) ?: Uri.fromFile(file)
        }

        context.contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return uri
    }
    fun shareDebtCard(context: Context, bitmap: Bitmap) {
        val uri = saveDebtCard(context, bitmap)
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "Share DebtBro card"))
    }
}
