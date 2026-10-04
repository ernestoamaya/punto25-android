package ar.com.mandados.app

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import android.util.Base64
import kotlin.random.Random

class MandadosController(context: Context) {
    private val appContext = context.applicationContext
    private val store = LocalStore(appContext)
    private val timestampFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
    private val legacyTimestampFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
    private val serviceDateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    val areaCodes: Map<String, AreaCode> = AreaCodeRepository.load(context)

    var customer by mutableStateOf(store.loadCustomer())
        private set
    var config by mutableStateOf(store.loadConfig())
        private set
    var orders by mutableStateOf(store.loadOrders())
        private set
    var riders by mutableStateOf(store.loadRiders())
        private set
    var shifts by mutableStateOf(store.loadShifts())
        private set
    var shiftReservations by mutableStateOf(store.loadShiftReservations())
        private set
    var shiftAuditEvents by mutableStateOf(store.loadShiftAuditEvents())
        private set
    var riderInvitations by mutableStateOf(store.loadRiderInvitations())
        private set
    var riderCredentials by mutableStateOf(store.loadRiderCredentials())
        private set
    var legalDocuments by mutableStateOf(store.loadLegalDocuments())
        private set
    var legalAcceptances by mutableStateOf(store.loadLegalAcceptances())
        private set
    var payments by mutableStateOf(store.loadPayments())
        private set
    var ratings by mutableStateOf(store.loadRatings())
        private set
    var draft by mutableStateOf(OrderDraft())
    var pendingCustomer by mutableStateOf<Customer?>(null)
    private var authenticatedRiderId: String? = null

    init {
        reconcileCurrentCustomerOrderIdentity()
    }

    fun registerPending(c: Customer) { pendingCustomer = c }

    fun applyGoogleIdentity(uid: String, email: String?, displayName: String?) {
        val pending = pendingCustomer ?: return
        pendingCustomer = pending.copy(
            name = pending.name.ifBlank { displayName.orEmpty() },
            accountId = "G-" + uid,
            googleEmail = email.orEmpty(),
            googleVerified = true
        )
    }

    fun applyDevelopmentIdentity() {
        if (!BuildConfig.DEBUG) return
        val pending = pendingCustomer ?: return
        val devId = if (pending.nationalNumber.isNotBlank()) pending.nationalNumber else System.currentTimeMillis().toString()
        pendingCustomer = pending.copy(
            accountId = "DEV-" + devId,
            googleVerified = false
        )
    }

    fun markPendingWhatsappVerified(rawPhone: String? = null): Boolean {
        val pending = pendingCustomer ?: return false
        val parsed = rawPhone?.let(::parseArgentineWhatsappNumber)
        val verified = if (parsed != null) {
            pending.copy(
                areaCode = parsed.first,
                subscriber = parsed.second,
                locality = areaCodes[parsed.first]?.locality ?: pending.locality,
                whatsappVerified = true,
                whatsappVerifiedAt = nowText()
            )
        } else {
            if (!BuildConfig.DEBUG && rawPhone.isNullOrBlank()) return false
            pending.copy(whatsappVerified = true, whatsappVerifiedAt = nowText())
        }
        pendingCustomer = verified
        return true
    }

    private fun parseArgentineWhatsappNumber(raw: String): Pair<String, String>? {
        var digits = raw.filter(Char::isDigit)
        if (digits.startsWith("54")) digits = digits.drop(2)
        if (digits.startsWith("9")) digits = digits.drop(1)
        if (digits.length != 10) return null
        val area = areaCodes.keys.filter { digits.startsWith(it) }
            .maxByOrNull { it.length } ?: return null
        val info = areaCodes[area] ?: return null
        val subscriber = digits.drop(area.length)
        return if (subscriber.length == info.subscriberDigits) area to subscriber else null
    }

    fun confirmRegistration(): Boolean {
        val pending = pendingCustomer ?: return false
        if (!pending.whatsappVerified) return false
        customer = pending
        reconcileCurrentCustomerOrderIdentity()
        store.saveCustomer(pending)
        pendingCustomer = null
        return true
    }

    fun logoutCustomer() {
        customer = null
        pendingCustomer = null
        store.clearCustomer()
    }

    fun updateConfig(newConfig: AdminConfig) {
        val switchingToSimple = config.operationMode != OperationMode.SIMPLE_WHATSAPP &&
            newConfig.operationMode == OperationMode.SIMPLE_WHATSAPP
        config = newConfig
        store.saveConfig(newConfig)
        if (switchingToSimple) {
            riders = riders.map { it.copy(available = false, availableUntilAt = null) }
            store.saveRiders(riders)
        }
    }

    fun resetDraft(type: ServiceType) {
        val category = if (type == ServiceType.SHOPPING) ServiceCategory.PURCHASE else ServiceCategory.ERRAND
        draft = OrderDraft(serviceType = type, category = category)
    }

    fun resetDraftForCategory(category: ServiceCategory) {
        draft = OrderDraft(
            serviceType = if (category == ServiceCategory.PURCHASE) ServiceType.SHOPPING else ServiceType.DELIVERY,
            category = category
        )
    }

    fun zone(id: String): ZoneConfig? = config.zones.firstOrNull { it.id == id }

    fun addZone(name: String, description: String, category: String, price: Int): Boolean {
        val clean = name.trim()
        if (clean.isBlank()) return false
        val id = "zone-${System.currentTimeMillis()}"
        updateConfig(config.copy(zones = config.zones + ZoneConfig(id, clean, description.trim(), category.trim().ifBlank { "OTRAS" }, price.coerceAtLeast(0), true)))
        return true
    }

    fun updateZone(id: String, name: String, description: String, category: String, price: Int, enabled: Boolean): Boolean {
        val clean = name.trim()
        if (clean.isBlank()) return false
        if (config.zones.none { it.id == id }) return false
        updateConfig(config.copy(zones = config.zones.map {
            if (it.id == id) it.copy(name = clean, description = description.trim(), category = category.trim().ifBlank { "OTRAS" }, price = price.coerceAtLeast(0), enabled = enabled) else it
        }))
        return true
    }

    fun order(id: String?): LocalOrder? = orders.firstOrNull { it.id == id }
    fun rider(id: String?): RiderProfile? = riders.firstOrNull { it.id == id }

    fun customerOwnsOrder(order: LocalOrder): Boolean =
        customer?.let { orderBelongsToCustomer(order, it) } == true

    fun customerOrders(): List<LocalOrder> = orders.filter(::customerOwnsOrder)

    fun customerOrder(id: String?): LocalOrder? =
        order(id)?.takeIf(::customerOwnsOrder)

    private fun reconcileCurrentCustomerOrderIdentity() {
        val current = customer ?: return
        val currentId = current.id
        var changed = false
        val reconciled = orders.map { existing ->
            if (existing.customerId != currentId && orderBelongsToCustomer(existing, current)) {
                changed = true
                existing.copy(customerId = currentId)
            } else {
                existing
            }
        }
        if (changed) orders = reconciled
    }

    fun pricing(d: OrderDraft = draft): PricingResult {
        val ids = mutableListOf<String>()
        if (d.serviceType == ServiceType.DELIVERY) {
            ids += d.originZoneId
            ids += d.destinationZoneId
        } else {
            if (d.storeZoneId.isNotBlank()) ids += d.storeZoneId
            ids += d.destinationZoneId
            if (d.requiresPrePickup()) ids += d.prePickupZoneId
        }
        if (ids.any { it.isBlank() || it == UNKNOWN_ZONE_ID }) {
            return PricingResult(true, null, null, null, if (config.rainEnabled) config.rainAmount else 0, null, "Zona a confirmar")
        }
        val zones = ids.mapNotNull { zone(it) }
        if (zones.size != ids.size) return PricingResult(true, null, null, null, null, null, "Zona inválida")
        if (zones.any { !it.enabled }) return PricingResult(true, null, null, null, null, null, "Hay una zona deshabilitada")
        if (zones.any { it.price <= 0 }) return PricingResult(true, null, null, null, null, null, "Falta configurar una tarifa")
        val baseZone = zones.maxBy { it.price }
        val pre = if (d.serviceType == ServiceType.SHOPPING && d.requiresPrePickup()) {
            when (config.prePickupMode) {
                PrePickupMode.OFF -> 0
                PrePickupMode.FIXED -> config.prePickupValue
                PrePickupMode.PERCENT_BASE -> percentOfBaseRoundedUpToHundred(baseZone.price, config.prePickupValue)
            }
        } else 0
        val rain = if (config.rainEnabled) config.rainAmount else 0
        return PricingResult(false, baseZone.price, baseZone.name, pre, rain, baseZone.price + pre + rain)
    }

