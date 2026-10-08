package ar.com.mandados.app

internal sealed interface OrderZoneOverrideSelection {
    data object Declared : OrderZoneOverrideSelection
    data class Catalog(val zoneId: String) : OrderZoneOverrideSelection
    data class AdHoc(val name: String, val price: Int) : OrderZoneOverrideSelection
}

internal data class OrderZoneOverrideEvaluation(
    val allowed: Boolean,
    val issue: String? = null,
    val point: OrderZonePoint,
    val declaredLabel: String,
    val previousAppliedLabel: String,
    val nextAppliedLabel: String,
    val currentTotal: Int?,
    val candidateTotal: Int?,
    val candidateOrder: LocalOrder? = null,
    val paymentMutation: OrderPaymentMutationAction = OrderPaymentMutationAction.DENY
)

private data class ResolvedPricingZone(
    val name: String,
    val price: Int
)

private data class PricingZoneResolution(
    val zone: ResolvedPricingZone? = null,
    val issue: String? = null,
    val unresolvedDeclared: Boolean = false
)

internal fun applicableOrderZonePoints(order: LocalOrder): List<OrderZonePoint> =
    applicableOrderZonePoints(
        serviceType = order.serviceType,
        instructionType = order.instructionType,
        purchasePayment = order.purchasePayment
    )

internal fun applicableOrderZonePoints(draft: OrderDraft): List<OrderZonePoint> =
    applicableOrderZonePoints(
        serviceType = draft.serviceType,
        instructionType = draft.instructionType,
        purchasePayment = draft.purchasePayment
    )

private fun applicableOrderZonePoints(
    serviceType: ServiceType,
    instructionType: PurchaseInstructionType,
    purchasePayment: PurchasePaymentMethod
): List<OrderZonePoint> = when (serviceType) {
    ServiceType.DELIVERY -> listOf(OrderZonePoint.ORIGIN, OrderZonePoint.DESTINATION)
    ServiceType.SHOPPING -> buildList {
        add(OrderZonePoint.STORE)
        if (instructionType != PurchaseInstructionType.IN_APP || purchasePayment == PurchasePaymentMethod.CASH_PRE_PICKUP) {
            add(OrderZonePoint.PRE_PICKUP)
        }
        add(OrderZonePoint.DESTINATION)
    }
}

internal fun declaredZoneIdFor(order: LocalOrder, point: OrderZonePoint): String = when (point) {
    OrderZonePoint.ORIGIN -> order.originZoneId
    OrderZonePoint.DESTINATION -> order.destinationZoneId
    OrderZonePoint.STORE -> order.storeZoneId
    OrderZonePoint.PRE_PICKUP -> order.prePickupZoneId
}

internal fun declaredZoneIdFor(draft: OrderDraft, point: OrderZonePoint): String = when (point) {
    OrderZonePoint.ORIGIN -> draft.originZoneId
    OrderZonePoint.DESTINATION -> draft.destinationZoneId
    OrderZonePoint.STORE -> draft.storeZoneId
    OrderZonePoint.PRE_PICKUP -> draft.prePickupZoneId
}

internal fun orderZonePointLabel(point: OrderZonePoint): String = when (point) {
    OrderZonePoint.ORIGIN -> "Retiro"
    OrderZonePoint.DESTINATION -> "Entrega"
    OrderZonePoint.STORE -> "Comercio"
    OrderZonePoint.PRE_PICKUP -> "Retiro previo"
}

