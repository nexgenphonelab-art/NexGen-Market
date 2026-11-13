package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AdminRecord
import com.example.data.model.AuditLog
import com.example.data.model.Category
import com.example.data.model.Coupon
import com.example.data.model.CrmCustomer
import com.example.data.model.Expense
import com.example.data.model.GatewayConfig
import com.example.data.model.ManualServiceEntry
import com.example.data.model.Order
import com.example.data.model.OrderLineItem
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
import com.example.data.remote.DarajaPaymentClient
import com.example.data.repository.AuthAndUserRepository
import com.example.data.repository.BusinessRepository
import com.example.data.repository.MarketplaceRepository
import com.example.data.repository.PosCartEntry
import com.example.data.unwrapFirestoreError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class SalesPoint(
    val label: String,
    val revenue: Double,
    val ordersCount: Int
)

data class FastMovingItem(
    val product: Product,
    val unitsMoved: Long,
    val velocityPerDay: Double,
    val daysOfStockLeft: Double?
)

data class FinancialSummary(
    val totalRevenue: Double = 0.0,
    val posRevenue: Double = 0.0,
    val onlineRevenue: Double = 0.0,
    val manualServiceRevenue: Double = 0.0,
    val manualServiceProfit: Double = 0.0,
    val manualServiceCount: Int = 0,
    val dailySales: Double = 0.0,
    val weeklySales: Double = 0.0,
    val monthlySales: Double = 0.0,
    val annualSales: Double = 0.0,
    val cogsTotal: Double = 0.0,
    val grossProfit: Double = 0.0,
    val operatingExpenses: Double = 0.0,
    val netProfit: Double = 0.0,
    val stockValuation: Double = 0.0,
    val assetsValuation: Double = 0.0,
    val totalShopWorth: Double = 0.0,
    val outstandingPayments: Double = 0.0,
    val customerDebts: Double = 0.0,
    val supplierPayables: Double = 0.0,
    val totalRefunds: Double = 0.0,
    val cashFlowNet: Double = 0.0,
    val totalOrdersCount: Int = 0,
    val totalPosCount: Int = 0,
    val lowStockCount: Int = 0,
    val outOfStockCount: Int = 0,
    val bestSellingProducts: List<Product> = emptyList(),
    val fastMovingProducts: List<FastMovingItem> = emptyList(),
    val salesSeries7d: List<SalesPoint> = emptyList(),
    val salesSeries30d: List<SalesPoint> = emptyList(),
    val salesSeries12m: List<SalesPoint> = emptyList(),
    val channelBreakdown: List<Pair<String, Double>> = emptyList()
)

