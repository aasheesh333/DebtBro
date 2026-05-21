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

        val formattedAmount = "${debt.currency}${(debt.amount - debt.amountPaid).toLong()}"
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
        
        val quoteLines = (quoteCharCount / 45.0).coerceAtLeast(1.0)
        val descLines = if (hasDesc) (descCharCount / 40.0).coerceAtLeast(1.0) else 0.0
        
        val estimatedHeight = 120 + 180 + 200 + 80 + (descLines * 28) + 100 + (quoteLines * 32) + 60 + 150
        val scaleFactor = if (estimatedHeight > 1350) (1350.0 / estimatedHeight).coerceAtMost(0.85) else 1.0
        
        val quoteFontSize = when {
            quoteCharCount <= 50 -> "2.4rem"
            quoteCharCount <= 100 -> "1.8rem"
            quoteCharCount <= 200 -> "1.4rem"
            quoteCharCount <= 350 -> "1.1rem"
            else -> "0.95rem"
        }
        
        val descFontSize = when {
            descCharCount <= 40 -> "1.4rem"
            descCharCount <= 80 -> "1.2rem"
            descCharCount <= 150 -> "1.0rem"
            descCharCount <= 250 -> "0.85rem"
            else -> "0.75rem"
        }
        
        val descLineClamp = when {
            descCharCount <= 40 -> 2
            descCharCount <= 80 -> 3
            descCharCount <= 150 -> 5
            descCharCount <= 250 -> 8
            else -> 12
        }
        
        val cardPadding = (60 * scaleFactor).toInt()
        val contentGap = (30 * scaleFactor).toInt()
        val headerMarginBottom = (40 * scaleFactor).toInt()
        val quoteMaxHeight = (280 * scaleFactor).toInt()
        val quotePadding = (40 * scaleFactor).toInt()
        val amountBoxPadding = (50 * scaleFactor).toInt()
        val detailsGap = (60 * scaleFactor).toInt()

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
            body { width: 1080px !important; height: 1350px !important; overflow: hidden !important; margin: 0 !important; padding: 0 !important; }
            .card { width: 1080px !important; height: 1350px !important; overflow: hidden !important; box-sizing: border-box !important; padding: var(--card-padding) !important; }
            .content { overflow: visible !important; gap: var(--content-gap) !important; }
            .header { margin-bottom: var(--header-margin-bottom) !important; }
            .quote-box, .note, .quote, .quote-hero { 
                width: 90% !important; 
                max-width: 900px !important; 
                min-height: 80px !important; 
                max-height: var(--quote-max-height) !important; 
                overflow: hidden !important; 
                padding: var(--quote-padding) !important;
            }
            .quote-text, .note-content { 
                width: 100% !important; 
                max-width: 800px !important; 
                word-break: break-word !important; 
                overflow-wrap: break-word !important; 
                word-wrap: break-word !important; 
                white-space: normal !important; 
                overflow: hidden !important; 
                display: block !important;
                font-size: var(--quote-font-size) !important;
                line-height: 1.35 !important;
            }
            .amount-box { padding: var(--amount-box-padding) !important; }
            .details { gap: var(--details-gap) !important; }
            .detail-item { align-items: flex-start !important; overflow: visible !important; }
            .detail-item .text-content { 
                max-width: 400px !important; 
                font-size: var(--desc-font-size) !important;
                line-height: 1.3 !important;
                display: -webkit-box !important; 
                -webkit-line-clamp: $descLineClamp !important; 
                -webkit-box-orient: vertical !important; 
                overflow: hidden !important; 
                word-break: break-word !important; 
                overflow-wrap: break-word !important; 
            }
            .party { padding: calc(25px * var(--scale-factor)) !important; }
            .party-name { font-size: clamp(calc(1.5rem * var(--scale-factor)), 4vw, calc(2.5rem * var(--scale-factor))) !important; }
            .amount { font-size: clamp(calc(3rem * var(--scale-factor)), 10vw, calc(8rem * var(--scale-factor))) !important; }
            .detail-item { font-size: calc(1.4rem * var(--scale-factor)) !important; }
            .detail-icon { font-size: calc(1.8rem * var(--scale-factor)) !important; }
        </style>
        """.trimIndent()

        return htmlContent.replace("</head>", "$cssVariables$enforceStyles</head>")
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
        withTimeout(10000L) {
            suspendCancellableCoroutine { continuation ->
                val width = 1080
                val height = 1350

                val webView = WebView(context).apply {
                    layoutParams = android.widget.FrameLayout.LayoutParams(width, height)
                    settings.apply {
                        loadWithOverviewMode = false
                        useWideViewPort = false
                        cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
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
                        view?.post {
                            try {
                                view.measure(
                                    android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY),
                                    android.view.View.MeasureSpec.makeMeasureSpec(height, android.view.View.MeasureSpec.EXACTLY)
                                )
                                view.layout(0, 0, width, height)
                                view.forceLayout()
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
                            } catch (e: Exception) {
                                if (continuation.isActive) {
                                    continuation.resumeWithException(e)
                                }
                            }
                        }
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
                    try { webView.destroy() } catch (_: Exception) {}
                }
            }
        }

    fun saveBitmap(context: Context, bitmap: Bitmap): File {
        val cacheDir = File(context.cacheDir, "share_images")
        cacheDir.mkdirs()
        val file = File(cacheDir, "debtbro-export-${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
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
            type = "image/jpeg"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(android.content.Intent.createChooser(intent, "Share DebtBro Card").apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}