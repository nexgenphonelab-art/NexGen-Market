package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String? = null,
    val phone: String? = null,
    val photoUrl: String? = null,
    val role: String = "customer",
    val loyaltyPoints: Long = 0L,
    val loyaltyTier: String = "Bronze",
    val customerGroup: String = "Retail",
    val totalSpent: Double = 0.0,
    val orderCount: Long = 0L,
    val outstandingBalance: Double = 0.0,
    val status: String = "active",
    val defaultAddress: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class SavedAddress(
    @DocumentId val id: String = "",
    val userId: String = "",
    val label: String = "Home",
    val recipientName: String = "",
    val phone: String = "",
    val street: String = "",
    val city: String = "",
    val latitude: Double = -1.286389,
    val longitude: Double = 36.817223,
    val isDefault: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class CartItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val productId: String = "",
    val shopId: String = "",
    val shopOwnerId: String = "",
    val shopName: String = "",
    val productName: String = "",
    val imageKey: String = "smartphone",
    val variationLabel: String = "Standard",
    val unitPrice: Double = 0.0,
    val costPrice: Double = 0.0,
    val quantity: Long = 1L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class WishlistItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val productId: String = "",
    val shopId: String = "",
    val productName: String = "",
    val imageKey: String = "smartphone",
    val price: Double = 0.0,
    val originalPrice: Double = 0.0,
    @ServerTimestamp val createdAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class AppNotification(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val body: String = "",
    val type: String = "system",
    val referenceId: String? = null,
    val targetRole: String? = null,
    val isRead: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class AdminRecord(
    @DocumentId val id: String = "",
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val role: String = "admin",
    val permissions: List<String> = emptyList(),
    val isActive: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class Shop(
    @DocumentId val id: String = "",
    val ownerId: String = "",
    val shopName: String = "NexGen Flagship Store",
    val tagline: String = "Smart Electronics & Universal Retail",
    val logoKey: String = "nexgen_logo",
    val categoryFocus: String = "Electronics",
    val locationLabel: String = "Global & Express Hub",
    val currencyCode: String = "KES",
    val taxRatePercent: Double = 16.0,
    val shippingFlatFee: Double = 250.0,
    val freeShippingThreshold: Double = 5000.0,
    val paybillNumber: String = "247247",
    val tillNumber: String = "512890",
    val accountReference: String = "NEXGEN",
    val commissionRatePercent: Double = 5.0,
    val isApproved: Boolean = true,
    val isFeatured: Boolean = true,
    val rating: Double = 4.9,
    val reviewCount: Long = 0L,
    // Universal Receipt Designer fields
    val receiptHeaderTitle: String = "NEXGEN MARKET STORE",
    val receiptSubtitle: String = "Official Customer Sales Receipt",
    val receiptAddressLine: String = "Tech Plaza, Suite 402",
    val receiptPhoneLine: String = "+254 700 000 000",
    val receiptTaxPin: String = "PIN: P051900001A",
    val receiptFooterMessage: String = "Thank you for shopping with us! Goods once sold are valid for 7-day warranty exchange with receipt.",
    val receiptShowLogo: Boolean = true,
    val receiptShowBarcode: Boolean = true,
    val receiptShowCashier: Boolean = true,
    val receiptShowTaxBreakdown: Boolean = true,
    val receiptPaperWidthMm: Long = 80L,
    val receiptAccentColorHex: String = "#0077FF",
    val mpesaStkEnabled: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class GatewayConfig(
    @DocumentId val id: String = "",
    val shopOwnerId: String = "",
    val darajaEnvironment: String = "sandbox",
    val darajaShortcode: String = "174379",
    val darajaCallbackUrl: String = "https://api.example.com/mpesa/callback",
    val darajaConsumerKey: String = "",
    val darajaConsumerSecret: String = "",
    val darajaPasskey: String = "",
    val stkEnabled: Boolean = false,
    val cardGatewayMerchantId: String = "",
    val whatsappBusinessNumber: String = "",
    val thermalPrinterName: String = "Generic ESC/POS 80mm BT",
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class Category(
    @DocumentId val id: String = "",
    val shopOwnerId: String = "",
    val name: String = "",
    val parentCategory: String = "",
    val iconName: String = "devices",
    val bannerTag: String = "",
    val sortOrder: Long = 0L,
    val isActive: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class Product(
    @DocumentId val id: String = "",
    val shopId: String = "",
    val shopOwnerId: String = "",
    val shopName: String = "",
    val name: String = "",
    val description: String = "",
    val category: String = "Phones & Tablets",
    val subcategory: String = "Smartphones",
    val sku: String = "",
    val barcode: String = "",
    val sellingPrice: Double = 0.0,
    val originalPrice: Double = 0.0,
    val costPrice: Double = 0.0,
    val discountPercent: Long = 0L,
    val stockQuantity: Long = 0L,
    val minStockLevel: Long = 5L,
    val stockLocation: String = "Main Warehouse",
    val colors: List<String> = emptyList(),
    val sizes: List<String> = emptyList(),
    val specifications: List<String> = emptyList(),
    val imageKeys: List<String> = listOf("smartphone"),
    val isTrending: Boolean = false,
    val isFlashSale: Boolean = false,
    val isNewArrival: Boolean = false,
    val isBestSeller: Boolean = false,
    val isDailyDeal: Boolean = false,
    val isSponsoredAd: Boolean = false,
    val isVisible: Boolean = true,
    val rating: Double = 5.0,
    val reviewCount: Long = 0L,
    val soldCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class ProductReview(
    @DocumentId val id: String = "",
    val productId: String = "",
    val reviewerId: String = "",
    val reviewerName: String = "",
    val rating: Long = 5L,
    val comment: String = "",
    val verifiedPurchase: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class Order(
    @DocumentId val id: String = "",
    val orderNumber: String = "",
    val customerId: String = "",
    val shopId: String = "",
    val shopOwnerId: String = "",
    val shopName: String = "",
    val customerName: String = "",
    val customerPhone: String = "",
    val customerEmail: String = "",
    val deliveryAddress: String = "",
    val deliveryOption: String = "Standard Delivery",
    val itemCount: Long = 1L,
    val firstItemName: String = "",
    val firstItemImage: String = "smartphone",
    val subtotal: Double = 0.0,
    val discountAmount: Double = 0.0,
    val couponCode: String? = null,
    val loyaltyPointsRedeemed: Long = 0L,
    val loyaltyDiscountAmount: Double = 0.0,
    val shippingFee: Double = 0.0,
    val taxAmount: Double = 0.0,
    val totalAmount: Double = 0.0,
    val cogsTotal: Double = 0.0,
    val paymentMethod: String = "M-Pesa STK",
    val paymentStatus: String = "Unpaid",
    val paymentReference: String? = null,
    val status: String = "Pending",
    val trackingNote: String? = null,
    val assignedRider: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class OrderLineItem(
    @DocumentId val id: String = "",
    val orderId: String = "",
    val customerId: String = "",
    val shopOwnerId: String = "",
    val productId: String = "",
    val productName: String = "",
    val variationLabel: String = "Standard",
    val imageKey: String = "smartphone",
    val unitPrice: Double = 0.0,
    val costPrice: Double = 0.0,
    val quantity: Long = 1L,
    val lineTotal: Double = 0.0,
)

@IgnoreExtraProperties
data class PosSale(
    @DocumentId val id: String = "",
    val receiptNumber: String = "",
    val shopId: String = "",
    val shopOwnerId: String = "",
    val cashierUid: String = "",
    val cashierName: String = "",
    val shiftId: String? = null,
    val customerId: String? = null,
    val customerName: String = "Walk-in Customer",
    val customerPhone: String? = null,
    val isWalkIn: Boolean = true,
    val itemsCount: Long = 1L,
    val subtotal: Double = 0.0,
    val discountAmount: Double = 0.0,
    val taxAmount: Double = 0.0,
    val totalAmount: Double = 0.0,
    val cogsTotal: Double = 0.0,
    val paymentMode: String = "Cash",
    val cashPaid: Double = 0.0,
    val mpesaPaid: Double = 0.0,
    val cardPaid: Double = 0.0,
    val creditBalance: Double = 0.0,
    val mpesaCode: String? = null,
    val status: String = "Completed",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class PosSaleItem(
    @DocumentId val id: String = "",
    val saleId: String = "",
    val shopOwnerId: String = "",
    val cashierUid: String = "",
    val productId: String = "",
    val productName: String = "",
    val variationLabel: String = "Standard",
    val unitPrice: Double = 0.0,
    val costPrice: Double = 0.0,
    val quantity: Long = 1L,
    val lineTotal: Double = 0.0,
)

@IgnoreExtraProperties
data class StaffMember(
    @DocumentId val id: String = "",
    val shopId: String = "",
    val shopOwnerId: String = "",
    val staffUid: String? = null,
    val staffEmail: String = "",
    val staffName: String = "",
    val roleTitle: String = "Cashier",
    val permissions: List<String> = listOf("POS", "ORDERS"),
    val isActive: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class Shift(
    @DocumentId val id: String = "",
    val shopId: String = "",
    val shopOwnerId: String = "",
    val staffUid: String = "",
    val staffName: String = "",
    val openingCash: Double = 0.0,
    val expectedCash: Double = 0.0,
    val countedCash: Double = 0.0,
    val variance: Double = 0.0,
    val totalSales: Double = 0.0,
    val status: String = "Open",
    val notes: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class Supplier(
    @DocumentId val id: String = "",
    val shopId: String = "",
    val shopOwnerId: String = "",
    val name: String = "",
    val contactPerson: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val address: String? = null,
    val totalPurchased: Double = 0.0,
    val balanceOwed: Double = 0.0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class PurchaseOrder(
    @DocumentId val id: String = "",
    val shopId: String = "",
    val shopOwnerId: String = "",
    val supplierId: String = "",
    val supplierName: String = "",
    val poNumber: String = "",
    val grnNumber: String? = null,
    val productId: String = "",
    val productName: String = "",
    val quantity: Long = 1L,
    val unitCost: Double = 0.0,
    val totalCost: Double = 0.0,
    val amountPaid: Double = 0.0,
    val status: String = "Ordered",
    val notes: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class StockMovement(
    @DocumentId val id: String = "",
    val shopId: String = "",
    val shopOwnerId: String = "",
    val productId: String = "",
    val productName: String = "",
    val type: String = "Adjustment",
    val quantityDelta: Long = 0L,
    val fromLocation: String? = null,
    val toLocation: String? = null,
    val reason: String? = null,
    val performedByUid: String = "",
    val performedByName: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class Expense(
    @DocumentId val id: String = "",
    val shopId: String = "",
    val shopOwnerId: String = "",
    val title: String = "",
    val category: String = "Utilities",
    val amount: Double = 0.0,
    val paymentMethod: String? = "Cash",
    val reference: String? = null,
    val recordedByUid: String = "",
    val notes: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class Coupon(
    @DocumentId val id: String = "",
    val shopId: String = "",
    val shopOwnerId: String = "",
    val code: String = "",
    val discountType: String = "percent",
    val discountValue: Double = 10.0,
    val minOrderAmount: Double = 0.0,
    val isActive: Boolean = true,
    val usageCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class AuditLog(
    @DocumentId val id: String = "",
    val shopId: String = "",
    val shopOwnerId: String = "",
    val actorUid: String = "",
    val actorName: String? = null,
    val actorRole: String? = null,
    val action: String = "",
    val module: String = "",
    val details: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class CrmCustomer(
    @DocumentId val id: String = "",
    val shopId: String = "",
    val shopOwnerId: String = "",
    val customerUid: String? = null,
    val name: String = "",
    val phone: String? = null,
    val email: String? = null,
    val group: String = "Retail",
    val ordersCount: Long = 0L,
    val totalSpent: Double = 0.0,
    val outstandingBalance: Double = 0.0,
    val loyaltyPoints: Long = 0L,
    val status: String = "Active",
    val supportNote: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class RefundRequest(
    @DocumentId val id: String = "",
    val shopId: String = "",
    val shopOwnerId: String = "",
    val customerId: String = "",
    val customerName: String? = null,
    val orderId: String = "",
    val orderNumber: String = "",
    val amount: Double = 0.0,
    val reason: String = "",
    val source: String = "Online",
    val status: String = "Requested",
    val processedByUid: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class RestockAlert(
    @DocumentId val id: String = "",
    val userId: String = "",
    val customerName: String = "",
    val productId: String = "",
    val productName: String = "",
    val shopId: String = "",
    val shopOwnerId: String = "",
    val status: String = "waiting",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class ShopAsset(
    @DocumentId val id: String = "",
    val shopId: String = "",
    val shopOwnerId: String = "",
    val name: String = "",
    val category: String = "Equipment",
    val quantity: Long = 1L,
    val purchasePrice: Double = 0.0,
    val currentValue: Double = 0.0,
    val condition: String = "Good",
    val serialOrTag: String? = null,
    val notes: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class ProductImeiRegistry(
    @DocumentId val id: String = "",
    val productId: String = "",
    val shopId: String = "",
    val shopOwnerId: String = "",
    val imeis: List<String> = emptyList(),
    val soldImeis: List<String> = emptyList(),
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class ManualServiceEntry(
    @DocumentId val id: String = "",
    val entryNumber: String = "",
    val shopId: String = "",
    val shopOwnerId: String = "",
    val shiftId: String = "",
    val dateKey: String = "",
    val recordedAtMillis: Long = 0L,
    val serviceName: String = "",
    val category: String = "Repair",
    val deviceModel: String = "",
    val deviceImei: String? = null,
    val customerName: String = "Walk-in Customer",
    val customerPhone: String? = null,
    val serviceFee: Double = 0.0,
    val partsCost: Double = 0.0,
    val paymentMode: String = "Cash",
    val mpesaCode: String? = null,
    val status: String = "Completed",
    val notes: String? = null,
    val recordedByUid: String = "",
    val recordedByName: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

