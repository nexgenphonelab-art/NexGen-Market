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
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Schedule
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.ManualServiceEntry
import com.example.data.model.Shift
import com.example.data.model.Shop
import com.example.ui.theme.NexGenAmber
import com.example.ui.theme.NexGenCobalt
import com.example.ui.theme.NexGenCoral
import com.example.ui.theme.NexGenEmerald
import com.example.util.PrintAndExportUtils
import com.example.util.ReceiptLine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val SERVICE_CATEGORIES = listOf(
    "Phone Repair",
    "Unlocking",
    "Flashing / Software",
    "Screen Replacement",
    "Battery / Charging",
    "Diagnostics",
    "Custom Service"
)

@Composable
fun ManualServiceBookScreen(
    shop: Shop,
    shifts: List<Shift>,
    manualServices: List<ManualServiceEntry>,
    isAdminRole: Boolean,
    onOpenShift: (Shop, Double) -> Unit,
    onRecordService: (
        shop: Shop,
        serviceName: String,
        category: String,
        deviceModel: String,
        deviceImei: String?,
        customerName: String,
        customerPhone: String?,
        serviceFee: Double,
        partsCost: Double,
        paymentMode: String,
        mpesaCode: String?,
        status: String,
        notes: String?
    ) -> Unit,
    onUpdateServiceStatus: (ManualServiceEntry, String) -> Unit
) {
    val context = LocalContext.current
    val openShift = remember(shifts) { shifts.firstOrNull { it.status == "Open" } }
    val isPosActive = openShift != null

    val todayKey = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    var selectedDateFilter by remember { mutableStateOf("All") }
    var showAddDialog by remember { mutableStateOf(false) }
    var showActivatePosDialog by remember { mutableStateOf(false) }
    var statusEditEntry by remember { mutableStateOf<ManualServiceEntry?>(null) }

    val availableDates = remember(manualServices, todayKey) {
        (listOf(todayKey) + manualServices.map { it.dateKey }.filter { it.isNotBlank() })
            .distinct()
            .sortedDescending()
    }

    val filteredEntries = remember(manualServices, selectedDateFilter) {
        if (selectedDateFilter == "All") manualServices
        else manualServices.filter { it.dateKey == selectedDateFilter }
    }

    val groupedByDate = remember(filteredEntries) {
        filteredEntries.groupBy { it.dateKey.ifBlank { todayKey } }
    }

    val todayEntries = remember(manualServices, todayKey) {
        manualServices.filter { it.dateKey == todayKey && it.status != "Cancelled" }
    }
    val todayTotalFees = remember(todayEntries) { todayEntries.sumOf { it.serviceFee } }
    val todayTotalNet = remember(todayEntries) { todayEntries.sumOf { it.serviceFee - it.partsCost } }

    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.US)
            PrintAndExportUtils.exportCsvToUri(
                context = context,
                uri = uri,
                headers = listOf("EntryNo", "Date", "Time", "Category", "Service", "DeviceModel", "IMEI", "Customer", "Phone", "Fee", "PartsCost", "Net", "Payment", "Status", "Staff"),
                rows = filteredEntries.map { e ->
                    val tsMs = e.recordedAtMillis.takeIf { it > 0L } ?: ((e.createdAt?.seconds ?: 0L) * 1000L)
                    listOf(
                        e.entryNumber,
                        e.dateKey,
                        if (tsMs > 0L) timeFmt.format(Date(tsMs)) else "-",
                        e.category,
                        e.serviceName,
                        e.deviceModel,
                        e.deviceImei ?: "",
                        e.customerName,
                        e.customerPhone ?: "",
                        e.serviceFee.toString(),
                        e.partsCost.toString(),
                        (e.serviceFee - e.partsCost).toString(),
                        e.paymentMode,
                        e.status,
                        e.recordedByName
                    )
                }
            )
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // POS Status & Activation Banner
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isPosActive) NexGenEmerald.copy(alpha = 0.12f) else NexGenAmber.copy(alpha = 0.14f)
                )
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            imageVector = if (isPosActive) Icons.Filled.LockOpen else Icons.Filled.Lock,
                            contentDescription = null,
                            tint = if (isPosActive) NexGenEmerald else NexGenAmber
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isPosActive) "Manual Service Book Active" else "Manual Service Book Locked",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isPosActive) NexGenEmerald else NexGenAmber
                            )
                            Text(
                                text = if (isPosActive) {
                                    "POS Shift Open (${openShift!!.staffName}) • Entries stamped automatically with today's date & time"
                                } else {
                                    "Active only when POS is active. Open a POS Cash Shift to unlock new manual service entries."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (isPosActive) {
                            Button(
                                onClick = { showAddDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = NexGenCoral),
                                modifier = Modifier.weight(1f).height(48.dp).testTag("record_manual_service_button")
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Record Manual Service", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick = { showActivatePosDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = NexGenCobalt),
                                modifier = Modifier.weight(1f).height(48.dp).testTag("unlock_manual_book_pos_button")
                            ) {
                                Icon(Icons.Filled.LockOpen, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Activate POS & Unlock Book", fontWeight = FontWeight.Bold)
                            }
                        }
                        OutlinedButton(
                            onClick = { csvLauncher.launch("${shop.shopName.replace(" ", "_")}_manual_book.csv") },
                            modifier = Modifier.height(48.dp)
                        ) {
                            Icon(Icons.Filled.TableChart, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("CSV")
                        }
                    }
                }
            }
        }

        // Today's Daily Service Book Summary
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Today ($todayKey)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${todayEntries.size} Services", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Daily Fees", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(PrintAndExportUtils.formatMoney(todayTotalFees, shop.currencyCode), style = MaterialTheme.typography.titleMedium, color = NexGenCoral, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Net Service Income", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(PrintAndExportUtils.formatMoney(todayTotalNet, shop.currencyCode), style = MaterialTheme.typography.titleMedium, color = NexGenEmerald, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Daily Date Filter Chips
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = selectedDateFilter == "All",
                        onClick = { selectedDateFilter = "All" },
                        label = { Text("All Days (${manualServices.size})") }
                    )
                }
                items(availableDates) { dateKey ->
                    val count = manualServices.count { it.dateKey == dateKey }
                    FilterChip(
                        selected = selectedDateFilter == dateKey,
                        onClick = { selectedDateFilter = dateKey },
                        leadingIcon = { Icon(Icons.Filled.CalendarToday, contentDescription = null, modifier = Modifier.size(14.dp)) },
                        label = { Text(if (dateKey == todayKey) "Today ($count)" else "$dateKey ($count)") }
                    )
                }
            }
        }

        if (groupedByDate.isEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Filled.Build, contentDescription = null, modifier = Modifier.size(40.dp), tint = NexGenCobalt)
                        Text("No Manual Service Records Yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            if (isPosActive) "Tap 'Record Manual Service' above to log repairs, unlocking, flashing, diagnostics, or custom jobs with exact date & time."
                            else "Open a POS shift first to start logging daily manual services.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            groupedByDate.forEach { (dateKey, dayEntries) ->
                val dayFees = dayEntries.filter { it.status != "Cancelled" }.sumOf { it.serviceFee }
                val dayNet = dayEntries.filter { it.status != "Cancelled" }.sumOf { it.serviceFee - it.partsCost }

                item(key = "header_$dateKey") {
                    Surface(color = NexGenCobalt.copy(alpha = 0.12f), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.CalendarToday, contentDescription = null, tint = NexGenCobalt, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (dateKey == todayKey) "Today • $dateKey" else dateKey,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = NexGenCobalt
                                )
                            }
                            Text(
                                "${dayEntries.size} jobs • ${PrintAndExportUtils.formatMoney(dayFees, shop.currencyCode)} (Net ${PrintAndExportUtils.formatMoney(dayNet, shop.currencyCode)})",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                items(dayEntries, key = { it.id }) { entry ->
                    val tsMillis = entry.recordedAtMillis.takeIf { it > 0L } ?: ((entry.createdAt?.seconds ?: 0L) * 1000L)
                    val timeLabel = remember(tsMillis) {
                        if (tsMillis > 0L) SimpleDateFormat("dd MMM yyyy • HH:mm:ss", Locale.US).format(Date(tsMillis))
                        else entry.dateKey
                    }

                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("${entry.entryNumber} • ${entry.category}", style = MaterialTheme.typography.labelLarge, color = NexGenCobalt, fontWeight = FontWeight.Bold)
                                Surface(
                                    color = when (entry.status) {
                                        "Completed" -> NexGenEmerald.copy(alpha = 0.14f)
                                        "Pending Parts" -> NexGenAmber.copy(alpha = 0.16f)
                                        else -> MaterialTheme.colorScheme.errorContainer
                                    },
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        entry.status,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = when (entry.status) {
                                            "Completed" -> NexGenEmerald
                                            "Pending Parts" -> NexGenAmber
                                            else -> MaterialTheme.colorScheme.error
                                        },
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            Text(entry.serviceName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(4.dp))
                                Text(timeLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            if (entry.deviceModel.isNotBlank() || !entry.deviceImei.isNullOrBlank()) {
                                Text(
                                    "Device: ${entry.deviceModel.ifBlank { "-" }}${entry.deviceImei?.takeIf { it.isNotBlank() }?.let { " • IMEI: $it" } ?: ""}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Text(
                                "Customer: ${entry.customerName}${entry.customerPhone?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""} • Staff: ${entry.recordedByName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (!entry.notes.isNullOrBlank()) {
                                Text("Notes: ${entry.notes}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            HorizontalDivider()
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column {
                                    Text(
                                        "Fee: ${PrintAndExportUtils.formatMoney(entry.serviceFee, shop.currencyCode)} (${entry.paymentMode}${entry.mpesaCode?.let { " • $it" } ?: ""})",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = NexGenCoral,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (entry.partsCost > 0.0) {
                                        Text(
                                            "Parts: ${PrintAndExportUtils.formatMoney(entry.partsCost, shop.currencyCode)} • Net: ${PrintAndExportUtils.formatMoney(entry.serviceFee - entry.partsCost, shop.currencyCode)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = NexGenEmerald
                                        )
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedButton(onClick = {
                                        val desc = if (entry.deviceModel.isNotBlank()) "${entry.serviceName} (${entry.deviceModel})" else entry.serviceName
                                        PrintAndExportUtils.printReceipt(
                                            context = context,
                                            jobName = "Service_${entry.entryNumber}",
                                            shop = shop,
                                            receiptNumber = entry.entryNumber,
                                            dateStr = timeLabel,
                                            cashierName = entry.recordedByName,
                                            customerName = entry.customerName,
                                            lines = listOf(ReceiptLine(desc, entry.category, 1L, entry.serviceFee, entry.serviceFee)),
                                            subtotal = entry.serviceFee,
                                            discount = 0.0,
                                            tax = 0.0,
                                            total = entry.serviceFee,
                                            paymentInfo = "${entry.paymentMode} ${entry.mpesaCode ?: ""}".trim()
                                        )
                                    }) {
                                        Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Receipt")
                                    }
                                    if (isAdminRole) {
                                        OutlinedButton(onClick = { statusEditEntry = entry }) {
                                            Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(15.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Status")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showActivatePosDialog) {
        var openingFloat by remember { mutableStateOf("0") }
        AlertDialog(
            onDismissRequest = { showActivatePosDialog = false },
            title = { Text("Activate POS Shift") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Opening a POS cash shift activates both the POS register and today's Manual Service Book.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = openingFloat,
                        onValueChange = { openingFloat = it },
                        label = { Text("Opening Float (${shop.currencyCode})") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onOpenShift(shop, openingFloat.toDoubleOrNull() ?: 0.0)
                        showActivatePosDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NexGenCobalt)
                ) { Text("Start Shift & Unlock") }
            },
            dismissButton = { TextButton(onClick = { showActivatePosDialog = false }) { Text("Cancel") } }
        )
    }

    if (showAddDialog) {
        var category by remember { mutableStateOf(SERVICE_CATEGORIES.first()) }
        var serviceName by remember { mutableStateOf("") }
        var deviceModel by remember { mutableStateOf("") }
        var deviceImei by remember { mutableStateOf("") }
        var customerName by remember { mutableStateOf("") }
        var customerPhone by remember { mutableStateOf("") }
        var feeStr by remember { mutableStateOf("") }
        var partsStr by remember { mutableStateOf("0") }
        var paymentMode by remember { mutableStateOf("Cash") }
        var mpesaCode by remember { mutableStateOf("") }
        var status by remember { mutableStateOf("Completed") }
        var notes by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Record Manual Service") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Service Category", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(SERVICE_CATEGORIES) { cat ->
                            FilterChip(
                                selected = category == cat,
                                onClick = {
                                    category = cat
                                    if (serviceName.isBlank()) serviceName = cat
                                },
                                label = { Text(cat) }
                            )
                        }
                    }
                    OutlinedTextField(value = serviceName, onValueChange = { serviceName = it }, label = { Text("Service Offered*") }, singleLine = true)
                    OutlinedTextField(value = deviceModel, onValueChange = { deviceModel = it }, label = { Text("Device / Model (e.g. iPhone 13 Pro)") }, singleLine = true)
                    OutlinedTextField(value = deviceImei, onValueChange = { deviceImei = it }, label = { Text("Device IMEI / Serial (Admin/Staff Only)") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(value = customerName, onValueChange = { customerName = it }, label = { Text("Customer Name") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = customerPhone, onValueChange = { customerPhone = it }, label = { Text("Phone") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(value = feeStr, onValueChange = { feeStr = it }, label = { Text("Service Fee (${shop.currencyCode})*") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = partsStr, onValueChange = { partsStr = it }, label = { Text("Parts Cost") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    Text("Payment Mode", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(listOf("Cash", "M-Pesa", "Card", "Credit")) { m ->
                            FilterChip(selected = paymentMode == m, onClick = { paymentMode = m }, label = { Text(m) })
                        }
                    }
                    if (paymentMode == "M-Pesa") {
                        OutlinedTextField(value = mpesaCode, onValueChange = { mpesaCode = it.uppercase() }, label = { Text("M-Pesa Code") }, singleLine = true)
                    }
                    Text("Service Status", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(listOf("Completed", "Pending Parts", "Warranty")) { s ->
                            FilterChip(selected = status == s, onClick = { status = s }, label = { Text(s) })
                        }
                    }
                    OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Work Notes / Spare Details") })
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val fee = feeStr.toDoubleOrNull() ?: -1.0
                        val parts = (partsStr.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)
                        if (serviceName.isNotBlank() && fee >= 0.0) {
                            onRecordService(
                                shop,
                                serviceName,
                                category,
                                deviceModel,
                                deviceImei.ifBlank { null },
                                customerName.ifBlank { "Walk-in Customer" },
                                customerPhone.ifBlank { null },
                                fee,
                                parts,
                                paymentMode,
                                mpesaCode.ifBlank { null },
                                status,
                                notes.ifBlank { null }
                            )
                            showAddDialog = false
                        }
                    },
                    enabled = serviceName.isNotBlank() && (feeStr.toDoubleOrNull() ?: -1.0) >= 0.0,
                    colors = ButtonDefaults.buttonColors(containerColor = NexGenCoral)
                ) { Text("Save to Daily Book") }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("Cancel") } }
        )
    }

    if (statusEditEntry != null && isAdminRole) {
        val entry = statusEditEntry!!
        var newStatus by remember { mutableStateOf(entry.status) }
        AlertDialog(
            onDismissRequest = { statusEditEntry = null },
            title = { Text("Update ${entry.entryNumber}") },
            text = {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(listOf("Completed", "Pending Parts", "Warranty", "Cancelled")) { s ->
                        FilterChip(selected = newStatus == s, onClick = { newStatus = s }, label = { Text(s) })
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    onUpdateServiceStatus(entry, newStatus)
                    statusEditEntry = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { statusEditEntry = null }) { Text("Cancel") } }
        )
    }
}
