package ar.com.mandados.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoneGeometryPolicyTest {
    @Test
    fun `REG-ZONE-GEOMETRY-VALID-001 only valid simple polygons are accepted`() {
        assertTrue(validateZoneGeometry(listOf(square(0.0, 0.0, 2.0))).valid)

        assertFalse(validateZoneGeometry(listOf(listOf(
            GeoPoint(0.0, 0.0), GeoPoint(0.0, 1.0)
        ))).valid)
        assertFalse(validateZoneGeometry(listOf(listOf(
            GeoPoint(Double.NaN, 0.0), GeoPoint(0.0, 1.0), GeoPoint(1.0, 0.0)
        ))).valid)
        assertFalse(validateZoneGeometry(listOf(listOf(
            GeoPoint(91.0, 0.0), GeoPoint(0.0, 1.0), GeoPoint(1.0, 0.0)
        ))).valid)
        assertFalse(validateZoneGeometry(listOf(listOf(
            GeoPoint(0.0, 181.0), GeoPoint(0.0, 1.0), GeoPoint(1.0, 0.0)
        ))).valid)
        assertFalse(validateZoneGeometry(listOf(listOf(
            GeoPoint(0.0, 0.0), GeoPoint(1.0, 1.0), GeoPoint(2.0, 2.0)
        ))).valid)
        assertFalse(validateZoneGeometry(listOf(listOf(
            GeoPoint(0.0, 0.0), GeoPoint(2.0, 2.0), GeoPoint(0.0, 2.0), GeoPoint(2.0, 0.0)
        ))).valid)
        assertFalse(validateZoneGeometry(listOf(listOf(
            GeoPoint(0.0, 0.0), GeoPoint(0.0, 2.0), GeoPoint(2.0, 2.0), GeoPoint(0.0, 0.0)
        ))).valid)
    }

    @Test
    fun `REG-ZONE-GEOMETRY-MULTIPOLYGON-001 multiple polygons may share only boundary or vertex`() {
        val first = square(0.0, 0.0, 1.0)
        val sharedEdge = listOf(
            GeoPoint(0.0, 1.0), GeoPoint(0.0, 2.0), GeoPoint(1.0, 2.0), GeoPoint(1.0, 1.0)
        )
        val sharedVertex = square(1.0, 1.0, 1.0)

        assertTrue(validateZoneGeometry(listOf(first, sharedEdge)).valid)
        assertTrue(validateZoneGeometry(listOf(first, sharedVertex)).valid)

        val overlapping = square(0.5, 0.5, 1.0)
        val result = validateZoneGeometry(listOf(first, overlapping))
        assertFalse(result.valid)
        assertEquals(ZoneGeometryIssue.SAME_ZONE_OVERLAP, result.issue)
    }

    @Test
    fun `REG-ZONE-GEOMETRY-OVERLAP-001 active zones reject overlap containment and duplicate coverage`() {
        val a = ZoneConfig("a", "A", "", "X", enabled = true, polygons = listOf(square(0.0, 0.0, 4.0)))

        val overlapping = ZoneConfig("b", "B", "", "X", enabled = true, polygons = listOf(square(3.0, 3.0, 2.0)))
        assertConflict(overlapping, listOf(a, overlapping), "a")

        val contained = ZoneConfig("c", "C", "", "X", enabled = true, polygons = listOf(square(1.0, 1.0, 1.0)))
        assertConflict(contained, listOf(a, contained), "a")

        val duplicate = a.copy(id = "d", name = "D")
        assertConflict(duplicate, listOf(a, duplicate), "a")

        val boundaryOnly = ZoneConfig(
            "e", "E", "", "X", enabled = true,
            polygons = listOf(listOf(
                GeoPoint(0.0, 4.0), GeoPoint(0.0, 6.0), GeoPoint(4.0, 6.0), GeoPoint(4.0, 4.0)
            ))
        )
        assertTrue(validateZoneGeometryForZone(boundaryOnly, listOf(a, boundaryOnly)).valid)

        val disabledConflict = overlapping.copy(enabled = false)
        assertTrue(validateZoneGeometryForZone(disabledConflict, listOf(a, disabledConflict)).valid)
    }

    @Test
    fun `REG-ZONE-GEOMETRY-COORDINATES-001 model stays latitude longitude and rendering closes lon lat only`() {
        val stored = listOf(
            GeoPoint(latitude = -35.40, longitude = -60.10),
            GeoPoint(latitude = -35.41, longitude = -60.20),
            GeoPoint(latitude = -35.50, longitude = -60.15)
        )
        val ring = zoneGeometryRenderRing(stored)

        assertEquals(3, stored.size)
        assertEquals(4, ring.size)
        assertEquals(-60.10, ring.first().longitude, 0.0)
        assertEquals(-35.40, ring.first().latitude, 0.0)
        assertEquals(ring.first(), ring.last())
        assertEquals(GeoPoint(-35.40, -60.10), stored.first())

        val geoJson = zoneGeometryMultiLineGeoJson(listOf(stored))
        assertTrue(geoJson.contains("[-60.1,-35.4]"))
        assertFalse(geoJson.contains("[-35.4,-60.1]"))
    }

    private fun assertConflict(candidate: ZoneConfig, zones: List<ZoneConfig>, expected: String) {
        val result = validateZoneGeometryForZone(candidate, zones)
        assertFalse(result.valid)
        assertEquals(ZoneGeometryIssue.ACTIVE_ZONE_OVERLAP, result.issue)
        assertEquals(expected, result.conflictingZoneId)
    }

    private fun square(lat: Double, lon: Double, size: Double): List<GeoPoint> = listOf(
        GeoPoint(lat, lon),
        GeoPoint(lat, lon + size),
        GeoPoint(lat + size, lon + size),
        GeoPoint(lat + size, lon)
    )
}
