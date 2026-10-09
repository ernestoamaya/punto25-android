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
import java.util.UUID
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import android.util.Base64
import kotlin.random.Random

class MandadosController(context: Context) {
    private val appContext = context.applicationContext
    private val store = LocalStore(appContext)
    private val shiftStoreV2 = ShiftStoreV2(appContext)
    private val shiftMutationLock = Any()
    private var shiftV2Ready = false
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
    // Legacy ShiftTemplate state is deliberately never loaded after the v2 cutover.
    var shifts by mutableStateOf(emptyList<ShiftTemplate>())
        private set
    var shiftReservations by mutableStateOf(emptyList<RiderShiftReservation>())
        private set
    var shiftAuditEvents by mutableStateOf(emptyList<ShiftAuditEvent>())
        private set
    var shiftRules by mutableStateOf(emptyList<ShiftGenerationRule>())
        private set
    var concreteShifts by mutableStateOf(emptyList<ConcreteShift>())
        private set
    var concreteShiftReservations by mutableStateOf(emptyList<ConcreteShiftReservation>())
        private set
    var concreteShiftAuditEvents by mutableStateOf(emptyList<ConcreteShiftAuditEvent>())
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
    var riderDenialFeedback by mutableStateOf<RiderEligibilityDecision?>(null)
        private set

    init {
        val initialization = shiftStoreV2.initializeIfNeeded()
        if (initialization.initializedNow) riders = store.loadRiders()
        val snapshot = if (initialization.success) {
            shiftStoreV2.loadSnapshot()
        } else {
            ShiftStoreSnapshot(emptyList(), emptyList(), emptyList(), emptyList(), false)
        }
        shiftV2Ready = initialization.success && isShiftSnapshotConsistent(snapshot)
        if (shiftV2Ready) {
            shiftRules = snapshot.rules
            concreteShifts = snapshot.shifts
            concreteShiftReservations = snapshot.reservations
            concreteShiftAuditEvents = snapshot.audit
        }
        reconcileCurrentCustomerOrderIdentity()
    }

    fun registerPending(c: Customer) {
        pendingCustomer = c.copy(
            accountId = "", googleVerified = false, googleEmail = "",
            whatsappVerified = false, whatsappVerifiedAt = null
        )
    }

    fun applyGoogleIdentity(uid: String, email: String?, displayName: String?) {
        if (uid.isBlank() || !GoogleAuthIntegration.isConfigured() || GoogleAuthIntegration.currentUid(appContext) != uid) return
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
        if (!canConfirmCustomerRegistration(
                pending, GoogleAuthIntegration.isConfigured(),
                GoogleAuthIntegration.currentUid(appContext), BuildConfig.DEBUG
            )) return false
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

    fun updateConfig(newConfig: AdminConfig): Boolean {
        if (newConfig.zoneAutoResolutionEnabled && !zoneAutoResolutionConfigIsValid(newConfig.zones)) return false
        val switchingToSimple = config.operationMode != OperationMode.SIMPLE_WHATSAPP &&
            newConfig.operationMode == OperationMode.SIMPLE_WHATSAPP
        config = newConfig
        store.saveConfig(newConfig)
        if (switchingToSimple) {
            riders = riders.map { it.copy(available = false, availableUntilAt = null) }
            store.saveRiders(riders)
        }
        return true
    }

    fun setZoneAutoResolutionEnabled(enabled: Boolean): Boolean =
        updateConfig(config.copy(zoneAutoResolutionEnabled = enabled))

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
        return updateConfig(config.copy(zones = config.zones + ZoneConfig(id, clean, description.trim(), category.trim().ifBlank { "OTRAS" }, price.coerceAtLeast(0), true)))
    }

    fun updateZone(id: String, name: String, description: String, category: String, price: Int, enabled: Boolean): Boolean {
        val clean = name.trim()
        if (clean.isBlank()) return false
        val current = config.zones.firstOrNull { it.id == id } ?: return false
        val candidate = current.copy(
            name = clean,
            description = description.trim(),
            category = category.trim().ifBlank { "OTRAS" },
            price = price.coerceAtLeast(0),
            enabled = enabled
        )
        if (!canApplyZoneMetadataUpdate(config, current, candidate)) return false
        if (candidate == current) return true
        return updateConfig(config.copy(zones = config.zones.map {
            if (it.id == id) candidate else it
        }))
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

    fun pricing(
        d: OrderDraft = draft,
        overrides: Map<OrderZonePoint, OrderZoneOverride> = emptyMap()
    ): PricingResult = calculateOrderPricing(d, overrides, config)

    private fun resolvedNewOrderDraft(d: OrderDraft = draft): ZoneAutoDraftResolution =
        resolveDraftZonesAutomatically(d, config)

    fun pricingForNewOrder(d: OrderDraft = draft): PricingResult =
        calculateOrderPricing(resolvedNewOrderDraft(d).draft, emptyMap(), config)

    fun createOrder(): OrderCreationResult {
        if (!config.acceptingOrders) return OrderCreationResult.Blocked(config.closedMessage)

        val c = requireNotNull(customer)
        val effectiveDraft = resolvedNewOrderDraft(draft).draft
        val p = calculateOrderPricing(effectiveDraft, emptyMap(), config)
        val id = newPublicCode()
        val created = nowText()
        val status = if (p.needsQuote) OrderStatus.AWAITING_QUOTE else OrderStatus.PENDING
        val detail = buildDetail(effectiveDraft, p)
        val message = buildWhatsAppMessage(id, c, effectiveDraft, p)
        val order = LocalOrder(
            id = id,
            createdAt = created,
            serviceType = effectiveDraft.serviceType,
            category = effectiveDraft.category,
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
            originLocation = effectiveDraft.originLocation,
            destinationLocation = effectiveDraft.destinationLocation,
            storeLocation = effectiveDraft.storeLocation,
            prePickupLocation = effectiveDraft.prePickupLocation,
            events = listOf(
                OrderEvent(
                    type = OrderEventType.CREATED,
                    at = created,
                    status = status,
                    note = if (status == OrderStatus.AWAITING_QUOTE) "Solicitud creada · tarifa a confirmar" else "Solicitud creada",
                    actor = "CLIENTE"
                )
            ),
            originAddress = effectiveDraft.originAddress,
            originReference = effectiveDraft.originReference,
            originZoneId = effectiveDraft.originZoneId,
            destinationAddress = effectiveDraft.destinationAddress,
            destinationReference = effectiveDraft.destinationReference,
            destinationZoneId = effectiveDraft.destinationZoneId,
            carriedItem = effectiveDraft.carriedItem,
            instructionType = effectiveDraft.instructionType,
            purchaseDescription = effectiveDraft.purchaseDescription,
            purchaseMaxAmount = effectiveDraft.purchaseMaxAmount,
            storeName = effectiveDraft.storeName,
            storeAddress = effectiveDraft.storeAddress,
            storeZoneId = effectiveDraft.storeZoneId,
            purchasePayment = effectiveDraft.purchasePayment,
            prePickupAddress = effectiveDraft.prePickupAddress,
            prePickupReference = effectiveDraft.prePickupReference,
            prePickupZoneId = effectiveDraft.prePickupZoneId,
            sameDeliveryAsPrePickup = effectiveDraft.sameDeliveryAsPrePickup,
            deliveryPayment = effectiveDraft.deliveryPayment,
            notes = effectiveDraft.notes
        )
        val nextOrders = listOf(order) + orders
        if (order.operationMode == OperationMode.MULTI_RIDER) {
            val payment = paymentRecordSynchronizedToOrder(order, existing = null, timestamp = created)
            val nextPayments = payments + payment
            store.saveOrdersAndPayments(nextOrders, nextPayments)
            orders = nextOrders
            payments = nextPayments
        } else {
            store.saveOrders(nextOrders)
            orders = nextOrders
        }
        return OrderCreationResult.Created(order)
    }

    fun cancelOrderByCustomer(id: String): Boolean {
        val o = customerOrder(id) ?: return false
        if (o.status != OrderStatus.PENDING && o.status != OrderStatus.AWAITING_QUOTE) return false
        return updateOrderStatus(id, OrderStatus.CANCELLED, "Cancelado por el cliente", "CLIENTE")
    }

    fun updateOrderStatus(id: String, status: OrderStatus, note: String? = null, actor: String? = null): Boolean {
        val o = order(id) ?: return false
        val effectiveActor = actor ?: "ADMIN"
        when (effectiveActor) {
            "ADMIN" -> Unit
            "CLIENTE" -> if (customerOrder(id) == null) return false
            else -> {
                if (!hasAuthenticatedRiderSession(effectiveActor)) return false
                if (o.assignedRiderId != effectiveActor) return false
                if (!riderExistingOrderContinuationDecision(effectiveActor).allowed) return false
                val validRiderTransition = when (status) {
                    OrderStatus.IN_PROGRESS -> o.status == OrderStatus.PENDING || o.status == OrderStatus.ACCEPTED
                    OrderStatus.COMPLETED -> o.status == OrderStatus.IN_PROGRESS
                    else -> false
                }
                if (!validRiderTransition) return false
            }
        }
        if (o.status == status) return true
        val event = OrderEvent(
            type = eventTypeForStatus(status),
            at = nowText(),
            status = status,
            riderId = o.assignedRiderId,
            note = note,
            actor = effectiveActor
        )
        updateOrder(o.copy(status = status, events = o.events + event))
        return true
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
        val paymentMatches = payments.filter { it.orderId == orderId }
        if (paymentMatches.size > 1) return false
        val existingPayment = paymentMatches.singleOrNull()

        if (riderId != null) {
            val target = rider(riderId) ?: return false
            if (!riderAccountEligibility(target).allowed) return false
            if (!riderHasActiveShiftNow(riderId)) return false
            if (!riderHasCapacity(target, excludingOrderId = orderId)) return false
        }

        val candidate = o.copy(assignedRiderId = riderId)
        if (isCommittedRiderTransferAssignmentLocked(o, riderId, existingPayment)) return false
        val mutation = evaluateOrderPaymentMutation(o, candidate, existingPayment)
        if (mutation == OrderPaymentMutationAction.DENY) return false

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
            events += OrderEvent(
                type = OrderEventType.RIDER_ASSIGNED,
                at = now,
                status = o.status,
                riderId = riderId,
                note = "Repartidor asignado",
                actor = "ADMIN"
            )
        }
        val updated = candidate.copy(events = events)
        when (mutation) {
            OrderPaymentMutationAction.DENY -> return false
            OrderPaymentMutationAction.ORDER_ONLY -> updateOrder(updated)
            OrderPaymentMutationAction.ORDER_AND_PAYMENT -> {
                val payment = paymentRecordSynchronizedToOrder(updated, existingPayment, now)
                persistOrderAndPayment(updated, payment)
            }
        }
        return true
    }

