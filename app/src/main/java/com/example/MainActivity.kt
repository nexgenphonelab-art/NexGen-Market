package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.ThemePreferences
import com.example.data.model.Product
import com.example.data.model.Shop
import com.example.data.repository.AuthAndUserRepository
import com.example.data.repository.BusinessRepository
import com.example.data.repository.MarketplaceRepository
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.attemptAutoSignIn
import com.example.ui.auth.signOut
import com.example.ui.business.BusinessConsoleShell
import com.example.ui.marketplace.AccountScreen
import com.example.ui.marketplace.CartScreen
import com.example.ui.marketplace.CategoriesScreen
import com.example.ui.marketplace.ExploreScreen
import com.example.ui.marketplace.GuestPreviewScreen
import com.example.ui.marketplace.MarketplaceHomeScreen
import com.example.ui.marketplace.NotificationsScreen
import com.example.ui.marketplace.OrdersScreen
import com.example.ui.marketplace.ProductDetailScreen
import com.example.ui.marketplace.WishlistScreen
import com.example.ui.theme.NexGenCoral
import com.example.ui.theme.NexGenMarketTheme
import com.example.ui.viewmodel.BusinessViewModel
import com.example.ui.viewmodel.MarketplaceViewModel
import com.example.util.LocalNotificationHelper
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

private enum class MarketTab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Filled.Home),
    CATEGORIES("Categories", Icons.Filled.Category),
    EXPLORE("Explore", Icons.Filled.Explore),
    CART("Cart", Icons.Filled.ShoppingCart),
    ORDERS("Orders", Icons.AutoMirrored.Filled.ReceiptLong),
    ACCOUNT("Account", Icons.Filled.Person)
}

private sealed interface AppOverlay {
    data object None : AppOverlay
    data class ProductDetail(val product: Product) : AppOverlay
    data object Wishlist : AppOverlay
    data object Notifications : AppOverlay
    data object BusinessConsole : AppOverlay
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            val themePrefs = remember(context) { ThemePreferences(context.applicationContext) }
            val savedDarkPref by themePrefs.isDarkMode.collectAsStateWithLifecycle(initialValue = null)
            val isDark = savedDarkPref ?: isSystemInDarkTheme()

            NexGenMarketTheme(darkTheme = isDark) {
                AuthGateRoot(
                    isDarkTheme = isDark,
                    onToggleTheme = { enabled ->
                        scope.launch { themePrefs.setDarkMode(enabled) }
                    }
                )
            }
        }
    }
}

@Composable
private fun AuthGateRoot(
    isDarkTheme: Boolean,
    onToggleTheme: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val authUserFlow = remember {
        callbackFlow {
            val auth = Firebase.auth
            val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
                trySend(firebaseAuth.currentUser)
            }
            auth.addAuthStateListener(listener)
            awaitClose { auth.removeAuthStateListener(listener) }
        }
    }
    val currentUser: FirebaseUser? by authUserFlow.collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)
    var guestPreviewMode by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        delay(250L)
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = { guestPreviewMode = false },
            onUnauthenticated = {},
            scope = scope
        )
    }

    val user = currentUser
    if (user == null) {
        if (guestPreviewMode) {
            GuestPreviewScreen(onBackToSignIn = { guestPreviewMode = false })
        } else {
            AuthScreen(
                onAuthSuccess = { guestPreviewMode = false },
                onPreviewGuestCatalog = { guestPreviewMode = true }
            )
        }
    } else {
        key(user.uid) {
            AuthenticatedNexGenShell(
                user = user,
                isDarkTheme = isDarkTheme,
                onToggleTheme = onToggleTheme,
                onSignOut = {
                    signOut(credentialManager, scope) {
                        guestPreviewMode = false
                    }
                }
            )
        }
    }
}

