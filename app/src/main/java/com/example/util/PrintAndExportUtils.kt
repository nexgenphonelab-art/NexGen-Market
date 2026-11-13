package com.example.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.print.pdf.PrintedPdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.example.R
import com.example.data.model.Order
import com.example.data.model.OrderLineItem
import com.example.data.model.Product
import com.example.data.model.Shop
import java.io.BufferedReader
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

data class ReceiptLine(
    val name: String,
    val variation: String,
    val qty: Long,
    val unitPrice: Double,
    val lineTotal: Double
)

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

object PrintAndExportUtils {

    fun formatMoney(amount: Double, currencyCode: String = "KES"): String {
        val nf = NumberFormat.getNumberInstance(Locale.US).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
        return "$currencyCode ${nf.format(amount)}"
    }

    fun formatDate(millis: Long? = null): String {
        val date = if (millis != null && millis > 0L) Date(millis) else Date()
        return SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.US).format(date)
    }

    fun buildReceiptHtml(
        shop: Shop,
        receiptNumber: String,
        dateStr: String,
        cashierName: String,
        customerName: String,
        lines: List<ReceiptLine>,
        subtotal: Double,
        discount: Double,
        tax: Double,
        total: Double,
        paymentInfo: String
    ): String {
        val widthPx = if (shop.receiptPaperWidthMm == 58L) "220px" else "300px"
        val accent = shop.receiptAccentColorHex.ifBlank { "#0077FF" }
        val rowsHtml = lines.joinToString("") { line ->
            """
            <tr>
              <td style="padding:4px 0;">
                <b>${escapeHtml(line.name)}</b><br/>
                <span style="font-size:10px;color:#555;">${escapeHtml(line.variation)} x ${line.qty} @ ${formatMoney(line.unitPrice, shop.currencyCode)}</span>
              </td>
              <td style="text-align:right;vertical-align:top;padding:4px 0;">${formatMoney(line.lineTotal, shop.currencyCode)}</td>
            </tr>
            """.trimIndent()
        }
        val taxRow = if (shop.receiptShowTaxBreakdown) {
            """<tr><td>Tax (${shop.taxRatePercent}%):</td><td style="text-align:right;">${formatMoney(tax, shop.currencyCode)}</td></tr>"""
        } else ""
        val cashierRow = if (shop.receiptShowCashier) {
            """<div>Cashier: ${escapeHtml(cashierName)}</div>"""
        } else ""
        val barcodeBlock = if (shop.receiptShowBarcode) {
            """<div style="text-align:center;margin-top:8px;letter-spacing:2px;font-family:monospace;font-size:11px;border-top:1px dashed #999;padding-top:6px;">|||| || ||| |||| ||| ||<br/>*${escapeHtml(receiptNumber)}*</div>"""
        } else ""

        return """
        <html>
        <body style="font-family:monospace;margin:0 auto;padding:12px;width:$widthPx;color:#0F172A;background:#FFFFFF;">
          <div style="height:5px;background:$accent;border-radius:4px;margin-bottom:10px;"></div>
          <div style="text-align:center;border-bottom:1.5px solid $accent;padding-bottom:10px;margin-bottom:8px;">
            ${if (shop.receiptShowLogo) "<div style='display:inline-block;padding:4px 10px;border:1.5px solid $accent;border-radius:8px;font-size:12px;font-weight:bold;color:$accent;margin-bottom:4px;'>NEXGEN MARKET • ${escapeHtml(shop.shopName)}</div>" else ""}
            <div style="font-size:15px;font-weight:bold;letter-spacing:0.5px;">${escapeHtml(shop.receiptHeaderTitle)}</div>
            ${if (shop.receiptSubtitle.isNotBlank()) "<div style='font-size:11px;color:#334155;'>${escapeHtml(shop.receiptSubtitle)}</div>" else ""}
            ${if (shop.receiptAddressLine.isNotBlank()) "<div style='font-size:10px;color:#475569;'>${escapeHtml(shop.receiptAddressLine)}</div>" else ""}
            ${if (shop.receiptPhoneLine.isNotBlank()) "<div style='font-size:10px;color:#475569;'>Tel: ${escapeHtml(shop.receiptPhoneLine)}</div>" else ""}
            ${if (shop.receiptTaxPin.isNotBlank()) "<div style='font-size:10px;color:#475569;'>${escapeHtml(shop.receiptTaxPin)}</div>" else ""}
          </div>
          <div style="font-size:11px;margin-bottom:8px;background:#F8FAFC;padding:8px;border-radius:6px;border:1px solid #E2E8F0;">
            <div><b>Receipt:</b> ${escapeHtml(receiptNumber)}</div>
            <div><b>Date:</b> $dateStr</div>
            <div><b>Customer:</b> ${escapeHtml(customerName)}</div>
            $cashierRow
          </div>
          <table style="width:100%;border-collapse:collapse;font-size:11px;border-top:1px dashed #64748B;border-bottom:1px dashed #64748B;">
            $rowsHtml
          </table>
          <table style="width:100%;font-size:11px;margin-top:8px;">
            <tr><td>Subtotal:</td><td style="text-align:right;">${formatMoney(subtotal, shop.currencyCode)}</td></tr>
            ${if (discount > 0) "<tr><td>Discount / Loyalty:</td><td style='text-align:right;color:#059669;'>-${formatMoney(discount, shop.currencyCode)}</td></tr>" else ""}
            $taxRow
            <tr style="font-size:13px;font-weight:bold;background:#F1F5F9;">
              <td style="padding:6px 4px;border-top:2px solid $accent;">TOTAL:</td>
              <td style="text-align:right;padding:6px 4px;border-top:2px solid $accent;color:$accent;">${formatMoney(total, shop.currencyCode)}</td>
            </tr>
          </table>
          <div style="font-size:11px;margin-top:8px;padding:4px 6px;background:#F8FAFC;border-radius:4px;"><b>Payment:</b> ${escapeHtml(paymentInfo)}</div>
          <div style="text-align:center;font-size:10px;margin-top:10px;color:#475569;">${escapeHtml(shop.receiptFooterMessage)}</div>
          $barcodeBlock
        </body>
        </html>
        """.trimIndent()
    }

    fun buildOrderDocumentHtml(
        shop: Shop,
        order: Order,
        lineItems: List<OrderLineItem>,
        docType: String
    ): String {
        val showPrices = docType != "PACKING SLIP"
        val rows = if (lineItems.isNotEmpty()) {
            lineItems.joinToString("") { item ->
                """
                <tr>
                  <td style="padding:6px;border-bottom:1px solid #ddd;">${escapeHtml(item.productName)} (${escapeHtml(item.variationLabel)})</td>
                  <td style="padding:6px;border-bottom:1px solid #ddd;text-align:center;">${item.quantity}</td>
                  ${if (showPrices) "<td style='padding:6px;border-bottom:1px solid #ddd;text-align:right;'>${formatMoney(item.unitPrice, shop.currencyCode)}</td><td style='padding:6px;border-bottom:1px solid #ddd;text-align:right;'>${formatMoney(item.lineTotal, shop.currencyCode)}</td>" else ""}
                </tr>
                """.trimIndent()
            }
        } else {
            """
            <tr>
              <td style="padding:6px;border-bottom:1px solid #ddd;">${escapeHtml(order.firstItemName)}</td>
              <td style="padding:6px;border-bottom:1px solid #ddd;text-align:center;">${order.itemCount}</td>
              ${if (showPrices) "<td style='padding:6px;border-bottom:1px solid #ddd;text-align:right;'>-</td><td style='padding:6px;border-bottom:1px solid #ddd;text-align:right;'>${formatMoney(order.subtotal, shop.currencyCode)}</td>" else ""}
            </tr>
            """.trimIndent()
        }

        return """
        <html>
        <body style="font-family:sans-serif;padding:20px;color:#111;">
          <div style="display:flex;justify-content:space-between;border-bottom:3px solid ${shop.receiptAccentColorHex};padding-bottom:12px;">
            <div>
              <h2 style="margin:0;">${escapeHtml(shop.receiptHeaderTitle)}</h2>
              <div>${escapeHtml(shop.receiptAddressLine)} | ${escapeHtml(shop.receiptPhoneLine)}</div>
              <div>${escapeHtml(shop.receiptTaxPin)}</div>
            </div>
            <div style="text-align:right;">
              <h2 style="margin:0;color:${shop.receiptAccentColorHex};">$docType</h2>
              <div><b>Order #:</b> ${escapeHtml(order.orderNumber)}</div>
              <div><b>Status:</b> ${escapeHtml(order.status)}</div>
              <div><b>Date:</b> ${formatDate(order.createdAt?.toDate()?.time)}</div>
            </div>
          </div>
          <div style="margin:14px 0;padding:10px;background:#f7f8fa;border-radius:6px;">
            <b>Bill / Deliver To:</b> ${escapeHtml(order.customerName)} (${escapeHtml(order.customerPhone)})<br/>
            <b>Address:</b> ${escapeHtml(order.deliveryAddress)}<br/>
            <b>Method:</b> ${escapeHtml(order.deliveryOption)} ${if (!order.assignedRider.isNullOrBlank()) "| Rider: ${escapeHtml(order.assignedRider)}" else ""}
          </div>
          <table style="width:100%;border-collapse:collapse;font-size:13px;">
            <thead>
              <tr style="background:#0f172a;color:#fff;">
                <th style="padding:8px;text-align:left;">Item Description</th>
                <th style="padding:8px;text-align:center;">Qty</th>
                ${if (showPrices) "<th style='padding:8px;text-align:right;'>Unit Price</th><th style='padding:8px;text-align:right;'>Total</th>" else ""}
              </tr>
            </thead>
            <tbody>$rows</tbody>
          </table>
          ${if (showPrices) """
          <div style="margin-top:16px;text-align:right;font-size:14px;">
            <div>Subtotal: ${formatMoney(order.subtotal, shop.currencyCode)}</div>
            <div>Discount: -${formatMoney(order.discountAmount, shop.currencyCode)}</div>
            <div>Shipping: ${formatMoney(order.shippingFee, shop.currencyCode)}</div>
            <div>Tax: ${formatMoney(order.taxAmount, shop.currencyCode)}</div>
            <h3>Grand Total: ${formatMoney(order.totalAmount, shop.currencyCode)}</h3>
            <div>Payment: ${escapeHtml(order.paymentMethod)} (${escapeHtml(order.paymentStatus)}) ${order.paymentReference ?: ""}</div>
          </div>
          """ else "<div style='margin-top:24px;'>Received By: _________________________   Date/Sign: _______________</div>"}
          <p style="margin-top:20px;font-size:11px;color:#555;text-align:center;">${escapeHtml(shop.receiptFooterMessage)}</p>
        </body>
        </html>
        """.trimIndent()
    }

    /**
     * Native PrintedPdfDocument printer for customized 58mm / 80mm thermal & A4 receipts.
     * Avoids spawning Chromium WebView renderer processes.
     */
    fun printReceipt(
        context: Context,
        jobName: String,
        shop: Shop,
        receiptNumber: String,
        dateStr: String,
        cashierName: String,
        customerName: String,
        lines: List<ReceiptLine>,
        subtotal: Double,
        discount: Double,
        tax: Double,
        total: Double,
        paymentInfo: String
    ) {
        val hostContext = context.findActivity() ?: context
        val printManager = hostContext.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        val accentColor = runCatching {
            Color.parseColor(shop.receiptAccentColorHex.ifBlank { "#0077FF" })
        }.getOrElse { Color.parseColor("#0077FF") }

        val adapter = object : PrintDocumentAdapter() {
            private var currentAttributes: PrintAttributes? = null

            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback,
                extras: Bundle?
            ) {
                currentAttributes = newAttributes
                if (cancellationSignal?.isCanceled == true) {
                    callback.onLayoutCancelled()
                    return
                }
                val info = PrintDocumentInfo.Builder("$jobName.pdf")
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                    .build()
                callback.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>,
                destination: ParcelFileDescriptor,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback
            ) {
                val attrs = currentAttributes ?: PrintAttributes.Builder().build()
                val pdfDoc = PrintedPdfDocument(hostContext, attrs)
                try {
                    val pageWidth = pdfDoc.pageWidth.coerceAtLeast(320)
                    val pageHeight = pdfDoc.pageHeight.coerceAtLeast(480)
                    val receiptMaxWidth = if (shop.receiptPaperWidthMm == 58L) 210f else 285f
                    val colWidth = receiptMaxWidth.coerceAtMost(pageWidth - 48f)
                    val left = ((pageWidth - colWidth) / 2f).coerceAtLeast(24f)
                    val right = left + colWidth
                    val centerX = left + colWidth / 2f
                    val bottomMargin = pageHeight - 36f

                    val monoTypeface = Typeface.MONOSPACE
                    val logoPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = accentColor
                        textSize = 11f
                        typeface = Typeface.create(monoTypeface, Typeface.BOLD)
                        textAlign = Paint.Align.CENTER
                    }
                    val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#0F172A")
                        textSize = 11f
                        typeface = Typeface.create(monoTypeface, Typeface.BOLD)
                        textAlign = Paint.Align.CENTER
                    }
                    val centerSubPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#334155")
                        textSize = 8.5f
                        typeface = monoTypeface
                        textAlign = Paint.Align.CENTER
                    }
                    val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#0F172A")
                        textSize = 8.5f
                        typeface = monoTypeface
                    }
                    val boldBodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#0F172A")
                        textSize = 8.5f
                        typeface = Typeface.create(monoTypeface, Typeface.BOLD)
                    }
                    val mutedPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#475569")
                        textSize = 7.5f
                        typeface = monoTypeface
                    }
                    val totalPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#0F172A")
                        textSize = 10.5f
                        typeface = Typeface.create(monoTypeface, Typeface.BOLD)
                    }
                    val accentDivider = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = accentColor
                        strokeWidth = 1.8f
                    }
                    val dashedDivider = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#64748B")
                        strokeWidth = 0.8f
                        pathEffect = DashPathEffect(floatArrayOf(4f, 3f), 0f)
                    }

                    val accentBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = accentColor
                    }
                    val metaBoxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#F8FAFC")
                    }
                    val metaBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#E2E8F0")
                        style = Paint.Style.STROKE
                        strokeWidth = 0.8f
                    }
                    val totalBannerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.argb(24, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
                    }
                    val logoBitmap: Bitmap? = if (shop.receiptShowLogo) {
                        runCatching {
                            BitmapFactory.decodeResource(hostContext.resources, R.drawable.img_nexgen_logo)?.let { raw ->
                                Bitmap.createScaledBitmap(raw, 44, 44, true)
                            }
                        }.getOrNull()
                    } else null

                    var pageNumber = 1
                    var page = pdfDoc.startPage(pageNumber)
                    var canvas = page.canvas
                    var y = 32f

                    fun ensureSpace(needed: Float) {
                        if (y + needed > bottomMargin) {
                            pdfDoc.finishPage(page)
                            pageNumber++
                            page = pdfDoc.startPage(pageNumber)
                            canvas = page.canvas
                            y = 32f
                        }
                    }

                    // Top colored receipt accent ribbon
                    canvas.drawRoundRect(left, y, right, y + 4f, 2f, 2f, accentBarPaint)
                    y += 10f

                    if (shop.receiptShowLogo) {
                        if (logoBitmap != null) {
                            val imgSize = 42f
                            val imgLeft = centerX - imgSize / 2f
                            val frameRect = RectF(imgLeft - 2f, y - 2f, imgLeft + imgSize + 2f, y + imgSize + 2f)
                            canvas.drawRoundRect(frameRect, 8f, 8f, metaBorderPaint)
                            canvas.drawBitmap(logoBitmap, null, RectF(imgLeft, y, imgLeft + imgSize, y + imgSize), null)
                            y += imgSize + 6f
                        }
                        y = drawCenteredWrappedText(canvas, shop.shopName.uppercase(Locale.US), centerX, y, colWidth, logoPaint)
                        y += 2f
                    }
                    y = drawCenteredWrappedText(canvas, shop.receiptHeaderTitle, centerX, y, colWidth, headerPaint)
                    if (shop.receiptSubtitle.isNotBlank()) {
                        y = drawCenteredWrappedText(canvas, shop.receiptSubtitle, centerX, y, colWidth, centerSubPaint)
                    }
                    if (shop.receiptAddressLine.isNotBlank()) {
                        y = drawCenteredWrappedText(canvas, shop.receiptAddressLine, centerX, y, colWidth, centerSubPaint)
                    }
                    if (shop.receiptPhoneLine.isNotBlank()) {
                        y = drawCenteredWrappedText(canvas, "Tel: ${shop.receiptPhoneLine}", centerX, y, colWidth, centerSubPaint)
                    }
                    if (shop.receiptTaxPin.isNotBlank()) {
                        y = drawCenteredWrappedText(canvas, shop.receiptTaxPin, centerX, y, colWidth, centerSubPaint)
                    }

                    y += 4f
                    canvas.drawLine(left, y, right, y, accentDivider)
                    y += 8f

                    // Structured metadata card
                    val metaRows = mutableListOf(
                        "Receipt: $receiptNumber",
                        "Date: $dateStr",
                        "Customer: $customerName"
                    )
                    if (shop.receiptShowCashier) metaRows.add("Cashier: $cashierName")
                    val metaHeight = metaRows.size * 12f + 10f
                    val metaRect = RectF(left, y, right, y + metaHeight)
                    canvas.drawRoundRect(metaRect, 5f, 5f, metaBoxPaint)
                    canvas.drawRoundRect(metaRect, 5f, 5f, metaBorderPaint)
                    var my = y + 5f
                    metaRows.forEach { rowTxt ->
                        my = drawWrappedText(canvas, rowTxt, left + 6f, my, colWidth - 12f, bodyPaint)
                    }
                    y += metaHeight + 8f

                    canvas.drawLine(left, y, right, y, dashedDivider)
                    y += 10f

                    for (line in lines) {
                        ensureSpace(34f)
                        val totalTxt = formatMoney(line.lineTotal, shop.currencyCode)
                        val priceWidth = boldBodyPaint.measureText(totalTxt) + 8f
                        val nameWidth = (colWidth - priceWidth).coerceAtLeast(80f)
                        val nextY = drawWrappedText(canvas, line.name, left, y, nameWidth, boldBodyPaint)
                        canvas.drawText(totalTxt, right - boldBodyPaint.measureText(totalTxt), y + 8f, boldBodyPaint)
                        y = nextY
                        y = drawWrappedText(
                            canvas,
                            "${line.variation} x${line.qty} @ ${formatMoney(line.unitPrice, shop.currencyCode)}",
                            left,
                            y,
                            colWidth,
                            mutedPaint
                        )
                        y += 3f
                    }

                    ensureSpace(88f)
                    y += 2f
                    canvas.drawLine(left, y, right, y, dashedDivider)
                    y += 12f

                    y = drawLeftRightLine(canvas, "Subtotal:", formatMoney(subtotal, shop.currencyCode), left, right, y, bodyPaint)
                    if (discount > 0.0) {
                        y = drawLeftRightLine(canvas, "Discount / Loyalty:", "-${formatMoney(discount, shop.currencyCode)}", left, right, y, bodyPaint)
                    }
                    if (shop.receiptShowTaxBreakdown) {
                        y = drawLeftRightLine(canvas, "Tax (${shop.taxRatePercent}%):", formatMoney(tax, shop.currencyCode), left, right, y, bodyPaint)
                    }
                    y += 4f
                    val totalRect = RectF(left, y, right, y + 22f)
                    canvas.drawRoundRect(totalRect, 5f, 5f, totalBannerPaint)
                    canvas.drawLine(left, y, right, y, accentDivider)
                    y += 4f
                    val accentTotalPaint = TextPaint(totalPaint).apply { color = accentColor }
                    drawLeftRightLine(canvas, "TOTAL:", formatMoney(total, shop.currencyCode), left + 6f, right - 6f, y, accentTotalPaint)
                    y += 22f
                    y = drawWrappedText(canvas, "Payment: $paymentInfo", left, y, colWidth, bodyPaint)

                    y += 8f
                    if (shop.receiptFooterMessage.isNotBlank()) {
                        ensureSpace(34f)
                        y = drawCenteredWrappedText(canvas, shop.receiptFooterMessage, centerX, y, colWidth, centerSubPaint)
                    }

                    if (shop.receiptShowBarcode) {
                        ensureSpace(44f)
                        y += 6f
                        drawPdfBarcode(canvas, receiptNumber, left + 12f, y, (colWidth - 24f).coerceAtLeast(80f), 24f)
                        y += 32f
                        drawCenteredWrappedText(canvas, "*$receiptNumber*", centerX, y, colWidth, centerSubPaint)
                    }

                    pdfDoc.finishPage(page)

                    if (cancellationSignal?.isCanceled == true) {
                        callback.onWriteCancelled()
                        return
                    }
                    FileOutputStream(destination.fileDescriptor).use { out ->
                        pdfDoc.writeTo(out)
                    }
                    callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback.onWriteFailed(e.localizedMessage ?: "Receipt print failed")
                } finally {
                    pdfDoc.close()
                }
            }
        }

        runCatching {
            printManager.print(jobName, adapter, PrintAttributes.Builder().build())
        }
    }

    /**
     * Native PrintedPdfDocument printer for A4 Invoices, Packing Slips, and Delivery Notes.
     * Avoids spawning Chromium WebView renderer processes.
     */
    fun printOrderDocument(
        context: Context,
        jobName: String,
        shop: Shop,
        order: Order,
        lineItems: List<OrderLineItem>,
        docType: String
    ) {
        val hostContext = context.findActivity() ?: context
        val printManager = hostContext.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        val accentColor = runCatching {
            Color.parseColor(shop.receiptAccentColorHex.ifBlank { "#0077FF" })
        }.getOrElse { Color.parseColor("#0077FF") }
        val showPrices = docType != "PACKING SLIP"

        val adapter = object : PrintDocumentAdapter() {
            private var currentAttributes: PrintAttributes? = null

            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback,
                extras: Bundle?
            ) {
                currentAttributes = newAttributes
                if (cancellationSignal?.isCanceled == true) {
                    callback.onLayoutCancelled()
                    return
                }
                val info = PrintDocumentInfo.Builder("$jobName.pdf")
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                    .build()
                callback.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>,
                destination: ParcelFileDescriptor,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback
            ) {
                val attrs = currentAttributes ?: PrintAttributes.Builder().build()
                val pdfDoc = PrintedPdfDocument(hostContext, attrs)
                try {
                    val pageWidth = pdfDoc.pageWidth.coerceAtLeast(520)
                    val pageHeight = pdfDoc.pageHeight.coerceAtLeast(700)
                    val margin = 36f
                    val contentWidth = pageWidth - margin * 2f
                    val right = pageWidth - margin
                    val bottomMargin = pageHeight - 40f

                    val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#0F172A")
                        textSize = 15f
                        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                    }
                    val docTitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = accentColor
                        textSize = 15f
                        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                        textAlign = Paint.Align.RIGHT
                    }
                    val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#1E293B")
                        textSize = 9.5f
                    }
                    val boldPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#0F172A")
                        textSize = 9.5f
                        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                    }
                    val mutedPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#475569")
                        textSize = 8.5f
                    }
                    val headerBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#0F172A")
                    }
                    val headerTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.WHITE
                        textSize = 9f
                        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                    }
                    val cardBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#F1F5F9")
                    }
                    val accentLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = accentColor
                        strokeWidth = 2f
                    }
                    val rowLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#CBD5E1")
                        strokeWidth = 0.8f
                    }

                    var pageNumber = 1
                    var page = pdfDoc.startPage(pageNumber)
                    var canvas = page.canvas
                    var y = 44f

                    fun ensureSpace(needed: Float) {
                        if (y + needed > bottomMargin) {
                            pdfDoc.finishPage(page)
                            pageNumber++
                            page = pdfDoc.startPage(pageNumber)
                            canvas = page.canvas
                            y = 44f
                        }
                    }

                    val logoBitmap: Bitmap? = if (shop.receiptShowLogo) {
                        runCatching {
                            BitmapFactory.decodeResource(hostContext.resources, R.drawable.img_nexgen_logo)?.let { raw ->
                                Bitmap.createScaledBitmap(raw, 42, 42, true)
                            }
                        }.getOrNull()
                    } else null

                    // Header Left & Right
                    val headerTextLeft = if (logoBitmap != null) {
                        canvas.drawBitmap(logoBitmap, null, RectF(margin, y - 14f, margin + 36f, y + 22f), null)
                        margin + 44f
                    } else margin

                    canvas.drawText(shop.receiptHeaderTitle, headerTextLeft, y, titlePaint)
                    canvas.drawText(docType, right, y, docTitlePaint)
                    y += 14f
                    val shopMeta = listOf(shop.receiptAddressLine, shop.receiptPhoneLine).filter { it.isNotBlank() }.joinToString(" | ")
                    if (shopMeta.isNotBlank()) {
                        canvas.drawText(shopMeta, headerTextLeft, y, mutedPaint)
                    }
                    val ordMeta = "Order #: ${order.orderNumber}"
                    canvas.drawText(ordMeta, right - boldPaint.measureText(ordMeta), y, boldPaint)
                    y += 12f
                    if (shop.receiptTaxPin.isNotBlank()) {
                        canvas.drawText(shop.receiptTaxPin, headerTextLeft, y, mutedPaint)
                    }
                    val statusMeta = "Status: ${order.status} | ${formatDate(order.createdAt?.toDate()?.time)}"
                    canvas.drawText(statusMeta, right - mutedPaint.measureText(statusMeta), y, mutedPaint)
                    y += 12f
                    canvas.drawLine(margin, y, right, y, accentLinePaint)
                    y += 14f

                    // Bill / Deliver To card
                    val riderInfo = if (!order.assignedRider.isNullOrBlank()) " | Rider: ${order.assignedRider}" else ""
                    val billLines = listOf(
                        "Bill / Deliver To: ${order.customerName} (${order.customerPhone})",
                        "Address: ${order.deliveryAddress}",
                        "Method: ${order.deliveryOption}$riderInfo"
                    )
                    val cardHeight = 52f
                    canvas.drawRoundRect(margin, y, right, y + cardHeight, 6f, 6f, cardBgPaint)
                    var cardY = y + 15f
                    billLines.forEach { ln ->
                        canvas.drawText(ln.take(90), margin + 10f, cardY, bodyPaint)
                        cardY += 13f
                    }
                    y += cardHeight + 14f

                    // Table header
                    val tableHeaderH = 22f
                    canvas.drawRect(margin, y, right, y + tableHeaderH, headerBgPaint)
                    val colQtyX = if (showPrices) right - 180f else right - 44f
                    val colUnitX = right - 105f
                    canvas.drawText("Item Description", margin + 8f, y + 14f, headerTextPaint)
                    canvas.drawText("Qty", colQtyX, y + 14f, headerTextPaint)
                    if (showPrices) {
                        canvas.drawText("Unit Price", colUnitX - headerTextPaint.measureText("Unit Price"), y + 14f, headerTextPaint)
                        canvas.drawText("Total", right - 8f - headerTextPaint.measureText("Total"), y + 14f, headerTextPaint)
                    }
                    y += tableHeaderH + 8f

                    val itemsToDraw = if (lineItems.isNotEmpty()) {
                        lineItems.map { Triple("${it.productName} (${it.variationLabel})", it.quantity, Pair(it.unitPrice, it.lineTotal)) }
                    } else {
                        listOf(Triple(order.firstItemName, order.itemCount.coerceAtLeast(1L), Pair(0.0, order.subtotal)))
                    }

                    for ((label, qty, prices) in itemsToDraw) {
                        ensureSpace(28f)
                        val descMaxW = (colQtyX - margin - 14f).coerceAtLeast(140f)
                        val nextY = drawWrappedText(canvas, label, margin + 8f, y, descMaxW, bodyPaint)
                        canvas.drawText(qty.toString(), colQtyX + 4f, y + 9f, bodyPaint)
                        if (showPrices) {
                            val unitStr = if (prices.first > 0.0) formatMoney(prices.first, shop.currencyCode) else "-"
                            val totStr = formatMoney(prices.second, shop.currencyCode)
                            canvas.drawText(unitStr, colUnitX - bodyPaint.measureText(unitStr), y + 9f, bodyPaint)
                            canvas.drawText(totStr, right - 8f - boldPaint.measureText(totStr), y + 9f, boldPaint)
                        }
                        y = maxOf(nextY, y + 14f) + 4f
                        canvas.drawLine(margin, y, right, y, rowLinePaint)
                        y += 8f
                    }

                    ensureSpace(105f)
                    y += 8f
                    if (showPrices) {
                        val summaryLines = listOf(
                            "Subtotal:" to formatMoney(order.subtotal, shop.currencyCode),
                            "Discount:" to "-${formatMoney(order.discountAmount, shop.currencyCode)}",
                            "Shipping:" to formatMoney(order.shippingFee, shop.currencyCode),
                            "Tax:" to formatMoney(order.taxAmount, shop.currencyCode)
                        )
                        val sumLeft = (right - 220f).coerceAtLeast(margin)
                        summaryLines.forEach { (k, v) ->
                            y = drawLeftRightLine(canvas, k, v, sumLeft, right, y, bodyPaint)
                        }
                        y += 4f
                        canvas.drawLine(sumLeft, y, right, y, accentLinePaint)
                        y += 14f
                        y = drawLeftRightLine(canvas, "Grand Total:", formatMoney(order.totalAmount, shop.currencyCode), sumLeft, right, y, titlePaint)
                        y += 4f
                        val payText = "Payment: ${order.paymentMethod} (${order.paymentStatus}) ${order.paymentReference ?: ""}".trim()
                        canvas.drawText(payText, right - mutedPaint.measureText(payText), y, mutedPaint)
                        y += 18f
                    } else {
                        canvas.drawText("Received By: _______________________   Date / Sign: _______________", margin, y + 12f, bodyPaint)
                        y += 28f
                    }

                    if (shop.receiptFooterMessage.isNotBlank()) {
                        val footerPaint = TextPaint(mutedPaint).apply { textAlign = Paint.Align.CENTER }
                        drawCenteredWrappedText(canvas, shop.receiptFooterMessage, margin + contentWidth / 2f, y + 8f, contentWidth, footerPaint)
                    }

                    pdfDoc.finishPage(page)

                    if (cancellationSignal?.isCanceled == true) {
                        callback.onWriteCancelled()
                        return
                    }
                    FileOutputStream(destination.fileDescriptor).use { out ->
                        pdfDoc.writeTo(out)
                    }
                    callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback.onWriteFailed(e.localizedMessage ?: "Order document print failed")
                } finally {
                    pdfDoc.close()
                }
            }
        }

        runCatching {
            printManager.print(jobName, adapter, PrintAttributes.Builder().build())
        }
    }

    /**
     * WebView-free HTML-to-PDF PrintManager fallback so no Chromium renderer process is ever launched.
     */
    fun printHtml(context: Context, jobName: String, htmlContent: String) {
        val hostContext = context.findActivity() ?: context
        val printManager = hostContext.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        val plainLines = htmlContent
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</(div|p|tr|h[1-6]|li)>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<td[^>]*>", RegexOption.IGNORE_CASE), "  ")
            .replace(Regex("<[^>]+>"), "")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val adapter = object : PrintDocumentAdapter() {
            private var currentAttributes: PrintAttributes? = null

            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback,
                extras: Bundle?
            ) {
                currentAttributes = newAttributes
                if (cancellationSignal?.isCanceled == true) {
                    callback.onLayoutCancelled()
                    return
                }
                val info = PrintDocumentInfo.Builder("$jobName.pdf")
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                    .build()
                callback.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>,
                destination: ParcelFileDescriptor,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback
            ) {
                val attrs = currentAttributes ?: PrintAttributes.Builder().build()
                val pdfDoc = PrintedPdfDocument(hostContext, attrs)
                try {
                    val margin = 36f
                    val colWidth = (pdfDoc.pageWidth - margin * 2f).coerceAtLeast(200f)
                    val bottomMargin = pdfDoc.pageHeight - 36f
                    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#0F172A")
                        textSize = 10f
                        typeface = Typeface.MONOSPACE
                    }
                    var pageNum = 1
                    var page = pdfDoc.startPage(pageNum)
                    var canvas = page.canvas
                    var y = 40f

                    for (line in plainLines) {
                        if (y > bottomMargin - 20f) {
                            pdfDoc.finishPage(page)
                            pageNum++
                            page = pdfDoc.startPage(pageNum)
                            canvas = page.canvas
                            y = 40f
                        }
                        y = drawWrappedText(canvas, line, margin, y, colWidth, paint) + 2f
                    }
                    pdfDoc.finishPage(page)
                    FileOutputStream(destination.fileDescriptor).use { out ->
                        pdfDoc.writeTo(out)
                    }
                    callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback.onWriteFailed(e.localizedMessage ?: "Print failed")
                } finally {
                    pdfDoc.close()
                }
            }
        }

        runCatching {
            printManager.print(jobName, adapter, PrintAttributes.Builder().build())
        }
    }

    private fun drawWrappedText(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        maxWidth: Float,
        paint: TextPaint
    ): Float {
        if (text.isBlank()) return y
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, maxWidth.toInt().coerceAtLeast(40))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.15f)
            .build()
        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
        return y + layout.height + 2f
    }

    private fun drawCenteredWrappedText(
        canvas: Canvas,
        text: String,
        centerX: Float,
        y: Float,
        maxWidth: Float,
        paint: TextPaint
    ): Float {
        if (text.isBlank()) return y
        val centeredPaint = TextPaint(paint).apply { textAlign = Paint.Align.LEFT }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, centeredPaint, maxWidth.toInt().coerceAtLeast(40))
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(0f, 1.15f)
            .build()
        canvas.save()
        canvas.translate(centerX - maxWidth / 2f, y)
        layout.draw(canvas)
        canvas.restore()
        return y + layout.height + 2f
    }

    private fun drawLeftRightLine(
        canvas: Canvas,
        leftText: String,
        rightText: String,
        left: Float,
        right: Float,
        y: Float,
        paint: TextPaint
    ): Float {
        val baseline = y + paint.textSize
        canvas.drawText(leftText, left, baseline, paint)
        canvas.drawText(rightText, right - paint.measureText(rightText), baseline, paint)
        return baseline + 5f
    }

    private fun drawPdfBarcode(
        canvas: Canvas,
        code: String,
        left: Float,
        top: Float,
        width: Float,
        height: Float
    ) {
        val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
        val seed = code.ifBlank { "600123456789" }
        val bars = 44
        val barUnit = width / (bars * 2f)
        var x = left
        val maxX = left + width
        for (i in 0 until bars) {
            val ch = seed[i % seed.length].code
            val wFactor = ((abs(ch * (i + 3)) % 3) + 1).toFloat()
            val barW = (barUnit * wFactor).coerceAtMost(maxX - x)
            if (i % 2 == 0 && barW > 0f) {
                canvas.drawRect(x, top, x + barW, top + height, barPaint)
            }
            x += barUnit * (wFactor * 0.9f + 0.8f)
            if (x >= maxX) break
        }
    }

    fun buildEscPosReceiptText(
        shop: Shop,
        receiptNumber: String,
        cashier: String,
        customer: String,
        lines: List<ReceiptLine>,
        total: Double,
        paymentInfo: String
    ): String {
        val sb = StringBuilder()
        sb.appendLine("================================")
        sb.appendLine(shop.receiptHeaderTitle.uppercase(Locale.US))
        if (shop.receiptSubtitle.isNotBlank()) sb.appendLine(shop.receiptSubtitle)
        if (shop.receiptAddressLine.isNotBlank()) sb.appendLine(shop.receiptAddressLine)
        if (shop.receiptPhoneLine.isNotBlank()) sb.appendLine("Tel: ${shop.receiptPhoneLine}")
        sb.appendLine("--------------------------------")
        sb.appendLine("RCP: $receiptNumber")
        sb.appendLine("Cashier: $cashier | Cust: $customer")
        sb.appendLine("--------------------------------")
        lines.forEach {
            sb.appendLine("${it.name} x${it.qty} = ${formatMoney(it.lineTotal, shop.currencyCode)}")
        }
        sb.appendLine("--------------------------------")
        sb.appendLine("TOTAL: ${formatMoney(total, shop.currencyCode)}")
        sb.appendLine("PAID: $paymentInfo")
        sb.appendLine("--------------------------------")
        sb.appendLine(shop.receiptFooterMessage)
        sb.appendLine("================================")
        return sb.toString()
    }

    fun exportPdfReportToUri(
        context: Context,
        uri: Uri,
        reportTitle: String,
        summaryLines: List<String>,
        tableLines: List<String>
    ): Result<Unit> = runCatching {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        var page = document.startPage(pageInfo)
        var canvas: Canvas = page.canvas

        val titlePaint = Paint().apply {
            color = Color.parseColor("#0F172A")
            textSize = 18f
            isFakeBoldText = true
        }
        val bodyPaint = Paint().apply {
            color = Color.parseColor("#334155")
            textSize = 11f
        }
        val accentPaint = Paint().apply {
            color = Color.parseColor("#FF472E")
            strokeWidth = 2f
        }

        var y = 48f
        canvas.drawText(reportTitle, 40f, y, titlePaint)
        y += 10f
        canvas.drawLine(40f, y, 555f, y, accentPaint)
        y += 22f
        canvas.drawText("Generated: ${formatDate()}", 40f, y, bodyPaint)
        y += 22f

        for (line in summaryLines + listOf("--- Details ---") + tableLines) {
            if (y > 790f) {
                document.finishPage(page)
                page = document.startPage(pageInfo)
                canvas = page.canvas
                y = 48f
            }
            canvas.drawText(line.take(92), 40f, y, bodyPaint)
            y += 16f
        }
        document.finishPage(page)

        context.contentResolver.openOutputStream(uri)?.use { out ->
            document.writeTo(out)
        } ?: throw IllegalStateException("Unable to open output stream")
        document.close()
    }

    fun exportCsvToUri(
        context: Context,
        uri: Uri,
        headers: List<String>,
        rows: List<List<String>>
    ): Result<Unit> = runCatching {
        context.contentResolver.openOutputStream(uri)?.use { out ->
            val sb = StringBuilder()
            sb.appendLine(headers.joinToString(",") { csvEscape(it) })
            rows.forEach { row ->
                sb.appendLine(row.joinToString(",") { csvEscape(it) })
            }
            out.write(sb.toString().toByteArray(Charsets.UTF_8))
        } ?: throw IllegalStateException("Unable to open CSV output stream")
    }

    fun parseProductsFromCsv(context: Context, uri: Uri, shop: Shop): Result<List<Product>> = runCatching {
        val result = mutableListOf<Product>()
        context.contentResolver.openInputStream(uri)?.use { input ->
            BufferedReader(InputStreamReader(input)).use { reader ->
                val lines = reader.readLines()
                lines.drop(1).forEach { rawLine ->
                    if (rawLine.isBlank()) return@forEach
                    val cols = rawLine.split(",").map { it.trim().removeSurrounding("\"") }
                    if (cols.size >= 4) {
                        val selling = cols.getOrNull(4)?.toDoubleOrNull() ?: 100.0
                        val cost = cols.getOrNull(5)?.toDoubleOrNull() ?: (selling * 0.65)
                        val stock = cols.getOrNull(6)?.toLongOrNull() ?: 10L
                        result.add(
                            Product(
                                shopId = shop.id,
                                shopOwnerId = shop.ownerId,
                                shopName = shop.shopName,
                                name = cols[0].ifBlank { "Imported Product" },
                                category = cols.getOrNull(1)?.ifBlank { "Electronics" } ?: "Electronics",
                                sku = cols.getOrNull(2)?.ifBlank { "IMP-${(1000..9999).random()}" } ?: "IMP-${(1000..9999).random()}",
                                barcode = cols.getOrNull(3)?.ifBlank { "600${(100000000..999999999).random()}" } ?: "",
                                sellingPrice = selling,
                                originalPrice = selling * 1.15,
                                costPrice = cost,
                                stockQuantity = stock,
                                minStockLevel = 3L,
                                description = cols.getOrNull(7) ?: "Imported via bulk CSV upload",
                                isVisible = true
                            )
                        )
                    }
                }
            }
        } ?: throw IllegalStateException("Cannot read selected CSV file")
        result
    }

    fun openMapsLocation(context: Context, addressQuery: String) {
        val uri = Uri.parse("geo:0,0?q=${Uri.encode(addressQuery)}")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        runCatching { context.startActivity(intent) }
    }

    fun shareText(context: Context, subject: String, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, subject))
    }

    private fun csvEscape(value: String): String {
        val cleaned = value.replace("\"", "\"\"")
        return "\"$cleaned\""
    }

    private fun escapeHtml(input: String): String {
        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }
}
