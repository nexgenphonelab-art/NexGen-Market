package com.example.ui.business

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.CrmCustomer
import com.example.data.model.PosSale
import com.example.data.model.PosSaleItem
import com.example.data.model.Product
import com.example.data.model.Shift
import com.example.data.model.Shop
import com.example.data.repository.PosCartEntry
import com.example.ui.components.BarcodeStrip
import com.example.ui.components.ProductImageBox
import com.example.ui.theme.NexGenAmber
import com.example.ui.theme.NexGenCobalt
import com.example.ui.theme.NexGenCoral
import com.example.ui.theme.NexGenEmerald
import com.example.util.PrintAndExportUtils
import com.example.util.ReceiptLine
import kotlinx.coroutines.launch

@Composable
fun PosTerminalScreen(
    shop: Shop,
    products: List<Product>,
    posCart: List<PosCartEntry>,
    posSales: List<PosSale>,
    shifts: List<Shift>,
    crmCustomers: List<CrmCustomer>,
    onAddToPosCart: (Product, String) -> Unit,
    onUpdatePosCartQty: (String, String, Long) -> Unit,
    onClearPosCart: () -> Unit,
    onScanBarcode: (String) -> Boolean,
    onCompletePosSale: (
        shop: Shop,
        shift: Shift?,
        customer: CrmCustomer?,
        discountAmount: Double,
        paymentMode: String,
        cashPaid: Double,
        mpesaPaid: Double,
        cardPaid: Double,
        creditBalance: Double,
        mpesaCode: String?,
        onSaleComplete: (PosSale, List<PosCartEntry>) -> Unit
    ) -> Unit,
    onRefundPosSale: (PosSale) -> Unit,
    loadPosSaleItems: suspend (String, String) -> List<PosSaleItem>,
    onOpenShift: (Shop, Double) -> Unit,
    onCloseShift: (Shift, Double, String?) -> Unit,
    canRefundSales: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val openShift = remember(shifts) { shifts.firstOrNull { it.status == "Open" } }

    var searchOrBarcode by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var showSalesHistory by remember { mutableStateOf(false) }
    var showOpenShiftDialog by remember { mutableStateOf(false) }
    var showCloseShiftDialog by remember { mutableStateOf(false) }
    var showCheckoutDialog by remember { mutableStateOf(false) }
    var showScannerDialog by remember { mutableStateOf(false) }
    var selectedVariationProduct by remember { mutableStateOf<Product?>(null) }
    var lastReceiptPreview by remember { mutableStateOf<Pair<PosSale, List<ReceiptLine>>?>(null) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) showScannerDialog = true
    }

    val categories = remember(products) { listOf("All") + products.map { it.category }.distinct().sorted() }
    val filteredProducts = remember(products, searchOrBarcode, selectedCategory) {
        products.filter { p ->
            (selectedCategory == "All" || p.category == selectedCategory) &&
                (searchOrBarcode.isBlank() ||
                    p.name.contains(searchOrBarcode, true) ||
                    p.sku.contains(searchOrBarcode, true) ||
                    p.barcode.contains(searchOrBarcode, true))
        }
    }

    val subtotal = remember(posCart) { posCart.sumOf { it.product.sellingPrice * it.quantity } }
    val taxAmount = remember(subtotal, shop.taxRatePercent) { subtotal * (shop.taxRatePercent / 100.0) }
    val grandTotal = subtotal + taxAmount

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Shift & Mode Top Bar
        Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (openShift != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.LockOpen, contentDescription = null, tint = NexGenEmerald, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Column {
                            Text("Shift Open (${openShift.staffName})", style = MaterialTheme.typography.labelLarge, color = NexGenEmerald)
                            Text("Expected Drawer: ${PrintAndExportUtils.formatMoney(openShift.expectedCash, shop.currencyCode)}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    OutlinedButton(onClick = { showCloseShiftDialog = true }) { Text("Close Shift") }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Text("No Active Cash Shift", style = MaterialTheme.typography.labelLarge, color = NexGenAmber)
                    }
                    Button(
                        onClick = { showOpenShiftDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = NexGenCobalt)
                    ) { Text("Open Shift") }
                }
                Spacer(Modifier.width(6.dp))
                IconButton(onClick = { showSalesHistory = !showSalesHistory }) {
                    Icon(Icons.Filled.History, contentDescription = "POS Sales History", tint = if (showSalesHistory) NexGenCoral else MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        if (showSalesHistory) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("POS Sales History (${posSales.size})", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { showSalesHistory = false }) { Text("Back to Register") }
                    }
                }
                items(posSales, key = { it.id }) { sale ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(sale.receiptNumber, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    sale.status,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (sale.status == "Refunded") MaterialTheme.colorScheme.error else NexGenEmerald
                                )
                            }
                            Text("Customer: ${sale.customerName} • Mode: ${sale.paymentMode} • Cashier: ${sale.cashierName}", style = MaterialTheme.typography.bodySmall)
                            Text("Total: ${PrintAndExportUtils.formatMoney(sale.totalAmount, shop.currencyCode)}", style = MaterialTheme.typography.titleSmall, color = NexGenCoral)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = {
                                    scope.launch {
                                        val items = loadPosSaleItems(sale.id, sale.shopOwnerId)
                                        val lines = items.map { ReceiptLine(it.productName, it.variationLabel, it.quantity, it.unitPrice, it.lineTotal) }
                                        lastReceiptPreview = sale to lines
                                    }
                                }) {
                                    Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Receipt")
                                }
                                if (sale.status != "Refunded" && canRefundSales) {
                                    OutlinedButton(onClick = { onRefundPosSale(sale) }) { Text("Refund") }
                                } else if (sale.status != "Refunded") {
                                    Surface(color = NexGenEmerald.copy(alpha = 0.12f), shape = RoundedCornerShape(8.dp)) {
                                        Text(
                                            "Finalized • Locked",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = NexGenEmerald,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Barcode / SKU Search + Camera Scan
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchOrBarcode,
                    onValueChange = { searchOrBarcode = it },
                    placeholder = { Text("Scan barcode, SKU, or name...") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("pos_barcode_input")
                )
                Button(
                    onClick = {
                        if (searchOrBarcode.isNotBlank() && onScanBarcode(searchOrBarcode)) {
                            searchOrBarcode = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NexGenCobalt)
                ) { Text("Add") }
                IconButton(
                    onClick = {
                        val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                        if (hasPerm) showScannerDialog = true else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    },
                    modifier = Modifier.testTag("pos_camera_scan_button")
                ) {
                    Icon(Icons.Filled.QrCodeScanner, contentDescription = "Camera Barcode Scanner", tint = NexGenCoral)
                }
            }

            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categories) { cat ->
                    FilterChip(selected = cat == selectedCategory, onClick = { selectedCategory = cat }, label = { Text(cat) })
                }
            }

            // Product Grid
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredProducts.chunked(2)) { pair ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { prod ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier.weight(1f).clickable {
                                    if (prod.colors.size > 1 || prod.sizes.size > 1) selectedVariationProduct = prod
                                    else onAddToPosCart(prod, "Standard")
                                }
                            ) {
                                Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    ProductImageBox(prod.imageKeys.firstOrNull() ?: "smartphone", prod.name, Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)))
                                    Spacer(Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(prod.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(PrintAndExportUtils.formatMoney(prod.sellingPrice, shop.currencyCode), style = MaterialTheme.typography.labelMedium, color = NexGenCoral, fontWeight = FontWeight.Bold)
                                        Text("Qty: ${prod.stockQuantity} • ${prod.sku}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }

            // Active POS Cart Footer Drawer
            Surface(tonalElevation = 8.dp, shadowElevation = 10.dp, color = MaterialTheme.colorScheme.surface) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (posCart.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(posCart) { entry ->
                                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(10.dp)) {
                                    Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(entry.product.name.take(14), style = MaterialTheme.typography.labelMedium)
                                        IconButton(onClick = { onUpdatePosCartQty(entry.product.id, entry.variationLabel, entry.quantity - 1L) }, modifier = Modifier.size(26.dp)) {
                                            Icon(Icons.Filled.Remove, contentDescription = "Decrease", modifier = Modifier.size(14.dp))
                                        }
                                        Text("${entry.quantity}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                        IconButton(onClick = { onUpdatePosCartQty(entry.product.id, entry.variationLabel, entry.quantity + 1L) }, modifier = Modifier.size(26.dp)) {
                                            Icon(Icons.Filled.Add, contentDescription = "Increase", modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("${posCart.sumOf { it.quantity }} item(s) • Tax ${shop.taxRatePercent}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Total: ${PrintAndExportUtils.formatMoney(grandTotal, shop.currencyCode)}", style = MaterialTheme.typography.titleLarge, color = NexGenCoral, fontWeight = FontWeight.Bold)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (posCart.isNotEmpty()) {
                                IconButton(onClick = onClearPosCart) {
                                    Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear Cart")
                                }
                            }
                            Button(
                                onClick = { showCheckoutDialog = true },
                                enabled = posCart.isNotEmpty(),
                                colors = ButtonDefaults.buttonColors(containerColor = NexGenCoral),
                                modifier = Modifier.height(48.dp).testTag("pos_charge_button")
                            ) {
                                Icon(Icons.Filled.PointOfSale, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Charge", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    if (selectedVariationProduct != null) {
        val prod = selectedVariationProduct!!
        var color by remember { mutableStateOf(prod.colors.firstOrNull() ?: "Default") }
        var size by remember { mutableStateOf(prod.sizes.firstOrNull() ?: "Standard") }
        AlertDialog(
            onDismissRequest = { selectedVariationProduct = null },
            title = { Text("Select Variation") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (prod.colors.isNotEmpty()) {
                        Text("Color", style = MaterialTheme.typography.labelLarge)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(prod.colors) { c -> FilterChip(selected = c == color, onClick = { color = c }, label = { Text(c) }) }
                        }
                    }
                    if (prod.sizes.isNotEmpty()) {
                        Text("Size / Edition", style = MaterialTheme.typography.labelLarge)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(prod.sizes) { s -> FilterChip(selected = s == size, onClick = { size = s }, label = { Text(s) }) }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    onAddToPosCart(prod, "$color • $size")
                    selectedVariationProduct = null
                }) { Text("Add to POS") }
            },
            dismissButton = { TextButton(onClick = { selectedVariationProduct = null }) { Text("Cancel") } }
        )
    }

    if (showScannerDialog) {
        val lifecycleOwner = LocalLifecycleOwner.current
        var manualCode by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showScannerDialog = false },
            title = { Text("CameraX Barcode & SKU Scanner") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(12.dp))) {
                        AndroidView(
                            factory = { ctx ->
                                val previewView = PreviewView(ctx).apply {
                                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                                }
                                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                cameraProviderFuture.addListener({
                                    runCatching {
                                        val provider = cameraProviderFuture.get()
                                        val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                                        provider.unbindAll()
                                        provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview)
                                    }
                                }, ContextCompat.getMainExecutor(ctx))
                                previewView
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    OutlinedTextField(
                        value = manualCode,
                        onValueChange = { manualCode = it },
                        label = { Text("Detected / Enter Barcode or SKU") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (manualCode.isNotBlank()) {
                        onScanBarcode(manualCode)
                        showScannerDialog = false
                    }
                }) { Text("Scan Item") }
            },
            dismissButton = { TextButton(onClick = { showScannerDialog = false }) { Text("Close") } }
        )
    }

    if (showOpenShiftDialog) {
        var opening by remember { mutableStateOf("0") }
        AlertDialog(
            onDismissRequest = { showOpenShiftDialog = false },
            title = { Text("Open Cash Register Shift") },
            text = { OutlinedTextField(value = opening, onValueChange = { opening = it }, label = { Text("Opening Float (${shop.currencyCode})") }, singleLine = true) },
            confirmButton = {
                Button(onClick = {
                    onOpenShift(shop, opening.toDoubleOrNull() ?: 0.0)
                    showOpenShiftDialog = false
                }) { Text("Start Shift") }
            },
            dismissButton = { TextButton(onClick = { showOpenShiftDialog = false }) { Text("Cancel") } }
        )
    }

    if (showCloseShiftDialog && openShift != null) {
        var counted by remember { mutableStateOf(openShift.expectedCash.toString()) }
        var notes by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCloseShiftDialog = false },
            title = { Text("Close & Reconcile Shift") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Expected Cash: ${PrintAndExportUtils.formatMoney(openShift.expectedCash, shop.currencyCode)}")
                    OutlinedTextField(value = counted, onValueChange = { counted = it }, label = { Text("Counted Cash Drawer") }, singleLine = true)
                    OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Reconciliation Notes") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    onCloseShift(openShift, counted.toDoubleOrNull() ?: 0.0, notes)
                    showCloseShiftDialog = false
                }) { Text("Close Shift") }
            },
            dismissButton = { TextButton(onClick = { showCloseShiftDialog = false }) { Text("Cancel") } }
        )
    }

    if (showCheckoutDialog) {
        PosPaymentModal(
            shop = shop,
            subtotal = subtotal,
            crmCustomers = crmCustomers,
            onDismiss = { showCheckoutDialog = false },
            onComplete = { customer, discount, mode, cash, mpesa, card, credit, mpesaCode ->
                onCompletePosSale(shop, openShift, customer, discount, mode, cash, mpesa, card, credit, mpesaCode) { sale, entries ->
                    val lines = entries.map {
                        ReceiptLine(it.product.name, it.variationLabel, it.quantity, it.product.sellingPrice, it.product.sellingPrice * it.quantity)
                    }
                    showCheckoutDialog = false
                    lastReceiptPreview = sale to lines
                }
            }
        )
    }

    if (lastReceiptPreview != null) {
        val (sale, lines) = lastReceiptPreview!!
        val dateStr = remember(sale.receiptNumber) { PrintAndExportUtils.formatDate() }
        val escPos = PrintAndExportUtils.buildEscPosReceiptText(
            shop, sale.receiptNumber, sale.cashierName, sale.customerName, lines, sale.totalAmount, sale.paymentMode
        )
        val accentColor = remember(shop.receiptAccentColorHex) {
            runCatching { Color(android.graphics.Color.parseColor(shop.receiptAccentColorHex)) }.getOrElse { NexGenCobalt }
        }
        AlertDialog(
            onDismissRequest = { lastReceiptPreview = null },
            title = { Text("Receipt ${sale.receiptNumber}") },
            text = {
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                ) {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()).padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxWidth().height(5.dp).background(accentColor, RoundedCornerShape(3.dp)))
                        if (shop.receiptShowLogo) {
                            Spacer(Modifier.height(2.dp))
                            Surface(
                                color = Color(0xFF0F172A),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.size(52.dp).border(1.5.dp, accentColor, RoundedCornerShape(10.dp))
                            ) {
                                AsyncImage(
                                    model = R.drawable.img_nexgen_logo,
                                    contentDescription = "NexGen Market Receipt Logo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp))
                                )
                            }
                            Text(shop.shopName.uppercase(), color = accentColor, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        }
                        Text(shop.receiptHeaderTitle, color = Color(0xFF0F172A), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        if (shop.receiptSubtitle.isNotBlank()) Text(shop.receiptSubtitle, color = Color(0xFF334155), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                        if (shop.receiptAddressLine.isNotBlank()) Text(shop.receiptAddressLine, color = Color(0xFF475569), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)

                        Surface(
                            color = Color(0xFFF8FAFC),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                        ) {
                            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("Receipt: ${sale.receiptNumber}", color = Color(0xFF0F172A), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                                Text("Date: $dateStr", color = Color(0xFF475569), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall)
                                if (shop.receiptShowCashier) {
                                    Text("Cashier: ${sale.cashierName} • ${sale.customerName}", color = Color(0xFF334155), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }

                        HorizontalDivider(color = accentColor, thickness = 1.5.dp)
                        lines.forEach {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${it.name} x${it.qty}", color = Color(0xFF0F172A), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                Text(PrintAndExportUtils.formatMoney(it.lineTotal, shop.currencyCode), color = Color(0xFF0F172A), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        HorizontalDivider()
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Subtotal", color = Color(0xFF475569), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall)
                            Text(PrintAndExportUtils.formatMoney(sale.subtotal, shop.currencyCode), color = Color(0xFF475569), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall)
                        }
                        if (sale.discountAmount > 0.0) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Discount", color = NexGenEmerald, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall)
                                Text("-${PrintAndExportUtils.formatMoney(sale.discountAmount, shop.currencyCode)}", color = NexGenEmerald, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        if (shop.receiptShowTaxBreakdown) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Tax", color = Color(0xFF475569), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall)
                                Text(PrintAndExportUtils.formatMoney(sale.taxAmount, shop.currencyCode), color = Color(0xFF475569), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Surface(color = accentColor.copy(alpha = 0.12f), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("TOTAL", color = Color(0xFF0F172A), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                Text(PrintAndExportUtils.formatMoney(sale.totalAmount, shop.currencyCode), color = accentColor, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            }
                        }
                        Text("Paid via ${sale.paymentMode} ${sale.mpesaCode ?: ""}".trim(), color = Color(0xFF334155), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall)
                        if (shop.receiptFooterMessage.isNotBlank()) {
                            Text(shop.receiptFooterMessage, color = Color(0xFF475569), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                        }
                        if (shop.receiptShowBarcode) {
                            BarcodeStrip(code = sale.receiptNumber, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    PrintAndExportUtils.printReceipt(
                        context = context,
                        jobName = "Receipt_${sale.receiptNumber}",
                        shop = shop,
                        receiptNumber = sale.receiptNumber,
                        dateStr = dateStr,
                        cashierName = sale.cashierName,
                        customerName = sale.customerName,
                        lines = lines,
                        subtotal = sale.subtotal,
                        discount = sale.discountAmount,
                        tax = sale.taxAmount,
                        total = sale.totalAmount,
                        paymentInfo = "${sale.paymentMode} ${sale.mpesaCode ?: ""}".trim()
                    )
                }, colors = ButtonDefaults.buttonColors(containerColor = NexGenCobalt)) {
                    Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Print")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { PrintAndExportUtils.shareText(context, "POS Receipt ${sale.receiptNumber}", escPos) }) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("ESC/POS")
                    }
                    TextButton(onClick = { lastReceiptPreview = null }) { Text("Done") }
                }
            }
        )
    }
}

@Composable
private fun PosPaymentModal(
    shop: Shop,
    subtotal: Double,
    crmCustomers: List<CrmCustomer>,
    onDismiss: () -> Unit,
    onComplete: (CrmCustomer?, Double, String, Double, Double, Double, Double, String?) -> Unit
) {
    var selectedCustomer by remember { mutableStateOf<CrmCustomer?>(null) }
    var discountStr by remember { mutableStateOf("0") }
    var paymentMode by remember { mutableStateOf("Cash") }
    var mpesaCode by remember { mutableStateOf("") }
    var cashStr by remember { mutableStateOf("") }
    var mpesaStr by remember { mutableStateOf("") }
    var cardStr by remember { mutableStateOf("") }

    val discount = (discountStr.toDoubleOrNull() ?: 0.0).coerceIn(0.0, subtotal)
    val total = (subtotal - discount) * (1.0 + shop.taxRatePercent / 100.0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("POS Payment") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Payable Total: ${PrintAndExportUtils.formatMoney(total, shop.currencyCode)}", style = MaterialTheme.typography.titleLarge, color = NexGenCoral, fontWeight = FontWeight.Bold)
                OutlinedTextField(value = discountStr, onValueChange = { discountStr = it }, label = { Text("Discount (${shop.currencyCode})") }, singleLine = true)

                Text("Customer", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        FilterChip(selected = selectedCustomer == null, onClick = { selectedCustomer = null }, label = { Text("Walk-in") })
                    }
                    items(crmCustomers) { cust ->
                        FilterChip(selected = selectedCustomer?.id == cust.id, onClick = { selectedCustomer = cust }, label = { Text(cust.name) })
                    }
                }

                Text("Payment Mode", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(listOf("Cash", "M-Pesa", "Card", "Split", "Credit")) { mode ->
                        FilterChip(selected = paymentMode == mode, onClick = { paymentMode = mode }, label = { Text(mode) })
                    }
                }

                if (paymentMode == "M-Pesa" || paymentMode == "Split") {
                    OutlinedTextField(value = mpesaCode, onValueChange = { mpesaCode = it.uppercase() }, label = { Text("M-Pesa Code (optional)") }, singleLine = true)
                }
                if (paymentMode == "Split") {
                    OutlinedTextField(value = cashStr, onValueChange = { cashStr = it }, label = { Text("Cash Amount") }, singleLine = true)
                    OutlinedTextField(value = mpesaStr, onValueChange = { mpesaStr = it }, label = { Text("M-Pesa Amount") }, singleLine = true)
                    OutlinedTextField(value = cardStr, onValueChange = { cardStr = it }, label = { Text("Card Amount") }, singleLine = true)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cash = if (paymentMode == "Cash") total else if (paymentMode == "Split") (cashStr.toDoubleOrNull() ?: 0.0) else 0.0
                    val mpesa = if (paymentMode == "M-Pesa") total else if (paymentMode == "Split") (mpesaStr.toDoubleOrNull() ?: 0.0) else 0.0
                    val card = if (paymentMode == "Card") total else if (paymentMode == "Split") (cardStr.toDoubleOrNull() ?: 0.0) else 0.0
                    val credit = if (paymentMode == "Credit") total else (total - (cash + mpesa + card)).coerceAtLeast(0.0)
                    onComplete(selectedCustomer, discount, paymentMode, cash, mpesa, card, credit, mpesaCode.ifBlank { null })
                },
                colors = ButtonDefaults.buttonColors(containerColor = NexGenEmerald),
                modifier = Modifier.testTag("confirm_pos_sale_button")
            ) { Text("Complete & Print") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