    fun createOrder(): LocalOrder {
        val c = requireNotNull(customer)
        val p = pricing(draft)
        val id = newPublicCode()
        val created = nowText()
        val status = if (p.needsQuote) OrderStatus.AWAITING_QUOTE else OrderStatus.PENDING
        val detail = buildDetail(draft, p)
        val message = buildWhatsAppMessage(id, c, draft, p)
        val order = LocalOrder(
            id = id,
            createdAt = created,
            serviceType = draft.serviceType,
            category = draft.category,
            operationMode = config.operationMode,
            status = status,
            customerName = c.name,
            customerPhone = c.displayPhone,
            customerId = c.id,
            detail = detail,
            baseAmount = p.baseAmount,
            baseZoneName = p.baseZoneName,
            prePickupAmount = p.prePickupAmount,
            rainAmount = p.rainAmount,
            totalAmount = p.totalAmount,
            whatsappMessage = message,
            originLocation = draft.originLocation,
            destinationLocation = draft.destinationLocation,
            storeLocation = draft.storeLocation,
            prePickupLocation = draft.prePickupLocation,
            events = listOf(
                OrderEvent(
                    type = OrderEventType.CREATED,
                    at = created,
                    status = status,
                    note = if (status == OrderStatus.AWAITING_QUOTE) "Solicitud creada · tarifa a confirmar" else "Solicitud creada",
                    actor = "CLIENTE"
                )
            ),
            originAddress = draft.originAddress,
            originReference = draft.originReference,
            originZoneId = draft.originZoneId,
            destinationAddress = draft.destinationAddress,
            destinationReference = draft.destinationReference,
            destinationZoneId = draft.destinationZoneId,
            carriedItem = draft.carriedItem,
            instructionType = draft.instructionType,
            purchaseDescription = draft.purchaseDescription,
            purchaseMaxAmount = draft.purchaseMaxAmount,
            storeName = draft.storeName,
            storeAddress = draft.storeAddress,
            storeZoneId = draft.storeZoneId,
            purchasePayment = draft.purchasePayment,
            prePickupAddress = draft.prePickupAddress,
            prePickupReference = draft.prePickupReference,
            prePickupZoneId = draft.prePickupZoneId,
            sameDeliveryAsPrePickup = draft.sameDeliveryAsPrePickup,
            deliveryPayment = draft.deliveryPayment,
            notes = draft.notes
        )
        orders = listOf(order) + orders
        store.saveOrders(orders)
        if (order.operationMode == OperationMode.MULTI_RIDER) ensurePaymentRecord(order)
        return order
    }

    fun cancelOrderByCustomer(id: String): Boolean {
        val o = customerOrder(id) ?: return false
        if (o.status != OrderStatus.PENDING && o.status != OrderStatus.AWAITING_QUOTE) return false
        updateOrderStatus(id, OrderStatus.CANCELLED, "Cancelado por el cliente", "CLIENTE")
        return true
    }

    fun updateOrderStatus(id: String, status: OrderStatus, note: String? = null, actor: String? = null) {
        val o = order(id) ?: return
        if (o.status == status) return
        val event = OrderEvent(
            type = eventTypeForStatus(status),
            at = nowText(),
            status = status,
            riderId = o.assignedRiderId,
            note = note,
            actor = actor ?: o.assignedRiderId ?: "ADMIN"
        )
        updateOrder(o.copy(status = status, events = o.events + event))
    }

    fun canAssignRider(order: LocalOrder): Boolean =
        order.operationMode == OperationMode.MULTI_RIDER &&
            order.status != OrderStatus.CANCELLED &&
            order.status != OrderStatus.REJECTED &&
            order.status != OrderStatus.COMPLETED &&
            order.status != OrderStatus.AWAITING_QUOTE

    fun effectiveRiderLimit(rider: RiderProfile): Int =
        (rider.maxConcurrentOrdersOverride ?: config.defaultMaxConcurrentOrders).coerceAtLeast(1)

    fun riderActiveCount(riderId: String): Int =
        orders.count {
            it.assignedRiderId == riderId &&
                it.status in setOf(OrderStatus.PENDING, OrderStatus.ACCEPTED, OrderStatus.IN_PROGRESS)
        }

    fun riderHasCapacity(rider: RiderProfile, excludingOrderId: String? = null): Boolean {
        val count = orders.count {
            it.id != excludingOrderId &&
                it.assignedRiderId == rider.id &&
                it.status in setOf(OrderStatus.PENDING, OrderStatus.ACCEPTED, OrderStatus.IN_PROGRESS)
        }
        return count < effectiveRiderLimit(rider)
    }

    fun assignRider(orderId: String, riderId: String?): Boolean {
        val o = order(orderId) ?: return false
        if (!canAssignRider(o)) return false
        if (o.assignedRiderId == riderId) return true
        val now = nowText()
        val events = o.events.toMutableList()

        o.assignedRiderId?.let { old ->
            events += OrderEvent(
                type = OrderEventType.RIDER_UNASSIGNED,
                at = now,
                status = o.status,
                riderId = old,
                note = "Repartidor desasignado",
                actor = "ADMIN"
            )
        }

        if (riderId != null) {
            val target = rider(riderId) ?: return false
            if (!target.active || target.approvalStatus != RiderApprovalStatus.APPROVED) return false
            if (!riderHasActiveShiftNow(riderId)) return false
            if (!riderHasCapacity(target, excludingOrderId = orderId)) return false
            events += OrderEvent(
                type = OrderEventType.RIDER_ASSIGNED,
                at = now,
                status = o.status,
                riderId = riderId,
                note = "Repartidor asignado",
                actor = "ADMIN"
            )
        }

        val updated = o.copy(assignedRiderId = riderId, events = events)
        updateOrder(updated)
        ensurePaymentRecord(updated)
        return true
    }

    fun takeOrder(orderId: String, riderId: String): Boolean {
        val o = order(orderId) ?: return false
        if (o.operationMode != OperationMode.MULTI_RIDER) return false
        val target = rider(riderId) ?: return false
        if (!target.active || !isRiderCurrentlyAvailable(riderId) || target.approvalStatus != RiderApprovalStatus.APPROVED) return false
        if (!riderHasActiveShiftNow(riderId)) return false
        if (!riderHasCapacity(target)) return false
        if (o.status != OrderStatus.PENDING || o.assignedRiderId != null) return false
        val now = nowText()
        val updated = o.copy(
            status = OrderStatus.ACCEPTED,
            assignedRiderId = riderId,
            events = o.events + listOf(
                OrderEvent(OrderEventType.RIDER_ASSIGNED, now, OrderStatus.PENDING, riderId, "Pedido tomado", riderId),
                OrderEvent(OrderEventType.ACCEPTED, now, OrderStatus.ACCEPTED, riderId, "Pedido aceptado por el Repartidor", riderId)
            )
        )
        updateOrder(updated)
        ensurePaymentRecord(updated)
        return true
    }

    fun draftFromOrder(order: LocalOrder): OrderDraft = OrderDraft(
        serviceType = order.serviceType,
        category = order.category,
        originAddress = order.originAddress,
        originReference = order.originReference,
        originZoneId = order.originZoneId,
        originLocation = order.originLocation,
        destinationAddress = order.destinationAddress,
        destinationReference = order.destinationReference,
        destinationZoneId = order.destinationZoneId,
        destinationLocation = order.destinationLocation,
        carriedItem = order.carriedItem,
        instructionType = order.instructionType,
        purchaseDescription = order.purchaseDescription,
        purchaseMaxAmount = order.purchaseMaxAmount,
        storeName = order.storeName,
        storeAddress = order.storeAddress,
        storeZoneId = order.storeZoneId,
        storeLocation = order.storeLocation,
        purchasePayment = order.purchasePayment,
        prePickupAddress = order.prePickupAddress,
        prePickupReference = order.prePickupReference,
        prePickupZoneId = order.prePickupZoneId,
        prePickupLocation = order.prePickupLocation,
        sameDeliveryAsPrePickup = order.sameDeliveryAsPrePickup,
        deliveryPayment = order.deliveryPayment,
        notes = order.notes
    )

    fun canEditOrder(order: LocalOrder): Boolean =
        order.operationMode == OperationMode.MULTI_RIDER &&
            order.status != OrderStatus.CANCELLED &&
            order.status != OrderStatus.COMPLETED

    fun editOrder(orderId: String, edited: OrderDraft, reason: String): Boolean {
        val o = order(orderId) ?: return false
        if (!canEditOrder(o)) return false
        if (reason.trim().isBlank()) return false

        val p = pricing(edited)
        val updated = o.copy(
            serviceType = edited.serviceType,
            category = edited.category,
            detail = buildDetail(edited, p),
            baseAmount = p.baseAmount,
            baseZoneName = p.baseZoneName,
            prePickupAmount = p.prePickupAmount,
            rainAmount = p.rainAmount,
            totalAmount = p.totalAmount,
            originLocation = edited.originLocation,
            destinationLocation = edited.destinationLocation,
            storeLocation = edited.storeLocation,
            prePickupLocation = edited.prePickupLocation,
            originAddress = edited.originAddress,
            originReference = edited.originReference,
            originZoneId = edited.originZoneId,
            destinationAddress = edited.destinationAddress,
            destinationReference = edited.destinationReference,
            destinationZoneId = edited.destinationZoneId,
            carriedItem = edited.carriedItem,
            instructionType = edited.instructionType,
            purchaseDescription = edited.purchaseDescription,
            purchaseMaxAmount = edited.purchaseMaxAmount,
            storeName = edited.storeName,
            storeAddress = edited.storeAddress,
            storeZoneId = edited.storeZoneId,
            purchasePayment = edited.purchasePayment,
            prePickupAddress = edited.prePickupAddress,
            prePickupReference = edited.prePickupReference,
            prePickupZoneId = edited.prePickupZoneId,
            sameDeliveryAsPrePickup = edited.sameDeliveryAsPrePickup,
            deliveryPayment = edited.deliveryPayment,
            notes = edited.notes,
            events = o.events + OrderEvent(
                type = OrderEventType.ORDER_EDITED,
                at = nowText(),
                status = o.status,
                riderId = o.assignedRiderId,
                note = reason.trim(),
                actor = "ADMIN"
            )
        )
        updateOrder(updated)
        ensurePaymentRecord(updated)
        return true
    }

    private fun updateOrder(updated: LocalOrder) {
        orders = orders.map { if (it.id == updated.id) updated else it }
        store.saveOrders(orders)
    }

