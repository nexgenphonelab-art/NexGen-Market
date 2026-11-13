package com.example.ui.marketplace

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AppNotification
import com.example.data.model.Order
import com.example.data.model.OrderLineItem
import com.example.data.model.Product
import com.example.data.model.SavedAddress
import com.example.data.model.Shop
import com.example.data.model.UserProfile
import com.example.data.model.WishlistItem
import com.example.ui.components.ProductImageBox
import com.example.ui.theme.NexGenAmber
import com.example.ui.theme.NexGenCobalt
import com.example.ui.theme.NexGenCoral
import com.example.ui.theme.NexGenEmerald
import com.example.util.PrintAndExportUtils
import kotlinx.coroutines.launch

private val ORDER_TIMELINE_STEPS = listOf(
    "Pending", "Awaiting Payment", "Paid", "Processing", "Packed",
    "Ready for Dispatch", "Shipped", "Out for Delivery", "Delivered"
)

@Composable
fun OrdersScreen(
    orders: List<Order>,
    shops: List<Shop>,
    loadLineItems: suspend (String) -> List<OrderLineItem>,
    onSubmitPaymentRef: (String, String) -> Unit,
    onCancelOrder: (String, String) -> Unit,
    onRequestReturn: (Order, String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var payRefDialogOrder by remember { mutableStateOf<Order?>(null) }
    var cancelDialogOrder by remember { mutableStateOf<Order?>(null) }
    var returnDialogOrder by remember { mutableStateOf<Order?>(null) }

    if (orders.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, tint = NexGenCobalt, modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(12.dp))
            Text("No Orders Yet", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Your live marketplace orders and tracking progress will appear here.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(orders, key = { it.id }) { order ->
            val shop = shops.firstOrNull { it.id == order.shopId } ?: Shop(shopName = order.shopName)
            val stepIndex = ORDER_TIMELINE_STEPS.indexOf(order.status)
            val progress = if (stepIndex >= 0) (stepIndex + 1).toFloat() / ORDER_TIMELINE_STEPS.size else 0f

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text(order.orderNumber, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(order.shopName, style = MaterialTheme.typography.labelSmall, color = NexGenCobalt)
                        }
                        Surface(
                            color = when (order.status) {
                                "Delivered" -> NexGenEmerald.copy(alpha = 0.16f)
                                "Cancelled", "Refunded" -> MaterialTheme.colorScheme.error.copy(alpha = 0.16f)
                                else -> NexGenCoral.copy(alpha = 0.16f)
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                order.status,
                                style = MaterialTheme.typography.labelMedium,
                                color = when (order.status) {
                                    "Delivered" -> NexGenEmerald
                                    "Cancelled", "Refunded" -> MaterialTheme.colorScheme.error
                                    else -> NexGenCoral
                                },
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    if (stepIndex >= 0) {
                        LinearProgressIndicator(
                            progress = { progress },
                            color = NexGenEmerald,
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProductImageBox(order.firstItemImage, order.firstItemName, Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)))
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(order.firstItemName, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                            Text("${order.itemCount} item(s) • ${order.deliveryOption}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "${PrintAndExportUtils.formatMoney(order.totalAmount, shop.currencyCode)} • Payment: ${order.paymentStatus}",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (order.paymentStatus == "Paid") NexGenEmerald else NexGenAmber
                            )
                        }
                    }

                    if (!order.trackingNote.isNullOrBlank()) {
                        Text("Update: ${order.trackingNote}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    HorizontalDivider()

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val items = loadLineItems(order.id)
                                    PrintAndExportUtils.printOrderDocument(
                                        context = context,
                                        jobName = "Invoice_${order.orderNumber}",
                                        shop = shop,
                                        order = order,
                                        lineItems = items,
                                        docType = "TAX INVOICE"
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Invoice")
                        }
                        if (order.deliveryAddress.isNotBlank()) {
                            OutlinedButton(
                                onClick = { PrintAndExportUtils.openMapsLocation(context, order.deliveryAddress) }
                            ) {
                                Icon(Icons.Filled.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Map")
                            }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (order.paymentStatus != "Paid" && order.status != "Cancelled") {
                            Button(
                                onClick = { payRefDialogOrder = order },
                                colors = ButtonDefaults.buttonColors(containerColor = NexGenCobalt),
                                modifier = Modifier.weight(1f)
                            ) { Text("Submit Pay Ref") }
                        }
                        if (order.status in listOf("Pending", "Awaiting Payment")) {
                            OutlinedButton(
                                onClick = { cancelDialogOrder = order },
                                modifier = Modifier.weight(1f)
                            ) { Text("Cancel") }
                        }
                        if (order.status == "Delivered") {
                            OutlinedButton(
                                onClick = { returnDialogOrder = order },
                                modifier = Modifier.weight(1f)
                            ) { Text("Return / Refund") }
                        }
                    }
                }
            }
        }
    }

    if (payRefDialogOrder != null) {
        var code by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { payRefDialogOrder = null },
            title = { Text("Submit M-Pesa / Payment Code") },
            text = {
                OutlinedTextField(value = code, onValueChange = { code = it.uppercase() }, label = { Text("Transaction Code") }, singleLine = true)
            },
            confirmButton = {
                Button(onClick = {
                    if (code.isNotBlank()) {
                        onSubmitPaymentRef(payRefDialogOrder!!.id, code)
                        payRefDialogOrder = null
                    }
                }) { Text("Submit") }
            },
            dismissButton = { TextButton(onClick = { payRefDialogOrder = null }) { Text("Close") } }
        )
    }

    if (cancelDialogOrder != null) {
        var reason by remember { mutableStateOf("Changed my mind") }
        AlertDialog(
            onDismissRequest = { cancelDialogOrder = null },
            title = { Text("Cancel Order ${cancelDialogOrder!!.orderNumber}?") },
            text = { OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("Cancellation Reason") }) },
            confirmButton = {
                Button(onClick = {
                    onCancelOrder(cancelDialogOrder!!.id, reason)
                    cancelDialogOrder = null
                }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Confirm Cancel") }
            },
            dismissButton = { TextButton(onClick = { cancelDialogOrder = null }) { Text("Keep Order") } }
        )
    }

    if (returnDialogOrder != null) {
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { returnDialogOrder = null },
            title = { Text("Request Refund / Return") },
            text = { OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("Reason for return") }) },
            confirmButton = {
                Button(onClick = {
                    if (reason.isNotBlank()) {
                        onRequestReturn(returnDialogOrder!!, reason)
                        returnDialogOrder = null
                    }
                }) { Text("Submit Request") }
            },
            dismissButton = { TextButton(onClick = { returnDialogOrder = null }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WishlistScreen(
    wishlist: List<WishlistItem>,
    products: List<Product>,
    onBack: () -> Unit,
    onOpenProduct: (Product) -> Unit,
    onRemove: (Product) -> Unit
) {
    BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Wishlist (${wishlist.size})") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (wishlist.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No saved favorites yet.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(wishlist, key = { it.id }) { w ->
                    val prod = products.firstOrNull { it.id == w.productId }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth().clickable { if (prod != null) onOpenProduct(prod) }
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            ProductImageBox(w.imageKey, w.productName, Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)))
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(w.productName, style = MaterialTheme.typography.titleSmall)
                                Text(PrintAndExportUtils.formatMoney(w.price), color = NexGenCoral, fontWeight = FontWeight.Bold)
                            }
                            if (prod != null) {
                                IconButton(onClick = { onRemove(prod) }) {
                                    Icon(Icons.Filled.Favorite, contentDescription = "Remove from Wishlist", tint = NexGenCoral)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    notifications: List<AppNotification>,
    onBack: () -> Unit,
    onMarkRead: (String) -> Unit
) {
    BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notifications") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (notifications.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("You are all caught up!")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(notifications, key = { it.id }) { n ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (n.isRead) MaterialTheme.colorScheme.surface
                            else NexGenCobalt.copy(alpha = 0.08f)
                        ),
                        modifier = Modifier.fillMaxWidth().clickable { if (!n.isRead) onMarkRead(n.id) }
                    ) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Filled.Notifications, contentDescription = null, tint = if (n.isRead) MaterialTheme.colorScheme.onSurfaceVariant else NexGenCoral)
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(n.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(n.body, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AccountScreen(
    userProfile: UserProfile?,
    addresses: List<SavedAddress>,
    hasBusinessAccess: Boolean,
    isSuperAdmin: Boolean,
    isDarkTheme: Boolean,
    onToggleTheme: (Boolean) -> Unit,
    onUpdateProfile: (String, String?, String?) -> Unit,
    onSaveAddress: (SavedAddress) -> Unit,
    onDeleteAddress: (String) -> Unit,
    onOpenBusinessConsole: () -> Unit,
    onCreateUniversalShop: (shopName: String, currency: String, receiptHeader: String) -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    var showEditProfile by remember { mutableStateOf(false) }
    var showAddAddress by remember { mutableStateOf(false) }
    var showCreateShopDialog by remember { mutableStateOf(false) }
    var showSupportDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Account & Loyalty Card
        item {
            val pts = userProfile?.loyaltyPoints ?: 0L
            val nextTierTarget = when {
                pts < 500L -> 500L to "Silver"
                pts < 2000L -> 2000L to "Gold"
                pts < 5000L -> 5000L to "Platinum"
                else -> 5000L to "Max Tier"
            }
            val tierProgress = (pts.toFloat() / nextTierTarget.first.toFloat()).coerceIn(0f, 1f)
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(userProfile?.displayName ?: "NexGen Shopper", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text(userProfile?.email ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (!userProfile?.phone.isNullOrBlank()) {
                                Text(userProfile?.phone ?: "", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Surface(color = NexGenAmber.copy(alpha = 0.18f), shape = RoundedCornerShape(10.dp)) {
                            Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Star, contentDescription = null, tint = NexGenAmber, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("${userProfile?.loyaltyTier ?: "Bronze"} • $pts pts", style = MaterialTheme.typography.labelMedium, color = NexGenAmber)
                            }
                        }
                    }
                    Surface(color = NexGenCobalt.copy(alpha = 0.08f), shape = RoundedCornerShape(10.dp)) {
                        Column(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Redeemable Loyalty Value", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                Text(PrintAndExportUtils.formatMoney(pts.toDouble(), "KES"), style = MaterialTheme.typography.labelMedium, color = NexGenEmerald, fontWeight = FontWeight.Bold)
                            }
                            LinearProgressIndicator(
                                progress = { tierProgress },
                                color = NexGenAmber,
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                            )
                            Text(
                                if (pts >= 5000L) "Top Platinum Rewards unlocked! Redeem up to 50% off subtotal in Cart."
                                else "${(nextTierTarget.first - pts).coerceAtLeast(0L)} pts to ${nextTierTarget.second} • Redeemable at Cart checkout",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showEditProfile = true }, modifier = Modifier.weight(1f)) { Text("Edit Profile") }
                        OutlinedButton(onClick = { showSupportDialog = true }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.SupportAgent, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Support")
                        }
                    }
                }
            }
        }

        // Business & POS Entry (Role-gated or Universal Shop Onboarding)
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.BusinessCenter, contentDescription = null, tint = NexGenCoral)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (isSuperAdmin) "Super Admin & Universal POS Suite" else "Universal Shop, POS & Receipt Designer",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        "Manage unlimited products, live POS counter, custom shop name & receipt design, staff shifts, inventory, and P&L reports.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8)
                    )
                    if (hasBusinessAccess) {
                        Button(
                            onClick = onOpenBusinessConsole,
                            colors = ButtonDefaults.buttonColors(containerColor = NexGenCoral),
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("open_business_console_button")
                        ) {
                            Icon(Icons.Filled.Storefront, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Open Business Console & POS", fontWeight = FontWeight.Bold)
                        }
                    }
                    OutlinedButton(
                        onClick = { showCreateShopDialog = true },
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("create_universal_shop_button")
                    ) {
                        Text(if (hasBusinessAccess) "+ Create Another Shop / Branch" else "Open Your Own Shop & POS", color = Color.White)
                    }
                }
            }
        }

        // Saved Addresses
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Saved Delivery Addresses", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { showAddAddress = true }) {
                            Icon(Icons.Filled.AddLocationAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add")
                        }
                    }
                    if (addresses.isEmpty()) {
                        Text("No saved addresses yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        addresses.forEach { addr ->
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${addr.label} • ${addr.recipientName}", style = MaterialTheme.typography.titleSmall)
                                    Text("${addr.street}, ${addr.city} (${addr.phone})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { onDeleteAddress(addr.id) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete Address", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }

        // Preferences & Sign Out
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Dark Mode Theme", style = MaterialTheme.typography.titleSmall)
                        Switch(checked = isDarkTheme, onCheckedChange = onToggleTheme)
                    }
                    OutlinedButton(
                        onClick = onSignOut,
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("sign_out_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Sign Out")
                    }
                }
            }
        }
    }

    if (showEditProfile) {
        var name by remember { mutableStateOf(userProfile?.displayName ?: "") }
        var phone by remember { mutableStateOf(userProfile?.phone ?: "") }
        var defAddr by remember { mutableStateOf(userProfile?.defaultAddress ?: "") }
        AlertDialog(
            onDismissRequest = { showEditProfile = false },
            title = { Text("Edit Profile") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Display Name") })
                    OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone Number") })
                    OutlinedTextField(value = defAddr, onValueChange = { defAddr = it }, label = { Text("Default Address") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (name.isNotBlank()) {
                        onUpdateProfile(name, phone, defAddr)
                        showEditProfile = false
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showEditProfile = false }) { Text("Cancel") } }
        )
    }

    if (showAddAddress) {
        var label by remember { mutableStateOf("Home") }
        var recipient by remember { mutableStateOf(userProfile?.displayName ?: "") }
        var phone by remember { mutableStateOf(userProfile?.phone ?: "") }
        var street by remember { mutableStateOf("") }
        var city by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddAddress = false },
            title = { Text("New Delivery Address") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("Label (Home / Office)") })
                    OutlinedTextField(value = recipient, onValueChange = { recipient = it }, label = { Text("Recipient Name") })
                    OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone Number") })
                    OutlinedTextField(value = street, onValueChange = { street = it }, label = { Text("Street / Building") })
                    OutlinedTextField(value = city, onValueChange = { city = it }, label = { Text("City") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (street.length >= 2 && city.length >= 2 && phone.length >= 3) {
                        onSaveAddress(SavedAddress(label = label, recipientName = recipient, phone = phone, street = street, city = city))
                        showAddAddress = false
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showAddAddress = false }) { Text("Cancel") } }
        )
    }

    if (showCreateShopDialog) {
        var shopName by remember { mutableStateOf("") }
        var currency by remember { mutableStateOf("KES") }
        var receiptHeader by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateShopDialog = false },
            title = { Text("Create Your Universal Shop") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Launch your own shop with customizable receipt design, POS terminal, inventory & orders:", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = shopName, onValueChange = { shopName = it; if (receiptHeader.isBlank()) receiptHeader = it.uppercase() }, label = { Text("Shop Name") })
                    OutlinedTextField(value = currency, onValueChange = { currency = it.uppercase() }, label = { Text("Currency Code (KES, USD, EUR)") })
                    OutlinedTextField(value = receiptHeader, onValueChange = { receiptHeader = it }, label = { Text("Receipt Header Title") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (shopName.length >= 2) {
                        onCreateUniversalShop(shopName, currency.ifBlank { "KES" }, receiptHeader.ifBlank { shopName.uppercase() })
                        showCreateShopDialog = false
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = NexGenCoral)) { Text("Launch Shop") }
            },
            dismissButton = { TextButton(onClick = { showCreateShopDialog = false }) { Text("Cancel") } }
        )
    }

    if (showSupportDialog) {
        AlertDialog(
            onDismissRequest = { showSupportDialog = false },
            title = { Text("NexGen Customer Support") },
            text = { Text("Need help with an order, refund, or shop setup? Share an inquiry ticket directly with our marketplace desk.") },
            confirmButton = {
                Button(onClick = {
                    PrintAndExportUtils.shareText(
                        context,
                        "NexGen Market Support Inquiry",
                        "Customer: ${userProfile?.displayName} (${userProfile?.email})\nInquiry: "
                    )
                    showSupportDialog = false
                }) { Text("Contact Support") }
            },
            dismissButton = { TextButton(onClick = { showSupportDialog = false }) { Text("Close") } }
        )
    }
}
