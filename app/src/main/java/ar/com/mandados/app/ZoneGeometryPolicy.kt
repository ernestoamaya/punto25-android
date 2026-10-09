package ar.com.mandados.app

import kotlin.math.abs
import kotlin.math.hypot

private const val GEOMETRY_EPSILON = 1e-9
private const val INTERIOR_PROBE_OFFSET = 1e-7

internal enum class ZoneGeometryIssue {
    NOT_ENOUGH_VERTICES,
    INVALID_COORDINATE,
    REPEATED_VERTEX,
    ZERO_LENGTH_EDGE,
    ZERO_AREA,
    SELF_INTERSECTION,
    SAME_ZONE_OVERLAP,
    ACTIVE_ZONE_OVERLAP,
    ZONE_NOT_FOUND
}

internal data class ZoneGeometryValidationResult(
    val valid: Boolean,
    val issue: ZoneGeometryIssue? = null,
    val message: String? = null,
    val conflictingZoneId: String? = null
) {
    companion object {
        val VALID = ZoneGeometryValidationResult(valid = true)
    }
}

internal data class ZoneGeometryMutationResult(
    val success: Boolean,
    val message: String? = null,
    val conflictingZoneId: String? = null
) {
    companion object {
        val SUCCESS = ZoneGeometryMutationResult(success = true)
    }
}

internal data class ZoneMapCoordinate(
    val longitude: Double,
    val latitude: Double
)

internal fun validateZoneGeometry(polygons: List<List<GeoPoint>>): ZoneGeometryValidationResult {
    polygons.forEachIndexed { index, polygon ->
        validatePolygon(polygon)?.let { issue ->
            return ZoneGeometryValidationResult(
                valid = false,
                issue = issue,
                message = polygonIssueMessage(index, issue)
            )
        }
    }

    for (i in polygons.indices) {
        for (j in i + 1 until polygons.size) {
            if (polygonInteriorsOverlap(polygons[i], polygons[j])) {
                return ZoneGeometryValidationResult(
                    valid = false,
                    issue = ZoneGeometryIssue.SAME_ZONE_OVERLAP,
                    message = "Los polígonos ${i + 1} y ${j + 1} de la zona se superponen por su interior."
                )
            }
        }
    }
    return ZoneGeometryValidationResult.VALID
}

internal fun validateZoneGeometryForZone(
    candidate: ZoneConfig,
    allZones: List<ZoneConfig>
): ZoneGeometryValidationResult {
    validateZoneGeometry(candidate.polygons).takeIf { !it.valid }?.let { return it }
    if (!candidate.enabled) return ZoneGeometryValidationResult.VALID

    allZones.asSequence()
        .filter { it.id != candidate.id && it.enabled }
        .forEach { other ->
            val otherValidation = validateZoneGeometry(other.polygons)
            if (!otherValidation.valid) {
                return ZoneGeometryValidationResult(
                    valid = false,
                    issue = ZoneGeometryIssue.ACTIVE_ZONE_OVERLAP,
                    message = "La zona activa ${other.name} tiene geometría inválida y bloquea la operación.",
                    conflictingZoneId = other.id
                )
            }
            candidate.polygons.forEach { candidatePolygon ->
                other.polygons.forEach { otherPolygon ->
                    if (polygonInteriorsOverlap(candidatePolygon, otherPolygon)) {
                        return ZoneGeometryValidationResult(
                            valid = false,
                            issue = ZoneGeometryIssue.ACTIVE_ZONE_OVERLAP,
                            message = "La geometría se superpone con la zona activa ${other.name}.",
                            conflictingZoneId = other.id
                        )
                    }
                }
            }
        }
    return ZoneGeometryValidationResult.VALID
}

internal fun zoneGeometryRenderRing(polygon: List<GeoPoint>): List<ZoneMapCoordinate> {
    if (polygon.isEmpty()) return emptyList()
    return polygon.map { ZoneMapCoordinate(it.longitude, it.latitude) } +
        ZoneMapCoordinate(polygon.first().longitude, polygon.first().latitude)
}

internal fun zoneGeometryMultiLineGeoJson(polygons: List<List<GeoPoint>>): String {
    val coordinates = polygons.joinToString(",") { polygon ->
        zoneGeometryRenderRing(polygon).joinToString(",", prefix = "[", postfix = "]") { point ->
            "[${point.longitude},${point.latitude}]"
        }
    }
    return """{"type":"Feature","geometry":{"type":"MultiLineString","coordinates":[$coordinates]},"properties":{}}"""
}

