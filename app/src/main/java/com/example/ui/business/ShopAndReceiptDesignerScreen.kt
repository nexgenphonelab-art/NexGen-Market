package com.example.ui.business

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.GatewayConfig
import com.example.data.model.Shop
import com.example.ui.components.BarcodeStrip
import com.example.ui.theme.NexGenCobalt
import com.example.ui.theme.NexGenCoral
import com.example.ui.theme.NexGenEmerald
import com.example.util.PrintAndExportUtils
import com.example.util.ReceiptLine

@Composable
fun ShopAndReceiptDesignerScreen(
    shop: Shop,
    gatewayConfig: GatewayConfig?,
    onSaveShop: (Shop) -> Unit,
    onSaveGatewayConfig: (String, GatewayConfig) -> Unit
) {
    val context = LocalContext.current

    var shopName by remember(shop.id) { mutableStateOf(shop.shopName) }
    var tagline by remember(shop.id) { mutableStateOf(shop.tagline) }
    var categoryFocus by remember(shop.id) { mutableStateOf(shop.categoryFocus) }
    var locationLabel by remember(shop.id) { mutableStateOf(shop.locationLabel) }
    var currencyCode by remember(shop.id) { mutableStateOf(shop.currencyCode) }
    var taxRateStr by remember(shop.id) { mutableStateOf(shop.taxRatePercent.toString()) }
    var shippingFeeStr by remember(shop.id) { mutableStateOf(shop.shippingFlatFee.toString()) }
    var freeThreshStr by remember(shop.id) { mutableStateOf(shop.freeShippingThreshold.toString()) }
    var paybillNumber by remember(shop.id) { mutableStateOf(shop.paybillNumber) }
    var tillNumber by remember(shop.id) { mutableStateOf(shop.tillNumber) }
    var accountReference by remember(shop.id) { mutableStateOf(shop.accountReference) }

    // Universal Receipt Designer fields
    var headerTitle by remember(shop.id) { mutableStateOf(shop.receiptHeaderTitle) }
    var subtitle by remember(shop.id) { mutableStateOf(shop.receiptSubtitle) }
    var addressLine by remember(shop.id) { mutableStateOf(shop.receiptAddressLine) }
    var phoneLine by remember(shop.id) { mutableStateOf(shop.receiptPhoneLine) }
    var taxPin by remember(shop.id) { mutableStateOf(shop.receiptTaxPin) }
    var footerMessage by remember(shop.id) { mutableStateOf(shop.receiptFooterMessage) }
    var showLogo by remember(shop.id) { mutableStateOf(shop.receiptShowLogo) }
    var showBarcode by remember(shop.id) { mutableStateOf(shop.receiptShowBarcode) }
    var showCashier by remember(shop.id) { mutableStateOf(shop.receiptShowCashier) }
    var showTaxBreakdown by remember(shop.id) { mutableStateOf(shop.receiptShowTaxBreakdown) }
    var paperWidthMm by remember(shop.id) { mutableStateOf(shop.receiptPaperWidthMm) }
    var accentHex by remember(shop.id) { mutableStateOf(shop.receiptAccentColorHex) }

    // Admin-only Daraja M-Pesa & Gateway Config
    var stkEnabled by remember(shop.id, gatewayConfig) { mutableStateOf(shop.mpesaStkEnabled || gatewayConfig?.stkEnabled == true) }
    var darajaEnv by remember(gatewayConfig) { mutableStateOf(gatewayConfig?.darajaEnvironment ?: "sandbox") }
    var darajaShortcode by remember(gatewayConfig) { mutableStateOf(gatewayConfig?.darajaShortcode ?: shop.paybillNumber.ifBlank { "174379" }) }
    var darajaKey by remember(gatewayConfig) { mutableStateOf(gatewayConfig?.darajaConsumerKey ?: "") }
    var darajaSecret by remember(gatewayConfig) { mutableStateOf(gatewayConfig?.darajaConsumerSecret ?: "") }
    var darajaPasskey by remember(gatewayConfig) { mutableStateOf(gatewayConfig?.darajaPasskey ?: "") }
    var darajaCallback by remember(gatewayConfig) { mutableStateOf(gatewayConfig?.darajaCallbackUrl ?: "https://api.example.com/mpesa/callback") }
    var cardMerchant by remember(gatewayConfig) { mutableStateOf(gatewayConfig?.cardGatewayMerchantId ?: "") }
    var whatsappNum by remember(gatewayConfig) { mutableStateOf(gatewayConfig?.whatsappBusinessNumber ?: "") }
    var printerName by remember(gatewayConfig) { mutableStateOf(gatewayConfig?.thermalPrinterName ?: "Generic ESC/POS 80mm BT") }
    var showDarajaSecrets by remember { mutableStateOf(false) }

    val draftShop = shop.copy(
        shopName = shopName.ifBlank { "My Store" },
        tagline = tagline,
        categoryFocus = categoryFocus,
        locationLabel = locationLabel,
        currencyCode = currencyCode.ifBlank { "KES" },
        taxRatePercent = taxRateStr.toDoubleOrNull() ?: 16.0,
        shippingFlatFee = shippingFeeStr.toDoubleOrNull() ?: 0.0,
        freeShippingThreshold = freeThreshStr.toDoubleOrNull() ?: 0.0,
        paybillNumber = paybillNumber,
        tillNumber = tillNumber,
        accountReference = accountReference,
        receiptHeaderTitle = headerTitle.ifBlank { shopName.uppercase() },
        receiptSubtitle = subtitle,
        receiptAddressLine = addressLine,
        receiptPhoneLine = phoneLine,
        receiptTaxPin = taxPin,
        receiptFooterMessage = footerMessage,
        receiptShowLogo = showLogo,
        receiptShowBarcode = showBarcode,
        receiptShowCashier = showCashier,
        receiptShowTaxBreakdown = showTaxBreakdown,
        receiptPaperWidthMm = paperWidthMm,
        receiptAccentColorHex = accentHex,
        mpesaStkEnabled = stkEnabled
    )

    val accentColor = remember(accentHex) {
        runCatching { Color(android.graphics.Color.parseColor(accentHex)) }.getOrElse { NexGenCobalt }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // LIVE THERMAL RECEIPT PREVIEW
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Receipt, contentDescription = null, tint = accentColor)
                            Spacer(Modifier.width(8.dp))
                            Text("Live Universal Receipt Preview (${paperWidthMm}mm)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(onClick = {
                            PrintAndExportUtils.printReceipt(
                                context = context,
                                jobName = "Receipt_Preview",
                                shop = draftShop,
                                receiptNumber = "RCP-SAMPLE-001",
                                dateStr = PrintAndExportUtils.formatDate(),
                                cashierName = "Alex (Cashier)",
                                customerName = "Walk-in Customer",
                                lines = listOf(ReceiptLine("Sample Product Pro", "Black • 256GB", 1L, 12500.0, 12500.0)),
                                subtotal = 12500.0,
                                discount = 0.0,
                                tax = 12500.0 * (draftShop.taxRatePercent / 100.0),
                                total = 12500.0 * (1.0 + draftShop.taxRatePercent / 100.0),
                                paymentInfo = "M-Pesa Paybill ${draftShop.paybillNumber}"
                            )
                        }) {
                            Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Test Print")
                        }
                    }

                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(12.dp),
                        shadowElevation = 3.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = if (paperWidthMm == 58L) 32.dp else 4.dp)
                            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(5.dp)
                                    .background(accentColor, RoundedCornerShape(3.dp))
                            )
                            Spacer(Modifier.height(4.dp))
                            if (showLogo) {
                                Surface(
                                    color = Color(0xFF0F172A),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .size(56.dp)
                                        .border(1.5.dp, accentColor, RoundedCornerShape(10.dp))
                                ) {
                                    AsyncImage(
                                        model = R.drawable.img_nexgen_logo,
                                        contentDescription = "NexGen Market Receipt Logo",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(10.dp))
                                    )
                                }
                                Text(
                                    draftShop.shopName.uppercase(),
                                    color = accentColor,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                            Text(
                                draftShop.receiptHeaderTitle,
                                color = Color(0xFF0F172A),
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            if (subtitle.isNotBlank()) Text(subtitle, color = Color(0xFF334155), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                            if (addressLine.isNotBlank()) Text(addressLine, color = Color(0xFF475569), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                            if (phoneLine.isNotBlank()) Text("Tel: $phoneLine", color = Color(0xFF475569), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall)
                            if (taxPin.isNotBlank()) Text(taxPin, color = Color(0xFF475569), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall)

                            Surface(
                                color = Color(0xFFF8FAFC),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                            ) {
                                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Receipt: #RCP-001", color = Color(0xFF0F172A), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                                        Text("Today", color = Color(0xFF475569), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall)
                                    }
                                    if (showCashier) {
                                        Text("Cashier: Staff Demo • Walk-in", color = Color(0xFF334155), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }

                            HorizontalDivider(color = accentColor, thickness = 1.5.dp, modifier = Modifier.padding(vertical = 2.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Sample Item Pro x1", color = Color.Black, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                                Text(PrintAndExportUtils.formatMoney(1000.0, draftShop.currencyCode), color = Color.Black, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                            }
                            if (showTaxBreakdown) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Tax (${draftShop.taxRatePercent}%)", color = Color(0xFF475569), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall)
                                    Text(PrintAndExportUtils.formatMoney(1000.0 * draftShop.taxRatePercent / 100.0, draftShop.currencyCode), color = Color(0xFF475569), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            Surface(
                                color = accentColor.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("TOTAL", color = Color(0xFF0F172A), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                    Text(
                                        PrintAndExportUtils.formatMoney(1000.0 * (1 + draftShop.taxRatePercent / 100.0), draftShop.currencyCode),
                                        color = accentColor,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            if (footerMessage.isNotBlank()) {
                                Spacer(Modifier.height(2.dp))
                                Text(footerMessage, color = Color(0xFF475569), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                            }
                            if (showBarcode) {
                                BarcodeStrip(code = "NXG-RCP-001", modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            }
        }

        // UNIVERSAL SHOP IDENTITY & TAX SETTINGS
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Shop Identity & Commerce Rules", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    OutlinedTextField(value = shopName, onValueChange = { shopName = it }, label = { Text("Shop / Brand Name") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("shop_name_input"))
                    OutlinedTextField(value = tagline, onValueChange = { tagline = it }, label = { Text("Tagline") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = categoryFocus, onValueChange = { categoryFocus = it }, label = { Text("Primary Category") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = currencyCode, onValueChange = { currencyCode = it.uppercase() }, label = { Text("Currency (KES/USD)") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = taxRateStr, onValueChange = { taxRateStr = it }, label = { Text("Tax Rate %") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = shippingFeeStr, onValueChange = { shippingFeeStr = it }, label = { Text("Flat Shipping") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = paybillNumber, onValueChange = { paybillNumber = it }, label = { Text("Paybill Number") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = tillNumber, onValueChange = { tillNumber = it }, label = { Text("Till Number") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    OutlinedTextField(value = accountReference, onValueChange = { accountReference = it }, label = { Text("Account Reference Prefix") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = locationLabel, onValueChange = { locationLabel = it }, label = { Text("Store Location") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        // RECEIPT DESIGNER FIELDS
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Custom Receipt Designer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    OutlinedTextField(value = headerTitle, onValueChange = { headerTitle = it }, label = { Text("Receipt Header Title") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("receipt_header_input"))
                    OutlinedTextField(value = subtitle, onValueChange = { subtitle = it }, label = { Text("Receipt Subtitle") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = addressLine, onValueChange = { addressLine = it }, label = { Text("Receipt Address Line") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = phoneLine, onValueChange = { phoneLine = it }, label = { Text("Receipt Phone") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = taxPin, onValueChange = { taxPin = it }, label = { Text("Tax PIN / Reg #") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    OutlinedTextField(value = footerMessage, onValueChange = { footerMessage = it }, label = { Text("Receipt Footer / Warranty Terms") }, modifier = Modifier.fillMaxWidth())

                    Text("Paper Width", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilterChip(selected = paperWidthMm == 58L, onClick = { paperWidthMm = 58L }, label = { Text("58mm Thermal") })
                        FilterChip(selected = paperWidthMm == 80L, onClick = { paperWidthMm = 80L }, label = { Text("80mm Thermal") })
                    }

                    Text("Receipt Accent Color", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf("#0077FF", "#FF472E", "#00A870", "#7C3AED", "#0F172A").forEach { hex ->
                            val c = Color(android.graphics.Color.parseColor(hex))
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(c, CircleShape)
                                    .border(if (accentHex == hex) 3.dp else 1.dp, Color.White, CircleShape)
                                    .clickable { accentHex = hex }
                            )
                        }
                    }

                    ToggleSettingRow("Show Shop Brand Header", showLogo) { showLogo = it }
                    ToggleSettingRow("Show Receipt Barcode", showBarcode) { showBarcode = it }
                    ToggleSettingRow("Show Cashier Name", showCashier) { showCashier = it }
                    ToggleSettingRow("Show Tax Breakdown", showTaxBreakdown) { showTaxBreakdown = it }

                    Button(
                        onClick = { onSaveShop(draftShop) },
                        colors = ButtonDefaults.buttonColors(containerColor = NexGenCoral),
                        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("save_shop_receipt_button")
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Save Shop & Receipt Template", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // ADMIN-ONLY PAYMENT GATEWAY (SAFARICOM DARAJA M-PESA) & THERMAL PRINTER CONFIG
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Settings, contentDescription = null, tint = NexGenCobalt)
                            Spacer(Modifier.width(8.dp))
                            Text("Admin M-Pesa Daraja Integration & Printer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = { showDarajaSecrets = !showDarajaSecrets }) {
                            Icon(
                                if (showDarajaSecrets) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (showDarajaSecrets) "Hide Daraja Credentials" else "Show Daraja Credentials"
                            )
                        }
                    }
                    Text(
                        "Stored securely for Admin/Owner only. Customers never see Consumer Key, Secret, or Passkey.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Enable M-Pesa STK Push at Checkout", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Switch(
                            checked = stkEnabled,
                            onCheckedChange = { stkEnabled = it },
                            modifier = Modifier.testTag("admin_mpesa_stk_switch")
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = darajaEnv == "sandbox", onClick = { darajaEnv = "sandbox" }, label = { Text("Daraja Sandbox") })
                        FilterChip(selected = darajaEnv == "production", onClick = { darajaEnv = "production" }, label = { Text("Daraja Production") })
                    }
                    OutlinedTextField(value = darajaShortcode, onValueChange = { darajaShortcode = it }, label = { Text("Daraja Business Shortcode / Paybill") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        value = darajaKey,
                        onValueChange = { darajaKey = it.trim() },
                        label = { Text("Daraja Consumer Key") },
                        singleLine = true,
                        visualTransformation = if (showDarajaSecrets) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = darajaSecret,
                        onValueChange = { darajaSecret = it.trim() },
                        label = { Text("Daraja Consumer Secret") },
                        singleLine = true,
                        visualTransformation = if (showDarajaSecrets) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = darajaPasskey,
                        onValueChange = { darajaPasskey = it.trim() },
                        label = { Text("Daraja Lipa Na M-Pesa Passkey") },
                        singleLine = true,
                        visualTransformation = if (showDarajaSecrets) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(value = darajaCallback, onValueChange = { darajaCallback = it }, label = { Text("Daraja Callback URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = cardMerchant, onValueChange = { cardMerchant = it }, label = { Text("Card Gateway Merchant ID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = whatsappNum, onValueChange = { whatsappNum = it }, label = { Text("WhatsApp Business Number") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = printerName, onValueChange = { printerName = it }, label = { Text("Default Thermal Printer Profile") }, singleLine = true, modifier = Modifier.fillMaxWidth())

                    Button(
                        onClick = {
                            onSaveShop(draftShop.copy(mpesaStkEnabled = stkEnabled))
                            onSaveGatewayConfig(
                                shop.id,
                                GatewayConfig(
                                    shopOwnerId = shop.ownerId,
                                    darajaEnvironment = darajaEnv,
                                    darajaShortcode = darajaShortcode,
                                    darajaCallbackUrl = darajaCallback,
                                    darajaConsumerKey = darajaKey,
                                    darajaConsumerSecret = darajaSecret,
                                    darajaPasskey = darajaPasskey,
                                    stkEnabled = stkEnabled,
                                    cardGatewayMerchantId = cardMerchant,
                                    whatsappBusinessNumber = whatsappNum,
                                    thermalPrinterName = printerName
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NexGenEmerald),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("save_admin_gateway_button")
                    ) {
                        Text("Save Admin M-Pesa & Printer Settings", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleSettingRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
