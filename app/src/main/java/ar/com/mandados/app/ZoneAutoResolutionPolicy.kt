package ar.com.mandados.app

import kotlin.math.abs

private const val AUTO_ZONE_EPSILON = 1e-9

internal enum class ZoneAutoResolutionStatus {
    RESOLVED,
    OUTSIDE,
    AMBIGUOUS,
    INVALID_CONFIG
}

internal data class ZoneAutoResolutionResult(
    val status: ZoneAutoResolutionStatus,
    val zoneId: String? = null,
    val candidateZoneIds: Set<String> = emptySet()
) {
    val effectiveZoneId: String
        get() = if (status == ZoneAutoResolutionStatus.RESOLVED && !zoneId.isNullOrBlank()) zoneId else UNKNOWN_ZONE_ID
}

internal data class ZoneAutoDraftResolution(
    val draft: OrderDraft,
    val results: Map<OrderZonePoint, ZoneAutoResolutionResult>
)

private enum class AutoPointRelation { OUTSIDE, BOUNDARY, INSIDE }

internal fun zoneAutoResolutionConfigIsValid(zones: List<ZoneConfig>): Boolean {
    val enabled = zones.filter { it.enabled }
    return enabled.all { zone -> validateZoneGeometryForZone(zone, zones).valid }
}

internal fun resolveZoneForPoint(
    point: GeoPoint?,
    zones: List<ZoneConfig>
): ZoneAutoResolutionResult {
    val enabled = zones.filter { it.enabled }
    if (!zoneAutoResolutionConfigIsValid(zones)) {
        return ZoneAutoResolutionResult(ZoneAutoResolutionStatus.INVALID_CONFIG)
    }
    if (point == null) return ZoneAutoResolutionResult(ZoneAutoResolutionStatus.OUTSIDE)
    if (!autoCoordinateIsValid(point)) {
        return ZoneAutoResolutionResult(ZoneAutoResolutionStatus.INVALID_CONFIG)
    }

    val candidates = linkedSetOf<String>()
    var boundary = false
    enabled.forEach { zone ->
        zone.polygons.forEach { polygon ->
            when (autoPointRelation(point, polygon)) {
                AutoPointRelation.INSIDE -> candidates += zone.id
                AutoPointRelation.BOUNDARY -> boundary = true
                AutoPointRelation.OUTSIDE -> Unit
            }
        }
    }

    return when {
        boundary -> ZoneAutoResolutionResult(
            status = ZoneAutoResolutionStatus.AMBIGUOUS,
            candidateZoneIds = candidates
        )
        candidates.size > 1 -> ZoneAutoResolutionResult(
            status = ZoneAutoResolutionStatus.AMBIGUOUS,
            candidateZoneIds = candidates
        )
        candidates.size == 1 -> ZoneAutoResolutionResult(
            status = ZoneAutoResolutionStatus.RESOLVED,
            zoneId = candidates.single(),
            candidateZoneIds = candidates
        )
        else -> ZoneAutoResolutionResult(ZoneAutoResolutionStatus.OUTSIDE)
    }
}

internal fun resolveDraftZonesAutomatically(
    draft: OrderDraft,
    config: AdminConfig
): ZoneAutoDraftResolution {
    if (!config.zoneAutoResolutionEnabled) {
        return ZoneAutoDraftResolution(draft = draft, results = emptyMap())
    }

    val applicable = applicableOrderZonePoints(draft)
    val results = applicable.associateWith { point ->
        resolveZoneForPoint(locationForZonePoint(draft, point), config.zones)
    }
    var effective = draft
    applicable.forEach { point ->
        effective = effective.withZoneId(point, results.getValue(point).effectiveZoneId)
    }
    return ZoneAutoDraftResolution(effective, results)
}

