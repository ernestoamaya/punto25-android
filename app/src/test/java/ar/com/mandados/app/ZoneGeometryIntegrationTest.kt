package ar.com.mandados.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ZoneGeometryIntegrationTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        prefs().edit().clear().commit()
    }

    @Test
    fun `REG-ZONE-GEOMETRY-OVERLAP-001 rejected active overlap changes neither memory nor persistence`() {
        val first = zone("a", enabled = true, polygons = listOf(square(0.0, 0.0, 2.0)))
        val second = zone("b", enabled = true, polygons = listOf(square(4.0, 4.0, 1.0)))
        val store = LocalStore(context)
        store.saveConfig(AdminConfig(zones = listOf(first, second)))
        val c = MandadosController(context)
        val before = c.config

        val result = c.updateZoneGeometry("b", listOf(square(1.0, 1.0, 2.0)))

        assertFalse(result.success)
        assertEquals("a", result.conflictingZoneId)
        assertEquals(before, c.config)
        assertEquals(before, LocalStore(context).loadConfig())
    }

    @Test
    fun `REG-ZONE-GEOMETRY-ACTIVATION-001 disabled conflicting geometry persists but activation fails closed`() {
        val active = zone("a", enabled = true, polygons = listOf(square(0.0, 0.0, 3.0)))
        val disabled = zone("b", enabled = false, polygons = listOf(square(1.0, 1.0, 1.0)))
        val store = LocalStore(context)
        val config = AdminConfig(zones = listOf(active, disabled))
        store.saveConfig(config)
        val c = MandadosController(context)

        assertEquals(disabled.polygons, c.zone("b")!!.polygons)
        assertFalse(
            c.updateZone(
                id = "b",
                name = "B habilitada",
                description = "no debe persistir",
                category = "NUEVA",
                price = 9999,
                enabled = true
            )
        )
        assertEquals(config, c.config)
        assertEquals(config, LocalStore(context).loadConfig())
    }

    @Test
    fun `REG-ZONE-GEOMETRY-PERSIST-001 round trip legacy default and malformed geometry are safe`() {
        val polygon = listOf(square(-35.44, -60.18, 0.01))
        val expected = zone("a", enabled = true, polygons = polygon)
        val store = LocalStore(context)
        store.saveConfig(AdminConfig(zones = listOf(expected)))
        assertEquals(polygon, LocalStore(context).loadConfig().zones.single().polygons)

        prefs().edit().clear().commit()
        prefs().edit().putString(
            "zones_json",
            JSONArray().put(JSONObject().apply {
                put("id", "legacy")
                put("name", "Legacy")
                put("description", "")
                put("category", "X")
                put("price", 123)
                put("enabled", true)
            }).toString()
        ).commit()
        assertEquals(emptyList<List<GeoPoint>>(), LocalStore(context).loadConfig().zones.single().polygons)

        prefs().edit().clear().commit()
        val malformed = JSONArray().put(JSONObject().apply {
            put("id", "bad")
            put("name", "Bad")
            put("description", "")
            put("category", "X")
            put("price", 100)
            put("enabled", true)
            put("polygons", JSONArray().put(JSONArray().apply {
                put(JSONObject().put("latitude", 0.0).put("longitude", 0.0))
                put(JSONObject().put("latitude", 1.0).put("longitude", 1.0))
            }))
        })
        prefs().edit().putString("zones_json", malformed.toString()).commit()
        val loaded = LocalStore(context).loadConfig().zones.single()
        assertEquals("bad", loaded.id)
        assertEquals(100, loaded.price)
        assertEquals(emptyList<List<GeoPoint>>(), loaded.polygons)
    }

    @Test
    fun `REG-ZONE-GEOMETRY-ISOLATION-001 geometry save changes only polygons`() {
        val originalZone = zone("a", enabled = true, polygons = emptyList()).copy(
            name = "Nombre",
            description = "Descripción",
            category = "Categoría",
            price = 4321
        )
        val order = testOrder()
        val payment = PaymentRecord(
            id = "PAY-1",
            orderId = order.id,
            channel = PaymentChannel.CASH,
            expectedAmount = 1000,
            createdAt = "01/01/2026 10:00:00",
            updatedAt = "01/01/2026 10:00:00"
        )
        val store = LocalStore(context)
        store.saveConfig(AdminConfig(zones = listOf(originalZone)))
        store.saveOrders(listOf(order))
        store.savePayments(listOf(payment))
        val c = MandadosController(context)
        c.draft = OrderDraft(originZoneId = "a", destinationZoneId = "a", notes = "draft")
        val configBefore = c.config
        val ordersBefore = c.orders
        val paymentsBefore = c.payments
        val ratingsBefore = c.ratings
        val draftBefore = c.draft

        assertTrue(c.updateZoneGeometry("a", listOf(square(-35.44, -60.18, 0.01))).success)

        val after = c.zone("a")!!
        assertEquals(configBefore.zones.single().copy(polygons = after.polygons), after)
        assertEquals(ordersBefore, c.orders)
        assertEquals(paymentsBefore, c.payments)
        assertEquals(ratingsBefore, c.ratings)
        assertEquals(draftBefore, c.draft)
        assertEquals(ordersBefore, LocalStore(context).loadOrders())
        assertEquals(paymentsBefore, LocalStore(context).loadPayments())
    }

    @Test
    fun `REG-ZONE-GEOMETRY-NOAUTO-001 geometry never assigns draft zones or invokes geographic pricing`() {
        val store = LocalStore(context)
        store.saveConfig(AdminConfig(zones = listOf(zone("a", enabled = true, polygons = emptyList()).copy(price = 5000))))
        val c = MandadosController(context)
        c.draft = OrderDraft(
            originLocation = GeoPoint(-35.44, -60.18),
            destinationLocation = GeoPoint(-35.445, -60.185),
            originZoneId = "",
            destinationZoneId = "",
            notes = "sin resolución geográfica"
        )
        val draftBefore = c.draft
        val pricingBefore = c.pricing(draftBefore)

        assertTrue(c.updateZoneGeometry("a", listOf(square(-35.45, -60.19, 0.03))).success)

        assertEquals(draftBefore, c.draft)
        assertEquals("", c.draft.originZoneId)
        assertEquals("", c.draft.destinationZoneId)
        assertEquals(pricingBefore, c.pricing(c.draft))
        assertTrue(c.orders.isEmpty())
        assertTrue(c.payments.isEmpty())
    }

    private fun prefs() = context.getSharedPreferences("mandados_alpha1", Context.MODE_PRIVATE)

    private fun zone(id: String, enabled: Boolean, polygons: List<List<GeoPoint>>) = ZoneConfig(
        id = id,
        name = id.uppercase(),
        description = "desc-$id",
        category = "TEST",
        price = 1000,
        enabled = enabled,
        polygons = polygons
    )

    private fun square(lat: Double, lon: Double, size: Double): List<GeoPoint> = listOf(
        GeoPoint(lat, lon), GeoPoint(lat, lon + size),
        GeoPoint(lat + size, lon + size), GeoPoint(lat + size, lon)
    )

    private fun testOrder() = LocalOrder(
        id = "P25-TEST",
        createdAt = "01/01/2026 10:00:00",
        serviceType = ServiceType.DELIVERY,
        status = OrderStatus.PENDING,
        customerName = "Cliente",
        customerPhone = "2345-123456",
        detail = "detalle",
        baseAmount = 1000,
        baseZoneName = "A",
        prePickupAmount = 0,
        rainAmount = 0,
        totalAmount = 1000,
        whatsappMessage = "",
        originZoneId = "a",
        destinationZoneId = "a",
        zoneOverrides = mapOf(
            OrderZonePoint.ORIGIN to OrderZoneOverride(OrderZoneOverrideSource.AD_HOC, name = "Snapshot", price = 1000)
        ),
        events = listOf(OrderEvent(OrderEventType.CREATED, "01/01/2026 10:00:00", OrderStatus.PENDING))
    )
}
