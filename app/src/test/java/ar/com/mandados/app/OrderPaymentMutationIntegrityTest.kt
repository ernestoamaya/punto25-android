package ar.com.mandados.app

import android.content.Context
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
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
class OrderPaymentMutationIntegrityTest {
    private lateinit var context: Context
    private val stamp = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("mandados_alpha1", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `REG-PAY-MUTATION-LOCK-001 committed payments block economic changes including dirty PENDING`() {
        val current = order("LOCK", total = 1000, paymentMethod = DeliveryPaymentMethod.CASH)
        val amountChanged = current.copy(totalAmount = 2000)
        val channelChanged = current.copy(deliveryPayment = DeliveryPaymentMethod.TRANSFER)

        PaymentStatus.entries.filter { it != PaymentStatus.PENDING }.forEach { status ->
            val payment = payment(current, status = status)
            assertTrue("$status must be committed", isPaymentFinanciallyCommitted(payment))
            assertEquals(OrderPaymentMutationAction.DENY, evaluateOrderPaymentMutation(current, amountChanged, payment))
            assertEquals(OrderPaymentMutationAction.DENY, evaluateOrderPaymentMutation(current, channelChanged, payment))
            assertFalse(isCleanPendingPaymentForTake(current, payment))
        }

        val pendingWithProof = payment(current, proofUri = "content://proof")
        val pendingWithProviderId = payment(current, providerPaymentId = "provider-123")
        listOf(pendingWithProof, pendingWithProviderId).forEach { dirtyPending ->
            assertTrue(isPaymentFinanciallyCommitted(dirtyPending))
            assertEquals(OrderPaymentMutationAction.DENY, evaluateOrderPaymentMutation(current, amountChanged, dirtyPending))
            assertEquals(OrderPaymentMutationAction.DENY, evaluateOrderPaymentMutation(current, channelChanged, dirtyPending))
            assertFalse(isCleanPendingPaymentForTake(current, dirtyPending))
        }

        assertFalse(isPaymentFinanciallyCommitted(payment(current, provider = "Mercado Pago")))
    }

    @Test
    fun `REG-PAY-MUTATION-LOCK-001 contradictory committed payment cannot reconcile order by edit`() {
        val current = order("CONTRADICTORY-PURE", total = 1000, paymentMethod = DeliveryPaymentMethod.CASH)
        val committedAmountMismatch = payment(current, status = PaymentStatus.CONFIRMED).copy(expectedAmount = 2000)
        val amountCandidate = current.copy(totalAmount = 2000)
        assertEquals(
            OrderPaymentMutationAction.DENY,
            evaluateOrderPaymentMutation(current, amountCandidate, committedAmountMismatch)
        )

        val committedChannelMismatch = payment(current, status = PaymentStatus.CONFIRMED).copy(
            channel = PaymentChannel.RIDER_TRANSFER
        )
        val channelCandidate = current.copy(deliveryPayment = DeliveryPaymentMethod.TRANSFER)
        assertEquals(
            OrderPaymentMutationAction.DENY,
            evaluateOrderPaymentMutation(current, channelCandidate, committedChannelMismatch)
        )

        val zones = configurePricedZones()
        val persistedOrder = order(
            id = "CONTRADICTORY-EDIT",
            total = 1000,
            paymentMethod = DeliveryPaymentMethod.CASH,
            originZoneId = zones.first,
            destinationZoneId = zones.first
        )
        val committedPayment = payment(persistedOrder, status = PaymentStatus.CONFIRMED).copy(expectedAmount = 2000)
        seed(persistedOrder, committedPayment)
        val c = MandadosController(context)
        val beforeOrders = c.orders
        val beforePayments = c.payments
        val beforeEvents = c.order(persistedOrder.id)!!.events

        val edited = c.draftFromOrder(persistedOrder).copy(
            destinationZoneId = zones.second,
            notes = "No debe reconciliar una contradicción previa"
        )
        assertFalse(c.editOrder(persistedOrder.id, edited, "Intento de reconciliación implícita"))
        assertEquals(beforeOrders, c.orders)
        assertEquals(beforePayments, c.payments)
        assertEquals(beforeEvents, c.order(persistedOrder.id)!!.events)
        assertEquals(persistedOrder, c.order(persistedOrder.id))
        assertEquals(committedPayment, c.paymentForOrder(persistedOrder.id))

        val store = LocalStore(context)
        assertEquals(beforeOrders, store.loadOrders())
        assertEquals(beforePayments, store.loadPayments())
        val restarted = MandadosController(context)
        assertEquals(persistedOrder, restarted.order(persistedOrder.id))
        assertEquals(committedPayment, restarted.paymentForOrder(persistedOrder.id))
        assertEquals(beforeEvents, restarted.order(persistedOrder.id)!!.events)
    }

    @Test
    fun `REG-PAY-RIDER-LOCK-001 committed rider transfer blocks reassignment and unassignment without side effects`() {
        val riderA = approvedRider("RID-A")
        val riderB = approvedRider("RID-B")
        val shift = allDayShift()
        val order = order(
            id = "RIDER-LOCK",
            total = 1000,
            paymentMethod = DeliveryPaymentMethod.TRANSFER,
            riderId = riderA.id
        )
        val payment = payment(order, status = PaymentStatus.DECLARED)
        seed(order, payment, riders = listOf(riderA, riderB), shifts = listOf(shift))
        val c = MandadosController(context)
        assertTrue(c.adminAddRiderToShift(riderB.id, shift.id))

        val beforeOrder = c.order(order.id)!!
        val beforePayment = c.paymentForOrder(order.id)!!
        assertTrue(isCommittedRiderTransferAssignmentLocked(beforeOrder, riderB.id, beforePayment))
        assertTrue(isCommittedRiderTransferAssignmentLocked(beforeOrder, null, beforePayment))

        assertFalse(c.assignRider(order.id, riderB.id))
        assertFalse(c.assignRider(order.id, null))
        assertEquals(beforeOrder, c.order(order.id))
        assertEquals(beforePayment, c.paymentForOrder(order.id))

        val restarted = MandadosController(context)
        assertEquals(beforeOrder, restarted.order(order.id))
        assertEquals(beforePayment, restarted.paymentForOrder(order.id))
    }

    @Test
    fun `REG-PAY-MUTATION-PENDING-001 clean pending mutation syncs total channel amount and normal take`() {
        val zones = configurePricedZones()
        val rider = approvedRider("RID-A")
        val shift = allDayShift()
        val original = order(
            id = "PENDING-MUTATION",
            total = 1000,
            paymentMethod = DeliveryPaymentMethod.CASH,
            originZoneId = zones.first,
            destinationZoneId = zones.first
        )
        val originalPayment = payment(original)
        seed(
            original,
            originalPayment,
            riders = listOf(rider),
            credentials = listOf(testCredential(rider.id, "RiderA123")),
            shifts = listOf(shift)
        )
        val c = MandadosController(context)
        assertTrue(c.authenticateRider(rider.id, "RiderA123"))
        assertTrue(c.reserveShift(rider.id, shift.id))
        assertTrue(c.setRiderAvailable(rider.id, true))

        val edited = c.draftFromOrder(original).copy(
            destinationZoneId = zones.second,
            deliveryPayment = DeliveryPaymentMethod.TRANSFER,
            notes = "Cambio válido"
        )
        assertTrue(c.editOrder(original.id, edited, "Actualización económica"))

        val afterEditOrder = c.order(original.id)!!
        val afterEditPayment = c.paymentForOrder(original.id)!!
        assertEquals(2000, afterEditOrder.totalAmount)
        assertEquals(PaymentChannel.RIDER_TRANSFER, afterEditPayment.channel)
        assertEquals(2000, afterEditPayment.expectedAmount)
        assertEquals(PaymentStatus.PENDING, afterEditPayment.status)
        assertEquals(1, c.payments.count { it.orderId == original.id })

        assertTrue(c.takeOrder(original.id, rider.id))
        assertEquals(rider.id, c.order(original.id)!!.assignedRiderId)
        assertEquals(rider.id, c.paymentForOrder(original.id)!!.riderId)
        assertEquals(2000, c.paymentForOrder(original.id)!!.expectedAmount)
    }

    @Test
    fun `REG-PAY-NONFINANCIAL-EDIT-001 committed coherent payment allows nonfinancial edit unchanged`() {
        val zones = configurePricedZones()
        val original = order(
            id = "NONFINANCIAL",
            total = 1000,
            paymentMethod = DeliveryPaymentMethod.CASH,
            originZoneId = zones.first,
            destinationZoneId = zones.first
        )
        val committed = payment(original, status = PaymentStatus.CONFIRMED)
        seed(original, committed)
        val c = MandadosController(context)

        val edited = c.draftFromOrder(original).copy(
            originReference = "Puerta azul",
            notes = "Sólo cambia texto"
        )
        assertTrue(c.editOrder(original.id, edited, "Corrección de referencia"))

        val updatedOrder = c.order(original.id)!!
        assertEquals("Puerta azul", updatedOrder.originReference)
        assertEquals(1000, updatedOrder.totalAmount)
        assertEquals(committed, c.paymentForOrder(original.id))
        assertEquals(original.events.size + 1, updatedOrder.events.size)
    }

    @Test
    fun `REG-PAY-QUOTE-CONSISTENCY-001 quote amount is zero and priced active order cannot degrade to unknown`() {
        val quote = order("QUOTE", total = null, status = OrderStatus.AWAITING_QUOTE)
        val quotePayment = paymentRecordSynchronizedToOrder(quote, existing = null, timestamp = nowText())
        assertEquals(0, quotePayment.expectedAmount)
        assertEquals(OrderPaymentMutationAction.ORDER_ONLY, evaluateOrderPaymentMutation(quote, quote, quotePayment))

        val zones = configurePricedZones()
        val priced = order(
            id = "KNOWN-PRICE",
            total = 1000,
            status = OrderStatus.PENDING,
            originZoneId = zones.first,
            destinationZoneId = zones.first
        )
        val pricedPayment = payment(priced)
        seed(priced, pricedPayment)
        val c = MandadosController(context)
        val unknown = c.draftFromOrder(priced).copy(destinationZoneId = UNKNOWN_ZONE_ID)

        assertFalse(c.editOrder(priced.id, unknown, "Intento de degradar tarifa"))
        assertEquals(priced, c.order(priced.id))
        assertEquals(pricedPayment, c.paymentForOrder(priced.id))
        assertEquals(OrderPaymentMutationAction.DENY, evaluateOrderPaymentMutation(priced, priced.copy(totalAmount = null), pricedPayment))
    }

    @Test
    fun `REG-PAY-ORDER-SYNC-001 allowed mutation survives recreation with exact order payment amount`() {
        val zones = configurePricedZones()
        val original = order(
            id = "RESTART-SYNC",
            total = 1000,
            originZoneId = zones.first,
            destinationZoneId = zones.first
        )
        seed(original, payment(original))
        val c = MandadosController(context)

        val edited = c.draftFromOrder(original).copy(destinationZoneId = zones.second)
        assertTrue(c.editOrder(original.id, edited, "Cambio de zona"))
        assertEquals(2000, c.order(original.id)!!.totalAmount)
        assertEquals(2000, c.paymentForOrder(original.id)!!.expectedAmount)

        val restarted = MandadosController(context)
        val persistedOrder = restarted.order(original.id)!!
        val persistedPayment = restarted.paymentForOrder(original.id)!!
        assertEquals(persistedOrder.totalAmount, persistedPayment.expectedAmount)
        assertEquals(2000, persistedPayment.expectedAmount)
        assertEquals(paymentChannelFor(persistedOrder.deliveryPayment), persistedPayment.channel)
    }

    @Test
    fun `REG-PAY-MUTATION-NO-PARTIAL-001 blocked mutation preserves memory events evidence provider and persistence`() {
        val zones = configurePricedZones()
        val original = order(
            id = "NO-PARTIAL",
            total = 1000,
            paymentMethod = DeliveryPaymentMethod.TRANSFER,
            originZoneId = zones.first,
            destinationZoneId = zones.first,
            riderId = "RID-A"
        )
        val committed = payment(
            original,
            status = PaymentStatus.PROOF_UPLOADED,
            proofUri = "content://proof/original",
            provider = "Mercado Pago",
            providerPaymentId = "provider-payment-777"
        )
        seed(original, committed)
        val c = MandadosController(context)
        val beforeOrders = c.orders
        val beforePayments = c.payments

        val edited = c.draftFromOrder(original).copy(
            destinationZoneId = zones.second,
            deliveryPayment = DeliveryPaymentMethod.CASH,
            notes = "No debe persistir"
        )
        assertFalse(c.editOrder(original.id, edited, "Mutación bloqueada"))
        assertEquals(beforeOrders, c.orders)
        assertEquals(beforePayments, c.payments)
        assertEquals(original.events, c.order(original.id)!!.events)
        assertEquals("content://proof/original", c.paymentForOrder(original.id)!!.proofUri)
        assertEquals("Mercado Pago", c.paymentForOrder(original.id)!!.provider)
        assertEquals("provider-payment-777", c.paymentForOrder(original.id)!!.providerPaymentId)

        assertEquals(beforeOrders, LocalStore(context).loadOrders())
        assertEquals(beforePayments, LocalStore(context).loadPayments())
        val restarted = MandadosController(context)
        assertEquals(original, restarted.order(original.id))
        assertEquals(committed, restarted.paymentForOrder(original.id))
    }

    private fun configurePricedZones(): Pair<String, String> {
        val c = MandadosController(context)
        require(c.config.zones.size >= 2)
        val updated = c.config.zones.mapIndexed { index, zone ->
            zone.copy(price = if (index == 0) 1000 else 2000 + index, enabled = true)
        }.toMutableList()
        updated[1] = updated[1].copy(price = 2000)
        c.updateConfig(c.config.copy(zones = updated))
        return updated[0].id to updated[1].id
    }

    private fun seed(
        order: LocalOrder,
        payment: PaymentRecord,
        riders: List<RiderProfile> = emptyList(),
        credentials: List<RiderCredential> = emptyList(),
        shifts: List<ConcreteShift> = emptyList()
    ) {
        val store = LocalStore(context)
        store.saveOrders(listOf(order))
        store.savePayments(listOf(payment))
        store.saveRiders(riders)
        store.saveRiderCredentials(credentials)
        val v2 = ShiftStoreV2(context)
        assertTrue(v2.initializeIfNeeded().success)
        assertTrue(v2.saveAll(emptyList(), shifts, emptyList(), emptyList()))
    }

    private fun order(
        id: String,
        total: Int?,
        paymentMethod: DeliveryPaymentMethod = DeliveryPaymentMethod.CASH,
        status: OrderStatus = if (total == null) OrderStatus.AWAITING_QUOTE else OrderStatus.PENDING,
        originZoneId: String = UNKNOWN_ZONE_ID,
        destinationZoneId: String = UNKNOWN_ZONE_ID,
        riderId: String? = null
    ): LocalOrder = LocalOrder(
        id = id,
        createdAt = nowText(),
        serviceType = ServiceType.DELIVERY,
        category = ServiceCategory.ERRAND,
        operationMode = OperationMode.MULTI_RIDER,
        status = status,
        customerName = "Cliente",
        customerPhone = "2345555999",
        customerId = "DEV-2345555999",
        detail = "Fixture",
        baseAmount = total,
        baseZoneName = if (total == null) null else "Fixture",
        prePickupAmount = if (total == null) null else 0,
        rainAmount = if (total == null) null else 0,
        totalAmount = total,
        whatsappMessage = "",
        assignedRiderId = riderId,
        originAddress = "Origen",
        originZoneId = originZoneId,
        destinationAddress = "Destino",
        destinationZoneId = destinationZoneId,
        deliveryPayment = paymentMethod,
        events = listOf(OrderEvent(OrderEventType.CREATED, nowText(), status, actor = "CLIENTE"))
    )

    private fun payment(
        order: LocalOrder,
        status: PaymentStatus = PaymentStatus.PENDING,
        proofUri: String? = null,
        provider: String? = null,
        providerPaymentId: String? = null
    ): PaymentRecord = PaymentRecord(
        id = "PAY-${order.id}",
        orderId = order.id,
        riderId = order.assignedRiderId,
        channel = paymentChannelFor(order.deliveryPayment),
        expectedAmount = order.totalAmount ?: 0,
        status = status,
        proofUri = proofUri,
        provider = provider,
        providerPaymentId = providerPaymentId,
        createdAt = nowText(),
        updatedAt = nowText()
    )

    private fun approvedRider(id: String): RiderProfile {
        val docs = RiderDocuments(
            dniFrontUri = "content://docs/$id/dni-front",
            dniBackUri = "content://docs/$id/dni-back",
            motorcyclePlateUri = "content://docs/$id/plate",
            driverLicenseFrontUri = "content://docs/$id/license-front",
            driverLicenseBackUri = "content://docs/$id/license-back",
            vehicleCardUri = "content://docs/$id/vehicle-card",
            insuranceCardUri = "content://docs/$id/insurance"
        )
        return RiderProfile(
            id = id,
            name = id,
            phone = "2345555001",
            vehicleType = VehicleType.MOTORCYCLE,
            documents = docs,
            approvalStatus = RiderApprovalStatus.APPROVED,
            documentReviews = RiderDocumentKey.entries.associateWith { DocumentReviewStatus.APPROVED },
            active = true,
            available = false
        )
    }

    private fun allDayShift(): ConcreteShift = ConcreteShift(
        id = "CS-PAY-MUTATION",
        serviceDate = ShiftSchedulePolicy.toIsoDate(LocalDate.now()),
        startMinute = 0,
        endMinute = 24 * 60,
        capacity = 4,
        enabled = true
    )

    private fun testCredential(riderId: String, password: String): RiderCredential {
        val salt = ("p25-$riderId-test-salt").toByteArray().copyOf(16)
        val iterations = 1_000
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
        val hash = try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
        return RiderCredential(
            riderId = riderId,
            saltBase64 = Base64.getEncoder().encodeToString(salt),
            passwordHashBase64 = Base64.getEncoder().encodeToString(hash),
            iterations = iterations,
            updatedAt = nowText()
        )
    }

    private fun nowText(): String = LocalDateTime.now().format(stamp)
}
