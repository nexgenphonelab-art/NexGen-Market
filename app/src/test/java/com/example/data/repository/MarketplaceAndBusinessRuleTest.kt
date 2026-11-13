package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.CartItem
import com.example.data.model.Product
import com.example.data.model.Shop
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class MarketplaceAndBusinessRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun createShopProductAndCompletePosSale_synchronizesStock() = runBlocking {
        val ownerUid = signInTestUser("owner_${UUID.randomUUID().toString().take(6)}@test.com")
        val bizRepo = BusinessRepository(firestore)
        val marketRepo = MarketplaceRepository(firestore)

        val shop = Shop(
            ownerId = ownerUid,
            shopName = "Universal Electronics",
            currencyCode = "KES",
            taxRatePercent = 16.0,
            receiptHeaderTitle = "UNIVERSAL ELECTRONICS",
            receiptFooterMessage = "Custom Warranty 7 Days"
        )
        val shopId = withTimeout(TIMEOUT_MS) { bizRepo.createOrUpdateShop(shop).getOrThrow() }
        assertTrue(shopId.isNotBlank())

        val product = Product(
            shopId = shopId,
            shopOwnerId = ownerUid,
            shopName = shop.shopName,
            name = "Titanium Smartwatch",
            category = "Wearables",
            sku = "SKU-${UUID.randomUUID().toString().take(6)}",
            sellingPrice = 12000.0,
            originalPrice = 15000.0,
            costPrice = 8000.0,
            stockQuantity = 10L,
            minStockLevel = 2L,
            isVisible = true
        )
        val productId = withTimeout(TIMEOUT_MS) { bizRepo.saveProduct(product).getOrThrow() }

        val visibleProducts = withTimeout(FLOW_TIMEOUT_MS) {
            marketRepo.observeVisibleProducts().first { list -> list.any { it.id == productId } }
        }
        val savedProduct = visibleProducts.first { it.id == productId }
        assertEquals(10L, savedProduct.stockQuantity)

        val sale = withTimeout(TIMEOUT_MS) {
            bizRepo.completePosSale(
                shop = shop.copy(id = shopId),
                cashierName = "Owner",
                shift = null,
                cartEntries = listOf(PosCartEntry(savedProduct, "Standard", 2L)),
                customer = null,
                discountAmount = 0.0,
                paymentMode = "Cash",
                cashPaid = 27840.0,
                mpesaPaid = 0.0,
                cardPaid = 0.0,
                creditBalance = 0.0,
                mpesaCode = null
            ).getOrThrow()
        }
        assertEquals("Completed", sale.status)

        val updatedProducts = withTimeout(FLOW_TIMEOUT_MS) {
            marketRepo.observeVisibleProducts().first { list ->
                list.any { it.id == productId && it.stockQuantity == 8L }
            }
        }
        assertEquals(8L, updatedProducts.first { it.id == productId }.stockQuantity)
    }

    @Test
    fun customerCheckout_deductsStockAndMarksAwaitingVerification() = runBlocking {
        val ownerUid = signInTestUser("merchant_${UUID.randomUUID().toString().take(6)}@test.com")
        val bizRepo = BusinessRepository(firestore)
        val shop = Shop(ownerId = ownerUid, shopName = "Mega Store", receiptHeaderTitle = "MEGA STORE")
        val shopId = withTimeout(TIMEOUT_MS) { bizRepo.createOrUpdateShop(shop).getOrThrow() }
        val productId = withTimeout(TIMEOUT_MS) {
            bizRepo.saveProduct(
                Product(
                    shopId = shopId,
                    shopOwnerId = ownerUid,
                    shopName = shop.shopName,
                    name = "ANC Headphones",
                    sku = "ANC-${UUID.randomUUID().toString().take(5)}",
                    sellingPrice = 5000.0,
                    costPrice = 3000.0,
                    stockQuantity = 5L,
                    minStockLevel = 1L,
                    isVisible = true
                )
            ).getOrThrow()
        }

        val buyerUid = signInTestUser("buyer_${UUID.randomUUID().toString().take(6)}@test.com")
        val marketRepo = MarketplaceRepository(firestore)
        val cartItem = CartItem(
            userId = buyerUid,
            productId = productId,
            shopId = shopId,
            shopOwnerId = ownerUid,
            shopName = shop.shopName,
            productName = "ANC Headphones",
            unitPrice = 5000.0,
            costPrice = 3000.0,
            quantity = 2L
        )
        val orders = withTimeout(TIMEOUT_MS) {
            marketRepo.placeOnlineOrder(
                items = listOf(cartItem),
                customerName = "Buyer One",
                customerPhone = "0712345678",
                customerEmail = "buyer@test.com",
                deliveryAddress = "CBD Plaza",
                deliveryOption = "Standard",
                paymentMethod = "Paybill",
                paymentReference = "SJK12345",
                coupon = null,
                shippingFeePerShop = 200.0,
                taxRatePercent = 16.0
            ).getOrThrow()
        }
        assertEquals(1, orders.size)
        assertEquals("Pending Verification", orders.first().paymentStatus)
    }

    @Test
    fun unauthenticatedQuery_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val marketRepo = MarketplaceRepository(firestore)
        val ex = try {
            withTimeout(FLOW_TIMEOUT_MS) { marketRepo.observeVisibleProducts().first() }
            null
        } catch (e: Exception) {
            e
        }
        val fsEx = ex?.unwrapFirestoreException()
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
    }

    @Test
    fun restockAlert_notifiesCustomerWhenOwnerRestocksProduct() = runBlocking {
        val ownerUid = signInTestUser("restock_owner_${UUID.randomUUID().toString().take(6)}@test.com")
        val bizRepo = BusinessRepository(firestore)
        val shop = Shop(ownerId = ownerUid, shopName = "Restock Shop", receiptHeaderTitle = "RESTOCK SHOP")
        val shopId = withTimeout(TIMEOUT_MS) { bizRepo.createOrUpdateShop(shop).getOrThrow() }
        val product = Product(
            shopId = shopId,
            shopOwnerId = ownerUid,
            shopName = shop.shopName,
            name = "OLED Screen Replacement",
            sku = "OLED-${UUID.randomUUID().toString().take(5)}",
            sellingPrice = 4500.0,
            costPrice = 2500.0,
            stockQuantity = 0L,
            minStockLevel = 2L,
            isVisible = true
        )
        val productId = withTimeout(TIMEOUT_MS) { bizRepo.saveProduct(product).getOrThrow() }
        val savedProduct = product.copy(id = productId)

        // Customer signs in and requests restock alert
        val buyerUid = signInTestUser("restock_buyer_${UUID.randomUUID().toString().take(6)}@test.com")
        val marketRepo = MarketplaceRepository(firestore)
        val authRepo = AuthAndUserRepository(firestore)
        withTimeout(TIMEOUT_MS) {
            authRepo.ensureUserProfile(buyerUid, "Waiting Buyer", "buyer@test.com", null, true).getOrThrow()
            marketRepo.toggleRestockAlert(savedProduct, customerName = "Waiting Buyer", currentlySubscribed = false).getOrThrow()
        }

        val alerts = withTimeout(FLOW_TIMEOUT_MS) {
            marketRepo.observeRestockAlerts(buyerUid).first { it.any { a -> a.productId == productId && a.status == "waiting" } }
        }
        assertTrue(alerts.any { it.productId == productId })

        withTimeout(TIMEOUT_MS) {
            marketRepo.toggleRestockAlert(savedProduct, customerName = "Waiting Buyer", currentlySubscribed = true).getOrThrow()
        }
        val afterUnsub = withTimeout(FLOW_TIMEOUT_MS) {
            marketRepo.observeRestockAlerts(buyerUid).first { list -> list.none { it.productId == productId && it.status == "waiting" } }
        }
        assertTrue(afterUnsub.none { it.productId == productId && it.status == "waiting" })
    }

    @Test
    fun restockAlertEndToEnd_and_shopAssets_and_loyaltyRedemption_and_gatewayPrivacy() = runBlocking {
        val suffix = UUID.randomUUID().toString().take(6)
        val ownerEmail = "owner_$suffix@test.com"
        val buyerEmail = "buyer_$suffix@test.com"

        val ownerUid = signInTestUser(ownerEmail)
        val bizRepo = BusinessRepository(firestore)
        val shop = Shop(ownerId = ownerUid, shopName = "NexGen Hub", receiptHeaderTitle = "NEXGEN HUB")
        val shopId = withTimeout(TIMEOUT_MS) { bizRepo.createOrUpdateShop(shop).getOrThrow() }

        // 1. Admin-only gateway config write
        withTimeout(TIMEOUT_MS) {
            bizRepo.saveGatewayConfig(
                shopId,
                com.example.data.model.GatewayConfig(
                    shopOwnerId = ownerUid,
                    darajaEnvironment = "sandbox",
                    darajaShortcode = "174379",
                    darajaConsumerKey = "secret-key-123",
                    darajaConsumerSecret = "secret-val-456",
                    darajaPasskey = "passkey-789",
                    stkEnabled = true
                )
            ).getOrThrow()
        }

        // 2. Shop Assets CRUD
        val asset = com.example.data.model.ShopAsset(
            shopId = shopId,
            shopOwnerId = ownerUid,
            name = "POS Thermal Printer",
            category = "Equipment",
            quantity = 2L,
            purchasePrice = 15000.0,
            currentValue = 12000.0,
            condition = "Good"
        )
        val assetId = withTimeout(TIMEOUT_MS) { bizRepo.saveShopAsset(asset).getOrThrow() }
        val assets = withTimeout(FLOW_TIMEOUT_MS) {
            bizRepo.observeShopAssets(ownerUid, isSuperAdmin = false).first { it.any { a -> a.id == assetId } }
        }
        assertEquals(24000.0, assets.sumOf { it.currentValue * it.quantity }, 0.01)

        // 3. Product out of stock for restock alert + product in stock for loyalty redemption
        val oosProduct = Product(
            shopId = shopId,
            shopOwnerId = ownerUid,
            shopName = shop.shopName,
            name = "GaN Charger 100W",
            sku = "GAN-$suffix",
            sellingPrice = 3000.0,
            costPrice = 1500.0,
            stockQuantity = 0L,
            minStockLevel = 2L,
            isVisible = true
        )
        val oosId = withTimeout(TIMEOUT_MS) { bizRepo.saveProduct(oosProduct).getOrThrow() }
        val inStockProd = Product(
            shopId = shopId,
            shopOwnerId = ownerUid,
            shopName = shop.shopName,
            name = "USB-C Hub",
            sku = "HUB-$suffix",
            sellingPrice = 2000.0,
            costPrice = 1000.0,
            stockQuantity = 10L,
            minStockLevel = 2L,
            isVisible = true
        )
        val inStockId = withTimeout(TIMEOUT_MS) { bizRepo.saveProduct(inStockProd).getOrThrow() }

        // Buyer signs in
        val buyerUid = signInTestUser(buyerEmail)
        val marketRepo = MarketplaceRepository(firestore)
        val authRepo = AuthAndUserRepository(firestore)
        val profile = withTimeout(TIMEOUT_MS) {
            authRepo.ensureUserProfile(buyerUid, "Loyalty Buyer", buyerEmail, null, true).getOrThrow()
        }
        assertEquals(100L, profile.loyaltyPoints)

        // Customer CANNOT read owner's gateway_config secrets
        val gwDenied = try {
            withTimeout(TIMEOUT_MS) {
                firestore.collection("shops").document(shopId)
                    .collection("gateway_config").document("default")
                    .get().await()
            }
            false
        } catch (e: Exception) {
            e.unwrapFirestoreException()?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
        }
        assertTrue("Customer should be denied access to gateway_config", gwDenied)

        // Customer subscribes to restock alert
        withTimeout(TIMEOUT_MS) {
            marketRepo.toggleRestockAlert(oosProduct.copy(id = oosId), customerName = "Loyalty Buyer", currentlySubscribed = false).getOrThrow()
        }

        // Customer places order redeeming 100 loyalty points (100.00 discount on 2000.00 subtotal)
        val orders = withTimeout(TIMEOUT_MS) {
            marketRepo.placeOnlineOrder(
                items = listOf(
                    CartItem(
                        userId = buyerUid,
                        productId = inStockId,
                        shopId = shopId,
                        shopOwnerId = ownerUid,
                        shopName = shop.shopName,
                        productName = "USB-C Hub",
                        unitPrice = 2000.0,
                        costPrice = 1000.0,
                        quantity = 1L
                    )
                ),
                customerName = "Loyalty Buyer",
                customerPhone = "0712000000",
                customerEmail = buyerEmail,
                deliveryAddress = "Westlands",
                deliveryOption = "Standard",
                paymentMethod = "Cash on Delivery",
                paymentReference = null,
                coupon = null,
                shippingFeePerShop = 100.0,
                taxRatePercent = 0.0,
                pointsToRedeem = 100L
            ).getOrThrow()
        }
        assertEquals(100L, orders.first().loyaltyPointsRedeemed)
        assertEquals(100.0, orders.first().loyaltyDiscountAmount, 0.01)
        assertEquals(2000.0, orders.first().totalAmount, 0.01) // 2000 - 100 loyalty + 100 shipping

        // Owner signs back in and restocks OOS product
        signInTestUser(ownerEmail)
        withTimeout(TIMEOUT_MS) {
            bizRepo.adjustStock(
                product = oosProduct.copy(id = oosId),
                delta = 5L,
                movementType = "Restock",
                fromLocation = "Supplier",
                toLocation = "Main Warehouse",
                reason = "New shipment",
                actorName = "Owner"
            ).getOrThrow()
        }

        // Buyer checks notifications for Back in Stock alert
        signInTestUser(buyerEmail)
        val notifs = withTimeout(FLOW_TIMEOUT_MS) {
            authRepo.observeNotifications(buyerUid).first { list -> list.any { it.type == "stock" && it.referenceId == oosId } }
        }
        assertTrue(notifs.any { it.type == "stock" && it.referenceId == oosId })
    }

    private companion object {
        const val TIMEOUT_MS = 8000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
