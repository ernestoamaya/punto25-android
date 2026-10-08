package ar.com.mandados.app

import kotlin.math.abs
import kotlin.math.max

enum class OrderLocationKind(
    val label: String,
    val adminButtonLabel: String
) {
    ORIGIN("Retiro", "VER RETIRO"),
    PRE_PICKUP("Retiro previo", "VER RETIRO PREVIO"),
    STORE("Comercio", "VER COMERCIO"),
    DESTINATION("Entrega", "VER ENTREGA")
}

data class OrderLocationPoint(
    val kind: OrderLocationKind,
    val point: GeoPoint
)

data class OrderPresentationLine(
    val label: String,
    val value: String
)

data class OrderMapCoordinate(
    val longitude: Double,
    val latitude: Double
)

data class OrderLocationsViewport(
    val target: GeoPoint,
    val zoom: Double
)

internal fun isValidOrderLocation(point: GeoPoint?): Boolean =
    point != null &&
        point.latitude.isFinite() && point.longitude.isFinite() &&
        point.latitude in -90.0..90.0 && point.longitude in -180.0..180.0

internal fun orderLocationPoints(order: LocalOrder): List<OrderLocationPoint> = buildList {
    fun addIfPresent(kind: OrderLocationKind, point: GeoPoint?) {
        if (isValidOrderLocation(point)) add(OrderLocationPoint(kind, point!!))
    }

    when (order.serviceType) {
        ServiceType.DELIVERY -> {
            addIfPresent(OrderLocationKind.ORIGIN, order.originLocation)
            addIfPresent(OrderLocationKind.DESTINATION, order.destinationLocation)
        }
        ServiceType.SHOPPING -> {
            addIfPresent(OrderLocationKind.PRE_PICKUP, order.prePickupLocation)
            addIfPresent(OrderLocationKind.STORE, order.storeLocation)
            addIfPresent(OrderLocationKind.DESTINATION, order.destinationLocation)
        }
    }
}

internal fun orderMapCoordinate(point: GeoPoint): OrderMapCoordinate =
    OrderMapCoordinate(longitude = point.longitude, latitude = point.latitude)

internal fun orderLocationsViewport(points: List<OrderLocationPoint>): OrderLocationsViewport? {
    if (points.isEmpty()) return null
    if (points.size == 1) return OrderLocationsViewport(points.first().point, 15.0)

    val latitudes = points.map { it.point.latitude }
    val longitudes = points.map { it.point.longitude }
    val minLat = latitudes.minOrNull() ?: return null
    val maxLat = latitudes.maxOrNull() ?: return null
    val minLon = longitudes.minOrNull() ?: return null
    val maxLon = longitudes.maxOrNull() ?: return null
    val span = max(abs(maxLat - minLat), abs(maxLon - minLon))
    val zoom = when {
        span <= 0.002 -> 15.0
        span <= 0.01 -> 13.5
        span <= 0.05 -> 11.5
        span <= 0.2 -> 9.5
        span <= 1.0 -> 7.5
        else -> 5.5
    }
    return OrderLocationsViewport(
        target = GeoPoint(
            latitude = (minLat + maxLat) / 2.0,
            longitude = (minLon + maxLon) / 2.0
        ),
        zoom = zoom
    )
}

