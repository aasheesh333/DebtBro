package com.dhanuk.debtbro.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.dhanuk.debtbro.data.db.entity.DebtEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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

    @Volatile
    private var lastError: String? = null
    private var lastTemplateIndex = -1

    fun getLastError(): String? = lastError

    private fun getRandomTemplateIndex(): Int {
        var newIndex: Int
        do {
            newIndex = (0 until templates.size).random()
        } while (newIndex == lastTemplateIndex && templates.size > 1)
        lastTemplateIndex = newIndex
        return newIndex
    }

    suspend fun generateShareableImage(
        context: Context,
        debt: DebtEntity,
        lenderName: String,
        aiMessage: String
    ): Bitmap = withContext(Dispatchers.Main) {
        try {
            val templateIndex = getRandomTemplateIndex()
            val templateFile = templates[templateIndex]
            android.util.Log.d("HtmlExporter", "Using template: $templateFile (index: $templateIndex)")
            val htmlContent = loadAndFillTemplate(context, templateFile, debt, lenderName, aiMessage)
            renderHtmlToBitmap(context, htmlContent)
        } catch (e: Exception) {
            lastError = e.message
            android.util.Log.e("HtmlExporter", "HTML export failed: ${e.message}", e)
            throw e
        }
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
        val hasDesc = debt.description.isNotBlank()

        return htmlContent
            .replace("{{lenderName}}", escapeHtml(lenderName))
            .replace("{{borrowerName}}", escapeHtml(debt.personName))
            .replace("{{amount}}", formattedAmount)
            .replace("{{currency}}", debt.currency)
            .replace("{{description}}", escapeHtml(debt.description))
            .replace("{{dueDate}}", dueDateStr)
            .replace("{{debtQuote}}", escapeHtml(aiMessage.ifBlank { "Please repay soon!" }))
            .replace("{{descriptionDisplay}}", if (hasDesc) "flex" else "none")
            .replace("{{dueDateText}}", if (hasDesc) "- Due" else "")
            .replace("{{quoteText}}", if (hasDesc) "." else "")
    }

    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }

    private suspend fun renderHtmlToBitmap(context: Context, html: String): Bitmap =
        withTimeout(30000L) {
            suspendCancellableCoroutine { continuation ->
                val webView = WebView(context).apply {
                    settings.apply {
                        javaScriptEnabled = true
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        cacheMode = WebSettings.LOAD_NO_CACHE
                        domStorageEnabled = true
                    }
                    setBackgroundColor(Color.WHITE)
                }

                val timeoutHandler = android.os.Handler(android.os.Looper.getMainLooper())
                val timeoutRunnable = Runnable {
                    if (continuation.isActive) {
                        try { webView.destroy() } catch (_: Exception) {}
                        continuation.resumeWithException(TimeoutException("WebView render timeout"))
                    }
                }
                timeoutHandler.postDelayed(timeoutRunnable, 28000)

                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        timeoutHandler.removeCallbacks(timeoutRunnable)

                        // Increased delay from 500ms to 1500ms for better rendering
                        view?.postDelayed({
                            try {
                                val width = 1080
                                val height = 1350

                                view.measure(
                                    android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY),
                                    android.view.View.MeasureSpec.makeMeasureSpec(height, android.view.View.MeasureSpec.EXACTLY)
                                )
                                view.layout(0, 0, width, height)

                                // Additional delay for complex CSS to render
                                view.postDelayed({
                                    try {
                                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                        val canvas = Canvas(bitmap)
                                        canvas.drawColor(Color.WHITE)
                                        view.draw(canvas)

                                        if (continuation.isActive) {
                                            continuation.resume(bitmap)
                                        }
                                    } catch (e: Exception) {
                                        if (continuation.isActive) {
                                            continuation.resumeWithException(e)
                                        }
                                    } finally {
                                        try { webView.destroy() } catch (_: Exception) {}
                                    }
                                }, 800)
                            } catch (e: Exception) {
                                if (continuation.isActive) {
                                    continuation.resumeWithException(e)
                                }
                            }
                        }, 1500)
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        errorCode: Int,
                        description: String?,
                        failingUrl: String?
                    ) {
                        android.util.Log.e("HtmlExporter", "WebView error: $description (code: $errorCode)")
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
                    timeoutHandler.removeCallbacks(timeoutRunnable)
                    try { webView.destroy() } catch (_: Exception) {}
                }
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

class TimeoutException(message: String) : Exception(message)