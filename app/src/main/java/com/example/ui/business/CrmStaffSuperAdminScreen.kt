package com.example.ui.business

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AdminRecord
import com.example.data.model.AuditLog
import com.example.data.model.Coupon
import com.example.data.model.CrmCustomer
import com.example.data.model.Expense
import com.example.data.model.GatewayConfig
import com.example.data.model.Order
import com.example.data.model.Product
import com.example.data.model.Shift
import com.example.data.model.Shop
import com.example.data.model.StaffMember
import com.example.ui.theme.NexGenAmber
import com.example.ui.theme.NexGenCobalt
import com.example.ui.theme.NexGenCoral
import com.example.ui.theme.NexGenEmerald
import com.example.ui.viewmodel.FinancialSummary
import com.example.util.PrintAndExportUtils
import java.io.BufferedReader
import java.io.InputStreamReader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrmOpsScreen(
    shop: Shop,
    summary: FinancialSummary,
    products: List<Product>,
    orders: List<Order>,
    crmCustomers: List<CrmCustomer>,
    expenses: List<Expense>,
    coupons: List<Coupon>,
    staffList: List<StaffMember>,
    shifts: List<Shift>,
    auditLogs: List<AuditLog>,
    gatewayConfig: GatewayConfig?,
    onSaveCrmCustomer: (CrmCustomer) -> Unit,
    onRecordExpense: (Expense) -> Unit,
    onSaveCoupon: (Coupon) -> Unit,
    onSaveStaff: (StaffMember) -> Unit,
    onDeleteStaff: (StaffMember) -> Unit = {}
) {
    val context = LocalContext.current
    var subTab by remember { mutableIntStateOf(0) }
    var showCustomerDialog by remember { mutableStateOf(false) }
    var showExpenseDialog by remember { mutableStateOf(false) }
    var showCouponDialog by remember { mutableStateOf(false) }
    var showStaffDialog by remember { mutableStateOf(false) }
    var auditModuleFilter by remember { mutableStateOf("All") }

    val auditPdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) {
            val sumLines = listOf(
                "Shop Audit: ${shop.shopName} (${shop.currencyCode})",
                "Total Shop Worth: ${PrintAndExportUtils.formatMoney(summary.totalShopWorth, shop.currencyCode)}",
                "Stock Valuation: ${PrintAndExportUtils.formatMoney(summary.stockValuation, shop.currencyCode)}",
                "Fixed Assets Worth: ${PrintAndExportUtils.formatMoney(summary.assetsValuation, shop.currencyCode)}",
                "Low Stock SKUs: ${summary.lowStockCount} | Out-of-Stock SKUs: ${summary.outOfStockCount}",
                "Pending / Unverified Orders: ${orders.count { it.paymentStatus != "Paid" && it.status != "Cancelled" }}",
                "Open / Variance Cash Shifts: ${shifts.count { it.status == "Open" || kotlin.math.abs(it.variance) > 0.01 }}",
                "M-Pesa STK Active: ${shop.mpesaStkEnabled && !gatewayConfig?.darajaShortcode.isNullOrBlank()}"
            )
            val logLines = auditLogs.take(60).map { log ->
                "${PrintAndExportUtils.formatDate(log.createdAt?.toDate()?.time)} | [${log.module}] ${log.action} by ${log.actorName ?: log.actorUid}"
            }
            PrintAndExportUtils.exportPdfReportToUri(context, uri, "${shop.shopName} App Audit Report", sumLines, logLines)
        }
    }

    val auditCsvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            PrintAndExportUtils.exportCsvToUri(
                context = context,
                uri = uri,
                headers = listOf("Date", "Module", "Action", "Actor", "Role", "Details"),
                rows = auditLogs.map {
                    listOf(
                        PrintAndExportUtils.formatDate(it.createdAt?.toDate()?.time),
                        it.module,
                        it.action,
                        it.actorName ?: it.actorUid,
                        it.actorRole ?: "",
                        it.details ?: ""
                    )
                }
            )
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        PrimaryScrollableTabRow(selectedTabIndex = subTab, edgePadding = 8.dp) {
            Tab(selected = subTab == 0, onClick = { subTab = 0 }, text = { Text("CRM") })
            Tab(selected = subTab == 1, onClick = { subTab = 1 }, text = { Text("Expenses") })
            Tab(selected = subTab == 2, onClick = { subTab = 2 }, text = { Text("Coupons") })
            Tab(selected = subTab == 3, onClick = { subTab = 3 }, text = { Text("Staff") })
            Tab(selected = subTab == 4, onClick = { subTab = 4 }, text = { Text("Audit") })
        }

        when (subTab) {
            0 -> {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        Button(onClick = { showCustomerDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = NexGenCobalt), modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Add CRM Customer")
                        }
                    }
                    items(crmCustomers, key = { it.id }) { cust ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("${cust.name} (${cust.group})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text("${cust.loyaltyPoints} pts", color = NexGenAmber, style = MaterialTheme.typography.labelMedium)
                                }
                                Text("${cust.phone ?: "-"} • ${cust.email ?: "-"}", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    "Spent: ${PrintAndExportUtils.formatMoney(cust.totalSpent, shop.currencyCode)} | Credit Debt: ${PrintAndExportUtils.formatMoney(cust.outstandingBalance, shop.currencyCode)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (cust.outstandingBalance > 0) NexGenCoral else NexGenEmerald
                                )
                                if (!cust.supportNote.isNullOrBlank()) {
                                    Text("Note: ${cust.supportNote}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
            1 -> {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        Button(onClick = { showExpenseDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = NexGenCoral), modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Record Expense")
                        }
                    }
                    items(expenses, key = { it.id }) { exp ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(exp.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text("${exp.category} • ${exp.paymentMethod ?: "Cash"}", style = MaterialTheme.typography.bodySmall)
                                }
                                Text(PrintAndExportUtils.formatMoney(exp.amount, shop.currencyCode), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            2 -> {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        Button(onClick = { showCouponDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = NexGenEmerald), modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Create Promo Coupon")
                        }
                    }
                    items(coupons, key = { it.id }) { cpn ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column {
                                    Text(cpn.code, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = NexGenCoral)
                                    Text("${cpn.discountValue} (${cpn.discountType}) • Min: ${cpn.minOrderAmount} • Used: ${cpn.usageCount}", style = MaterialTheme.typography.bodySmall)
                                }
                                Switch(checked = cpn.isActive, onCheckedChange = { onSaveCoupon(cpn.copy(isActive = it)) })
                            }
                        }
                    }
                }
            }
            3 -> {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        Button(onClick = { showStaffDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = NexGenCobalt), modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Add Staff Account & Permissions")
                        }
                    }
                    items(staffList, key = { it.id }) { st ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${st.staffName} (${st.roleTitle})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        Text(st.staffEmail, style = MaterialTheme.typography.bodySmall)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Switch(checked = st.isActive, onCheckedChange = { onSaveStaff(st.copy(isActive = it)) })
                                        androidx.compose.material3.IconButton(onClick = { onDeleteStaff(st) }) {
                                            Icon(Icons.Filled.Delete, contentDescription = "Remove Staff", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                                Text("Permissions: ${st.permissions.joinToString(", ")}", style = MaterialTheme.typography.labelSmall, color = NexGenCobalt)
                                Text("Salespersons cannot undo/redo or refund completed sales.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    item { Text("Recent Shifts & Cash Drawers (${shifts.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                    items(shifts.take(20), key = { it.id }) { sh ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(sh.staffName, style = MaterialTheme.typography.titleSmall)
                                    Text(sh.status, color = if (sh.status == "Open") NexGenEmerald else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text("Sales: ${PrintAndExportUtils.formatMoney(sh.totalSales, shop.currencyCode)} • Expected Cash: ${PrintAndExportUtils.formatMoney(sh.expectedCash, shop.currencyCode)} • Variance: ${sh.variance}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            else -> {
                val outOfStock = remember(products) { products.count { it.stockQuantity <= 0L } }
                val lowStock = remember(products) { products.count { it.stockQuantity in 1L..it.minStockLevel } }
                val unverifiedOrders = remember(orders) { orders.count { it.paymentStatus != "Paid" && it.status != "Cancelled" } }
                val openOrVarShifts = remember(shifts) { shifts.count { it.status == "Open" || kotlin.math.abs(it.variance) > 0.01 } }
                val mpesaConfigured = shop.mpesaStkEnabled &&
                    !gatewayConfig?.darajaConsumerKey.isNullOrBlank() &&
                    !gatewayConfig?.darajaConsumerSecret.isNullOrBlank() &&
                    !gatewayConfig?.darajaPasskey.isNullOrBlank()

                val filteredLogs = remember(auditLogs, auditModuleFilter) {
                    if (auditModuleFilter == "All") auditLogs
                    else auditLogs.filter { it.module.equals(auditModuleFilter, ignoreCase = true) }
                }

                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.FactCheck, contentDescription = null, tint = NexGenCobalt)
                                    Spacer(Modifier.width(8.dp))
                                    Text("General App Audit & Health Diagnostics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                }
                                AuditCheckRow(
                                    label = "Total Shop Worth (Stock + Assets + Net Cash)",
                                    statusText = PrintAndExportUtils.formatMoney(summary.totalShopWorth, shop.currencyCode),
                                    isOk = summary.totalShopWorth >= 0.0
                                )
                                AuditCheckRow(
                                    label = "Inventory Risk ($outOfStock out of stock, $lowStock low)",
                                    statusText = if (outOfStock == 0 && lowStock == 0) "OK" else "Attention",
                                    isOk = outOfStock == 0 && lowStock == 0
                                )
                                AuditCheckRow(
                                    label = "Unverified / Pending Order Payments ($unverifiedOrders)",
                                    statusText = if (unverifiedOrders == 0) "Clear" else "Review",
                                    isOk = unverifiedOrders == 0
                                )
                                AuditCheckRow(
                                    label = "Cash Shifts Open / Variance ($openOrVarShifts)",
                                    statusText = if (openOrVarShifts == 0) "Reconciled" else "Active/Check",
                                    isOk = openOrVarShifts == 0
                                )
                                AuditCheckRow(
                                    label = "M-Pesa Daraja STK Push Integration",
                                    statusText = if (mpesaConfigured) "Configured" else if (shop.mpesaStkEnabled) "Needs Keys" else "Manual Paybill",
                                    isOk = mpesaConfigured || !shop.mpesaStkEnabled
                                )
                                AuditCheckRow(
                                    label = "Receipt Branding & App Logo",
                                    statusText = if (shop.receiptShowLogo) "Logo Enabled" else "Text Header Only",
                                    isOk = shop.receiptShowLogo
                                )
                                HorizontalDivider()
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { auditPdfLauncher.launch("${shop.shopName.replace(" ", "_")}_audit.pdf") },
                                        colors = ButtonDefaults.buttonColors(containerColor = NexGenCoral),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Audit PDF")
                                    }
                                    OutlinedButton(
                                        onClick = { auditCsvLauncher.launch("${shop.shopName.replace(" ", "_")}_audit.csv") },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Filled.TableChart, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Audit CSV")
                                    }
                                }
                            }
                        }
                    }

                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(listOf("All", "Inventory", "POS", "Orders", "Assets", "M-Pesa", "Settings")) { mod ->
                                FilterChip(
                                    selected = auditModuleFilter == mod,
                                    onClick = { auditModuleFilter = mod },
                                    label = { Text(mod) }
                                )
                            }
                        }
                    }

                    item {
                        Text("Activity & Compliance Trail (${filteredLogs.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    items(filteredLogs.take(60), key = { it.id }) { log ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("[${log.module}] ${log.action}", style = MaterialTheme.typography.labelLarge, color = NexGenCobalt)
                                    Text(PrintAndExportUtils.formatDate(log.createdAt?.toDate()?.time), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text("Actor: ${log.actorName ?: log.actorUid} (${log.actorRole ?: "Staff"})", style = MaterialTheme.typography.bodySmall)
                                if (!log.details.isNullOrBlank()) {
                                    Text(log.details, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCustomerDialog) {
        var name by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        var email by remember { mutableStateOf("") }
        var group by remember { mutableStateOf("Retail") }
        var note by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCustomerDialog = false },
            title = { Text("Add CRM Customer") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Customer Name*") }, singleLine = true)
                    OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone") }, singleLine = true)
                    OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, singleLine = true)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(listOf("Retail", "VIP", "Wholesale")) { g -> FilterChip(selected = g == group, onClick = { group = g }, label = { Text(g) }) }
                    }
                    OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Support / Account Note") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (name.isNotBlank()) {
                        onSaveCrmCustomer(CrmCustomer(shopId = shop.id, shopOwnerId = shop.ownerId, name = name, phone = phone, email = email, group = group, supportNote = note))
                        showCustomerDialog = false
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showCustomerDialog = false }) { Text("Cancel") } }
        )
    }

    if (showExpenseDialog) {
        var title by remember { mutableStateOf("") }
        var category by remember { mutableStateOf("Utilities") }
        var amountStr by remember { mutableStateOf("") }
        var method by remember { mutableStateOf("Cash") }
        AlertDialog(
            onDismissRequest = { showExpenseDialog = false },
            title = { Text("Record Business Expense") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Expense Title*") }, singleLine = true)
                    OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Category (Rent/Utilities/Payroll)") }, singleLine = true)
                    OutlinedTextField(value = amountStr, onValueChange = { amountStr = it }, label = { Text("Amount (> 0)*") }, singleLine = true)
                    OutlinedTextField(value = method, onValueChange = { method = it }, label = { Text("Payment Method") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val amt = amountStr.toDoubleOrNull() ?: 0.0
                    if (title.isNotBlank() && amt > 0.0) {
                        onRecordExpense(Expense(shopId = shop.id, shopOwnerId = shop.ownerId, title = title, category = category, amount = amt, paymentMethod = method))
                        showExpenseDialog = false
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showExpenseDialog = false }) { Text("Cancel") } }
        )
    }

    if (showCouponDialog) {
        var code by remember { mutableStateOf("") }
        var type by remember { mutableStateOf("percent") }
        var valueStr by remember { mutableStateOf("10") }
        var minStr by remember { mutableStateOf("0") }
        AlertDialog(
            onDismissRequest = { showCouponDialog = false },
            title = { Text("Create Promo Coupon") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = code, onValueChange = { code = it.uppercase() }, label = { Text("Coupon Code*") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = type == "percent", onClick = { type = "percent" }, label = { Text("Percent %") })
                        FilterChip(selected = type == "fixed", onClick = { type = "fixed" }, label = { Text("Fixed Amount") })
                    }
                    OutlinedTextField(value = valueStr, onValueChange = { valueStr = it }, label = { Text("Discount Value (> 0)*") }, singleLine = true)
                    OutlinedTextField(value = minStr, onValueChange = { minStr = it }, label = { Text("Minimum Order Amount") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val valNum = valueStr.toDoubleOrNull() ?: 0.0
                    if (code.length >= 2 && valNum > 0.0) {
                        onSaveCoupon(Coupon(shopId = shop.id, shopOwnerId = shop.ownerId, code = code, discountType = type, discountValue = valNum, minOrderAmount = (minStr.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)))
                        showCouponDialog = false
                    }
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showCouponDialog = false }) { Text("Cancel") } }
        )
    }

    if (showStaffDialog) {
        var name by remember { mutableStateOf("") }
        var email by remember { mutableStateOf("") }
        var roleTitle by remember { mutableStateOf("Salesperson") }
        var selectedPerms by remember { mutableStateOf(setOf("POS", "ORDERS", "MANUAL_BOOK")) }
        val allPerms = listOf("POS", "ORDERS", "MANUAL_BOOK")
        AlertDialog(
            onDismissRequest = { showStaffDialog = false },
            title = { Text("Add Staff / Salesperson") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Salespersons can accept orders, complete POS sales & log daily manual services. They can NEVER undo or redo a sale.",
                        style = MaterialTheme.typography.labelSmall,
                        color = NexGenCobalt
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = roleTitle == "Salesperson",
                            onClick = {
                                roleTitle = "Salesperson"
                                selectedPerms = setOf("POS", "ORDERS", "MANUAL_BOOK")
                            },
                            label = { Text("Salesperson") }
                        )
                        FilterChip(
                            selected = roleTitle == "Technician",
                            onClick = {
                                roleTitle = "Technician"
                                selectedPerms = setOf("POS", "MANUAL_BOOK")
                            },
                            label = { Text("Technician") }
                        )
                        FilterChip(
                            selected = roleTitle == "Order Fulfillment",
                            onClick = {
                                roleTitle = "Order Fulfillment"
                                selectedPerms = setOf("ORDERS")
                            },
                            label = { Text("Orders") }
                        )
                    }
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Full Name*") }, singleLine = true)
                    OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Google Email*") }, singleLine = true)
                    OutlinedTextField(value = roleTitle, onValueChange = { roleTitle = it }, label = { Text("Role Title") }, singleLine = true)
                    Text("Access Modules", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(allPerms) { p ->
                            FilterChip(
                                selected = selectedPerms.contains(p),
                                onClick = { selectedPerms = if (selectedPerms.contains(p)) selectedPerms - p else selectedPerms + p },
                                label = { Text(p) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (name.isNotBlank() && email.contains("@")) {
                        onSaveStaff(StaffMember(shopId = shop.id, shopOwnerId = shop.ownerId, staffName = name, staffEmail = email, roleTitle = roleTitle, permissions = selectedPerms.toList()))
                        showStaffDialog = false
                    }
                }) { Text("Save Staff") }
            },
            dismissButton = { TextButton(onClick = { showStaffDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun SuperAdminGovernanceScreen(
    activeShop: Shop,
    allShops: List<Shop>,
    admins: List<AdminRecord>,
    auditLogs: List<AuditLog>,
    createBackupJson: () -> String,
    onRestoreBackupJson: (String, Shop) -> Unit,
    onSaveAdmin: (AdminRecord) -> Unit,
    onApproveShop: (String, Boolean, Boolean, Double) -> Unit
) {
    val context = LocalContext.current
    var showAdminDialog by remember { mutableStateOf(false) }

    val backupExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(createBackupJson().toByteArray(Charsets.UTF_8))
                }
            }
        }
    }

    val backupRestoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    BufferedReader(InputStreamReader(input)).readText()
                }
            }.onSuccess { text ->
                if (!text.isNullOrBlank()) onRestoreBackupJson(text, activeShop)
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AdminPanelSettings, contentDescription = null, tint = NexGenCoral)
                        Spacer(Modifier.width(8.dp))
                        Text("Super Admin Controls & Backup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { backupExportLauncher.launch("nexgen_backup.json") }, colors = ButtonDefaults.buttonColors(containerColor = NexGenCobalt), modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Backup, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Backup JSON")
                        }
                        OutlinedButton(onClick = { backupRestoreLauncher.launch(arrayOf("application/json", "text/*")) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Restore JSON")
                        }
                    }
                    Button(onClick = { showAdminDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = NexGenCoral), modifier = Modifier.fillMaxWidth()) {
                        Text("+ Assign Platform Administrator")
                    }
                }
            }
        }

        item { Text("Platform Administrators (${admins.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        items(admins, key = { it.uid }) { adm ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("${adm.displayName} (${adm.role})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text("${adm.email} • UID: ${adm.uid}", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = adm.isActive, onCheckedChange = { onSaveAdmin(adm.copy(isActive = it)) })
                }
            }
        }

        item { Text("Marketplace Shops & Commissions (${allShops.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        items(allShops, key = { it.id }) { s ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${s.shopName} (${s.currencyCode})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("Commission: ${s.commissionRatePercent}% • Owner: ${s.ownerId.take(10)}", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = s.isApproved,
                            onClick = { onApproveShop(s.id, !s.isApproved, s.isFeatured, s.commissionRatePercent) },
                            label = { Text(if (s.isApproved) "Approved" else "Suspended") }
                        )
                        FilterChip(
                            selected = s.isFeatured,
                            onClick = { onApproveShop(s.id, s.isApproved, !s.isFeatured, s.commissionRatePercent) },
                            label = { Text(if (s.isFeatured) "Featured" else "Standard") }
                        )
                    }
                }
            }
        }

        item { Text("Security & Operation Audit Logs (${auditLogs.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        items(auditLogs.take(40), key = { it.id }) { log ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                    Text("[${log.module}] ${log.action}", style = MaterialTheme.typography.labelLarge)
                    Text("Actor: ${log.actorName ?: log.actorUid} (${log.actorRole ?: "User"})", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    if (showAdminDialog) {
        var email by remember { mutableStateOf("") }
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdminDialog = false },
            title = { Text("Assign Store Administrator") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Admins can add/delete products & private phone IMEIs, manage orders, and refund sales. Super Admin stays restricted to nexgenphonelab@gmail.com & mrclvictor14@gmail.com only.",
                        style = MaterialTheme.typography.labelSmall,
                        color = NexGenCobalt
                    )
                    OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Admin Google Email*") }, singleLine = true)
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Display Name*") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (email.contains("@") && name.isNotBlank()) {
                        onSaveAdmin(AdminRecord(uid = "", email = email.trim(), displayName = name.trim(), role = "admin", isActive = true))
                        showAdminDialog = false
                    }
                }) { Text("Assign Admin") }
            },
            dismissButton = { TextButton(onClick = { showAdminDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun AuditCheckRow(label: String, statusText: String, isOk: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Surface(
            color = (if (isOk) NexGenEmerald else NexGenAmber).copy(alpha = 0.16f),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
        ) {
            Text(
                statusText,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (isOk) NexGenEmerald else NexGenAmber,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
}
