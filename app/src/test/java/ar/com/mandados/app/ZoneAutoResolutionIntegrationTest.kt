package ar.com.mandados.app

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ZoneAutoResolutionIntegrationTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("mandados_alpha1", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `REG-ZONE-AUTO-SWITCH-001 persists round trip rejects unsafe activation and does not mutate history`() {
        val store = LocalStore(context)
        assertFalse(store.loadConfig().zoneAutoResolutionEnabled)

        val valid = config(auto = true)
        store.saveConfig(valid)
        assertTrue(LocalStore(context).loadConfig().zoneAutoResolutionEnabled)

        val historical = historicalOrder("SWITCH-HISTORY")
        val payment = paymentFor(historical, PaymentStatus.CONFIRMED)
        store.saveOrders(listOf(historical))
        store.savePayments(listOf(payment))
        val controller = MandadosController(context)
        val beforeOrders = controller.orders
        val beforePayments = controller.payments

        assertTrue(controller.setZoneAutoResolutionEnabled(false))
        assertEquals(beforeOrders, controller.orders)
        assertEquals(beforePayments, controller.payments)
        assertTrue(controller.setZoneAutoResolutionEnabled(true))
        assertEquals(beforeOrders, controller.orders)
        assertEquals(beforePayments, controller.payments)

        val invalidZone = zone(
            "invalid",
            3000,
            listOf(
                GeoPoint(-35.50, -60.30),
                GeoPoint(-35.40, -60.20),
                GeoPoint(-35.50, -60.20),
                GeoPoint(-35.40, -60.30)
            )
        )
        assertTrue(controller.setZoneAutoResolutionEnabled(false))
        assertFalse(controller.updateConfig(controller.config.copy(zoneAutoResolutionEnabled = true, zones = listOf(invalidZone))))
        assertFalse(controller.config.zoneAutoResolutionEnabled)
        assertEquals(beforeOrders, controller.orders)
        assertEquals(beforePayments, controller.payments)
    }

    @Test
    fun `REG-ZONE-AUTO-CREATE-001 create recomputes persists detected zones coordinates addresses and safe payment`() {
        seedCustomerAndConfig(config(auto = true))
        val controller = MandadosController(context)
        val originalDraft = OrderDraft(
            serviceType = ServiceType.DELIVERY,
            category = ServiceCategory.ERRAND,
            originAddress = "Retiro escrito",
            originZoneId = "stale-manual-origin",
            originLocation = GeoPoint(-35.45, -60.20),
            destinationAddress = "Entrega escrita",
            destinationZoneId = "stale-manual-destination",
            destinationLocation = GeoPoint(-35.45, -60.05),
            deliveryPayment = DeliveryPaymentMethod.CASH
        )
        controller.draft = originalDraft

        val created = controller.createOrder() as OrderCreationResult.Created
        val order = created.order
        assertEquals("a", order.originZoneId)
        assertEquals("b", order.destinationZoneId)
        assertEquals(originalDraft.originLocation, order.originLocation)
        assertEquals(originalDraft.destinationLocation, order.destinationLocation)
        assertEquals("Retiro escrito", order.originAddress)
        assertEquals("Entrega escrita", order.destinationAddress)
        assertEquals(2000, order.totalAmount)
        assertEquals(OrderStatus.PENDING, order.status)
        assertEquals(originalDraft, controller.draft)
        assertEquals(2000, controller.paymentForOrder(order.id)?.expectedAmount)

        val restarted = MandadosController(context)
        assertEquals(order, restarted.order(order.id))
        assertEquals(2000, restarted.paymentForOrder(order.id)?.expectedAmount)
    }

    @Test
    fun `REG-ZONE-AUTO-CREATE-001 unresolved create persists unknown awaits quote and payment amount zero`() {
        seedCustomerAndConfig(config(auto = true))
        val controller = MandadosController(context)
        controller.draft = OrderDraft(
            serviceType = ServiceType.DELIVERY,
            category = ServiceCategory.ERRAND,
            originAddress = "Retiro",
            originZoneId = "stale-a",
            originLocation = GeoPoint(-35.45, -60.20),
            destinationAddress = "Sólo dirección descriptiva",
            destinationZoneId = "stale-b",
            destinationLocation = null,
            deliveryPayment = DeliveryPaymentMethod.TRANSFER
        )

        val order = (controller.createOrder() as OrderCreationResult.Created).order
        assertEquals("a", order.originZoneId)
        assertEquals(UNKNOWN_ZONE_ID, order.destinationZoneId)
        assertEquals(OrderStatus.AWAITING_QUOTE, order.status)
        assertNull(order.totalAmount)
        assertEquals(0, controller.paymentForOrder(order.id)?.expectedAmount)
        assertEquals(PaymentChannel.RIDER_TRANSFER, controller.paymentForOrder(order.id)?.channel)
    }

    @Test
    fun `REG-ZONE-AUTO-HISTORY-001 switch and geometry changes never auto-reprice existing order or payment`() {
        val store = LocalStore(context)
        val initialConfig = config(auto = false)
        store.saveConfig(initialConfig)
        val historical = historicalOrder(
            id = "HISTORY",
            originZone = "a",
            destinationZone = "a",
            originLocation = GeoPoint(-35.45, -60.05),
            destinationLocation = GeoPoint(-35.45, -60.05)
        )
        val committed = paymentFor(historical, PaymentStatus.CONFIRMED)
        store.saveOrders(listOf(historical))
        store.savePayments(listOf(committed))
        val controller = MandadosController(context)

        assertTrue(controller.setZoneAutoResolutionEnabled(true))
        assertEquals(historical, controller.order(historical.id))
        assertEquals(committed, controller.paymentForOrder(historical.id))

        val shiftedGeometry = controller.config.zones.map { zone ->
            when (zone.id) {
                "a" -> zone.copy(polygons = listOf(rectangle(-35.60, -60.40, -35.50, -60.30)))
                "b" -> zone.copy(polygons = listOf(rectangle(-35.50, -60.10, -35.40, -60.00)))
                else -> zone
            }
        }
        assertTrue(controller.updateConfig(controller.config.copy(zones = shiftedGeometry)))
        assertEquals(historical, controller.order(historical.id))
        assertEquals(committed, controller.paymentForOrder(historical.id))

        val edited = controller.draftFromOrder(historical).copy(notes = "Sólo texto histórico")
        assertTrue(controller.editOrder(historical.id, edited, "Corrección descriptiva"))
        val afterEdit = controller.order(historical.id)!!
        assertEquals("a", afterEdit.originZoneId)
        assertEquals("a", afterEdit.destinationZoneId)
        assertEquals(1000, afterEdit.totalAmount)
        assertEquals(committed, controller.paymentForOrder(historical.id))
    }

    @Test
    fun `REG-ZONE-AUTO-OVERRIDE-001 admin override preserves detected zone audit and payment integrity`() {
        seedCustomerAndConfig(config(auto = true))
        val controller = MandadosController(context)
        controller.draft = OrderDraft(
            serviceType = ServiceType.DELIVERY,
            originAddress = "Origen",
            destinationAddress = "Destino",
            originLocation = GeoPoint(-35.45, -60.20),
            destinationLocation = GeoPoint(-35.45, -60.20),
            deliveryPayment = DeliveryPaymentMethod.CASH
        )
        val created = (controller.createOrder() as OrderCreationResult.Created).order
        assertEquals("a", created.destinationZoneId)
        assertEquals(1000, created.totalAmount)
        val eventCount = created.events.size

        assertTrue(
            controller.applyOrderZoneOverride(
                created.id,
                OrderZonePoint.DESTINATION,
                OrderZoneOverrideSelection.Catalog("b"),
                "Corrección administrativa explícita"
            )
        )
        val overridden = controller.order(created.id)!!
        assertEquals("a", overridden.destinationZoneId)
        assertEquals(OrderZoneOverrideSource.CATALOG, overridden.zoneOverrides[OrderZonePoint.DESTINATION]?.source)
        assertEquals("b", overridden.zoneOverrides[OrderZonePoint.DESTINATION]?.catalogZoneId)
        assertEquals(2000, overridden.totalAmount)
        assertEquals(2000, controller.paymentForOrder(created.id)?.expectedAmount)
        assertEquals(eventCount + 1, overridden.events.size)
        assertEquals(OrderEventType.ORDER_EDITED, overridden.events.last().type)
        assertEquals("ADMIN", overridden.events.last().actor)
        assertTrue(overridden.events.last().note.orEmpty().contains("Corrección administrativa explícita"))
    }

    private fun seedCustomerAndConfig(config: AdminConfig) {
        val store = LocalStore(context)
        store.saveConfig(config)
        store.saveCustomer(
            Customer(
                name = "Cliente Test",
                areaCode = "2345",
                subscriber = "123456",
                locality = "25 de Mayo",
                accountId = "DEV-TEST"
            )
        )
    }

    private fun config(auto: Boolean): AdminConfig = AdminConfig(
        operationMode = OperationMode.MULTI_RIDER,
        zoneAutoResolutionEnabled = auto,
        zones = listOf(
            zone("a", 1000, rectangle(-35.50, -60.30, -35.40, -60.10)),
            zone("b", 2000, rectangle(-35.50, -60.10, -35.40, -60.00))
        )
    )

    private fun historicalOrder(
        id: String,
        originZone: String = "a",
        destinationZone: String = "a",
        originLocation: GeoPoint = GeoPoint(-35.45, -60.20),
        destinationLocation: GeoPoint = GeoPoint(-35.45, -60.20)
    ): LocalOrder = LocalOrder(
        id = id,
        createdAt = "09/10/2026 08:00:00",
        serviceType = ServiceType.DELIVERY,
        category = ServiceCategory.ERRAND,
        operationMode = OperationMode.MULTI_RIDER,
        status = OrderStatus.PENDING,
        customerName = "Cliente",
        customerPhone = "2345-123456",
        detail = "snapshot histórico",
        baseAmount = 1000,
        baseZoneName = "Zona A",
        prePickupAmount = 0,
        rainAmount = 0,
        totalAmount = 1000,
        whatsappMessage = "snapshot",
        originLocation = originLocation,
        destinationLocation = destinationLocation,
        customerId = "DEV-TEST",
        originAddress = "Origen histórico",
        originZoneId = originZone,
        destinationAddress = "Destino histórico",
        destinationZoneId = destinationZone,
        deliveryPayment = DeliveryPaymentMethod.CASH,
        events = listOf(OrderEvent(OrderEventType.CREATED, "09/10/2026 08:00:00", OrderStatus.PENDING, actor = "CLIENTE"))
    )

    private fun paymentFor(order: LocalOrder, status: PaymentStatus): PaymentRecord = PaymentRecord(
        id = "PAY-${order.id}",
        orderId = order.id,
        channel = if (order.deliveryPayment == DeliveryPaymentMethod.TRANSFER) PaymentChannel.RIDER_TRANSFER else PaymentChannel.CASH,
        expectedAmount = order.totalAmount ?: 0,
        status = status,
        createdAt = order.createdAt,
        updatedAt = order.createdAt
    )

    private fun zone(id: String, price: Int, polygon: List<GeoPoint>): ZoneConfig = ZoneConfig(
        id = id,
        name = "Zona ${id.uppercase()}",
        description = "",
        category = "TEST",
        price = price,
        enabled = true,
        polygons = listOf(polygon)
    )

    private fun rectangle(minLat: Double, minLon: Double, maxLat: Double, maxLon: Double): List<GeoPoint> = listOf(
        GeoPoint(minLat, minLon),
        GeoPoint(minLat, maxLon),
        GeoPoint(maxLat, maxLon),
        GeoPoint(maxLat, minLon)
    )
}