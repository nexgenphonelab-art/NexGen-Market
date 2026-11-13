package com.example.ui.marketplace

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Verified
import androidx.core.content.ContextCompat
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.Category
import com.example.data.model.Product
import com.example.data.model.ProductReview
import com.example.ui.components.DiscountBadge
import com.example.ui.components.PriceDisplay
import com.example.ui.components.ProductImageBox
import com.example.ui.components.StarRatingRow
import com.example.ui.theme.NexGenAmber
import com.example.ui.theme.NexGenCobalt
import com.example.ui.theme.NexGenCoral
import com.example.ui.theme.NexGenEmerald
import com.example.util.PrintAndExportUtils

@Composable
fun CategoriesScreen(
    categories: List<Category>,
    products: List<Product>,
    onSelectCategoryForExplore: (String) -> Unit,
    onOpenProduct: (Product) -> Unit
) {
    val allCats = remember(categories, products) {
        val fromCats = categories.map { it.name }
        val fromProds = products.map { it.category }
        (fromCats + fromProds).distinct().ifEmpty {
            listOf("Phones & Tablets", "Audio & Headphones", "Smartwatches", "Accessories & Repair")
        }
    }
    var activeCat by remember(allCats) { mutableStateOf(allCats.first()) }
    val matchingProducts = remember(products, activeCat) {
        products.filter { it.category.equals(activeCat, ignoreCase = true) }
    }
    val subCategories = remember(matchingProducts) {
        matchingProducts.map { it.subcategory.ifBlank { "General" } }.distinct()
    }

    Row(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Left vertical category rail
        LazyColumn(
            modifier = Modifier
                .width(124.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            items(allCats) { cat ->
                val selected = cat == activeCat
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (selected) MaterialTheme.colorScheme.background
                            else MaterialTheme.colorScheme.surface
                        )
                        .clickable { activeCat = cat }
                        .padding(horizontal = 12.dp, vertical = 16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (selected) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(22.dp)
                                    .background(NexGenCoral, RoundedCornerShape(2.dp))
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            text = cat,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) NexGenCoral else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        // Right detail pane
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(activeCat, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    OutlinedButton(onClick = { onSelectCategoryForExplore(activeCat) }) {
                        Text("Explore All")
                    }
                }
            }
            if (subCategories.isNotEmpty()) {
                item {
                    Text("Subcategories", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(subCategories) { sub ->
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.clickable { onSelectCategoryForExplore(activeCat) }
                            ) {
                                Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Category, contentDescription = null, tint = NexGenCobalt, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(sub, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }
            items(matchingProducts, key = { it.id }) { prod ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onOpenProduct(prod) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        ProductImageBox(
                            imageKey = prod.imageKeys.firstOrNull() ?: "smartphone",
                            contentDescription = prod.name,
                            modifier = Modifier.size(68.dp).clip(RoundedCornerShape(10.dp))
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(prod.name, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                            Spacer(Modifier.height(4.dp))
                            PriceDisplay(prod.sellingPrice, prod.originalPrice)
                            Text("Stock: ${prod.stockQuantity}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExploreScreen(
    products: List<Product>,
    wishlistIds: Set<String>,
    initialFilter: String,
    onOpenProduct: (Product) -> Unit,
    onToggleWishlist: (Product, Boolean) -> Unit
) {
    var sortOption by remember { mutableStateOf("Recommended") }
    var filterMode by remember(initialFilter) { mutableStateOf(initialFilter) }
    var search by remember { mutableStateOf("") }

    val filterChips = listOf("All", "Flash Sale", "Trending", "Best Selling", "New Arrivals", "Discount", "In Stock")
    val sortChips = listOf("Recommended", "Price: Low-High", "Price: High-Low", "Top Rated")

    val displayed = remember(products, filterMode, sortOption, search) {
        val base = products.filter { p ->
            val matchSearch = search.isBlank() || p.name.contains(search, true) || p.category.contains(search, true)
            val matchFilter = when (filterMode) {
                "Flash Sale" -> p.isFlashSale || p.discountPercent >= 15
                "Trending" -> p.isTrending
                "Best Selling" -> p.isBestSeller || p.soldCount > 0
                "New Arrivals" -> p.isNewArrival
                "Discount" -> p.discountPercent > 0
                "In Stock" -> p.stockQuantity > 0
                else -> true
            }
            matchSearch && matchFilter
        }
        when (sortOption) {
            "Price: Low-High" -> base.sortedBy { it.sellingPrice }
            "Price: High-Low" -> base.sortedByDescending { it.sellingPrice }
            "Top Rated" -> base.sortedByDescending { it.rating }
            else -> base
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Filter explore catalog...") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filterChips) { f ->
                    FilterChip(
                        selected = f == filterMode,
                        onClick = { filterMode = f },
                        label = { Text(f) }
                    )
                }
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(sortChips) { s ->
                    FilterChip(
                        selected = s == sortOption,
                        onClick = { sortOption = s },
                        label = { Text(s) }
                    )
                }
            }
        }
        items(displayed.chunked(2)) { pair ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { prod ->
                    MarketplaceProductCard(
                        product = prod,
                        isWishlisted = wishlistIds.contains(prod.id),
                        onClick = { onOpenProduct(prod) },
                        onToggleWishlist = { onToggleWishlist(prod, wishlistIds.contains(prod.id)) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailScreen(
    product: Product,
    relatedProducts: List<Product>,
    reviews: List<ProductReview>,
    isWishlisted: Boolean,
    isRestockAlertActive: Boolean = false,
    onBack: () -> Unit,
    onToggleWishlist: () -> Unit,
    onToggleRestockAlert: () -> Unit = {},
    onAddToCart: (String, Long) -> Unit,
    onBuyNow: (String, Long) -> Unit,
    onSubmitReview: (Long, String) -> Unit,
    onOpenRelated: (Product) -> Unit
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        onToggleRestockAlert()
    }
    val gallery = product.imageKeys.ifEmpty { listOf("smartphone") }
    var selectedImage by remember(product.id) { mutableStateOf(gallery.first()) }
    var selectedColor by remember(product.id) { mutableStateOf(product.colors.firstOrNull() ?: "Default") }
    var selectedSize by remember(product.id) { mutableStateOf(product.sizes.firstOrNull() ?: "Standard") }
    var quantity by remember(product.id) { mutableLongStateOf(1L) }
    var reviewText by remember { mutableStateOf("") }
    var reviewRating by remember { mutableLongStateOf(5L) }
    val variationLabel = listOf(selectedColor, selectedSize).distinct().joinToString(" • ")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Product Details", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("product_detail_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        PrintAndExportUtils.shareText(
                            context,
                            product.name,
                            "Check out ${product.name} on NexGen Market for ${PrintAndExportUtils.formatMoney(product.sellingPrice)}! Shop: ${product.shopName}"
                        )
                    }, modifier = Modifier.testTag("share_product_button")) {
                        Icon(Icons.Filled.Share, contentDescription = "Share Product")
                    }
                    IconButton(onClick = onToggleWishlist, modifier = Modifier.testTag("detail_wishlist_button")) {
                        Icon(
                            if (isWishlisted) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = "Save to Wishlist",
                            tint = if (isWishlisted) NexGenCoral else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 6.dp, shadowElevation = 8.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { onAddToCart(variationLabel, quantity) },
                        enabled = product.stockQuantity > 0L,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(48.dp).testTag("add_to_cart_button")
                    ) {
                        Icon(Icons.Filled.AddShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add to Cart")
                    }
                    Button(
                        onClick = { onBuyNow(variationLabel, quantity) },
                        enabled = product.stockQuantity > 0L,
                        colors = ButtonDefaults.buttonColors(containerColor = NexGenCoral),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(48.dp).testTag("buy_now_button")
                    ) {
                        Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Buy Now")
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                Box(modifier = Modifier.fillMaxWidth().height(290.dp)) {
                    ProductImageBox(
                        imageKey = selectedImage,
                        contentDescription = product.name,
                        modifier = Modifier.fillMaxSize()
                    )
                    DiscountBadge(
                        percent = product.discountPercent,
                        modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
                    )
                }
                if (gallery.size > 1) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(gallery) { img ->
                            ProductImageBox(
                                imageKey = img,
                                contentDescription = "Gallery image",
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(
                                        width = if (img == selectedImage) 2.dp else 1.dp,
                                        color = if (img == selectedImage) NexGenCoral else MaterialTheme.colorScheme.outline,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { selectedImage = img }
                            )
                        }
                    }
                }
            }

            item {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PriceDisplay(product.sellingPrice, product.originalPrice, large = true)
                    Text(product.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StarRatingRow(product.rating, product.reviewCount, product.soldCount)
                        Text(
                            text = if (product.stockQuantity > 0L) "${product.stockQuantity} in stock" else "Out of stock",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (product.stockQuantity > 0L) NexGenEmerald else MaterialTheme.colorScheme.error
                        )
                    }

                    if (product.stockQuantity <= 0L) {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "Currently Out of Stock",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    "Subscribe to get an instant alert when ${product.shopName} restocks this item.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(
                                    onClick = {
                                        if (!isRestockAlertActive &&
                                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                                        ) {
                                            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        } else {
                                            onToggleRestockAlert()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isRestockAlertActive) NexGenEmerald else NexGenCobalt
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("notify_restock_button")
                                ) {
                                    Icon(
                                        if (isRestockAlertActive) Icons.Filled.NotificationsOff else Icons.Filled.NotificationsActive,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        if (isRestockAlertActive) "Restock Alert Active (Tap to Remove)" else "Notify Me When Available",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Variations
                    if (product.colors.isNotEmpty()) {
                        Text("Color", style = MaterialTheme.typography.titleSmall)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(product.colors) { c ->
                                FilterChip(selected = c == selectedColor, onClick = { selectedColor = c }, label = { Text(c) })
                            }
                        }
                    }
                    if (product.sizes.isNotEmpty()) {
                        Text("Size / Edition", style = MaterialTheme.typography.titleSmall)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(product.sizes) { s ->
                                FilterChip(selected = s == selectedSize, onClick = { selectedSize = s }, label = { Text(s) })
                            }
                        }
                    }

                    // Quantity selector
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Quantity:", style = MaterialTheme.typography.titleSmall)
                        OutlinedButton(onClick = { if (quantity > 1) quantity-- }, shape = RoundedCornerShape(8.dp)) { Text("-") }
                        Text("$quantity", style = MaterialTheme.typography.titleMedium)
                        OutlinedButton(onClick = { if (quantity < product.stockQuantity) quantity++ }, shape = RoundedCornerShape(8.dp)) { Text("+") }
                    }

                    HorizontalDivider()

                    // Seller & Delivery info
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Storefront, contentDescription = null, tint = NexGenCobalt)
                                Spacer(Modifier.width(8.dp))
                                Text("Sold by ${product.shopName}", style = MaterialTheme.typography.titleSmall)
                                Spacer(Modifier.width(6.dp))
                                Icon(Icons.Filled.Verified, contentDescription = "Verified Seller", tint = NexGenEmerald, modifier = Modifier.size(16.dp))
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.LocalShipping, contentDescription = null, tint = NexGenCoral)
                                Spacer(Modifier.width(8.dp))
                                Text("Standard, Express Courier & Shop Pickup available • SKU: ${product.sku}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    Text("Description", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(product.description.ifBlank { "Quality verified product from ${product.shopName}." }, style = MaterialTheme.typography.bodyMedium)

                    if (product.specifications.isNotEmpty()) {
                        Text("Specifications", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        product.specifications.forEach { spec ->
                            Text("• $spec", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    HorizontalDivider()

                    // Customer Reviews
                    Text("Customer Reviews (${reviews.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        (1L..5L).forEach { star ->
                            IconButton(onClick = { reviewRating = star }, modifier = Modifier.size(34.dp)) {
                                Icon(
                                    Icons.Filled.Star,
                                    contentDescription = "Rate $star stars",
                                    tint = if (star <= reviewRating) NexGenAmber else MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = reviewText,
                            onValueChange = { reviewText = it },
                            placeholder = { Text("Share your verified review...") },
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                if (reviewText.isNotBlank()) {
                                    onSubmitReview(reviewRating, reviewText)
                                    reviewText = ""
                                }
                            },
                            enabled = reviewText.isNotBlank()
                        ) {
                            Text("Post")
                        }
                    }
                    reviews.forEach { rev ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text(rev.reviewerName, style = MaterialTheme.typography.titleSmall)
                                    Text("${rev.rating} ★", color = NexGenAmber, style = MaterialTheme.typography.labelMedium)
                                }
                                Text(rev.comment, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    if (relatedProducts.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text("Related Products", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(relatedProducts, key = { it.id }) { rel ->
                                MarketplaceProductCard(
                                    product = rel,
                                    isWishlisted = false,
                                    onClick = { onOpenRelated(rel) },
                                    onToggleWishlist = {},
                                    modifier = Modifier.width(160.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
