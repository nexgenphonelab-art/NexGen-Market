package com.example.ui.business

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.Category
import com.example.data.model.Product
import com.example.data.model.ProductImeiRegistry
import com.example.data.model.PurchaseOrder
import com.example.data.model.Shop
import com.example.data.model.ShopAsset
import com.example.data.model.StockMovement
import com.example.data.model.Supplier
import com.example.ui.components.BarcodeStrip
import com.example.ui.components.ProductImageBox
import com.example.ui.theme.NexGenAmber
import com.example.ui.theme.NexGenCobalt
import com.example.ui.theme.NexGenCoral
import com.example.ui.theme.NexGenEmerald
import com.example.util.PrintAndExportUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryAndProductsScreen(
    shop: Shop,
    products: List<Product>,
    categories: List<Category>,
    stockMovements: List<StockMovement>,
    suppliers: List<Supplier>,
    purchases: List<PurchaseOrder>,
    shopAssets: List<ShopAsset>,
    productImeis: Map<String, ProductImeiRegistry>,
    onSaveProductWithImeis: (Product, List<String>) -> Unit,
    onDeleteProduct: (Product) -> Unit,
    onBulkImportProducts: (List<Product>) -> Unit,
    onLoadStarterCatalog: (Shop) -> Unit,
    onSaveCategory: (Category) -> Unit,
    onAdjustStock: (Product, Long, String, String, String, String) -> Unit,
    onSaveSupplier: (Supplier) -> Unit,
    onCreatePurchaseOrder: (Shop, Supplier, Product, Long, Double, Double, Boolean) -> Unit,
    onSaveAsset: (ShopAsset) -> Unit,
    onDeleteAsset: (ShopAsset) -> Unit
) {
    val context = LocalContext.current
    var subTab by remember { mutableIntStateOf(0) }
    var search by remember { mutableStateOf("") }
    var editingProduct by remember { mutableStateOf<Product?>(null) }
    var deletingProduct by remember { mutableStateOf<Product?>(null) }
    var stockAdjustProduct by remember { mutableStateOf<Product?>(null) }
    var editingAsset by remember { mutableStateOf<ShopAsset?>(null) }
    var showCategoryDialog by remember { mutableStateOf(false) }
    var showSupplierDialog by remember { mutableStateOf(false) }
    var showPoDialog by remember { mutableStateOf(false) }

    val csvExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            PrintAndExportUtils.exportCsvToUri(
                context = context,
                uri = uri,
                headers = listOf("Name", "Category", "SKU", "Barcode", "SellingPrice", "CostPrice", "Stock", "Description"),
                rows = products.map {
                    listOf(it.name, it.category, it.sku, it.barcode, it.sellingPrice.toString(), it.costPrice.toString(), it.stockQuantity.toString(), it.description)
                }
            )
        }
    }

    val csvImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            PrintAndExportUtils.parseProductsFromCsv(context, uri, shop).onSuccess { imported ->
                if (imported.isNotEmpty()) onBulkImportProducts(imported)
            }
        }
    }

    val lowOrOutProducts = remember(products) { products.filter { it.stockQuantity <= it.minStockLevel } }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        PrimaryScrollableTabRow(selectedTabIndex = subTab, edgePadding = 8.dp) {
            Tab(selected = subTab == 0, onClick = { subTab = 0 }, text = { Text("Products") })
            Tab(selected = subTab == 1, onClick = { subTab = 1 }, text = { Text("Categories") })
            Tab(selected = subTab == 2, onClick = { subTab = 2 }, text = { Text("Stock") })
            Tab(selected = subTab == 3, onClick = { subTab = 3 }, text = { Text("Suppliers") })
            Tab(selected = subTab == 4, onClick = { subTab = 4 }, text = { Text("Assets") })
        }

        when (subTab) {
            0 -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (lowOrOutProducts.isNotEmpty()) {
                        item {
                            Surface(color = NexGenAmber.copy(alpha = 0.16f), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Warning, contentDescription = null, tint = NexGenAmber)
                                    Spacer(Modifier.width(8.dp))
                                    Text("${lowOrOutProducts.size} product(s) at or below minimum stock level!", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }

                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = search,
                                onValueChange = { search = it },
                                placeholder = { Text("Search SKU, barcode, product...") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = { editingProduct = Product(shopId = shop.id, shopOwnerId = shop.ownerId, shopName = shop.shopName) },
                                colors = ButtonDefaults.buttonColors(containerColor = NexGenCoral),
                                modifier = Modifier.testTag("add_product_button")
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Add")
                            }
                        }
                    }

                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                OutlinedButton(onClick = { onLoadStarterCatalog(shop) }) {
                                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Load Starter Catalog")
                                }
                            }
                            item {
                                OutlinedButton(onClick = { csvImportLauncher.launch(arrayOf("text/*", "text/csv")) }) {
                                    Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Import CSV")
                                }
                            }
                            item {
                                OutlinedButton(onClick = { csvExportLauncher.launch("${shop.shopName.replace(" ", "_")}_products.csv") }) {
                                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Export CSV")
                                }
                            }
                        }
                    }

                    val filtered = products.filter {
                        search.isBlank() || it.name.contains(search, true) || it.sku.contains(search, true) || it.barcode.contains(search, true)
                    }
                    items(filtered, key = { it.id }) { prod ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    ProductImageBox(prod.imageKeys.firstOrNull() ?: "smartphone", prod.name, Modifier.size(60.dp).clip(RoundedCornerShape(10.dp)))
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(prod.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        Text("${prod.category} • SKU: ${prod.sku}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(
                                            "Sell: ${PrintAndExportUtils.formatMoney(prod.sellingPrice, shop.currencyCode)} | Cost: ${PrintAndExportUtils.formatMoney(prod.costPrice, shop.currencyCode)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = NexGenCoral
                                        )
                                        Text(
                                            "Stock: ${prod.stockQuantity} (${prod.stockLocation}) • ${if (prod.isVisible) "Visible" else "Hidden"}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (prod.stockQuantity <= prod.minStockLevel) NexGenAmber else NexGenEmerald
                                        )
                                        val prodImeiCount = productImeis[prod.id]?.imeis?.size ?: 0
                                        if (prodImeiCount > 0) {
                                            Text(
                                                "Private Admin IMEIs: $prodImeiCount tracked (hidden from customers)",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = NexGenCobalt,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                    Column {
                                        IconButton(onClick = { editingProduct = prod }) { Icon(Icons.Filled.Edit, contentDescription = "Edit Product") }
                                        IconButton(onClick = { deletingProduct = prod }) { Icon(Icons.Filled.Delete, contentDescription = "Delete Product", tint = MaterialTheme.colorScheme.error) }
                                    }
                                }
                                if (prod.barcode.isNotBlank()) {
                                    BarcodeStrip(code = prod.barcode, modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                    }
                }
            }

            1 -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Button(onClick = { showCategoryDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = NexGenCobalt), modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Create Category")
                        }
                    }
                    items(categories, key = { it.id }) { cat ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Row(modifier = Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column {
                                    Text(cat.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text("Parent: ${cat.parentCategory.ifBlank { "Root" }} • Order: ${cat.sortOrder}", style = MaterialTheme.typography.bodySmall)
                                }
                                Switch(checked = cat.isActive, onCheckedChange = { onSaveCategory(cat.copy(isActive = it)) })
                            }
                        }
                    }
                }
            }

            2 -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Text("Quick Stock Adjustment / Transfer / Damaged", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    items(products, key = { it.id }) { prod ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(prod.name, style = MaterialTheme.typography.titleSmall)
                                    Text("Current: ${prod.stockQuantity} • Location: ${prod.stockLocation}", style = MaterialTheme.typography.bodySmall)
                                }
                                OutlinedButton(onClick = { stockAdjustProduct = prod }) {
                                    Icon(Icons.Filled.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Move / Adjust")
                                }
                            }
                        }
                    }
                    item {
                        Spacer(Modifier.height(6.dp))
                        Text("Recent Stock Movements (${stockMovements.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    items(stockMovements.take(30), key = { it.id }) { mov ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("${mov.type}: ${mov.productName}", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        if (mov.quantityDelta >= 0) "+${mov.quantityDelta}" else "${mov.quantityDelta}",
                                        color = if (mov.quantityDelta >= 0) NexGenEmerald else NexGenCoral,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text("${mov.fromLocation ?: ""} -> ${mov.toLocation ?: ""} • ${mov.reason ?: ""}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            3 -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { showSupplierDialog = true }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = NexGenCobalt)) {
                                Text("+ Supplier")
                            }
                            Button(
                                onClick = { showPoDialog = true },
                                enabled = suppliers.isNotEmpty() && products.isNotEmpty(),
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = NexGenCoral)
                            ) {
                                Text("+ PO / GRN")
                            }
                        }
                    }
                    item { Text("Suppliers (${suppliers.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                    items(suppliers, key = { it.id }) { sup ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                                Text(sup.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text("Contact: ${sup.contactPerson ?: "-"} • ${sup.phone ?: "-"}", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    "Total Purchased: ${PrintAndExportUtils.formatMoney(sup.totalPurchased, shop.currencyCode)} | Payable Balance: ${PrintAndExportUtils.formatMoney(sup.balanceOwed, shop.currencyCode)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (sup.balanceOwed > 0) NexGenCoral else NexGenEmerald
                                )
                            }
                        }
                    }
                    item { Text("Purchase Orders & GRNs (${purchases.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                    items(purchases, key = { it.id }) { po ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("${po.poNumber} ${po.grnNumber?.let { "/ $it" } ?: ""}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text(po.status, color = NexGenEmerald, style = MaterialTheme.typography.labelMedium)
                                }
                                Text("${po.quantity}x ${po.productName} from ${po.supplierName}", style = MaterialTheme.typography.bodySmall)
                                Text("Cost: ${PrintAndExportUtils.formatMoney(po.totalCost, shop.currencyCode)} • Paid: ${PrintAndExportUtils.formatMoney(po.amountPaid, shop.currencyCode)}", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            else -> {
                val totalPurchaseValue = remember(shopAssets) { shopAssets.sumOf { it.purchasePrice * it.quantity } }
                val totalCurrentValue = remember(shopAssets) { shopAssets.sumOf { it.currentValue * it.quantity } }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Shop Fixed Assets Valuation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column {
                                        Text("Original Cost", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(PrintAndExportUtils.formatMoney(totalPurchaseValue, shop.currencyCode), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("Current Worth (${shopAssets.sumOf { it.quantity }} units)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(PrintAndExportUtils.formatMoney(totalCurrentValue, shop.currencyCode), style = MaterialTheme.typography.titleMedium, color = NexGenEmerald, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Button(
                            onClick = { editingAsset = ShopAsset(shopId = shop.id, shopOwnerId = shop.ownerId) },
                            colors = ButtonDefaults.buttonColors(containerColor = NexGenCoral),
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("add_shop_asset_button")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Add Shop Asset (Equipment / Fixtures / Hardware)", fontWeight = FontWeight.Bold)
                        }
                    }
                    items(shopAssets, key = { it.id }) { asset ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text("${asset.name} (${asset.category})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text(
                                        "Qty: ${asset.quantity} • Condition: ${asset.condition} ${asset.serialOrTag?.takeIf { it.isNotBlank() }?.let { "• Tag: $it" } ?: ""}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        "Unit Value: ${PrintAndExportUtils.formatMoney(asset.currentValue, shop.currencyCode)} • Line Worth: ${PrintAndExportUtils.formatMoney(asset.currentValue * asset.quantity, shop.currencyCode)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = NexGenEmerald
                                    )
                                    if (!asset.notes.isNullOrBlank()) {
                                        Text(asset.notes, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Column {
                                    IconButton(onClick = { editingAsset = asset }) {
                                        Icon(Icons.Filled.Edit, contentDescription = "Edit Shop Asset")
                                    }
                                    IconButton(onClick = { onDeleteAsset(asset) }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Delete Shop Asset", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (editingProduct != null) {
        val initProd = editingProduct!!
        val initImeis = productImeis[initProd.id]?.imeis ?: emptyList()
        ProductEditorDialog(
            initial = initProd,
            initialImeis = initImeis,
            onDismiss = { editingProduct = null },
            onSave = { prod, imeis ->
                onSaveProductWithImeis(prod, imeis)
                editingProduct = null
            }
        )
    }

    if (deletingProduct != null) {
        val delProd = deletingProduct!!
        AlertDialog(
            onDismissRequest = { deletingProduct = null },
            title = { Text("Delete Product?") },
            text = { Text("Permanently delete '${delProd.name}' and its private IMEI records? This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteProduct(delProd)
                        deletingProduct = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deletingProduct = null }) { Text("Cancel") } }
        )
    }

    if (stockAdjustProduct != null) {
        val prod = stockAdjustProduct!!
        var deltaStr by remember { mutableStateOf("5") }
        var moveType by remember { mutableStateOf("Restock") }
        var toLoc by remember { mutableStateOf(prod.stockLocation) }
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { stockAdjustProduct = null },
            title = { Text("Stock Movement: ${prod.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(listOf("Restock", "Transfer", "Adjustment", "Damaged")) { t ->
                            FilterChip(selected = moveType == t, onClick = { moveType = t }, label = { Text(t) })
                        }
                    }
                    OutlinedTextField(value = deltaStr, onValueChange = { deltaStr = it }, label = { Text("Quantity Delta (use negative for reduction)") }, singleLine = true)
                    OutlinedTextField(value = toLoc, onValueChange = { toLoc = it }, label = { Text("Target Location") }, singleLine = true)
                    OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("Reason / Reference") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    val delta = deltaStr.toLongOrNull() ?: 0L
                    onAdjustStock(prod, delta, moveType, prod.stockLocation, toLoc, reason.ifBlank { moveType })
                    stockAdjustProduct = null
                }) { Text("Save Movement") }
            },
            dismissButton = { TextButton(onClick = { stockAdjustProduct = null }) { Text("Cancel") } }
        )
    }

    if (showCategoryDialog) {
        var name by remember { mutableStateOf("") }
        var parent by remember { mutableStateOf("Electronics") }
        AlertDialog(
            onDismissRequest = { showCategoryDialog = false },
            title = { Text("New Category") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Category Name") })
                    OutlinedTextField(value = parent, onValueChange = { parent = it }, label = { Text("Parent Group") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (name.isNotBlank()) {
                        onSaveCategory(Category(shopOwnerId = shop.ownerId, name = name, parentCategory = parent, isActive = true))
                        showCategoryDialog = false
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showCategoryDialog = false }) { Text("Cancel") } }
        )
    }

    if (showSupplierDialog) {
        var name by remember { mutableStateOf("") }
        var contact by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSupplierDialog = false },
            title = { Text("Add Supplier") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Supplier Name") })
                    OutlinedTextField(value = contact, onValueChange = { contact = it }, label = { Text("Contact Person") })
                    OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (name.isNotBlank()) {
                        onSaveSupplier(Supplier(shopId = shop.id, shopOwnerId = shop.ownerId, name = name, contactPerson = contact, phone = phone))
                        showSupplierDialog = false
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showSupplierDialog = false }) { Text("Cancel") } }
        )
    }

    if (showPoDialog && suppliers.isNotEmpty() && products.isNotEmpty()) {
        var selSupplier by remember { mutableStateOf(suppliers.first()) }
        var selProduct by remember { mutableStateOf(products.first()) }
        var qtyStr by remember { mutableStateOf("10") }
        var unitCostStr by remember { mutableStateOf(selProduct.costPrice.toString()) }
        var paidStr by remember { mutableStateOf("0") }
        var receiveNow by remember { mutableStateOf(true) }

        AlertDialog(
            onDismissRequest = { showPoDialog = false },
            title = { Text("Create Purchase Order & GRN") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Supplier", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(suppliers) { s -> FilterChip(selected = s.id == selSupplier.id, onClick = { selSupplier = s }, label = { Text(s.name) }) }
                    }
                    Text("Product", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(products) { p -> FilterChip(selected = p.id == selProduct.id, onClick = { selProduct = p; unitCostStr = p.costPrice.toString() }, label = { Text(p.name.take(18)) }) }
                    }
                    OutlinedTextField(value = qtyStr, onValueChange = { qtyStr = it }, label = { Text("Quantity") }, singleLine = true)
                    OutlinedTextField(value = unitCostStr, onValueChange = { unitCostStr = it }, label = { Text("Unit Cost") }, singleLine = true)
                    OutlinedTextField(value = paidStr, onValueChange = { paidStr = it }, label = { Text("Amount Paid Now") }, singleLine = true)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Receive GRN into stock now")
                        Switch(checked = receiveNow, onCheckedChange = { receiveNow = it })
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val qty = (qtyStr.toLongOrNull() ?: 1L).coerceAtLeast(1L)
                    val cost = (unitCostStr.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)
                    val paid = (paidStr.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)
                    onCreatePurchaseOrder(shop, selSupplier, selProduct, qty, cost, paid, receiveNow)
                    showPoDialog = false
                }) { Text("Confirm PO") }
            },
            dismissButton = { TextButton(onClick = { showPoDialog = false }) { Text("Cancel") } }
        )
    }

    if (editingAsset != null) {
        val initAsset = editingAsset!!
        var name by remember(initAsset.id) { mutableStateOf(initAsset.name) }
        var category by remember(initAsset.id) { mutableStateOf(initAsset.category) }
        var qtyStr by remember(initAsset.id) { mutableStateOf(initAsset.quantity.toString()) }
        var purchaseStr by remember(initAsset.id) { mutableStateOf(if (initAsset.purchasePrice > 0) initAsset.purchasePrice.toString() else "") }
        var currentStr by remember(initAsset.id) { mutableStateOf(if (initAsset.currentValue > 0) initAsset.currentValue.toString() else "") }
        var condition by remember(initAsset.id) { mutableStateOf(initAsset.condition) }
        var serialTag by remember(initAsset.id) { mutableStateOf(initAsset.serialOrTag ?: "") }
        var notes by remember(initAsset.id) { mutableStateOf(initAsset.notes ?: "") }

        AlertDialog(
            onDismissRequest = { editingAsset = null },
            title = { Text(if (initAsset.id.isBlank()) "Add Shop Asset" else "Edit Shop Asset") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Asset Name*") }, singleLine = true)
                    Text("Category", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(listOf("Equipment", "Fixtures", "POS Hardware", "Vehicles", "Display", "Other")) { c ->
                            FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c) })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(value = qtyStr, onValueChange = { qtyStr = it }, label = { Text("Qty") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = purchaseStr, onValueChange = { purchaseStr = it }, label = { Text("Purchase Cost") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    OutlinedTextField(value = currentStr, onValueChange = { currentStr = it }, label = { Text("Current Unit Worth*") }, singleLine = true)
                    Text("Condition", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(listOf("New", "Good", "Fair", "Needs Repair")) { cond ->
                            FilterChip(selected = condition == cond, onClick = { condition = cond }, label = { Text(cond) })
                        }
                    }
                    OutlinedTextField(value = serialTag, onValueChange = { serialTag = it }, label = { Text("Serial / Asset Tag") }, singleLine = true)
                    OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") })
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val qty = (qtyStr.toLongOrNull() ?: 1L).coerceAtLeast(1L)
                        val purchase = (purchaseStr.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)
                        val current = (currentStr.toDoubleOrNull() ?: purchase).coerceAtLeast(0.0)
                        onSaveAsset(
                            initAsset.copy(
                                name = name.trim(),
                                category = category,
                                quantity = qty,
                                purchasePrice = purchase,
                                currentValue = current,
                                condition = condition,
                                serialOrTag = serialTag.ifBlank { null },
                                notes = notes.ifBlank { null }
                            )
                        )
                        editingAsset = null
                    },
                    enabled = name.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = NexGenCoral)
                ) { Text("Save Asset") }
            },
            dismissButton = { TextButton(onClick = { editingAsset = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ProductEditorDialog(
    initial: Product,
    initialImeis: List<String>,
    onDismiss: () -> Unit,
    onSave: (Product, List<String>) -> Unit
) {
    var name by remember { mutableStateOf(initial.name) }
    var description by remember { mutableStateOf(initial.description) }
    var category by remember { mutableStateOf(initial.category) }
    var subcategory by remember { mutableStateOf(initial.subcategory) }
    var sku by remember { mutableStateOf(initial.sku) }
    var barcode by remember { mutableStateOf(initial.barcode) }
    var sellingStr by remember { mutableStateOf(if (initial.sellingPrice > 0) initial.sellingPrice.toString() else "") }
    var originalStr by remember { mutableStateOf(if (initial.originalPrice > 0) initial.originalPrice.toString() else "") }
    var costStr by remember { mutableStateOf(if (initial.costPrice > 0) initial.costPrice.toString() else "") }
    var stockStr by remember { mutableStateOf(initial.stockQuantity.toString()) }
    var minStockStr by remember { mutableStateOf(initial.minStockLevel.toString()) }
    var location by remember { mutableStateOf(initial.stockLocation) }
    var colorsStr by remember { mutableStateOf(initial.colors.joinToString(", ")) }
    var sizesStr by remember { mutableStateOf(initial.sizes.joinToString(", ")) }
    var specsStr by remember { mutableStateOf(initial.specifications.joinToString(", ")) }
    var imageKey by remember { mutableStateOf(initial.imageKeys.firstOrNull() ?: "smartphone") }

    val looksLikePhone = category.contains("phone", ignoreCase = true) ||
        subcategory.contains("phone", ignoreCase = true) ||
        name.contains("phone", ignoreCase = true) ||
        initialImeis.isNotEmpty()
    var trackImeis by remember { mutableStateOf(looksLikePhone || initialImeis.isNotEmpty()) }
    var imeiInput by remember { mutableStateOf("") }
    var imeiList by remember { mutableStateOf(initialImeis) }

    var isVisible by remember { mutableStateOf(initial.isVisible) }
    var isFlashSale by remember { mutableStateOf(initial.isFlashSale) }
    var isTrending by remember { mutableStateOf(initial.isTrending) }
    var isDailyDeal by remember { mutableStateOf(initial.isDailyDeal) }
    var isNewArrival by remember { mutableStateOf(initial.isNewArrival) }
    var isSponsoredAd by remember { mutableStateOf(initial.isSponsoredAd) }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) imageKey = uri.toString()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id.isBlank()) "Add New Product" else "Edit Product") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Product Name*") }, singleLine = true)
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") })
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Category") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = subcategory, onValueChange = { subcategory = it }, label = { Text("Subcategory") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(value = sku, onValueChange = { sku = it }, label = { Text("SKU (auto if blank)") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = barcode, onValueChange = { barcode = it }, label = { Text("Barcode") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(value = sellingStr, onValueChange = { sellingStr = it }, label = { Text("Sell Price*") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = originalStr, onValueChange = { originalStr = it }, label = { Text("Old Price") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = costStr, onValueChange = { costStr = it }, label = { Text("Cost*") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(value = stockStr, onValueChange = { stockStr = it }, label = { Text("Stock Qty") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = minStockStr, onValueChange = { minStockStr = it }, label = { Text("Min Alert") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(value = location, onValueChange = { location = it }, label = { Text("Stock Location") }, singleLine = true)
                OutlinedTextField(value = colorsStr, onValueChange = { colorsStr = it }, label = { Text("Colors (comma-separated)") }, singleLine = true)
                OutlinedTextField(value = sizesStr, onValueChange = { sizesStr = it }, label = { Text("Sizes/Variants (comma-separated)") }, singleLine = true)
                OutlinedTextField(value = specsStr, onValueChange = { specsStr = it }, label = { Text("Specs (comma-separated)") })

                Text("Product Artwork / Photo", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Pick Photo")
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(listOf("smartphone", "audio", "watch", "nexgen_logo")) { k ->
                            FilterChip(selected = imageKey == k, onClick = { imageKey = k }, label = { Text(k) })
                        }
                    }
                }

                ToggleFlag("Track Phone IMEIs (Admin/Staff Only)", trackImeis) { trackImeis = it }
                if (trackImeis) {
                    Surface(
                        color = NexGenCobalt.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "Private Phone IMEIs (${imeiList.size}) • Customers ONLY see picture & price",
                                style = MaterialTheme.typography.labelMedium,
                                color = NexGenCobalt,
                                fontWeight = FontWeight.Bold
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = imeiInput,
                                    onValueChange = { imeiInput = it },
                                    label = { Text("Enter IMEI(s) separated by comma/line") },
                                    modifier = Modifier.weight(1f)
                                )
                                Button(
                                    onClick = {
                                        val added = imeiInput.split(',', '\n', ' ').map { it.trim() }.filter { it.isNotBlank() }
                                        if (added.isNotEmpty()) {
                                            imeiList = (imeiList + added).distinct()
                                            imeiInput = ""
                                            if ((stockStr.toLongOrNull() ?: 0L) < imeiList.size) {
                                                stockStr = imeiList.size.toString()
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = NexGenCobalt)
                                ) { Text("Add") }
                            }
                            if (imeiList.isNotEmpty()) {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(imeiList) { imei ->
                                        FilterChip(
                                            selected = true,
                                            onClick = { imeiList = imeiList.filterNot { it == imei } },
                                            label = { Text("$imei ×") }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                ToggleFlag("Visible on Marketplace", isVisible) { isVisible = it }
                ToggleFlag("Flash Sale", isFlashSale) { isFlashSale = it }
                ToggleFlag("Trending", isTrending) { isTrending = it }
                ToggleFlag("Daily Deal", isDailyDeal) { isDailyDeal = it }
                ToggleFlag("New Arrival", isNewArrival) { isNewArrival = it }
                ToggleFlag("Sponsored Ad Spotlight", isSponsoredAd) { isSponsoredAd = it }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val sell = sellingStr.toDoubleOrNull() ?: 0.0
                    val orig = originalStr.toDoubleOrNull() ?: sell
                    val cost = costStr.toDoubleOrNull() ?: 0.0
                    val pendingImeis = if (trackImeis) {
                        (imeiList + imeiInput.split(',', '\n', ' ').map { it.trim() }.filter { it.isNotBlank() }).distinct()
                    } else {
                        emptyList()
                    }
                    onSave(
                        initial.copy(
                            name = name.trim(),
                            description = description.trim(),
                            category = category.ifBlank { "Electronics" },
                            subcategory = subcategory.ifBlank { "General" },
                            sku = sku.trim(),
                            barcode = barcode.trim(),
                            sellingPrice = sell,
                            originalPrice = orig.coerceAtLeast(sell),
                            costPrice = cost,
                            stockQuantity = stockStr.toLongOrNull() ?: pendingImeis.size.toLong(),
                            minStockLevel = minStockStr.toLongOrNull() ?: 5L,
                            stockLocation = location.ifBlank { "Main Warehouse" },
                            colors = colorsStr.split(",").map { it.trim() }.filter { it.isNotBlank() },
                            sizes = sizesStr.split(",").map { it.trim() }.filter { it.isNotBlank() },
                            specifications = specsStr.split(",").map { it.trim() }.filter { it.isNotBlank() },
                            imageKeys = listOf(imageKey),
                            isVisible = isVisible,
                            isFlashSale = isFlashSale,
                            isTrending = isTrending,
                            isDailyDeal = isDailyDeal,
                            isNewArrival = isNewArrival,
                            isSponsoredAd = isSponsoredAd
                        ),
                        pendingImeis
                    )
                },
                enabled = name.isNotBlank() && (sellingStr.toDoubleOrNull() ?: -1.0) >= 0.0,
                colors = ButtonDefaults.buttonColors(containerColor = NexGenCoral)
            ) { Text("Save Product") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ToggleFlag(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
