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
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.dhanuk.debtbro.data.db.entity.DebtEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object CanvasExporter {
    private const val W = 900
    private val H = 1350

    private var lastStyle = -1
    private fun nextStyle(): Int {
        var s: Int
        do { s = (0..3).random() } while (s == lastStyle)
        lastStyle = s
        return s
    }

    private fun drawWrappedText(
        canvas: Canvas,
        text: String,
        basePaint: TextPaint,
        x: Float,
        y: Float,
        maxWidth: Int,
        maxHeight: Int = Int.MAX_VALUE
    ): Int {
        var currentTextSize = basePaint.textSize
        var layout: StaticLayout
        var iterations = 0
        
        // Scale down font until it fits in maxHeight
        while (iterations < 10) {
            val paint = TextPaint(basePaint).apply { textSize = currentTextSize }
            layout = StaticLayout.Builder.obtain(
                text, 0, text.length, paint, maxWidth
            )
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.25f)
                .setIncludePad(false)
                .build()
            
            if (layout.height <= maxHeight || currentTextSize <= 20f) break
            currentTextSize *= 0.9f
            iterations++
        }
        
        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
        
        return layout.height
    }

    fun createDebtCard(
        context: Context,
        debt: DebtEntity,
        aiMessage: String,
        roastLevel: String = "MEDIUM",
        showDescription: Boolean = true,
        showDueDate: Boolean = true,
        showEmoji: Boolean = true
    ): Bitmap {
        val style = nextStyle()
        val bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val remaining = debt.amount - debt.amountPaid
        val amountStr = formatCurrency(remaining, debt.currency)
        val dueDate = debt.dueDate?.let { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(it)) } ?: "No due date"
        val message = aiMessage.ifBlank { debt.description.ifBlank { "DebtBro keeps score so you do not have to." } }

        when (style) {
            0 -> drawWallOfShame(canvas, paint, debt, amountStr, dueDate, message, showDescription, showDueDate, showEmoji)
            1 -> drawInstaVibe(canvas, paint, debt, amountStr, dueDate, message, showDescription, showDueDate, showEmoji)
            2 -> drawElegantMinimal(canvas, paint, debt, amountStr, dueDate, message, showDescription, showDueDate, showEmoji)
            3 -> drawCyberpunk(canvas, paint, debt, amountStr, dueDate, message, showDescription, showDueDate, showEmoji)
        }

        return bitmap
    }

    private fun drawWallOfShame(canvas: Canvas, paint: Paint, debt: DebtEntity, amount: String, dueDate: String, message: String, showDescription: Boolean, showDueDate: Boolean, showEmoji: Boolean) {
        val bg = LinearGradient(0f, 0f, W.toFloat(), H.toFloat(), Color.rgb(255, 200, 50), Color.rgb(200, 150, 30), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), Paint().apply { shader = bg })

        paint.color = Color.BLACK
        canvas.drawRect(30f, 30f, (W - 30).toFloat(), (H - 30).toFloat(), paint)
        canvas.drawRect(40f, 40f, (W - 40).toFloat(), (H - 40).toFloat(), Paint().apply { color = Color.rgb(255, 220, 100) })

        paint.textAlign = Paint.Align.CENTER
        paint.color = Color.BLACK
        paint.textSize = 48f
        paint.isFakeBoldText = true
        canvas.drawText("WANTED", (W / 2).toFloat(), 140f, paint)

        paint.textSize = 36f
        canvas.drawText("FOR DEBT", (W / 2).toFloat(), 190f, paint)

        if (showEmoji && debt.personEmoji.isNotBlank()) {
            paint.textSize = 100f
            canvas.drawText(debt.personEmoji, (W / 2).toFloat(), 320f, paint)
        }

        paint.textSize = 52f
        paint.isFakeBoldText = true
        canvas.drawText(debt.personName, (W / 2).toFloat(), 400f, paint)

        paint.textSize = 80f
        paint.color = Color.rgb(180, 0, 0)
        canvas.drawText(amount, (W / 2).toFloat(), 520f, paint)

        var yOffset = 600
        if (showDueDate) {
            paint.textSize = 32f
            paint.color = Color.BLACK
            canvas.drawText("Due: $dueDate", (W / 2).toFloat(), yOffset.toFloat(), paint)
            yOffset += 40
        }

        if (showDescription && debt.description.isNotBlank()) {
            paint.textSize = 28f
            paint.textAlign = Paint.Align.LEFT
            val descTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 28f
                isFakeBoldText = true
            }
            val descText = "For: ${debt.description}"
            val descHeight = drawWrappedText(canvas, descText, descTextPaint, 80f, yOffset.toFloat(), W - 160)
            yOffset += descHeight + 20
            paint.textAlign = Paint.Align.CENTER
        }

        val boxTop = (yOffset + 40).coerceAtLeast(720).toFloat()
        val boxMaxBottom = (H - 100).toFloat()
        val quoteMaxHeight = (boxMaxBottom - boxTop - 100).toInt()
        val quoteText = message
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 36f
            isFakeBoldText = true
            letterSpacing = 0.02f
        }
        val quoteHeight = drawWrappedText(canvas, quoteText, textPaint, 120f, boxTop + 60, W - 240, quoteMaxHeight)
        val boxBottom = (boxTop + 60 + quoteHeight + 40).coerceAtMost(boxMaxBottom)
        val box = RectF(80f, boxTop, (W - 80).toFloat(), boxBottom)
        canvas.drawRoundRect(box, 20f, 20f, Paint().apply { color = Color.argb(100, 0, 0, 0) })

        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 24f
        paint.color = Color.BLACK
        canvas.drawText("DebtBro", (W / 2).toFloat(), (H - 80).toFloat(), paint)
    }

    private fun drawInstaVibe(canvas: Canvas, paint: Paint, debt: DebtEntity, amount: String, dueDate: String, message: String, showDescription: Boolean, showDueDate: Boolean, showEmoji: Boolean) {
        val bg = LinearGradient(0f, 0f, W.toFloat(), H.toFloat(), Color.rgb(20, 30, 60), Color.rgb(40, 60, 100), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), Paint().apply { shader = bg })

        val card = RectF(50f, 100f, (W - 50).toFloat(), 1100f)
        canvas.drawRoundRect(card, 30f, 30f, Paint().apply { color = Color.argb(80, 255, 255, 255) })

        paint.textAlign = Paint.Align.CENTER
        paint.color = Color.rgb(100, 200, 255)
        paint.textSize = 40f
        paint.isFakeBoldText = true
        canvas.drawText("DEBT ALERT", (W / 2).toFloat(), 170f, paint)

        if (showEmoji && debt.personEmoji.isNotBlank()) {
            paint.textSize = 100f
            paint.color = Color.WHITE
            canvas.drawText(debt.personEmoji, (W / 2).toFloat(), 300f, paint)
        }

        paint.textSize = 52f
        paint.isFakeBoldText = true
        canvas.drawText(debt.personName, (W / 2).toFloat(), 380f, paint)

        paint.textSize = 28f
        paint.color = Color.rgb(180, 180, 180)
        canvas.drawText(if (debt.type == "THEY_OWE_ME") "OWES YOU" else "YOU OWE", (W / 2).toFloat(), 430f, paint)

        paint.textSize = 72f
        paint.color = Color.rgb(100, 200, 255)
        paint.isFakeBoldText = true
        canvas.drawText(amount, (W / 2).toFloat(), 530f, paint)

        var yOffset = 580
        if (showDueDate) {
            paint.textSize = 30f
            paint.color = Color.WHITE
            paint.isFakeBoldText = false
            canvas.drawText("Due: $dueDate", (W / 2).toFloat(), yOffset.toFloat(), paint)
            yOffset += 50
        }

        if (showDescription && debt.description.isNotBlank()) {
            paint.textAlign = Paint.Align.LEFT
            val descTextPaint2 = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 28f
                isFakeBoldText = true
            }
            val descHeight = drawWrappedText(canvas, "For: ${debt.description}", descTextPaint2, 80f, yOffset.toFloat(), W - 160)
            yOffset += descHeight + 20
            paint.textAlign = Paint.Align.CENTER
        }

        val quoteBoxTop = (yOffset + 30).coerceAtLeast(650).toFloat()
        val quoteBoxMaxBottom = 1000f
        val quoteMaxHeight = (quoteBoxMaxBottom - quoteBoxTop - 100).toInt()
        val quoteText = message
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 34f
            isFakeBoldText = true
        }
        val quoteHeight = drawWrappedText(canvas, quoteText, textPaint, 120f, quoteBoxTop + 60, W - 240, quoteMaxHeight)
        val quoteBoxBottom = (quoteBoxTop + 60 + quoteHeight + 40).coerceAtMost(quoteBoxMaxBottom)
        val quoteBox = RectF(80f, quoteBoxTop, (W - 80).toFloat(), quoteBoxBottom)
        canvas.drawRoundRect(quoteBox, 20f, 20f, Paint().apply { color = Color.argb(60, 100, 200, 255) })

        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 22f
        paint.color = Color.rgb(150, 150, 150)
        canvas.drawText("DebtBro", (W / 2).toFloat(), (H - 60).toFloat(), paint)
    }

    private fun drawElegantMinimal(canvas: Canvas, paint: Paint, debt: DebtEntity, amount: String, dueDate: String, message: String, showDescription: Boolean, showDueDate: Boolean, showEmoji: Boolean) {
        canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), Paint().apply { color = Color.WHITE })

        paint.color = Color.rgb(200, 180, 150)
        canvas.drawRect(0f, 0f, W.toFloat(), 8f, paint)

        paint.textAlign = Paint.Align.CENTER
        paint.color = Color.rgb(80, 80, 80)
        paint.textSize = 36f
        paint.isFakeBoldText = true
        canvas.drawText("Gentle Reminder", (W / 2).toFloat(), 100f, paint)

        paint.color = Color.rgb(200, 180, 150)
        canvas.drawLine(200f, 120f, (W - 200).toFloat(), 120f, paint)

        if (showEmoji && debt.personEmoji.isNotBlank()) {
            paint.textSize = 100f
            paint.color = Color.rgb(60, 60, 60)
            canvas.drawText(debt.personEmoji, (W / 2).toFloat(), 240f, paint)
        }

        paint.textSize = 48f
        paint.isFakeBoldText = true
        canvas.drawText(debt.personName, (W / 2).toFloat(), 320f, paint)

        paint.textSize = 72f
        paint.color = Color.rgb(180, 150, 100)
        canvas.drawText(amount, (W / 2).toFloat(), 420f, paint)

        var yOffset = 470
        if (showDueDate) {
            paint.textSize = 28f
            paint.color = Color.rgb(120, 120, 120)
            canvas.drawText("Due: $dueDate", (W / 2).toFloat(), yOffset.toFloat(), paint)
            yOffset += 40
        }

        if (showDescription && debt.description.isNotBlank()) {
            paint.textAlign = Paint.Align.LEFT
            val descTextPaint3 = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(120, 120, 120)
                textSize = 28f
                isFakeBoldText = true
            }
            val descHeight = drawWrappedText(canvas, "For: ${debt.description}", descTextPaint3, 100f, yOffset.toFloat(), W - 200)
            yOffset += descHeight + 20
            paint.textAlign = Paint.Align.CENTER
        }

        val lineY = (yOffset + 10).coerceAtLeast(560).toFloat()
        paint.color = Color.rgb(220, 210, 200)
        canvas.drawLine(100f, lineY, (W - 100).toFloat(), lineY, paint)

        val quoteText = message
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(60, 60, 60)
            textSize = 32f
            isFakeBoldText = true
        }
        val quoteMaxHeight = ((H - 80) - (lineY + 70)).toInt()
        drawWrappedText(canvas, quoteText, textPaint, 100f, lineY + 70, W - 200, quoteMaxHeight)

        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 24f
        paint.color = Color.rgb(180, 150, 100)
        paint.isFakeBoldText = true
        canvas.drawText("DebtBro Luxury", (W / 2).toFloat(), (H - 80).toFloat(), paint)

        paint.color = Color.rgb(200, 180, 150)
        canvas.drawRect(0f, (H - 8).toFloat(), W.toFloat(), H.toFloat(), paint)
    }

    private fun drawCyberpunk(canvas: Canvas, paint: Paint, debt: DebtEntity, amount: String, dueDate: String, message: String, showDescription: Boolean, showDueDate: Boolean, showEmoji: Boolean) {
        canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), Paint().apply { color = Color.BLACK })

        val cyan = Color.rgb(0, 255, 255)
        val border = RectF(20f, 20f, (W - 20).toFloat(), (H - 20).toFloat())
        canvas.drawRoundRect(border, 10f, 10f, Paint().apply { color = cyan; style = Paint.Style.STROKE; strokeWidth = 4f })

        paint.textAlign = Paint.Align.CENTER
        paint.color = cyan
        paint.textSize = 44f
        paint.isFakeBoldText = true
        canvas.drawText("DEBT DETECTED", (W / 2).toFloat(), 100f, paint)

        paint.textSize = 24f
        paint.color = Color.rgb(0, 200, 200)
        canvas.drawText("SYSTEM ALERT // PRIORITY: HIGH", (W / 2).toFloat(), 140f, paint)

        if (showEmoji && debt.personEmoji.isNotBlank()) {
            paint.textSize = 100f
            paint.color = Color.WHITE
            canvas.drawText(debt.personEmoji, (W / 2).toFloat(), 270f, paint)
        }

        paint.textSize = 48f
        paint.isFakeBoldText = true
        canvas.drawText(debt.personName, (W / 2).toFloat(), 350f, paint)

        paint.textSize = 28f
        paint.color = Color.rgb(0, 200, 200)
        canvas.drawText("TARGET: ${if (debt.type == "THEY_OWE_ME") "DEBTOR" else "CREDITOR"}", (W / 2).toFloat(), 400f, paint)

        paint.textSize = 72f
        paint.color = cyan
        paint.isFakeBoldText = true
        canvas.drawText(amount, (W / 2).toFloat(), 500f, paint)

        var yOffset = 540
        if (showDueDate) {
            paint.textSize = 28f
            paint.color = Color.WHITE
            canvas.drawText("DUE: $dueDate", (W / 2).toFloat(), yOffset.toFloat(), paint)
            yOffset += 40
        }

        if (showDescription && debt.description.isNotBlank()) {
            paint.textAlign = Paint.Align.LEFT
            val descTextPaint4 = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 28f
                isFakeBoldText = true
            }
            val descHeight = drawWrappedText(canvas, "REASON: ${debt.description}", descTextPaint4, 60f, yOffset.toFloat(), W - 120)
            yOffset += descHeight + 20
            paint.textAlign = Paint.Align.CENTER
        }

        val boxTop = (yOffset + 30).coerceAtLeast(620).toFloat()
        val boxMaxBottom = 1000f
        val quoteMaxHeight = (boxMaxBottom - boxTop - 100).toInt()
        val quoteText = message
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 34f
            isFakeBoldText = true
        }
        val quoteHeight = drawWrappedText(canvas, quoteText, textPaint, 100f, boxTop + 60, W - 200, quoteMaxHeight)
        val boxBottom = (boxTop + 60 + quoteHeight + 40).coerceAtMost(boxMaxBottom)
        val box = RectF(60f, boxTop, (W - 60).toFloat(), boxBottom)
        canvas.drawRect(box, Paint().apply { color = Color.argb(40, 0, 255, 255) })
        canvas.drawRect(box, Paint().apply { color = cyan; style = Paint.Style.STROKE; strokeWidth = 2f })

        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 20f
        paint.color = cyan
        canvas.drawText("DEBTBRO_OS v2.0", (W / 2).toFloat(), (H - 60).toFloat(), paint)
    }

    fun saveDebtCard(context: Context, bitmap: Bitmap): Uri {
        val cacheDir = File(context.cacheDir, "share_images")
        cacheDir.mkdirs()
        val file = File(cacheDir, "debtbro-card-${System.currentTimeMillis()}.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

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