    fun saveRider(
        id: String?,
        name: String,
        phone: String,
        birthDate: String,
        vehicleType: VehicleType,
        address: String,
        documents: RiderDocuments,
        maxConcurrentOrdersOverride: Int? = null
    ): String? {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return null
        val cleanPhone = phone.filter(Char::isDigit)
        val targetId = id ?: newRiderId()

        riders = if (id == null) {
            val reviews = RiderDocumentKey.entries.associateWith { key ->
                if (documents.uriFor(key).isNullOrBlank()) DocumentReviewStatus.NOT_UPLOADED else DocumentReviewStatus.PENDING
            }
            riders + RiderProfile(
                id = targetId,
                name = cleanName,
                phone = cleanPhone,
                birthDate = birthDate.trim(),
                vehicleType = vehicleType,
                address = address.trim(),
                transferAlias = "",
                maxConcurrentOrdersOverride = maxConcurrentOrdersOverride?.coerceAtLeast(1),
                documents = documents,
                approvalStatus = RiderApprovalStatus.PENDING,
                documentReviews = reviews,
                active = true,
                available = false
            )
        } else {
            riders.map { current ->
                if (current.id == id) {
                    val reviews = RiderDocumentKey.entries.associateWith { key ->
                        val oldUri = current.documents.uriFor(key)
                        val newUri = documents.uriFor(key)
                        when {
                            newUri.isNullOrBlank() -> DocumentReviewStatus.NOT_UPLOADED
                            newUri != oldUri -> DocumentReviewStatus.PENDING
                            else -> current.reviewFor(key)
                        }
                    }
                    current.copy(
                        name = cleanName,
                        phone = cleanPhone,
                        birthDate = birthDate.trim(),
                        vehicleType = vehicleType,
                        address = address.trim(),
                        maxConcurrentOrdersOverride = maxConcurrentOrdersOverride?.coerceAtLeast(1),
                        documents = documents,
                        documentReviews = reviews
                    )
                } else current
            }
        }
        store.saveRiders(riders)
        return targetId
    }

    fun setRiderApprovalStatus(id: String, status: RiderApprovalStatus) {
        riders = riders.map {
            if (it.id == id) it.copy(
                approvalStatus = status,
                available = if (status == RiderApprovalStatus.APPROVED && it.active) it.available else false,
                availableUntilAt = if (status == RiderApprovalStatus.APPROVED && it.active) it.availableUntilAt else null
            ) else it
        }
        store.saveRiders(riders)
    }

    fun setRiderDocumentReview(id: String, key: RiderDocumentKey, status: DocumentReviewStatus, note: String = "") {
        riders = riders.map { r ->
            if (r.id != id) return@map r
            val effective = if (r.documents.uriFor(key).isNullOrBlank()) DocumentReviewStatus.NOT_UPLOADED else status
            r.copy(
                documentReviews = r.documentReviews + (key to effective),
                documentNotes = if (note.isBlank()) r.documentNotes - key else r.documentNotes + (key to note.trim())
            )
        }
        store.saveRiders(riders)
    }

    fun setRiderActive(id: String, active: Boolean) {
        riders = riders.map {
            if (it.id == id) it.copy(
                active = active,
                available = if (active) it.available else false,
                availableUntilAt = if (active) it.availableUntilAt else null
            ) else it
        }
        store.saveRiders(riders)
    }

    fun setRiderAvailable(id: String, available: Boolean): Boolean {
        val target = rider(id) ?: return false
        val canEnable = target.active &&
            target.approvalStatus == RiderApprovalStatus.APPROVED &&
            config.operationMode == OperationMode.MULTI_RIDER &&
            riderHasActiveShiftNow(id)
        if (available && !canEnable) return false
        val until = if (available) LocalDateTime.now().plusMinutes(30).format(timestampFormat) else null
        riders = riders.map {
            if (it.id == id) it.copy(available = available && canEnable, availableUntilAt = until) else it
        }
        store.saveRiders(riders)
        return true
    }

    fun updateRiderTransferAlias(id: String, alias: String): Boolean {
        val target = rider(id) ?: return false
        val clean = alias.trim()
        riders = riders.map { if (it.id == id) it.copy(transferAlias = clean) else it }
        store.saveRiders(riders)
        return true
    }

    fun availabilityRemainingSeconds(id: String): Long {
        val target = rider(id) ?: return 0
        val until = target.availableUntilAt?.let(::parseTimestamp) ?: return 0
        return Duration.between(LocalDateTime.now(), until).seconds.coerceAtLeast(0)
    }

    fun isRiderCurrentlyAvailable(id: String): Boolean {
        expireAvailabilityIfNeeded(id)
        val target = rider(id) ?: return false
        return target.available && target.active && target.approvalStatus == RiderApprovalStatus.APPROVED &&
            config.operationMode == OperationMode.MULTI_RIDER && riderHasActiveShiftNow(id)
    }

    private fun expireAvailabilityIfNeeded(id: String) {
        val target = rider(id) ?: return
        val until = target.availableUntilAt?.let(::parseTimestamp)
        val mustDisable = target.available && (until == null || !LocalDateTime.now().isBefore(until) || !riderHasActiveShiftNow(id))
        if (mustDisable) {
            riders = riders.map { if (it.id == id) it.copy(available = false, availableUntilAt = null) else it }
            store.saveRiders(riders)
        }
    }

    fun riderCompletedOrders(riderId: String): List<LocalOrder> =
        orders.filter { it.assignedRiderId == riderId && it.status == OrderStatus.COMPLETED }

    fun riderTotalOrders(riderId: String): Int =
        orders.count { order ->
            order.assignedRiderId == riderId ||
                order.events.any { it.type == OrderEventType.RIDER_ASSIGNED && it.riderId == riderId }
        }

    fun riderBalanceForOrders(riderId: String, items: List<LocalOrder>): Int =
        calculateRiderBalance(riderId, items, ratings)

    fun riderCurrentBalance(riderId: String): Int =
        riderBalanceForOrders(riderId, riderCompletedOrders(riderId))

    fun deliveryDurationSeconds(order: LocalOrder): Long? {
        val completedEvent = order.events.lastOrNull { it.type == OrderEventType.COMPLETED } ?: return null
        val completedAt = parseTimestamp(completedEvent.at) ?: return null
        val targetRider = completedEvent.riderId ?: order.assignedRiderId
        val assignedEvent = order.events
            .filter { it.type == OrderEventType.RIDER_ASSIGNED && (targetRider == null || it.riderId == targetRider) }
            .lastOrNull { event ->
                val at = parseTimestamp(event.at)
                at != null && !at.isAfter(completedAt)
            }
            ?: order.events.lastOrNull {
                it.type == OrderEventType.ACCEPTED && (targetRider == null || it.riderId == targetRider)
            }
            ?: return null
        val assignedAt = parseTimestamp(assignedEvent.at) ?: return null
        return Duration.between(assignedAt, completedAt).seconds.takeIf { it >= 0 }
    }

    fun averageDeliverySeconds(items: List<LocalOrder> = orders): Long? {
        val values = items.mapNotNull(::deliveryDurationSeconds)
        if (values.isEmpty()) return null
        return values.sum() / values.size
    }

    fun riderAverageDeliverySeconds(riderId: String): Long? =
        averageDeliverySeconds(riderCompletedOrders(riderId))

    fun pendingRatingForCustomer(): LocalOrder? {
        if (customer == null) return null
        return orders
            .filter { customerOwnsOrder(it) && it.status == OrderStatus.COMPLETED && it.assignedRiderId != null }
            .sortedByDescending { parseTimestamp(it.createdAt) ?: LocalDateTime.MIN }
            .firstOrNull { order -> ratings.none { it.orderId == order.id } }
    }

    fun submitRating(orderId: String, stars: Int, tags: List<String>, comment: String, tipAmount: Int): Boolean {
        val c = customer ?: return false
        val o = customerOrder(orderId) ?: return false
        val riderId = o.assignedRiderId ?: return false
        if (o.status != OrderStatus.COMPLETED) return false
        if (ratings.any { it.orderId == orderId }) return false
        val amount = normalizedDigitalTipAmount(o, config.paymentConfig.tipsEnabled, tipAmount)
        val rating = OrderRating(
            orderId = orderId,
            customerId = c.id,
            riderId = riderId,
            stars = stars.coerceIn(1, 5),
            tags = tags,
            comment = comment.trim(),
            tipAmount = amount,
            tipStatus = if (amount > 0) TipStatus.SELECTED else TipStatus.NONE,
            createdAt = nowText()
        )
        ratings = ratings + rating
        store.saveRatings(ratings)
        val current = order(orderId) ?: return true
        updateOrder(current.copy(events = current.events + OrderEvent(
            OrderEventType.RATING_SUBMITTED, nowText(), current.status, riderId, "Calificación: ${rating.stars}/5", c.id
        )))
        return true
    }

    fun riderRatingAverage(riderId: String): Double? {
        val own = ratings.filter { it.riderId == riderId }
        return if (own.isEmpty()) null else own.map { it.stars }.average()
    }

    fun riderRatingCount(riderId: String): Int = ratings.count { it.riderId == riderId }

    fun canOfferDigitalTip(order: LocalOrder): Boolean =
        canOfferDigitalTip(order, config.paymentConfig.tipsEnabled)

    fun customerDigitalTipForOrder(orderId: String): OrderRating? {
        val ownOrder = customerOrder(orderId) ?: return null
        val rating = ratings.firstOrNull { it.orderId == orderId } ?: return null
        return rating.takeIf { isDigitalTipRecord(ownOrder, it) }
    }

