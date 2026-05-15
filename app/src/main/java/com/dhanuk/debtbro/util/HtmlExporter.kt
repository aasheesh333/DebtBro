package com.dhanuk.debtbro.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.print.PrintAttributes
import android.print.pdf.PrintedPdfDocument
import android.webkit.WebView
import android.webkit.WebViewClient
import com.dhanuk.debtbro.data.db.entity.DebtEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object HtmlExporter {

    private val templates = listOf(
        "wall_of_shame.html",
        "insta_vibe.html",
        "elegant_minimal.html",
        "cyberpunk_debt.html"
    )

    data class DebtData(
        val lenderName: String,
        val borrowerName: String,
        val amount: String,
        val currency: String,
        val description: String,
        val dueDate: String,
        val debtQuote: String,
        val personEmoji: String
    )

    suspend fun generateShareableImage(
        context: Context,
        debt: DebtEntity,
        lenderName: String,
        aiMessage: String
    ): Bitmap = withContext(Dispatchers.Main) {
        val templateFile = getRandomTemplate()
        val htmlContent = loadAndFillTemplate(context, templateFile, debt, lenderName, aiMessage)
        renderHtmlToBitmap(context, htmlContent)
    }

    private fun getRandomTemplate(): String {
        return templates.random()
    }

    private fun loadAndFillTemplate(
        context: Context,
        templateName: String,
        debt: DebtEntity,
        lenderName: String,
        aiMessage: String
    ): String {
        val inputStream = context.assets.open("html_templates/$templateName")
        val htmlContent = inputStream.bufferedReader().use { it.readText() }

        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val dueDateStr = debt.dueDate?.let { dateFormat.format(Date(it)) } ?: "No due date"

        val formattedAmount = "${debt.currency}${(debt.amount - debt.amountPaid).toLong()}"

        return htmlContent
            .replace("{{lenderName}}", lenderName)
            .replace("{{borrowerName}}", debt.personName)
            .replace("{{amount}}", formattedAmount)
            .replace("{{currency}}", debt.currency)
            .replace("{{description}}", debt.description.ifBlank { "Personal Loan" })
            .replace("{{dueDate}}", dueDateStr)
            .replace("{{debtQuote}}", aiMessage.ifBlank { "Please repay soon!" })
    }

    private suspend fun renderHtmlToBitmap(context: Context, html: String): Bitmap =
        suspendCancellableCoroutine { continuation ->
            val webView = WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                setBackgroundColor(Color.WHITE)
            }

            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    view?.postDelayed({
                        try {
                            val width = 1080
                            val height = 1350
                            view.measure(
                                android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY),
                                android.view.View.MeasureSpec.makeMeasureSpec(height, android.view.View.MeasureSpec.EXACTLY)
                            )
                            view.layout(0, 0, width, height)

                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            val canvas = Canvas(bitmap)
                            canvas.drawColor(Color.WHITE)
                            view.draw(canvas)

                            if (continuation.isActive) {
                                continuation.resume(bitmap)
                            }
                            webView.destroy()
                        } catch (e: Exception) {
                            if (continuation.isActive) {
                                continuation.resumeWithException(e)
                            }
                        }
                    }, 500)
                }
            }

            webView.loadDataWithBaseURL(
                "file:///android_asset/",
                html,
                "text/html",
                "UTF-8",
                null
            )

            continuation.invokeOnCancellation {
                webView.destroy()
            }
        }

    fun saveBitmap(context: Context, bitmap: Bitmap): File {
        val cacheDir = File(context.cacheDir, "share_images")
        cacheDir.mkdirs()
        val file = File(cacheDir, "debtbro-export-${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return file
    }

    fun getShareableUri(context: Context, bitmap: Bitmap): android.net.Uri {
        val file = saveBitmap(context, bitmap)
        return androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    fun shareImage(context: Context, bitmap: Bitmap) {
        val uri = getShareableUri(context, bitmap)
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(android.content.Intent.createChooser(intent, "Share DebtBro Card").apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}