package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.OperationType
import com.example.data.handleFirestoreError
import com.example.data.model.CartItem
import com.example.data.model.Category
import com.example.data.model.Coupon
import com.example.data.model.Order
import com.example.data.model.OrderLineItem
import com.example.data.model.Product
import com.example.data.model.ProductReview
import com.example.data.model.RefundRequest
import com.example.data.model.RestockAlert
import com.example.data.model.Shop
import com.example.data.model.WishlistItem
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class MarketplaceRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return Firebase.auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeApprovedShops(): Flow<List<Shop>> {
        val query = db.collection("shops").whereEqualTo("isApproved", true)
        return query.snapshots()
            .map { snap ->
                snap.toObjects(Shop::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                if (error is CancellationException && error.cause == null) throw error
                handleFirestoreError(error, OperationType.LIST, "shops")
                throw error
            }
    }

    fun observeActiveCategories(): Flow<List<Category>> {
        val query = db.collection("categories").whereEqualTo("isActive", true)
        return query.snapshots()
            .map { snap ->
                snap.toObjects(Category::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { it.sortOrder }
            }
            .catch { error ->
                if (error is CancellationException && error.cause == null) throw error
                handleFirestoreError(error, OperationType.LIST, "categories")
                throw error
            }
    }

    fun observeVisibleProducts(): Flow<List<Product>> {
        val query = db.collection("products").whereEqualTo("isVisible", true)
        return query.snapshots()
            .map { snap ->
                snap.toObjects(Product::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                if (error is CancellationException && error.cause == null) throw error
                handleFirestoreError(error, OperationType.LIST, "products")
                throw error
            }
    }

    fun observeProductReviews(productId: String): Flow<List<ProductReview>> {
        val query = db.collection("products").document(productId)
            .collection("reviews")
            .whereEqualTo("productId", productId)
        return query.snapshots()
            .map { snap ->
                snap.toObjects(ProductReview::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                if (error is CancellationException && error.cause == null) throw error
                handleFirestoreError(error, OperationType.LIST, "products/$productId/reviews")
                throw error
            }
    }

    fun observeCart(userId: String): Flow<List<CartItem>> {
        val colRef = db.collection("users").document(userId).collection("cart")
        return colRef.snapshots()
            .map { snap ->
                snap.toObjects(CartItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                if (error is CancellationException && error.cause == null) throw error
                handleFirestoreError(error, OperationType.LIST, colRef.path)
                throw error
            }
    }

    fun observeWishlist(userId: String): Flow<List<WishlistItem>> {
        val colRef = db.collection("users").document(userId).collection("wishlist")
        return colRef.snapshots()
            .map { snap ->
                snap.toObjects(WishlistItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                if (error is CancellationException && error.cause == null) throw error
                handleFirestoreError(error, OperationType.LIST, colRef.path)
                throw error
            }
    }

    fun observeRestockAlerts(userId: String): Flow<List<RestockAlert>> {
        val query = db.collection("restock_alerts").whereEqualTo("userId", userId)
        return query.snapshots()
            .map { snap ->
                snap.toObjects(RestockAlert::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                if (error is CancellationException && error.cause == null) throw error
                handleFirestoreError(error, OperationType.LIST, "restock_alerts")
                throw error
            }
    }

    suspend fun toggleRestockAlert(
        product: Product,
        customerName: String,
        currentlySubscribed: Boolean
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val alertId = "${uid}_${product.id}".replace(Regex("[^a-zA-Z0-9_-]"), "_").take(120)
        val docRef = db.collection("restock_alerts").document(alertId)
        try {
            if (currentlySubscribed) {
                docRef.delete().await()
            } else {
                docRef.set(
                    hashMapOf(
                        "userId" to uid,
                        "customerName" to customerName.ifBlank { "Shopper" }.take(100),
                        "productId" to product.id,
                        "productName" to product.name.take(180),
                        "shopId" to product.shopId,
                        "shopOwnerId" to product.shopOwnerId,
                        "status" to "waiting",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    fun observeActiveCoupons(): Flow<List<Coupon>> {
        val query = db.collection("coupons").whereEqualTo("isActive", true)
        return query.snapshots()
            .map { snap ->
                snap.toObjects(Coupon::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                if (error is CancellationException && error.cause == null) throw error
                handleFirestoreError(error, OperationType.LIST, "coupons")
                throw error
            }
    }

    fun observeCustomerOrders(customerId: String): Flow<List<Order>> {
        val query = db.collection("orders")
            .whereEqualTo("customerId", customerId)
        return query.snapshots()
            .map { snap ->
                snap.toObjects(Order::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                if (error is CancellationException && error.cause == null) throw error
                handleFirestoreError(error, OperationType.LIST, "orders")
                throw error
            }
    }

    suspend fun getOrderLineItems(orderId: String, uid: String, isShopSide: Boolean): Result<List<OrderLineItem>> = runCatching {
        val field = if (isShopSide) "shopOwnerId" else "customerId"
        val query = db.collection("orders").document(orderId).collection("items")
            .whereEqualTo(field, uid)
        try {
            query.get().await().toObjects(OrderLineItem::class.java)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "orders/$orderId/items")
            throw e
        }
    }

    suspend fun addToCart(product: Product, variationLabel: String, quantity: Long = 1L): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(product.stockQuantity > 0) { "Product is currently out of stock" }
        val cartDocId = "${product.id}_${variationLabel.replace(Regex("[^a-zA-Z0-9_-]"), "_")}".take(120)
        val docRef = db.collection("users").document(uid).collection("cart").document(cartDocId)
        try {
            val snap = docRef.get().await()
            if (snap.exists()) {
                val existingQty = snap.getLong("quantity") ?: 1L
                val newQty = (existingQty + quantity).coerceAtMost(product.stockQuantity)
                docRef.update(
                    mapOf(
                        "quantity" to newQty,
                        "unitPrice" to product.sellingPrice,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                val payload = hashMapOf<String, Any>(
                    "userId" to uid,
                    "productId" to product.id,
                    "shopId" to product.shopId,
                    "shopOwnerId" to product.shopOwnerId,
                    "shopName" to product.shopName,
                    "productName" to product.name,
                    "imageKey" to (product.imageKeys.firstOrNull() ?: "smartphone"),
                    "variationLabel" to variationLabel,
                    "unitPrice" to product.sellingPrice,
                    "costPrice" to product.costPrice,
                    "quantity" to quantity.coerceAtMost(product.stockQuantity),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun updateCartQuantity(cartItemId: String, newQty: Long): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("cart").document(cartItemId)
        try {
            if (newQty <= 0L) {
                docRef.delete().await()
            } else {
                docRef.update(
                    mapOf(
                        "quantity" to newQty.coerceAtMost(999L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun removeFromCart(cartItemId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("cart").document(cartItemId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    suspend fun toggleWishlist(product: Product, currentlySaved: Boolean): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("wishlist").document(product.id)
        try {
            if (currentlySaved) {
                docRef.delete().await()
            } else {
                val payload = hashMapOf<String, Any>(
                    "userId" to uid,
                    "productId" to product.id,
                    "shopId" to product.shopId,
                    "productName" to product.name,
                    "imageKey" to (product.imageKeys.firstOrNull() ?: "smartphone"),
                    "price" to product.sellingPrice,
                    "originalPrice" to product.originalPrice,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun submitReview(product: Product, reviewerName: String, rating: Long, comment: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val reviewId = "rev_${UUID.randomUUID().toString().take(8)}"
        val reviewRef = db.collection("products").document(product.id).collection("reviews").document(reviewId)
        val prodRef = db.collection("products").document(product.id)
        try {
            db.runTransaction { tx ->
                val prodSnap = tx.get(prodRef)
                val curRating = prodSnap.getDouble("rating") ?: 5.0
                val curCount = prodSnap.getLong("reviewCount") ?: 0L
                val newCount = curCount + 1L
                val newAvg = ((curRating * curCount) + rating.toDouble()) / newCount.toDouble()

                tx.set(
                    reviewRef,
                    hashMapOf(
                        "productId" to product.id,
                        "reviewerId" to uid,
                        "reviewerName" to reviewerName.ifBlank { "Verified Shopper" },
                        "rating" to rating.coerceIn(1L, 5L),
                        "comment" to comment.trim().take(600),
                        "verifiedPurchase" to true,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
                tx.update(
                    prodRef,
                    mapOf(
                        "rating" to newAvg,
                        "reviewCount" to newCount,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, reviewRef.path)
            throw e
        }
    }

    /**
     * Atomic multi-vendor checkout: groups cart items per shop, verifies stock in a Firestore transaction
     * to prevent overselling, deducts inventory, creates Order + OrderLineItems, clears cart, and creates
     * order notifications without marking paymentStatus as Paid until verified!
     */
    suspend fun placeOnlineOrder(
        items: List<CartItem>,
        customerName: String,
        customerPhone: String,
        customerEmail: String,
        deliveryAddress: String,
        deliveryOption: String,
        paymentMethod: String,
        paymentReference: String?,
        coupon: Coupon?,
        shippingFeePerShop: Double,
        taxRatePercent: Double,
        pointsToRedeem: Long = 0L
    ): Result<List<Order>> = runCatching {
        val uid = requireUserId()
        require(items.isNotEmpty()) { "Cart cannot be empty" }

        val groupedByShop = items.groupBy { it.shopId }
        val createdOrders = mutableListOf<Order>()
        val dateStamp = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

        try {
            db.runTransaction { tx ->
                // 1. Read all products first (Firestore Transaction invariant: all reads before writes)
                val productSnaps = items.associate { cartItem ->
                    val pRef = db.collection("products").document(cartItem.productId)
                    cartItem.productId to tx.get(pRef)
                }

                val userRef = db.collection("users").document(uid)
                val userSnap = tx.get(userRef)

                val couponRef = coupon?.id?.takeIf { it.isNotBlank() }?.let { db.collection("coupons").document(it) }
                val couponSnap = couponRef?.let { tx.get(it) }

                // Validate stock for every line item
                for (cartItem in items) {
                    val pSnap = productSnaps[cartItem.productId]
                        ?: throw IllegalStateException("Product ${cartItem.productName} not found")
                    val currentStock = pSnap.getLong("stockQuantity") ?: 0L
                    if (currentStock < cartItem.quantity) {
                        throw IllegalStateException("Insufficient stock for ${cartItem.productName} (Available: $currentStock)")
                    }
                }

                val availablePts = if (userSnap.exists()) {
                    (userSnap.getLong("loyaltyPoints") ?: 0L).coerceAtLeast(0L)
                } else 0L
                var remainingRedeemablePts = pointsToRedeem.coerceIn(0L, availablePts)
                var actualRedeemedPtsTotal = 0L

                var totalOrderSpend = 0.0
                var orderCountDelta = 0L

                // 2. Write orders per shop
                for ((shopId, shopItems) in groupedByShop) {
                    val orderId = "ord_${UUID.randomUUID().toString().take(10)}"
                    val orderNumber = "NXG-$dateStamp-${UUID.randomUUID().toString().take(4).uppercase(Locale.US)}"
                    val first = shopItems.first()
                    val subtotal = shopItems.sumOf { it.unitPrice * it.quantity }
                    val cogs = shopItems.sumOf { it.costPrice * it.quantity }
                    val couponDiscount = if (coupon != null && subtotal >= coupon.minOrderAmount) {
                        if (coupon.discountType == "percent") subtotal * (coupon.discountValue / 100.0)
                        else coupon.discountValue.coerceAtMost(subtotal)
                    } else 0.0
                    val maxLoyaltyDiscount = (subtotal * 0.5).coerceAtMost((subtotal - couponDiscount).coerceAtLeast(0.0))
                    val shopRedeemedPts = remainingRedeemablePts.coerceAtMost(maxLoyaltyDiscount.toLong().coerceAtLeast(0L))
                    remainingRedeemablePts -= shopRedeemedPts
                    actualRedeemedPtsTotal += shopRedeemedPts
                    val loyaltyDiscount = shopRedeemedPts.toDouble()
                    val discount = couponDiscount + loyaltyDiscount
                    val taxable = (subtotal - discount).coerceAtLeast(0.0)
                    val tax = taxable * (taxRatePercent / 100.0)
                    val shipping = if (deliveryOption.contains("Pickup", ignoreCase = true)) 0.0 else shippingFeePerShop
                    val total = taxable + tax + shipping
                    totalOrderSpend += total
                    orderCountDelta += 1L

                    val initialPaymentStatus = if (!paymentReference.isNullOrBlank()) "Pending Verification" else "Unpaid"
                    val initialStatus = if (paymentMethod == "Cash on Delivery") "Pending" else "Awaiting Payment"

                    val orderRef = db.collection("orders").document(orderId)
                    val orderPayload = hashMapOf<String, Any>(
                        "orderNumber" to orderNumber,
                        "customerId" to uid,
                        "shopId" to shopId,
                        "shopOwnerId" to first.shopOwnerId,
                        "shopName" to first.shopName,
                        "customerName" to customerName.ifBlank { "Customer" },
                        "customerPhone" to customerPhone,
                        "customerEmail" to customerEmail,
                        "deliveryAddress" to deliveryAddress,
                        "deliveryOption" to deliveryOption,
                        "itemCount" to shopItems.sumOf { it.quantity },
                        "firstItemName" to first.productName,
                        "firstItemImage" to first.imageKey,
                        "subtotal" to subtotal,
                        "discountAmount" to discount,
                        "loyaltyPointsRedeemed" to shopRedeemedPts,
                        "loyaltyDiscountAmount" to loyaltyDiscount,
                        "shippingFee" to shipping,
                        "taxAmount" to tax,
                        "totalAmount" to total,
                        "cogsTotal" to cogs,
                        "paymentMethod" to paymentMethod,
                        "paymentStatus" to initialPaymentStatus,
                        "status" to initialStatus,
                        "trackingNote" to "Order placed. Awaiting payment verification / processing.",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    if (coupon != null && couponDiscount > 0) orderPayload["couponCode"] = coupon.code
                    if (!paymentReference.isNullOrBlank()) orderPayload["paymentReference"] = paymentReference.trim()

                    tx.set(orderRef, orderPayload)

                    // Order line items
                    shopItems.forEachIndexed { idx, line ->
                        val itemDocRef = orderRef.collection("items").document("line_$idx")
                        tx.set(
                            itemDocRef,
                            hashMapOf(
                                "orderId" to orderId,
                                "customerId" to uid,
                                "shopOwnerId" to first.shopOwnerId,
                                "productId" to line.productId,
                                "productName" to line.productName,
                                "variationLabel" to line.variationLabel,
                                "imageKey" to line.imageKey,
                                "unitPrice" to line.unitPrice,
                                "costPrice" to line.costPrice,
                                "quantity" to line.quantity,
                                "lineTotal" to (line.unitPrice * line.quantity)
                            )
                        )
                    }

                    // Notify shop owner
                    val ownerNotifId = "notif_${UUID.randomUUID().toString().take(8)}"
                    tx.set(
                        db.collection("users").document(first.shopOwnerId).collection("notifications").document(ownerNotifId),
                        hashMapOf(
                            "userId" to first.shopOwnerId,
                            "title" to "New Order $orderNumber",
                            "body" to "$customerName placed an order worth ${String.format(Locale.US, "%.2f", total)} ($paymentMethod)",
                            "type" to "order",
                            "referenceId" to orderId,
                            "targetRole" to "admin",
                            "isRead" to false,
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                    )

                    createdOrders.add(
                        Order(
                            id = orderId,
                            orderNumber = orderNumber,
                            customerId = uid,
                            shopId = shopId,
                            shopOwnerId = first.shopOwnerId,
                            shopName = first.shopName,
                            customerName = customerName,
                            customerPhone = customerPhone,
                            customerEmail = customerEmail,
                            deliveryAddress = deliveryAddress,
                            deliveryOption = deliveryOption,
                            itemCount = shopItems.sumOf { it.quantity },
                            firstItemName = first.productName,
                            firstItemImage = first.imageKey,
                            subtotal = subtotal,
                            discountAmount = discount,
                            loyaltyPointsRedeemed = shopRedeemedPts,
                            loyaltyDiscountAmount = loyaltyDiscount,
                            couponCode = coupon?.code,
                            shippingFee = shipping,
                            taxAmount = tax,
                            totalAmount = total,
                            cogsTotal = cogs,
                            paymentMethod = paymentMethod,
                            paymentStatus = initialPaymentStatus,
                            paymentReference = paymentReference,
                            status = initialStatus
                        )
                    )
                }

                // 3. Deduct stock & increment soldCount for each product
                val qtyByProduct = items.groupBy { it.productId }.mapValues { entry -> entry.value.sumOf { it.quantity } }
                for ((productId, qty) in qtyByProduct) {
                    val pSnap = productSnaps[productId]!!
                    val pRef = db.collection("products").document(productId)
                    val curStock = pSnap.getLong("stockQuantity") ?: 0L
                    val curSold = pSnap.getLong("soldCount") ?: 0L
                    tx.update(
                        pRef,
                        mapOf(
                            "stockQuantity" to (curStock - qty).coerceAtLeast(0L),
                            "soldCount" to (curSold + qty),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }

                // 4. Increment coupon usage if applied
                if (couponRef != null && couponSnap != null && couponSnap.exists()) {
                    val curUsage = couponSnap.getLong("usageCount") ?: 0L
                    tx.update(
                        couponRef,
                        mapOf(
                            "usageCount" to (curUsage + 1L),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }

                // 5. Clear cart items
                items.forEach { cartItem ->
                    if (cartItem.id.isNotBlank()) {
                        tx.delete(db.collection("users").document(uid).collection("cart").document(cartItem.id))
                    }
                }

                // 6. Update user loyalty points & order counters if profile exists
                if (userSnap.exists()) {
                    val curPts = userSnap.getLong("loyaltyPoints") ?: 0L
                    val curSpent = userSnap.getDouble("totalSpent") ?: 0.0
                    val curOrders = userSnap.getLong("orderCount") ?: 0L
                    val earnedPts = (totalOrderSpend / 100.0).toLong().coerceAtLeast(5L)
                    val newPts = (curPts - actualRedeemedPtsTotal + earnedPts).coerceAtLeast(0L)
                    val newTier = when {
                        newPts >= 5000L -> "Platinum"
                        newPts >= 2000L -> "Gold"
                        newPts >= 500L -> "Silver"
                        else -> "Bronze"
                    }
                    tx.update(
                        userRef,
                        mapOf(
                            "loyaltyPoints" to newPts,
                            "loyaltyTier" to newTier,
                            "totalSpent" to (curSpent + totalOrderSpend),
                            "orderCount" to (curOrders + orderCountDelta),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }

                // Customer notification
                val custNotifId = "notif_${UUID.randomUUID().toString().take(8)}"
                tx.set(
                    db.collection("users").document(uid).collection("notifications").document(custNotifId),
                    hashMapOf(
                        "userId" to uid,
                        "title" to "Order Confirmed",
                        "body" to "Placed ${createdOrders.size} order(s): ${createdOrders.joinToString { it.orderNumber }}",
                        "type" to "order",
                        "targetRole" to "customer",
                        "isRead" to false,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "orders")
            throw e
        }
        createdOrders
    }

    suspend fun submitPaymentReference(orderId: String, referenceCode: String): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("orders").document(orderId)
        try {
            docRef.update(
                mapOf(
                    "paymentReference" to referenceCode.trim().take(120),
                    "paymentStatus" to "Pending Verification",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun cancelOrder(orderId: String, reason: String): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("orders").document(orderId)
        try {
            docRef.update(
                mapOf(
                    "status" to "Cancelled",
                    "trackingNote" to reason.ifBlank { "Cancelled by customer" }.take(240),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun requestOrderReturn(order: Order, reason: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val orderRef = db.collection("orders").document(order.id)
        val refundId = "ref_${UUID.randomUUID().toString().take(8)}"
        val refundRef = db.collection("refunds").document(refundId)
        try {
            db.runBatch { batch ->
                batch.update(
                    orderRef,
                    mapOf(
                        "status" to "Return Requested",
                        "trackingNote" to "Return requested: $reason".take(240),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                batch.set(
                    refundRef,
                    hashMapOf(
                        "shopId" to order.shopId,
                        "shopOwnerId" to order.shopOwnerId,
                        "customerId" to uid,
                        "customerName" to order.customerName,
                        "orderId" to order.id,
                        "orderNumber" to order.orderNumber,
                        "amount" to order.totalAmount,
                        "reason" to reason.trim().take(400),
                        "source" to "Online",
                        "status" to "Requested",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, refundRef.path)
            throw e
        }
    }
}