class BusinessViewModel(
    private val bizRepo: BusinessRepository,
    private val marketRepo: MarketplaceRepository,
    private val authRepo: AuthAndUserRepository,
    val currentUserId: String,
    val userEmail: String?,
    val userDisplayName: String,
    val isEmailVerified: Boolean
) : ViewModel() {

    val darajaClient = DarajaPaymentClient()

    val isBootstrappedSuperAdmin: Boolean =
        AuthAndUserRepository.isSuperAdminEmail(userEmail, isEmailVerified)

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    val adminRecord: StateFlow<AdminRecord?> = authRepo.observeAdminRecord(currentUserId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val adminGrant: StateFlow<AdminRecord?> = authRepo.observeAdminGrantByEmail(userEmail)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val staffMemberships: StateFlow<List<StaffMember>> = authRepo.observeStaffMembership(currentUserId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val staffGrant: StateFlow<StaffMember?> = authRepo.observeStaffGrantByEmail(userEmail)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    // Super Admin strictly locked to nexgenphonelab@gmail.com or mrclvictor14@gmail.com only
    val isSuperAdmin: StateFlow<Boolean> = MutableStateFlow(isBootstrappedSuperAdmin).asStateFlow()

    val isAdminRole: StateFlow<Boolean> = combine(isSuperAdmin, adminRecord, adminGrant) { sup, rec, grant ->
        sup || (rec != null && rec.isActive) || (grant != null && grant.isActive)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), isBootstrappedSuperAdmin)

    val isStaffSalesperson: StateFlow<Boolean> = combine(isAdminRole, staffMemberships, staffGrant) { admin, memberships, grant ->
        !admin && (memberships.any { it.isActive } || (grant != null && grant.isActive))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), false)

    val hasBusinessAccess: StateFlow<Boolean> = combine(isAdminRole, isStaffSalesperson) { admin, staff ->
        admin || staff
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), isBootstrappedSuperAdmin)

    val effectivePermissions: StateFlow<Set<String>> = combine(staffMemberships, staffGrant) { memberships, grant ->
        val perms = mutableSetOf<String>()
        memberships.filter { it.isActive }.forEach { perms.addAll(it.permissions) }
        if (grant != null && grant.isActive) perms.addAll(grant.permissions)
        perms
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptySet())

    val ownedShops: StateFlow<List<Shop>> = if (isBootstrappedSuperAdmin) {
        bizRepo.observeOwnedShops(currentUserId)
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())
    } else {
        MutableStateFlow(emptyList<Shop>()).asStateFlow()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val allShops: StateFlow<List<Shop>> = hasBusinessAccess.flatMapLatest { access ->
        if (access) bizRepo.observeAllShopsAdmin().catch { emit(emptyList()) }
        else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val _selectedShopId = MutableStateFlow<String?>(null)

    val activeShop: StateFlow<Shop?> = combine(ownedShops, allShops, _selectedShopId) { owned, all, selectedId ->
        val pool = owned.ifEmpty { all }
        if (!selectedId.isNullOrBlank()) pool.firstOrNull { it.id == selectedId } ?: pool.firstOrNull()
        else pool.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val gatewayConfig: StateFlow<GatewayConfig?> = combine(activeShop, isAdminRole) { shop, admin ->
        shop to admin
    }.flatMapLatest { (shop, admin) ->
        if (shop == null || !admin) flowOf(null)
        else bizRepo.observeGatewayConfig(shop.id, shop.ownerId, fullStoreAccess = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val shopProducts: StateFlow<List<Product>> = hasBusinessAccess.flatMapLatest { access ->
        if (!access) flowOf(emptyList())
        else bizRepo.observeShopProducts(currentUserId, isSuperAdmin = true).catch { emit(emptyList()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val productImeis: StateFlow<Map<String, ProductImeiRegistry>> = hasBusinessAccess.flatMapLatest { access ->
        if (!access) flowOf(emptyMap())
        else bizRepo.observeProductImeis(currentUserId, hasStoreAccess = true).catch { emit(emptyMap()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyMap())

    @OptIn(ExperimentalCoroutinesApi::class)
    val shopOrders: StateFlow<List<Order>> = hasBusinessAccess.flatMapLatest { access ->
        if (!access) flowOf(emptyList())
        else bizRepo.observeShopOrders(currentUserId, isSuperAdmin = true).catch { emit(emptyList()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val posSales: StateFlow<List<PosSale>> = hasBusinessAccess.flatMapLatest { access ->
        if (!access) flowOf(emptyList())
        else bizRepo.observePosSales(currentUserId, isSuperAdmin = true).catch { emit(emptyList()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val manualServices: StateFlow<List<ManualServiceEntry>> = hasBusinessAccess.flatMapLatest { access ->
        if (!access) flowOf(emptyList())
        else bizRepo.observeManualServices(currentUserId, hasStoreAccess = true).catch { emit(emptyList()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val staffList: StateFlow<List<StaffMember>> = isAdminRole.flatMapLatest { admin ->
        if (!admin) flowOf(emptyList())
        else bizRepo.observeStaff(currentUserId, isSuperAdmin = true).catch { emit(emptyList()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val shifts: StateFlow<List<Shift>> = hasBusinessAccess.flatMapLatest { access ->
        if (!access) flowOf(emptyList())
        else bizRepo.observeShifts(currentUserId, isSuperAdmin = true).catch { emit(emptyList()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val suppliers: StateFlow<List<Supplier>> = hasBusinessAccess.flatMapLatest { access ->
        if (!access) flowOf(emptyList())
        else bizRepo.observeSuppliers(currentUserId, isSuperAdmin = true).catch { emit(emptyList()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val purchases: StateFlow<List<PurchaseOrder>> = isAdminRole.flatMapLatest { admin ->
        if (!admin) flowOf(emptyList())
        else bizRepo.observePurchases(currentUserId, isSuperAdmin = true).catch { emit(emptyList()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val stockMovements: StateFlow<List<StockMovement>> = hasBusinessAccess.flatMapLatest { access ->
        if (!access) flowOf(emptyList())
        else bizRepo.observeStockMovements(currentUserId, isSuperAdmin = true).catch { emit(emptyList()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val expenses: StateFlow<List<Expense>> = isAdminRole.flatMapLatest { admin ->
        if (!admin) flowOf(emptyList())
        else bizRepo.observeExpenses(currentUserId, isSuperAdmin = true).catch { emit(emptyList()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val crmCustomers: StateFlow<List<CrmCustomer>> = hasBusinessAccess.flatMapLatest { access ->
        if (!access) flowOf(emptyList())
        else bizRepo.observeCrmCustomers(currentUserId, isSuperAdmin = true).catch { emit(emptyList()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val refunds: StateFlow<List<RefundRequest>> = isAdminRole.flatMapLatest { admin ->
        if (!admin) flowOf(emptyList())
        else bizRepo.observeRefunds(currentUserId, isSuperAdmin = true).catch { emit(emptyList()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val auditLogs: StateFlow<List<AuditLog>> = isAdminRole.flatMapLatest { admin ->
        if (!admin) flowOf(emptyList())
        else bizRepo.observeAuditLogs(currentUserId, isSuperAdmin = true).catch { emit(emptyList()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val shopAssets: StateFlow<List<ShopAsset>> = isAdminRole.flatMapLatest { admin ->
        if (!admin) flowOf(emptyList())
        else bizRepo.observeShopAssets(currentUserId, isSuperAdmin = true).catch { emit(emptyList()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val adminsList: StateFlow<List<AdminRecord>> = isSuperAdmin.flatMapLatest { superAdmin ->
        if (superAdmin) bizRepo.observeAdmins().catch { emit(emptyList()) }
        else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    // Live Financial Metrics computed exclusively from Firestore records
    val financialSummary: StateFlow<FinancialSummary> = combine(
        shopOrders,
        posSales,
        shopProducts,
        expenses,
        suppliers,
        crmCustomers,
        refunds,
        shopAssets,
        stockMovements,
        manualServices
    ) { arr ->
        @Suppress("UNCHECKED_CAST")
        val ordersList = arr[0] as List<Order>
        @Suppress("UNCHECKED_CAST")
        val salesList = arr[1] as List<PosSale>
        @Suppress("UNCHECKED_CAST")
        val productsList = arr[2] as List<Product>
        @Suppress("UNCHECKED_CAST")
        val expensesList = arr[3] as List<Expense>
        @Suppress("UNCHECKED_CAST")
        val suppliersList = arr[4] as List<Supplier>
        @Suppress("UNCHECKED_CAST")
        val crmList = arr[5] as List<CrmCustomer>
        @Suppress("UNCHECKED_CAST")
        val refundsList = arr[6] as List<RefundRequest>
        @Suppress("UNCHECKED_CAST")
        val assetsList = arr[7] as List<ShopAsset>
        @Suppress("UNCHECKED_CAST")
        val movementsList = arr[8] as List<StockMovement>
        @Suppress("UNCHECKED_CAST")
        val servicesList = arr[9] as List<ManualServiceEntry>

        val now = Calendar.getInstance()
        val nowMs = now.timeInMillis
        val dayMs = 24L * 3600 * 1000
        val startOfDay = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val startOfWeek = nowMs - 7L * dayMs
        val startOfMonth = (now.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0) }.timeInMillis
        val startOfYear = (now.clone() as Calendar).apply { set(Calendar.DAY_OF_YEAR, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0) }.timeInMillis

        val paidOnline = ordersList.filter { it.paymentStatus == "Paid" && it.status != "Cancelled" && it.status != "Refunded" }
        val completedPos = salesList.filter { it.status == "Completed" }
        val activeServices = servicesList.filter { it.status != "Cancelled" }

        val onlineRev = paidOnline.sumOf { it.totalAmount }
        val posRev = completedPos.sumOf { it.totalAmount }
        val manualRev = activeServices.sumOf { it.serviceFee }
        val manualParts = activeServices.sumOf { it.partsCost }
        val totalRev = onlineRev + posRev + manualRev

        var daily = 0.0
        var weekly = 0.0
        var monthly = 0.0
        var annual = 0.0

        val txPoints = mutableListOf<Pair<Long, Double>>()
        paidOnline.forEach { o ->
            val t = o.createdAt?.toDate()?.time ?: nowMs
            txPoints.add(t to o.totalAmount)
            if (t >= startOfDay) daily += o.totalAmount
            if (t >= startOfWeek) weekly += o.totalAmount
            if (t >= startOfMonth) monthly += o.totalAmount
            if (t >= startOfYear) annual += o.totalAmount
        }
        completedPos.forEach { s ->
            val t = s.createdAt?.toDate()?.time ?: nowMs
            txPoints.add(t to s.totalAmount)
            if (t >= startOfDay) daily += s.totalAmount
            if (t >= startOfWeek) weekly += s.totalAmount
            if (t >= startOfMonth) monthly += s.totalAmount
            if (t >= startOfYear) annual += s.totalAmount
        }
        activeServices.forEach { srv ->
            val t = srv.recordedAtMillis.takeIf { it > 0L } ?: srv.createdAt?.toDate()?.time ?: nowMs
            txPoints.add(t to srv.serviceFee)
            if (t >= startOfDay) daily += srv.serviceFee
            if (t >= startOfWeek) weekly += srv.serviceFee
            if (t >= startOfMonth) monthly += srv.serviceFee
            if (t >= startOfYear) annual += srv.serviceFee
        }

        // 7D Series (last 7 days daily)
        val dayFmt = SimpleDateFormat("EEE", Locale.US)
        val sales7d = (6 downTo 0).map { daysAgo ->
            val cal = (now.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, -daysAgo)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val start = cal.timeInMillis
            val end = start + dayMs
            val matches = txPoints.filter { it.first in start until end }
            SalesPoint(dayFmt.format(Date(start)), matches.sumOf { it.second }, matches.size)
        }

        // 30D Series (6 buckets of 5 days)
        val dateFmt = SimpleDateFormat("MM/dd", Locale.US)
        val sales30d = (5 downTo 0).map { bucket ->
            val end = startOfDay + dayMs - bucket * 5L * dayMs
            val start = end - 5L * dayMs
            val matches = txPoints.filter { it.first in start until end }
            SalesPoint(dateFmt.format(Date(start)), matches.sumOf { it.second }, matches.size)
        }

        // 12M Series (last 12 months)
        val monthFmt = SimpleDateFormat("MMM", Locale.US)
        val sales12m = (11 downTo 0).map { monthsAgo ->
            val cal = (now.clone() as Calendar).apply {
                add(Calendar.MONTH, -monthsAgo)
                set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val start = cal.timeInMillis
            val nextCal = (cal.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
            val end = nextCal.timeInMillis
            val matches = txPoints.filter { it.first in start until end }
            SalesPoint(monthFmt.format(Date(start)), matches.sumOf { it.second }, matches.size)
        }

        val cogs = paidOnline.sumOf { it.cogsTotal } + completedPos.sumOf { it.cogsTotal } + manualParts
        val gross = totalRev - cogs
        val opEx = expensesList.sumOf { it.amount }
        val net = gross - opEx
        val stockVal = productsList.sumOf { it.costPrice * it.stockQuantity }
        val assetsVal = assetsList.sumOf { it.currentValue * it.quantity }
        val outstandingOrd = ordersList.filter { it.paymentStatus != "Paid" && it.status != "Cancelled" }.sumOf { it.totalAmount }
        val custDebts = crmList.sumOf { it.outstandingBalance }
        val supPayables = suppliersList.sumOf { it.balanceOwed }
        val refTotal = refundsList.filter { it.status == "Refunded" }.sumOf { it.amount }
        val cashCollected = completedPos.sumOf { it.cashPaid + it.mpesaPaid + it.cardPaid } + onlineRev + manualRev
        val cashOut = opEx + refTotal + manualParts
        val cashFlowNet = cashCollected - cashOut
        val totalWorth = stockVal + assetsVal + cashFlowNet.coerceAtLeast(0.0) + custDebts - supPayables

        // Fast Moving Products based on 30-day outgoing movements + total soldCount
        val thirtyDaysAgo = nowMs - 30L * dayMs
        val sold30dByProd = movementsList
            .filter { (it.type == "POS" || it.type == "Sale") && (it.createdAt?.toDate()?.time ?: nowMs) >= thirtyDaysAgo }
            .groupBy { it.productId }
            .mapValues { entry -> entry.value.sumOf { kotlin.math.abs(it.quantityDelta) } }

        val fastMoving = productsList
            .map { p ->
                val moved30d = sold30dByProd[p.id] ?: 0L
                val effectiveMoved = if (moved30d > 0L) moved30d else p.soldCount
                val velocity = effectiveMoved / 30.0
                val daysLeft = if (velocity > 0.01) p.stockQuantity / velocity else null
                FastMovingItem(
                    product = p,
                    unitsMoved = effectiveMoved,
                    velocityPerDay = velocity,
                    daysOfStockLeft = daysLeft
                )
            }
            .filter { it.unitsMoved > 0L }
            .sortedByDescending { it.unitsMoved }
            .take(8)

        FinancialSummary(
            totalRevenue = totalRev,
            posRevenue = posRev,
            onlineRevenue = onlineRev,
            manualServiceRevenue = manualRev,
            manualServiceProfit = manualRev - manualParts,
            manualServiceCount = activeServices.size,
            dailySales = daily,
            weeklySales = weekly,
            monthlySales = monthly,
            annualSales = annual,
            cogsTotal = cogs,
            grossProfit = gross,
            operatingExpenses = opEx,
            netProfit = net,
            stockValuation = stockVal,
            assetsValuation = assetsVal,
            totalShopWorth = totalWorth,
            outstandingPayments = outstandingOrd,
            customerDebts = custDebts,
            supplierPayables = supPayables,
            totalRefunds = refTotal,
            cashFlowNet = cashFlowNet,
            totalOrdersCount = ordersList.size,
            totalPosCount = salesList.size,
            lowStockCount = productsList.count { it.stockQuantity in 1..it.minStockLevel },
            outOfStockCount = productsList.count { it.stockQuantity == 0L },
            bestSellingProducts = productsList.sortedByDescending { it.soldCount }.take(5),
            fastMovingProducts = fastMoving,
            salesSeries7d = sales7d,
            salesSeries30d = sales30d,
            salesSeries12m = sales12m,
            channelBreakdown = listOf("POS Counter" to posRev, "Online Store" to onlineRev, "Manual Book" to manualRev)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), FinancialSummary())

    // POS Terminal in-memory cart state
    private val _posCart = MutableStateFlow<List<PosCartEntry>>(emptyList())
    val posCart: StateFlow<List<PosCartEntry>> = _posCart.asStateFlow()

    fun clearMessage() {
        _statusMessage.value = null
    }

    fun selectShop(shopId: String) {
        _selectedShopId.value = shopId
    }

    fun saveShop(shop: Shop, onDone: (String) -> Unit = {}) {
        viewModelScope.launch {
            bizRepo.createOrUpdateShop(shop)
                .onSuccess { id ->
                    _selectedShopId.value = id
                    _statusMessage.value = "Saved '${shop.shopName}' & Receipt Configuration"
                    onDone(id)
                }
                .onFailure { _statusMessage.value = it.unwrapFirestoreError().localizedMessage }
        }
    }

    fun saveGatewayConfig(shopId: String, config: GatewayConfig) {
        viewModelScope.launch {
            bizRepo.saveGatewayConfig(shopId, config)
                .onSuccess { _statusMessage.value = "Payment & Printer settings saved" }
                .onFailure { _statusMessage.value = it.unwrapFirestoreError().localizedMessage }
        }
    }

    fun saveCategory(category: Category) {
        viewModelScope.launch {
            bizRepo.saveCategory(category)
                .onSuccess { _statusMessage.value = "Category '${category.name}' saved" }
                .onFailure { _statusMessage.value = it.unwrapFirestoreError().localizedMessage }
        }
    }

    fun saveProduct(product: Product, onDone: () -> Unit = {}) {
        if (!isAdminRole.value) {
            _statusMessage.value = "Only Admins can add or edit products."
            return
        }
        viewModelScope.launch {
            bizRepo.saveProduct(product)
                .onSuccess {
                    _statusMessage.value = "Product '${product.name}' saved"
                    onDone()
                }
                .onFailure { _statusMessage.value = it.unwrapFirestoreError().localizedMessage }
        }
    }

    fun saveProductWithImeis(product: Product, imeis: List<String>, onDone: () -> Unit = {}) {
        if (!isAdminRole.value) {
            _statusMessage.value = "Only Admins can add or edit products."
            return
        }
        viewModelScope.launch {
            bizRepo.saveProduct(product)
                .onSuccess { savedId ->
                    val existingRegistry = productImeis.value[savedId]
                    val cleaned = imeis.map { it.trim() }.filter { it.isNotBlank() }.distinct()
                    if (cleaned.isNotEmpty() || existingRegistry != null) {
                        bizRepo.saveProductImeis(
                            productId = savedId,
                            shopId = product.shopId,
                            shopOwnerId = product.shopOwnerId.ifBlank { currentUserId },
                            imeis = cleaned,
                            soldImeis = existingRegistry?.soldImeis ?: emptyList()
                        )
                    }
                    _statusMessage.value = if (cleaned.isNotEmpty()) {
                        "Saved '${product.name}' with ${cleaned.size} private IMEI(s)"
                    } else {
                        "Product '${product.name}' saved"
                    }
                    onDone()
                }
                .onFailure { _statusMessage.value = it.unwrapFirestoreError().localizedMessage }
        }
    }

    fun bulkImportProducts(products: List<Product>) {
        viewModelScope.launch {
            var ok = 0
            products.forEach { prod ->
                bizRepo.saveProduct(prod).onSuccess { ok++ }
            }
            _statusMessage.value = "Imported $ok of ${products.size} products"
        }
    }

    fun createStarterCatalogForShop(shop: Shop) {
        viewModelScope.launch {
            val starterCategories = listOf(
                Category(shopOwnerId = shop.ownerId, name = "Phones & Tablets", parentCategory = "Electronics", iconName = "smartphone", sortOrder = 1, isActive = true),
                Category(shopOwnerId = shop.ownerId, name = "Audio & Headphones", parentCategory = "Electronics", iconName = "headphones", sortOrder = 2, isActive = true),
                Category(shopOwnerId = shop.ownerId, name = "Smartwatches", parentCategory = "Wearables", iconName = "watch", sortOrder = 3, isActive = true),
                Category(shopOwnerId = shop.ownerId, name = "Accessories & Repair", parentCategory = "Tech Care", iconName = "build", sortOrder = 4, isActive = true)
            )
            starterCategories.forEach { bizRepo.saveCategory(it) }

            val starterProducts = listOf(
                Product(
                    shopId = shop.id, shopOwnerId = shop.ownerId, shopName = shop.shopName,
                    name = "Apex X1 Titanium 5G Smartphone (256GB)",
                    description = "6.7-inch 120Hz LTPO AMOLED display, 50MP OIS triple camera, 5000mAh 67W fast charge, dual SIM unlocked.",
                    category = "Phones & Tablets", subcategory = "Flagship 5G",
                    sku = "NXG-PHN-001", barcode = "600100200301",
                    sellingPrice = 42500.0, originalPrice = 49900.0, costPrice = 34000.0,
                    stockQuantity = 18L, minStockLevel = 4L, stockLocation = "Main Warehouse",
                    colors = listOf("Titanium Blue", "Obsidian Black", "Silver Frost"),
                    sizes = listOf("256GB", "512GB"),
                    specifications = listOf("Display: 6.7\" 120Hz AMOLED", "RAM: 12GB LPDDR5X", "Battery: 5000mAh 67W"),
                    imageKeys = listOf("smartphone"),
                    isTrending = true, isFlashSale = true, isBestSeller = true, isVisible = true
                ),
                Product(
                    shopId = shop.id, shopOwnerId = shop.ownerId, shopName = shop.shopName,
                    name = "Pulse Pro Hybrid ANC Wireless Headphones",
                    description = "45dB active noise cancellation, 50-hour battery life, Bluetooth 5.4 multipoint, low-latency studio acoustics.",
                    category = "Audio & Headphones", subcategory = "Over-Ear ANC",
                    sku = "NXG-AUD-002", barcode = "600100200302",
                    sellingPrice = 6800.0, originalPrice = 8900.0, costPrice = 4300.0,
                    stockQuantity = 25L, minStockLevel = 5L, stockLocation = "POS Counter",
                    colors = listOf("Matte Charcoal", "Cyan Ring"),
                    sizes = listOf("Standard"),
                    specifications = listOf("ANC: 45dB Hybrid", "Battery: 50 Hours", "Charging: USB-C Fast"),
                    imageKeys = listOf("audio"),
                    isTrending = true, isNewArrival = true, isDailyDeal = true, isVisible = true
                ),
                Product(
                    shopId = shop.id, shopOwnerId = shop.ownerId, shopName = shop.shopName,
                    name = "Titan Ultra GPS Rugged Smartwatch",
                    description = "Sapphire crystal bezel, dual-band GPS, SpO2 & heart rate tracking, 14-day battery, 5ATM waterproof.",
                    category = "Smartwatches", subcategory = "Sport & Titanium",
                    sku = "NXG-WCH-003", barcode = "600100200303",
                    sellingPrice = 11500.0, originalPrice = 14500.0, costPrice = 7800.0,
                    stockQuantity = 14L, minStockLevel = 3L, stockLocation = "Main Warehouse",
                    colors = listOf("Coral Orange", "Titanium Gray"),
                    sizes = listOf("45mm", "49mm"),
                    specifications = listOf("Waterproof: 5ATM", "GPS: Dual-Band", "Battery: 14 Days"),
                    imageKeys = listOf("watch"),
                    isFlashSale = true, isDailyDeal = true, isSponsoredAd = true, isVisible = true
                ),
                Product(
                    shopId = shop.id, shopOwnerId = shop.ownerId, shopName = shop.shopName,
                    name = "65W GaN Fast Charger & Braided Type-C Kit",
                    description = "Compact 3-port GaN wall adapter with 100W E-Marker braided USB-C cable for phones and laptops.",
                    category = "Accessories & Repair", subcategory = "Chargers & Cables",
                    sku = "NXG-ACC-004", barcode = "600100200304",
                    sellingPrice = 2400.0, originalPrice = 3200.0, costPrice = 1350.0,
                    stockQuantity = 40L, minStockLevel = 8L, stockLocation = "POS Counter",
                    colors = listOf("Graphite", "Electric Blue"),
                    sizes = listOf("UK/KE Plug"),
                    specifications = listOf("Output: 65W PD 3.0", "Ports: 2x USB-C, 1x USB-A"),
                    imageKeys = listOf("nexgen_logo"),
                    isNewArrival = true, isBestSeller = true, isVisible = true
                )
            )
            starterProducts.forEach { bizRepo.saveProduct(it) }
            bizRepo.saveCoupon(
                Coupon(
                    shopId = shop.id,
                    shopOwnerId = shop.ownerId,
                    code = "NEXGEN10",
                    discountType = "percent",
                    discountValue = 10.0,
                    minOrderAmount = 1000.0,
                    isActive = true
                )
            )
            _statusMessage.value = "Loaded starter products, categories, and NEXGEN10 coupon"
        }
    }

    fun deleteProduct(product: Product) {
        if (!isAdminRole.value) {
            _statusMessage.value = "Only Admins can delete products."
            return
        }
        viewModelScope.launch {
            bizRepo.deleteProduct(product)
                .onSuccess { _statusMessage.value = "Deleted '${product.name}'" }
                .onFailure { _statusMessage.value = it.unwrapFirestoreError().localizedMessage }
        }
    }

    fun adjustStock(
        product: Product,
        delta: Long,
        movementType: String,
        fromLocation: String,
        toLocation: String,
        reason: String
    ) {
        viewModelScope.launch {
            bizRepo.adjustStock(product, delta, movementType, fromLocation, toLocation, reason, userDisplayName)
                .onSuccess { _statusMessage.value = "Stock movement ($movementType) recorded" }
                .onFailure { _statusMessage.value = it.unwrapFirestoreError().localizedMessage }
        }
    }

    fun updateOrderStatus(
        order: Order,
        newStatus: String,
        newPaymentStatus: String,
        trackingNote: String?,
        assignedRider: String?
    ) {
        viewModelScope.launch {
            bizRepo.updateOrderStatusAndPayment(order, newStatus, newPaymentStatus, trackingNote, assignedRider)
                .onSuccess { _statusMessage.value = "Order ${order.orderNumber} updated to $newStatus" }
                .onFailure { _statusMessage.value = it.unwrapFirestoreError().localizedMessage }
        }
    }

    suspend fun loadOrderLineItems(orderId: String, shopOwnerId: String): List<OrderLineItem> {
        return marketRepo.getOrderLineItems(orderId, shopOwnerId, isShopSide = true).getOrElse { emptyList() }
    }

    // POS Cart operations
    fun addToPosCart(product: Product, variation: String = "Standard") {
        val current = _posCart.value.toMutableList()
        val idx = current.indexOfFirst { it.product.id == product.id && it.variationLabel == variation }
        if (idx >= 0) {
            val existing = current[idx]
            if (existing.quantity < product.stockQuantity) {
                current[idx] = existing.copy(quantity = existing.quantity + 1L)
            } else {
                _statusMessage.value = "Max available stock reached (${product.stockQuantity})"
            }
        } else {
            if (product.stockQuantity > 0L) {
                current.add(PosCartEntry(product, variation, 1L))
            } else {
                _statusMessage.value = "${product.name} is out of stock"
            }
        }
        _posCart.value = current
    }

    fun updatePosCartQty(productId: String, variation: String, newQty: Long) {
        val current = _posCart.value.toMutableList()
        val idx = current.indexOfFirst { it.product.id == productId && it.variationLabel == variation }
        if (idx >= 0) {
            if (newQty <= 0L) current.removeAt(idx)
            else current[idx] = current[idx].copy(quantity = newQty.coerceAtMost(current[idx].product.stockQuantity))
        }
        _posCart.value = current
    }

    fun clearPosCart() {
        _posCart.value = emptyList()
    }

    fun scanBarcodeForPos(barcodeOrSku: String): Boolean {
        val query = barcodeOrSku.trim()
        val match = shopProducts.value.firstOrNull {
            it.barcode.equals(query, ignoreCase = true) || it.sku.equals(query, ignoreCase = true)
        }
        return if (match != null) {
            addToPosCart(match)
            _statusMessage.value = "Scanned: ${match.name}"
            true
        } else {
            _statusMessage.value = "No product matched barcode/SKU '$query'"
            false
        }
    }

    fun completePosSale(
        shop: Shop,
        shift: Shift?,
        customer: CrmCustomer?,
        discountAmount: Double,
        paymentMode: String,
        cashPaid: Double,
        mpesaPaid: Double,
        cardPaid: Double,
        creditBalance: Double,
        mpesaCode: String?,
        onSaleComplete: (PosSale, List<PosCartEntry>) -> Unit
    ) {
        val entries = _posCart.value
        if (entries.isEmpty()) return
        viewModelScope.launch {
            bizRepo.completePosSale(
                shop = shop,
                cashierName = userDisplayName,
                shift = shift,
                cartEntries = entries,
                customer = customer,
                discountAmount = discountAmount,
                paymentMode = paymentMode,
                cashPaid = cashPaid,
                mpesaPaid = mpesaPaid,
                cardPaid = cardPaid,
                creditBalance = creditBalance,
                mpesaCode = mpesaCode
            ).onSuccess { sale ->
                _posCart.value = emptyList()
                _statusMessage.value = "Receipt ${sale.receiptNumber} completed"
                onSaleComplete(sale, entries)
            }.onFailure {
                _statusMessage.value = it.unwrapFirestoreError().localizedMessage ?: "POS sale failed"
            }
        }
    }

    fun refundPosSale(sale: PosSale) {
        if (!isAdminRole.value) {
            _statusMessage.value = "Salespersons cannot undo or refund completed POS sales."
            return
        }
        viewModelScope.launch {
            bizRepo.refundPosSale(sale)
                .onSuccess { _statusMessage.value = "POS Receipt ${sale.receiptNumber} refunded" }
                .onFailure { _statusMessage.value = it.unwrapFirestoreError().localizedMessage }
        }
    }

    suspend fun loadPosSaleItems(saleId: String, ownerId: String): List<PosSaleItem> {
        return bizRepo.getPosSaleItems(saleId, ownerId).getOrElse { emptyList() }
    }

    fun openShift(shop: Shop, openingCash: Double) {
        viewModelScope.launch {
            bizRepo.openShift(shop.id, shop.ownerId, userDisplayName, openingCash)
                .onSuccess { _statusMessage.value = "POS Active — Shift & Manual Service Book Unlocked" }
                .onFailure { _statusMessage.value = it.unwrapFirestoreError().localizedMessage }
        }
    }

    fun closeShift(shift: Shift, countedCash: Double, notes: String?) {
        viewModelScope.launch {
            bizRepo.closeShift(shift, countedCash, notes)
                .onSuccess { _statusMessage.value = "Shift closed & reconciled" }
                .onFailure { _statusMessage.value = it.unwrapFirestoreError().localizedMessage }
        }
    }

    fun recordManualService(
        shop: Shop,
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
        onRecorded: (ManualServiceEntry) -> Unit = {}
    ) {
        val openShift = shifts.value.firstOrNull { it.status == "Open" }
        if (openShift == null) {
            _statusMessage.value = "Manual Service Book is only active when POS is active. Open a POS Shift first."
            return
        }
        viewModelScope.launch {
            bizRepo.recordManualService(
                shop = shop,
                openShift = openShift,
                serviceName = serviceName,
                category = category,
                deviceModel = deviceModel,
                deviceImei = deviceImei,
                customerName = customerName,
                customerPhone = customerPhone,
                serviceFee = serviceFee,
                partsCost = partsCost,
                paymentMode = paymentMode,
                mpesaCode = mpesaCode,
                status = status,
                notes = notes,
                recordedByName = userDisplayName
            ).onSuccess { entry ->
                _statusMessage.value = "Recorded ${entry.entryNumber} in Daily Manual Book"
                onRecorded(entry)
            }.onFailure {
                _statusMessage.value = it.unwrapFirestoreError().localizedMessage ?: "Failed to record manual service"
            }
        }
    }

    fun updateManualServiceStatus(entry: ManualServiceEntry, newStatus: String) {
        if (!isAdminRole.value) {
            _statusMessage.value = "Only Admins can modify Manual Service records."
            return
        }
        viewModelScope.launch {
            bizRepo.updateManualServiceStatus(entry, newStatus)
                .onSuccess { _statusMessage.value = "${entry.entryNumber} marked $newStatus" }
                .onFailure { _statusMessage.value = it.unwrapFirestoreError().localizedMessage }
        }
    }

    fun saveStaffMember(member: StaffMember) {
        if (!isSuperAdmin.value) {
            _statusMessage.value = "Only Super Admin can manage staff and salespersons."
            return
        }
        viewModelScope.launch {
            bizRepo.saveStaffMember(member)
                .onSuccess { _statusMessage.value = "Staff member '${member.staffName}' saved" }
                .onFailure { _statusMessage.value = it.unwrapFirestoreError().localizedMessage }
        }
    }

    fun deleteStaffMember(member: StaffMember) {
        if (!isSuperAdmin.value) {
            _statusMessage.value = "Only Super Admin can remove staff accounts."
            return
        }
        viewModelScope.launch {
            bizRepo.deleteStaffMember(member)
                .onSuccess { _statusMessage.value = "Removed staff '${member.staffName}'" }
                .onFailure { _statusMessage.value = it.unwrapFirestoreError().localizedMessage }
        }
    }

    fun saveSupplier(supplier: Supplier) {
        viewModelScope.launch {
            bizRepo.saveSupplier(supplier)
                .onSuccess { _statusMessage.value = "Supplier '${supplier.name}' saved" }
                .onFailure { _statusMessage.value = it.unwrapFirestoreError().localizedMessage }
        }
    }

    fun createPurchaseOrder(
        shop: Shop,
        supplier: Supplier,
        product: Product,
        quantity: Long,
        unitCost: Double,
        amountPaid: Double,
        receiveImmediately: Boolean
    ) {
        viewModelScope.launch {
            bizRepo.createPurchaseOrderAndReceive(
                shop, supplier, product, quantity, unitCost, amountPaid, receiveImmediately, userDisplayName
            ).onSuccess {
                _statusMessage.value = if (receiveImmediately) "PO & GRN received into stock" else "Purchase order created"
            }.onFailure { _statusMessage.value = it.unwrapFirestoreError().localizedMessage }
        }
    }

    fun recordExpense(expense: Expense) {
        viewModelScope.launch {
            bizRepo.recordExpense(expense)
                .onSuccess { _statusMessage.value = "Expense '${expense.title}' recorded" }
                .onFailure { _statusMessage.value = it.unwrapFirestoreError().localizedMessage }
        }
    }

    fun saveCoupon(coupon: Coupon) {
        viewModelScope.launch {
            bizRepo.saveCoupon(coupon)
                .onSuccess { _statusMessage.value = "Coupon '${coupon.code}' saved" }
                .onFailure { _statusMessage.value = it.unwrapFirestoreError().localizedMessage }
        }
    }

    fun saveCrmCustomer(customer: CrmCustomer) {
        viewModelScope.launch {
            bizRepo.saveCrmCustomer(customer)
                .onSuccess { _statusMessage.value = "Customer '${customer.name}' saved" }
                .onFailure { _statusMessage.value = it.unwrapFirestoreError().localizedMessage }
        }
    }

    fun processRefund(refund: RefundRequest, newStatus: String) {
        viewModelScope.launch {
            bizRepo.processRefund(refund, newStatus)
                .onSuccess { _statusMessage.value = "Refund marked $newStatus" }
                .onFailure { _statusMessage.value = it.unwrapFirestoreError().localizedMessage }
        }
    }

    fun saveAdmin(record: AdminRecord) {
        viewModelScope.launch {
            bizRepo.saveAdminRecord(record)
                .onSuccess { _statusMessage.value = "Admin account '${record.displayName}' saved" }
                .onFailure { _statusMessage.value = it.unwrapFirestoreError().localizedMessage }
        }
    }

    fun approveShop(shopId: String, approved: Boolean, featured: Boolean, commission: Double) {
        viewModelScope.launch {
            bizRepo.approveShopAndCommission(shopId, approved, featured, commission)
                .onSuccess { _statusMessage.value = "Shop marketplace settings updated" }
                .onFailure { _statusMessage.value = it.unwrapFirestoreError().localizedMessage }
        }
    }

    fun saveShopAsset(asset: ShopAsset) {
        viewModelScope.launch {
            bizRepo.saveShopAsset(asset)
                .onSuccess { _statusMessage.value = "Shop asset '${asset.name}' saved" }
                .onFailure { _statusMessage.value = it.unwrapFirestoreError().localizedMessage }
        }
    }

    fun deleteShopAsset(asset: ShopAsset) {
        viewModelScope.launch {
            bizRepo.deleteShopAsset(asset)
                .onSuccess { _statusMessage.value = "Shop asset '${asset.name}' removed" }
                .onFailure { _statusMessage.value = it.unwrapFirestoreError().localizedMessage }
        }
    }

    fun triggerAdminStkPush(
        shop: Shop,
        phone: String,
        amount: Double,
        reference: String,
        onResult: (String) -> Unit
    ) {
        val cfg = gatewayConfig.value
        if (cfg == null || !cfg.stkEnabled) {
            _statusMessage.value = "Enable M-Pesa STK Push and configure Daraja keys in Admin Settings first."
            return
        }
        viewModelScope.launch {
            darajaClient.triggerStkPush(
                environment = cfg.darajaEnvironment,
                consumerKey = cfg.darajaConsumerKey,
                consumerSecret = cfg.darajaConsumerSecret,
                shortcode = cfg.darajaShortcode.ifBlank { shop.paybillNumber },
                passkey = cfg.darajaPasskey,
                phone = phone,
                amount = amount,
                accountReference = reference.ifBlank { shop.accountReference },
                callbackUrl = cfg.darajaCallbackUrl
            ).onSuccess { resp ->
                _statusMessage.value = "STK Prompt Sent (${resp.checkoutRequestId}): ${resp.customerMessage}"
                onResult(resp.checkoutRequestId)
            }.onFailure { err ->
                _statusMessage.value = err.localizedMessage ?: "M-Pesa STK Push error"
            }
        }
    }

    fun createBackupJson(): String {
        val root = JSONObject()
        root.put("exportedAt", System.currentTimeMillis())
        root.put("shopCount", allShops.value.size)
        root.put("productCount", shopProducts.value.size)
        root.put("orderCount", shopOrders.value.size)
        root.put("posSaleCount", posSales.value.size)

        val prodArray = JSONArray()
        shopProducts.value.forEach { p ->
            prodArray.put(JSONObject().apply {
                put("name", p.name)
                put("category", p.category)
                put("sku", p.sku)
                put("barcode", p.barcode)
                put("sellingPrice", p.sellingPrice)
                put("costPrice", p.costPrice)
                put("stockQuantity", p.stockQuantity)
                put("description", p.description)
            })
        }
        root.put("products", prodArray)
        return root.toString(2)
    }

    fun restoreProductsFromBackupJson(jsonText: String, shop: Shop) {
        viewModelScope.launch {
            runCatching {
                val obj = JSONObject(jsonText)
                val arr = obj.getJSONArray("products")
                val list = mutableListOf<Product>()
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    list.add(
                        Product(
                            shopId = shop.id,
                            shopOwnerId = shop.ownerId,
                            shopName = shop.shopName,
                            name = item.getString("name"),
                            category = item.optString("category", "Electronics"),
                            sku = item.optString("sku", "RST-$i"),
                            barcode = item.optString("barcode", ""),
                            sellingPrice = item.optDouble("sellingPrice", 100.0),
                            originalPrice = item.optDouble("sellingPrice", 100.0),
                            costPrice = item.optDouble("costPrice", 60.0),
                            stockQuantity = item.optLong("stockQuantity", 0L),
                            description = item.optString("description", ""),
                            isVisible = true
                        )
                    )
                }
                list
            }.onSuccess { restored ->
                bulkImportProducts(restored)
            }.onFailure {
                _statusMessage.value = "Restore failed: ${it.localizedMessage}"
            }
        }
    }

    private companion object {
        const val TAG = "BusinessVM"
    }
}
