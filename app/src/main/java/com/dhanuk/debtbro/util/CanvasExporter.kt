package com.dhanuk.debtbro.util

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
import androidx.core.content.FileProvider
import com.dhanuk.debtbro.data.db.entity.DebtEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object CanvasExporter {
    // 3:4 ratio - 900x1200 for better WhatsApp sharing
    private const val W = 900
    private const val H = 1200

    fun createDebtCard(context: Context, debt: DebtEntity, aiMessage: String, roastLevel: String = "MEDIUM"): Bitmap {
        val bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Determine colors based on roast level for more impactful sharing
        val isSavage = roastLevel.equals("SAVAGE", ignoreCase = true)
        val backgroundStartColor = if (isSavage) Color.rgb(80, 0, 0) else Color.rgb(13, 13, 13)
        val backgroundEndColor = if (isSavage) Color.rgb(130, 0, 0) else Color.rgb(0, 70, 50)
        val accentColor = if (isSavage) Color.rgb(255, 69, 0) else Color.rgb(0, 184, 122)
        val boxColor = if (isSavage) Color.argb(230, 20, 0, 0) else Color.argb(200, 20, 20, 20)
        val brandingColor = if (isSavage) Color.argb(200, 255, 69, 0) else Color.argb(160, 0, 184, 122)

        // 1. Background gradient
        paint.shader = LinearGradient(0f, 0f, W.toFloat(), H.toFloat(),
            backgroundStartColor, backgroundEndColor, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), paint)
        paint.shader = null

        // 2. Top accent line
        paint.style = Paint.Style.FILL
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

        // 8. Debt details section
        paint.color = boxColor
        val roundRect = RectF(60f, 400f, (W - 60).toFloat(), 700f)
        canvas.drawRoundRect(roundRect, 24f, 24f, paint)

        // 9. Debt details content
        paint.color = Color.WHITE
        paint.textSize = 30f
        paint.textAlign = Paint.Align.LEFT
        paint.isFakeBoldText = false
        canvas.drawText("Name: ${debt.personName}", 100f, 450f, paint)
        canvas.drawText("Amount: ${formatCurrency(debt.amount - debt.amountPaid, debt.currency)}", 100f, 500f, paint)
        canvas.drawText("For: ${debt.description}", 100f, 550f, paint)
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val dueDate = debt.dueDate?.let { dateFormat.format(Date(it)) } ?: "No due date"
        canvas.drawText("Due: $dueDate", 100f, 600f, paint)
        if (!debt.notes.isNullOrBlank()) {
            canvas.drawText("Note: ${debt.notes}", 100f, 650f, paint)
        }

        // 10. AI Quote / Message box
        paint.color = boxColor
        val quoteRect = RectF(60f, 720f, (W - 60).toFloat(), 1000f)
        canvas.drawRoundRect(quoteRect, 24f, 24f, paint)

        // AI Quote content
        paint.color = if (isSavage) Color.WHITE else Color.WHITE
        paint.textSize = 36f
        paint.isFakeBoldText = false
        paint.textAlign = Paint.Align.LEFT
        val message = aiMessage.ifBlank { debt.description.ifBlank { "DebtBro keeps score so you do not have to." } }
        canvas.drawText(message, 100f, 780f, paint)

        // 11. Bottom branding bar
        paint.color = brandingColor
        canvas.drawRect(0f, (H - 50).toFloat(), W.toFloat(), H.toFloat(), paint)
        paint.textSize = 22f
        paint.color = Color.BLACK
        paint.isFakeBoldText = true
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("DebtBro — because friends forget \uD83D\uDE05", (W / 2).toFloat(), (H - 16).toFloat(), paint)

        return bitmap
    }

    /** Saves bitmap to app cache and returns a FileProvider URI (works on all Android versions). */
    fun saveDebtCard(context: Context, bitmap: Bitmap): Uri {
        val cacheDir = File(context.cacheDir, "share_images")
        cacheDir.mkdirs()
        val file = File(cacheDir, "debtbro-card-${System.currentTimeMillis()}.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    /** Shares the bitmap as an image via ACTION_SEND. */
    fun shareDebtCard(context: Context, bitmap: Bitmap) {
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
    }
}