internal fun zoneGeometryDrawingGeoJson(points: List<GeoPoint>): String {
    val coordinates = points.joinToString(",") { "[${it.longitude},${it.latitude}]" }
    return """{"type":"Feature","geometry":{"type":"LineString","coordinates":[$coordinates]},"properties":{}}"""
}

private fun validatePolygon(polygon: List<GeoPoint>): ZoneGeometryIssue? {
    if (polygon.size < 3 || polygon.distinct().size < 3) return ZoneGeometryIssue.NOT_ENOUGH_VERTICES
    if (polygon.any { !validCoordinate(it) }) return ZoneGeometryIssue.INVALID_COORDINATE
    if (polygon.toSet().size != polygon.size) return ZoneGeometryIssue.REPEATED_VERTEX

    for (i in polygon.indices) {
        val next = polygon[(i + 1) % polygon.size]
        if (samePoint(polygon[i], next)) return ZoneGeometryIssue.ZERO_LENGTH_EDGE
    }

    if (abs(signedArea(polygon)) <= GEOMETRY_EPSILON) return ZoneGeometryIssue.ZERO_AREA

    for (i in polygon.indices) {
        val a1 = polygon[i]
        val a2 = polygon[(i + 1) % polygon.size]
        for (j in i + 1 until polygon.size) {
            if (edgesAreAdjacent(i, j, polygon.size)) continue
            val b1 = polygon[j]
            val b2 = polygon[(j + 1) % polygon.size]
            if (segmentsIntersectOrTouch(a1, a2, b1, b2)) return ZoneGeometryIssue.SELF_INTERSECTION
        }
    }
    return null
}

private fun polygonIssueMessage(index: Int, issue: ZoneGeometryIssue): String {
    val number = index + 1
    return when (issue) {
        ZoneGeometryIssue.NOT_ENOUGH_VERTICES -> "El polígono $number necesita al menos 3 vértices distintos."
        ZoneGeometryIssue.INVALID_COORDINATE -> "El polígono $number contiene coordenadas inválidas o fuera de rango."
        ZoneGeometryIssue.REPEATED_VERTEX -> "El polígono $number contiene un vértice repetido que vuelve ambigua la topología."
        ZoneGeometryIssue.ZERO_LENGTH_EDGE -> "El polígono $number contiene una arista de longitud cero."
        ZoneGeometryIssue.ZERO_AREA -> "El polígono $number tiene área nula."
        ZoneGeometryIssue.SELF_INTERSECTION -> "El polígono $number se auto-intersecta."
        else -> "La geometría del polígono $number no es válida."
    }
}

private fun validCoordinate(point: GeoPoint): Boolean =
    point.latitude.isFinite() && point.longitude.isFinite() &&
        point.latitude in -90.0..90.0 && point.longitude in -180.0..180.0

private fun samePoint(a: GeoPoint, b: GeoPoint): Boolean =
    abs(a.latitude - b.latitude) <= GEOMETRY_EPSILON &&
        abs(a.longitude - b.longitude) <= GEOMETRY_EPSILON

private fun signedArea(polygon: List<GeoPoint>): Double {
    var sum = 0.0
    for (i in polygon.indices) {
        val a = polygon[i]
        val b = polygon[(i + 1) % polygon.size]
        sum += a.longitude * b.latitude - b.longitude * a.latitude
    }
    return sum / 2.0
}

private fun edgesAreAdjacent(first: Int, second: Int, size: Int): Boolean =
    first == second ||
        (first + 1) % size == second ||
        (second + 1) % size == first

private fun cross(a: GeoPoint, b: GeoPoint, c: GeoPoint): Double =
    (b.longitude - a.longitude) * (c.latitude - a.latitude) -
        (b.latitude - a.latitude) * (c.longitude - a.longitude)

private fun sign(value: Double): Int = when {
    value > GEOMETRY_EPSILON -> 1
    value < -GEOMETRY_EPSILON -> -1
    else -> 0
}

private fun pointOnSegment(point: GeoPoint, a: GeoPoint, b: GeoPoint): Boolean {
    if (abs(cross(a, b, point)) > GEOMETRY_EPSILON) return false
    return point.longitude >= minOf(a.longitude, b.longitude) - GEOMETRY_EPSILON &&
        point.longitude <= maxOf(a.longitude, b.longitude) + GEOMETRY_EPSILON &&
        point.latitude >= minOf(a.latitude, b.latitude) - GEOMETRY_EPSILON &&
        point.latitude <= maxOf(a.latitude, b.latitude) + GEOMETRY_EPSILON
}

