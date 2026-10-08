package ar.com.mandados.app

import android.content.Context
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
class OrderZoneOverrideCommittedPaymentTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        clearMainStore()
    }

    @Test
    fun `REG-ZONE-OVERRIDE-PAYMENT-001 confirmed payment blocks equal tariff catalog and ad hoc without any mutation`() {
        val cfg = testConfig()
        val original = orderFromDraft("PAY-COMMITTED-EQUAL", deliveryDraft("a", "a"), cfg)
        val committed = payment(original, status = PaymentStatus.CONFIRMED)
        seed(cfg, original, committed)
        val c = MandadosController(context)

        val beforeOrder = c.order(original.id)!!
        val beforePayment = c.paymentForOrder(original.id)!!
        val beforeOrders = c.orders
        val beforePayments = c.payments
        val beforeEvents = beforeOrder.events

        val equalCatalogPreview = c.previewOrderZoneOverride(
            original.id,
            OrderZonePoint.DESTINATION,
            OrderZoneOverrideSelection.Catalog("same")
        )!!
        assertFalse(equalCatalogPreview.allowed)

        assertFalse(
            c.applyOrderZoneOverride(
                original.id,
                OrderZonePoint.DESTINATION,
                OrderZoneOverrideSelection.Catalog("same"),
                "Zona distinta con igual tarifa"
            )
        )
        assertFalse(
            c.applyOrderZoneOverride(
                original.id,
                OrderZonePoint.DESTINATION,
                OrderZoneOverrideSelection.AdHoc("Zona circunstancial equivalente", 1000),
                "AD_HOC distinto con igual tarifa"
            )
        )

        assertEquals(beforeOrder, c.order(original.id))
        assertEquals(beforePayment, c.paymentForOrder(original.id))
        assertEquals(beforeOrders, c.orders)
        assertEquals(beforePayments, c.payments)
        assertEquals(beforeEvents, c.order(original.id)!!.events)
        assertTrue(c.order(original.id)!!.zoneOverrides.isEmpty())
        assertEquals(beforeOrders, LocalStore(context).loadOrders())
        assertEquals(beforePayments, LocalStore(context).loadPayments())

        val restarted = MandadosController(context)
        assertEquals(beforeOrder, restarted.order(original.id))
        assertEquals(beforePayment, restarted.paymentForOrder(original.id))
        assertEquals(beforeEvents, restarted.order(original.id)!!.events)
        assertTrue(restarted.order(original.id)!!.zoneOverrides.isEmpty())
    }

    @Test
    fun `REG-ZONE-OVERRIDE-PAYMENT-001 dirty pending and committed restore or replacement fail closed`() {
        val cfg = testConfig()
        val original = orderFromDraft("PAY-DIRTY-EQUAL", deliveryDraft("a", "a"), cfg)

        listOf(
            payment(original, proofUri = "content://proof"),
            payment(original, providerPaymentId = "provider-1")
        ).forEach { dirtyPayment ->
            assertFalse(
                evaluateOrderZoneOverride(
                    currentOrder = original,
                    point = OrderZonePoint.DESTINATION,
                    selection = OrderZoneOverrideSelection.Catalog("same"),
                    config = cfg,
                    paymentMatches = listOf(dirtyPayment)
                ).allowed
            )
            assertFalse(
                evaluateOrderZoneOverride(
                    currentOrder = original,
                    point = OrderZonePoint.DESTINATION,
                    selection = OrderZoneOverrideSelection.AdHoc("Igual tarifa", 1000),
                    config = cfg,
                    paymentMatches = listOf(dirtyPayment)
                ).allowed
            )
        }

        clearMainStore()
        val overridden = orderFromDraft("PAY-COMMITTED-RESTORE", deliveryDraft("a", "a"), cfg).copy(
            zoneOverrides = mapOf(
                OrderZonePoint.DESTINATION to OrderZoneOverride(
                    source = OrderZoneOverrideSource.AD_HOC,
                    name = "Aplicada equivalente",
                    price = 1000
                )
            )
        )
        val committed = payment(overridden, status = PaymentStatus.CONFIRMED)
        seed(cfg, overridden, committed)
        val c = MandadosController(context)
        val beforeOrder = c.order(overridden.id)!!
        val beforePayment = c.paymentForOrder(overridden.id)!!
        val beforeEvents = beforeOrder.events
        val beforeOverride = beforeOrder.zoneOverrides[OrderZonePoint.DESTINATION]

        assertFalse(
            c.applyOrderZoneOverride(
                overridden.id,
                OrderZonePoint.DESTINATION,
                OrderZoneOverrideSelection.Declared,
                "Restauración bloqueada por pago comprometido"
            )
        )
        assertFalse(
            c.applyOrderZoneOverride(
                overridden.id,
                OrderZonePoint.DESTINATION,
                OrderZoneOverrideSelection.Catalog("same"),
                "Cambio bloqueado por pago comprometido"
            )
        )

        assertEquals(beforeOrder, c.order(overridden.id))
        assertEquals(beforePayment, c.paymentForOrder(overridden.id))
        assertEquals(beforeEvents, c.order(overridden.id)!!.events)
        assertEquals(beforeOverride, c.order(overridden.id)!!.zoneOverrides[OrderZonePoint.DESTINATION])
        assertEquals(listOf(beforeOrder), LocalStore(context).loadOrders())
        assertEquals(listOf(beforePayment), LocalStore(context).loadPayments())

        val restarted = MandadosController(context)
        assertEquals(beforeOrder, restarted.order(overridden.id))
        assertEquals(beforePayment, restarted.paymentForOrder(overridden.id))
        assertEquals(beforeEvents, restarted.order(overridden.id)!!.events)
        assertEquals(beforeOverride, restarted.order(overridden.id)!!.zoneOverrides[OrderZonePoint.DESTINATION])
    }

    private fun testConfig(): AdminConfig = AdminConfig(
        operationMode = OperationMode.MULTI_RIDER,
        zones = listOf(
            ZoneConfig("a", "Zona A", "", "TEST", 1000, true),
            ZoneConfig("same", "Zona B equivalente", "", "TEST", 1000, true),
            ZoneConfig("b", "Zona B", "", "TEST", 2000, true)
        )
    )

    private fun deliveryDraft(origin: String, destination: String): OrderDraft = OrderDraft(
        serviceType = ServiceType.DELIVERY,
        category = ServiceCategory.ERRAND,
        originAddress = "Origen",
        originZoneId = origin,
        destinationAddress = "Destino",
        destinationZoneId = destination,
        deliveryPayment = DeliveryPaymentMethod.CASH
    )

    private fun orderFromDraft(id: String, draft: OrderDraft, cfg: AdminConfig): LocalOrder {
        val pricing = calculateOrderPricing(draft, emptyMap(), cfg)
        return LocalOrder(
            id = id,
            createdAt = stamp(),
            serviceType = draft.serviceType,
            category = draft.category,
            operationMode = OperationMode.MULTI_RIDER,
            status = OrderStatus.PENDING,
            customerName = "Cliente",
            customerPhone = "234-5555999",
            customerId = "CLI-TEST",
            detail = "Fixture",
            baseAmount = pricing.baseAmount,
            baseZoneName = pricing.baseZoneName,
            prePickupAmount = pricing.prePickupAmount,
            rainAmount = pricing.rainAmount,
            totalAmount = pricing.totalAmount,
            whatsappMessage = "",
            originAddress = draft.originAddress,
            originZoneId = draft.originZoneId,
            destinationAddress = draft.destinationAddress,
            destinationZoneId = draft.destinationZoneId,
            deliveryPayment = draft.deliveryPayment,
            events = listOf(OrderEvent(OrderEventType.CREATED, stamp(), OrderStatus.PENDING, actor = "CLIENTE"))
        )
    }

    private fun payment(
        order: LocalOrder,
        status: PaymentStatus = PaymentStatus.PENDING,
        proofUri: String? = null,
        providerPaymentId: String? = null
    ): PaymentRecord = PaymentRecord(
        id = "PAY-${order.id}",
        orderId = order.id,
        riderId = order.assignedRiderId,
        channel = paymentChannelFor(order.deliveryPayment),
        expectedAmount = order.totalAmount ?: 0,
        status = status,
        proofUri = proofUri,
        providerPaymentId = providerPaymentId,
        createdAt = stamp(),
        updatedAt = stamp()
    )

    private fun seed(cfg: AdminConfig, order: LocalOrder, payment: PaymentRecord) {
        val store = LocalStore(context)
        store.saveConfig(cfg)
        store.saveOrdersAndPayments(listOf(order), listOf(payment))
    }

    private fun clearMainStore() {
        context.getSharedPreferences("mandados_alpha1", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun stamp(): String = "08/10/2026 15:38:00"
}
