package com.dhanuk.debtbro.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.provider.MediaStore
import com.dhanuk.debtbro.data.db.entity.DebtEntity

object CanvasExporter {
    // 4:3 ratio - 1200x900 for WhatsApp friendly dimensions
    private const val W = 1200
    private const val H = 900

    fun createDebtCard(context: Context, debt: DebtEntity, aiMessage: String, roastLevel: String = "MEDIUM"): Bitmap {
        val bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        
        // Determine colors based on roast level for more impactful sharing
        val isSavage = roastLevel.equals("SAVAGE", ignoreCase = true)
        val backgroundStartColor = if (isSavage) Color.rgb(80, 0, 0) else Color.rgb(13, 13, 13) // Dark red for savage
        val backgroundEndColor = if (isSavage) Color.rgb(130, 0, 0) else Color.rgb(0, 70, 50) // Deeper red for savage
        val accentColor = if (isSavage) Color.rgb(255, 69, 0) else Color.rgb(0, 184, 122) // Orange-red for savage
        val boxColor = if (isSavage) Color.argb(230, 20, 0, 0) else Color.argb(200, 20, 20, 20) // More opaque red for savage
        val boxTextColor = if (isSavage) Color.WHITE else Color.WHITE
        val brandingColor = if (isSavage) Color.argb(200, 255, 69, 0) else Color.argb(160, 0, 184, 122) // More visible orange for savage

        // 1. Background gradient
        paint.shader = LinearGradient(0f, 0f, W.toFloat(), H.toFloat(),
            backgroundStartColor, backgroundEndColor, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), paint)
        paint.shader = null

        // 2. Top accent line
        paint.color = accentColor
        canvas.drawRect(0f, 0f, W.toFloat(), 6f, paint)

        // 3. Emoji
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 100f
        canvas.drawText(debt.personEmoji, (W / 2).toFloat(), 140f, paint)

        // 4. Person name
        paint.textSize = 48f
        paint.isFakeBoldText = true
        paint.color = Color.WHITE
        canvas.drawText(debt.personName, (W / 2).toFloat(), 210f, paint)

        // 5. Label
        paint.textSize = 24f
        paint.isFakeBoldText = false
        paint.color = Color.rgb(160, 160, 160)
        canvas.drawText(if (debt.type == "THEY_OWE_ME") "OWES YOU" else "YOU OWE", (W / 2).toFloat(), 250f, paint)

        // 6. Amount - big and bold
        paint.textSize = 72f
        paint.isFakeBoldText = true
        paint.color = if (debt.type == "THEY_OWE_ME") Color.rgb(0, 230, 160) else Color.rgb(255, 71, 87)
        val amountStr = formatCurrency(debt.amount - debt.amountPaid, debt.currency)
        canvas.drawText(amountStr, (W / 2).toFloat(), 330f, paint)

        // 7. Separator line
        paint.color = Color.argb(80, 255, 255, 255)
        canvas.drawLine(200f, 370f, (W - 200).toFloat(), 370f, paint)

        // 8. AI Quote / Message box
        paint.color = boxColor
        val roundRect = RectF(60f, 400f, (W - 60).toFloat(), 680f)
        canvas.drawRoundRect(roundRect, 24f, 24f, paint)

        // AI Quote content
        paint.color = boxTextColor
        paint.textSize = 30f
        paint.isFakeBoldText = false
        paint.textAlign = Paint.Align.LEFT
        val message = aiMessage.ifBlank { debt.description.ifBlank { "DebtBro keeps score so you do not have to." } }

        // Word-wrap the message in the box
        val maxWidth = W - 160f
        val words = message.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = ""
        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (paint.measureText(testLine) <= maxWidth) {
                currentLine = testLine
            } else {
                lines.add(currentLine)
                currentLine = word
            }
        }
        if (currentLine.isNotEmpty()) lines.add(currentLine)

        val startY = 470f
        val lineHeight = 42f
        lines.take(5).forEachIndexed { i, line ->
            canvas.drawText(line, 120f, startY + i * lineHeight, paint)
        }

        // 9. Bottom branding bar
        paint.color = brandingColor
        canvas.drawRect(0f, (H - 50).toFloat(), W.toFloat(), H.toFloat(), paint)
        paint.textSize = 22f
        paint.color = Color.BLACK
        paint.isFakeBoldText = true
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("DebtBro — because friends forget 😅", (W / 2).toFloat(), (H - 16).toFloat(), paint)

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
        try {
            val uri = saveDebtCard(context, bitmap)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Share DebtBro card").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback to text sharing if image sharing fails
            val fallbackText = "Check out DebtBro - the app that tracks debts with style!\nDownload now: https://play.google.com/store/apps/details?id=com.dhanuk.debtbro"
            val fallbackIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, fallbackText)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(fallbackIntent, "Share via").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }
}
