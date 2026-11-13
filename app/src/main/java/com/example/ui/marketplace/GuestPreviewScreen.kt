package com.example.ui.marketplace

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.Product
import com.example.ui.components.DiscountBadge
import com.example.ui.components.GlowingNexGenLogo
import com.example.ui.components.PriceDisplay
import com.example.ui.components.ProductImageBox
import com.example.ui.components.StarRatingRow
import com.example.ui.theme.NexGenCobalt
import com.example.ui.theme.NexGenCoral

/**
 * Purely in-memory guest showcase preview of NexGen Market (zero Firestore reads or writes).
 * Any interactive action requiring persistence prompts Google Sign-In.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuestPreviewScreen(
    onBackToSignIn: () -> Unit
) {
    BackHandler(onBack = onBackToSignIn)

    val previewCatalog = remember {
        listOf(
            Product(
                id = "preview_1",
                shopName = "NexGen Flagship Store",
                name = "Apex X1 Titanium 5G Smartphone (256GB)",
                category = "Phones & Tablets",
                sellingPrice = 42500.0,
                originalPrice = 49900.0,
                discountPercent = 15L,
                stockQuantity = 18L,
                imageKeys = listOf("smartphone"),
                rating = 4.9,
                soldCount = 142L,
                isFlashSale = true
            ),
            Product(
                id = "preview_2",
                shopName = "NexGen Audio Hub",
                name = "Pulse Pro Hybrid ANC Wireless Headphones",
                category = "Audio & Headphones",
                sellingPrice = 6800.0,
                originalPrice = 8900.0,
                discountPercent = 24L,
                stockQuantity = 25L,
                imageKeys = listOf("audio"),
                rating = 4.8,
                soldCount = 310L,
                isTrending = true
            ),
            Product(
                id = "preview_3",
                shopName = "Titan Wearables",
                name = "Titan Ultra GPS Rugged Smartwatch",
                category = "Smartwatches",
                sellingPrice = 11500.0,
                originalPrice = 14500.0,
                discountPercent = 21L,
                stockQuantity = 14L,
                imageKeys = listOf("watch"),
                rating = 4.9,
                soldCount = 88L,
                isDailyDeal = true
            ),
            Product(
                id = "preview_4",
                shopName = "NexGen Phone Lab",
                name = "65W GaN Fast Charger & Type-C Pro Kit",
                category = "Accessories & Repair",
                sellingPrice = 2400.0,
                originalPrice = 3200.0,
                discountPercent = 25L,
                stockQuantity = 40L,
                imageKeys = listOf("nexgen_logo"),
                rating = 4.7,
                soldCount = 520L,
                isBestSeller = true
            )
        )
    }

    var search by remember { mutableStateOf("") }
    var selectedCat by remember { mutableStateOf("All") }
    var signInPromptReason by remember { mutableStateOf<String?>(null) }

    val categories = listOf("All", "Phones & Tablets", "Audio & Headphones", "Smartwatches", "Accessories & Repair")
    val filtered = remember(search, selectedCat) {
        previewCatalog.filter { p ->
            (selectedCat == "All" || p.category == selectedCat) &&
                (search.isBlank() || p.name.contains(search, ignoreCase = true) || p.shopName.contains(search, ignoreCase = true))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GlowingNexGenLogo(size = 34.dp, cornerRadius = 9.dp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("NexGen Market", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("Guest Preview Mode", style = MaterialTheme.typography.labelSmall, color = NexGenCoral)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackToSignIn) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Sign In")
                    }
                },
                actions = {
                    Button(
                        onClick = onBackToSignIn,
                        colors = ButtonDefaults.buttonColors(containerColor = NexGenCoral),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.padding(end = 8.dp).testTag("guest_sign_in_cta")
                    ) {
                        Text("Sign In")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Surface(
                    color = NexGenCobalt.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Live Sync Requires Google Sign-In", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                "Sign in with Google to shop live seller catalogs, place M-Pesa orders, or open your own shop & POS.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Icon(Icons.Filled.Lock, contentDescription = null, tint = NexGenCobalt)
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text("Preview catalog search...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = NexGenCoral) },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categories) { cat ->
                        FilterChip(
                            selected = cat == selectedCat,
                            onClick = { selectedCat = cat },
                            label = { Text(cat) }
                        )
                    }
                }
            }

            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { signInPromptReason = "Browse live Flash Sales with your Google account" }
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_banner_flash_sale),
                        contentDescription = "Flash Sale Preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.72f), Color.Transparent)))
                    )
                    Column(modifier = Modifier.align(Alignment.BottomStart).padding(14.dp)) {
                        Surface(color = NexGenCoral, shape = RoundedCornerShape(6.dp)) {
                            Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Bolt, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("FLASH DEALS", style = MaterialTheme.typography.labelSmall, color = Color.White)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text("NexGen Universal Marketplace", style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Tap any item to sign in & start shopping", style = MaterialTheme.typography.bodySmall, color = Color(0xFFE2E8F0))
                    }
                }
            }

            items(filtered.chunked(2)) { pair ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { prod ->
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { signInPromptReason = "Sign in with Google to add '${prod.name}' to your cart or buy now." },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column {
                                Box(modifier = Modifier.fillMaxWidth().height(136.dp)) {
                                    ProductImageBox(
                                        imageKey = prod.imageKeys.firstOrNull() ?: "smartphone",
                                        contentDescription = prod.name,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    DiscountBadge(prod.discountPercent, modifier = Modifier.padding(8.dp))
                                }
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(prod.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    PriceDisplay(prod.sellingPrice, prod.originalPrice)
                                    StarRatingRow(prod.rating, soldCount = prod.soldCount)
                                    Button(
                                        onClick = { signInPromptReason = "Sign in with Google to add '${prod.name}' to cart." },
                                        colors = ButtonDefaults.buttonColors(containerColor = NexGenCoral),
                                        contentPadding = PaddingValues(vertical = 4.dp),
                                        modifier = Modifier.fillMaxWidth().height(36.dp)
                                    ) {
                                        Icon(Icons.Filled.ShoppingCart, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Add to Cart", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }

    if (signInPromptReason != null) {
        AlertDialog(
            onDismissRequest = { signInPromptReason = null },
            title = { Text("Sign In Required") },
            text = { Text(signInPromptReason!!) },
            confirmButton = {
                Button(
                    onClick = {
                        signInPromptReason = null
                        onBackToSignIn()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NexGenCoral)
                ) {
                    Text("Sign in with Google")
                }
            },
            dismissButton = {
                TextButton(onClick = { signInPromptReason = null }) {
                    Text("Continue Browsing")
                }
            }
        )
    }
}