internal fun autoResolutionPresentation(
    result: ZoneAutoResolutionResult,
    config: AdminConfig,
    hasLocation: Boolean
): String = when {
    !hasLocation -> "Falta marcar ubicación"
    result.status == ZoneAutoResolutionStatus.RESOLVED -> {
        val name = config.zones.firstOrNull { it.id == result.zoneId }?.name
        if (name.isNullOrBlank()) "No se puede resolver automáticamente · tarifa a confirmar"
        else "Zona detectada: $name"
    }
    result.status == ZoneAutoResolutionStatus.OUTSIDE -> "Fuera de cobertura · tarifa a confirmar"
    result.status == ZoneAutoResolutionStatus.AMBIGUOUS -> "Ubicación en límite/ambigua · tarifa a confirmar"
    else -> "No se puede resolver automáticamente · tarifa a confirmar"
}

internal fun locationForZonePoint(draft: OrderDraft, point: OrderZonePoint): GeoPoint? = when (point) {
    OrderZonePoint.ORIGIN -> draft.originLocation
    OrderZonePoint.DESTINATION -> draft.destinationLocation
    OrderZonePoint.STORE -> draft.storeLocation
    OrderZonePoint.PRE_PICKUP -> draft.prePickupLocation
}

private fun OrderDraft.withZoneId(point: OrderZonePoint, zoneId: String): OrderDraft = when (point) {
    OrderZonePoint.ORIGIN -> copy(originZoneId = zoneId)
    OrderZonePoint.DESTINATION -> copy(destinationZoneId = zoneId)
    OrderZonePoint.STORE -> copy(storeZoneId = zoneId)
    OrderZonePoint.PRE_PICKUP -> copy(prePickupZoneId = zoneId)
}

private fun autoCoordinateIsValid(point: GeoPoint): Boolean =
    point.latitude.isFinite() && point.longitude.isFinite() &&
        point.latitude in -90.0..90.0 && point.longitude in -180.0..180.0

private fun autoSamePoint(a: GeoPoint, b: GeoPoint): Boolean =
    abs(a.latitude - b.latitude) <= AUTO_ZONE_EPSILON &&
        abs(a.longitude - b.longitude) <= AUTO_ZONE_EPSILON

private fun autoCross(a: GeoPoint, b: GeoPoint, c: GeoPoint): Double =
    (b.longitude - a.longitude) * (c.latitude - a.latitude) -
        (b.latitude - a.latitude) * (c.longitude - a.longitude)

private fun autoPointOnSegment(point: GeoPoint, a: GeoPoint, b: GeoPoint): Boolean {
    if (abs(autoCross(a, b, point)) > AUTO_ZONE_EPSILON) return false
    return point.longitude >= minOf(a.longitude, b.longitude) - AUTO_ZONE_EPSILON &&
        point.longitude <= maxOf(a.longitude, b.longitude) + AUTO_ZONE_EPSILON &&
        point.latitude >= minOf(a.latitude, b.latitude) - AUTO_ZONE_EPSILON &&
        point.latitude <= maxOf(a.latitude, b.latitude) + AUTO_ZONE_EPSILON
}

private fun autoPointRelation(point: GeoPoint, polygon: List<GeoPoint>): AutoPointRelation {
    if (polygon.size < 3) return AutoPointRelation.OUTSIDE
    for (i in polygon.indices) {
        if (autoPointOnSegment(point, polygon[i], polygon[(i + 1) % polygon.size])) {
            return AutoPointRelation.BOUNDARY
        }
    }
    var inside = false
    var j = polygon.lastIndex
    for (i in polygon.indices) {
        val pi = polygon[i]
        val pj = polygon[j]
        val intersects = (pi.latitude > point.latitude) != (pj.latitude > point.latitude) &&
            point.longitude < (pj.longitude - pi.longitude) *
            (point.latitude - pi.latitude) / (pj.latitude - pi.latitude) + pi.longitude
        if (intersects) inside = !inside
        j = i
    }
    return if (inside) AutoPointRelation.INSIDE else AutoPointRelation.OUTSIDE
}
