package com.example.ui.marketplace

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingCartCheckout
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.CartItem
import com.example.data.model.Coupon
import com.example.data.model.Order
import com.example.data.model.SavedAddress
import com.example.data.model.Shop
import com.example.data.model.UserProfile
import com.example.ui.components.ProductImageBox
import com.example.ui.theme.NexGenAmber
import com.example.ui.theme.NexGenCobalt
import com.example.ui.theme.NexGenCoral
import com.example.ui.theme.NexGenEmerald
import com.example.util.PrintAndExportUtils
import kotlin.math.floor

@Composable
fun CartScreen(
    cartItems: List<CartItem>,
    coupons: List<Coupon>,
    shops: List<Shop>,
    addresses: List<SavedAddress>,
    userProfile: UserProfile?,
    onUpdateQty: (String, Long) -> Unit,
    onRemoveItem: (String) -> Unit,
    onSaveAddress: (SavedAddress) -> Unit,
    onPlaceOrder: (
        items: List<CartItem>,
        customerName: String,
        customerPhone: String,
        customerEmail: String,
        deliveryAddress: String,
        deliveryOption: String,
        paymentMethod: String,
        paymentReference: String?,
        coupon: Coupon?,
        shippingFee: Double,
        taxRatePercent: Double,
        pointsToRedeem: Long,
        onSuccess: (List<Order>) -> Unit
    ) -> Unit,
    onOrderPlacedNavigate: () -> Unit
) {
    var couponCodeInput by remember { mutableStateOf("") }
    var appliedCoupon by remember { mutableStateOf<Coupon?>(null) }
    var deliveryOption by remember { mutableStateOf("Standard Delivery") }
    var redeemLoyaltyEnabled by remember { mutableStateOf(false) }
    var chosenPoints by remember { mutableLongStateOf(0L) }
    var showCheckoutDialog by remember { mutableStateOf(false) }

    val primaryShop = remember(cartItems, shops) {
        val firstShopId = cartItems.firstOrNull()?.shopId
        shops.firstOrNull { it.id == firstShopId } ?: shops.firstOrNull() ?: Shop()
    }

    val subtotal = remember(cartItems) { cartItems.sumOf { it.unitPrice * it.quantity } }
    val discount = remember(subtotal, appliedCoupon) {
        val c = appliedCoupon ?: return@remember 0.0
        if (subtotal < c.minOrderAmount) 0.0
        else if (c.discountType == "percent") subtotal * (c.discountValue / 100.0)
        else c.discountValue.coerceAtMost(subtotal)
    }
    val availablePoints = userProfile?.loyaltyPoints ?: 0L
    val maxRedeemablePoints = remember(availablePoints, subtotal, discount) {
        val halfSubtotalCap = floor((subtotal * 0.5).coerceAtLeast(0.0)).toLong()
        val afterCouponCap = floor((subtotal - discount).coerceAtLeast(0.0)).toLong()
        minOf(availablePoints, halfSubtotalCap, afterCouponCap).coerceAtLeast(0L)
    }
    val activePointsToRedeem = if (redeemLoyaltyEnabled && maxRedeemablePoints > 0L) {
        (if (chosenPoints <= 0L) maxRedeemablePoints else chosenPoints).coerceIn(0L, maxRedeemablePoints)
    } else 0L
    val loyaltyDiscount = activePointsToRedeem.toDouble()

    val shopCount = remember(cartItems) { cartItems.map { it.shopId }.distinct().size.coerceAtLeast(1) }
    val baseShipping = when {
        cartItems.isEmpty() -> 0.0
        deliveryOption.contains("Pickup", true) -> 0.0
        deliveryOption.contains("Express", true) -> (primaryShop.shippingFlatFee + 250.0) * shopCount
        primaryShop.freeShippingThreshold > 0 && subtotal >= primaryShop.freeShippingThreshold -> 0.0
        else -> primaryShop.shippingFlatFee * shopCount
    }
    val taxable = (subtotal - discount - loyaltyDiscount).coerceAtLeast(0.0)
    val taxAmount = taxable * (primaryShop.taxRatePercent / 100.0)
    val grandTotal = taxable + taxAmount + baseShipping

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (cartItems.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Filled.ShoppingCartCheckout, contentDescription = null, tint = NexGenCobalt, modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(12.dp))
                Text("Your Cart is Empty", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Explore products, flash deals, and featured shops to add items to your cart.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(cartItems, key = { it.id }) { item ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            ProductImageBox(
                                imageKey = item.imageKey,
                                contentDescription = item.productName,
                                modifier = Modifier.size(72.dp).clip(RoundedCornerShape(10.dp))
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.shopName, style = MaterialTheme.typography.labelSmall, color = NexGenCobalt)
                                Text(item.productName, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                                Text(item.variationLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    PrintAndExportUtils.formatMoney(item.unitPrice * item.quantity, primaryShop.currencyCode),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = NexGenCoral,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                IconButton(onClick = { onRemoveItem(item.id) }) {
                                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Remove Item", tint = MaterialTheme.colorScheme.error)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { onUpdateQty(item.id, item.quantity - 1L) }, modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Filled.Remove, contentDescription = "Decrease Quantity", modifier = Modifier.size(16.dp))
                                    }
                                    Text("${item.quantity}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 6.dp))
                                    IconButton(onClick = { onUpdateQty(item.id, item.quantity + 1L) }, modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Filled.Add, contentDescription = "Increase Quantity", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                // Coupon & Delivery Options
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Promo & Coupon Code", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = couponCodeInput,
                                    onValueChange = { couponCodeInput = it.uppercase() },
                                    placeholder = { Text("Enter coupon (e.g. NEXGEN10)") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                Button(
                                    onClick = {
                                        val found = coupons.firstOrNull { it.code.equals(couponCodeInput.trim(), ignoreCase = true) && it.isActive }
                                        appliedCoupon = found
                                    }
                                ) { Text("Apply") }
                            }
                            if (coupons.isNotEmpty()) {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(coupons) { cpn ->
                                        FilterChip(
                                            selected = appliedCoupon?.id == cpn.id,
                                            onClick = {
                                                couponCodeInput = cpn.code
                                                appliedCoupon = cpn
                                            },
                                            label = {
                                                val lbl = if (cpn.discountType == "percent") "${cpn.code} (${cpn.discountValue.toInt()}% OFF)"
                                                else "${cpn.code} (-${cpn.discountValue.toInt()})"
                                                Text(lbl)
                                            }
                                        )
                                    }
                                }
                            }

                            HorizontalDivider()
                            Text("Delivery Option", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            listOf("Standard Delivery", "Express Courier", "Shop Pickup").forEach { opt ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable { deliveryOption = opt },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(selected = deliveryOption == opt, onClick = { deliveryOption = opt })
                                    Text(opt, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }

                // Loyalty Points Redemption Card (for registered customers with points)
                if (availablePoints > 0L) {
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Icon(Icons.Filled.Star, contentDescription = null, tint = NexGenAmber)
                                        Spacer(Modifier.width(8.dp))
                                        Column {
                                            Text("Redeem Loyalty Points", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                            Text(
                                                "Available: $availablePoints pts • Max this order: $maxRedeemablePoints pts (1 pt = 1 ${primaryShop.currencyCode})",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Switch(
                                        checked = redeemLoyaltyEnabled,
                                        onCheckedChange = { checked ->
                                            redeemLoyaltyEnabled = checked
                                            if (checked && chosenPoints <= 0L) chosenPoints = maxRedeemablePoints
                                        },
                                        enabled = maxRedeemablePoints > 0L,
                                        modifier = Modifier.testTag("loyalty_redeem_switch")
                                    )
                                }
                                if (redeemLoyaltyEnabled && maxRedeemablePoints > 0L) {
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        val presets = listOf(100L, 250L, 500L, maxRedeemablePoints)
                                            .filter { it in 1L..maxRedeemablePoints }
                                            .distinct()
                                        items(presets) { pts ->
                                            FilterChip(
                                                selected = activePointsToRedeem == pts,
                                                onClick = { chosenPoints = pts },
                                                label = {
                                                    Text(if (pts == maxRedeemablePoints) "Max ($pts pts)" else "$pts pts")
                                                }
                                            )
                                        }
                                    }
                                    Text(
                                        "Applying $activePointsToRedeem pts (-${PrintAndExportUtils.formatMoney(loyaltyDiscount, primaryShop.currencyCode)})",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = NexGenEmerald,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }

                // Order Summary
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Order Summary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            SummaryRow("Subtotal", PrintAndExportUtils.formatMoney(subtotal, primaryShop.currencyCode))
                            if (discount > 0.0) {
                                SummaryRow("Coupon Discount", "-${PrintAndExportUtils.formatMoney(discount, primaryShop.currencyCode)}", NexGenEmerald)
                            }
                            if (loyaltyDiscount > 0.0) {
                                SummaryRow("Loyalty Points ($activePointsToRedeem pts)", "-${PrintAndExportUtils.formatMoney(loyaltyDiscount, primaryShop.currencyCode)}", NexGenEmerald)
                            }
                            SummaryRow("Shipping ($deliveryOption)", PrintAndExportUtils.formatMoney(baseShipping, primaryShop.currencyCode))
                            SummaryRow("Tax (${primaryShop.taxRatePercent}%)", PrintAndExportUtils.formatMoney(taxAmount, primaryShop.currencyCode))
                            HorizontalDivider()
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Grand Total", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    PrintAndExportUtils.formatMoney(grandTotal, primaryShop.currencyCode),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = NexGenCoral,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            Surface(tonalElevation = 6.dp, shadowElevation = 8.dp) {
                Button(
                    onClick = { showCheckoutDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = NexGenCoral),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(52.dp)
                        .testTag("proceed_to_checkout_button")
                ) {
                    Icon(Icons.Filled.ShoppingCartCheckout, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Checkout • ${PrintAndExportUtils.formatMoney(grandTotal, primaryShop.currencyCode)}", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showCheckoutDialog) {
        CheckoutModalDialog(
            shop = primaryShop,
            totalAmount = grandTotal,
            deliveryOption = deliveryOption,
            addresses = addresses,
            userProfile = userProfile,
            onDismiss = { showCheckoutDialog = false },
            onSaveAddress = onSaveAddress,
            onConfirmOrder = { name, phone, email, addr, payMethod, payRef ->
                val perShopShipping = if (deliveryOption.contains("Pickup", true)) 0.0 else (baseShipping / shopCount)
                onPlaceOrder(
                    cartItems, name, phone, email, addr, deliveryOption, payMethod, payRef,
                    appliedCoupon, perShopShipping, primaryShop.taxRatePercent, activePointsToRedeem
                ) {
                    redeemLoyaltyEnabled = false
                    chosenPoints = 0L
                    showCheckoutDialog = false
                    onOrderPlacedNavigate()
                }
            }
        )
    }
}

@Composable
private fun CheckoutModalDialog(
    shop: Shop,
    totalAmount: Double,
    deliveryOption: String,
    addresses: List<SavedAddress>,
    userProfile: UserProfile?,
    onDismiss: () -> Unit,
    onSaveAddress: (SavedAddress) -> Unit,
    onConfirmOrder: (String, String, String, String, String, String?) -> Unit
) {
    var customerName by remember { mutableStateOf(userProfile?.displayName ?: "") }
    var customerPhone by remember { mutableStateOf(userProfile?.phone ?: addresses.firstOrNull()?.phone ?: "") }
    var customerEmail by remember { mutableStateOf(userProfile?.email ?: "") }
    var selectedAddr by remember { mutableStateOf(addresses.firstOrNull()?.let { "${it.street}, ${it.city}" } ?: userProfile?.defaultAddress ?: "") }
    var showNewAddrForm by remember { mutableStateOf(addresses.isEmpty()) }
    var newStreet by remember { mutableStateOf("") }
    var newCity by remember { mutableStateOf("") }

    var paymentMethod by remember { mutableStateOf("M-Pesa STK Push") }
    var paymentRef by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Secure Checkout") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Total Due: ${PrintAndExportUtils.formatMoney(totalAmount, shop.currencyCode)} ($deliveryOption)",
                    style = MaterialTheme.typography.titleMedium,
                    color = NexGenCoral,
                    fontWeight = FontWeight.Bold
                )
                OutlinedTextField(value = customerName, onValueChange = { customerName = it }, label = { Text("Recipient Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = customerPhone, onValueChange = { customerPhone = it }, label = { Text("Phone / M-Pesa Number (2547...)") }, singleLine = true, modifier = Modifier.fillMaxWidth())

                if (addresses.isNotEmpty()) {
                    Text("Saved Addresses", style = MaterialTheme.typography.labelLarge)
                    addresses.forEach { a ->
                        val formatted = "${a.street}, ${a.city}"
                        FilterChip(
                            selected = selectedAddr == formatted,
                            onClick = {
                                selectedAddr = formatted
                                if (a.phone.isNotBlank()) customerPhone = a.phone
                            },
                            label = { Text("${a.label}: $formatted") }
                        )
                    }
                }
                TextButton(onClick = { showNewAddrForm = !showNewAddrForm }) {
                    Text(if (showNewAddrForm) "Hide Address Form" else "+ Add / Edit Delivery Address")
                }
                if (showNewAddrForm) {
                    OutlinedTextField(value = newStreet, onValueChange = { newStreet = it; selectedAddr = "$it, $newCity" }, label = { Text("Street / Building") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = newCity, onValueChange = { newCity = it; selectedAddr = "$newStreet, $it" }, label = { Text("City / Town") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedButton(
                        onClick = {
                            if (newStreet.length >= 2 && newCity.length >= 2) {
                                onSaveAddress(
                                    SavedAddress(
                                        label = "Delivery",
                                        recipientName = customerName.ifBlank { "Shopper" },
                                        phone = customerPhone.ifBlank { "0700000000" },
                                        street = newStreet,
                                        city = newCity
                                    )
                                )
                                selectedAddr = "$newStreet, $newCity"
                                showNewAddrForm = false
                            }
                        }
                    ) { Text("Save Address to Account") }
                }

                HorizontalDivider()
                Text("Payment Method", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                val methods = listOf("M-Pesa STK Push", "M-Pesa Paybill", "Card Payment", "Cash on Delivery")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(methods) { m ->
                        FilterChip(selected = paymentMethod == m, onClick = { paymentMethod = m }, label = { Text(m) })
                    }
                }

                when (paymentMethod) {
                    "M-Pesa STK Push" -> {
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(10.dp)) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.PhoneAndroid, contentDescription = null, tint = NexGenEmerald)
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        if (shop.mpesaStkEnabled) "M-Pesa STK Express (Admin Configured)" else "M-Pesa Express / Paybill Checkout",
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                                Text(
                                    "Paybill: ${shop.paybillNumber} • Account Ref: ${shop.accountReference}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    if (shop.mpesaStkEnabled) {
                                        "Place order with your M-Pesa phone number above; the store's Admin Daraja gateway confirms and dispatches payment verification."
                                    } else {
                                        "Complete M-Pesa payment using Paybill ${shop.paybillNumber} and enter your confirmation code below."
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        OutlinedTextField(
                            value = paymentRef,
                            onValueChange = { paymentRef = it.uppercase() },
                            label = { Text("M-Pesa Confirmation Code (if already paid)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    "M-Pesa Paybill" -> {
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(10.dp)) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("Paybill: ${shop.paybillNumber} • Till: ${shop.tillNumber}", fontWeight = FontWeight.Bold)
                                Text("Account Ref: ${shop.accountReference}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        OutlinedTextField(
                            value = paymentRef,
                            onValueChange = { paymentRef = it.uppercase() },
                            label = { Text("M-Pesa Confirmation Code (e.g. QKH9...)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    "Card Payment" -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CreditCard, contentDescription = null, tint = NexGenCobalt)
                            Spacer(Modifier.width(6.dp))
                            Text("Approved Gateway Reference Verification", style = MaterialTheme.typography.bodySmall)
                        }
                        OutlinedTextField(
                            value = paymentRef,
                            onValueChange = { paymentRef = it },
                            label = { Text("Card Auth / Gateway Reference") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    else -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.LocalShipping, contentDescription = null, tint = NexGenCoral)
                            Spacer(Modifier.width(6.dp))
                            Text("Pay cash/M-Pesa on delivery. Order remains Unpaid until verified.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                Surface(color = NexGenCobalt.copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp)) {
                    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = NexGenCobalt, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Payment security: orders are never marked Paid until verified by Admin/Daraja confirmation.",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirmOrder(
                        customerName.ifBlank { "Customer" },
                        customerPhone.ifBlank { "0700000000" },
                        customerEmail,
                        selectedAddr.ifBlank { "Shop Pickup / Delivery" },
                        paymentMethod,
                        paymentRef.ifBlank { null }
                    )
                },
                enabled = customerName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = NexGenCoral),
                modifier = Modifier.testTag("place_order_confirm_button")
            ) {
                Text("Place Order")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun SummaryRow(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColor, fontWeight = FontWeight.Medium)
    }
}