internal fun resolveOrderZoneOverrideSelection(
    selection: OrderZoneOverrideSelection,
    config: AdminConfig
): Result<OrderZoneOverride?> = when (selection) {
    OrderZoneOverrideSelection.Declared -> Result.success(null)
    is OrderZoneOverrideSelection.Catalog -> {
        val zone = config.zones.firstOrNull { it.id == selection.zoneId }
        when {
            zone == null -> Result.failure(IllegalArgumentException("La zona del catálogo no existe."))
            !zone.enabled -> Result.failure(IllegalArgumentException("La zona del catálogo está deshabilitada."))
            zone.price <= 0 -> Result.failure(IllegalArgumentException("La zona del catálogo no tiene una tarifa válida."))
            zone.name.trim().isBlank() -> Result.failure(IllegalArgumentException("La zona del catálogo no tiene un nombre válido."))
            else -> Result.success(
                OrderZoneOverride(
                    source = OrderZoneOverrideSource.CATALOG,
                    catalogZoneId = zone.id,
                    name = zone.name.trim(),
                    price = zone.price
                )
            )
        }
    }
    is OrderZoneOverrideSelection.AdHoc -> {
        val cleanName = selection.name.trim()
        when {
            cleanName.isBlank() -> Result.failure(IllegalArgumentException("Ingresá un nombre para la zona circunstancial."))
            selection.price <= 0 -> Result.failure(IllegalArgumentException("La tarifa de la zona circunstancial debe ser mayor a cero."))
            else -> Result.success(
                OrderZoneOverride(
                    source = OrderZoneOverrideSource.AD_HOC,
                    catalogZoneId = null,
                    name = cleanName,
                    price = selection.price
                )
            )
        }
    }
}

internal fun declaredOrderZoneLabel(
    order: LocalOrder,
    point: OrderZonePoint,
    config: AdminConfig
): String = declaredZoneLabel(declaredZoneIdFor(order, point), config)

internal fun appliedOrderZoneLabel(
    order: LocalOrder,
    point: OrderZonePoint,
    config: AdminConfig
): String = order.zoneOverrides[point]?.let(::overrideLabel)
    ?: declaredOrderZoneLabel(order, point, config)

private fun declaredZoneLabel(zoneId: String, config: AdminConfig): String = when {
    zoneId.isBlank() -> "Sin zona declarada"
    zoneId == UNKNOWN_ZONE_ID -> "A confirmar"
    else -> config.zones.firstOrNull { it.id == zoneId }?.let { zone ->
        "${zone.name} · $${zone.price} · id=${zone.id}"
    } ?: "Zona no disponible · id=$zoneId"
}

private fun overrideLabel(override: OrderZoneOverride): String = when (override.source) {
    OrderZoneOverrideSource.CATALOG ->
        "${override.name} · $${override.price} · CATALOG · id=${override.catalogZoneId.orEmpty()}"
    OrderZoneOverrideSource.AD_HOC ->
        "${override.name} · $${override.price} · AD_HOC"
}

private fun pricingPoints(
    draft: OrderDraft,
    overrides: Map<OrderZonePoint, OrderZoneOverride>
): List<OrderZonePoint> = when (draft.serviceType) {
    ServiceType.DELIVERY -> listOf(OrderZonePoint.ORIGIN, OrderZonePoint.DESTINATION)
    ServiceType.SHOPPING -> buildList {
        if (draft.storeZoneId.isNotBlank() || overrides.containsKey(OrderZonePoint.STORE)) {
            add(OrderZonePoint.STORE)
        }
        if (draft.requiresPrePickup()) add(OrderZonePoint.PRE_PICKUP)
        add(OrderZonePoint.DESTINATION)
    }
}

private fun resolvePricingZone(
    zoneId: String,
    override: OrderZoneOverride?,
    config: AdminConfig
): PricingZoneResolution {
    if (override != null) {
        if (override.name.trim().isBlank() || override.price <= 0) {
            return PricingZoneResolution(issue = "Zona aplicada inválida")
        }
        if (override.source == OrderZoneOverrideSource.CATALOG && override.catalogZoneId.isNullOrBlank()) {
            return PricingZoneResolution(issue = "Zona aplicada inválida")
        }
        return PricingZoneResolution(ResolvedPricingZone(override.name, override.price))
    }

    if (zoneId.isBlank() || zoneId == UNKNOWN_ZONE_ID) {
        return PricingZoneResolution(issue = "Zona a confirmar", unresolvedDeclared = true)
    }
    val zone = config.zones.firstOrNull { it.id == zoneId }
        ?: return PricingZoneResolution(issue = "Zona inválida")
    if (!zone.enabled) return PricingZoneResolution(issue = "Hay una zona deshabilitada")
    if (zone.price <= 0) return PricingZoneResolution(issue = "Falta configurar una tarifa")
    return PricingZoneResolution(ResolvedPricingZone(zone.name, zone.price))
}