private fun segmentsIntersectOrTouch(a1: GeoPoint, a2: GeoPoint, b1: GeoPoint, b2: GeoPoint): Boolean {
    val o1 = sign(cross(a1, a2, b1))
    val o2 = sign(cross(a1, a2, b2))
    val o3 = sign(cross(b1, b2, a1))
    val o4 = sign(cross(b1, b2, a2))
    if (o1 * o2 < 0 && o3 * o4 < 0) return true
    if (o1 == 0 && pointOnSegment(b1, a1, a2)) return true
    if (o2 == 0 && pointOnSegment(b2, a1, a2)) return true
    if (o3 == 0 && pointOnSegment(a1, b1, b2)) return true
    if (o4 == 0 && pointOnSegment(a2, b1, b2)) return true
    return false
}

private fun segmentsProperlyIntersect(a1: GeoPoint, a2: GeoPoint, b1: GeoPoint, b2: GeoPoint): Boolean {
    val o1 = sign(cross(a1, a2, b1))
    val o2 = sign(cross(a1, a2, b2))
    val o3 = sign(cross(b1, b2, a1))
    val o4 = sign(cross(b1, b2, a2))
    return o1 * o2 < 0 && o3 * o4 < 0
}

private enum class PointRelation { OUTSIDE, BOUNDARY, INSIDE }

private fun pointRelation(point: GeoPoint, polygon: List<GeoPoint>): PointRelation {
    for (i in polygon.indices) {
        if (pointOnSegment(point, polygon[i], polygon[(i + 1) % polygon.size])) return PointRelation.BOUNDARY
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
    return if (inside) PointRelation.INSIDE else PointRelation.OUTSIDE
}

private fun equivalentRing(a: List<GeoPoint>, b: List<GeoPoint>): Boolean {
    if (a.size != b.size || a.isEmpty()) return false
    for (start in b.indices) {
        if (!samePoint(a[0], b[start])) continue
        val forward = a.indices.all { offset -> samePoint(a[offset], b[(start + offset) % b.size]) }
        if (forward) return true
        val reverse = a.indices.all { offset ->
            val index = (start - offset).floorMod(b.size)
            samePoint(a[offset], b[index])
        }
        if (reverse) return true
    }
    return false
}

private fun Int.floorMod(divisor: Int): Int = ((this % divisor) + divisor) % divisor

private fun polygonInteriorsOverlap(a: List<GeoPoint>, b: List<GeoPoint>): Boolean {
    if (equivalentRing(a, b)) return true

    for (i in a.indices) {
        val a1 = a[i]
        val a2 = a[(i + 1) % a.size]
        for (j in b.indices) {
            val b1 = b[j]
            val b2 = b[(j + 1) % b.size]
            if (segmentsProperlyIntersect(a1, a2, b1, b2)) return true
        }
    }

    if (a.any { pointRelation(it, b) == PointRelation.INSIDE }) return true
    if (b.any { pointRelation(it, a) == PointRelation.INSIDE }) return true

    if (hasSharedInteriorProbe(a, b)) return true
    if (hasSharedInteriorProbe(b, a)) return true
    return false
}

private fun hasSharedInteriorProbe(source: List<GeoPoint>, other: List<GeoPoint>): Boolean {
    val orientation = if (signedArea(source) > 0) 1.0 else -1.0
    for (i in source.indices) {
        val a = source[i]
        val b = source[(i + 1) % source.size]
        val dx = b.longitude - a.longitude
        val dy = b.latitude - a.latitude
        val length = hypot(dx, dy)
        if (length <= GEOMETRY_EPSILON) continue
        val midpointLon = (a.longitude + b.longitude) / 2.0
        val midpointLat = (a.latitude + b.latitude) / 2.0
        val inwardLon = -dy / length * INTERIOR_PROBE_OFFSET * orientation
        val inwardLat = dx / length * INTERIOR_PROBE_OFFSET * orientation
        val probe = GeoPoint(midpointLat + inwardLat, midpointLon + inwardLon)
        if (pointRelation(probe, source) == PointRelation.INSIDE &&
            pointRelation(probe, other) == PointRelation.INSIDE
        ) return true
    }
    return false
}