    fun riderDigitalTipForOrder(orderId: String, riderId: String): OrderRating? {
        if (!hasAuthenticatedRiderSession(riderId)) return null
        val ownOrder = order(orderId) ?: return null
        val rating = ratings.firstOrNull { it.orderId == orderId } ?: return null
        return rating.takeIf {
            tipBelongsToAssignedRider(ownOrder, it, riderId) && it.tipStatus != TipStatus.NONE
        }
    }

    fun confirmedDigitalTipAmountForOrder(orderId: String, riderId: String): Int {
        if (rider(riderId) == null) return 0
        val ownOrder = order(orderId) ?: return 0
        val rating = ratings.firstOrNull { it.orderId == orderId } ?: return 0
        return confirmedDigitalTipAmount(ownOrder, rating, riderId)
    }

    fun customerCanDeclareTipTransfer(orderId: String): Boolean {
        if (customer == null) return false
        val ownOrder = customerOrder(orderId) ?: return false
        val rating = ratings.firstOrNull { it.orderId == orderId } ?: return false
        return canCustomerDeclareTipTransfer(ownOrder, rating) ||
            isCustomerTipDeclarationIdempotent(ownOrder, rating)
    }

    fun declareTipTransfer(orderId: String): Boolean {
        val currentCustomer = customer ?: return false
        val ownOrder = customerOrder(orderId) ?: return false
        val rating = ratings.firstOrNull { it.orderId == orderId } ?: return false
        val updated = declareTipTransferState(ownOrder, rating) ?: return false
        if (updated == rating) return true
        ratings = ratings.map { if (it.orderId == orderId) updated else it }
        store.saveRatings(ratings)
        appendTipEvent(
            orderId,
            OrderEventType.TIP_TRANSFER_DECLARED,
            "Cliente informó la transferencia adicional de la propina",
            currentCustomer.id
        )
        return true
    }

    fun pendingTipsForRider(riderId: String): List<OrderRating> {
        if (!hasAuthenticatedRiderSession(riderId)) return emptyList()
        return ratings.filter { rating ->
            val ownOrder = order(rating.orderId) ?: return@filter false
            canRiderConfirmTipTransfer(ownOrder, rating, riderId)
        }.sortedByDescending { parseTimestamp(it.createdAt) ?: LocalDateTime.MIN }
    }

    fun riderCanConfirmTip(orderId: String, riderId: String): Boolean {
        if (!hasAuthenticatedRiderSession(riderId)) return false
        val ownOrder = order(orderId) ?: return false
        val rating = ratings.firstOrNull { it.orderId == orderId } ?: return false
        return canRiderConfirmTipTransfer(ownOrder, rating, riderId) ||
            isRiderTipConfirmationIdempotent(ownOrder, rating, riderId)
    }

    fun confirmTip(orderId: String, riderId: String): Boolean {
        if (!hasAuthenticatedRiderSession(riderId)) return false
        val ownOrder = order(orderId) ?: return false
        val rating = ratings.firstOrNull { it.orderId == orderId } ?: return false
        val updated = confirmTipTransferState(ownOrder, rating, riderId) ?: return false
        if (updated == rating) return true
        ratings = ratings.map { if (it.orderId == orderId) updated else it }
        store.saveRatings(ratings)
        appendTipEvent(
            orderId,
            OrderEventType.TIP_TRANSFER_CONFIRMED,
            "Propina acreditada confirmada por el Repartidor",
            riderId
        )
        return true
    }

    private fun appendTipEvent(orderId: String, type: OrderEventType, note: String, actor: String) {
        val ownOrder = order(orderId) ?: return
        updateOrder(ownOrder.copy(events = ownOrder.events + OrderEvent(
            type, nowText(), ownOrder.status, ownOrder.assignedRiderId, note, actor
        )))
    }

    fun paymentForOrder(orderId: String): PaymentRecord? = payments.lastOrNull { it.orderId == orderId }

    private fun ensurePaymentRecord(order: LocalOrder) {
        val channel = when (order.deliveryPayment) {
  DeliveryPaymentMethod.CASH -> PaymentChannel.CASH
  DeliveryPaymentMethod.TRANSFER -> PaymentChannel.RIDER_TRANSFER
  DeliveryPaymentMethod.QR -> PaymentChannel.QR_INTEROPERABLE
  DeliveryPaymentMethod.ONLINE -> PaymentChannel.ONLINE_CHECKOUT
        }
        val existing = paymentForOrder(order.id)
        val now = nowText()
        val record = if (existing == null) {
  PaymentRecord(
      id = "PAY-${order.id}",
      orderId = order.id,
      riderId = order.assignedRiderId,
      channel = channel,
      expectedAmount = order.totalAmount ?: 0,
      createdAt = now,
      updatedAt = now
  )
        } else {
  existing.copy(
      riderId = order.assignedRiderId,
      channel = channel,
      expectedAmount = order.totalAmount ?: existing.expectedAmount,
      updatedAt = now
  )
        }
        payments = if (existing == null) payments + record else payments.map { if (it.id == record.id) record else it }
        store.savePayments(payments)
    }

    private fun transfersForRider(riderId: String, statuses: Set<PaymentStatus>): List<PaymentRecord> =
        payments.filter { payment ->
  val ownOrder = order(payment.orderId)
  payment.status in statuses && ownOrder != null && riderOwnsTransfer(ownOrder, payment, riderId)
        }.sortedByDescending { parseTimestamp(it.updatedAt) ?: LocalDateTime.MIN }

    fun riderPendingTransfers(riderId: String): List<PaymentRecord> =
        transfersForRider(riderId, transferPendingStatuses)

    fun riderCompletedTransfers(riderId: String): List<PaymentRecord> =
        transfersForRider(riderId, setOf(PaymentStatus.CONFIRMED))

    fun riderTransferAttentionCount(riderId: String): Int =
        riderPendingTransfers(riderId).count(::transferRequiresAttention)

    fun riderCanAccessTransfer(orderId: String, riderId: String): Boolean {
        val ownOrder = order(orderId) ?: return false
        val payment = paymentForOrder(orderId) ?: return false
        return riderOwnsTransfer(ownOrder, payment, riderId)
    }

    fun riderCanConfirmTransfer(orderId: String, riderId: String): Boolean {
        val ownOrder = order(orderId) ?: return false
        val payment = paymentForOrder(orderId) ?: return false
        return canRiderConfirmTransfer(ownOrder, payment, riderId, config.paymentConfig.transferProofRequired)
    }

    fun riderCanReportTransfer(orderId: String, riderId: String): Boolean {
        val ownOrder = order(orderId) ?: return false
        val payment = paymentForOrder(orderId) ?: return false
        return canRiderReportTransfer(ownOrder, payment, riderId)
    }

    fun declarePayment(orderId: String): Boolean {
        val currentCustomer = customer ?: return false
        if (customerOrder(orderId) == null) return false
        val payment = paymentForOrder(orderId) ?: return false
        if (!canCustomerDeclareTransfer(payment)) return false
        val updated = payment.copy(status = PaymentStatus.DECLARED, updatedAt = nowText())
        payments = payments.map { if (it.id == updated.id) updated else it }
        store.savePayments(payments)
        appendPaymentEvent(
  orderId,
  OrderEventType.PAYMENT_DECLARED,
  "Cliente informó que realizó la transferencia",
  currentCustomer.id
        )
        return true
    }

    fun attachTransferProof(orderId: String, uri: String): Boolean {
        val currentCustomer = customer ?: return false
        if (customerOrder(orderId) == null || uri.isBlank()) return false
        val payment = paymentForOrder(orderId) ?: return false
        if (!canCustomerAttachTransferProof(payment)) return false
        val updated = payment.copy(
  proofUri = uri,
  status = PaymentStatus.PROOF_UPLOADED,
  updatedAt = nowText()
        )
        payments = payments.map { if (it.id == updated.id) updated else it }
        store.savePayments(payments)
        appendPaymentEvent(
  orderId,
  OrderEventType.PAYMENT_PROOF_ATTACHED,
  "Comprobante de transferencia adjunto",
  currentCustomer.id
        )
        return true
    }

    fun confirmPaymentByRider(orderId: String, riderId: String): Boolean {
        if (!riderCanConfirmTransfer(orderId, riderId)) return false
        val payment = paymentForOrder(orderId) ?: return false
        val updated = payment.copy(status = PaymentStatus.CONFIRMED, updatedAt = nowText())
        payments = payments.map { if (it.id == updated.id) updated else it }
        store.savePayments(payments)
        appendPaymentEvent(
  orderId,
  OrderEventType.PAYMENT_CONFIRMED,
  "Acreditación confirmada por el Repartidor",
  riderId
        )
        return true
    }

    fun reportPaymentProblem(orderId: String, riderId: String): Boolean {
        if (!riderCanReportTransfer(orderId, riderId)) return false
        val payment = paymentForOrder(orderId) ?: return false
        val updated = payment.copy(status = PaymentStatus.IN_REVIEW, updatedAt = nowText())
        payments = payments.map { if (it.id == updated.id) updated else it }
        store.savePayments(payments)
        appendPaymentEvent(
  orderId,
  OrderEventType.PAYMENT_REVIEW_REQUESTED,
  "El Repartidor informó que no ve acreditado el pago",
  riderId
        )
        return true
    }

    private fun appendPaymentEvent(orderId: String, type: OrderEventType, note: String, actor: String) {
        val o = order(orderId) ?: return
        updateOrder(o.copy(events = o.events + OrderEvent(
  type, nowText(), o.status, o.assignedRiderId, note, actor
        )))
    }

