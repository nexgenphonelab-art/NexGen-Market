package com.example.ui.business

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.Shop
import com.example.ui.theme.NexGenCobalt
import com.example.ui.theme.NexGenCoral
import com.example.ui.viewmodel.BusinessViewModel
import com.example.ui.viewmodel.MarketplaceViewModel

private enum class BizTab(val label: String, val icon: ImageVector) {
    DASHBOARD("Analytics", Icons.Filled.Analytics),
    POS("POS", Icons.Filled.PointOfSale),
    ORDERS("Orders", Icons.Filled.ShoppingBag),
    INVENTORY("Stock", Icons.Filled.Inventory2),
    RECEIPT_SHOP("Receipt", Icons.Filled.Receipt),
    CRM_OPS("CRM & Ops", Icons.Filled.Group),
    SUPER_ADMIN("Admin", Icons.Filled.AdminPanelSettings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BusinessConsoleShell(
    bizVm: BusinessViewModel,
    marketVm: MarketplaceViewModel,
    activeShop: Shop?,
    allShops: List<Shop>,
    isSuperAdmin: Boolean,
    allowedPermissions: Set<String>,
    productsState: List<com.example.data.model.Product>,
    categoriesState: List<com.example.data.model.Category>,
    ordersState: List<com.example.data.model.Order>,
    posSalesState: List<com.example.data.model.PosSale>,
    posCartState: List<com.example.data.repository.PosCartEntry>,
    shiftsState: List<com.example.data.model.Shift>,
    suppliersState: List<com.example.data.model.Supplier>,
    purchasesState: List<com.example.data.model.PurchaseOrder>,
    stockMovementsState: List<com.example.data.model.StockMovement>,
    shopAssetsState: List<com.example.data.model.ShopAsset>,
    expensesState: List<com.example.data.model.Expense>,
    couponsState: List<com.example.data.model.Coupon>,
    crmState: List<com.example.data.model.CrmCustomer>,
    refundsState: List<com.example.data.model.RefundRequest>,
    staffState: List<com.example.data.model.StaffMember>,
    adminsState: List<com.example.data.model.AdminRecord>,
    auditLogsState: List<com.example.data.model.AuditLog>,
    gatewayConfig: com.example.data.model.GatewayConfig?,
    financialSummary: com.example.ui.viewmodel.FinancialSummary,
    onExitToMarketplace: () -> Unit
) {
    BackHandler(onBack = onExitToMarketplace)

    val isOwnerOrSuper = isSuperAdmin || (activeShop != null && activeShop.ownerId == bizVm.currentUserId)
    val availableTabs = remember(isSuperAdmin, isOwnerOrSuper, allowedPermissions) {
        BizTab.entries.filter { tab ->
            when (tab) {
                BizTab.SUPER_ADMIN -> isSuperAdmin
                BizTab.RECEIPT_SHOP -> isOwnerOrSuper || allowedPermissions.contains("SETTINGS")
                BizTab.CRM_OPS -> isOwnerOrSuper || allowedPermissions.contains("FINANCE")
                BizTab.DASHBOARD -> isOwnerOrSuper || allowedPermissions.contains("FINANCE")
                BizTab.POS -> isOwnerOrSuper || allowedPermissions.contains("POS")
                BizTab.ORDERS -> isOwnerOrSuper || allowedPermissions.contains("ORDERS")
                BizTab.INVENTORY -> isOwnerOrSuper || allowedPermissions.contains("PRODUCTS") || allowedPermissions.contains("INVENTORY")
            }
        }.ifEmpty { listOf(BizTab.POS, BizTab.ORDERS) }
    }

    var currentTab by remember(availableTabs) { mutableStateOf(availableTabs.first()) }

    if (activeShop == null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Create Your Shop & POS") },
                    navigationIcon = {
                        IconButton(onClick = onExitToMarketplace) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Marketplace")
                        }
                    }
                )
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp), contentAlignment = Alignment.Center) {
                var shopName by remember { mutableStateOf("NexGen Phone Lab") }
                var currency by remember { mutableStateOf("KES") }
                var header by remember { mutableStateOf("NEXGEN PHONE LAB") }

                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Filled.Storefront, contentDescription = null, tint = NexGenCoral)
                        Text("Launch Your Universal Business & Receipt Designer", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Set your own shop name, currency, and custom receipt header.", style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(value = shopName, onValueChange = { shopName = it; header = it.uppercase() }, label = { Text("Shop Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = currency, onValueChange = { currency = it.uppercase() }, label = { Text("Currency Code") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = header, onValueChange = { header = it }, label = { Text("Receipt Header Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Button(
                            onClick = {
                                if (shopName.length >= 2) {
                                    bizVm.saveShop(
                                        Shop(
                                            ownerId = bizVm.currentUserId,
                                            shopName = shopName,
                                            currencyCode = currency.ifBlank { "KES" },
                                            receiptHeaderTitle = header.ifBlank { shopName.uppercase() }
                                        )
                                    )
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NexGenCoral),
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                        ) { Text("Create Shop & Continue") }
                    }
                }
            }
        }
        return
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 700.dp

        Scaffold(
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            Column {
                                Text(activeShop.shopName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    if (isSuperAdmin) "Super Admin Console" else "Business & POS Console",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NexGenCoral
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onExitToMarketplace, modifier = Modifier.testTag("exit_business_console_button")) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Marketplace")
                            }
                        }
                    )
                    if (allShops.size > 1) {
                        Surface(color = MaterialTheme.colorScheme.surface) {
                            LazyRow(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(allShops, key = { it.id }) { s ->
                                    FilterChip(
                                        selected = s.id == activeShop.id,
                                        onClick = { bizVm.selectShop(s.id) },
                                        label = { Text(s.shopName) }
                                    )
                                }
                            }
                        }
                    }
                }
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar {
                        availableTabs.take(5).forEach { tab ->
                            NavigationBarItem(
                                selected = currentTab == tab,
                                onClick = { currentTab = tab },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label, maxLines = 1) }
                            )
                        }
                    }
                }
            }
        ) { padding ->
            Row(modifier = Modifier.fillMaxSize().padding(padding)) {
                if (useRail) {
                    NavigationRail {
                        availableTabs.forEach { tab ->
                            NavigationRailItem(
                                selected = currentTab == tab,
                                onClick = { currentTab = tab },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label) }
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f).fillMaxSize()) {
                    if (!useRail && availableTabs.size > 5) {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(availableTabs.drop(5)) { extraTab ->
                                FilterChip(
                                    selected = currentTab == extraTab,
                                    onClick = { currentTab = extraTab },
                                    label = { Text(extraTab.label) }
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        when (currentTab) {
                            BizTab.DASHBOARD -> BusinessDashboardScreen(
                                shop = activeShop,
                                summary = financialSummary,
                                orders = ordersState
                            )
                            BizTab.POS -> PosTerminalScreen(
                                shop = activeShop,
                                products = productsState,
                                posCart = posCartState,
                                posSales = posSalesState,
                                shifts = shiftsState,
                                crmCustomers = crmState,
                                onAddToPosCart = bizVm::addToPosCart,
                                onUpdatePosCartQty = bizVm::updatePosCartQty,
                                onClearPosCart = bizVm::clearPosCart,
                                onScanBarcode = bizVm::scanBarcodeForPos,
                                onCompletePosSale = bizVm::completePosSale,
                                onRefundPosSale = bizVm::refundPosSale,
                                loadPosSaleItems = bizVm::loadPosSaleItems,
                                onOpenShift = bizVm::openShift,
                                onCloseShift = bizVm::closeShift
                            )
                            BizTab.ORDERS -> BusinessOrdersScreen(
                                shop = activeShop,
                                orders = ordersState,
                                refunds = refundsState,
                                loadLineItems = bizVm::loadOrderLineItems,
                                onUpdateOrder = bizVm::updateOrderStatus,
                                onProcessRefund = bizVm::processRefund
                            )
                            BizTab.INVENTORY -> InventoryAndProductsScreen(
                                shop = activeShop,
                                products = productsState,
                                categories = categoriesState,
                                stockMovements = stockMovementsState,
                                suppliers = suppliersState,
                                purchases = purchasesState,
                                shopAssets = shopAssetsState,
                                onSaveProduct = { bizVm.saveProduct(it) },
                                onDeleteProduct = bizVm::deleteProduct,
                                onBulkImportProducts = bizVm::bulkImportProducts,
                                onLoadStarterCatalog = bizVm::createStarterCatalogForShop,
                                onSaveCategory = bizVm::saveCategory,
                                onAdjustStock = bizVm::adjustStock,
                                onSaveSupplier = bizVm::saveSupplier,
                                onCreatePurchaseOrder = bizVm::createPurchaseOrder,
                                onSaveAsset = bizVm::saveShopAsset,
                                onDeleteAsset = bizVm::deleteShopAsset
                            )
                            BizTab.RECEIPT_SHOP -> ShopAndReceiptDesignerScreen(
                                shop = activeShop,
                                gatewayConfig = gatewayConfig,
                                onSaveShop = { bizVm.saveShop(it) },
                                onSaveGatewayConfig = bizVm::saveGatewayConfig
                            )
                            BizTab.CRM_OPS -> CrmOpsScreen(
                                shop = activeShop,
                                summary = financialSummary,
                                products = productsState,
                                orders = ordersState,
                                crmCustomers = crmState,
                                expenses = expensesState,
                                coupons = couponsState,
                                staffList = staffState,
                                shifts = shiftsState,
                                auditLogs = auditLogsState,
                                gatewayConfig = gatewayConfig,
                                onSaveCrmCustomer = bizVm::saveCrmCustomer,
                                onRecordExpense = bizVm::recordExpense,
                                onSaveCoupon = bizVm::saveCoupon,
                                onSaveStaff = bizVm::saveStaffMember
                            )
                            BizTab.SUPER_ADMIN -> SuperAdminGovernanceScreen(
                                activeShop = activeShop,
                                allShops = allShops,
                                admins = adminsState,
                                auditLogs = auditLogsState,
                                createBackupJson = bizVm::createBackupJson,
                                onRestoreBackupJson = bizVm::restoreProductsFromBackupJson,
                                onSaveAdmin = bizVm::saveAdmin,
                                onApproveShop = bizVm::approveShop
                            )
                        }
                    }
                }
            }
        }
    }
}