internal fun calculateOrderPricing(
    draft: OrderDraft,
    overrides: Map<OrderZonePoint, OrderZoneOverride>,
    config: AdminConfig
): PricingResult {
    val points = pricingPoints(draft, overrides)
    val resolved = points.map { point ->
        resolvePricingZone(
            zoneId = declaredZoneIdFor(draft, point),
            override = overrides[point],
            config = config
        )
    }
    val issue = resolved.firstOrNull { it.zone == null }
    if (issue != null) {
        val rain = if (issue.unresolvedDeclared && config.rainEnabled) config.rainAmount else if (issue.unresolvedDeclared) 0 else null
        return PricingResult(true, null, null, null, rain, null, issue.issue)
    }

    val zones = resolved.map { requireNotNull(it.zone) }
    if (zones.isEmpty()) {
        return PricingResult(true, null, null, null, null, null, "Zona a confirmar")
    }
    val baseZone = zones.maxBy { it.price }
    val pre = if (draft.serviceType == ServiceType.SHOPPING && draft.requiresPrePickup()) {
        when (config.prePickupMode) {
            PrePickupMode.OFF -> 0
            PrePickupMode.FIXED -> config.prePickupValue
            PrePickupMode.PERCENT_BASE -> percentOfBaseRoundedUpToHundredForZoneOverride(baseZone.price, config.prePickupValue)
        }
    } else {
        0
    }
    val rain = if (config.rainEnabled) config.rainAmount else 0
    return PricingResult(
        needsQuote = false,
        baseAmount = baseZone.price,
        baseZoneName = baseZone.name,
        prePickupAmount = pre,
        rainAmount = rain,
        totalAmount = baseZone.price + pre + rain
    )
}

internal fun calculateOrderPricing(
    order: LocalOrder,
    overrides: Map<OrderZonePoint, OrderZoneOverride>,
    config: AdminConfig
): PricingResult = calculateOrderPricing(
    draft = OrderDraft(
        serviceType = order.serviceType,
        category = order.category,
        originZoneId = order.originZoneId,
        destinationZoneId = order.destinationZoneId,
        instructionType = order.instructionType,
        storeZoneId = order.storeZoneId,
        purchasePayment = order.purchasePayment,
        prePickupZoneId = order.prePickupZoneId,
        deliveryPayment = order.deliveryPayment
    ),
    overrides = overrides,
    config = config
)