@Composable
private fun AuthenticatedNexGenShell(
    user: FirebaseUser,
    isDarkTheme: Boolean,
    onToggleTheme: (Boolean) -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val appCtx = context.applicationContext

    val marketVm: MarketplaceViewModel = viewModel(
        key = "market_${user.uid}",
        factory = viewModelFactory {
            initializer {
                MarketplaceViewModel(
                    marketRepo = MarketplaceRepository(appCtx),
                    authRepo = AuthAndUserRepository(appCtx),
                    currentUserId = user.uid,
                    userDisplayName = user.displayName ?: user.email?.substringBefore("@") ?: "NexGen Shopper",
                    userEmail = user.email,
                    userPhotoUrl = user.photoUrl?.toString(),
                    isEmailVerified = user.isEmailVerified
                )
            }
        }
    )

    val bizVm: BusinessViewModel = viewModel(
        key = "biz_${user.uid}",
        factory = viewModelFactory {
            initializer {
                BusinessViewModel(
                    bizRepo = BusinessRepository(appCtx),
                    marketRepo = MarketplaceRepository(appCtx),
                    authRepo = AuthAndUserRepository(appCtx),
                    currentUserId = user.uid,
                    userEmail = user.email,
                    userDisplayName = user.displayName ?: user.email?.substringBefore("@") ?: "Store Admin",
                    isEmailVerified = user.isEmailVerified
                )
            }
        }
    )

    val marketMsg by marketVm.statusMessage.collectAsStateWithLifecycle()
    val bizMsg by bizVm.statusMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(marketMsg) {
        val m = marketMsg ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(m)
        marketVm.clearMessage()
    }
    LaunchedEffect(bizMsg) {
        val m = bizMsg ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(m)
        bizVm.clearMessage()
    }

    // Marketplace state
    val userProfile by marketVm.userProfile.collectAsStateWithLifecycle()
    val shops by marketVm.shops.collectAsStateWithLifecycle()
    val categories by marketVm.categories.collectAsStateWithLifecycle()
    val products by marketVm.products.collectAsStateWithLifecycle()
    val cartItems by marketVm.cartItems.collectAsStateWithLifecycle()
    val wishlistItems by marketVm.wishlistItems.collectAsStateWithLifecycle()
    val customerOrders by marketVm.orders.collectAsStateWithLifecycle()
    val coupons by marketVm.coupons.collectAsStateWithLifecycle()
    val addresses by marketVm.addresses.collectAsStateWithLifecycle()
    val notifications by marketVm.notifications.collectAsStateWithLifecycle()
    val restockAlerts by marketVm.restockAlerts.collectAsStateWithLifecycle()
    val searchQuery by marketVm.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by marketVm.selectedCategory.collectAsStateWithLifecycle()
    val recentlyViewedIds by marketVm.recentlyViewedIds.collectAsStateWithLifecycle()
    val selectedProductReviews by marketVm.selectedProductReviews.collectAsStateWithLifecycle()

    var initializedNotifSync by remember { mutableStateOf(false) }
    val seenNotifIds = remember { mutableSetOf<String>() }
    LaunchedEffect(notifications) {
        if (!initializedNotifSync) {
            notifications.forEach { seenNotifIds.add(it.id) }
            if (notifications.isNotEmpty()) {
                initializedNotifSync = true
            } else {
                delay(1500L)
                initializedNotifSync = true
            }
            return@LaunchedEffect
        }
        notifications
            .filter { !it.isRead && (it.type == "stock" || it.type == "order") && seenNotifIds.add(it.id) }
            .forEach { notif ->
                LocalNotificationHelper.showNotification(appCtx, notif.id, notif.title, notif.body)
            }
    }

    // Business state
    val isSuperAdmin by bizVm.isSuperAdmin.collectAsStateWithLifecycle()
    val adminRecord by bizVm.adminRecord.collectAsStateWithLifecycle()
    val staffMemberships by bizVm.staffMemberships.collectAsStateWithLifecycle()
    val ownedShops by bizVm.ownedShops.collectAsStateWithLifecycle()
    val allShops by bizVm.allShops.collectAsStateWithLifecycle()
    val activeShop by bizVm.activeShop.collectAsStateWithLifecycle()
    val gatewayConfig by bizVm.gatewayConfig.collectAsStateWithLifecycle()
    val shopProducts by bizVm.shopProducts.collectAsStateWithLifecycle()
    val shopOrders by bizVm.shopOrders.collectAsStateWithLifecycle()
    val posSales by bizVm.posSales.collectAsStateWithLifecycle()
    val posCart by bizVm.posCart.collectAsStateWithLifecycle()
    val shifts by bizVm.shifts.collectAsStateWithLifecycle()
    val suppliers by bizVm.suppliers.collectAsStateWithLifecycle()
    val purchases by bizVm.purchases.collectAsStateWithLifecycle()
    val stockMovements by bizVm.stockMovements.collectAsStateWithLifecycle()
    val shopAssets by bizVm.shopAssets.collectAsStateWithLifecycle()
    val expenses by bizVm.expenses.collectAsStateWithLifecycle()
    val crmCustomers by bizVm.crmCustomers.collectAsStateWithLifecycle()
    val refunds by bizVm.refunds.collectAsStateWithLifecycle()
    val staffList by bizVm.staffList.collectAsStateWithLifecycle()
    val adminsList by bizVm.adminsList.collectAsStateWithLifecycle()
    val auditLogs by bizVm.auditLogs.collectAsStateWithLifecycle()
    val financialSummary by bizVm.financialSummary.collectAsStateWithLifecycle()

    val hasBusinessAccess = isSuperAdmin ||
        (adminRecord != null && adminRecord?.isActive == true) ||
        ownedShops.isNotEmpty() ||
        staffMemberships.any { it.isActive }
    val staffPermissions = remember(staffMemberships) {
        staffMemberships.filter { it.isActive }.flatMap { it.permissions }.toSet()
    }

    var activeTab by remember { mutableStateOf(MarketTab.HOME) }
    var exploreFilter by remember { mutableStateOf("All") }
    var overlay by remember { mutableStateOf<AppOverlay>(AppOverlay.None) }

    val wishlistProductIds = remember(wishlistItems) { wishlistItems.map { it.productId }.toSet() }
    val unreadNotifCount = remember(notifications) { notifications.count { !it.isRead } }
    val totalCartCount = remember(cartItems) { cartItems.sumOf { it.quantity }.toInt() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { rootPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(rootPadding)) {
            when (val currentOverlay = overlay) {
                AppOverlay.BusinessConsole -> {
                    BusinessConsoleShell(
                        bizVm = bizVm,
                        marketVm = marketVm,
                        activeShop = activeShop,
                        allShops = allShops,
                        isSuperAdmin = isSuperAdmin,
                        allowedPermissions = staffPermissions,
                        productsState = shopProducts,
                        categoriesState = categories,
                        ordersState = shopOrders,
                        posSalesState = posSales,
                        posCartState = posCart,
                        shiftsState = shifts,
                        suppliersState = suppliers,
                        purchasesState = purchases,
                        stockMovementsState = stockMovements,
                        shopAssetsState = shopAssets,
                        expensesState = expenses,
                        couponsState = coupons,
                        crmState = crmCustomers,
                        refundsState = refunds,
                        staffState = staffList,
                        adminsState = adminsList,
                        auditLogsState = auditLogs,
                        gatewayConfig = gatewayConfig,
                        financialSummary = financialSummary,
                        onExitToMarketplace = { overlay = AppOverlay.None }
                    )
                }

                is AppOverlay.ProductDetail -> {
                    val liveProd = products.firstOrNull { it.id == currentOverlay.product.id } ?: currentOverlay.product
                    val related = remember(products, liveProd) {
                        products.filter { it.category == liveProd.category && it.id != liveProd.id }.take(6)
                    }
                    val restockSubscribed = remember(restockAlerts, liveProd.id) {
                        restockAlerts.any { it.productId == liveProd.id && it.status == "waiting" }
                    }
                    ProductDetailScreen(
                        product = liveProd,
                        relatedProducts = related,
                        reviews = selectedProductReviews,
                        isWishlisted = wishlistProductIds.contains(liveProd.id),
                        isRestockAlertActive = restockSubscribed,
                        onBack = { overlay = AppOverlay.None },
                        onToggleWishlist = { marketVm.toggleWishlist(liveProd, wishlistProductIds.contains(liveProd.id)) },
                        onToggleRestockAlert = { marketVm.toggleRestockAlert(liveProd) },
                        onAddToCart = { variation, qty -> marketVm.addToCart(liveProd, variation, qty) },
                        onBuyNow = { variation, qty ->
                            marketVm.addToCart(liveProd, variation, qty) {
                                overlay = AppOverlay.None
                                activeTab = MarketTab.CART
                            }
                        },
                        onSubmitReview = { rating, comment -> marketVm.submitReview(liveProd, rating, comment) },
                        onOpenRelated = { rel ->
                            marketVm.markProductViewed(rel.id)
                            overlay = AppOverlay.ProductDetail(rel)
                        }
                    )
                }

                AppOverlay.Wishlist -> {
                    WishlistScreen(
                        wishlist = wishlistItems,
                        products = products,
                        onBack = { overlay = AppOverlay.None },
                        onOpenProduct = { prod ->
                            marketVm.markProductViewed(prod.id)
                            overlay = AppOverlay.ProductDetail(prod)
                        },
                        onRemove = { prod -> marketVm.toggleWishlist(prod, true) }
                    )
                }

                AppOverlay.Notifications -> {
                    NotificationsScreen(
                        notifications = notifications,
                        onBack = { overlay = AppOverlay.None },
                        onMarkRead = marketVm::markNotificationRead
                    )
                }

                AppOverlay.None -> {
                    if (activeTab != MarketTab.HOME) {
                        BackHandler { activeTab = MarketTab.HOME }
                    }
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val useRail = maxWidth >= 700.dp

                        Scaffold(
                            contentWindowInsets = WindowInsets(0, 0, 0, 0),
                            bottomBar = {
                                if (!useRail) {
                                    NavigationBar {
                                        MarketTab.entries.forEach { tab ->
                                            NavigationBarItem(
                                                selected = activeTab == tab,
                                                onClick = { activeTab = tab },
                                                icon = {
                                                    if (tab == MarketTab.CART && totalCartCount > 0) {
                                                        BadgedBox(badge = { Badge(containerColor = NexGenCoral) { Text("$totalCartCount", color = Color.White) } }) {
                                                            Icon(tab.icon, contentDescription = tab.label)
                                                        }
                                                    } else {
                                                        Icon(tab.icon, contentDescription = tab.label)
                                                    }
                                                },
                                                label = { Text(tab.label, maxLines = 1) }
                                            )
                                        }
                                    }
                                }
                            }
                        ) { innerPadding ->
                            Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                                if (useRail) {
                                    NavigationRail {
                                        MarketTab.entries.forEach { tab ->
                                            NavigationRailItem(
                                                selected = activeTab == tab,
                                                onClick = { activeTab = tab },
                                                icon = {
                                                    if (tab == MarketTab.CART && totalCartCount > 0) {
                                                        BadgedBox(badge = { Badge(containerColor = NexGenCoral) { Text("$totalCartCount", color = Color.White) } }) {
                                                            Icon(tab.icon, contentDescription = tab.label)
                                                        }
                                                    } else {
                                                        Icon(tab.icon, contentDescription = tab.label)
                                                    }
                                                },
                                                label = { Text(tab.label) }
                                            )
                                        }
                                    }
                                }

                                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                                    when (activeTab) {
                                        MarketTab.HOME -> MarketplaceHomeScreen(
                                            products = products,
                                            shops = shops,
                                            wishlistIds = wishlistProductIds,
                                            recentlyViewedIds = recentlyViewedIds,
                                            unreadNotifs = unreadNotifCount,
                                            searchQuery = searchQuery,
                                            selectedCategory = selectedCategory,
                                            isDarkTheme = isDarkTheme,
                                            onToggleTheme = { onToggleTheme(!isDarkTheme) },
                                            onSearchChange = marketVm::setSearchQuery,
                                            onCategorySelected = marketVm::setCategory,
                                            onOpenProduct = { prod ->
                                                marketVm.markProductViewed(prod.id)
                                                overlay = AppOverlay.ProductDetail(prod)
                                            },
                                            onToggleWishlist = marketVm::toggleWishlist,
                                            onOpenNotifications = { overlay = AppOverlay.Notifications },
                                            onOpenWishlist = { overlay = AppOverlay.Wishlist },
                                            onOpenExploreWithFilter = { filter ->
                                                exploreFilter = filter
                                                activeTab = MarketTab.EXPLORE
                                            }
                                        )

                                        MarketTab.CATEGORIES -> CategoriesScreen(
                                            categories = categories,
                                            products = products,
                                            onSelectCategoryForExplore = { cat ->
                                                marketVm.setCategory(cat)
                                                exploreFilter = "All"
                                                activeTab = MarketTab.EXPLORE
                                            },
                                            onOpenProduct = { prod ->
                                                marketVm.markProductViewed(prod.id)
                                                overlay = AppOverlay.ProductDetail(prod)
                                            }
                                        )

                                        MarketTab.EXPLORE -> ExploreScreen(
                                            products = products,
                                            wishlistIds = wishlistProductIds,
                                            initialFilter = exploreFilter,
                                            onOpenProduct = { prod ->
                                                marketVm.markProductViewed(prod.id)
                                                overlay = AppOverlay.ProductDetail(prod)
                                            },
                                            onToggleWishlist = marketVm::toggleWishlist
                                        )

                                        MarketTab.CART -> CartScreen(
                                            cartItems = cartItems,
                                            coupons = coupons,
                                            shops = shops,
                                            addresses = addresses,
                                            userProfile = userProfile,
                                            onUpdateQty = marketVm::updateCartQty,
                                            onRemoveItem = marketVm::removeFromCart,
                                            onSaveAddress = marketVm::saveAddress,
                                            onPlaceOrder = marketVm::placeOrder,
                                            onOrderPlacedNavigate = { activeTab = MarketTab.ORDERS }
                                        )

                                        MarketTab.ORDERS -> OrdersScreen(
                                            orders = customerOrders,
                                            shops = shops,
                                            loadLineItems = marketVm::loadOrderLineItems,
                                            onSubmitPaymentRef = marketVm::submitPaymentReference,
                                            onCancelOrder = marketVm::cancelOrder,
                                            onRequestReturn = marketVm::requestReturn
                                        )

                                        MarketTab.ACCOUNT -> AccountScreen(
                                            userProfile = userProfile,
                                            addresses = addresses,
                                            hasBusinessAccess = hasBusinessAccess,
                                            isSuperAdmin = isSuperAdmin,
                                            isDarkTheme = isDarkTheme,
                                            onToggleTheme = onToggleTheme,
                                            onUpdateProfile = marketVm::updateProfile,
                                            onSaveAddress = marketVm::saveAddress,
                                            onDeleteAddress = marketVm::deleteAddress,
                                            onOpenBusinessConsole = { overlay = AppOverlay.BusinessConsole },
                                            onCreateUniversalShop = { shopName, currency, receiptHeader ->
                                                bizVm.saveShop(
                                                    Shop(
                                                        ownerId = user.uid,
                                                        shopName = shopName,
                                                        currencyCode = currency,
                                                        receiptHeaderTitle = receiptHeader
                                                    )
                                                ) {
                                                    overlay = AppOverlay.BusinessConsole
                                                }
                                            },
                                            onSignOut = onSignOut
                                        )
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