    fun takeOrderDecision(orderId: String, riderId: String): RiderEligibilityDecision {
        val eligibility = riderOperationalEligibility(riderId)
        if (!eligibility.allowed) return eligibility
        val target = rider(riderId) ?: return RiderEligibilityDecision.denied(RiderDenialReason.SESSION_REQUIRED)
        val o = order(orderId) ?: return RiderEligibilityDecision.denied(RiderDenialReason.ORDER_NOT_AVAILABLE)
        if (o.operationMode != OperationMode.MULTI_RIDER || config.operationMode != OperationMode.MULTI_RIDER) {
            return RiderEligibilityDecision.denied(RiderDenialReason.OPERATION_MODE_UNAVAILABLE)
        }
        if (!riderHasActiveShiftNow(riderId)) return RiderEligibilityDecision.denied(RiderDenialReason.NO_ACTIVE_SHIFT)
        if (!isRiderCurrentlyAvailable(riderId)) return RiderEligibilityDecision.denied(RiderDenialReason.RIDER_NOT_AVAILABLE)
        if (!riderHasCapacity(target)) return RiderEligibilityDecision.denied(RiderDenialReason.NO_CAPACITY)
        if (o.status != OrderStatus.PENDING || o.assignedRiderId != null) {
            return RiderEligibilityDecision.denied(RiderDenialReason.ORDER_NOT_AVAILABLE)
        }
        val paymentMatches = payments.filter { it.orderId == orderId }
        if (paymentMatches.size != 1 || !isCleanPendingPaymentForTake(o, paymentMatches.single())) {
            return RiderEligibilityDecision.denied(RiderDenialReason.ORDER_NOT_AVAILABLE)
        }
        return RiderEligibilityDecision.ALLOWED
    }

    fun takeOrderWithDecision(orderId: String, riderId: String): RiderEligibilityDecision {
        val decision = takeOrderDecision(orderId, riderId)
        if (!decision.allowed) return decision
        val o = order(orderId) ?: return RiderEligibilityDecision.denied(RiderDenialReason.ORDER_NOT_AVAILABLE)
        val existingPayment = payments.filter { it.orderId == orderId }.singleOrNull()
            ?: return RiderEligibilityDecision.denied(RiderDenialReason.ORDER_NOT_AVAILABLE)
        if (!isCleanPendingPaymentForTake(o, existingPayment)) {
            return RiderEligibilityDecision.denied(RiderDenialReason.ORDER_NOT_AVAILABLE)
        }
        val now = nowText()
        val updated = o.copy(
            status = OrderStatus.ACCEPTED,
            assignedRiderId = riderId,
            events = o.events + listOf(
                OrderEvent(OrderEventType.RIDER_ASSIGNED, now, OrderStatus.PENDING, riderId, "Pedido tomado", riderId),
                OrderEvent(OrderEventType.ACCEPTED, now, OrderStatus.ACCEPTED, riderId, "Pedido aceptado por el Repartidor", riderId)
            )
        )
        val payment = paymentRecordSynchronizedToOrder(updated, existingPayment, now)
        persistOrderAndPayment(updated, payment)
        return RiderEligibilityDecision.ALLOWED
    }

    fun takeOrder(orderId: String, riderId: String): Boolean =
        publishRiderDecision(takeOrderWithDecision(orderId, riderId)).allowed

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

    internal fun previewOrderZoneOverride(
        orderId: String,
        point: OrderZonePoint,
        selection: OrderZoneOverrideSelection
    ): OrderZoneOverrideEvaluation? {
        val current = order(orderId) ?: return null
        return evaluateOrderZoneOverride(
            currentOrder = current,
            point = point,
            selection = selection,
            config = config,
            paymentMatches = payments.filter { it.orderId == orderId }
        )
    }

    internal fun applyOrderZoneOverride(
        orderId: String,
        point: OrderZonePoint,
        selection: OrderZoneOverrideSelection,
        reason: String
    ): Boolean {
        if (reason.trim().isBlank()) return false
        val current = order(orderId) ?: return false
        val paymentMatches = payments.filter { it.orderId == orderId }
        val evaluation = evaluateOrderZoneOverride(
            currentOrder = current,
            point = point,
            selection = selection,
            config = config,
            paymentMatches = paymentMatches
        )
        if (!evaluation.allowed) return false
        val candidate = evaluation.candidateOrder ?: return false
        val now = nowText()
        val updated = candidate.copy(
            events = current.events + OrderEvent(
                type = OrderEventType.ORDER_EDITED,
                at = now,
                status = candidate.status,
                riderId = current.assignedRiderId,
                note = zoneOverrideAuditNote(evaluation, reason),
                actor = "ADMIN"
            )
        )
        return when (evaluation.paymentMutation) {
            OrderPaymentMutationAction.DENY -> false
            OrderPaymentMutationAction.ORDER_ONLY -> {
                updateOrder(updated)
                true
            }
            OrderPaymentMutationAction.ORDER_AND_PAYMENT -> {
                val payment = paymentRecordSynchronizedToOrder(
                    order = updated,
                    existing = paymentMatches.singleOrNull(),
                    timestamp = now
                )
                persistOrderAndPayment(updated, payment)
                true
            }
        }
    }