    fun saveLegalDocument(document: LegalDocument) {
        legalDocuments = legalDocuments.filterNot { it.id == document.id } + document.copy(updatedAt = nowText())
        store.saveLegalDocuments(legalDocuments)
    }

    fun saveLegalPdf(
        type: LegalDocumentType,
        version: String,
        effectiveDate: String,
        published: Boolean,
        requireAcceptance: Boolean,
        uriString: String,
        fileNameHint: String? = null
    ): Boolean {
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return false
        val hash = runCatching { sha256ForUri(uri) }.getOrNull() ?: return false
        val fileName = queryDisplayName(uri) ?: fileNameHint?.takeIf { it.isNotBlank() } ?: "documento.pdf"
        val existing = legalDocuments.filter { it.type == type }.maxByOrNull { parseTimestamp(it.updatedAt) ?: LocalDateTime.MIN }
        val cleanVersion = version.trim().ifBlank { "1.0" }
        val changed = existing == null ||
            existing.version != cleanVersion ||
            existing.fileSha256 != hash ||
            existing.effectiveDate != effectiveDate ||
            existing.published != published ||
            existing.requireAcceptance != requireAcceptance
        if (existing?.published == true && changed && existing.version == cleanVersion) return false
        val preservePrevious = existing?.published == true && changed
        val id = if (preservePrevious || existing == null) "LEGAL-${type.name}-${System.currentTimeMillis()}" else existing.id
        saveLegalDocument(
            LegalDocument(
                id = id,
                type = type,
                version = cleanVersion,
                title = legalTitle(type),
                effectiveDate = effectiveDate,
                published = published,
                requireAcceptance = requireAcceptance,
                updatedAt = nowText(),
                fileUri = uriString,
                fileName = fileName,
                fileSha256 = hash
            )
        )
        return true
    }

