package ar.com.mandados.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoneAutoResolutionPolicyTest {
    @Test
    fun `REG-ZONE-AUTO-SWITCH-001 default off preserves manual draft exactly`() {
        val config = AdminConfig(zones = testZones())
        val manual = OrderDraft(
            serviceType = ServiceType.DELIVERY,
            originZoneId = "manual-origin",
            destinationZoneId = "manual-destination",
            originLocation = GeoPoint(-35.45, -60.15),
            destinationLocation = GeoPoint(-35.45, -60.05)
        )

        assertFalse(config.zoneAutoResolutionEnabled)
        val result = resolveDraftZonesAutomatically(manual, config)
        assertEquals(manual, result.draft)
        assertTrue(result.results.isEmpty())
    }

    @Test
    fun `REG-ZONE-AUTO-RESOLVE-001 interior active zone and multipolygon resolve same zone`() {
        val multi = ZoneConfig(
            id = "multi",
            name = "Multi",
            description = "",
            category = "TEST",
            price = 1700,
            enabled = true,
            polygons = listOf(
                rectangle(-35.50, -60.30, -35.40, -60.20),
                rectangle(-35.50, -60.10, -35.40, -60.00)
            )
        )

        val first = resolveZoneForPoint(GeoPoint(-35.45, -60.25), listOf(multi))
        val second = resolveZoneForPoint(GeoPoint(-35.45, -60.05), listOf(multi))

        listOf(first, second).forEach { result ->
            assertEquals(ZoneAutoResolutionStatus.RESOLVED, result.status)
            assertEquals("multi", result.zoneId)
            assertEquals(setOf("multi"), result.candidateZoneIds)
            assertEquals("multi", result.effectiveZoneId)
        }
    }

    @Test
    fun `REG-ZONE-AUTO-FAILCLOSED-001 outside boundary and invalid conflict never choose zone`() {
        val zones = testZones()
        val outside = resolveZoneForPoint(GeoPoint(-35.70, -60.70), zones)
        assertEquals(ZoneAutoResolutionStatus.OUTSIDE, outside.status)
        assertEquals(UNKNOWN_ZONE_ID, outside.effectiveZoneId)
        assertNull(outside.zoneId)

        val sharedBoundary = resolveZoneForPoint(GeoPoint(-35.45, -60.10), zones)
        assertEquals(ZoneAutoResolutionStatus.AMBIGUOUS, sharedBoundary.status)
        assertEquals(UNKNOWN_ZONE_ID, sharedBoundary.effectiveZoneId)
        assertNull(sharedBoundary.zoneId)

        val overlapping = listOf(
            zone("first", 1000, rectangle(-35.50, -60.30, -35.40, -60.10)),
            zone("second", 2000, rectangle(-35.48, -60.25, -35.38, -60.05))
        )
        assertFalse(zoneAutoResolutionConfigIsValid(overlapping))
        val invalid = resolveZoneForPoint(GeoPoint(-35.45, -60.20), overlapping)
        assertEquals(ZoneAutoResolutionStatus.INVALID_CONFIG, invalid.status)
        assertEquals(UNKNOWN_ZONE_ID, invalid.effectiveZoneId)
        assertNull(invalid.zoneId)

        val reversed = resolveZoneForPoint(GeoPoint(-35.45, -60.20), overlapping.reversed())
        assertEquals(ZoneAutoResolutionStatus.INVALID_CONFIG, reversed.status)
        assertNull(reversed.zoneId)
    }

    @Test
    fun `REG-ZONE-AUTO-INACTIVE-001 disabled zones never participate`() {
        val disabled = zone(
            id = "disabled",
            price = 9000,
            polygon = rectangle(-35.50, -60.30, -35.40, -60.20),
            enabled = false
        )
        val activeElsewhere = zone(
            id = "active",
            price = 1000,
            polygon = rectangle(-35.50, -60.10, -35.40, -60.00)
        )

        val result = resolveZoneForPoint(GeoPoint(-35.45, -60.25), listOf(disabled, activeElsewhere))
        assertEquals(ZoneAutoResolutionStatus.OUTSIDE, result.status)
        assertEquals(UNKNOWN_ZONE_ID, result.effectiveZoneId)
    }

    @Test
    fun `REG-ZONE-AUTO-DRAFT-001 delivery shopping missing and same-delivery resolve applicable points only`() {
        val config = AdminConfig(zoneAutoResolutionEnabled = true, zones = testZones())
        val delivery = OrderDraft(
            serviceType = ServiceType.DELIVERY,
            originZoneId = "stale",
            destinationZoneId = "stale",
            originLocation = GeoPoint(-35.45, -60.20),
            destinationLocation = GeoPoint(-35.45, -60.05)
        )
        val resolvedDelivery = resolveDraftZonesAutomatically(delivery, config)
        assertEquals("a", resolvedDelivery.draft.originZoneId)
        assertEquals("b", resolvedDelivery.draft.destinationZoneId)
        assertEquals(setOf(OrderZonePoint.ORIGIN, OrderZonePoint.DESTINATION), resolvedDelivery.results.keys)

        val shared = GeoPoint(-35.45, -60.05)
        val shopping = OrderDraft(
            serviceType = ServiceType.SHOPPING,
            category = ServiceCategory.PURCHASE,
            instructionType = PurchaseInstructionType.PHYSICAL_NOTE,
            purchasePayment = PurchasePaymentMethod.CASH_PRE_PICKUP,
            storeZoneId = "stale-store",
            destinationZoneId = "stale-destination",
            prePickupZoneId = "stale-pre",
            storeLocation = GeoPoint(-35.45, -60.20),
            destinationLocation = shared,
            prePickupLocation = shared,
            sameDeliveryAsPrePickup = true
        )
        val resolvedShopping = resolveDraftZonesAutomatically(shopping, config)
        assertEquals("a", resolvedShopping.draft.storeZoneId)
        assertEquals("b", resolvedShopping.draft.destinationZoneId)
        assertEquals("b", resolvedShopping.draft.prePickupZoneId)
        assertEquals(
            resolvedShopping.results[OrderZonePoint.DESTINATION],
            resolvedShopping.results[OrderZonePoint.PRE_PICKUP]
        )

        val missing = resolveDraftZonesAutomatically(shopping.copy(storeLocation = null), config)
        assertEquals(UNKNOWN_ZONE_ID, missing.draft.storeZoneId)
        assertEquals(ZoneAutoResolutionStatus.OUTSIDE, missing.results[OrderZonePoint.STORE]?.status)

        val noPrePickup = resolveDraftZonesAutomatically(
            shopping.copy(
                instructionType = PurchaseInstructionType.IN_APP,
                purchasePayment = PurchasePaymentMethod.DIRECT_TO_STORE,
                prePickupLocation = GeoPoint(-35.45, -60.20),
                prePickupZoneId = "manual-pre"
            ),
            config
        )
        assertFalse(noPrePickup.results.containsKey(OrderZonePoint.PRE_PICKUP))
        assertEquals("manual-pre", noPrePickup.draft.prePickupZoneId)
    }

    @Test
    fun `REG-ZONE-AUTO-COORDINATES-001 asymmetric fixtures detect latitude longitude swap`() {
        val zone = zone(
            id = "asymmetric",
            price = 1000,
            polygon = rectangle(-35.50, -60.30, -35.40, -60.20)
        )
        val correct = geoPointFromMapCoordinates(latitude = -35.45, longitude = -60.25)
        assertEquals(-35.45, correct.latitude, 0.0)
        assertEquals(-60.25, correct.longitude, 0.0)
        assertEquals(ZoneAutoResolutionStatus.RESOLVED, resolveZoneForPoint(correct, listOf(zone)).status)

        val swapped = GeoPoint(latitude = -60.25, longitude = -35.45)
        assertEquals(ZoneAutoResolutionStatus.OUTSIDE, resolveZoneForPoint(swapped, listOf(zone)).status)
    }

    @Test
    fun `REG-ZONE-AUTO-PRICING-001 existing pricing engine keeps max zone prepickup rain and quote semantics`() {
        val config = AdminConfig(
            zoneAutoResolutionEnabled = true,
            rainEnabled = true,
            rainAmount = 150,
            prePickupMode = PrePickupMode.PERCENT_BASE,
            prePickupValue = 10,
            zones = testZones()
        )
        val draft = OrderDraft(
            serviceType = ServiceType.SHOPPING,
            category = ServiceCategory.PURCHASE,
            instructionType = PurchaseInstructionType.PHYSICAL_NOTE,
            purchasePayment = PurchasePaymentMethod.CASH_PRE_PICKUP,
            storeLocation = GeoPoint(-35.45, -60.20),
            destinationLocation = GeoPoint(-35.45, -60.05),
            prePickupLocation = GeoPoint(-35.45, -60.20)
        )
        val effective = resolveDraftZonesAutomatically(draft, config).draft
        val priced = calculateOrderPricing(effective, emptyMap(), config)

        assertFalse(priced.needsQuote)
        assertEquals(2000, priced.baseAmount)
        assertEquals(200, priced.prePickupAmount)
        assertEquals(150, priced.rainAmount)
        assertEquals(2350, priced.totalAmount)

        val unresolved = resolveDraftZonesAutomatically(draft.copy(destinationLocation = null), config).draft
        val quote = calculateOrderPricing(unresolved, emptyMap(), config)
        assertTrue(quote.needsQuote)
        assertNull(quote.totalAmount)
    }

    private fun testZones(): List<ZoneConfig> = listOf(
        zone("a", 1000, rectangle(-35.50, -60.30, -35.40, -60.10)),
        zone("b", 2000, rectangle(-35.50, -60.10, -35.40, 0.0 - 60.00))
    )

    private fun zone(
        id: String,
        price: Int,
        polygon: List<GeoPoint>,
        enabled: Boolean = true
    ): ZoneConfig = ZoneConfig(
        id = id,
        name = "Zona ${id.uppercase()}",
        description = "",
        category = "TEST",
        price = price,
        enabled = enabled,
        polygons = listOf(polygon)
    )

    private fun rectangle(minLat: Double, minLon: Double, maxLat: Double, maxLon: Double): List<GeoPoint> = listOf(
        GeoPoint(minLat, minLon),
        GeoPoint(minLat, maxLon),
        GeoPoint(maxLat, maxLon),
        GeoPoint(maxLat, minLon)
    )
}