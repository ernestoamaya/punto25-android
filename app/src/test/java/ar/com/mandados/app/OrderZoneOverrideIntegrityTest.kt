package ar.com.mandados.app

import android.content.Context
import org.json.JSONArray
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
class OrderZoneOverrideIntegrityTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        clearMainStore()
    }

    @Test
    fun `REG-ZONE-OVERRIDE-DECLARED-001 declared zones are never overwritten and explicit restore removes only override`() {
        val cfg = testConfig()
        val original = orderFromDraft("DECLARED", deliveryDraft("a", "a"), cfg)
        seed(cfg, listOf(original), listOf(payment(original)))
        val c = MandadosController(context)

        assertTrue(
            c.applyOrderZoneOverride(
                original.id,
                OrderZonePoint.ORIGIN,
                OrderZoneOverrideSelection.AdHoc("Circunstancial", 1500),
                "Corrección manual"
            )
        )
        val overridden = c.order(original.id)!!
        assertEquals("a", overridden.originZoneId)
        assertEquals("a", overridden.destinationZoneId)
        assertEquals(1500, overridden.zoneOverrides[OrderZonePoint.ORIGIN]?.price)

        assertTrue(
            c.applyOrderZoneOverride(
                original.id,
                OrderZonePoint.ORIGIN,
                OrderZoneOverrideSelection.Declared,
                "Restaurar zona declarada"
            )
        )
        val restored = c.order(original.id)!!
        assertEquals("a", restored.originZoneId)
        assertEquals("a", restored.destinationZoneId)
        assertFalse(restored.zoneOverrides.containsKey(OrderZonePoint.ORIGIN))
        assertEquals(1000, restored.totalAmount)
        assertEquals(1000, c.paymentForOrder(original.id)!!.expectedAmount)
    }

    @Test
    fun `REG-ZONE-OVERRIDE-CATALOG-001 catalog override snapshots valid zone deterministically`() {
        val cfg = testConfig()
        val original = orderFromDraft("CATALOG", deliveryDraft("a", "a"), cfg)
        seed(cfg, listOf(original), listOf(payment(original)))
        val c = MandadosController(context)

        assertTrue(
            c.applyOrderZoneOverride(
                original.id,
                OrderZonePoint.ORIGIN,
                OrderZoneOverrideSelection.Catalog("b"),
                "Aplicar catálogo"
            )
        )
        val applied = c.order(original.id)!!
        val snapshot = applied.zoneOverrides[OrderZonePoint.ORIGIN]!!
        assertEquals(OrderZoneOverrideSource.CATALOG, snapshot.source)
        assertEquals("b", snapshot.catalogZoneId)
        assertEquals("Zona B", snapshot.name)
        assertEquals(2000, snapshot.price)
        assertEquals(2000, applied.totalAmount)

        val changedCatalog = c.config.copy(
            zones = c.config.zones.map {
                if (it.id == "b") it.copy(name = "Zona B nueva", price = 3500) else it
            }
        )
        c.updateConfig(changedCatalog)
        val edited = c.draftFromOrder(applied).copy(notes = "Edición no financiera")
        assertTrue(c.editOrder(original.id, edited, "Sólo notas"))
        val afterCatalogChange = c.order(original.id)!!
        assertEquals(snapshot, afterCatalogChange.zoneOverrides[OrderZonePoint.ORIGIN])
        assertEquals(2000, afterCatalogChange.totalAmount)
        assertEquals(2000, c.paymentForOrder(original.id)!!.expectedAmount)

        val invalid = c.previewOrderZoneOverride(
            original.id,
            OrderZonePoint.DESTINATION,
            OrderZoneOverrideSelection.Catalog("disabled")
        )!!
        assertFalse(invalid.allowed)
    }

    @Test
    fun `REG-ZONE-OVERRIDE-ADHOC-001 ad hoc affects one order and never mutates catalog`() {
        val cfg = testConfig()
        val first = orderFromDraft("ADHOC-A", deliveryDraft("a", "a"), cfg)
        val second = orderFromDraft("ADHOC-B", deliveryDraft("a", "a"), cfg)
        seed(cfg, listOf(first, second), listOf(payment(first), payment(second)))
        val c = MandadosController(context)
        val catalogBefore = c.config.zones

        assertTrue(
            c.applyOrderZoneOverride(
                first.id,
                OrderZonePoint.DESTINATION,
                OrderZoneOverrideSelection.AdHoc("Barrio temporal", 2600),
                "Caso excepcional"
            )
        )
        assertEquals(catalogBefore, c.config.zones)
        assertEquals(2600, c.order(first.id)!!.zoneOverrides[OrderZonePoint.DESTINATION]?.price)
        assertTrue(c.order(second.id)!!.zoneOverrides.isEmpty())
        assertEquals(1000, c.order(second.id)!!.totalAmount)

        assertFalse(
            c.previewOrderZoneOverride(
                first.id,
                OrderZonePoint.ORIGIN,
                OrderZoneOverrideSelection.AdHoc("   ", 1000)
            )!!.allowed
        )
        assertFalse(
            c.previewOrderZoneOverride(
                first.id,
                OrderZonePoint.ORIGIN,
                OrderZoneOverrideSelection.AdHoc("Temporal", 0)
            )!!.allowed
        )
    }

    @Test
    fun `REG-ZONE-OVERRIDE-PRICE-001 delivery and shopping use applied zones with prepickup and rain`() {
        val cfg = testConfig(
            rainEnabled = true,
            rainAmount = 100,
            prePickupMode = PrePickupMode.PERCENT_BASE,
            prePickupValue = 10
        )
        val delivery = deliveryDraft("a", "b")
        val deliveryPrice = calculateOrderPricing(
            delivery,
            mapOf(
                OrderZonePoint.ORIGIN to OrderZoneOverride(
                    OrderZoneOverrideSource.AD_HOC,
                    name = "Origen especial",
                    price = 3000
                )
            ),
            cfg
        )
        assertFalse(deliveryPrice.needsQuote)
        assertEquals(3000, deliveryPrice.baseAmount)
        assertEquals(0, deliveryPrice.prePickupAmount)
        assertEquals(100, deliveryPrice.rainAmount)
        assertEquals(3100, deliveryPrice.totalAmount)

        val shopping = shoppingDraft(store = "a", destination = "b", prePickup = "a")
        val shoppingOverrides = mapOf(
            OrderZonePoint.PRE_PICKUP to OrderZoneOverride(
                OrderZoneOverrideSource.AD_HOC,
                name = "Retiro especial",
                price = 4000
            )
        )
        val shoppingPrice = calculateOrderPricing(shopping, shoppingOverrides, cfg)
        assertFalse(shoppingPrice.needsQuote)
        assertEquals(4000, shoppingPrice.baseAmount)
        assertEquals(400, shoppingPrice.prePickupAmount)
        assertEquals(100, shoppingPrice.rainAmount)
        assertEquals(4500, shoppingPrice.totalAmount)

        seed(cfg, emptyList(), emptyList())
        val c = MandadosController(context)
        assertEquals(shoppingPrice, c.pricing(shopping, shoppingOverrides))
    }

    @Test
    fun `REG-ZONE-OVERRIDE-AUDIT-001 reason is mandatory and each operation appends exactly one admin event`() {
        val cfg = testConfig()
        val original = orderFromDraft("AUDIT", deliveryDraft("a", "a"), cfg)
        seed(cfg, listOf(original), listOf(payment(original)))
        val c = MandadosController(context)
        val initialEvents = original.events.size

        assertFalse(
            c.applyOrderZoneOverride(
                original.id,
                OrderZonePoint.ORIGIN,
                OrderZoneOverrideSelection.AdHoc("Temporal", 1500),
                "   "
            )
        )
        assertEquals(initialEvents, c.order(original.id)!!.events.size)

        assertTrue(
            c.applyOrderZoneOverride(
                original.id,
                OrderZonePoint.ORIGIN,
                OrderZoneOverrideSelection.AdHoc("Temporal", 1500),
                "Dirección fuera de zona declarada"
            )
        )
        val first = c.order(original.id)!!
        assertEquals(initialEvents + 1, first.events.size)
        val firstAudit = first.events.last()
        assertEquals(OrderEventType.ORDER_EDITED, firstAudit.type)
        assertEquals("ADMIN", firstAudit.actor)
        val note = firstAudit.note.orEmpty()
        assertTrue(note.contains("punto=ORIGIN"))
        assertTrue(note.contains("zona declarada="))
        assertTrue(note.contains("zona aplicada anterior="))
        assertTrue(note.contains("zona aplicada nueva="))
        assertTrue(note.contains("total anterior=1000"))
        assertTrue(note.contains("total nuevo=1500"))
        assertTrue(note.contains("motivo=Dirección fuera de zona declarada"))

        assertTrue(
            c.applyOrderZoneOverride(
                original.id,
                OrderZonePoint.ORIGIN,
                OrderZoneOverrideSelection.Catalog("b"),
                "Ajuste de segunda revisión"
            )
        )
        assertEquals(initialEvents + 2, c.order(original.id)!!.events.size)
    }

    @Test
    fun `REG-ZONE-OVERRIDE-PAYMENT-001 mutable payment syncs exactly and committed payment rejects without mutation`() {
        val cfg = testConfig()
        val mutableOrder = orderFromDraft("PAY-MUTABLE", deliveryDraft("a", "a"), cfg)
        seed(cfg, listOf(mutableOrder), listOf(payment(mutableOrder)))
        val mutableController = MandadosController(context)

        assertTrue(
            mutableController.applyOrderZoneOverride(
                mutableOrder.id,
                OrderZonePoint.DESTINATION,
                OrderZoneOverrideSelection.Catalog("b"),
                "Actualizar tarifa"
            )
        )
        val updatedOrder = mutableController.order(mutableOrder.id)!!
        val updatedPayment = mutableController.paymentForOrder(mutableOrder.id)!!
        assertEquals(2000, updatedOrder.totalAmount)
        assertEquals(updatedOrder.totalAmount, updatedPayment.expectedAmount)
        assertEquals(PaymentStatus.PENDING, updatedPayment.status)
        assertEquals(1, mutableController.payments.count { it.orderId == mutableOrder.id })

        clearMainStore()
        val committedOrder = orderFromDraft("PAY-COMMITTED", deliveryDraft("a", "a"), cfg)
        val committed = payment(committedOrder, status = PaymentStatus.CONFIRMED)
        seed(cfg, listOf(committedOrder), listOf(committed))
        val committedController = MandadosController(context)
        val beforeOrders = committedController.orders
        val beforePayments = committedController.payments
        val beforeEvents = committedController.order(committedOrder.id)!!.events

        assertFalse(
            committedController.applyOrderZoneOverride(
                committedOrder.id,
                OrderZonePoint.DESTINATION,
                OrderZoneOverrideSelection.Catalog("b"),
                "No debe pasar"
            )
        )
        assertEquals(beforeOrders, committedController.orders)
        assertEquals(beforePayments, committedController.payments)
        assertEquals(beforeEvents, committedController.order(committedOrder.id)!!.events)
        assertEquals(beforeOrders, LocalStore(context).loadOrders())
        assertEquals(beforePayments, LocalStore(context).loadPayments())

        val dirtyPendingProof = payment(committedOrder, proofUri = "content://proof")
        val dirtyPendingProvider = payment(committedOrder, providerPaymentId = "provider-1")
        listOf(dirtyPendingProof, dirtyPendingProvider).forEach { dirty ->
            val preview = evaluateOrderZoneOverride(
                committedOrder,
                OrderZonePoint.DESTINATION,
                OrderZoneOverrideSelection.Catalog("b"),
                cfg,
                listOf(dirty)
            )
            assertFalse(preview.allowed)
        }
    }

    @Test
    fun `REG-ZONE-OVERRIDE-BALANCE-001 corrected total enters rider balance once and tip remains separate`() {
        val cfg = testConfig()
        val original = orderFromDraft(
            "BALANCE",
            deliveryDraft("a", "a", DeliveryPaymentMethod.TRANSFER),
            cfg,
            riderId = "RID-1"
        )
        seed(cfg, listOf(original), listOf(payment(original)))
        val c = MandadosController(context)

        assertTrue(
            c.applyOrderZoneOverride(
                original.id,
                OrderZonePoint.ORIGIN,
                OrderZoneOverrideSelection.AdHoc("Tarifa corregida", 2500),
                "Corrección previa al cierre"
            )
        )
        val corrected = c.order(original.id)!!
        assertEquals(2500, corrected.totalAmount)
        val completed = corrected.copy(status = OrderStatus.COMPLETED)
        val tip = OrderRating(
            orderId = completed.id,
            customerId = completed.customerId,
            riderId = "RID-1",
            stars = 5,
            tipAmount = 300,
            tipStatus = TipStatus.CONFIRMED,
            createdAt = stamp()
        )
        assertEquals(2500, calculateRiderBalance("RID-1", listOf(completed), emptyList()))
        assertEquals(2800, calculateRiderBalance("RID-1", listOf(completed), listOf(tip)))
    }

    @Test
    fun `REG-ZONE-OVERRIDE-TERMINAL-001 terminal states reject override without side effects`() {
        val cfg = testConfig()
        listOf(OrderStatus.COMPLETED, OrderStatus.CANCELLED, OrderStatus.REJECTED).forEach { status ->
            clearMainStore()
            val terminal = orderFromDraft("TERM-${status.name}", deliveryDraft("a", "a"), cfg, status = status)
            val terminalPayment = payment(terminal)
            seed(cfg, listOf(terminal), listOf(terminalPayment))
            val c = MandadosController(context)
            val beforeEvents = terminal.events

            assertFalse(
                c.applyOrderZoneOverride(
                    terminal.id,
                    OrderZonePoint.ORIGIN,
                    OrderZoneOverrideSelection.Catalog("b"),
                    "No permitido"
                )
            )
            assertEquals(terminal, c.order(terminal.id))
            assertEquals(terminalPayment, c.paymentForOrder(terminal.id))
            assertEquals(beforeEvents, c.order(terminal.id)!!.events)
            assertEquals(listOf(terminal), LocalStore(context).loadOrders())
            assertEquals(listOf(terminalPayment), LocalStore(context).loadPayments())
        }
    }

    @Test
    fun `REG-ZONE-OVERRIDE-QUOTE-001 partial quote remains zero and final zone resolves atomically to pending`() {
        val cfg = testConfig()
        val quoteDraft = deliveryDraft(UNKNOWN_ZONE_ID, UNKNOWN_ZONE_ID)
        val quote = orderFromDraft("QUOTE", quoteDraft, cfg)
        assertEquals(OrderStatus.AWAITING_QUOTE, quote.status)
        assertNull(quote.totalAmount)
        val quotePayment = payment(quote)
        assertEquals(0, quotePayment.expectedAmount)
        seed(cfg, listOf(quote), listOf(quotePayment))
        val c = MandadosController(context)

        assertTrue(
            c.applyOrderZoneOverride(
                quote.id,
                OrderZonePoint.ORIGIN,
                OrderZoneOverrideSelection.Catalog("a"),
                "Resolver retiro"
            )
        )
        val partial = c.order(quote.id)!!
        assertEquals(OrderStatus.AWAITING_QUOTE, partial.status)
        assertNull(partial.totalAmount)
        assertEquals(0, c.paymentForOrder(quote.id)!!.expectedAmount)
        assertEquals(UNKNOWN_ZONE_ID, partial.originZoneId)
        assertEquals(UNKNOWN_ZONE_ID, partial.destinationZoneId)

        assertTrue(
            c.applyOrderZoneOverride(
                quote.id,
                OrderZonePoint.DESTINATION,
                OrderZoneOverrideSelection.Catalog("b"),
                "Resolver entrega"
            )
        )
        val resolved = c.order(quote.id)!!
        val resolvedPayment = c.paymentForOrder(quote.id)!!
        assertEquals(OrderStatus.PENDING, resolved.status)
        assertEquals(2000, resolved.totalAmount)
        assertEquals(resolved.totalAmount, resolvedPayment.expectedAmount)
        assertEquals(PaymentStatus.PENDING, resolvedPayment.status)
        assertEquals(UNKNOWN_ZONE_ID, resolved.originZoneId)
        assertEquals(UNKNOWN_ZONE_ID, resolved.destinationZoneId)
    }

    @Test
    fun `REG-ZONE-OVERRIDE-PERSIST-001 declared applied pricing audit and payment survive recreation with legacy safe default`() {
        val cfg = testConfig()
        val original = orderFromDraft("PERSIST", deliveryDraft("a", "a"), cfg)
        seed(cfg, listOf(original), listOf(payment(original)))
        val c = MandadosController(context)
        assertTrue(
            c.applyOrderZoneOverride(
                original.id,
                OrderZonePoint.ORIGIN,
                OrderZoneOverrideSelection.AdHoc("Persistida", 2750),
                "Persistir snapshot"
            )
        )
        val beforeOrder = c.order(original.id)!!
        val beforePayment = c.paymentForOrder(original.id)!!
        val restarted = MandadosController(context)
        assertEquals(beforeOrder, restarted.order(original.id))
        assertEquals(beforePayment, restarted.paymentForOrder(original.id))
        assertEquals("a", restarted.order(original.id)!!.originZoneId)
        assertEquals(2750, restarted.order(original.id)!!.zoneOverrides[OrderZonePoint.ORIGIN]?.price)
        assertTrue(restarted.order(original.id)!!.events.last().note.orEmpty().contains("Persistir snapshot"))

        clearMainStore()
        val legacy = orderFromDraft("LEGACY-NO-OVERRIDE", deliveryDraft("a", "a"), cfg)
        val legacyStore = LocalStore(context)
        legacyStore.saveConfig(cfg)
        legacyStore.saveOrders(listOf(legacy))
        val prefs = context.getSharedPreferences("mandados_alpha1", Context.MODE_PRIVATE)
        val raw = JSONArray(prefs.getString("orders", "[]") ?: "[]")
        raw.getJSONObject(0).remove("zoneOverrides")
        prefs.edit().putString("orders", raw.toString()).commit()
        val loadedLegacy = LocalStore(context).loadOrders().single()
        assertTrue(loadedLegacy.zoneOverrides.isEmpty())
        assertEquals("a", loadedLegacy.originZoneId)
    }

    @Test
    fun `REG-ZONE-OVERRIDE-EDIT-001 editOrder preserves override and only explicit restore removes it`() {
        val cfg = testConfig()
        val original = orderFromDraft("EDIT", deliveryDraft("a", "a"), cfg)
        seed(cfg, listOf(original), listOf(payment(original)))
        val c = MandadosController(context)
        assertTrue(
            c.applyOrderZoneOverride(
                original.id,
                OrderZonePoint.ORIGIN,
                OrderZoneOverrideSelection.AdHoc("Aplicada", 3000),
                "Override inicial"
            )
        )
        val overrideSnapshot = c.order(original.id)!!.zoneOverrides[OrderZonePoint.ORIGIN]!!

        val edited = c.draftFromOrder(c.order(original.id)!!).copy(
            originZoneId = "b",
            originReference = "Nueva referencia",
            notes = "Edición general"
        )
        assertTrue(c.editOrder(original.id, edited, "Edición posterior"))
        val afterEdit = c.order(original.id)!!
        assertEquals("b", afterEdit.originZoneId)
        assertEquals(overrideSnapshot, afterEdit.zoneOverrides[OrderZonePoint.ORIGIN])
        assertEquals(3000, afterEdit.totalAmount)
        assertEquals(3000, c.paymentForOrder(original.id)!!.expectedAmount)

        assertTrue(
            c.applyOrderZoneOverride(
                original.id,
                OrderZonePoint.ORIGIN,
                OrderZoneOverrideSelection.Declared,
                "Restauración explícita"
            )
        )
        val restored = c.order(original.id)!!
        assertFalse(restored.zoneOverrides.containsKey(OrderZonePoint.ORIGIN))
        assertEquals("b", restored.originZoneId)
        assertEquals(2000, restored.totalAmount)
        assertEquals(2000, c.paymentForOrder(original.id)!!.expectedAmount)
    }

    private fun testConfig(
        rainEnabled: Boolean = false,
        rainAmount: Int = 0,
        prePickupMode: PrePickupMode = PrePickupMode.OFF,
        prePickupValue: Int = 0
    ): AdminConfig = AdminConfig(
        operationMode = OperationMode.MULTI_RIDER,
        rainEnabled = rainEnabled,
        rainAmount = rainAmount,
        prePickupMode = prePickupMode,
        prePickupValue = prePickupValue,
        zones = listOf(
            ZoneConfig("a", "Zona A", "", "TEST", 1000, true),
            ZoneConfig("b", "Zona B", "", "TEST", 2000, true),
            ZoneConfig("c", "Zona C", "", "TEST", 3000, true),
            ZoneConfig("disabled", "Deshabilitada", "", "TEST", 5000, false),
            ZoneConfig("zero", "Sin tarifa", "", "TEST", 0, true)
        )
    )

    private fun deliveryDraft(
        origin: String,
        destination: String,
        paymentMethod: DeliveryPaymentMethod = DeliveryPaymentMethod.CASH
    ): OrderDraft = OrderDraft(
        serviceType = ServiceType.DELIVERY,
        category = ServiceCategory.ERRAND,
        originAddress = "Origen",
        originZoneId = origin,
        destinationAddress = "Destino",
        destinationZoneId = destination,
        deliveryPayment = paymentMethod
    )

    private fun shoppingDraft(
        store: String,
        destination: String,
        prePickup: String
    ): OrderDraft = OrderDraft(
        serviceType = ServiceType.SHOPPING,
        category = ServiceCategory.PURCHASE,
        storeName = "Comercio",
        storeAddress = "Dirección comercio",
        storeZoneId = store,
        destinationAddress = "Destino",
        destinationZoneId = destination,
        instructionType = PurchaseInstructionType.PHYSICAL_NOTE,
        prePickupAddress = "Retiro previo",
        prePickupZoneId = prePickup,
        purchasePayment = PurchasePaymentMethod.DIRECT_TO_STORE,
        deliveryPayment = DeliveryPaymentMethod.CASH
    )

    private fun orderFromDraft(
        id: String,
        draft: OrderDraft,
        cfg: AdminConfig,
        status: OrderStatus? = null,
        riderId: String? = null
    ): LocalOrder {
        val pricing = calculateOrderPricing(draft, emptyMap(), cfg)
        val effectiveStatus = status ?: if (pricing.needsQuote) OrderStatus.AWAITING_QUOTE else OrderStatus.PENDING
        return LocalOrder(
            id = id,
            createdAt = stamp(),
            serviceType = draft.serviceType,
            category = draft.category,
            operationMode = OperationMode.MULTI_RIDER,
            status = effectiveStatus,
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
            assignedRiderId = riderId,
            originAddress = draft.originAddress,
            originReference = draft.originReference,
            originZoneId = draft.originZoneId,
            originLocation = draft.originLocation,
            destinationAddress = draft.destinationAddress,
            destinationReference = draft.destinationReference,
            destinationZoneId = draft.destinationZoneId,
            destinationLocation = draft.destinationLocation,
            carriedItem = draft.carriedItem,
            instructionType = draft.instructionType,
            purchaseDescription = draft.purchaseDescription,
            purchaseMaxAmount = draft.purchaseMaxAmount,
            storeName = draft.storeName,
            storeAddress = draft.storeAddress,
            storeZoneId = draft.storeZoneId,
            storeLocation = draft.storeLocation,
            purchasePayment = draft.purchasePayment,
            prePickupAddress = draft.prePickupAddress,
            prePickupReference = draft.prePickupReference,
            prePickupZoneId = draft.prePickupZoneId,
            prePickupLocation = draft.prePickupLocation,
            sameDeliveryAsPrePickup = draft.sameDeliveryAsPrePickup,
            deliveryPayment = draft.deliveryPayment,
            notes = draft.notes,
            events = listOf(OrderEvent(OrderEventType.CREATED, stamp(), effectiveStatus, actor = "CLIENTE"))
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

    private fun seed(
        cfg: AdminConfig,
        orders: List<LocalOrder>,
        payments: List<PaymentRecord>,
        ratings: List<OrderRating> = emptyList()
    ) {
        val store = LocalStore(context)
        store.saveConfig(cfg)
        store.saveOrdersAndPayments(orders, payments)
        store.saveRatings(ratings)
    }

    private fun clearMainStore() {
        context.getSharedPreferences("mandados_alpha1", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun stamp(): String = "08/10/2026 12:00:00"
}