    private fun sha256ForUri(uri: Uri): String {
        val digest = MessageDigest.getInstance("SHA-256")
        appContext.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        } ?: error("No se pudo abrir el PDF")
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun queryDisplayName(uri: Uri): String? =
        runCatching {
            appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()

    private fun legalTitle(type: LegalDocumentType): String = when (type) {
        LegalDocumentType.TERMS -> "Términos y Condiciones"
        LegalDocumentType.PRIVACY -> "Política de Privacidad"
        LegalDocumentType.CANCELLATIONS -> "Cancelaciones, arrepentimiento y reembolsos"
        LegalDocumentType.LOCATION_PERMISSIONS -> "Ubicación y permisos"
        LegalDocumentType.RIDER_TERMS -> "Condiciones del Repartidor"
    }

    fun publishedLegalDocument(type: LegalDocumentType): LegalDocument? =
        legalDocuments.filter { it.type == type && it.published }.maxByOrNull { parseTimestamp(it.updatedAt) ?: LocalDateTime.MIN }

    fun requiredLegalDocumentsForCustomer(): List<LegalDocument> {
        val c = customer ?: return emptyList()
        return LegalDocumentType.entries
            .filter { it != LegalDocumentType.RIDER_TERMS }
            .mapNotNull(::publishedLegalDocument)
            .filter { it.requireAcceptance }
            .filter { doc ->
                legalAcceptances.none { it.customerId == c.id && it.documentId == doc.id && it.version == doc.version }
            }
    }

    fun acceptRequiredLegalDocuments() {
        val c = customer ?: return
        val pending = requiredLegalDocumentsForCustomer()
        if (pending.isEmpty()) return
        legalAcceptances = legalAcceptances + pending.map { doc ->
            LegalAcceptance(
                customerId = c.id,
                documentId = doc.id,
                documentType = doc.type,
                version = doc.version,
                acceptedAt = nowText()
            )
        }
        store.saveLegalAcceptances(legalAcceptances)
    }

    fun saveShiftTemplate(shift: ShiftTemplate) {
        shifts = shifts.filterNot { it.id == shift.id } + shift
        store.saveShifts(shifts)
    }

    fun isShiftActiveNow(shift: ShiftTemplate, now: LocalDateTime = LocalDateTime.now()): Boolean {
        val candidates = listOf(now.toLocalDate(), now.toLocalDate().minusDays(1))
        return candidates.any { date ->
            val dateText = date.format(serviceDateFormat)
            val applies = if (shift.isSpecificDate) {
                shift.specificDate == dateText
            } else {
                shift.dayOfWeek == date.dayOfWeek.value
            }
            if (!applies) return@any false
            val window = shiftWindow(shift, dateText) ?: return@any false
            !now.isBefore(window.first) && now.isBefore(window.second)
        }
    }

    fun canDeleteShiftTemplate(id: String): Boolean {
        val shift = shifts.firstOrNull { it.id == id } ?: return false
        if (isShiftActiveNow(shift)) return false
        val now = LocalDateTime.now()
        val hasCurrentOrFutureReservations = shiftReservations.any { reservation ->
            if (reservation.shiftTemplateId != id || reservation.status != ShiftReservationStatus.RESERVED) return@any false
            val window = shiftWindow(shift, reservation.serviceDate) ?: return@any false
            window.second.isAfter(now)
        }
        return !hasCurrentOrFutureReservations
    }

    fun deleteShiftTemplate(id: String): Boolean {
        if (!canDeleteShiftTemplate(id)) return false
        if (shifts.none { it.id == id }) return false
        shifts = shifts.filterNot { it.id == id }
        store.saveShifts(shifts)
        return true
    }

    fun addShiftTemplatesBulk(days: Set<Int>, ranges: List<Pair<String, String>>, capacity: Int): String? {
        if (days.isEmpty()) return "Seleccioná al menos un día."
        if (ranges.isEmpty()) return "Agregá al menos un horario."
        if (capacity < 1) return "El cupo debe ser mayor a cero."

        val candidates = mutableListOf<ShiftTemplate>()
        days.sorted().forEach { day ->
            ranges.forEach { (rawStart, rawEnd) ->
                val startTime = normalizeClock(rawStart, allow24 = false) ?: return "Horario de inicio inválido: " + rawStart
                val endTime = normalizeClock(rawEnd, allow24 = true) ?: return "Horario de fin inválido: " + rawEnd
                if (startTime == endTime) return "El inicio y el fin no pueden ser iguales (" + startTime + ")."
                candidates += ShiftTemplate(
                    id = "SHIFT-" + System.currentTimeMillis() + "-" + day + "-" + candidates.size,
                    dayOfWeek = day,
                    startTime = startTime,
                    endTime = endTime,
                    capacity = capacity,
                    specificDate = null
                )
            }
        }

        val existing = shifts.filter { it.enabled && !it.isSpecificDate }
        for (i in candidates.indices) {
            for (j in i + 1 until candidates.size) {
                if (shiftTemplatesOverlap(candidates[i], candidates[j])) {
                    val a = candidates[i]
                    val b = candidates[j]
                    return "Horario duplicado o superpuesto dentro del lote: " + dayName(a.dayOfWeek) + " " + a.startTime + "–" + a.endTime +
                        " con " + dayName(b.dayOfWeek) + " " + b.startTime + "–" + b.endTime + "."
                }
            }
            val conflict = existing.firstOrNull { shiftTemplatesOverlap(it, candidates[i]) }
            if (conflict != null) {
                val candidate = candidates[i]
                return "Horario duplicado o superpuesto: " + dayName(candidate.dayOfWeek) + " " + candidate.startTime + "–" + candidate.endTime +
                    " entra en conflicto con " + dayName(conflict.dayOfWeek) + " " + conflict.startTime + "–" + conflict.endTime + "."
            }
        }
        shifts = shifts + candidates
        store.saveShifts(shifts)
        return null
    }

    fun addSpecificDateShifts(dateText: String, ranges: List<Pair<String, String>>, capacity: Int): String? {
        val date = runCatching { LocalDate.parse(dateText, serviceDateFormat) }.getOrNull()
            ?: return "Fecha inválida. Usá DD/MM/AAAA."
        if (ranges.isEmpty()) return "Agregá al menos un horario."
        if (capacity < 1) return "El cupo debe ser mayor a cero."
        val candidates = mutableListOf<ShiftTemplate>()
        ranges.forEach { (rawStart, rawEnd) ->
            val startTime = normalizeClock(rawStart, allow24 = false) ?: return "Horario de inicio inválido: " + rawStart
            val endTime = normalizeClock(rawEnd, allow24 = true) ?: return "Horario de fin inválido: " + rawEnd
            if (startTime == endTime) return "El inicio y el fin no pueden ser iguales (" + startTime + ")."
            candidates += ShiftTemplate(
                id = "SHIFT-DATE-" + System.currentTimeMillis() + "-" + candidates.size,
                dayOfWeek = date.dayOfWeek.value,
                startTime = startTime,
                endTime = endTime,
                capacity = capacity,
                specificDate = dateText
            )
        }
        for (i in candidates.indices) {
            for (j in i + 1 until candidates.size) {
                if (shiftOccurrenceOverlap(candidates[i], dateText, candidates[j], dateText)) {
                    return "Hay horarios superpuestos dentro del lote para " + dateText + "."
                }
            }
            val conflict = shifts.firstOrNull {
                it.enabled && it.isSpecificDate && it.id != candidates[i].id &&
                    shiftOccurrenceOverlap(it, it.specificDate ?: dateText, candidates[i], dateText)
            }
            if (conflict != null) {
                return "Ese horario se superpone con otro turno específico del " + (conflict.specificDate ?: dateText) + "."
            }
        }
        // Si ya existían reservas sobre el patrón semanal para esta fecha especial,
        // las migramos al turno específico equivalente para no duplicar la ocurrencia.
        val migrationTargets = mutableMapOf<String, String>()
        shifts.filter { it.enabled && !it.isSpecificDate }.forEach { recurring ->
            val target = candidates.firstOrNull { shiftOccurrenceOverlap(recurring, dateText, it, dateText) }
            if (target != null) migrationTargets[recurring.id] = target.id
        }
        candidates.forEach { candidate ->
            val incoming = shiftReservations.count { reservation ->
                reservation.serviceDate == dateText &&
                    reservation.status == ShiftReservationStatus.RESERVED &&
                    migrationTargets[reservation.shiftTemplateId] == candidate.id
            }
            if (incoming > candidate.capacity) {
                return "El cupo del turno especial debe ser al menos " + incoming + " porque ya hay inscripciones en el horario semanal reemplazado."
            }
        }

        shifts = shifts + candidates
        if (migrationTargets.isNotEmpty()) {
            shiftReservations = shiftReservations.map { reservation ->
                if (reservation.serviceDate == dateText && migrationTargets.containsKey(reservation.shiftTemplateId)) {
                    reservation.copy(shiftTemplateId = migrationTargets.getValue(reservation.shiftTemplateId))
                } else reservation
            }
            store.saveShiftReservations(shiftReservations)
        }
        store.saveShifts(shifts)
        return null
    }

    fun updateShiftTemplate(
        id: String,
        recurring: Boolean,
        dayOfWeek: Int,
        specificDate: String?,
        rawStart: String,
        rawEnd: String,
        capacity: Int,
        enabled: Boolean
    ): String? {
        val current = shifts.firstOrNull { it.id == id } ?: return "Turno no encontrado."
        if (capacity < 1) return "El cupo debe ser mayor a cero."
        val startTime = normalizeClock(rawStart, allow24 = false) ?: return "Horario de inicio inválido."
        val endTime = normalizeClock(rawEnd, allow24 = true) ?: return "Horario de fin inválido."
        if (startTime == endTime) return "El inicio y el fin no pueden ser iguales."

        val date = if (recurring) null else runCatching {
            LocalDate.parse(specificDate.orEmpty(), serviceDateFormat)
        }.getOrNull() ?: if (!recurring) return "Fecha inválida. Usá DD/MM/AAAA." else null

        val candidate = current.copy(
            dayOfWeek = if (recurring) dayOfWeek.coerceIn(1, 7) else date!!.dayOfWeek.value,
            startTime = startTime,
            endTime = endTime,
            capacity = capacity,
            enabled = enabled,
            specificDate = if (recurring) null else specificDate
        )

        val maxReserved = shiftReservations
            .filter { it.shiftTemplateId == id && it.status == ShiftReservationStatus.RESERVED }
            .groupingBy { it.serviceDate }.eachCount().values.maxOrNull() ?: 0
        if (capacity < maxReserved) return "El cupo no puede quedar por debajo de " + maxReserved + " porque ya hay inscripciones."

        val conflict = if (candidate.isSpecificDate) {
            shifts.firstOrNull {
                it.id != id && it.enabled && it.isSpecificDate &&
                    shiftOccurrenceOverlap(it, it.specificDate!!, candidate, candidate.specificDate!!)
            }
        } else {
            shifts.firstOrNull { it.id != id && it.enabled && !it.isSpecificDate && shiftTemplatesOverlap(it, candidate) }
        }
        if (conflict != null) return "El turno editado se superpone con otro horario configurado."

        shifts = shifts.map { if (it.id == id) candidate else it }
        store.saveShifts(shifts)
        if (!enabled) {
            riders.filter { riderHasActiveShiftNow(it.id) }.forEach { /* conserva presencia si tiene otro turno */ }
        }
        return null
    }

    fun normalizeClock(raw: String, allow24: Boolean): String? {
        val digits = raw.filter(Char::isDigit)
        if (digits.length != 4) return null
        val hour = digits.substring(0, 2).toIntOrNull() ?: return null
        val minute = digits.substring(2, 4).toIntOrNull() ?: return null
        if (minute !in 0..59) return null
        if (hour == 24) return if (allow24 && minute == 0) "24:00" else null
        if (hour !in 0..23) return null
        return "%02d:%02d".format(hour, minute)
    }

    private fun minuteOfDay(text: String): Int? {
        if (text == "24:00") return 1440
        val parts = text.split(":")
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }

    private fun weeklyInterval(shift: ShiftTemplate): Pair<Int, Int>? {
        val startMinute = minuteOfDay(shift.startTime) ?: return null
        val endMinuteRaw = minuteOfDay(shift.endTime) ?: return null
        val base = (shift.dayOfWeek - 1).coerceIn(0, 6) * 1440
        val startMinuteOfWeek = base + startMinute
        val endMinuteOfWeek = when {
            endMinuteRaw == 1440 -> base + 1440
            endMinuteRaw <= startMinute -> base + 1440 + endMinuteRaw
            else -> base + endMinuteRaw
        }
        return startMinuteOfWeek to endMinuteOfWeek
    }

    private fun shiftTemplatesOverlap(a: ShiftTemplate, b: ShiftTemplate): Boolean {
        val ai = weeklyInterval(a) ?: return true
        val bi = weeklyInterval(b) ?: return true
        val week = 7 * 1440
        fun overlaps(x: Pair<Int, Int>, y: Pair<Int, Int>): Boolean = x.first < y.second && y.first < x.second
        return overlaps(ai, bi) ||
            overlaps(ai, (bi.first + week) to (bi.second + week)) ||
            overlaps((ai.first + week) to (ai.second + week), bi)
    }

    fun shiftWindow(shift: ShiftTemplate, serviceDate: String): Pair<LocalDateTime, LocalDateTime>? {
        val date = runCatching { LocalDate.parse(serviceDate, serviceDateFormat) }.getOrNull() ?: return null
        val startMinutes = minuteOfDay(shift.startTime) ?: return null
        val endMinutes = minuteOfDay(shift.endTime) ?: return null
        if (startMinutes >= 1440) return null
        val startAt = date.atStartOfDay().plusMinutes(startMinutes.toLong())
        val endAt = when {
            endMinutes == 1440 -> date.plusDays(1).atStartOfDay()
            endMinutes <= startMinutes -> date.plusDays(1).atStartOfDay().plusMinutes(endMinutes.toLong())
            else -> date.atStartOfDay().plusMinutes(endMinutes.toLong())
        }
        return startAt to endAt
    }

    private fun shiftOccurrenceOverlap(a: ShiftTemplate, dateA: String, b: ShiftTemplate, dateB: String): Boolean {
        val aw = shiftWindow(a, dateA) ?: return true
        val bw = shiftWindow(b, dateB) ?: return true
        return aw.first.isBefore(bw.second) && bw.first.isBefore(aw.second)
    }

    fun shiftOccurrences(from: LocalDate, to: LocalDate): List<Pair<ShiftTemplate, String>> {
        if (to.isBefore(from)) return emptyList()
        val result = mutableListOf<Pair<ShiftTemplate, String>>()
        var date = from
        while (!date.isAfter(to)) {
            val dateText = date.format(serviceDateFormat)
            val specifics = shifts.filter { it.enabled && it.specificDate == dateText }
            val recurring = shifts.filter {
                it.enabled && !it.isSpecificDate && it.dayOfWeek == date.dayOfWeek.value
            }.filter { regular ->
                specifics.none { special -> shiftOccurrenceOverlap(regular, dateText, special, dateText) }
            }
            (specifics + recurring).forEach { result += it to dateText }
            date = date.plusDays(1)
        }

        shiftReservations.filter { it.status == ShiftReservationStatus.RESERVED }.forEach { reservation ->
            val serviceDate = runCatching { LocalDate.parse(reservation.serviceDate, serviceDateFormat) }.getOrNull()
                ?: return@forEach
            if (serviceDate.isBefore(from) || serviceDate.isAfter(to)) return@forEach
            val shift = shifts.firstOrNull { it.id == reservation.shiftTemplateId } ?: return@forEach
            if (result.none { it.first.id == shift.id && it.second == reservation.serviceDate }) {
                result += shift to reservation.serviceDate
            }
        }
        return result.distinctBy { it.first.id + "|" + it.second }
            .sortedBy { (shift, dateText) -> shiftWindow(shift, dateText)?.first ?: LocalDateTime.MAX }
    }

    fun riderHasActiveShiftNow(riderId: String, now: LocalDateTime = LocalDateTime.now()): Boolean =
        shiftReservations.any { reservation ->
            if (reservation.riderId != riderId || reservation.status != ShiftReservationStatus.RESERVED) return@any false
            val shift = shifts.firstOrNull { it.id == reservation.shiftTemplateId && it.enabled } ?: return@any false
            val window = shiftWindow(shift, reservation.serviceDate) ?: return@any false
            !now.isBefore(window.first) && now.isBefore(window.second)
        }

    fun riderCanAccessNewOrders(riderId: String): Boolean {
        if (config.operationMode != OperationMode.MULTI_RIDER) return false
        val target = rider(riderId) ?: return false
        return target.active && target.approvalStatus == RiderApprovalStatus.APPROVED && riderHasActiveShiftNow(riderId)
    }

    fun reserveShift(riderId: String, shiftId: String, serviceDate: String): Boolean {
        val target = rider(riderId) ?: return false
        if (!target.active || target.approvalStatus != RiderApprovalStatus.APPROVED) return false
        val shift = shifts.firstOrNull { it.id == shiftId && it.enabled } ?: return false
        val window = shiftWindow(shift, serviceDate) ?: return false
        if (!LocalDateTime.now().isBefore(window.second)) return false
        val existing = shiftReservations.firstOrNull {
            it.riderId == riderId && it.shiftTemplateId == shiftId && it.serviceDate == serviceDate
        }
        if (existing?.status == ShiftReservationStatus.RESERVED) return true
        if (existing?.blockedRejoin == true) return false
        if (existing?.lastCancelledAt != null) {
            val cancelled = parseTimestamp(existing.lastCancelledAt) ?: return false
            if (Duration.between(cancelled, LocalDateTime.now()).toMinutes() < 15) return false
        }
        val occupied = shiftReservations.count {
            it.shiftTemplateId == shiftId && it.serviceDate == serviceDate && it.status == ShiftReservationStatus.RESERVED
        }
        if (occupied >= shift.capacity) return false
        val nowText = nowText()
        val reservation = if (existing == null) {
            RiderShiftReservation(
                id = "SHR-" + System.currentTimeMillis(),
                riderId = riderId,
                shiftTemplateId = shiftId,
                serviceDate = serviceDate,
                joinedAt = nowText
            )
        } else existing.copy(status = ShiftReservationStatus.RESERVED, joinedAt = nowText, cancelledAt = null)
        shiftReservations = shiftReservations.filterNot { it.id == reservation.id } + reservation
        store.saveShiftReservations(shiftReservations)
        appendShiftAudit(reservation, ShiftEventType.RIDER_JOINED, riderId)
        return true
    }

    fun canCancelShift(reservation: RiderShiftReservation): Boolean {
        if (reservation.status != ShiftReservationStatus.RESERVED) return false
        val joined = parseTimestamp(reservation.joinedAt) ?: return false
        return Duration.between(joined, LocalDateTime.now()).toMinutes() in 0..15
    }

    fun cancelShift(reservationId: String): Boolean {
        val r = shiftReservations.firstOrNull { it.id == reservationId } ?: return false
        if (!canCancelShift(r)) return false
        val now = nowText()
        val newCount = r.cancellationCount + 1
        val updated = r.copy(
            status = ShiftReservationStatus.CANCELLED,
            cancelledAt = now,
            lastCancelledAt = now,
            cancellationCount = newCount,
            blockedRejoin = newCount >= 2
        )
        shiftReservations = shiftReservations.map { if (it.id == reservationId) updated else it }
        store.saveShiftReservations(shiftReservations)
        appendShiftAudit(updated, ShiftEventType.RIDER_CANCELLED, r.riderId)
        disableAvailabilityIfNoActiveShift(r.riderId)
        return true
    }

    fun adminAddRiderToShift(riderId: String, shiftId: String, serviceDate: String): Boolean {
        val target = rider(riderId) ?: return false
        if (!target.active || target.approvalStatus != RiderApprovalStatus.APPROVED) return false
        val shift = shifts.firstOrNull { it.id == shiftId && it.enabled } ?: return false
        val occupied = shiftReservations.count {
            it.shiftTemplateId == shiftId && it.serviceDate == serviceDate && it.status == ShiftReservationStatus.RESERVED
        }
        val existing = shiftReservations.firstOrNull {
            it.riderId == riderId && it.shiftTemplateId == shiftId && it.serviceDate == serviceDate
        }
        if (existing?.status == ShiftReservationStatus.RESERVED) return true
        if (occupied >= shift.capacity) return false
        val now = nowText()
        val reservation = if (existing == null) {
            RiderShiftReservation("SHR-" + System.currentTimeMillis(), riderId, shiftId, serviceDate, now)
        } else existing.copy(status = ShiftReservationStatus.RESERVED, joinedAt = now, cancelledAt = null)
        shiftReservations = shiftReservations.filterNot { it.id == reservation.id } + reservation
        store.saveShiftReservations(shiftReservations)
        appendShiftAudit(reservation, ShiftEventType.ADMIN_ADDED, "ADMIN")
        return true
    }

    fun adminRemoveRiderFromShift(reservationId: String): Boolean {
        val r = shiftReservations.firstOrNull { it.id == reservationId } ?: return false
        if (r.status != ShiftReservationStatus.RESERVED) return false
        val now = nowText()
        val updated = r.copy(
            status = ShiftReservationStatus.CANCELLED,
            cancelledAt = now,
            lastCancelledAt = now,
            blockedRejoin = true
        )
        shiftReservations = shiftReservations.map { if (it.id == reservationId) updated else it }
        store.saveShiftReservations(shiftReservations)
        appendShiftAudit(updated, ShiftEventType.ADMIN_REMOVED, "ADMIN")
        disableAvailabilityIfNoActiveShift(r.riderId)
        return true
    }

    private fun appendShiftAudit(r: RiderShiftReservation, type: ShiftEventType, actor: String) {
        val event = ShiftAuditEvent(
            id = "SHE-" + System.currentTimeMillis() + "-" + shiftAuditEvents.size,
            reservationId = r.id,
            shiftTemplateId = r.shiftTemplateId,
            riderId = r.riderId,
            serviceDate = r.serviceDate,
            type = type,
            at = nowText(),
            actor = actor
        )
        shiftAuditEvents = shiftAuditEvents + event
        store.saveShiftAuditEvents(shiftAuditEvents)
    }

    private fun disableAvailabilityIfNoActiveShift(riderId: String) {
        if (riderHasActiveShiftNow(riderId)) return
        riders = riders.map { if (it.id == riderId) it.copy(available = false, availableUntilAt = null) else it }
        store.saveRiders(riders)
    }

    fun minutesUntilRiderCanRejoin(reservation: RiderShiftReservation): Long {
        val at = reservation.lastCancelledAt?.let(::parseTimestamp) ?: return 0
        return (15 - Duration.between(at, LocalDateTime.now()).toMinutes()).coerceAtLeast(0)
    }

    fun dayName(day: Int): String = when (day) {
        1 -> "Lunes"; 2 -> "Martes"; 3 -> "Miércoles"; 4 -> "Jueves"; 5 -> "Viernes"; 6 -> "Sábado"; else -> "Domingo"
    }

    fun greetingForNow(): String = when (LocalDateTime.now().hour) {
        in 5..11 -> "Buenos días"
        in 12..19 -> "Buenas tardes"
        else -> "Buenas noches"
    }

    fun requestLocationMessage(rider: RiderProfile): String =
        "${greetingForNow()}. Soy ${rider.name}, Repartidor de Punto25. ¿Sería tan amable de enviarme su ubicación por WhatsApp? Muchas gracias."

    private fun newRiderId(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        var id: String
        do {
            id = "RID-" + (1..5).joinToString("") { chars[Random.nextInt(chars.length)].toString() }
        } while (riders.any { it.id == id })
        return id
    }

    fun hasRiderCredential(riderId: String): Boolean =
        riderCredentials.any { it.riderId == riderId }

    fun createRiderInvitation(riderId: String, resetAccess: Boolean = false): RiderInvitation? {
        val rider = rider(riderId) ?: return null
        if (rider.phone.isBlank()) return null
        val now = LocalDateTime.now()
        riderInvitations = riderInvitations.map {
            if (it.riderId == riderId && it.status == RiderInvitationStatus.PENDING)
                it.copy(status = RiderInvitationStatus.CANCELLED)
            else it
        }
        if (resetAccess) {
            riderCredentials = riderCredentials.filterNot { it.riderId == riderId }
            store.saveRiderCredentials(riderCredentials)
        }
        val invitation = RiderInvitation(
            id = "INV-" + System.currentTimeMillis(),
            code = newInvitationCode(),
            riderId = riderId,
            phone = rider.phone,
            createdAt = now.format(timestampFormat),
            expiresAt = now.plusDays(7).format(timestampFormat),
            resetAccess = resetAccess
        )
        riderInvitations = riderInvitations + invitation
        store.saveRiderInvitations(riderInvitations)
        return invitation
    }

    fun redeemRiderInvitation(code: String, password: String): String? {
        if (!passwordIsStrong(password)) return null
        val normalized = code.trim().uppercase()
        val invitation = riderInvitations.firstOrNull {
            it.code.equals(normalized, ignoreCase = true) && it.status == RiderInvitationStatus.PENDING
        } ?: return null
        val expires = parseTimestamp(invitation.expiresAt) ?: return null
        if (LocalDateTime.now().isAfter(expires)) {
            riderInvitations = riderInvitations.map {
                if (it.id == invitation.id) it.copy(status = RiderInvitationStatus.EXPIRED) else it
            }
            store.saveRiderInvitations(riderInvitations)
            return null
        }
        val credential = buildRiderCredential(invitation.riderId, password)
        riderCredentials = riderCredentials.filterNot { it.riderId == invitation.riderId } + credential
        riderInvitations = riderInvitations.map {
            if (it.id == invitation.id) it.copy(status = RiderInvitationStatus.USED, usedAt = nowText()) else it
        }
        store.saveRiderCredentials(riderCredentials)
        store.saveRiderInvitations(riderInvitations)
        authenticatedRiderId = invitation.riderId
        return invitation.riderId
    }

    fun authenticateRider(riderId: String, password: String): Boolean {
        authenticatedRiderId = null
        val rider = rider(riderId) ?: return false
        if (!rider.active) return false
        val credential = riderCredentials.firstOrNull { it.riderId.equals(riderId.trim(), ignoreCase = true) } ?: return false
        val authenticated = verifyRiderPassword(password, credential)
        if (authenticated) authenticatedRiderId = rider.id
        return authenticated
    }

    fun logoutRider() {
        authenticatedRiderId = null
    }

    private fun hasAuthenticatedRiderSession(riderId: String): Boolean =
        riderActorMatchesAuthenticatedSession(authenticatedRiderId, riderId) &&
            rider(riderId) != null &&
            hasRiderCredential(riderId)

    fun changeRiderPassword(riderId: String, currentPassword: String, newPassword: String): Boolean {
        if (!authenticateRider(riderId, currentPassword) || !passwordIsStrong(newPassword)) return false
        val credential = buildRiderCredential(riderId, newPassword)
        riderCredentials = riderCredentials.filterNot { it.riderId == riderId } + credential
        store.saveRiderCredentials(riderCredentials)
        return true
    }

    fun passwordIsStrong(password: String): Boolean =
        password.length >= 8 &&
            password.any(Char::isUpperCase) &&
            password.any(Char::isLowerCase) &&
            password.any(Char::isDigit)

    private fun buildRiderCredential(riderId: String, password: String): RiderCredential {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iterations = 120000
        val hash = passwordHash(password, salt, iterations)
        return RiderCredential(
            riderId = riderId,
            saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP),
            passwordHashBase64 = Base64.encodeToString(hash, Base64.NO_WRAP),
            iterations = iterations,
            updatedAt = nowText()
        )
    }

