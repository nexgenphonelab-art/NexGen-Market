package com.example.ui.business

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.Order
import com.example.data.model.OrderLineItem
import com.example.data.model.RefundRequest
import com.example.data.model.Shop
import com.example.ui.theme.NexGenAmber
import com.example.ui.theme.NexGenCobalt
import com.example.ui.theme.NexGenCoral
import com.example.ui.theme.NexGenEmerald
import com.example.ui.viewmodel.FinancialSummary
import com.example.util.PrintAndExportUtils
import kotlinx.coroutines.launch
import java.util.Locale

private val ALL_ORDER_STATUSES = listOf(
    "Pending", "Awaiting Payment", "Paid", "Processing", "Packed",
    "Ready for Dispatch", "Shipped", "Out for Delivery", "Delivered",
    "Cancelled", "Return Requested", "Refunded"
)

@Composable
fun BusinessDashboardScreen(
    shop: Shop,
    summary: FinancialSummary,
    orders: List<Order>
) {
    val context = LocalContext.current

    val pdfExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) {
            val sumLines = listOf(
                "Shop: ${shop.shopName} (${shop.currencyCode})",
                "Total Revenue: ${PrintAndExportUtils.formatMoney(summary.totalRevenue, shop.currencyCode)}",
                "POS Sales: ${PrintAndExportUtils.formatMoney(summary.posRevenue, shop.currencyCode)} (${summary.totalPosCount} receipts)",
                "Online Orders: ${PrintAndExportUtils.formatMoney(summary.onlineRevenue, shop.currencyCode)} (${summary.totalOrdersCount} orders)",
                "COGS: ${PrintAndExportUtils.formatMoney(summary.cogsTotal, shop.currencyCode)}",
                "Gross Profit: ${PrintAndExportUtils.formatMoney(summary.grossProfit, shop.currencyCode)}",
                "Operating Expenses: ${PrintAndExportUtils.formatMoney(summary.operatingExpenses, shop.currencyCode)}",
                "Net Profit: ${PrintAndExportUtils.formatMoney(summary.netProfit, shop.currencyCode)}",
                "Stock Valuation: ${PrintAndExportUtils.formatMoney(summary.stockValuation, shop.currencyCode)}",
                "Shop Assets Valuation: ${PrintAndExportUtils.formatMoney(summary.assetsValuation, shop.currencyCode)}",
                "Total Shop Worth: ${PrintAndExportUtils.formatMoney(summary.totalShopWorth, shop.currencyCode)}",
                "Outstanding Payments: ${PrintAndExportUtils.formatMoney(summary.outstandingPayments, shop.currencyCode)}",
                "Customer Debts: ${PrintAndExportUtils.formatMoney(summary.customerDebts, shop.currencyCode)}",
                "Supplier Payables: ${PrintAndExportUtils.formatMoney(summary.supplierPayables, shop.currencyCode)}"
            )
            val details = orders.take(40).map {
                "${it.orderNumber} | ${it.customerName} | ${it.status} | ${it.paymentStatus} | ${PrintAndExportUtils.formatMoney(it.totalAmount, shop.currencyCode)}"
            }
            PrintAndExportUtils.exportPdfReportToUri(context, uri, "${shop.shopName} Financial Report", sumLines, details)
        }
    }

    val csvReportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            PrintAndExportUtils.exportCsvToUri(
                context = context,
                uri = uri,
                headers = listOf("OrderNumber", "Customer", "Status", "PaymentStatus", "PaymentMethod", "Total", "COGS"),
                rows = orders.map {
                    listOf(it.orderNumber, it.customerName, it.status, it.paymentStatus, it.paymentMethod, it.totalAmount.toString(), it.cogsTotal.toString())
                }
            )
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { pdfExportLauncher.launch("${shop.shopName.replace(" ", "_")}_report.pdf") },
                    colors = ButtonDefaults.buttonColors(containerColor = NexGenCoral),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Export PDF")
                }
                OutlinedButton(
                    onClick = { csvReportLauncher.launch("${shop.shopName.replace(" ", "_")}_sales.csv") },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.TableChart, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Export Excel CSV")
                }
            }
        }

        // Key Metrics Grid (Computed 100% live from Firestore)
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KpiCard("Total Revenue", PrintAndExportUtils.formatMoney(summary.totalRevenue, shop.currencyCode), NexGenCoral, Modifier.weight(1f))
                KpiCard("Net Profit", PrintAndExportUtils.formatMoney(summary.netProfit, shop.currencyCode), NexGenEmerald, Modifier.weight(1f))
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KpiCard("POS Counter", PrintAndExportUtils.formatMoney(summary.posRevenue, shop.currencyCode), NexGenCobalt, Modifier.weight(1f))
                KpiCard("Online Store", PrintAndExportUtils.formatMoney(summary.onlineRevenue, shop.currencyCode), NexGenAmber, Modifier.weight(1f))
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KpiCard("Stock Valuation", PrintAndExportUtils.formatMoney(summary.stockValuation, shop.currencyCode), NexGenCobalt, Modifier.weight(1f))
                KpiCard("Expenses", PrintAndExportUtils.formatMoney(summary.operatingExpenses, shop.currencyCode), MaterialTheme.colorScheme.error, Modifier.weight(1f))
            }
        }

        // Total Shop Worth & Asset Valuation Card
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.AccountBalance, contentDescription = null, tint = NexGenCobalt)
                            Spacer(Modifier.width(8.dp))
                            Text("Total Shop Worth & Net Valuation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            PrintAndExportUtils.formatMoney(summary.totalShopWorth, shop.currencyCode),
                            style = MaterialTheme.typography.titleMedium,
                            color = NexGenEmerald,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        "Worth = Inventory Cost Valuation + Physical Shop Assets + Receivables + Cash Flow - Supplier Payables",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider()
                    LedgerRow("Inventory Valuation (Stock at Cost)", PrintAndExportUtils.formatMoney(summary.stockValuation, shop.currencyCode))
                    LedgerRow("Shop Assets & Equipment Value", PrintAndExportUtils.formatMoney(summary.assetsValuation, shop.currencyCode))
                    LedgerRow("Net Cash Flow", PrintAndExportUtils.formatMoney(summary.cashFlowNet, shop.currencyCode))
                    LedgerRow("Customer Credit Receivables", PrintAndExportUtils.formatMoney(summary.customerDebts, shop.currencyCode))
                    LedgerRow("Supplier Payables (-)", PrintAndExportUtils.formatMoney(summary.supplierPayables, shop.currencyCode))
                }
            }
        }

        // Interactive Time-Series Sales Graph (7D / 30D / 12M)
        item {
            var salesRange by remember { mutableStateOf("7D") }
            val activeSeries = when (salesRange) {
                "30D" -> summary.salesSeries30d
                "12M" -> summary.salesSeries12m
                else -> summary.salesSeries7d
            }
            val peakPoint = activeSeries.maxByOrNull { it.revenue }
            val seriesTotal = activeSeries.sumOf { it.revenue }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.testTag("sales_graph_card")
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.ShowChart, contentDescription = null, tint = NexGenCoral)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text("Sales Revenue Trend", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    "Period Total: ${PrintAndExportUtils.formatMoney(seriesTotal, shop.currencyCode)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("7D", "30D", "12M").forEach { range ->
                                FilterChip(
                                    selected = salesRange == range,
                                    onClick = { salesRange = range },
                                    label = { Text(range) }
                                )
                            }
                        }
                    }

                    if (activeSeries.isEmpty() || seriesTotal <= 0.0) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Complete POS or online orders to plot your live $salesRange sales curve.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        val maxRev = activeSeries.maxOf { it.revenue }.coerceAtLeast(1.0)
                        Canvas(modifier = Modifier.fillMaxWidth().height(160.dp)) {
                            val padX = 12f
                            val padY = 16f
                            val plotW = (size.width - padX * 2f).coerceAtLeast(20f)
                            val plotH = (size.height - padY * 2f).coerceAtLeast(20f)
                            val gridColor = Color(0xFF94A3B8).copy(alpha = 0.25f)
                            for (g in 0..3) {
                                val gy = padY + plotH * (g / 3f)
                                drawLine(gridColor, Offset(padX, gy), Offset(padX + plotW, gy), strokeWidth = 1f)
                            }
                            val stepX = if (activeSeries.size > 1) plotW / (activeSeries.size - 1) else plotW
                            val points = activeSeries.mapIndexed { idx, pt ->
                                val x = if (activeSeries.size == 1) padX + plotW / 2f else padX + idx * stepX
                                val y = padY + plotH * (1f - (pt.revenue / maxRev).toFloat().coerceIn(0f, 1f))
                                Offset(x, y)
                            }
                            val areaPath = Path().apply {
                                moveTo(points.first().x, padY + plotH)
                                points.forEach { p -> lineTo(p.x, p.y) }
                                lineTo(points.last().x, padY + plotH)
                                close()
                            }
                            drawPath(
                                path = areaPath,
                                brush = Brush.verticalGradient(
                                    colors = listOf(NexGenCoral.copy(alpha = 0.35f), NexGenCoral.copy(alpha = 0.02f)),
                                    startY = padY,
                                    endY = padY + plotH
                                )
                            )
                            val linePath = Path().apply {
                                moveTo(points.first().x, points.first().y)
                                points.drop(1).forEach { p -> lineTo(p.x, p.y) }
                            }
                            drawPath(
                                path = linePath,
                                color = NexGenCoral,
                                style = Stroke(width = 4.5f, cap = StrokeCap.Round)
                            )
                            if (points.size <= 14) {
                                points.forEach { p ->
                                    drawCircle(color = NexGenCobalt, radius = 5.5f, center = p)
                                    drawCircle(color = Color.White, radius = 2.5f, center = p)
                                }
                            }
                        }
                        val labelIndices = if (activeSeries.size <= 7) activeSeries.indices.toList()
                        else listOf(0, activeSeries.size / 3, (activeSeries.size * 2) / 3, activeSeries.lastIndex).distinct()
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            labelIndices.forEach { idx ->
                                Text(
                                    activeSeries[idx].label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (peakPoint != null && peakPoint.revenue > 0.0) {
                            Text(
                                "Peak: ${peakPoint.label} (${PrintAndExportUtils.formatMoney(peakPoint.revenue, shop.currencyCode)} • ${peakPoint.ordersCount} sales)",
                                style = MaterialTheme.typography.labelSmall,
                                color = NexGenCobalt,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        // Revenue & Profit Canvas Chart
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Live P&L & Channel Breakdown", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    val bars = listOf(
                        Triple("POS", summary.posRevenue, NexGenCobalt),
                        Triple("Online", summary.onlineRevenue, NexGenCoral),
                        Triple("COGS", summary.cogsTotal, NexGenAmber),
                        Triple("Net Profit", summary.netProfit.coerceAtLeast(0.0), NexGenEmerald),
                        Triple("Expenses", summary.operatingExpenses, MaterialTheme.colorScheme.error)
                    )
                    val maxVal = bars.maxOf { it.second }.coerceAtLeast(1.0)
                    Canvas(modifier = Modifier.fillMaxWidth().height(150.dp)) {
                        val barWidth = (size.width / (bars.size * 2f)).coerceAtLeast(24f)
                        val spacing = (size.width - (barWidth * bars.size)) / (bars.size + 1)
                        bars.forEachIndexed { idx, (_, value, color) ->
                            val ratio = (value / maxVal).toFloat().coerceIn(0.04f, 1f)
                            val h = size.height * ratio
                            val left = spacing + idx * (barWidth + spacing)
                            drawRoundRect(
                                color = color,
                                topLeft = Offset(left, size.height - h),
                                size = Size(barWidth, h),
                                cornerRadius = CornerRadius(8f, 8f)
                            )
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                        bars.forEach { (label, _, color) ->
                            Text(label, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Fast Moving Products (Turnover Velocity & Stockout Risk)
        if (summary.fastMovingProducts.isNotEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Bolt, contentDescription = null, tint = NexGenAmber)
                            Spacer(Modifier.width(6.dp))
                            Text("Fast Moving Products (30-Day Velocity)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        summary.fastMovingProducts.forEach { fm ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(fm.product.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                    val rateStr = String.format(Locale.US, "%.1f/day", fm.velocityPerDay)
                                    val coverStr = fm.daysOfStockLeft?.let { String.format(Locale.US, "%.0f days cover", it) } ?: "In stock"
                                    Text(
                                        "${fm.unitsMoved} moved • $rateStr • Left: ${fm.product.stockQuantity} ($coverStr)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (fm.daysOfStockLeft != null && fm.daysOfStockLeft < 7.0) {
                                    Surface(color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f), shape = RoundedCornerShape(6.dp)) {
                                        Text(
                                            "Low Stock Risk",
                                            color = MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }

        // Period Sales & Balances Breakdown
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Sales & Accounting Ledger", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    LedgerRow("Daily Sales", PrintAndExportUtils.formatMoney(summary.dailySales, shop.currencyCode))
                    LedgerRow("Weekly Sales", PrintAndExportUtils.formatMoney(summary.weeklySales, shop.currencyCode))
                    LedgerRow("Monthly Sales", PrintAndExportUtils.formatMoney(summary.monthlySales, shop.currencyCode))
                    LedgerRow("Annual Sales", PrintAndExportUtils.formatMoney(summary.annualSales, shop.currencyCode))
                    HorizontalDivider()
                    LedgerRow("Cost of Goods Sold (COGS)", PrintAndExportUtils.formatMoney(summary.cogsTotal, shop.currencyCode))
                    LedgerRow("Gross Profit", PrintAndExportUtils.formatMoney(summary.grossProfit, shop.currencyCode))
                    LedgerRow("Net Cash Flow", PrintAndExportUtils.formatMoney(summary.cashFlowNet, shop.currencyCode))
                    LedgerRow("Pending Order Payments", PrintAndExportUtils.formatMoney(summary.outstandingPayments, shop.currencyCode))
                    LedgerRow("Customer Credit Debts", PrintAndExportUtils.formatMoney(summary.customerDebts, shop.currencyCode))
                    LedgerRow("Supplier Payables", PrintAndExportUtils.formatMoney(summary.supplierPayables, shop.currencyCode))
                    LedgerRow("Low Stock / Out of Stock", "${summary.lowStockCount} low / ${summary.outOfStockCount} out")
                }
            }
        }

        if (summary.bestSellingProducts.isNotEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Best Selling Products", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        summary.bestSellingProducts.forEach { p ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(p.name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                Text("${p.soldCount} sold", style = MaterialTheme.typography.labelMedium, color = NexGenCoral)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BusinessOrdersScreen(
    shop: Shop,
    orders: List<Order>,
    refunds: List<RefundRequest>,
    loadLineItems: suspend (String, String) -> List<OrderLineItem>,
    onUpdateOrder: (Order, String, String, String?, String?) -> Unit,
    onProcessRefund: (RefundRequest, String) -> Unit,
    canProcessRefunds: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var filterStatus by remember { mutableStateOf("All") }
    var editingOrder by remember { mutableStateOf<Order?>(null) }

    val filteredOrders = remember(orders, filterStatus) {
        if (filterStatus == "All") orders else orders.filter { it.status == filterStatus }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item { FilterChip(selected = filterStatus == "All", onClick = { filterStatus = "All" }, label = { Text("All (${orders.size})") }) }
                items(ALL_ORDER_STATUSES) { st ->
                    FilterChip(selected = filterStatus == st, onClick = { filterStatus = st }, label = { Text(st) })
                }
            }
        }

        if (canProcessRefunds && refunds.any { it.status == "Requested" }) {
            item {
                Text("Pending Return & Refund Requests", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = NexGenCoral)
            }
            items(refunds.filter { it.status == "Requested" }, key = { it.id }) { ref ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${ref.orderNumber} • ${ref.customerName ?: "Customer"}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text("Amount: ${PrintAndExportUtils.formatMoney(ref.amount, shop.currencyCode)} • Reason: ${ref.reason}", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onProcessRefund(ref, "Refunded") }, colors = ButtonDefaults.buttonColors(containerColor = NexGenEmerald)) { Text("Approve & Refund") }
                            OutlinedButton(onClick = { onProcessRefund(ref, "Rejected") }) { Text("Reject") }
                        }
                    }
                }
            }
        }

        items(filteredOrders, key = { it.id }) { order ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(order.orderNumber, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("${order.customerName} (${order.customerPhone})", style = MaterialTheme.typography.bodySmall)
                        }
                        Surface(color = NexGenCobalt.copy(alpha = 0.14f), shape = RoundedCornerShape(8.dp)) {
                            Text(order.status, color = NexGenCobalt, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                    }
                    Text("${order.firstItemName} (${order.itemCount} items) • ${PrintAndExportUtils.formatMoney(order.totalAmount, shop.currencyCode)}", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Payment: ${order.paymentMethod} • ${order.paymentStatus} ${order.paymentReference?.let { "(Ref: $it)" } ?: ""}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (order.paymentStatus == "Paid") NexGenEmerald else NexGenAmber
                    )
                    Text("Delivery: ${order.deliveryOption} • ${order.deliveryAddress}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (order.paymentStatus != "Paid" && order.status != "Cancelled") {
                            Button(
                                onClick = {
                                    val nextStatus = if (order.status in listOf("Pending", "Awaiting Payment")) "Paid" else order.status
                                    onUpdateOrder(order, nextStatus, "Paid", "Payment verified by Admin", order.assignedRider)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NexGenEmerald),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Verify Paid")
                            }
                        }
                        OutlinedButton(onClick = { editingOrder = order }, modifier = Modifier.weight(1f)) {
                            Text("Update Status")
                        }
                    }

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("TAX INVOICE", "PACKING SLIP", "DELIVERY NOTE").forEach { docType ->
                            item {
                                OutlinedButton(onClick = {
                                    scope.launch {
                                        val items = loadLineItems(order.id, order.shopOwnerId)
                                        PrintAndExportUtils.printOrderDocument(
                                            context = context,
                                            jobName = "${docType}_${order.orderNumber}",
                                            shop = shop,
                                            order = order,
                                            lineItems = items,
                                            docType = docType
                                        )
                                    }
                                }) {
                                    Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(docType, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                        if (order.deliveryAddress.isNotBlank()) {
                            item {
                                OutlinedButton(onClick = { PrintAndExportUtils.openMapsLocation(context, order.deliveryAddress) }) {
                                    Icon(Icons.Filled.LocationOn, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Route Map", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (editingOrder != null) {
        val ord = editingOrder!!
        var status by remember { mutableStateOf(ord.status) }
        var payStatus by remember { mutableStateOf(ord.paymentStatus) }
        var rider by remember { mutableStateOf(ord.assignedRider ?: "") }
        var note by remember { mutableStateOf(ord.trackingNote ?: "") }

        AlertDialog(
            onDismissRequest = { editingOrder = null },
            title = { Text("Manage ${ord.orderNumber}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val statusChoices = remember(canProcessRefunds) {
                        if (canProcessRefunds) ALL_ORDER_STATUSES
                        else ALL_ORDER_STATUSES.filter { it != "Refunded" }
                    }
                    val paymentChoices = remember(canProcessRefunds) {
                        if (canProcessRefunds) listOf("Unpaid", "Pending Verification", "Paid", "Refunded")
                        else listOf("Unpaid", "Pending Verification", "Paid")
                    }
                    Text("Order Status", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(statusChoices) { s -> FilterChip(selected = s == status, onClick = { status = s }, label = { Text(s) }) }
                    }
                    Text("Payment Status", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(paymentChoices) { p ->
                            FilterChip(selected = p == payStatus, onClick = { payStatus = p }, label = { Text(p) })
                        }
                    }
                    OutlinedTextField(value = rider, onValueChange = { rider = it }, label = { Text("Assigned Rider / Courier") }, singleLine = true)
                    OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Tracking Note") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    onUpdateOrder(ord, status, payStatus, note.ifBlank { null }, rider.ifBlank { null })
                    editingOrder = null
                }, colors = ButtonDefaults.buttonColors(containerColor = NexGenCoral)) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editingOrder = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun KpiCard(title: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, color = accent, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun LedgerRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}