internal fun evaluateOrderZoneOverride(
    currentOrder: LocalOrder,
    point: OrderZonePoint,
    selection: OrderZoneOverrideSelection,
    config: AdminConfig,
    paymentMatches: List<PaymentRecord>
): OrderZoneOverrideEvaluation {
    val declaredLabel = declaredOrderZoneLabel(currentOrder, point, config)
    val previousAppliedLabel = appliedOrderZoneLabel(currentOrder, point, config)

    fun denied(issue: String, nextLabel: String = previousAppliedLabel): OrderZoneOverrideEvaluation =
        OrderZoneOverrideEvaluation(
            allowed = false,
            issue = issue,
            point = point,
            declaredLabel = declaredLabel,
            previousAppliedLabel = previousAppliedLabel,
            nextAppliedLabel = nextLabel,
            currentTotal = currentOrder.totalAmount,
            candidateTotal = currentOrder.totalAmount
        )

    if (currentOrder.operationMode != OperationMode.MULTI_RIDER) {
        return denied("La corrección de zona sólo está disponible en pedidos Multi-Repartidor.")
    }
    if (currentOrder.status in setOf(OrderStatus.COMPLETED, OrderStatus.CANCELLED, OrderStatus.REJECTED)) {
        return denied("El pedido está finalizado y no admite correcciones de zona.")
    }
    if (point !in applicableOrderZonePoints(currentOrder)) {
        return denied("Ese punto no aplica a este pedido.")
    }
    if (paymentMatches.size > 1) {
        return denied("El pedido tiene más de un registro de pago y requiere revisión manual.")
    }

    val overrideResult = resolveOrderZoneOverrideSelection(selection, config)
    val nextOverride = overrideResult.getOrElse { return denied(it.message ?: "Zona inválida.") }
    val nextOverrides = currentOrder.zoneOverrides.toMutableMap().apply {
        if (nextOverride == null) remove(point) else put(point, nextOverride)
    }.toMap()
    if (nextOverrides == currentOrder.zoneOverrides) return denied("No hay cambios para aplicar.")

    val candidatePricing = calculateOrderPricing(currentOrder, nextOverrides, config)
    val candidateStatus = when (currentOrder.status) {
        OrderStatus.AWAITING_QUOTE -> if (candidatePricing.needsQuote) OrderStatus.AWAITING_QUOTE else OrderStatus.PENDING
        else -> currentOrder.status
    }
    if (currentOrder.status != OrderStatus.AWAITING_QUOTE && candidatePricing.totalAmount == null) {
        return denied("Un pedido activo tarifado no puede volver a una tarifa desconocida.")
    }

    val candidate = currentOrder.copy(
        status = candidateStatus,
        baseAmount = candidatePricing.baseAmount,
        baseZoneName = candidatePricing.baseZoneName,
        prePickupAmount = candidatePricing.prePickupAmount,
        rainAmount = candidatePricing.rainAmount,
        totalAmount = candidatePricing.totalAmount,
        zoneOverrides = nextOverrides
    )
    val paymentMutation = evaluateOrderPaymentMutation(
        currentOrder = currentOrder,
        candidateOrder = candidate,
        payment = paymentMatches.singleOrNull()
    )
    val nextLabel = nextOverride?.let(::overrideLabel) ?: declaredLabel
    if (paymentMutation == OrderPaymentMutationAction.DENY) {
        return OrderZoneOverrideEvaluation(
            allowed = false,
            issue = "La corrección no puede aplicarse porque el estado financiero del pedido está comprometido o es inconsistente.",
            point = point,
            declaredLabel = declaredLabel,
            previousAppliedLabel = previousAppliedLabel,
            nextAppliedLabel = nextLabel,
            currentTotal = currentOrder.totalAmount,
            candidateTotal = candidate.totalAmount
        )
    }

    return OrderZoneOverrideEvaluation(
        allowed = true,
        point = point,
        declaredLabel = declaredLabel,
        previousAppliedLabel = previousAppliedLabel,
        nextAppliedLabel = nextLabel,
        currentTotal = currentOrder.totalAmount,
        candidateTotal = candidate.totalAmount,
        candidateOrder = candidate,
        paymentMutation = paymentMutation
    )
}

internal fun zoneOverrideAuditNote(
    evaluation: OrderZoneOverrideEvaluation,
    reason: String
): String = buildString {
    append("Corrección de zona aplicada · punto=")
    append(evaluation.point.name)
    append(" · zona declarada=")
    append(evaluation.declaredLabel)
    append(" · zona aplicada anterior=")
    append(evaluation.previousAppliedLabel)
    append(" · zona aplicada nueva=")
    append(evaluation.nextAppliedLabel)
    append(" · total anterior=")
    append(evaluation.currentTotal?.toString() ?: "A confirmar")
    append(" · total nuevo=")
    append(evaluation.candidateTotal?.toString() ?: "A confirmar")
    append(" · motivo=")
    append(reason.trim())
}

private fun percentOfBaseRoundedUpToHundredForZoneOverride(base: Int, percent: Int): Int {
    if (base <= 0 || percent <= 0) return 0
    val numerator = base.toLong() * percent.toLong()
    val rounded = ((numerator + 9_999L) / 10_000L) * 100L
    return rounded.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}