    private fun verifyRiderPassword(password: String, credential: RiderCredential): Boolean {
        val salt = runCatching { Base64.decode(credential.saltBase64, Base64.NO_WRAP) }.getOrNull() ?: return false
        val expected = runCatching { Base64.decode(credential.passwordHashBase64, Base64.NO_WRAP) }.getOrNull() ?: return false
        val actual = passwordHash(password, salt, credential.iterations)
        return MessageDigest.isEqual(expected, actual)
    }

    private fun passwordHash(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun newInvitationCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        var code: String
        do {
            code = "P25-" + (1..6).joinToString("") { chars[Random.nextInt(chars.length)].toString() }
        } while (riderInvitations.any { it.code == code && it.status == RiderInvitationStatus.PENDING })
        return code
    }

    fun nowText(): String = LocalDateTime.now().format(timestampFormat)

    fun parseTimestamp(value: String): LocalDateTime? =
        runCatching { LocalDateTime.parse(value, timestampFormat) }.getOrNull()
            ?: runCatching { LocalDateTime.parse(value, legacyTimestampFormat) }.getOrNull()

    private fun eventTypeForStatus(status: OrderStatus): OrderEventType = when (status) {
        OrderStatus.PENDING, OrderStatus.AWAITING_QUOTE -> OrderEventType.CREATED
        OrderStatus.ACCEPTED -> OrderEventType.ACCEPTED
        OrderStatus.IN_PROGRESS -> OrderEventType.IN_PROGRESS
        OrderStatus.COMPLETED -> OrderEventType.COMPLETED
        OrderStatus.REJECTED -> OrderEventType.REJECTED
        OrderStatus.CANCELLED -> OrderEventType.CANCELLED
    }

