package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.OperationType
import com.example.data.handleFirestoreError
import com.example.data.model.AdminRecord
import com.example.data.model.AuditLog
import com.example.data.model.Category
import com.example.data.model.Coupon
import com.example.data.model.CrmCustomer
import com.example.data.model.Expense
import com.example.data.model.GatewayConfig
import com.example.data.model.ManualServiceEntry
import com.example.data.model.Order
import com.example.data.model.PosSale
import com.example.data.model.PosSaleItem
import com.example.data.model.Product
import com.example.data.model.ProductImeiRegistry
import com.example.data.model.PurchaseOrder
import com.example.data.model.RefundRequest
import com.example.data.model.Shift
import com.example.data.model.Shop
import com.example.data.model.ShopAsset
import com.example.data.model.StaffMember
import com.example.data.model.StockMovement
import com.example.data.model.Supplier
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
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

data class PosCartEntry(
    val product: Product,
    val variationLabel: String = "Standard",
    val quantity: Long = 1L
)

class BusinessRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return Firebase.auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    // --- Shop & Receipt Designer ---
    fun observeOwnedShops(ownerId: String): Flow<List<Shop>> {
        val query = db.collection("shops").whereEqualTo("ownerId", ownerId)
        return query.snapshots()
            .map { snap -> snap.toObjects(Shop::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
            .catch { error ->
                if (error is CancellationException && error.cause == null) throw error
                handleFirestoreError(error, OperationType.LIST, "shops")
                throw error
            }
    }

    fun observeAllShopsAdmin(): Flow<List<Shop>> {
        val colRef = db.collection("shops")
        return colRef.snapshots()
            .map { snap -> snap.toObjects(Shop::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
            .catch { error ->
                if (error is CancellationException && error.cause == null) throw error
                handleFirestoreError(error, OperationType.LIST, "shops")
                throw error
            }
    }

    suspend fun createOrUpdateShop(shop: Shop): Result<String> = runCatching {
        val uid = requireUserId()
        val shopId = shop.id.ifBlank { "shop_${UUID.randomUUID().toString().take(8)}" }
        val docRef = db.collection("shops").document(shopId)
        val isNew = shop.id.isBlank()
        val payload = hashMapOf<String, Any>(
            "ownerId" to (shop.ownerId.ifBlank { uid }),
            "shopName" to shop.shopName.trim().ifBlank { "My NexGen Store" },
            "tagline" to shop.tagline.trim(),
            "logoKey" to shop.logoKey,
            "categoryFocus" to shop.categoryFocus,
            "locationLabel" to shop.locationLabel,
            "currencyCode" to shop.currencyCode.ifBlank { "KES" },
            "taxRatePercent" to shop.taxRatePercent.coerceIn(0.0, 50.0),
            "shippingFlatFee" to shop.shippingFlatFee.coerceAtLeast(0.0),
            "freeShippingThreshold" to shop.freeShippingThreshold.coerceAtLeast(0.0),
            "paybillNumber" to shop.paybillNumber.trim(),
            "tillNumber" to shop.tillNumber.trim(),
            "accountReference" to shop.accountReference.trim(),
            "commissionRatePercent" to shop.commissionRatePercent.coerceIn(0.0, 50.0),
            "isApproved" to shop.isApproved,
            "isFeatured" to shop.isFeatured,
            "rating" to shop.rating,
            "reviewCount" to shop.reviewCount,
            "receiptHeaderTitle" to shop.receiptHeaderTitle.trim().ifBlank { shop.shopName.trim().uppercase(Locale.US) },
            "receiptSubtitle" to shop.receiptSubtitle.trim(),
            "receiptAddressLine" to shop.receiptAddressLine.trim(),
            "receiptPhoneLine" to shop.receiptPhoneLine.trim(),
            "receiptTaxPin" to shop.receiptTaxPin.trim(),
            "receiptFooterMessage" to shop.receiptFooterMessage.trim(),
            "receiptShowLogo" to shop.receiptShowLogo,
            "receiptShowBarcode" to shop.receiptShowBarcode,
            "receiptShowCashier" to shop.receiptShowCashier,
            "receiptShowTaxBreakdown" to shop.receiptShowTaxBreakdown,
            "receiptPaperWidthMm" to (if (shop.receiptPaperWidthMm == 58L) 58L else 80L),
            "receiptAccentColorHex" to shop.receiptAccentColorHex,
            "mpesaStkEnabled" to shop.mpesaStkEnabled,
            "createdAt" to (shop.createdAt ?: FieldValue.serverTimestamp()),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            if (isNew) {
                logAudit(shopId, uid, "Shop", "Created shop '${shop.shopName}'", "Shops")
            } else {
                logAudit(shopId, uid, "Shop", "Updated shop & receipt design for '${shop.shopName}'", "Settings")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
        shopId
    }

    suspend fun saveGatewayConfig(shopId: String, config: GatewayConfig): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("shops").document(shopId).collection("gateway_config").document("default")
        val payload = hashMapOf<String, Any>(
            "shopOwnerId" to (config.shopOwnerId.ifBlank { uid }),
            "darajaEnvironment" to config.darajaEnvironment,
            "darajaShortcode" to config.darajaShortcode.trim(),
            "darajaCallbackUrl" to config.darajaCallbackUrl.trim(),
            "darajaConsumerKey" to config.darajaConsumerKey.trim(),
            "darajaConsumerSecret" to config.darajaConsumerSecret.trim(),
            "darajaPasskey" to config.darajaPasskey.trim(),
            "stkEnabled" to config.stkEnabled,
            "cardGatewayMerchantId" to config.cardGatewayMerchantId.trim(),
            "whatsappBusinessNumber" to config.whatsappBusinessNumber.trim(),
            "thermalPrinterName" to config.thermalPrinterName.trim(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            // Reflect public non-secret STK availability on shop record
            runCatching {
                db.collection("shops").document(shopId).update(
                    mapOf(
                        "mpesaStkEnabled" to config.stkEnabled,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            logAudit(shopId, uid, "M-Pesa", "Updated Daraja STK & Payment Gateway config (enabled=${config.stkEnabled})", "Settings")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    fun observeGatewayConfig(shopId: String, ownerId: String, fullStoreAccess: Boolean = false): Flow<GatewayConfig?> {
        val query = if (fullStoreAccess) {
            db.collection("shops").document(shopId).collection("gateway_config")
        } else {
            db.collection("shops").document(shopId).collection("gateway_config")
                .whereEqualTo("shopOwnerId", ownerId)
        }
        return query.snapshots()
            .map { snap ->
                snap.documents.firstOrNull()?.toObject(GatewayConfig::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                if (error is CancellationException && error.cause == null) throw error
                handleFirestoreError(error, OperationType.LIST, "shops/$shopId/gateway_config")
                emit(null)
            }
    }

    // --- Categories ---
    suspend fun saveCategory(category: Category): Result<String> = runCatching {
        val uid = requireUserId()
        val id = category.id.ifBlank { "cat_${UUID.randomUUID().toString().take(8)}" }
        val docRef = db.collection("categories").document(id)
        val payload = hashMapOf<String, Any>(
            "shopOwnerId" to (category.shopOwnerId.ifBlank { uid }),
            "name" to category.name.trim(),
            "parentCategory" to category.parentCategory.trim(),
            "iconName" to category.iconName.ifBlank { "devices" },
            "bannerTag" to category.bannerTag.trim(),
            "sortOrder" to category.sortOrder,
            "isActive" to category.isActive,
            "createdAt" to (category.createdAt ?: FieldValue.serverTimestamp()),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
        id
    }

    // --- Products ---
    fun observeShopProducts(ownerId: String, isSuperAdmin: Boolean): Flow<List<Product>> {
        val query = if (isSuperAdmin) {
            db.collection("products")
        } else {
            db.collection("products").whereEqualTo("shopOwnerId", ownerId)
        }
        return query.snapshots()
            .map { snap -> snap.toObjects(Product::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
            .catch { error ->
                if (error is CancellationException && error.cause == null) throw error
                handleFirestoreError(error, OperationType.LIST, "products")
                throw error
            }
    }

    suspend fun saveProduct(product: Product): Result<String> = runCatching {
        val uid = requireUserId()
        val id = product.id.ifBlank { "prod_${UUID.randomUUID().toString().take(8)}" }
        val docRef = db.collection("products").document(id)
        val discount = if (product.originalPrice > product.sellingPrice && product.originalPrice > 0) {
            (((product.originalPrice - product.sellingPrice) / product.originalPrice) * 100).toLong().coerceIn(0L, 99L)
        } else {
            product.discountPercent.coerceIn(0L, 99L)
        }
        val payload = hashMapOf<String, Any>(
            "shopId" to product.shopId,
            "shopOwnerId" to (product.shopOwnerId.ifBlank { uid }),
            "shopName" to product.shopName,
            "name" to product.name.trim(),
            "description" to product.description.trim().take(1200),
            "category" to product.category.ifBlank { "Electronics" },
            "subcategory" to product.subcategory.ifBlank { "General" },
            "sku" to product.sku.ifBlank { "SKU-${id.takeLast(6).uppercase(Locale.US)}" },
            "barcode" to product.barcode.ifBlank { "600${(100000000..999999999).random()}" },
            "sellingPrice" to product.sellingPrice.coerceAtLeast(0.0),
            "originalPrice" to product.originalPrice.coerceAtLeast(product.sellingPrice),
            "costPrice" to product.costPrice.coerceAtLeast(0.0),
            "discountPercent" to discount,
            "stockQuantity" to product.stockQuantity.coerceAtLeast(0L),
            "minStockLevel" to product.minStockLevel.coerceAtLeast(0L),
            "stockLocation" to product.stockLocation.ifBlank { "Main Warehouse" },
            "colors" to product.colors.take(10),
            "sizes" to product.sizes.take(10),
            "specifications" to product.specifications.take(15),
            "imageKeys" to product.imageKeys.ifEmpty { listOf("smartphone") }.take(6),
            "isTrending" to product.isTrending,
            "isFlashSale" to product.isFlashSale,
            "isNewArrival" to product.isNewArrival,
            "isBestSeller" to product.isBestSeller,
            "isDailyDeal" to product.isDailyDeal,
            "isSponsoredAd" to product.isSponsoredAd,
            "isVisible" to product.isVisible,
            "rating" to product.rating.coerceIn(0.0, 5.0),
            "reviewCount" to product.reviewCount.coerceAtLeast(0L),
            "soldCount" to product.soldCount.coerceAtLeast(0L),
            "createdAt" to (product.createdAt ?: FieldValue.serverTimestamp()),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            logAudit(product.shopId, uid, "Product", "Saved product '${product.name}' (SKU: ${payload["sku"]})", "Products")
            if (product.stockQuantity > 0L && product.isVisible) {
                notifyWaitingCustomersOnRestock(id, product.name, product.shopOwnerId.ifBlank { uid })
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
        id
    }

    suspend fun deleteProduct(product: Product): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("products").document(product.id)
        try {
            docRef.delete().await()
            runCatching { db.collection("product_imeis").document(product.id).delete().await() }
            logAudit(product.shopId, uid, "Product", "Deleted product '${product.name}'", "Products")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- Confidential Phone IMEIs (Admin / Staff Only) ---
    fun observeProductImeis(ownerId: String, hasStoreAccess: Boolean): Flow<Map<String, ProductImeiRegistry>> {
        val query = if (hasStoreAccess) {
            db.collection("product_imeis")
        } else {
            db.collection("product_imeis").whereEqualTo("shopOwnerId", ownerId)
        }
        return query.snapshots()
            .map { snap ->
                snap.toObjects(ProductImeiRegistry::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .associateBy { it.productId.ifBlank { it.id } }
            }
            .catch { error ->
                if (error is CancellationException && error.cause == null) throw error
                emit(emptyMap())
            }
    }

    suspend fun saveProductImeis(
        productId: String,
        shopId: String,
        shopOwnerId: String,
        imeis: List<String>,
        soldImeis: List<String> = emptyList()
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val cleaned = imeis.map { it.trim() }.filter { it.isNotBlank() }.distinct().take(500)
        val cleanedSold = soldImeis.map { it.trim() }.filter { it.isNotBlank() }.distinct().take(500)
        val docRef = db.collection("product_imeis").document(productId)
        if (cleaned.isEmpty() && cleanedSold.isEmpty()) {
            runCatching { docRef.delete().await() }
            return@runCatching
        }
        val payload = hashMapOf<String, Any>(
            "productId" to productId,
            "shopId" to shopId,
            "shopOwnerId" to shopOwnerId.ifBlank { uid },
            "imeis" to cleaned,
            "soldImeis" to cleanedSold,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            logAudit(shopId, uid, "IMEI", "Updated ${cleaned.size} phone IMEI(s) for $productId", "Inventory")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun adjustStock(
        product: Product,
        delta: Long,
        movementType: String,
        fromLocation: String,
        toLocation: String,
        reason: String,
        actorName: String
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val prodRef = db.collection("products").document(product.id)
        val movId = "mov_${UUID.randomUUID().toString().take(8)}"
        val movRef = db.collection("stock_movements").document(movId)
        try {
            var newStock = 0L
            db.runTransaction { tx ->
                val snap = tx.get(prodRef)
                val current = snap.getLong("stockQuantity") ?: 0L
                newStock = (current + delta).coerceAtLeast(0L)
                tx.update(
                    prodRef,
                    mapOf(
                        "stockQuantity" to newStock,
                        "stockLocation" to toLocation.ifBlank { product.stockLocation },
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                tx.set(
                    movRef,
                    hashMapOf(
                        "shopId" to product.shopId,
                        "shopOwnerId" to product.shopOwnerId,
                        "productId" to product.id,
                        "productName" to product.name,
                        "type" to movementType,
                        "quantityDelta" to delta,
                        "fromLocation" to fromLocation,
                        "toLocation" to toLocation,
                        "reason" to reason.take(240),
                        "performedByUid" to uid,
                        "performedByName" to actorName,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()

            if (newStock <= product.minStockLevel) {
                val notifId = "notif_${UUID.randomUUID().toString().take(8)}"
                db.collection("users").document(product.shopOwnerId).collection("notifications").document(notifId)
                    .set(
                        hashMapOf(
                            "userId" to product.shopOwnerId,
                            "title" to if (newStock == 0L) "Out of Stock Alert" else "Low Stock Alert",
                            "body" to "${product.name} (SKU: ${product.sku}) has $newStock remaining (min ${product.minStockLevel}).",
                            "type" to "stock",
                            "referenceId" to product.id,
                            "targetRole" to "admin",
                            "isRead" to false,
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
            } else if (delta > 0L && newStock > 0L) {
                notifyWaitingCustomersOnRestock(product.id, product.name, product.shopOwnerId)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, prodRef.path)
            throw e
        }
    }

    // --- Shop Orders ---
    fun observeShopOrders(ownerId: String, isSuperAdmin: Boolean): Flow<List<Order>> {
        val query = if (isSuperAdmin) {
            db.collection("orders")
        } else {
            db.collection("orders").whereEqualTo("shopOwnerId", ownerId)
        }
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

    suspend fun updateOrderStatusAndPayment(
        order: Order,
        newStatus: String,
        newPaymentStatus: String,
        trackingNote: String?,
        assignedRider: String?
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("orders").document(order.id)
        val updates = mutableMapOf<String, Any>(
            "status" to newStatus,
            "paymentStatus" to newPaymentStatus,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (!trackingNote.isNullOrBlank()) updates["trackingNote"] = trackingNote.take(240)
        if (!assignedRider.isNullOrBlank()) updates["assignedRider"] = assignedRider.take(100)
        try {
            docRef.update(updates).await()
            // Notify customer of status update
            val notifId = "notif_${UUID.randomUUID().toString().take(8)}"
            db.collection("users").document(order.customerId).collection("notifications").document(notifId)
                .set(
                    hashMapOf(
                        "userId" to order.customerId,
                        "title" to "Order ${order.orderNumber} Updated",
                        "body" to "Status: $newStatus • Payment: $newPaymentStatus",
                        "type" to if (newStatus == "Shipped" || newStatus == "Out for Delivery") "shipping" else "order",
                        "referenceId" to order.id,
                        "targetRole" to "customer",
                        "isRead" to false,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            logAudit(order.shopId, uid, "Order", "Updated ${order.orderNumber} -> $newStatus ($newPaymentStatus)", "Orders")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    // --- Integrated POS Terminal ---
    fun observePosSales(ownerId: String, isSuperAdmin: Boolean): Flow<List<PosSale>> {
        val query = if (isSuperAdmin) {
            db.collection("pos_sales")
        } else {
            db.collection("pos_sales").whereEqualTo("shopOwnerId", ownerId)
        }
        return query.snapshots()
            .map { snap ->
                snap.toObjects(PosSale::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                if (error is CancellationException && error.cause == null) throw error
                handleFirestoreError(error, OperationType.LIST, "pos_sales")
                throw error
            }
    }

    suspend fun getPosSaleItems(saleId: String, ownerId: String): Result<List<PosSaleItem>> = runCatching {
        val query = db.collection("pos_sales").document(saleId).collection("items")
            .whereEqualTo("shopOwnerId", ownerId)
        try {
            query.get().await().toObjects(PosSaleItem::class.java)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "pos_sales/$saleId/items")
            throw e
        }
    }

    suspend fun completePosSale(
        shop: Shop,
        cashierName: String,
        shift: Shift?,
        cartEntries: List<PosCartEntry>,
        customer: CrmCustomer?,
        discountAmount: Double,
        paymentMode: String,
        cashPaid: Double,
        mpesaPaid: Double,
        cardPaid: Double,
        creditBalance: Double,
        mpesaCode: String?
    ): Result<PosSale> = runCatching {
        val uid = requireUserId()
        require(cartEntries.isNotEmpty()) { "POS cart is empty" }
        val saleId = "pos_${UUID.randomUUID().toString().take(10)}"
        val dateStamp = SimpleDateFormat("yyMMddHHmm", Locale.US).format(Date())
        val receiptNumber = "RCP-$dateStamp-${UUID.randomUUID().toString().take(3).uppercase(Locale.US)}"
        val saleRef = db.collection("pos_sales").document(saleId)

        val subtotal = cartEntries.sumOf { it.product.sellingPrice * it.quantity }
        val cogsTotal = cartEntries.sumOf { it.product.costPrice * it.quantity }
        val taxable = (subtotal - discountAmount).coerceAtLeast(0.0)
        val taxAmount = taxable * (shop.taxRatePercent / 100.0)
        val totalAmount = taxable + taxAmount

        try {
            db.runTransaction { tx ->
                // 1. Read all product snapshots first
                val productSnaps = cartEntries.associate { entry ->
                    entry.product.id to tx.get(db.collection("products").document(entry.product.id))
                }
                val shiftSnap = shift?.id?.takeIf { it.isNotBlank() }?.let {
                    tx.get(db.collection("shifts").document(it))
                }
                val crmSnap = customer?.id?.takeIf { it.isNotBlank() }?.let {
                    tx.get(db.collection("crm_customers").document(it))
                }

                for (entry in cartEntries) {
                    val pSnap = productSnaps[entry.product.id]
                        ?: throw IllegalStateException("Product ${entry.product.name} missing")
                    val curStock = pSnap.getLong("stockQuantity") ?: 0L
                    if (curStock < entry.quantity) {
                        throw IllegalStateException("Insufficient stock for ${entry.product.name} (Available: $curStock)")
                    }
                }

                // 2. Write POS Sale
                val salePayload = hashMapOf<String, Any>(
                    "receiptNumber" to receiptNumber,
                    "shopId" to shop.id,
                    "shopOwnerId" to shop.ownerId,
                    "cashierUid" to uid,
                    "cashierName" to cashierName,
                    "customerName" to (customer?.name ?: "Walk-in Customer"),
                    "isWalkIn" to (customer == null),
                    "itemsCount" to cartEntries.sumOf { it.quantity },
                    "subtotal" to subtotal,
                    "discountAmount" to discountAmount,
                    "taxAmount" to taxAmount,
                    "totalAmount" to totalAmount,
                    "cogsTotal" to cogsTotal,
                    "paymentMode" to paymentMode,
                    "cashPaid" to cashPaid,
                    "mpesaPaid" to mpesaPaid,
                    "cardPaid" to cardPaid,
                    "creditBalance" to creditBalance,
                    "status" to "Completed",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (shift != null && shift.id.isNotBlank()) salePayload["shiftId"] = shift.id
                if (customer != null) {
                    salePayload["customerId"] = customer.id
                    if (!customer.phone.isNullOrBlank()) salePayload["customerPhone"] = customer.phone
                }
                if (!mpesaCode.isNullOrBlank()) salePayload["mpesaCode"] = mpesaCode.trim()

                tx.set(saleRef, salePayload)

                // 3. Write line items + stock movements + deduct stock
                cartEntries.forEachIndexed { idx, entry ->
                    val itemRef = saleRef.collection("items").document("item_$idx")
                    tx.set(
                        itemRef,
                        hashMapOf(
                            "saleId" to saleId,
                            "shopOwnerId" to shop.ownerId,
                            "cashierUid" to uid,
                            "productId" to entry.product.id,
                            "productName" to entry.product.name,
                            "variationLabel" to entry.variationLabel,
                            "unitPrice" to entry.product.sellingPrice,
                            "costPrice" to entry.product.costPrice,
                            "quantity" to entry.quantity,
                            "lineTotal" to (entry.product.sellingPrice * entry.quantity)
                        )
                    )

                    val pSnap = productSnaps[entry.product.id]!!
                    val curStock = pSnap.getLong("stockQuantity") ?: 0L
                    val curSold = pSnap.getLong("soldCount") ?: 0L
                    val newStock = (curStock - entry.quantity).coerceAtLeast(0L)
                    tx.update(
                        db.collection("products").document(entry.product.id),
                        mapOf(
                            "stockQuantity" to newStock,
                            "soldCount" to (curSold + entry.quantity),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )

                    val movId = "mov_${UUID.randomUUID().toString().take(8)}"
                    tx.set(
                        db.collection("stock_movements").document(movId),
                        hashMapOf(
                            "shopId" to shop.id,
                            "shopOwnerId" to shop.ownerId,
                            "productId" to entry.product.id,
                            "productName" to entry.product.name,
                            "type" to "POS",
                            "quantityDelta" to -entry.quantity,
                            "fromLocation" to entry.product.stockLocation,
                            "toLocation" to "POS Sale $receiptNumber",
                            "reason" to "POS Sale $receiptNumber",
                            "performedByUid" to uid,
                            "performedByName" to cashierName,
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                    )
                }

                // 4. Update Shift cash drawer
                if (shiftSnap != null && shiftSnap.exists()) {
                    val curExpected = shiftSnap.getDouble("expectedCash") ?: 0.0
                    val curSales = shiftSnap.getDouble("totalSales") ?: 0.0
                    tx.update(
                        db.collection("shifts").document(shift.id),
                        mapOf(
                            "expectedCash" to (curExpected + cashPaid),
                            "totalSales" to (curSales + totalAmount),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }

                // 5. Update CRM customer stats if selected
                if (crmSnap != null && crmSnap.exists() && customer != null) {
                    val curOrders = crmSnap.getLong("ordersCount") ?: 0L
                    val curSpent = crmSnap.getDouble("totalSpent") ?: 0.0
                    val curDebt = crmSnap.getDouble("outstandingBalance") ?: 0.0
                    val curPts = crmSnap.getLong("loyaltyPoints") ?: 0L
                    tx.update(
                        db.collection("crm_customers").document(customer.id),
                        mapOf(
                            "ordersCount" to (curOrders + 1L),
                            "totalSpent" to (curSpent + totalAmount),
                            "outstandingBalance" to (curDebt + creditBalance),
                            "loyaltyPoints" to (curPts + (totalAmount / 100.0).toLong().coerceAtLeast(1L)),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }.await()
            logAudit(shop.id, uid, "POS", "Completed POS Sale $receiptNumber (${totalAmount})", "POS")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, saleRef.path)
            throw e
        }

        PosSale(
            id = saleId,
            receiptNumber = receiptNumber,
            shopId = shop.id,
            shopOwnerId = shop.ownerId,
            cashierUid = uid,
            cashierName = cashierName,
            shiftId = shift?.id,
            customerId = customer?.id,
            customerName = customer?.name ?: "Walk-in Customer",
            customerPhone = customer?.phone,
            isWalkIn = customer == null,
            itemsCount = cartEntries.sumOf { it.quantity },
            subtotal = subtotal,
            discountAmount = discountAmount,
            taxAmount = taxAmount,
            totalAmount = totalAmount,
            cogsTotal = cogsTotal,
            paymentMode = paymentMode,
            cashPaid = cashPaid,
            mpesaPaid = mpesaPaid,
            cardPaid = cardPaid,
            creditBalance = creditBalance,
            mpesaCode = mpesaCode,
            status = "Completed"
        )
    }

    suspend fun refundPosSale(sale: PosSale): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("pos_sales").document(sale.id)
        try {
            docRef.update(
                mapOf(
                    "status" to "Refunded",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            logAudit(sale.shopId, uid, "POS", "Refunded POS Sale ${sale.receiptNumber}", "POS")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    // --- Staff & Shifts ---
    fun observeStaff(ownerId: String, isSuperAdmin: Boolean): Flow<List<StaffMember>> {
        val query = if (isSuperAdmin) db.collection("staff") else db.collection("staff").whereEqualTo("shopOwnerId", ownerId)
        return query.snapshots()
            .map { snap -> snap.toObjects(StaffMember::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
            .catch { error ->
                if (error is CancellationException && error.cause == null) throw error
                handleFirestoreError(error, OperationType.LIST, "staff")
                throw error
            }
    }

    suspend fun saveStaffMember(member: StaffMember): Result<Unit> = runCatching {
        val uid = requireUserId()
        val id = member.id.ifBlank { "stf_${UUID.randomUUID().toString().take(8)}" }
        val emailLower = member.staffEmail.trim().lowercase(Locale.US)
        val docRef = db.collection("staff").document(id)
        val grantRef = db.collection("staff_grants").document(emailLower)
        val payload = hashMapOf<String, Any>(
            "shopId" to member.shopId,
            "shopOwnerId" to (member.shopOwnerId.ifBlank { uid }),
            "staffEmail" to emailLower,
            "staffName" to member.staffName.trim(),
            "roleTitle" to member.roleTitle.trim().ifBlank { "Salesperson" },
            "permissions" to member.permissions.take(20),
            "isActive" to member.isActive,
            "createdAt" to (member.createdAt ?: FieldValue.serverTimestamp()),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (!member.staffUid.isNullOrBlank()) payload["staffUid"] = member.staffUid
        try {
            db.runBatch { batch ->
                batch.set(docRef, payload)
                batch.set(grantRef, payload)
            }.await()
            logAudit(member.shopId, uid, "Staff", "Saved staff member ${member.staffName} (${member.roleTitle})", "Staff")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun deleteStaffMember(member: StaffMember): Result<Unit> = runCatching {
        val uid = requireUserId()
        val emailLower = member.staffEmail.trim().lowercase(Locale.US)
        try {
            db.runBatch { batch ->
                if (member.id.isNotBlank()) batch.delete(db.collection("staff").document(member.id))
                if (emailLower.isNotBlank()) batch.delete(db.collection("staff_grants").document(emailLower))
            }.await()
            logAudit(member.shopId, uid, "Staff", "Removed staff member ${member.staffName}", "Staff")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "staff/${member.id}")
            throw e
        }
    }

    fun observeShifts(ownerId: String, isSuperAdmin: Boolean): Flow<List<Shift>> {
        val query = if (isSuperAdmin) db.collection("shifts") else db.collection("shifts").whereEqualTo("shopOwnerId", ownerId)
        return query.snapshots()
            .map { snap ->
                snap.toObjects(Shift::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                if (error is CancellationException && error.cause == null) throw error
                handleFirestoreError(error, OperationType.LIST, "shifts")
                throw error
            }
    }

    suspend fun openShift(shopId: String, shopOwnerId: String, staffName: String, openingCash: Double): Result<String> = runCatching {
        val uid = requireUserId()
        val id = "shf_${UUID.randomUUID().toString().take(8)}"
        val docRef = db.collection("shifts").document(id)
        try {
            docRef.set(
                hashMapOf(
                    "shopId" to shopId,
                    "shopOwnerId" to shopOwnerId,
                    "staffUid" to uid,
                    "staffName" to staffName,
                    "openingCash" to openingCash,
                    "expectedCash" to openingCash,
                    "countedCash" to 0.0,
                    "variance" to 0.0,
                    "totalSales" to 0.0,
                    "status" to "Open",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            logAudit(shopId, uid, "Shift", "Opened shift with cash $openingCash", "Staff")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
        id
    }

    suspend fun closeShift(shift: Shift, countedCash: Double, notes: String?): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("shifts").document(shift.id)
        val variance = countedCash - shift.expectedCash
        val updates = mutableMapOf<String, Any>(
            "countedCash" to countedCash,
            "variance" to variance,
            "status" to "Closed",
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (!notes.isNullOrBlank()) updates["notes"] = notes.take(300)
        try {
            docRef.update(updates).await()
            logAudit(shift.shopId, uid, "Shift", "Closed shift (Expected: ${shift.expectedCash}, Counted: $countedCash, Variance: $variance)", "Staff")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    // --- Suppliers & Purchases ---
    fun observeSuppliers(ownerId: String, isSuperAdmin: Boolean): Flow<List<Supplier>> {
        val query = if (isSuperAdmin) db.collection("suppliers") else db.collection("suppliers").whereEqualTo("shopOwnerId", ownerId)
        return query.snapshots()
            .map { snap -> snap.toObjects(Supplier::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
            .catch { error ->
                if (error is CancellationException && error.cause == null) throw error
                handleFirestoreError(error, OperationType.LIST, "suppliers")
                throw error
            }
    }

    suspend fun saveSupplier(supplier: Supplier): Result<Unit> = runCatching {
        val uid = requireUserId()
        val id = supplier.id.ifBlank { "sup_${UUID.randomUUID().toString().take(8)}" }
        val docRef = db.collection("suppliers").document(id)
        val payload = hashMapOf<String, Any>(
            "shopId" to supplier.shopId,
            "shopOwnerId" to (supplier.shopOwnerId.ifBlank { uid }),
            "name" to supplier.name.trim(),
            "totalPurchased" to supplier.totalPurchased,
            "balanceOwed" to supplier.balanceOwed,
            "createdAt" to (supplier.createdAt ?: FieldValue.serverTimestamp()),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (!supplier.contactPerson.isNullOrBlank()) payload["contactPerson"] = supplier.contactPerson.trim()
        if (!supplier.phone.isNullOrBlank()) payload["phone"] = supplier.phone.trim()
        if (!supplier.email.isNullOrBlank()) payload["email"] = supplier.email.trim()
        if (!supplier.address.isNullOrBlank()) payload["address"] = supplier.address.trim()
        try {
            docRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    fun observePurchases(ownerId: String, isSuperAdmin: Boolean): Flow<List<PurchaseOrder>> {
        val query = if (isSuperAdmin) db.collection("purchases") else db.collection("purchases").whereEqualTo("shopOwnerId", ownerId)
        return query.snapshots()
            .map { snap ->
                snap.toObjects(PurchaseOrder::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                if (error is CancellationException && error.cause == null) throw error
                handleFirestoreError(error, OperationType.LIST, "purchases")
                throw error
            }
    }

    suspend fun createPurchaseOrderAndReceive(
        shop: Shop,
        supplier: Supplier,
        product: Product,
        quantity: Long,
        unitCost: Double,
        amountPaid: Double,
        receiveImmediately: Boolean,
        actorName: String
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val poId = "po_${UUID.randomUUID().toString().take(8)}"
        val poNumber = "PO-${UUID.randomUUID().toString().take(5).uppercase(Locale.US)}"
        val grnNumber = if (receiveImmediately) "GRN-${UUID.randomUUID().toString().take(5).uppercase(Locale.US)}" else null
        val totalCost = quantity * unitCost
        val status = when {
            amountPaid >= totalCost && receiveImmediately -> "Paid"
            receiveImmediately -> "Received"
            else -> "Ordered"
        }

        try {
            db.runTransaction { tx ->
                val prodRef = db.collection("products").document(product.id)
                val prodSnap = tx.get(prodRef)
                val supRef = db.collection("suppliers").document(supplier.id)
                val supSnap = tx.get(supRef)

                val poPayload = hashMapOf<String, Any>(
                    "shopId" to shop.id,
                    "shopOwnerId" to shop.ownerId,
                    "supplierId" to supplier.id,
                    "supplierName" to supplier.name,
                    "poNumber" to poNumber,
                    "productId" to product.id,
                    "productName" to product.name,
                    "quantity" to quantity,
                    "unitCost" to unitCost,
                    "totalCost" to totalCost,
                    "amountPaid" to amountPaid,
                    "status" to status,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (grnNumber != null) poPayload["grnNumber"] = grnNumber
                tx.set(db.collection("purchases").document(poId), poPayload)

                if (receiveImmediately && prodSnap.exists()) {
                    val curStock = prodSnap.getLong("stockQuantity") ?: 0L
                    tx.update(
                        prodRef,
                        mapOf(
                            "stockQuantity" to (curStock + quantity),
                            "costPrice" to unitCost,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    val movId = "mov_${UUID.randomUUID().toString().take(8)}"
                    tx.set(
                        db.collection("stock_movements").document(movId),
                        hashMapOf(
                            "shopId" to shop.id,
                            "shopOwnerId" to shop.ownerId,
                            "productId" to product.id,
                            "productName" to product.name,
                            "type" to "Restock",
                            "quantityDelta" to quantity,
                            "fromLocation" to supplier.name,
                            "toLocation" to product.stockLocation,
                            "reason" to "GRN $grnNumber ($poNumber)",
                            "performedByUid" to uid,
                            "performedByName" to actorName,
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                    )
                }

                if (supSnap.exists()) {
                    val curTotal = supSnap.getDouble("totalPurchased") ?: 0.0
                    val curOwed = supSnap.getDouble("balanceOwed") ?: 0.0
                    val newOwed = (curOwed + (totalCost - amountPaid)).coerceAtLeast(0.0)
                    tx.update(
                        supRef,
                        mapOf(
                            "totalPurchased" to (curTotal + totalCost),
                            "balanceOwed" to newOwed,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }.await()
            logAudit(shop.id, uid, "Purchases", "Created $poNumber for $quantity x ${product.name}", "Inventory")
            if (receiveImmediately && quantity > 0L) {
                notifyWaitingCustomersOnRestock(product.id, product.name, shop.ownerId)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "purchases/$poId")
            throw e
        }
    }

    // --- Stock Movements, Expenses, Coupons, CRM, Refunds, Admins, Audit Logs ---
    fun observeStockMovements(ownerId: String, isSuperAdmin: Boolean): Flow<List<StockMovement>> {
        val query = if (isSuperAdmin) db.collection("stock_movements") else db.collection("stock_movements").whereEqualTo("shopOwnerId", ownerId)
        return query.snapshots()
            .map { snap ->
                snap.toObjects(StockMovement::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                if (error is CancellationException && error.cause == null) throw error
                handleFirestoreError(error, OperationType.LIST, "stock_movements")
                throw error
            }
    }

    fun observeExpenses(ownerId: String, isSuperAdmin: Boolean): Flow<List<Expense>> {
        val query = if (isSuperAdmin) db.collection("expenses") else db.collection("expenses").whereEqualTo("shopOwnerId", ownerId)
        return query.snapshots()
            .map { snap ->
                snap.toObjects(Expense::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                if (error is CancellationException && error.cause == null) throw error
                handleFirestoreError(error, OperationType.LIST, "expenses")
                throw error
            }
    }

    suspend fun recordExpense(expense: Expense): Result<Unit> = runCatching {
        val uid = requireUserId()
        val id = expense.id.ifBlank { "exp_${UUID.randomUUID().toString().take(8)}" }
        val docRef = db.collection("expenses").document(id)
        val payload = hashMapOf<String, Any>(
            "shopId" to expense.shopId,
            "shopOwnerId" to (expense.shopOwnerId.ifBlank { uid }),
            "title" to expense.title.trim(),
            "category" to expense.category,
            "amount" to expense.amount,
            "recordedByUid" to uid,
            "createdAt" to (expense.createdAt ?: FieldValue.serverTimestamp()),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (!expense.paymentMethod.isNullOrBlank()) payload["paymentMethod"] = expense.paymentMethod
        if (!expense.reference.isNullOrBlank()) payload["reference"] = expense.reference.trim()
        if (!expense.notes.isNullOrBlank()) payload["notes"] = expense.notes.trim()
        try {
            docRef.set(payload).await()
            logAudit(expense.shopId, uid, "Finance", "Recorded expense '${expense.title}' (${expense.amount})", "Expenses")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun saveCoupon(coupon: Coupon): Result<Unit> = runCatching {
        val uid = requireUserId()
        val id = coupon.id.ifBlank { "cpn_${UUID.randomUUID().toString().take(8)}" }
        val docRef = db.collection("coupons").document(id)
        val payload = hashMapOf<String, Any>(
            "shopId" to coupon.shopId,
            "shopOwnerId" to (coupon.shopOwnerId.ifBlank { uid }),
            "code" to coupon.code.trim().uppercase(Locale.US),
            "discountType" to coupon.discountType,
            "discountValue" to coupon.discountValue,
            "minOrderAmount" to coupon.minOrderAmount,
            "isActive" to coupon.isActive,
            "usageCount" to coupon.usageCount,
            "createdAt" to (coupon.createdAt ?: FieldValue.serverTimestamp()),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    fun observeCrmCustomers(ownerId: String, isSuperAdmin: Boolean): Flow<List<CrmCustomer>> {
        val query = if (isSuperAdmin) db.collection("crm_customers") else db.collection("crm_customers").whereEqualTo("shopOwnerId", ownerId)
        return query.snapshots()
            .map { snap -> snap.toObjects(CrmCustomer::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
            .catch { error ->
                if (error is CancellationException && error.cause == null) throw error
                handleFirestoreError(error, OperationType.LIST, "crm_customers")
                throw error
            }
    }

    suspend fun saveCrmCustomer(customer: CrmCustomer): Result<Unit> = runCatching {
        val uid = requireUserId()
        val id = customer.id.ifBlank { "crm_${UUID.randomUUID().toString().take(8)}" }
        val docRef = db.collection("crm_customers").document(id)
        val payload = hashMapOf<String, Any>(
            "shopId" to customer.shopId,
            "shopOwnerId" to (customer.shopOwnerId.ifBlank { uid }),
            "name" to customer.name.trim(),
            "group" to customer.group,
            "ordersCount" to customer.ordersCount,
            "totalSpent" to customer.totalSpent,
            "outstandingBalance" to customer.outstandingBalance,
            "loyaltyPoints" to customer.loyaltyPoints,
            "status" to customer.status,
            "createdAt" to (customer.createdAt ?: FieldValue.serverTimestamp()),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (!customer.customerUid.isNullOrBlank()) payload["customerUid"] = customer.customerUid
        if (!customer.phone.isNullOrBlank()) payload["phone"] = customer.phone.trim()
        if (!customer.email.isNullOrBlank()) payload["email"] = customer.email.trim()
        if (!customer.supportNote.isNullOrBlank()) payload["supportNote"] = customer.supportNote.trim()
        try {
            docRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    fun observeRefunds(ownerId: String, isSuperAdmin: Boolean): Flow<List<RefundRequest>> {
        val query = if (isSuperAdmin) db.collection("refunds") else db.collection("refunds").whereEqualTo("shopOwnerId", ownerId)
        return query.snapshots()
            .map { snap ->
                snap.toObjects(RefundRequest::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                if (error is CancellationException && error.cause == null) throw error
                handleFirestoreError(error, OperationType.LIST, "refunds")
                throw error
            }
    }

    suspend fun processRefund(refund: RefundRequest, newStatus: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val refundRef = db.collection("refunds").document(refund.id)
        val orderRef = db.collection("orders").document(refund.orderId)
        try {
            db.runBatch { batch ->
                batch.update(
                    refundRef,
                    mapOf(
                        "status" to newStatus,
                        "processedByUid" to uid,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                if (newStatus == "Refunded" && refund.source == "Online") {
                    batch.update(
                        orderRef,
                        mapOf(
                            "status" to "Refunded",
                            "paymentStatus" to "Refunded",
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }.await()
            logAudit(refund.shopId, uid, "Refund", "Updated refund for ${refund.orderNumber} -> $newStatus", "Refunds")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, refundRef.path)
            throw e
        }
    }

    fun observeAuditLogs(ownerId: String, isSuperAdmin: Boolean): Flow<List<AuditLog>> {
        val query = if (isSuperAdmin) db.collection("audit_logs") else db.collection("audit_logs").whereEqualTo("shopOwnerId", ownerId)
        return query.snapshots()
            .map { snap ->
                snap.toObjects(AuditLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                if (error is CancellationException && error.cause == null) throw error
                handleFirestoreError(error, OperationType.LIST, "audit_logs")
                throw error
            }
    }

    fun observeAdmins(): Flow<List<AdminRecord>> {
        val colRef = db.collection("admins")
        return colRef.snapshots()
            .map { snap -> snap.toObjects(AdminRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
            .catch { error ->
                if (error is CancellationException && error.cause == null) throw error
                handleFirestoreError(error, OperationType.LIST, "admins")
                emit(emptyList())
            }
    }

    suspend fun saveAdminRecord(record: AdminRecord): Result<Unit> = runCatching {
        val emailLower = record.email.trim().lowercase(Locale.US)
        val docId = record.uid.trim().ifBlank {
            "adm_${emailLower.replace(Regex("[^a-z0-9]"), "_").take(64)}"
        }
        val assignedRole = if (AuthAndUserRepository.SUPER_ADMIN_EMAILS.contains(emailLower)) record.role else "admin"
        val docRef = db.collection("admins").document(docId)
        val grantRef = db.collection("admin_grants").document(emailLower)
        val payload = hashMapOf<String, Any>(
            "uid" to docId,
            "email" to emailLower,
            "displayName" to record.displayName.trim(),
            "role" to assignedRole,
            "permissions" to record.permissions.take(20),
            "isActive" to record.isActive,
            "createdAt" to (record.createdAt ?: FieldValue.serverTimestamp()),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            db.runBatch { batch ->
                batch.set(docRef, payload)
                batch.set(grantRef, payload)
            }.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun approveShopAndCommission(shopId: String, approved: Boolean, featured: Boolean, commissionPercent: Double): Result<Unit> = runCatching {
        val docRef = db.collection("shops").document(shopId)
        try {
            docRef.update(
                mapOf(
                    "isApproved" to approved,
                    "isFeatured" to featured,
                    "commissionRatePercent" to commissionPercent.coerceIn(0.0, 50.0),
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

    // --- Shop Assets (Total Shop Worth & Valuation) ---
    fun observeShopAssets(ownerId: String, isSuperAdmin: Boolean): Flow<List<ShopAsset>> {
        val query = if (isSuperAdmin) db.collection("shop_assets") else db.collection("shop_assets").whereEqualTo("shopOwnerId", ownerId)
        return query.snapshots()
            .map { snap -> snap.toObjects(ShopAsset::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
            .catch { error ->
                if (error is CancellationException && error.cause == null) throw error
                handleFirestoreError(error, OperationType.LIST, "shop_assets")
                throw error
            }
    }

    suspend fun saveShopAsset(asset: ShopAsset): Result<String> = runCatching {
        val uid = requireUserId()
        val id = asset.id.ifBlank { "ast_${UUID.randomUUID().toString().take(8)}" }
        val docRef = db.collection("shop_assets").document(id)
        val payload = hashMapOf<String, Any>(
            "shopId" to asset.shopId,
            "shopOwnerId" to (asset.shopOwnerId.ifBlank { uid }),
            "name" to asset.name.trim(),
            "category" to asset.category.ifBlank { "Equipment" },
            "quantity" to asset.quantity.coerceAtLeast(1L),
            "purchasePrice" to asset.purchasePrice.coerceAtLeast(0.0),
            "currentValue" to asset.currentValue.coerceAtLeast(0.0),
            "condition" to asset.condition.ifBlank { "Good" },
            "createdAt" to (asset.createdAt ?: FieldValue.serverTimestamp()),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (!asset.serialOrTag.isNullOrBlank()) payload["serialOrTag"] = asset.serialOrTag.trim()
        if (!asset.notes.isNullOrBlank()) payload["notes"] = asset.notes.trim()
        try {
            docRef.set(payload).await()
            logAudit(asset.shopId, uid, "Assets", "Saved shop asset '${asset.name}' (${asset.quantity} x ${asset.currentValue})", "Assets")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
        id
    }

    suspend fun deleteShopAsset(asset: ShopAsset): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("shop_assets").document(asset.id)
        try {
            docRef.delete().await()
            logAudit(asset.shopId, uid, "Assets", "Removed shop asset '${asset.name}'", "Assets")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- Daily Manual Service Book (Unlocked when POS Shift is Open) ---
    fun observeManualServices(ownerId: String, hasStoreAccess: Boolean): Flow<List<ManualServiceEntry>> {
        val query = if (hasStoreAccess) {
            db.collection("manual_services")
        } else {
            db.collection("manual_services").whereEqualTo("shopOwnerId", ownerId)
        }
        return query.snapshots()
            .map { snap ->
                snap.toObjects(ManualServiceEntry::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.recordedAtMillis.takeIf { ms -> ms > 0L } ?: ((it.createdAt?.seconds ?: 0L) * 1000L) }
            }
            .catch { error ->
                if (error is CancellationException && error.cause == null) throw error
                handleFirestoreError(error, OperationType.LIST, "manual_services")
                emit(emptyList())
            }
    }

    suspend fun recordManualService(
        shop: Shop,
        openShift: Shift,
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
        notes: String?,
        recordedByName: String
    ): Result<ManualServiceEntry> = runCatching {
        val uid = requireUserId()
        require(openShift.status == "Open") { "POS Shift must be Active / Open to record Manual Services." }
        val nowMillis = System.currentTimeMillis()
        val dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(nowMillis))
        val entryId = "srv_${UUID.randomUUID().toString().take(10)}"
        val entryNumber = "SRV-${SimpleDateFormat("yyMMdd-HHmm", Locale.US).format(Date(nowMillis))}-${UUID.randomUUID().toString().take(2).uppercase(Locale.US)}"
        val docRef = db.collection("manual_services").document(entryId)

        val payload = hashMapOf<String, Any>(
            "entryNumber" to entryNumber,
            "shopId" to shop.id,
            "shopOwnerId" to shop.ownerId,
            "shiftId" to openShift.id,
            "dateKey" to dateKey,
            "recordedAtMillis" to nowMillis,
            "serviceName" to serviceName.trim(),
            "category" to category.ifBlank { "Repair" },
            "deviceModel" to deviceModel.trim(),
            "customerName" to customerName.trim().ifBlank { "Walk-in Customer" },
            "serviceFee" to serviceFee.coerceAtLeast(0.0),
            "partsCost" to partsCost.coerceAtLeast(0.0),
            "paymentMode" to paymentMode.ifBlank { "Cash" },
            "status" to status.ifBlank { "Completed" },
            "recordedByUid" to uid,
            "recordedByName" to recordedByName.ifBlank { "Staff" },
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (!deviceImei.isNullOrBlank()) payload["deviceImei"] = deviceImei.trim()
        if (!customerPhone.isNullOrBlank()) payload["customerPhone"] = customerPhone.trim()
        if (!mpesaCode.isNullOrBlank()) payload["mpesaCode"] = mpesaCode.trim().uppercase(Locale.US)
        if (!notes.isNullOrBlank()) payload["notes"] = notes.trim()

        try {
            docRef.set(payload).await()
            logAudit(shop.id, uid, "Manual Book", "Recorded $entryNumber: $serviceName (${serviceFee})", "ManualBook")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }

        ManualServiceEntry(
            id = entryId,
            entryNumber = entryNumber,
            shopId = shop.id,
            shopOwnerId = shop.ownerId,
            shiftId = openShift.id,
            dateKey = dateKey,
            recordedAtMillis = nowMillis,
            serviceName = serviceName.trim(),
            category = category.ifBlank { "Repair" },
            deviceModel = deviceModel.trim(),
            deviceImei = deviceImei?.trim()?.takeIf { it.isNotBlank() },
            customerName = customerName.trim().ifBlank { "Walk-in Customer" },
            customerPhone = customerPhone?.trim()?.takeIf { it.isNotBlank() },
            serviceFee = serviceFee.coerceAtLeast(0.0),
            partsCost = partsCost.coerceAtLeast(0.0),
            paymentMode = paymentMode.ifBlank { "Cash" },
            mpesaCode = mpesaCode?.trim()?.uppercase(Locale.US)?.takeIf { it.isNotBlank() },
            status = status.ifBlank { "Completed" },
            notes = notes?.trim()?.takeIf { it.isNotBlank() },
            recordedByUid = uid,
            recordedByName = recordedByName
        )
    }

    suspend fun updateManualServiceStatus(entry: ManualServiceEntry, newStatus: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("manual_services").document(entry.id)
        try {
            docRef.update(
                mapOf(
                    "status" to newStatus,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            logAudit(entry.shopId, uid, "Manual Book", "Updated ${entry.entryNumber} -> $newStatus", "ManualBook")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    private suspend fun notifyWaitingCustomersOnRestock(productId: String, productName: String, shopOwnerId: String) {
        try {
            val alertsSnap = db.collection("restock_alerts")
                .whereEqualTo("shopOwnerId", shopOwnerId)
                .whereEqualTo("productId", productId)
                .whereEqualTo("status", "waiting")
                .get()
                .await()
            if (alertsSnap.isEmpty) return

            val batch = db.batch()
            alertsSnap.documents.forEach { doc ->
                val targetUserId = doc.getString("userId") ?: return@forEach
                val notifId = "notif_${UUID.randomUUID().toString().take(8)}"
                val notifRef = db.collection("users").document(targetUserId).collection("notifications").document(notifId)
                batch.set(
                    notifRef,
                    hashMapOf(
                        "userId" to targetUserId,
                        "title" to "Back in Stock: $productName",
                        "body" to "$productName is now available in the shop! Tap to view and order.",
                        "type" to "stock",
                        "referenceId" to productId,
                        "targetRole" to "customer",
                        "isRead" to false,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
                batch.update(
                    doc.reference,
                    mapOf(
                        "status" to "notified",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
        } catch (_: Exception) {
            // Non-blocking notification fan-out
        }
    }

    private suspend fun logAudit(shopId: String, actorUid: String, module: String, action: String, actorRole: String) {
        val logId = "aud_${UUID.randomUUID().toString().take(8)}"
        val email = Firebase.auth.currentUser?.displayName ?: Firebase.auth.currentUser?.email ?: actorUid
        try {
            db.collection("audit_logs").document(logId).set(
                hashMapOf(
                    "shopId" to shopId,
                    "shopOwnerId" to actorUid,
                    "actorUid" to actorUid,
                    "actorName" to email,
                    "actorRole" to actorRole,
                    "action" to action.take(120),
                    "module" to module.take(60),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (_: Exception) {
            // Non-blocking audit logging
        }
    }
}