internal fun orderPresentationLines(
    order: LocalOrder,
    includeLocationDetails: Boolean = true,
    zoneLabel: (String) -> String? = { id -> id.takeIf(String::isNotBlank) }
): List<OrderPresentationLine> {
    val locationKinds = if (includeLocationDetails) orderLocationPoints(order).associateBy { it.kind } else emptyMap()
    val lines = mutableListOf<OrderPresentationLine>()

    fun add(label: String, value: String?) {
        val cleaned = value?.trim().orEmpty()
        if (cleaned.isNotBlank()) lines += OrderPresentationLine(label, cleaned)
    }

    fun addressOrLocation(address: String, kind: OrderLocationKind): String? = when {
        address.isNotBlank() -> address
        kind in locationKinds -> "Ubicación marcada"
        else -> null
    }

    fun zone(id: String): String? = if (id.isBlank()) null else zoneLabel(id)

    add("Tipo", serviceCategoryPresentationLabel(order.category))
    when (order.serviceType) {
        ServiceType.DELIVERY -> {
            if (includeLocationDetails) {
                add("Retiro", addressOrLocation(order.originAddress, OrderLocationKind.ORIGIN))
                add("Referencia retiro", order.originReference)
                add("Zona retiro", zone(order.originZoneId))
                add("Entrega", addressOrLocation(order.destinationAddress, OrderLocationKind.DESTINATION))
                add("Referencia entrega", order.destinationReference)
                add("Zona entrega", zone(order.destinationZoneId))
            }
            add("Contenido", order.carriedItem)
        }
        ServiceType.SHOPPING -> {
            add("Pedido", purchaseInstructionPresentationLabel(order.instructionType))
            add("Descripción", order.purchaseDescription)
            if (order.purchaseMaxAmount > 0) add("Presupuesto máximo", presentationMoney(order.purchaseMaxAmount))

            if (includeLocationDetails) {
                val store = listOf(order.storeName, order.storeAddress)
                    .map(String::trim)
                    .filter(String::isNotBlank)
                    .joinToString(" · ")
                add(
                    "Comercio",
                    store.ifBlank {
                        if (OrderLocationKind.STORE in locationKinds) "Ubicación marcada" else ""
                    }
                )
                add("Zona comercio", zone(order.storeZoneId))

                if (
                    order.prePickupAddress.isNotBlank() || order.prePickupReference.isNotBlank() ||
                    order.prePickupZoneId.isNotBlank() || OrderLocationKind.PRE_PICKUP in locationKinds
                ) {
                    add("Retiro previo", addressOrLocation(order.prePickupAddress, OrderLocationKind.PRE_PICKUP))
                    add("Referencia retiro previo", order.prePickupReference)
                    add("Zona retiro previo", zone(order.prePickupZoneId))
                }

                add("Entrega", addressOrLocation(order.destinationAddress, OrderLocationKind.DESTINATION))
                add("Referencia entrega", order.destinationReference)
                add("Zona entrega", zone(order.destinationZoneId))
            }
            add("Pago compra", purchasePaymentPresentationLabel(order.purchasePayment))
        }
    }
    add("Pago delivery", deliveryPaymentPresentationLabel(order.deliveryPayment))
    add("Aclaraciones", order.notes)
    return lines
}

private fun serviceCategoryPresentationLabel(category: ServiceCategory): String = when (category) {
    ServiceCategory.PURCHASE -> "Compra"
    ServiceCategory.ERRAND -> "Encargo"
    ServiceCategory.PROCEDURE -> "Trámite"
    ServiceCategory.SHIPMENT -> "Envío"
}

private fun purchaseInstructionPresentationLabel(type: PurchaseInstructionType): String = when (type) {
    PurchaseInstructionType.IN_APP -> "Pedido escrito en la app"
    PurchaseInstructionType.PHYSICAL_NOTE -> "Retirar lista / nota / receta"
    PurchaseInstructionType.IN_PERSON -> "Recibir indicaciones personalmente"
}

private fun purchasePaymentPresentationLabel(method: PurchasePaymentMethod): String = when (method) {
    PurchasePaymentMethod.DIRECT_TO_STORE -> "Pago directo al comercio / ya abonado"
    PurchasePaymentMethod.TRANSFER_TO_RIDER -> "Transferencia previa al Repartidor"
    PurchasePaymentMethod.CASH_PRE_PICKUP -> "Efectivo previo al Repartidor"
}

private fun deliveryPaymentPresentationLabel(method: DeliveryPaymentMethod): String = when (method) {
    DeliveryPaymentMethod.CASH -> "Efectivo"
    DeliveryPaymentMethod.TRANSFER -> "Transferencia"
    DeliveryPaymentMethod.QR -> "QR"
    DeliveryPaymentMethod.ONLINE -> "Pago online"
}

private fun presentationMoney(value: Int): String = "$" + "%,d".format(value).replace(',', '.')