    private fun percentOfBaseRoundedUpToHundred(base: Int, percent: Int): Int {
        if (base <= 0 || percent <= 0) return 0
        val numerator = base.toLong() * percent.toLong()
        val rounded = ((numerator + 9_999L) / 10_000L) * 100L
        return rounded.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    private fun newPublicCode(): String {
        val date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val suffix = (1..4).joinToString("") { chars[Random.nextInt(chars.length)].toString() }
        return "P25-$date-$suffix"
    }

    private fun money(v: Int?): String = if (v == null) "A confirmar" else "$" + "%,d".format(v).replace(',', '.')
    private fun zoneName(id: String): String = if (id == UNKNOWN_ZONE_ID) "No sé qué zona corresponde" else zone(id)?.name ?: "Sin zona"
    private fun mapLink(point: GeoPoint?): String? = point?.let { "https://maps.google.com/?q=${it.latitude},${it.longitude}" }

    fun prePickupPurposeText(d: OrderDraft = draft): String {
        val parts = mutableListOf<String>()
        when (d.instructionType) {
            PurchaseInstructionType.PHYSICAL_NOTE -> parts += "lista / nota / receta"
            PurchaseInstructionType.IN_PERSON -> parts += "indicaciones al Repartidor"
            PurchaseInstructionType.IN_APP -> Unit
        }
        if (d.purchasePayment == PurchasePaymentMethod.CASH_PRE_PICKUP) parts += "efectivo"
        return parts.joinToString(" + ")
    }

    private fun categoryTitle(category: ServiceCategory): String = when (category) {
        ServiceCategory.PURCHASE -> "Compra"
        ServiceCategory.ERRAND -> "Encargo"
        ServiceCategory.PROCEDURE -> "Trámite"
        ServiceCategory.SHIPMENT -> "Envío"
    }

    private fun buildDetail(d: OrderDraft, p: PricingResult): String = buildString {
        appendLine(categoryTitle(d.category))
        if (d.serviceType == ServiceType.DELIVERY) {
            appendLine("Retiro: ${d.originAddress.ifBlank { "Ubicación indicada en mapa" }} (${zoneName(d.originZoneId)})")
            if (d.originReference.isNotBlank()) appendLine("Referencia retiro: ${d.originReference}")
            mapLink(d.originLocation)?.let { appendLine("Pin retiro: $it") }
            appendLine("Entrega: ${d.destinationAddress.ifBlank { "Ubicación indicada en mapa" }} (${zoneName(d.destinationZoneId)})")
            if (d.destinationReference.isNotBlank()) appendLine("Referencia entrega: ${d.destinationReference}")
            mapLink(d.destinationLocation)?.let { appendLine("Pin entrega: $it") }
            if (d.carriedItem.isNotBlank()) appendLine("Detalle: ${d.carriedItem}")
        } else {
            appendLine("Pedido: ${instructionText(d.instructionType)}")
            if (d.purchaseDescription.isNotBlank()) appendLine("Detalle: ${d.purchaseDescription}")
            if (d.purchaseMaxAmount > 0) appendLine("Máximo compra informado: ${money(d.purchaseMaxAmount)}")
            if (d.requiresPrePickup()) {
                val purpose = prePickupPurposeText(d)
                val label = when {
                    purpose == "efectivo" -> "Retiro de efectivo"
                    purpose.isNotBlank() -> "Retiro previo — $purpose"
                    else -> "Retiro previo"
                }
                appendLine("$label: ${d.prePickupAddress.ifBlank { "Ubicación indicada en mapa" }} (${zoneName(d.prePickupZoneId)})")
                if (d.prePickupReference.isNotBlank()) appendLine("Referencia retiro previo: ${d.prePickupReference}")
                mapLink(d.prePickupLocation)?.let { appendLine("Pin retiro previo: $it") }
            }
            val store = listOf(d.storeName, d.storeAddress).filter { it.isNotBlank() }.joinToString(" · ")
            appendLine("Comercio de retiro: ${store.ifBlank { "No especificado" }}")
            mapLink(d.storeLocation)?.let { appendLine("Pin comercio: $it") }
            appendLine("Entrega: ${d.destinationAddress.ifBlank { "Ubicación indicada en mapa" }} (${zoneName(d.destinationZoneId)})")
            if (d.destinationReference.isNotBlank()) appendLine("Referencia entrega: ${d.destinationReference}")
            mapLink(d.destinationLocation)?.let { appendLine("Pin entrega: $it") }
            appendLine("Pago compra: ${purchasePaymentText(d.purchasePayment)}")
        }
        appendLine("Pago servicio: ${deliveryPaymentText(d.deliveryPayment)}")
        if (d.notes.isNotBlank()) appendLine("Aclaraciones: ${d.notes}")
        appendLine("Tarifa base: ${money(p.baseAmount)}${p.baseZoneName?.let { " ($it)" } ?: ""}")
        if ((p.prePickupAmount ?: 0) > 0) appendLine("Retiro previo: ${money(p.prePickupAmount)}")
        if ((p.rainAmount ?: 0) > 0) appendLine("Lluvia/barro: ${money(p.rainAmount)}")
        appendLine("TOTAL SERVICIO: ${money(p.totalAmount)}")
        if (p.needsQuote) appendLine("Estado tarifario: ${p.issue ?: "A confirmar"}")
    }

    private fun buildWhatsAppMessage(id: String, c: Customer, d: OrderDraft, p: PricingResult): String = buildString {
        appendLine("Punto25 · NUEVA SOLICITUD")
        appendLine(id)
        appendLine()
        appendLine("Cliente: ${c.name}")
        appendLine("WhatsApp: ${c.displayPhone}")
        appendLine()
        append(buildDetail(d, p))
        appendLine()
        appendLine("Estado: PENDIENTE DE ACEPTACIÓN")
    }

    fun instructionText(v: PurchaseInstructionType) = when (v) {
        PurchaseInstructionType.IN_APP -> "Escrito en la app"
        PurchaseInstructionType.PHYSICAL_NOTE -> "Lista / nota / receta física"
        PurchaseInstructionType.IN_PERSON -> "Indicación personal al retirar"
    }

    fun purchasePaymentText(v: PurchasePaymentMethod) = when (v) {
        PurchasePaymentMethod.DIRECT_TO_STORE -> "Ya paga / pago directo al comercio"
        PurchasePaymentMethod.TRANSFER_TO_RIDER -> "Transferencia previa al Repartidor"
        PurchasePaymentMethod.CASH_PRE_PICKUP -> "Efectivo previo al Repartidor"
    }

    fun deliveryPaymentText(v: DeliveryPaymentMethod) = when (v) {
        DeliveryPaymentMethod.CASH -> "Efectivo"
        DeliveryPaymentMethod.TRANSFER -> "Transferencia"
        DeliveryPaymentMethod.QR -> "QR interoperable"
        DeliveryPaymentMethod.ONLINE -> "Pago online"
    }
}