    fun canEditOrder(order: LocalOrder): Boolean =
        order.operationMode == OperationMode.MULTI_RIDER &&
            order.status != OrderStatus.CANCELLED &&
            order.status != OrderStatus.COMPLETED

    fun editOrder(orderId: String, edited: OrderDraft, reason: String): Boolean {
        val o = order(orderId) ?: return false
        if (!canEditOrder(o)) return false
        if (reason.trim().isBlank()) return false

        val p = pricing(edited, o.zoneOverrides)
        val candidate = o.copy(
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
            notes = edited.notes
        )
        val paymentMatches = payments.filter { it.orderId == orderId }
        if (paymentMatches.size > 1) return false
        val existingPayment = paymentMatches.singleOrNull()
        val mutation = evaluateOrderPaymentMutation(o, candidate, existingPayment)
        if (mutation == OrderPaymentMutationAction.DENY) return false

        val now = nowText()
        val updated = candidate.copy(
            events = o.events + OrderEvent(
                type = OrderEventType.ORDER_EDITED,
                at = now,
                status = o.status,
                riderId = o.assignedRiderId,
                note = reason.trim(),
                actor = "ADMIN"
            )
        )
        when (mutation) {
            OrderPaymentMutationAction.DENY -> return false
            OrderPaymentMutationAction.ORDER_ONLY -> updateOrder(updated)
            OrderPaymentMutationAction.ORDER_AND_PAYMENT -> {
                val payment = paymentRecordSynchronizedToOrder(updated, existingPayment, now)
                persistOrderAndPayment(updated, payment)
            }
        }
        return true
    }

    private fun updateOrder(updated: LocalOrder) {
        orders = orders.map { if (it.id == updated.id) updated else it }
        store.saveOrders(orders)
    }

