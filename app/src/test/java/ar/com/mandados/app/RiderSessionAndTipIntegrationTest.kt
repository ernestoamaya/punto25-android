package ar.com.mandados.app

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RiderSessionAndTipIntegrationTest {
    private lateinit var context: Context
    private val stamp = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")

    private val customer = Customer(
        name = "Cliente Test",
        areaCode = "2345",
        subscriber = "555555",
        locality = "25 de Mayo",
        accountId = "DEV-2345555555",
        whatsappVerified = true,
        whatsappVerifiedAt = "05/10/2026 02:00:00"
    )

    private val riderA = RiderProfile(
        id = "RID-A",
        name = "Rider A",
        phone = "2345555001",
        approvalStatus = RiderApprovalStatus.APPROVED,
        active = true
    )

    private val riderB = RiderProfile(
        id = "RID-B",
        name = "Rider B",
        phone = "2345555002",
        approvalStatus = RiderApprovalStatus.APPROVED,
        active = true
    )

    private val passwordA = "RiderA123"
    private val passwordB = "RiderB123"

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("mandados_alpha1", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `REG-PAY-TIP-017 fixture integral 100 llega a una unica propina pendiente para Rider autenticado`() {
        val c = fixtureDeclaredTip100()

        assertTrue(c.authenticateRider(riderA.id, passwordA))
        val pending = c.pendingTipsForRider(riderA.id)

        assertEquals(1, pending.size)
        assertEquals(TIP_ORDER_ID, pending.single().orderId)
        assertEquals(100, pending.single().tipAmount)
        assertEquals(TipStatus.TRANSFER_DECLARED, pending.single().tipStatus)
        assertTrue(c.riderCanConfirmTip(TIP_ORDER_ID, riderA.id))
    }

    @Test
    fun `REG-PAY-TIP-018 confirmar deja historial confirmado y suma exactamente 100 una vez`() {
        val c = fixtureDeclaredTip100()
        assertTrue(c.authenticateRider(riderA.id, passwordA))
        val before = c.riderCurrentBalance(riderA.id)

        assertTrue(c.confirmTip(TIP_ORDER_ID, riderA.id))

        val tip = c.riderDigitalTipForOrder(TIP_ORDER_ID, riderA.id)
        assertNotNull(tip)
        assertEquals(100, tip!!.tipAmount)
        assertEquals(TipStatus.CONFIRMED, tip.tipStatus)
        assertTrue(c.pendingTipsForRider(riderA.id).isEmpty())
        assertEquals(before + 100, c.riderCurrentBalance(riderA.id))
        assertEquals(1, c.order(TIP_ORDER_ID)!!.events.count { it.type == OrderEventType.TIP_TRANSFER_CONFIRMED })
    }

    @Test
    fun `REG-PAY-TIP-019 reinicio y relogin conservan CONFIRMED sin perder ni duplicar`() {
        val first = fixtureDeclaredTip100()
        assertTrue(first.authenticateRider(riderA.id, passwordA))
        assertTrue(first.confirmTip(TIP_ORDER_ID, riderA.id))
        val balanceAfterConfirm = first.riderCurrentBalance(riderA.id)
        first.logoutRider()

        val restarted = MandadosController(context)
        assertTrue(restarted.pendingTipsForRider(riderA.id).isEmpty())
        assertNull(restarted.riderDigitalTipForOrder(TIP_ORDER_ID, riderA.id))
        assertEquals(0, restarted.riderCurrentBalance(riderA.id))

        assertTrue(restarted.authenticateRider(riderA.id, passwordA))
        val restored = restarted.riderDigitalTipForOrder(TIP_ORDER_ID, riderA.id)
        assertNotNull(restored)
        assertEquals(TipStatus.CONFIRMED, restored!!.tipStatus)
        assertEquals(100, restored.tipAmount)
        assertTrue(restarted.pendingTipsForRider(riderA.id).isEmpty())
        assertEquals(balanceAfterConfirm, restarted.riderCurrentBalance(riderA.id))
        assertEquals(1, restarted.order(TIP_ORDER_ID)!!.events.count { it.type == OrderEventType.TIP_TRANSFER_CONFIRMED })
    }

    @Test
    fun `REG-PAY-TIP-020 doble confirmacion no duplica saldo evento ni efecto financiero`() {
        val c = fixtureDeclaredTip100()
        assertTrue(c.authenticateRider(riderA.id, passwordA))
        val before = c.riderCurrentBalance(riderA.id)

        assertTrue(c.confirmTip(TIP_ORDER_ID, riderA.id))
        val afterFirst = c.riderCurrentBalance(riderA.id)
        val firstEvents = c.order(TIP_ORDER_ID)!!.events.count { it.type == OrderEventType.TIP_TRANSFER_CONFIRMED }

        assertTrue(c.confirmTip(TIP_ORDER_ID, riderA.id))
        val afterSecond = c.riderCurrentBalance(riderA.id)
        val secondEvents = c.order(TIP_ORDER_ID)!!.events.count { it.type == OrderEventType.TIP_TRANSFER_CONFIRMED }

        assertEquals(before + 100, afterFirst)
        assertEquals(afterFirst, afterSecond)
        assertEquals(1, firstEvents)
        assertEquals(1, secondEvents)
        assertEquals(TipStatus.CONFIRMED, c.ratings.single { it.orderId == TIP_ORDER_ID }.tipStatus)
    }

    @Test
    fun `REG-PAY-TIP-021 Rider B no ve ni confirma propina de Rider A`() {
        val c = fixtureDeclaredTip100()
        assertTrue(c.authenticateRider(riderB.id, passwordB))

        assertTrue(c.pendingTipsForRider(riderB.id).isEmpty())
        assertNull(c.riderDigitalTipForOrder(TIP_ORDER_ID, riderB.id))
        assertFalse(c.riderCanConfirmTip(TIP_ORDER_ID, riderB.id))
        assertFalse(c.confirmTip(TIP_ORDER_ID, riderB.id))
        assertEquals(TipStatus.TRANSFER_DECLARED, c.ratings.single { it.orderId == TIP_ORDER_ID }.tipStatus)
        assertEquals(0, c.order(TIP_ORDER_ID)!!.events.count { it.type == OrderEventType.TIP_TRANSFER_CONFIRMED })
    }

    @Test
    fun `REG-RIDER-AUTH-001 sin sesion Rider las acciones self service fallan cerrado`() {
        seedRidersAndCredentials()
        val shift = allDayShift()
        seedV2(listOf(shift))
        val pendingOrder = pendingOrder("P25-AUTH-NOSESSION")
        LocalStore(context).saveOrders(listOf(pendingOrder))
        val c = MandadosController(context)

        assertFalse(c.hasAuthenticatedRiderSession(riderA.id))
        assertFalse(c.setRiderAvailable(riderA.id, true))
        assertFalse(c.updateRiderTransferAlias(riderA.id, "alias.a"))
        assertFalse(c.reserveShift(riderA.id, shift.id))
        assertFalse(c.takeOrder(pendingOrder.id, riderA.id))
        assertFalse(c.confirmTip("NO-EXISTE", riderA.id))
        assertEquals(0, c.riderCurrentBalance(riderA.id))
    }

    @Test
    fun `REG-RIDER-AUTH-002 sesion Rider A no opera recursos de Rider B`() {
        seedRidersAndCredentials()
        val shift = allDayShift()
        seedV2(listOf(shift))
        val c = MandadosController(context)
        assertTrue(c.authenticateRider(riderA.id, passwordA))

        assertFalse(c.setRiderAvailable(riderB.id, true))
        assertFalse(c.updateRiderTransferAlias(riderB.id, "alias.b"))
        assertFalse(c.reserveShift(riderB.id, shift.id))
        assertFalse(c.changeRiderPassword(riderB.id, passwordB, "NuevoB123"))
        assertEquals("", c.rider(riderB.id)!!.transferAlias)
    }

    @Test
    fun `REG-PAY-AUTH-002 conocer RID asignado no alcanza para comprobante confirmar ni reportar`() {
        seedRidersAndCredentials()
        val order = completedTransferOrder("P25-PAY-AUTH", riderA.id)
        val payment = PaymentRecord(
            id = "PAY-${order.id}",
            orderId = order.id,
            riderId = riderA.id,
            channel = PaymentChannel.RIDER_TRANSFER,
            expectedAmount = order.totalAmount ?: 0,
            status = PaymentStatus.PROOF_UPLOADED,
            proofUri = "content://proof/rider-a",
            createdAt = nowText(),
            updatedAt = nowText()
        )
        val store = LocalStore(context)
        store.saveOrders(listOf(order))
        store.savePayments(listOf(payment))
        val c = MandadosController(context)

        assertNull(c.riderPaymentForOrder(order.id, riderA.id))
        assertFalse(c.riderCanAccessTransfer(order.id, riderA.id))
        assertFalse(c.riderCanConfirmTransfer(order.id, riderA.id))
        assertFalse(c.riderCanReportTransfer(order.id, riderA.id))

        assertTrue(c.authenticateRider(riderA.id, passwordA))
        assertEquals("content://proof/rider-a", c.riderPaymentForOrder(order.id, riderA.id)?.proofUri)
        assertTrue(c.riderCanAccessTransfer(order.id, riderA.id))
        assertTrue(c.riderCanConfirmTransfer(order.id, riderA.id))

        c.logoutRider()
        assertTrue(c.authenticateRider(riderB.id, passwordB))
        assertNull(c.riderPaymentForOrder(order.id, riderA.id))
        assertNull(c.riderPaymentForOrder(order.id, riderB.id))
        assertFalse(c.riderCanConfirmTransfer(order.id, riderA.id))
        assertFalse(c.riderCanReportTransfer(order.id, riderA.id))
    }

    @Test
    fun `REG-SHIFT-AUTH-001 Rider A no reserva ni cancela turnos como Rider B`() {
        seedRidersAndCredentials()
        val shift = allDayShift()
        val reservation = ConcreteShiftReservation(
            id = "CSR-B",
            riderId = riderB.id,
            concreteShiftId = shift.id,
            joinedAt = nowText(),
            status = ShiftReservationStatus.RESERVED
        )
        seedV2(listOf(shift), listOf(reservation))
        val c = MandadosController(context)
        assertTrue(c.authenticateRider(riderA.id, passwordA))

        assertFalse(c.reserveShift(riderB.id, shift.id))
        assertFalse(c.cancelConcreteShift(reservation.id))
        assertEquals(
            ShiftReservationStatus.RESERVED,
            c.concreteShiftReservations.single { it.id == reservation.id }.status
        )
    }

    @Test
    fun `REG-ORDER-AUTH-001 Rider A no toma ni modifica pedidos actuando como Rider B`() {
        seedRidersAndCredentials()
        val pending = pendingOrder("P25-PENDING")
        val assignedToB = pendingOrder("P25-RIDER-B").copy(
            status = OrderStatus.ACCEPTED,
            assignedRiderId = riderB.id
        )
        LocalStore(context).saveOrders(listOf(pending, assignedToB))
        val c = MandadosController(context)
        assertTrue(c.authenticateRider(riderA.id, passwordA))

        assertFalse(c.takeOrder(pending.id, riderB.id))
        assertFalse(c.updateOrderStatus(assignedToB.id, OrderStatus.IN_PROGRESS, actor = riderB.id))
        assertEquals(OrderStatus.ACCEPTED, c.order(assignedToB.id)!!.status)

        assertTrue(c.updateOrderStatus(assignedToB.id, OrderStatus.IN_PROGRESS, actor = "ADMIN"))
        val last = c.order(assignedToB.id)!!.events.last()
        assertEquals("ADMIN", last.actor)
        assertEquals(OrderStatus.IN_PROGRESS, c.order(assignedToB.id)!!.status)
    }

    private fun fixtureDeclaredTip100(): MandadosController {
        seedRidersAndCredentials()
        val order = completedTransferOrder(TIP_ORDER_ID, riderA.id)
        val payment = PaymentRecord(
            id = "PAY-${order.id}",
            orderId = order.id,
            riderId = riderA.id,
            channel = PaymentChannel.RIDER_TRANSFER,
            expectedAmount = order.totalAmount ?: 0,
            status = PaymentStatus.CONFIRMED,
            proofUri = "content://service-payment-proof",
            createdAt = "05/10/2026 02:00:00",
            updatedAt = "05/10/2026 02:05:00"
        )
        val store = LocalStore(context)
        store.saveCustomer(customer)
        store.saveOrders(listOf(order))
        store.savePayments(listOf(payment))

        val c = MandadosController(context)
        assertEquals(PaymentStatus.CONFIRMED, c.paymentForOrder(order.id)?.status)
        assertTrue(c.submitRating(order.id, 5, listOf("Amable"), "", 100))
        assertEquals(TipStatus.SELECTED, c.ratings.single { it.orderId == order.id }.tipStatus)
        assertEquals(100, c.ratings.single { it.orderId == order.id }.tipAmount)
        assertTrue(c.declareTipTransfer(order.id))
        assertEquals(TipStatus.TRANSFER_DECLARED, c.ratings.single { it.orderId == order.id }.tipStatus)
        assertEquals(1, c.order(order.id)!!.events.count { it.type == OrderEventType.TIP_TRANSFER_DECLARED })
        return c
    }

    private fun seedRidersAndCredentials() {
        val store = LocalStore(context)
        store.saveRiders(listOf(riderA, riderB))
        store.saveRiderCredentials(
            listOf(
                testCredential(riderA.id, passwordA),
                testCredential(riderB.id, passwordB)
            )
        )
    }

    private fun seedV2(shifts: List<ConcreteShift>, reservations: List<ConcreteShiftReservation> = emptyList()) {
        val v2 = ShiftStoreV2(context)
        assertTrue(v2.initializeIfNeeded().success)
        assertTrue(v2.saveAll(emptyList(), shifts, reservations, emptyList()))
    }

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

    private fun completedTransferOrder(id: String, riderId: String): LocalOrder = LocalOrder(
        id = id,
        createdAt = "05/10/2026 01:30:00",
        serviceType = ServiceType.DELIVERY,
        category = ServiceCategory.SHIPMENT,
        operationMode = OperationMode.MULTI_RIDER,
        status = OrderStatus.COMPLETED,
        customerName = customer.name,
        customerPhone = customer.displayPhone,
        customerId = customer.id,
        detail = "Fixture transferencia",
        baseAmount = 2500,
        baseZoneName = "Urbana",
        prePickupAmount = 0,
        rainAmount = 0,
        totalAmount = 2500,
        whatsappMessage = "",
        assignedRiderId = riderId,
        deliveryPayment = DeliveryPaymentMethod.TRANSFER,
        events = listOf(
            OrderEvent(
                type = OrderEventType.RIDER_ASSIGNED,
                at = "05/10/2026 01:35:00",
                status = OrderStatus.PENDING,
                riderId = riderId,
                note = "Fixture",
                actor = "ADMIN"
            ),
            OrderEvent(
                type = OrderEventType.COMPLETED,
                at = "05/10/2026 02:00:00",
                status = OrderStatus.COMPLETED,
                riderId = riderId,
                note = "Fixture completado",
                actor = riderId
            )
        )
    )

    private fun pendingOrder(id: String): LocalOrder = LocalOrder(
        id = id,
        createdAt = nowText(),
        serviceType = ServiceType.DELIVERY,
        category = ServiceCategory.ERRAND,
        operationMode = OperationMode.MULTI_RIDER,
        status = OrderStatus.PENDING,
        customerName = customer.name,
        customerPhone = customer.displayPhone,
        customerId = customer.id,
        detail = "Pedido pendiente",
        baseAmount = 1500,
        baseZoneName = "Urbana",
        prePickupAmount = 0,
        rainAmount = 0,
        totalAmount = 1500,
        whatsappMessage = "",
        deliveryPayment = DeliveryPaymentMethod.CASH
    )

    private fun allDayShift(): ConcreteShift = ConcreteShift(
        id = "CS-ALL-DAY",
        serviceDate = ShiftSchedulePolicy.toIsoDate(LocalDate.now()),
        startMinute = 0,
        endMinute = 24 * 60,
        capacity = 2,
        enabled = true
    )

    private fun nowText(): String = LocalDateTime.now().format(stamp)

    companion object {
        private const val TIP_ORDER_ID = "P25-TIP-100"
    }
}
