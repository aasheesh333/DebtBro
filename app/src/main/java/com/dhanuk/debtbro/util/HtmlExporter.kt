package com.dhanuk.debtbro.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import com.dhanuk.debtbro.BuildConfig
import com.dhanuk.debtbro.data.db.entity.DebtEntity
import kotlinx.coroutines.Dispatchers
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
    @Volatile
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
        aiMessage: String,
        showDescription: Boolean = true,
        showDueDate: Boolean = true,
        showEmoji: Boolean = true
    ): Bitmap = withContext(Dispatchers.Main) {
        try {
            val templateIndex = getRandomTemplateIndex()
            val templateFile = templates[templateIndex]
            android.util.Log.d("HtmlExporter", "Using template: $templateFile (index: $templateIndex)")
            val htmlContent = loadAndFillTemplate(context, templateFile, debt, lenderName, aiMessage, showDescription, showDueDate, showEmoji)
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
        aiMessage: String,
        showDescription: Boolean = true,
        showDueDate: Boolean = true,
        showEmoji: Boolean = true
    ): String {
        val inputStream = context.assets.open("html_templates/$templateName")
        var htmlContent = inputStream.bufferedReader().use { it.readText() }

        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val dueDateStr = debt.dueDate?.let { dateFormat.format(Date(it)) } ?: "No due date"

        val formattedAmount = com.dhanuk.debtbro.util.formatCurrency(debt.amount - debt.amountPaid, debt.currency)
        val hasDesc = debt.description.isNotBlank() && showDescription
        val hasDueDate = showDueDate
        val hasEmoji = debt.personEmoji.isNotBlank() && showEmoji
        
        // Process AI message - remove truncation, preserve full message
        val processedMessage = aiMessage
            .replace("\n", " ")
            .replace("\r", " ")
        
        // Process description - remove truncation, preserve full description
        val processedDesc = debt.description
        
        val quoteCharCount = processedMessage.length
        val descCharCount = processedDesc.length
        val totalChars = quoteCharCount + descCharCount
        
        val quoteLines = (quoteCharCount / 45.0).coerceAtLeast(1.0)
        val descLines = if (hasDesc) (descCharCount / 40.0).coerceAtLeast(1.0) else 0.0
        
        val estimatedHeight = 200 + 350 + 150 + 250 + 100 + (descLines * 28) + 100 + (quoteLines * 32) + 80 + 200
        val scaleFactor = if (estimatedHeight > 1350) (1350.0 / estimatedHeight).coerceAtMost(0.75) else 1.0
        
        val quoteFontSize = when {
            quoteCharCount <= 50 -> "2.2rem"
            quoteCharCount <= 100 -> "1.7rem"
            quoteCharCount <= 200 -> "1.3rem"
            quoteCharCount <= 350 -> "1.0rem"
            else -> "0.85rem"
        }
        
        val descFontSize = when {
            descCharCount <= 40 -> "1.3rem"
            descCharCount <= 80 -> "1.1rem"
            descCharCount <= 150 -> "0.95rem"
            descCharCount <= 250 -> "0.8rem"
            else -> "0.7rem"
        }
        
        val descLineClamp = when {
            descCharCount <= 40 -> 3
            descCharCount <= 80 -> 4
            descCharCount <= 150 -> 6
            descCharCount <= 250 -> 10
            else -> 15
        }
        
        val cardPadding = (50 * scaleFactor).toInt()
        val contentGap = (25 * scaleFactor).toInt()
        val headerMarginBottom = (35 * scaleFactor).toInt()
        val quoteMaxHeight = (350 * scaleFactor).toInt()
        val quotePadding = (30 * scaleFactor).toInt()
        val amountBoxPadding = (40 * scaleFactor).toInt()
        val detailsGap = (40 * scaleFactor).toInt()

        htmlContent = htmlContent
            .replace("{{lenderName}}", escapeHtml(lenderName))
            .replace("{{borrowerName}}", escapeHtml(debt.personName))
            .replace("{{amount}}", formattedAmount)
            .replace("{{currency}}", debt.currency)
            .replace("{{description}}", escapeHtml(processedDesc))
            .replace("{{dueDate}}", dueDateStr)
            .replace("{{debtQuote}}", escapeHtml(processedMessage.ifBlank { "Please repay soon!" }))
            .replace("{{descriptionDisplay}}", if (hasDesc) "flex" else "none")
            .replace("{{dueDateDisplay}}", if (hasDueDate) "flex" else "none")
            .replace("{{emojiDisplay}}", if (hasEmoji) "block" else "none")
            .replace("{{personEmoji}}", escapeHtml(debt.personEmoji))
            .replace("{{dueDateText}}", if (hasDesc) "- Due" else "")
            .replace("{{quoteText}}", if (hasDesc) "." else "")

        val cssVariables = """
        <style>
            :root {
                --quote-font-size: $quoteFontSize;
                --desc-font-size: $descFontSize;
                --card-padding: ${cardPadding}px;
                --content-gap: ${contentGap}px;
                --header-margin-bottom: ${headerMarginBottom}px;
                --quote-max-height: ${quoteMaxHeight}px;
                --quote-padding: ${quotePadding}px;
                --amount-box-padding: ${amountBoxPadding}px;
                --details-gap: ${detailsGap}px;
                --scale-factor: $scaleFactor;
            }
        </style>
        """.trimIndent()

        val enforceStyles = """
        <style>
            * { box-sizing: border-box !important; }
            body { width: 1080px !important; margin: 0 !important; padding: 0 !important; }
            .card { width: 1080px !important; height: auto !important; min-height: 1350px !important; overflow: visible !important; box-sizing: border-box !important; padding: var(--card-padding) !important; }
            .content { overflow: visible !important; gap: var(--content-gap) !important; }
            .header { margin-bottom: var(--header-margin-bottom) !important; }
            .quote-box, .note, .quote, .quote-hero { 
                width: 90% !important; 
                max-width: 900px !important; 
                min-height: 80px !important; 
                max-height: none !important; 
                overflow: visible !important; 
                padding: var(--quote-padding) !important;
            }
            .quote-text, .note-content { 
                width: 100% !important; 
                max-width: 800px !important; 
                word-break: break-word !important; 
                overflow-wrap: break-word !important; 
                word-wrap: break-word !important; 
                white-space: normal !important; 
                overflow: visible !important; 
                display: block !important;
                font-size: var(--quote-font-size) !important;
                line-height: 1.35 !important;
                -webkit-line-clamp: unset !important;
                -webkit-box-orient: unset !important;
                text-overflow: unset !important;
            }
            .amount-box { padding: var(--amount-box-padding) !important; }
            .details { gap: var(--details-gap) !important; }
            .detail-item { align-items: flex-start !important; overflow: visible !important; }
            .detail-item .text-content { 
                max-width: 400px !important; 
                font-size: var(--desc-font-size) !important;
                line-height: 1.3 !important;
                display: block !important; 
                -webkit-line-clamp: unset !important; 
                -webkit-box-orient: unset !important; 
                overflow: visible !important; 
                word-break: break-word !important; 
                overflow-wrap: break-word !important; 
                text-overflow: unset !important;
            }
            .party { padding: calc(25px * var(--scale-factor)) !important; }
            .party-name { font-size: clamp(calc(1.5rem * var(--scale-factor)), 4vw, calc(2.5rem * var(--scale-factor))) !important; }
            .amount { font-size: clamp(calc(3rem * var(--scale-factor)), 10vw, calc(8rem * var(--scale-factor))) !important; }
            .detail-item { font-size: calc(1.4rem * var(--scale-factor)) !important; }
            .detail-icon { font-size: calc(1.8rem * var(--scale-factor)) !important; }
            /* Kill ALL fade/gradient overlays and pseudo-elements */
            .quote-hero::after, .quote-hero::before,
            .quote-hero::after-content, .quote-hero::after-gradient,
            .quote-hero::after-content::after, .quote-hero::after-gradient::after,
            .detail-item::after, .detail-item::before,
            .card::after, .card::before,
            .content::after, .content::before {
                display: none !important;
                content: none !important;
                background: none !important;
                height: 0 !important;
                width: 0 !important;
            }
        </style>
        """.trimIndent()

        return htmlContent.replace("</body>", "$cssVariables$enforceStyles</body>")
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
        withTimeout(25000L) {
            suspendCancellableCoroutine { continuation ->
                val width = 1080
                val height = 1350

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    WebView.enableSlowWholeDocumentDraw()
                }

                // Single source of truth for "the WebView has been
                // destroyed" — every post-cancellation callback (JS
                // evaluateJavascript result, postDelayed runnable,
                // invokeOnCancellation) MUST consult this before touching
                // the WebView. Previously the WebView could be destroy()ed
                // concurrently by `invokeOnCancellation` while an in-flight
                // `view.measure()` or `view.evaluateJavascript` callback was
                // scheduled, throwing `IllegalStateException: The WebView
                // has already been destroyed` inside the export-coroutine
                // and surfacing as the runtime crash from the
                // `fix-google-signin-and-export-crash-…` branch.
                val destroyed = java.util.concurrent.atomic.AtomicBoolean(false)
                var webViewRef: WebView? = null

                fun destroyWebView() {
                    if (destroyed.get()) return
                    destroyed.set(true)
                    try { webViewRef?.destroy() } catch (_: Exception) {}
                    webViewRef = null
                }

                val webView = WebView(context).apply {
                    webViewRef = this
                    layoutParams = android.widget.FrameLayout.LayoutParams(width, height)
                    settings.apply {
                        javaScriptEnabled = true
                        loadWithOverviewMode = false
                        useWideViewPort = false
                        cacheMode = WebSettings.LOAD_NO_CACHE
                        defaultTextEncodingName = "UTF-8"
                        builtInZoomControls = false
                        displayZoomControls = false
                    }
                    setInitialScale(100)
                    setBackgroundColor(Color.WHITE)
                    isHorizontalScrollBarEnabled = false
                    isVerticalScrollBarEnabled = false
                    isScrollbarFadingEnabled = false
                }

                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        if (destroyed.get() || view == null) return
                        view.postDelayed({
                            // If cancellation happened between scheduling
                            // and execution, abort before any view.measure
                            // call — those throw IllegalStateException once
                            // the WebView has been destroy()ed.
                            if (destroyed.get()) return@postDelayed
                            try {
                                val widthSpec = android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY)
                                val heightSpec = android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED)
                                view.measure(widthSpec, heightSpec)
                                view.layout(0, 0, view.measuredWidth, view.measuredHeight)

                                view.evaluateJavascript(
                                    "(function(){var c=document.querySelector('.card');return c?c.scrollHeight:1350;})();"
                                ) { heightStr ->
                                    // JS callbacks are deferred; verify the
                                    // WebView is still alive AND the coroutine
                                    // still wants a result before trying to draw
                                    // it. Otherwise destroy and bail.
                                    if (destroyed.get()) return@evaluateJavascript
                                    try {
                                        val contentHeight = heightStr?.replace("\"", "")?.toIntOrNull() ?: 1350
                                        val actualHeight = maxOf(contentHeight, view.measuredHeight, height).coerceAtMost(2048)

                                        val bitmap = Bitmap.createBitmap(width, actualHeight, Bitmap.Config.ARGB_8888)
                                        val canvas = Canvas(bitmap)
                                        canvas.drawColor(Color.WHITE)
                                        view.draw(canvas)

                                        val scaledBitmap = if (actualHeight > height) {
                                            Bitmap.createScaledBitmap(bitmap, width, height, true).also {
                                                bitmap.recycle()
                                            }
                                        } else {
                                            bitmap
                                        }

                                        if (continuation.isActive) {
                                            continuation.resume(scaledBitmap)
                                        }
                                    } catch (e: Exception) {
                                        if (continuation.isActive) {
                                            continuation.resumeWithException(e)
                                        }
                                    } finally {
                                        destroyWebView()
                                    }
                                }
                            } catch (e: Exception) {
                                if (continuation.isActive) {
                                    continuation.resumeWithException(e)
                                }
                                destroyWebView()
                            }
                        }, 800)
                    }

                    override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(Exception("WebView error: $description"))
                        }
                        destroyWebView()
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
                    // Mark destroyed BEFORE actually calling destroy() so
                    // any in-flight callbacks (postDelayed, evaluateJavascript)
                    // observe the flag and short-circuit instead of touching
                    // a defunct WebView.
                    destroyWebView()
                }
            }
        }

    fun saveBitmap(context: Context, bitmap: Bitmap): File {
        cleanOldCacheFiles(context)
        val cacheDir = File(context.cacheDir, "share_images")
        cacheDir.mkdirs()
        val file = File(cacheDir, "debtbro-export-${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return file
    }

    private fun cleanOldCacheFiles(context: Context, maxAgeMs: Long = 24 * 60 * 60 * 1000L) {
        val cacheDir = File(context.cacheDir, "share_images")
        if (!cacheDir.exists()) return
        val now = System.currentTimeMillis()
        cacheDir.listFiles()?.forEach { file ->
            if (now - file.lastModified() > maxAgeMs) file.delete()
        }
    }

    fun getShareableUri(context: Context, bitmap: Bitmap): android.net.Uri {
        val file = saveBitmap(context, bitmap)
        return androidx.core.content.FileProvider.getUriForFile(
            context,
            "${BuildConfig.PACKAGE_NAME}.fileprovider",
            file
        )
    }

    fun shareImage(context: Context, bitmap: Bitmap) {
        val uri = getShareableUri(context, bitmap)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(Intent.createChooser(intent, "Share DebtPayoff Pro Card").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            Toast.makeText(context, "Could not share image", Toast.LENGTH_SHORT).show()
        }
    }
}