    private fun persistOrderAndPayment(updatedOrder: LocalOrder, updatedPayment: PaymentRecord) {
        val nextOrders = orders.map { if (it.id == updatedOrder.id) updatedOrder else it }
        val hasExisting = payments.any { it.orderId == updatedOrder.id }
        val nextPayments = if (hasExisting) {
            payments.map { if (it.orderId == updatedOrder.id) updatedPayment else it }
        } else {
            payments + updatedPayment
        }
        store.saveOrdersAndPayments(nextOrders, nextPayments)
        orders = nextOrders
        payments = nextPayments
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
                    val updated = current.copy(
                        name = cleanName,
                        phone = cleanPhone,
                        birthDate = birthDate.trim(),
                        vehicleType = vehicleType,
                        address = address.trim(),
                        maxConcurrentOrdersOverride = maxConcurrentOrdersOverride?.coerceAtLeast(1),
                        documents = documents,
                        documentReviews = reviews
                    )
                    if (updated.approvalStatus == RiderApprovalStatus.APPROVED && riderHasApprovedRequiredDocuments(updated)) {
                        updated
                    } else {
                        updated.copy(available = false, availableUntilAt = null)
                    }
                } else current
            }
        }
        store.saveRiders(riders)
        return targetId
    }

    fun setRiderApprovalStatus(id: String, status: RiderApprovalStatus) {
        riders = riders.map {
            if (it.id == id) {
                val keepAvailability = status == RiderApprovalStatus.APPROVED && it.active && riderHasApprovedRequiredDocuments(it)
                it.copy(
                    approvalStatus = status,
                    available = if (keepAvailability) it.available else false,
                    availableUntilAt = if (keepAvailability) it.availableUntilAt else null
                )
            } else it
        }
        store.saveRiders(riders)
    }

    fun setRiderDocumentReview(id: String, key: RiderDocumentKey, status: DocumentReviewStatus, note: String = "") {
        riders = riders.map { r ->
            if (r.id != id) return@map r
            val effective = if (r.documents.uriFor(key).isNullOrBlank()) DocumentReviewStatus.NOT_UPLOADED else status
            val updated = r.copy(
                documentReviews = r.documentReviews + (key to effective),
                documentNotes = if (note.isBlank()) r.documentNotes - key else r.documentNotes + (key to note.trim())
            )
            if (updated.approvalStatus == RiderApprovalStatus.APPROVED && riderHasApprovedRequiredDocuments(updated)) {
                updated
            } else {
                updated.copy(available = false, availableUntilAt = null)
            }
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
        if (!active && authenticatedRiderId == id) authenticatedRiderId = null
        store.saveRiders(riders)
    }

    private fun riderSessionDecision(riderId: String): RiderEligibilityDecision {
        if (!riderActorMatchesAuthenticatedSession(authenticatedRiderId, riderId)) {
            return RiderEligibilityDecision.denied(RiderDenialReason.SESSION_REQUIRED)
        }
        val target = rider(riderId) ?: return RiderEligibilityDecision.denied(RiderDenialReason.SESSION_REQUIRED)
        if (!hasRiderCredential(riderId)) return RiderEligibilityDecision.denied(RiderDenialReason.SESSION_REQUIRED)
        if (!target.active) {
            if (authenticatedRiderId == target.id) authenticatedRiderId = null
            return RiderEligibilityDecision.denied(RiderDenialReason.ACCOUNT_DEACTIVATED)
        }
        return RiderEligibilityDecision.ALLOWED
    }

    fun riderOperationalEligibility(riderId: String): RiderEligibilityDecision {
        val session = riderSessionDecision(riderId)
        if (!session.allowed) return session
        val target = rider(riderId) ?: return RiderEligibilityDecision.denied(RiderDenialReason.SESSION_REQUIRED)
        return riderAccountEligibility(target)
    }

    fun riderExistingOrderContinuationDecision(riderId: String): RiderEligibilityDecision {
        val session = riderSessionDecision(riderId)
        if (!session.allowed) return session
        val target = rider(riderId) ?: return RiderEligibilityDecision.denied(RiderDenialReason.SESSION_REQUIRED)
        val account = riderAccountEligibility(target)
        if (account.allowed) return RiderEligibilityDecision.ALLOWED
        return if (account.reason in setOf(
                RiderDenialReason.SUSPENDED,
                RiderDenialReason.DOCUMENT_NOT_UPLOADED,
                RiderDenialReason.DOCUMENT_PENDING,
                RiderDenialReason.DOCUMENT_REJECTED
            )
        ) RiderEligibilityDecision.ALLOWED else account
    }

    fun riderCanViewShiftsDecision(riderId: String): RiderEligibilityDecision {
        val eligibility = riderOperationalEligibility(riderId)
        if (!eligibility.allowed) return eligibility
        if (config.operationMode != OperationMode.MULTI_RIDER) {
            return RiderEligibilityDecision.denied(RiderDenialReason.OPERATION_MODE_UNAVAILABLE)
        }
        return RiderEligibilityDecision.ALLOWED
    }

    private fun publishRiderDecision(decision: RiderEligibilityDecision): RiderEligibilityDecision {
        riderDenialFeedback = decision.takeUnless { it.allowed }
        return decision
    }

    fun clearRiderDenialFeedback() {
        riderDenialFeedback = null
    }

    fun setRiderAvailableWithDecision(id: String, available: Boolean): RiderEligibilityDecision {
        if (!available) {
            val session = riderSessionDecision(id)
            if (!session.allowed) return session
            riders = riders.map { if (it.id == id) it.copy(available = false, availableUntilAt = null) else it }
            store.saveRiders(riders)
            return RiderEligibilityDecision.ALLOWED
        }
        val eligibility = riderCanViewShiftsDecision(id)
        if (!eligibility.allowed) return eligibility
        if (!riderHasActiveShiftNow(id)) return RiderEligibilityDecision.denied(RiderDenialReason.NO_ACTIVE_SHIFT)
        val until = LocalDateTime.now().plusMinutes(30).format(timestampFormat)
        riders = riders.map { if (it.id == id) it.copy(available = true, availableUntilAt = until) else it }
        store.saveRiders(riders)
        return RiderEligibilityDecision.ALLOWED
    }

    fun setRiderAvailable(id: String, available: Boolean): Boolean =
        publishRiderDecision(setRiderAvailableWithDecision(id, available)).allowed

    fun updateRiderTransferAlias(id: String, alias: String): Boolean {
        if (authenticatedRiderFor(id) == null) return false
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
        return target.available && riderAccountEligibility(target).allowed &&
            config.operationMode == OperationMode.MULTI_RIDER && riderHasActiveShiftNow(id)
    }

    private fun expireAvailabilityIfNeeded(id: String) {
        val target = rider(id) ?: return
        val until = target.availableUntilAt?.let(::parseTimestamp)
        val mustDisable = target.available && (
            until == null ||
                !LocalDateTime.now().isBefore(until) ||
                !riderHasActiveShiftNow(id) ||
                !riderAccountEligibility(target).allowed ||
                config.operationMode != OperationMode.MULTI_RIDER
            )
        if (mustDisable) {
            riders = riders.map { if (it.id == id) it.copy(available = false, availableUntilAt = null) else it }
            store.saveRiders(riders)
        }
    }

    fun riderCompletedOrders(riderId: String): List<LocalOrder> {
        if (!hasAuthenticatedRiderSession(riderId)) return emptyList()
        return orders.filter { it.assignedRiderId == riderId && it.status == OrderStatus.COMPLETED }
    }

    fun riderHistoryOrders(riderId: String): List<LocalOrder> {
        if (!hasAuthenticatedRiderSession(riderId)) return emptyList()
        val finals = setOf(OrderStatus.COMPLETED, OrderStatus.CANCELLED, OrderStatus.REJECTED)
        return orders.filter { it.assignedRiderId == riderId && it.status in finals }
    }

    fun riderTotalOrders(riderId: String): Int {
        if (!hasAuthenticatedRiderSession(riderId)) return 0
        return orders.count { order ->
            order.assignedRiderId == riderId ||
                order.events.any { it.type == OrderEventType.RIDER_ASSIGNED && it.riderId == riderId }
        }
    }

    fun riderBalanceForOrders(riderId: String, items: List<LocalOrder>): Int {
        if (!hasAuthenticatedRiderSession(riderId)) return 0
        return calculateRiderBalance(riderId, items, ratings)
    }

    fun riderCurrentBalance(riderId: String): Int {
        if (!hasAuthenticatedRiderSession(riderId)) return 0
        return calculateRiderBalance(
            riderId,
            orders.filter { it.assignedRiderId == riderId && it.status == OrderStatus.COMPLETED },
            ratings
        )
    }

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

    fun riderAverageDeliverySeconds(riderId: String): Long? {
        if (!hasAuthenticatedRiderSession(riderId)) return null
        return averageDeliverySeconds(orders.filter { it.assignedRiderId == riderId && it.status == OrderStatus.COMPLETED })
    }

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
        if (!hasAuthenticatedRiderSession(riderId)) return null
        val own = ratings.filter { it.riderId == riderId }
        return if (own.isEmpty()) null else own.map { it.stars }.average()
    }

    fun riderRatingCount(riderId: String): Int {
        if (!hasAuthenticatedRiderSession(riderId)) return 0
        return ratings.count { it.riderId == riderId }
    }

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
        if (!hasAuthenticatedRiderSession(riderId)) return 0
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

    fun riderPaymentForOrder(orderId: String, riderId: String): PaymentRecord? {
        if (!hasAuthenticatedRiderSession(riderId)) return null
        val ownOrder = order(orderId) ?: return null
        val payment = paymentForOrder(orderId) ?: return null
        return payment.takeIf { riderOwnsTransfer(ownOrder, it, riderId) }
    }

    private fun transfersForRider(riderId: String, statuses: Set<PaymentStatus>): List<PaymentRecord> {
        if (!hasAuthenticatedRiderSession(riderId)) return emptyList()
        return payments.filter { payment ->
            val ownOrder = order(payment.orderId)
            payment.status in statuses && ownOrder != null && riderOwnsTransfer(ownOrder, payment, riderId)
        }.sortedByDescending { parseTimestamp(it.updatedAt) ?: LocalDateTime.MIN }
    }

    fun riderPendingTransfers(riderId: String): List<PaymentRecord> =
        transfersForRider(riderId, transferPendingStatuses)

    fun riderCompletedTransfers(riderId: String): List<PaymentRecord> =
        transfersForRider(riderId, setOf(PaymentStatus.CONFIRMED))

    fun riderTransferAttentionCount(riderId: String): Int =
        riderPendingTransfers(riderId).count(::transferRequiresAttention)

    fun riderCanAccessTransfer(orderId: String, riderId: String): Boolean {
        if (!hasAuthenticatedRiderSession(riderId)) return false
        val ownOrder = order(orderId) ?: return false
        val payment = paymentForOrder(orderId) ?: return false
        return riderOwnsTransfer(ownOrder, payment, riderId)
    }

    fun riderCanConfirmTransfer(orderId: String, riderId: String): Boolean {
        if (!hasAuthenticatedRiderSession(riderId)) return false
        val ownOrder = order(orderId) ?: return false
        val payment = paymentForOrder(orderId) ?: return false
        return canRiderConfirmTransfer(ownOrder, payment, riderId, config.paymentConfig.transferProofRequired)
    }

    fun riderCanReportTransfer(orderId: String, riderId: String): Boolean {
        if (!hasAuthenticatedRiderSession(riderId)) return false
        val ownOrder = order(orderId) ?: return false
        val payment = paymentForOrder(orderId) ?: return false
        return canRiderReportTransfer(ownOrder, payment, riderId)
    }

    fun declarePayment(orderId: String): Boolean {
        val currentCustomer = customer ?: return false
        val ownOrder = customerOrder(orderId) ?: return false
        val matches = payments.filter { it.orderId == orderId }
        if (matches.size != 1) return false
        val payment = matches.single()
        if (!canCustomerDeclareTransfer(payment)) return false
        val now = nowText()
        val updatedPayment = payment.copy(status = PaymentStatus.DECLARED, updatedAt = now)
        val updatedOrder = ownOrder.copy(events = ownOrder.events + OrderEvent(
            OrderEventType.PAYMENT_DECLARED,
            now,
            ownOrder.status,
            ownOrder.assignedRiderId,
            "Cliente informó que realizó la transferencia",
            currentCustomer.id
        ))
        persistOrderAndPayment(updatedOrder, updatedPayment)
        return true
    }

    fun attachTransferProof(orderId: String, uri: String): Boolean {
        val currentCustomer = customer ?: return false
        val ownOrder = customerOrder(orderId) ?: return false
        if (uri.isBlank()) return false
        val matches = payments.filter { it.orderId == orderId }
        if (matches.size != 1) return false
        val payment = matches.single()
        if (!canCustomerAttachTransferProof(payment)) return false
        val now = nowText()
        val updatedPayment = payment.copy(
            proofUri = uri,
            status = PaymentStatus.PROOF_UPLOADED,
            updatedAt = now
        )
        val updatedOrder = ownOrder.copy(events = ownOrder.events + OrderEvent(
            OrderEventType.PAYMENT_PROOF_ATTACHED,
            now,
            ownOrder.status,
            ownOrder.assignedRiderId,
            "Comprobante de transferencia adjunto",
            currentCustomer.id
        ))
        persistOrderAndPayment(updatedOrder, updatedPayment)
        return true
    }

    fun confirmPaymentByRider(orderId: String, riderId: String): Boolean {
        if (!riderCanConfirmTransfer(orderId, riderId)) return false
        val ownOrder = order(orderId) ?: return false
        val matches = payments.filter { it.orderId == orderId }
        if (matches.size != 1) return false
        val payment = matches.single()
        if (!canRiderConfirmTransfer(ownOrder, payment, riderId, config.paymentConfig.transferProofRequired)) return false
        val now = nowText()
        val updatedPayment = payment.copy(status = PaymentStatus.CONFIRMED, updatedAt = now)
        val updatedOrder = ownOrder.copy(events = ownOrder.events + OrderEvent(
            OrderEventType.PAYMENT_CONFIRMED,
            now,
            ownOrder.status,
            ownOrder.assignedRiderId,
            "Acreditación confirmada por el Repartidor",
            riderId
        ))
        persistOrderAndPayment(updatedOrder, updatedPayment)
        return true
    }

    fun reportPaymentProblem(orderId: String, riderId: String): Boolean {
        if (!riderCanReportTransfer(orderId, riderId)) return false
        val ownOrder = order(orderId) ?: return false
        val matches = payments.filter { it.orderId == orderId }
        if (matches.size != 1) return false
        val payment = matches.single()
        if (!canRiderReportTransfer(ownOrder, payment, riderId)) return false
        val now = nowText()
        val updatedPayment = payment.copy(status = PaymentStatus.IN_REVIEW, updatedAt = now)
        val updatedOrder = ownOrder.copy(events = ownOrder.events + OrderEvent(
            OrderEventType.PAYMENT_REVIEW_REQUESTED,
            now,
            ownOrder.status,
            ownOrder.assignedRiderId,
            "El Repartidor informó que no ve acreditado el pago",
            riderId
        ))
        persistOrderAndPayment(updatedOrder, updatedPayment)
        return true
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

    private fun isShiftSnapshotConsistent(snapshot: ShiftStoreSnapshot): Boolean {
        if (!snapshot.healthy) return false
        if (snapshot.rules.map { it.id }.toSet().size != snapshot.rules.size) return false
        if (snapshot.shifts.map { it.id }.toSet().size != snapshot.shifts.size) return false
        if (snapshot.reservations.map { it.id }.toSet().size != snapshot.reservations.size) return false
        if (snapshot.rules.any { ShiftSchedulePolicy.validateRule(it) != null }) return false
        for (i in snapshot.rules.indices) {
            for (j in i + 1 until snapshot.rules.size) {
                if (ShiftSchedulePolicy.rulesOverlap(snapshot.rules[i], snapshot.rules[j])) return false
            }
        }
        if (snapshot.shifts.any { ShiftSchedulePolicy.validateConcrete(it) != null }) return false
        if (snapshot.shifts.filter { it.originRuleId != null }
                .groupBy { it.originRuleId!! to it.serviceDate }.any { it.value.size > 1 }) return false
        for (i in snapshot.shifts.indices) {
            for (j in i + 1 until snapshot.shifts.size) {
                if (ShiftSchedulePolicy.overlaps(snapshot.shifts[i], snapshot.shifts[j])) return false
            }
        }
        val shiftsById = snapshot.shifts.associateBy { it.id }
        if (snapshot.reservations.any { reservation ->
                shiftsById[reservation.concreteShiftId] == null || riders.none { it.id == reservation.riderId }
            }) return false
        snapshot.shifts.forEach { shift ->
            val reserved = snapshot.reservations.filter {
                it.concreteShiftId == shift.id && it.status == ShiftReservationStatus.RESERVED
            }
            if (reserved.size > shift.capacity) return false
            if (!shift.enabled && reserved.isNotEmpty()) return false
        }
        snapshot.reservations.filter { it.status == ShiftReservationStatus.RESERVED }
            .groupBy { it.riderId }.values.forEach { reservations ->
                for (i in reservations.indices) {
                    for (j in i + 1 until reservations.size) {
                        val a = shiftsById[reservations[i].concreteShiftId] ?: return false
                        val b = shiftsById[reservations[j].concreteShiftId] ?: return false
                        if (ShiftSchedulePolicy.overlaps(a, b)) return false
                    }
                }
            }
        if (snapshot.audit.any { event ->
                shiftsById[event.concreteShiftId] == null || snapshot.reservations.none { it.id == event.reservationId }
            }) return false
        return true
    }

    fun isShiftSubsystemReady(): Boolean = shiftV2Ready

    fun shiftRule(id: String?): ShiftGenerationRule? = shiftRules.firstOrNull { it.id == id }

    fun concreteShift(id: String?): ConcreteShift? = concreteShifts.firstOrNull { it.id == id }

    fun concreteShifts(from: LocalDate, to: LocalDate): List<ConcreteShift> {
        if (!shiftV2Ready || to.isBefore(from)) return emptyList()
        return concreteShifts.filter { shift ->
            val date = ShiftSchedulePolicy.parseIsoDate(shift.serviceDate) ?: return@filter false
            !date.isBefore(from) && !date.isAfter(to)
        }.sortedBy { ShiftSchedulePolicy.window(it)?.first ?: LocalDateTime.MAX }
    }

    fun shiftWindow(shift: ConcreteShift): Pair<LocalDateTime, LocalDateTime>? =
        ShiftSchedulePolicy.window(shift)

    fun addShiftRule(days: Set<Int>, rawStart: String, rawEnd: String, capacity: Int): String? =
        synchronized(shiftMutationLock) {
            if (!shiftV2Ready) return@synchronized "El almacenamiento de Turnos no está disponible."
            val start = ShiftSchedulePolicy.parseClock(rawStart, allow24 = false)
                ?: return@synchronized "Horario de inicio inválido."
            val end = ShiftSchedulePolicy.parseClock(rawEnd, allow24 = true)
                ?: return@synchronized "Horario de fin inválido."
            val candidate = ShiftGenerationRule(
                id = "SGR-${UUID.randomUUID()}",
                daysOfWeek = days.toSet(),
                startMinute = start,
                endMinute = end,
                capacity = capacity
            )
            ShiftSchedulePolicy.validateRule(candidate)?.let { return@synchronized it }
            if (ShiftSchedulePolicy.firstRuleConflict(shiftRules, candidate) != null) {
                return@synchronized "La regla se superpone con otro horario configurado, incluso considerando cruces de medianoche."
            }
            val final = shiftRules + candidate
            if (!shiftStoreV2.saveRules(final)) return@synchronized "No se pudo guardar la configuración de turnos."
            shiftRules = final
            null
        }

    fun updateShiftRule(id: String, days: Set<Int>, rawStart: String, rawEnd: String, capacity: Int): String? =
        synchronized(shiftMutationLock) {
            if (!shiftV2Ready) return@synchronized "El almacenamiento de Turnos no está disponible."
            val current = shiftRules.firstOrNull { it.id == id } ?: return@synchronized "Regla no encontrada."
            val start = ShiftSchedulePolicy.parseClock(rawStart, allow24 = false)
                ?: return@synchronized "Horario de inicio inválido."
            val end = ShiftSchedulePolicy.parseClock(rawEnd, allow24 = true)
                ?: return@synchronized "Horario de fin inválido."
            val candidate = current.copy(daysOfWeek = days.toSet(), startMinute = start, endMinute = end, capacity = capacity)
            ShiftSchedulePolicy.validateRule(candidate)?.let { return@synchronized it }
            if (ShiftSchedulePolicy.firstRuleConflict(shiftRules, candidate) != null) {
                return@synchronized "La regla se superpone con otro horario configurado, incluso considerando cruces de medianoche."
            }
            if (candidate == current) return@synchronized null
            val final = shiftRules.map { if (it.id == id) candidate else it }
            if (!shiftStoreV2.saveRules(final)) return@synchronized "No se pudo guardar la configuración de turnos."
            shiftRules = final
            null
        }

    fun deleteShiftRule(id: String): Boolean = synchronized(shiftMutationLock) {
        if (!shiftV2Ready || shiftRules.none { it.id == id }) return@synchronized false
        val final = shiftRules.filterNot { it.id == id }
        if (!shiftStoreV2.saveRules(final)) return@synchronized false
        shiftRules = final
        true
    }

    fun previewShiftGeneration(request: ShiftGenerationRequest): ShiftGenerationPreview =
        synchronized(shiftMutationLock) {
            if (!shiftV2Ready) {
                ShiftGenerationPreview(request, 0, emptyList(), 0, 0, error = "El almacenamiento de Turnos no está disponible.")
            } else {
                ShiftSchedulePolicy.buildGenerationPreview(request, shiftRules, concreteShifts)
            }
        }

    fun confirmShiftGeneration(request: ShiftGenerationRequest): ShiftGenerationPreview =
        synchronized(shiftMutationLock) {
            if (!shiftV2Ready) {
                return@synchronized ShiftGenerationPreview(
                    request, 0, emptyList(), 0, 0,
                    error = "El almacenamiento de Turnos no está disponible."
                )
            }
            val current = ShiftSchedulePolicy.buildGenerationPreview(request, shiftRules, concreteShifts)
            if (!current.canConfirm || current.newShifts.isEmpty()) return@synchronized current
            val final = concreteShifts + current.newShifts
            if (!shiftStoreV2.saveConcreteShifts(final)) {
                return@synchronized current.copy(
                    newShifts = emptyList(),
                    error = "No se pudo persistir la generación. No se aplicó ningún turno."
                )
            }
            concreteShifts = final
            current
        }

    fun updateConcreteShift(
        id: String,
        rawStart: String,
        rawEnd: String,
        capacity: Int,
        enabled: Boolean
    ): String? = synchronized(shiftMutationLock) {
        if (!shiftV2Ready) return@synchronized "El almacenamiento de Turnos no está disponible."
        val current = concreteShifts.firstOrNull { it.id == id } ?: return@synchronized "Turno no encontrado."
        val start = ShiftSchedulePolicy.parseClock(rawStart, allow24 = false)
            ?: return@synchronized "Horario de inicio inválido."
        val end = ShiftSchedulePolicy.parseClock(rawEnd, allow24 = true)
            ?: return@synchronized "Horario de fin inválido."
        ShiftSchedulePolicy.validateWindow(start, end)?.let { return@synchronized it }
        if (capacity < 1) return@synchronized "El cupo debe ser mayor a cero."

        val reservedCount = concreteShiftReservations.count {
            it.concreteShiftId == id && it.status == ShiftReservationStatus.RESERVED
        }
        val hoursChanged = start != current.startMinute || end != current.endMinute
        if (reservedCount > 0 && hoursChanged) {
            return@synchronized "No se puede cambiar el horario mientras haya reservas activas."
        }
        if (capacity < reservedCount) {
            return@synchronized "El cupo no puede quedar por debajo de $reservedCount porque ya hay reservas activas."
        }
        if (reservedCount > 0 && current.enabled && !enabled) {
            return@synchronized "No se puede deshabilitar un turno mientras haya reservas activas."
        }
        if (start == current.startMinute && end == current.endMinute &&
            capacity == current.capacity && enabled == current.enabled) return@synchronized null

        val candidate = current.copy(
            startMinute = start,
            endMinute = end,
            capacity = capacity,
            enabled = enabled,
            isException = current.isException || current.originRuleId != null
        )
        if (concreteShifts.any { it.id != id && ShiftSchedulePolicy.overlaps(it, candidate) }) {
            return@synchronized "La regla se superpone con otro horario configurado, incluso considerando cruces de medianoche."
        }
        val final = concreteShifts.map { if (it.id == id) candidate else it }
        if (!shiftStoreV2.saveConcreteShifts(final)) return@synchronized "No se pudo guardar el turno."
        concreteShifts = final
        if (!enabled) disableAvailabilityIfNoActiveShiftForAll()
        null
    }

    fun riderHasActiveShiftNow(riderId: String, now: LocalDateTime = LocalDateTime.now()): Boolean {
        if (!shiftV2Ready) return false
        return concreteShiftReservations.any { reservation ->
            if (reservation.riderId != riderId || reservation.status != ShiftReservationStatus.RESERVED) return@any false
            val shift = concreteShifts.firstOrNull {
                it.id == reservation.concreteShiftId && it.enabled
            } ?: return@any false
            val window = ShiftSchedulePolicy.window(shift) ?: return@any false
            !now.isBefore(window.first) && now.isBefore(window.second)
        }
    }

    fun riderCanAccessNewOrdersDecision(riderId: String): RiderEligibilityDecision {
        val eligibility = riderCanViewShiftsDecision(riderId)
        if (!eligibility.allowed) return eligibility
        if (!riderHasActiveShiftNow(riderId)) return RiderEligibilityDecision.denied(RiderDenialReason.NO_ACTIVE_SHIFT)
        return RiderEligibilityDecision.ALLOWED
    }

    fun riderCanAccessNewOrders(riderId: String): Boolean =
        riderCanAccessNewOrdersDecision(riderId).allowed

    fun reserveShiftDecision(riderId: String, concreteShiftId: String): RiderEligibilityDecision =
        synchronized(shiftMutationLock) {
            if (!shiftV2Ready) return@synchronized RiderEligibilityDecision.denied(RiderDenialReason.SHIFT_NOT_AVAILABLE)
            val eligibility = riderCanViewShiftsDecision(riderId)
            if (!eligibility.allowed) return@synchronized eligibility
            val shift = concreteShifts.firstOrNull { it.id == concreteShiftId && it.enabled }
                ?: return@synchronized RiderEligibilityDecision.denied(RiderDenialReason.SHIFT_NOT_AVAILABLE)
            val window = ShiftSchedulePolicy.window(shift)
                ?: return@synchronized RiderEligibilityDecision.denied(RiderDenialReason.SHIFT_NOT_AVAILABLE)
            if (!LocalDateTime.now().isBefore(window.second)) {
                return@synchronized RiderEligibilityDecision.denied(RiderDenialReason.SHIFT_NOT_AVAILABLE)
            }
            val existing = concreteShiftReservations.firstOrNull {
                it.riderId == riderId && it.concreteShiftId == concreteShiftId
            }
            if (existing?.status == ShiftReservationStatus.RESERVED) return@synchronized RiderEligibilityDecision.ALLOWED
            if (existing?.blockedRejoin == true) return@synchronized RiderEligibilityDecision.denied(RiderDenialReason.REJOIN_BLOCKED)
            existing?.lastCancelledAt?.let { raw ->
                val cancelled = parseTimestamp(raw)
                    ?: return@synchronized RiderEligibilityDecision.denied(RiderDenialReason.SHIFT_NOT_AVAILABLE)
                if (Duration.between(cancelled, LocalDateTime.now()).toMinutes() < 15) {
                    return@synchronized RiderEligibilityDecision.denied(RiderDenialReason.REJOIN_COOLDOWN)
                }
            }
            val overlap = concreteShiftReservations.any { reservation ->
                reservation.riderId == riderId &&
                    reservation.status == ShiftReservationStatus.RESERVED &&
                    reservation.concreteShiftId != concreteShiftId &&
                    concreteShifts.firstOrNull { it.id == reservation.concreteShiftId }
                        ?.let { ShiftSchedulePolicy.overlaps(it, shift) } == true
            }
            if (overlap) return@synchronized RiderEligibilityDecision.denied(RiderDenialReason.SHIFT_NOT_AVAILABLE)
            val occupied = concreteShiftReservations.count {
                it.concreteShiftId == concreteShiftId && it.status == ShiftReservationStatus.RESERVED
            }
            if (occupied >= shift.capacity) return@synchronized RiderEligibilityDecision.denied(RiderDenialReason.SHIFT_FULL)
            RiderEligibilityDecision.ALLOWED
        }

    fun reserveShiftWithDecision(riderId: String, concreteShiftId: String): RiderEligibilityDecision =
        synchronized(shiftMutationLock) {
            val decision = reserveShiftDecision(riderId, concreteShiftId)
            if (!decision.allowed) return@synchronized decision
            val shift = concreteShifts.firstOrNull { it.id == concreteShiftId }
                ?: return@synchronized RiderEligibilityDecision.denied(RiderDenialReason.SHIFT_NOT_AVAILABLE)
            val existing = concreteShiftReservations.firstOrNull {
                it.riderId == riderId && it.concreteShiftId == concreteShiftId
            }
            if (existing?.status == ShiftReservationStatus.RESERVED) return@synchronized RiderEligibilityDecision.ALLOWED
            val joinedAt = nowText()
            val reservation = if (existing == null) {
                ConcreteShiftReservation(
                    id = "CSR-${UUID.randomUUID()}",
                    riderId = riderId,
                    concreteShiftId = concreteShiftId,
                    joinedAt = joinedAt
                )
            } else {
                existing.copy(status = ShiftReservationStatus.RESERVED, joinedAt = joinedAt, cancelledAt = null)
            }
            val finalReservations = concreteShiftReservations.filterNot { it.id == reservation.id } + reservation
            val finalAudit = concreteShiftAuditEvents + concreteAuditEvent(
                reservation, shift, ShiftEventType.RIDER_JOINED, riderId
            )
            if (!shiftStoreV2.saveReservationsAndAudit(finalReservations, finalAudit)) {
                return@synchronized RiderEligibilityDecision.denied(RiderDenialReason.SHIFT_NOT_AVAILABLE)
            }
            concreteShiftReservations = finalReservations
            concreteShiftAuditEvents = finalAudit
            RiderEligibilityDecision.ALLOWED
        }

    fun reserveShift(riderId: String, concreteShiftId: String): Boolean =
        publishRiderDecision(reserveShiftWithDecision(riderId, concreteShiftId)).allowed

    fun canCancelConcreteShift(reservation: ConcreteShiftReservation): Boolean {
        if (reservation.status != ShiftReservationStatus.RESERVED) return false
        if (concreteShifts.none { it.id == reservation.concreteShiftId }) return false
        val joined = parseTimestamp(reservation.joinedAt) ?: return false
        return Duration.between(joined, LocalDateTime.now()).toMinutes() in 0..15
    }

    fun cancelConcreteShift(reservationId: String): Boolean = synchronized(shiftMutationLock) {
        if (!shiftV2Ready) return@synchronized false
        val current = concreteShiftReservations.firstOrNull { it.id == reservationId } ?: return@synchronized false
        if (!hasAuthenticatedRiderSession(current.riderId) || !canCancelConcreteShift(current)) return@synchronized false
        val shift = concreteShifts.firstOrNull { it.id == current.concreteShiftId } ?: return@synchronized false
        val now = nowText()
        val count = current.cancellationCount + 1
        val updated = current.copy(
            status = ShiftReservationStatus.CANCELLED,
            cancelledAt = now,
            lastCancelledAt = now,
            cancellationCount = count,
            blockedRejoin = count >= 2
        )
        val finalReservations = concreteShiftReservations.map { if (it.id == reservationId) updated else it }
        val finalAudit = concreteShiftAuditEvents + concreteAuditEvent(
            updated, shift, ShiftEventType.RIDER_CANCELLED, current.riderId
        )
        if (!shiftStoreV2.saveReservationsAndAudit(finalReservations, finalAudit)) return@synchronized false
        concreteShiftReservations = finalReservations
        concreteShiftAuditEvents = finalAudit
        disableAvailabilityIfNoActiveShift(current.riderId)
        true
    }

    fun adminAddRiderToShift(riderId: String, concreteShiftId: String): Boolean =
        synchronized(shiftMutationLock) {
            if (!shiftV2Ready) return@synchronized false
            val target = rider(riderId) ?: return@synchronized false
            if (!riderAccountEligibility(target).allowed) return@synchronized false
            val shift = concreteShifts.firstOrNull { it.id == concreteShiftId && it.enabled } ?: return@synchronized false
            val window = ShiftSchedulePolicy.window(shift) ?: return@synchronized false
            if (!LocalDateTime.now().isBefore(window.second)) return@synchronized false
            val existing = concreteShiftReservations.firstOrNull {
                it.riderId == riderId && it.concreteShiftId == concreteShiftId
            }
            if (existing?.status == ShiftReservationStatus.RESERVED) return@synchronized true
            val overlap = concreteShiftReservations.any { reservation ->
                reservation.riderId == riderId &&
                    reservation.status == ShiftReservationStatus.RESERVED &&
                    reservation.concreteShiftId != concreteShiftId &&
                    concreteShifts.firstOrNull { it.id == reservation.concreteShiftId }
                        ?.let { ShiftSchedulePolicy.overlaps(it, shift) } == true
            }
            if (overlap) return@synchronized false
            val occupied = concreteShiftReservations.count {
                it.concreteShiftId == concreteShiftId && it.status == ShiftReservationStatus.RESERVED
            }
            if (occupied >= shift.capacity) return@synchronized false
            val now = nowText()
            val reservation = if (existing == null) {
                ConcreteShiftReservation(
                    id = "CSR-${UUID.randomUUID()}",
                    riderId = riderId,
                    concreteShiftId = concreteShiftId,
                    joinedAt = now
                )
            } else {
                existing.copy(status = ShiftReservationStatus.RESERVED, joinedAt = now, cancelledAt = null)
            }
            val finalReservations = concreteShiftReservations.filterNot { it.id == reservation.id } + reservation
            val finalAudit = concreteShiftAuditEvents + concreteAuditEvent(
                reservation, shift, ShiftEventType.ADMIN_ADDED, "ADMIN"
            )
            if (!shiftStoreV2.saveReservationsAndAudit(finalReservations, finalAudit)) return@synchronized false
            concreteShiftReservations = finalReservations
            concreteShiftAuditEvents = finalAudit
            true
        }

    fun adminRemoveRiderFromConcreteShift(reservationId: String): Boolean = synchronized(shiftMutationLock) {
        if (!shiftV2Ready) return@synchronized false
        val current = concreteShiftReservations.firstOrNull { it.id == reservationId } ?: return@synchronized false
        if (current.status != ShiftReservationStatus.RESERVED) return@synchronized false
        val shift = concreteShifts.firstOrNull { it.id == current.concreteShiftId } ?: return@synchronized false
        val now = nowText()
        val updated = current.copy(
            status = ShiftReservationStatus.CANCELLED,
            cancelledAt = now,
            lastCancelledAt = now,
            blockedRejoin = true
        )
        val finalReservations = concreteShiftReservations.map { if (it.id == reservationId) updated else it }
        val finalAudit = concreteShiftAuditEvents + concreteAuditEvent(
            updated, shift, ShiftEventType.ADMIN_REMOVED, "ADMIN"
        )
        if (!shiftStoreV2.saveReservationsAndAudit(finalReservations, finalAudit)) return@synchronized false
        concreteShiftReservations = finalReservations
        concreteShiftAuditEvents = finalAudit
        disableAvailabilityIfNoActiveShift(current.riderId)
        true
    }

    private fun concreteAuditEvent(
        reservation: ConcreteShiftReservation,
        shift: ConcreteShift,
        type: ShiftEventType,
        actor: String
    ): ConcreteShiftAuditEvent = ConcreteShiftAuditEvent(
        id = "CSE-${UUID.randomUUID()}",
        reservationId = reservation.id,
        concreteShiftId = reservation.concreteShiftId,
        riderId = reservation.riderId,
        serviceDate = shift.serviceDate,
        type = type,
        at = nowText(),
        actor = actor
    )

    private fun disableAvailabilityIfNoActiveShift(riderId: String) {
        if (riderHasActiveShiftNow(riderId)) return
        riders = riders.map { if (it.id == riderId) it.copy(available = false, availableUntilAt = null) else it }
        store.saveRiders(riders)
    }

    private fun disableAvailabilityIfNoActiveShiftForAll() {
        riders.filter { it.available }.forEach { disableAvailabilityIfNoActiveShift(it.id) }
    }

    fun minutesUntilRiderCanRejoin(reservation: ConcreteShiftReservation): Long {
        val at = reservation.lastCancelledAt?.let(::parseTimestamp) ?: return 0
        return (15 - Duration.between(at, LocalDateTime.now()).toMinutes()).coerceAtLeast(0)
    }

    @Deprecated("ShiftTemplate v1 is disabled by TODO-3A")
    fun saveShiftTemplate(shift: ShiftTemplate) = Unit

    @Deprecated("ShiftTemplate v1 is disabled by TODO-3A")
    fun isShiftActiveNow(shift: ShiftTemplate, now: LocalDateTime = LocalDateTime.now()): Boolean = false

    @Deprecated("ShiftTemplate v1 is disabled by TODO-3A")
    fun canDeleteShiftTemplate(id: String): Boolean = false

    @Deprecated("ShiftTemplate v1 is disabled by TODO-3A")
    fun deleteShiftTemplate(id: String): Boolean = false

    @Deprecated("ShiftTemplate v1 is disabled by TODO-3A")
    fun addShiftTemplatesBulk(days: Set<Int>, ranges: List<Pair<String, String>>, capacity: Int): String? =
        "El modelo semanal legacy fue retirado."

    @Deprecated("ShiftTemplate v1 is disabled by TODO-3A")
    fun addSpecificDateShifts(dateText: String, ranges: List<Pair<String, String>>, capacity: Int): String? =
        "El modelo de fecha específica legacy fue retirado."

    @Deprecated("ShiftTemplate v1 is disabled by TODO-3A")
    fun updateShiftTemplate(
        id: String,
        recurring: Boolean,
        dayOfWeek: Int,
        specificDate: String?,
        rawStart: String,
        rawEnd: String,
        capacity: Int,
        enabled: Boolean
    ): String? = "El modelo legacy fue retirado."

    fun normalizeClock(raw: String, allow24: Boolean): String? =
        ShiftSchedulePolicy.parseClock(raw, allow24)?.let(ShiftSchedulePolicy::formatMinute)

    @Deprecated("ShiftTemplate v1 is disabled by TODO-3A")
    fun shiftWindow(shift: ShiftTemplate, serviceDate: String): Pair<LocalDateTime, LocalDateTime>? = null

    @Deprecated("ShiftTemplate v1 is disabled by TODO-3A")
    fun shiftOccurrences(from: LocalDate, to: LocalDate): List<Pair<ShiftTemplate, String>> = emptyList()

    @Deprecated("Caller-provided serviceDate is disabled by TODO-3A")
    fun reserveShiftDecision(riderId: String, shiftId: String, serviceDate: String): RiderEligibilityDecision =
        RiderEligibilityDecision.denied(RiderDenialReason.SHIFT_NOT_AVAILABLE)

    @Deprecated("Caller-provided serviceDate is disabled by TODO-3A")
    fun reserveShiftWithDecision(riderId: String, shiftId: String, serviceDate: String): RiderEligibilityDecision =
        RiderEligibilityDecision.denied(RiderDenialReason.SHIFT_NOT_AVAILABLE)

    @Deprecated("Caller-provided serviceDate is disabled by TODO-3A")
    fun reserveShift(riderId: String, shiftId: String, serviceDate: String): Boolean = false

    @Deprecated("Legacy shift reservation is disabled by TODO-3A")
    fun canCancelShift(reservation: RiderShiftReservation): Boolean = false

    @Deprecated("Legacy shift reservation is disabled by TODO-3A")
    fun cancelShift(reservationId: String): Boolean = false

    @Deprecated("Caller-provided serviceDate is disabled by TODO-3A")
    fun adminAddRiderToShift(riderId: String, shiftId: String, serviceDate: String): Boolean = false

    @Deprecated("Legacy shift reservation is disabled by TODO-3A")
    fun adminRemoveRiderFromShift(reservationId: String): Boolean = false

    @Deprecated("Legacy shift reservation is disabled by TODO-3A")
    fun minutesUntilRiderCanRejoin(reservation: RiderShiftReservation): Long = 0

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
            if (authenticatedRiderId == riderId) authenticatedRiderId = null
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
        authenticatedRiderId = null
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
        val target = rider(invitation.riderId) ?: return null
        if (!target.active) return null
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

    fun authenticateRiderResult(riderId: String, password: String): RiderAuthenticationResult {
        authenticatedRiderId = null
        riderDenialFeedback = null
        val normalizedId = riderId.trim()
        val target = riders.firstOrNull { it.id.equals(normalizedId, ignoreCase = true) }
            ?: return RiderAuthenticationResult(RiderAuthenticationStatus.INVALID_CREDENTIALS)
        val credential = riderCredentials.firstOrNull { it.riderId.equals(target.id, ignoreCase = true) }
            ?: return RiderAuthenticationResult(RiderAuthenticationStatus.INVALID_CREDENTIALS)
        if (!verifyRiderPassword(password, credential)) {
            return RiderAuthenticationResult(RiderAuthenticationStatus.INVALID_CREDENTIALS)
        }
        if (!target.active) {
            return RiderAuthenticationResult(
                status = RiderAuthenticationStatus.DEACTIVATED,
                riderId = target.id,
                riderName = target.name
            )
        }
        authenticatedRiderId = target.id
        return RiderAuthenticationResult(
            status = RiderAuthenticationStatus.AUTHENTICATED,
            riderId = target.id,
            riderName = target.name
        )
    }

    fun authenticateRider(riderId: String, password: String): Boolean =
        authenticateRiderResult(riderId, password).status == RiderAuthenticationStatus.AUTHENTICATED

    fun logoutRider() {
        authenticatedRiderId = null
        riderDenialFeedback = null
    }

    fun hasAuthenticatedRiderSession(riderId: String): Boolean =
        authenticatedRiderFor(riderId) != null

    private fun authenticatedRiderFor(riderId: String): RiderProfile? {
        if (!riderActorMatchesAuthenticatedSession(authenticatedRiderId, riderId)) return null
        val target = rider(riderId) ?: return null
        if (!hasRiderCredential(riderId)) return null
        if (!target.active) {
            if (authenticatedRiderId == target.id) authenticatedRiderId = null
            return null
        }
        return target
    }

    fun changeRiderPassword(riderId: String, currentPassword: String, newPassword: String): Boolean {
        if (authenticatedRiderFor(riderId) == null || !passwordIsStrong(newPassword)) return false
        val currentCredential = riderCredentials.firstOrNull { it.riderId == riderId } ?: return false
        if (!verifyRiderPassword(currentPassword, currentCredential)) return false
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
        appendLine("Punto25 · NUEVA SOLICUD")
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