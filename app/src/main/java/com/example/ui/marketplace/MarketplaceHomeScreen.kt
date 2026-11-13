package com.example.ui.marketplace

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.example.data.model.Shop
import com.example.ui.components.DiscountBadge
import com.example.ui.components.GlowingNexGenLogo
import com.example.ui.components.PriceDisplay
import com.example.ui.components.ProductImageBox
import com.example.ui.components.StarRatingRow
import com.example.ui.theme.NexGenAmber
import com.example.ui.theme.NexGenCobalt
import com.example.ui.theme.NexGenCoral
import com.example.ui.theme.NexGenEmerald

@Composable
fun MarketplaceHomeScreen(
    products: List<Product>,
    shops: List<Shop>,
    wishlistIds: Set<String>,
    recentlyViewedIds: List<String>,
    unreadNotifs: Int,
    searchQuery: String,
    selectedCategory: String,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onSearchChange: (String) -> Unit,
    onCategorySelected: (String) -> Unit,
    onOpenProduct: (Product) -> Unit,
    onToggleWishlist: (Product, Boolean) -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenWishlist: () -> Unit,
    onOpenExploreWithFilter: (String) -> Unit
) {
    val categoryNames = remember(products) {
        listOf("All") + products.map { it.category }.distinct().sorted()
    }
    var showSuggestions by remember { mutableStateOf(false) }

    val filteredProducts = remember(products, searchQuery, selectedCategory) {
        products.filter { p ->
            val matchesCat = selectedCategory == "All" || p.category.equals(selectedCategory, ignoreCase = true)
            val matchesSearch = searchQuery.isBlank() ||
                p.name.contains(searchQuery, ignoreCase = true) ||
                p.category.contains(searchQuery, ignoreCase = true) ||
                p.shopName.contains(searchQuery, ignoreCase = true)
            matchesCat && matchesSearch
        }
    }

    val flashSales = remember(products) { products.filter { it.isFlashSale || it.discountPercent >= 15 } }
    val trending = remember(products) { products.filter { it.isTrending } }
    val bestSellers = remember(products) { products.sortedByDescending { it.soldCount }.take(8) }
    val newArrivals = remember(products) { products.filter { it.isNewArrival } }
    val dailyDeals = remember(products) { products.filter { it.isDailyDeal } }
    val sponsored = remember(products) { products.filter { it.isSponsoredAd } }
    val recentlyViewed = remember(products, recentlyViewedIds) {
        recentlyViewedIds.mapNotNull { id -> products.firstOrNull { it.id == id } }
    }

    val suggestions = remember(products, searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else products.filter {
            it.name.contains(searchQuery, ignoreCase = true) || it.category.contains(searchQuery, ignoreCase = true)
        }.take(5)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Branded Header + Search Bar
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.surface,
                                MaterialTheme.colorScheme.background
                            )
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GlowingNexGenLogo(size = 42.dp, cornerRadius = 11.dp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "NexGen Market",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Global Deals • Choice Express",
                                style = MaterialTheme.typography.labelSmall,
                                color = NexGenCoral
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onToggleTheme, modifier = Modifier.testTag("theme_toggle_button")) {
                            Icon(
                                imageVector = if (isDarkTheme) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                                contentDescription = "Toggle Dark/Light Theme"
                            )
                        }
                        IconButton(onClick = onOpenWishlist, modifier = Modifier.testTag("home_wishlist_button")) {
                            Icon(Icons.Filled.FavoriteBorder, contentDescription = "Wishlist")
                        }
                        IconButton(onClick = onOpenNotifications, modifier = Modifier.testTag("home_notifications_button")) {
                            BadgedBox(
                                badge = {
                                    if (unreadNotifs > 0) {
                                        Badge(containerColor = NexGenCoral) {
                                            Text(unreadNotifs.coerceAtMost(99).toString(), color = Color.White)
                                        }
                                    }
                                }
                            ) {
                                Icon(Icons.Filled.Notifications, contentDescription = "Notifications")
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        onSearchChange(it)
                        showSuggestions = it.isNotBlank()
                    },
                    placeholder = { Text("Search smartphones, headphones, shops, SKUs...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = NexGenCoral) },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = NexGenCoral,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_search_input")
                )

                if (showSuggestions && suggestions.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(4.dp)
                    ) {
                        Column {
                            suggestions.forEach { prod ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            showSuggestions = false
                                            onOpenProduct(prod)
                                        }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.width(10.dp))
                                    Text(prod.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    Text(prod.category, style = MaterialTheme.typography.labelSmall, color = NexGenCobalt)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Category Quick Chips
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categoryNames) { cat ->
                    val selected = cat == selectedCategory
                    FilterChip(
                        selected = selected,
                        onClick = { onCategorySelected(cat) },
                        label = { Text(cat) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NexGenCoral,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
        }

        // Promotional Banners Carousel
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    PromoBannerCard(
                        drawableRes = R.drawable.img_banner_flash_sale,
                        badgeText = "FLASH SALE",
                        title = "Super Tech Deals",
                        subtitle = "Up to 45% OFF Flagship Gear",
                        accent = NexGenCoral,
                        onClick = { onOpenExploreWithFilter("Flash Sale") }
                    )
                }
                item {
                    PromoBannerCard(
                        drawableRes = R.drawable.img_banner_mega_deals,
                        badgeText = "MEGA CHOICE",
                        title = "Universal Marketplace",
                        subtitle = "Verified Sellers • Fast Dispatch",
                        accent = NexGenCobalt,
                        onClick = { onOpenExploreWithFilter("All") }
                    )
                }
            }
        }

        // Featured Sellers & Shops
        if (shops.isNotEmpty()) {
            item {
                SectionHeader("Featured Sellers & Shops", icon = Icons.Filled.Storefront, tint = NexGenCobalt)
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(shops, key = { it.id }) { shop ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .width(200.dp)
                                .clickable { onSearchChange(shop.shopName) }
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = NexGenCobalt.copy(alpha = 0.14f),
                                    shape = CircleShape,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Filled.Storefront, contentDescription = null, tint = NexGenCobalt)
                                    }
                                }
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(shop.shopName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(shop.tagline.ifBlank { shop.categoryFocus }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                    Text("★ ${shop.rating} • ${shop.currencyCode}", style = MaterialTheme.typography.labelSmall, color = NexGenAmber)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Flash Sales Strip
        if (flashSales.isNotEmpty()) {
            item {
                SectionHeader("Flash Sales", icon = Icons.Filled.Bolt, tint = NexGenCoral, actionLabel = "See All") {
                    onOpenExploreWithFilter("Flash Sale")
                }
                HorizontalProductStrip(flashSales, wishlistIds, onOpenProduct, onToggleWishlist)
            }
        }

        // Daily Deals
        if (dailyDeals.isNotEmpty()) {
            item {
                SectionHeader("Daily Deals", icon = Icons.Filled.LocalOffer, tint = NexGenAmber)
                HorizontalProductStrip(dailyDeals, wishlistIds, onOpenProduct, onToggleWishlist)
            }
        }

        // Trending & Best Selling
        if (trending.isNotEmpty()) {
            item {
                SectionHeader("Trending Now", icon = Icons.AutoMirrored.Filled.TrendingUp, tint = NexGenEmerald)
                HorizontalProductStrip(trending, wishlistIds, onOpenProduct, onToggleWishlist)
            }
        }

        // Sponsored / Product Advertisements
        if (sponsored.isNotEmpty()) {
            item {
                SectionHeader("Sponsored Spotlight", icon = Icons.Filled.Campaign, tint = NexGenCobalt)
                HorizontalProductStrip(sponsored, wishlistIds, onOpenProduct, onToggleWishlist, badge = "AD")
            }
        }

        // Recently Viewed
        if (recentlyViewed.isNotEmpty()) {
            item {
                SectionHeader("Recently Viewed", icon = Icons.Filled.Search, tint = MaterialTheme.colorScheme.secondary)
                HorizontalProductStrip(recentlyViewed, wishlistIds, onOpenProduct, onToggleWishlist)
            }
        }

        // Main AliExpress-Style 2-Column Catalog Grid
        item {
            SectionHeader(
                title = if (selectedCategory == "All") "More To Love • Recommended" else "$selectedCategory Catalog",
                icon = Icons.Filled.LocalOffer,
                tint = NexGenCoral
            )
        }

        if (filteredProducts.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Filled.Storefront, contentDescription = null, modifier = Modifier.size(48.dp), tint = NexGenCobalt)
                        Spacer(Modifier.height(10.dp))
                        Text("Marketplace Catalog Empty", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Open your Shop Workspace in My Account -> Business Console to add live products or load a starter catalog.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            val rows = filteredProducts.chunked(2)
            items(rows) { pair ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    pair.forEach { prod ->
                        MarketplaceProductCard(
                            product = prod,
                            isWishlisted = wishlistIds.contains(prod.id),
                            onClick = { onOpenProduct(prod) },
                            onToggleWishlist = { onToggleWishlist(prod, wishlistIds.contains(prod.id)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (pair.size == 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        // Best Sellers & New Arrivals summary row
        if (bestSellers.isNotEmpty() || newArrivals.isNotEmpty()) {
            item {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(onClick = { onOpenExploreWithFilter("Best Selling") }, label = { Text("Best Sellers (${bestSellers.size})") })
                    AssistChip(onClick = { onOpenExploreWithFilter("New Arrivals") }, label = { Text("New Arrivals (${newArrivals.size})") })
                    AssistChip(onClick = { onOpenExploreWithFilter("Discount") }, label = { Text("Discounted") })
                }
            }
        }
    }
}

@Composable
private fun PromoBannerCard(
    drawableRes: Int,
    badgeText: String,
    title: String,
    subtitle: String,
    accent: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(304.dp)
            .height(148.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
    ) {
        Image(
            painter = painterResource(id = drawableRes),
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Black.copy(alpha = 0.72f), Color.Transparent)
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(14.dp)
        ) {
            Surface(color = accent, shape = RoundedCornerShape(6.dp)) {
                Text(
                    badgeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color(0xFFE2E8F0))
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(actionLabel, color = NexGenCoral)
            }
        }
    }
}

@Composable
private fun HorizontalProductStrip(
    items: List<Product>,
    wishlistIds: Set<String>,
    onOpenProduct: (Product) -> Unit,
    onToggleWishlist: (Product, Boolean) -> Unit,
    badge: String? = null
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(items, key = { it.id }) { prod ->
            MarketplaceProductCard(
                product = prod,
                isWishlisted = wishlistIds.contains(prod.id),
                onClick = { onOpenProduct(prod) },
                onToggleWishlist = { onToggleWishlist(prod, wishlistIds.contains(prod.id)) },
                extraBadge = badge,
                modifier = Modifier.width(172.dp)
            )
        }
    }
}

@Composable
fun MarketplaceProductCard(
    product: Product,
    isWishlisted: Boolean,
    onClick: () -> Unit,
    onToggleWishlist: () -> Unit,
    modifier: Modifier = Modifier,
    extraBadge: String? = null
) {
    Card(
        modifier = modifier
            .clickable(onClick = onClick)
            .testTag("product_card_${product.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(148.dp)
            ) {
                ProductImageBox(
                    imageKey = product.imageKeys.firstOrNull() ?: "smartphone",
                    contentDescription = product.name,
                    modifier = Modifier.fillMaxSize()
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        DiscountBadge(product.discountPercent)
                        if (extraBadge != null) {
                            Surface(color = Color.Black.copy(alpha = 0.65f), shape = RoundedCornerShape(6.dp)) {
                                Text(extraBadge, style = MaterialTheme.typography.labelSmall, color = Color.White, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                    }
                    Surface(
                        color = Color.Black.copy(alpha = 0.45f),
                        shape = CircleShape,
                        modifier = Modifier
                            .size(32.dp)
                            .clickable(onClick = onToggleWishlist)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isWishlisted) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = "Toggle Wishlist",
                                tint = if (isWishlisted) NexGenCoral else Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
                if (product.stockQuantity <= 0L) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.72f),
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    ) {
                        Text(
                            "OUT OF STOCK",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp)
                        )
                    }
                }
            }
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                PriceDisplay(sellingPrice = product.sellingPrice, originalPrice = product.originalPrice)
                StarRatingRow(rating = product.rating, soldCount = product.soldCount)
                Text(
                    text = product.shopName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
