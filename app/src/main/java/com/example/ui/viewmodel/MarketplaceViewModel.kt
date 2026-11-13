package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AppNotification
import com.example.data.model.CartItem
import com.example.data.model.Category
import com.example.data.model.Coupon
import com.example.data.model.Order
import com.example.data.model.OrderLineItem
import com.example.data.model.Product
import com.example.data.model.ProductReview
import com.example.data.model.RestockAlert
import com.example.data.model.SavedAddress
import com.example.data.model.Shop
import com.example.data.model.UserProfile
import com.example.data.model.WishlistItem
import com.example.data.repository.AuthAndUserRepository
import com.example.data.repository.MarketplaceRepository
import com.example.data.unwrapFirestoreError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MarketplaceViewModel(
    private val marketRepo: MarketplaceRepository,
    private val authRepo: AuthAndUserRepository,
    private val currentUserId: String,
    userDisplayName: String,
    userEmail: String?,
    userPhotoUrl: String?,
    isEmailVerified: Boolean
) : ViewModel() {

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _sortOption = MutableStateFlow("Recommended")
    val sortOption: StateFlow<String> = _sortOption.asStateFlow()

    private val _recentlyViewedIds = MutableStateFlow<List<String>>(emptyList())
    val recentlyViewedIds: StateFlow<List<String>> = _recentlyViewedIds.asStateFlow()

    private val _selectedProductId = MutableStateFlow<String?>(null)

    val userProfile: StateFlow<UserProfile?> = authRepo.observeUserProfile(currentUserId)
        .catch { e ->
            if (e is CancellationException && e.cause == null) throw e
            Log.w(TAG, "Profile error", e.unwrapFirestoreError())
            emit(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val shops: StateFlow<List<Shop>> = marketRepo.observeApprovedShops()
        .catch { e ->
            if (e is CancellationException && e.cause == null) throw e
            Log.w(TAG, "Shops error", e.unwrapFirestoreError())
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val categories: StateFlow<List<Category>> = marketRepo.observeActiveCategories()
        .catch { e ->
            if (e is CancellationException && e.cause == null) throw e
            Log.w(TAG, "Categories error", e.unwrapFirestoreError())
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val products: StateFlow<List<Product>> = marketRepo.observeVisibleProducts()
        .catch { e ->
            if (e is CancellationException && e.cause == null) throw e
            Log.w(TAG, "Products error", e.unwrapFirestoreError())
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val cartItems: StateFlow<List<CartItem>> = marketRepo.observeCart(currentUserId)
        .catch { e ->
            if (e is CancellationException && e.cause == null) throw e
            Log.w(TAG, "Cart error", e.unwrapFirestoreError())
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val wishlistItems: StateFlow<List<WishlistItem>> = marketRepo.observeWishlist(currentUserId)
        .catch { e ->
            if (e is CancellationException && e.cause == null) throw e
            Log.w(TAG, "Wishlist error", e.unwrapFirestoreError())
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val orders: StateFlow<List<Order>> = marketRepo.observeCustomerOrders(currentUserId)
        .catch { e ->
            if (e is CancellationException && e.cause == null) throw e
            Log.w(TAG, "Orders error", e.unwrapFirestoreError())
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val coupons: StateFlow<List<Coupon>> = marketRepo.observeActiveCoupons()
        .catch { e ->
            if (e is CancellationException && e.cause == null) throw e
            Log.w(TAG, "Coupons error", e.unwrapFirestoreError())
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val addresses: StateFlow<List<SavedAddress>> = authRepo.observeAddresses(currentUserId)
        .catch { e ->
            if (e is CancellationException && e.cause == null) throw e
            Log.w(TAG, "Addresses error", e.unwrapFirestoreError())
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val notifications: StateFlow<List<AppNotification>> = authRepo.observeNotifications(currentUserId)
        .catch { e ->
            if (e is CancellationException && e.cause == null) throw e
            Log.w(TAG, "Notifications error", e.unwrapFirestoreError())
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val restockAlerts: StateFlow<List<RestockAlert>> = marketRepo.observeRestockAlerts(currentUserId)
        .catch { e ->
            if (e is CancellationException && e.cause == null) throw e
            Log.w(TAG, "Restock alerts error", e.unwrapFirestoreError())
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedProductReviews: StateFlow<List<ProductReview>> = _selectedProductId
        .flatMapLatest { prodId ->
            if (prodId.isNullOrBlank()) flowOf(emptyList())
            else marketRepo.observeProductReviews(prodId).catch { emit(emptyList()) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    init {
        viewModelScope.launch {
            authRepo.ensureUserProfile(
                uid = currentUserId,
                displayName = userDisplayName,
                email = userEmail,
                photoUrl = userPhotoUrl,
                isEmailVerified = isEmailVerified
            ).onFailure {
                _statusMessage.value = it.unwrapFirestoreError().localizedMessage
            }
        }
    }

    fun clearMessage() {
        _statusMessage.value = null
    }

    fun setSearchQuery(q: String) {
        _searchQuery.value = q
    }

    fun setCategory(cat: String) {
        _selectedCategory.value = cat
    }

    fun setSortOption(option: String) {
        _sortOption.value = option
    }

    fun markProductViewed(productId: String) {
        _selectedProductId.value = productId
        val updated = listOf(productId) + _recentlyViewedIds.value.filterNot { it == productId }
        _recentlyViewedIds.value = updated.take(10)
    }

    fun addToCart(product: Product, variationLabel: String = "Standard", quantity: Long = 1L, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            marketRepo.addToCart(product, variationLabel, quantity)
                .onSuccess {
                    _statusMessage.value = "Added ${product.name} to cart"
                    onDone()
                }
                .onFailure {
                    _statusMessage.value = it.unwrapFirestoreError().localizedMessage ?: "Failed to add to cart"
                }
        }
    }

    fun updateCartQty(cartItemId: String, qty: Long) {
        viewModelScope.launch {
            marketRepo.updateCartQuantity(cartItemId, qty)
                .onFailure { _statusMessage.value = it.unwrapFirestoreError().localizedMessage }
        }
    }

    fun removeFromCart(cartItemId: String) {
        viewModelScope.launch {
            marketRepo.removeFromCart(cartItemId)
                .onFailure { _statusMessage.value = it.unwrapFirestoreError().localizedMessage }
        }
    }

    fun toggleWishlist(product: Product, currentlySaved: Boolean) {
        viewModelScope.launch {
            marketRepo.toggleWishlist(product, currentlySaved)
                .onSuccess {
                    _statusMessage.value = if (currentlySaved) "Removed from Wishlist" else "Saved to Wishlist"
                }
                .onFailure { _statusMessage.value = it.unwrapFirestoreError().localizedMessage }
        }
    }

    fun toggleRestockAlert(product: Product) {
        viewModelScope.launch {
            val currentlySubscribed = restockAlerts.value.any { it.productId == product.id && it.status == "waiting" }
            val customerName = userProfile.value?.displayName?.ifBlank { "Shopper" } ?: "Shopper"
            marketRepo.toggleRestockAlert(product, customerName, currentlySubscribed)
                .onSuccess {
                    _statusMessage.value = if (currentlySubscribed) {
                        "Restock alert removed for ${product.name}"
                    } else {
                        "You will be notified when ${product.name} is back in stock!"
                    }
                }
                .onFailure {
                    _statusMessage.value = it.unwrapFirestoreError().localizedMessage ?: "Failed to update restock alert"
                }
        }
    }

    fun submitReview(product: Product, rating: Long, comment: String) {
        viewModelScope.launch {
            val name = userProfile.value?.displayName ?: "Verified Customer"
            marketRepo.submitReview(product, name, rating, comment)
                .onSuccess { _statusMessage.value = "Review published!" }
                .onFailure { _statusMessage.value = it.unwrapFirestoreError().localizedMessage }
        }
    }

    fun placeOrder(
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
        pointsToRedeem: Long = 0L,
        onSuccess: (List<Order>) -> Unit
    ) {
        viewModelScope.launch {
            marketRepo.placeOnlineOrder(
                items = items,
                customerName = customerName,
                customerPhone = customerPhone,
                customerEmail = customerEmail,
                deliveryAddress = deliveryAddress,
                deliveryOption = deliveryOption,
                paymentMethod = paymentMethod,
                paymentReference = paymentReference,
                coupon = coupon,
                shippingFeePerShop = shippingFee,
                taxRatePercent = taxRatePercent,
                pointsToRedeem = pointsToRedeem
            ).onSuccess { created ->
                _statusMessage.value = "Order ${created.joinToString { it.orderNumber }} placed!"
                onSuccess(created)
            }.onFailure {
                _statusMessage.value = it.unwrapFirestoreError().localizedMessage ?: "Checkout failed"
            }
        }
    }

    fun submitPaymentReference(orderId: String, code: String) {
        viewModelScope.launch {
            marketRepo.submitPaymentReference(orderId, code)
                .onSuccess { _statusMessage.value = "Payment code submitted for verification" }
                .onFailure { _statusMessage.value = it.unwrapFirestoreError().localizedMessage }
        }
    }

    fun cancelOrder(orderId: String, reason: String) {
        viewModelScope.launch {
            marketRepo.cancelOrder(orderId, reason)
                .onSuccess { _statusMessage.value = "Order cancelled" }
                .onFailure { _statusMessage.value = it.unwrapFirestoreError().localizedMessage }
        }
    }

    fun requestReturn(order: Order, reason: String) {
        viewModelScope.launch {
            marketRepo.requestOrderReturn(order, reason)
                .onSuccess { _statusMessage.value = "Return & refund request submitted" }
                .onFailure { _statusMessage.value = it.unwrapFirestoreError().localizedMessage }
        }
    }

    suspend fun loadOrderLineItems(orderId: String): List<OrderLineItem> {
        return marketRepo.getOrderLineItems(orderId, currentUserId, isShopSide = false)
            .getOrElse { emptyList() }
    }

    fun saveAddress(address: SavedAddress) {
        viewModelScope.launch {
            authRepo.saveAddress(address)
                .onSuccess { _statusMessage.value = "Delivery address saved" }
                .onFailure { _statusMessage.value = it.unwrapFirestoreError().localizedMessage }
        }
    }

    fun deleteAddress(addressId: String) {
        viewModelScope.launch {
            authRepo.deleteAddress(addressId)
                .onFailure { _statusMessage.value = it.unwrapFirestoreError().localizedMessage }
        }
    }

    fun updateProfile(displayName: String, phone: String?, defaultAddress: String?) {
        viewModelScope.launch {
            authRepo.updateUserProfile(displayName, phone, defaultAddress)
                .onSuccess { _statusMessage.value = "Profile updated" }
                .onFailure { _statusMessage.value = it.unwrapFirestoreError().localizedMessage }
        }
    }

    fun markNotificationRead(notifId: String) {
        viewModelScope.launch {
            authRepo.markNotificationRead(notifId)
        }
    }

    private companion object {
        const val TAG = "MarketplaceVM"
    }
}
