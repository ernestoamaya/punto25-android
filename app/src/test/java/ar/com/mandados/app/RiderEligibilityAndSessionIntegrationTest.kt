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
class RiderEligibilityAndSessionIntegrationTest {
    private lateinit var context: Context
    private val stamp = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
    private val passwordA = "RiderA123"
    private val passwordB = "RiderB123"

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("mandados_alpha1", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `REG-RIDER-ELIG-001 fixture Rider persistido pre dev3_8 apto opera sobre ConcreteShift v2`() {
        val rider = approvedRider("RID-LEGACY", "Rider Legacy")
        seed(riders = listOf(rider), credentials = listOf(testCredential(rider.id, passwordA)), shifts = listOf(allDayShift()))
        val c = MandadosController(context)

        assertTrue(c.authenticateRider(rider.id, passwordA))
        assertTrue(c.riderCanViewShiftsDecision(rider.id).allowed)
        assertTrue(c.reserveShift(rider.id, allDayShift().id))
        assertTrue(c.setRiderAvailable(rider.id, true))
        assertTrue(c.isRiderCurrentlyAvailable(rider.id))
    }

    @Test
    fun `REG-RIDER-ELIG-002 Rider creado actualmente activo aprobado y docs aprobadas opera`() {
        val first = MandadosController(context)
        val docs = approvedDocuments()
        val riderId = first.saveRider(
            id = null,
            name = "Rider Nuevo",
            phone = "2345555100",
            birthDate = "01/01/1990",
            vehicleType = VehicleType.MOTORCYCLE,
            address = "25 de Mayo",
            documents = docs
        )!!
        RiderDocumentKey.entries.forEach { key ->
            first.setRiderDocumentReview(riderId, key, DocumentReviewStatus.APPROVED)
        }
        first.setRiderApprovalStatus(riderId, RiderApprovalStatus.APPROVED)
        LocalStore(context).saveRiderCredentials(listOf(testCredential(riderId, passwordA)))
        assertTrue(ShiftStoreV2(context).saveConcreteShifts(listOf(allDayShift())))

        val c = MandadosController(context)
        assertTrue(c.authenticateRider(riderId, passwordA))
        assertTrue(c.riderCanViewShiftsDecision(riderId).allowed)
        assertTrue(c.reserveShift(riderId, allDayShift().id))
        assertTrue(c.setRiderAvailable(riderId, true))
    }

    @Test
    fun `REG-RIDER-ELIG-003 sesion Rider A nunca opera como Rider B`() {
        val a = approvedRider("RID-A", "Rider A")
        val b = approvedRider("RID-B", "Rider B")
        seed(listOf(a, b), listOf(testCredential(a.id, passwordA), testCredential(b.id, passwordB)), listOf(allDayShift()))
        val c = MandadosController(context)
        assertTrue(c.authenticateRider(a.id, passwordA))

        assertEquals(RiderDenialReason.SESSION_REQUIRED, c.riderOperationalEligibility(b.id).reason)
        assertEquals(RiderDenialReason.SESSION_REQUIRED, c.reserveShiftWithDecision(b.id, allDayShift().id).reason)
        assertEquals(RiderDenialReason.SESSION_REQUIRED, c.setRiderAvailableWithDecision(b.id, true).reason)
        assertFalse(c.updateRiderTransferAlias(b.id, "alias.b"))
    }

    @Test
    fun `REG-RIDER-ELIG-004 documento pendiente permite login pero bloquea trabajo nuevo con motivo`() {
        val rider = approvedRider("RID-PENDING-DOC", "Rider Pendiente").withReview(
            RiderDocumentKey.INSURANCE_CARD,
            DocumentReviewStatus.PENDING
        )
        val c = controllerFor(rider)
        assertTrue(c.authenticateRider(rider.id, passwordA))

        assertDeniedEverywhere(c, rider.id, RiderDenialReason.DOCUMENT_PENDING)
    }

    @Test
    fun `REG-RIDER-ELIG-005 documento rechazado permite login pero bloquea trabajo nuevo con motivo`() {
        val rider = approvedRider("RID-REJECTED-DOC", "Rider Rechazado").withReview(
            RiderDocumentKey.DRIVER_LICENSE_FRONT,
            DocumentReviewStatus.REJECTED
        )
        val c = controllerFor(rider)
        assertTrue(c.authenticateRider(rider.id, passwordA))

        assertDeniedEverywhere(c, rider.id, RiderDenialReason.DOCUMENT_REJECTED)
    }

    @Test
    fun `REG-RIDER-ELIG-006 documento faltante permite login pero bloquea trabajo nuevo con motivo`() {
        val base = approvedRider("RID-MISSING-DOC", "Rider Faltante")
        val rider = base.copy(
            documents = base.documents.copy(insuranceCardUri = null),
            documentReviews = base.documentReviews + (RiderDocumentKey.INSURANCE_CARD to DocumentReviewStatus.NOT_UPLOADED)
        )
        val c = controllerFor(rider)
        assertTrue(c.authenticateRider(rider.id, passwordA))

        assertDeniedEverywhere(c, rider.id, RiderDenialReason.DOCUMENT_NOT_UPLOADED)
    }

    @Test
    fun `REG-RIDER-ELIG-007 suspendido entra y consulta pero no accede a trabajo nuevo`() {
        val rider = approvedRider("RID-SUSP", "Rider Suspendido").copy(approvalStatus = RiderApprovalStatus.SUSPENDED)
        val completed = order("DONE-SUSP", rider.id, OrderStatus.COMPLETED)
        val c = controllerFor(rider, orders = listOf(completed))
        assertTrue(c.authenticateRider(rider.id, passwordA))

        assertEquals(1, c.riderCompletedOrders(rider.id).size)
        assertDeniedEverywhere(c, rider.id, RiderDenialReason.SUSPENDED)
    }

    @Test
    fun `REG-RIDER-LOGIN-001 desactivado con password correcto devuelve DEACTIVATED sin sesion`() {
        val rider = approvedRider("RID-OFF", "Rider Desactivado").copy(active = false)
        val c = controllerFor(rider)

        val result = c.authenticateRiderResult(rider.id, passwordA)

        assertEquals(RiderAuthenticationStatus.DEACTIVATED, result.status)
        assertEquals(rider.name, result.riderName)
        assertFalse(c.hasAuthenticatedRiderSession(rider.id))
    }

    @Test
    fun `REG-RIDER-LOGIN-002 desactivado con password incorrecto no revela estado`() {
        val rider = approvedRider("RID-OFF-BAD", "Rider Oculto").copy(active = false)
        val c = controllerFor(rider)

        val result = c.authenticateRiderResult(rider.id, "PasswordIncorrecta123")

        assertEquals(RiderAuthenticationStatus.INVALID_CREDENTIALS, result.status)
        assertNull(result.riderId)
        assertNull(result.riderName)
        assertFalse(c.hasAuthenticatedRiderSession(rider.id))
    }

    @Test
    fun `REG-RIDER-LOGIN-003 invitacion de Rider desactivado no crea credencial ni sesion`() {
        val rider = approvedRider("RID-INV-OFF", "Rider Invitacion")
        seed(listOf(rider), emptyList(), emptyList())
        val c = MandadosController(context)
        val invitation = c.createRiderInvitation(rider.id)!!
        c.setRiderActive(rider.id, false)

        assertNull(c.redeemRiderInvitation(invitation.code, passwordA))
        assertFalse(c.hasRiderCredential(rider.id))
        assertFalse(c.hasAuthenticatedRiderSession(rider.id))
    }

    @Test
    fun `REG-RIDER-SESSION-003 desactivacion invalida inmediatamente toda capacidad Rider`() {
        val rider = approvedRider("RID-LIVE-OFF", "Rider Live")
        val assigned = order("ACTIVE-OFF", rider.id, OrderStatus.IN_PROGRESS)
        val c = controllerFor(rider, orders = listOf(assigned))
        assertTrue(c.authenticateRider(rider.id, passwordA))
        c.setRiderActive(rider.id, false)

        assertFalse(c.hasAuthenticatedRiderSession(rider.id))
        assertFalse(c.updateRiderTransferAlias(rider.id, "alias"))
        assertFalse(c.updateOrderStatus(assigned.id, OrderStatus.COMPLETED, actor = rider.id))
        assertEquals(0, c.riderCurrentBalance(rider.id))
    }

    @Test
    fun `REG-RIDER-SESSION-004 recreation no restaura workspace self service sin sesion`() {
        val rider = approvedRider("RID-RECREATE", "Rider Recreation")
        val first = controllerFor(rider)
        assertTrue(first.authenticateRider(rider.id, passwordA))
        assertTrue(riderWorkspaceSessionValid(first, rider.id))

        val recreated = MandadosController(context)
        assertFalse(riderWorkspaceSessionValid(recreated, rider.id))
        assertFalse(recreated.hasAuthenticatedRiderSession(rider.id))
    }

    @Test
    fun `REG-RIDER-HISTORY-002 contador cuenta COMPLETED e historial preserva finales sin fuga A B`() {
        val a = approvedRider("RID-HIST-A", "Rider Historia A")
        val b = approvedRider("RID-HIST-B", "Rider Historia B")
        val orders = listOf(
            order("A-DONE", a.id, OrderStatus.COMPLETED),
            order("A-CANCEL", a.id, OrderStatus.CANCELLED),
            order("A-REJECT", a.id, OrderStatus.REJECTED),
            order("B-DONE", b.id, OrderStatus.COMPLETED)
        )
        seed(listOf(a, b), listOf(testCredential(a.id, passwordA), testCredential(b.id, passwordB)), emptyList(), orders)
        val c = MandadosController(context)
        assertTrue(c.authenticateRider(a.id, passwordA))

        assertEquals(listOf("A-DONE"), c.riderCompletedOrders(a.id).map { it.id })
        assertEquals(setOf("A-DONE", "A-CANCEL", "A-REJECT"), c.riderHistoryOrders(a.id).map { it.id }.toSet())
        assertTrue(c.riderHistoryOrders(b.id).isEmpty())
    }

    @Test
    fun `REG-RIDER-DENIAL-001 acciones denegadas entregan motivo tipado`() {
        val rider = approvedRider("RID-DENIAL", "Rider Denial").withReview(
            RiderDocumentKey.DNI_BACK,
            DocumentReviewStatus.PENDING
        )
        val c = controllerFor(rider)
        assertTrue(c.authenticateRider(rider.id, passwordA))

        val shiftDecision = c.reserveShiftWithDecision(rider.id, allDayShift().id)
        val availabilityDecision = c.setRiderAvailableWithDecision(rider.id, true)
        val ordersDecision = c.riderCanAccessNewOrdersDecision(rider.id)

        assertFalse(shiftDecision.allowed)
        assertEquals(RiderDenialReason.DOCUMENT_PENDING, shiftDecision.reason)
        assertNotNull(shiftDecision.documentKey)
        assertEquals(RiderDenialReason.DOCUMENT_PENDING, availabilityDecision.reason)
        assertEquals(RiderDenialReason.DOCUMENT_PENDING, ordersDecision.reason)
    }

    @Test
    fun `REG-RIDER-ADMIN-001 acciones Admin no crean sesion Rider ni habilitan self service`() {
        val rider = approvedRider("RID-ADMIN", "Rider Admin")
        seed(listOf(rider), listOf(testCredential(rider.id, passwordA)), listOf(allDayShift()))
        val c = MandadosController(context)

        assertTrue(c.adminAddRiderToShift(rider.id, allDayShift().id))
        assertFalse(c.hasAuthenticatedRiderSession(rider.id))
        assertFalse(riderWorkspaceSessionValid(c, rider.id))
        assertFalse(c.setRiderAvailable(rider.id, true))
    }

    @Test
    fun `REG-RIDER-ELIG-008 suspendido puede finalizar pedido ya asignado pero no tomar uno nuevo`() {
        val rider = approvedRider("RID-SUSP-ACTIVE", "Rider Suspendido Activo").copy(approvalStatus = RiderApprovalStatus.SUSPENDED)
        val assigned = order("ASSIGNED-SUSP", rider.id, OrderStatus.ACCEPTED)
        val pending = order("NEW-SUSP", null, OrderStatus.PENDING)
        val c = controllerFor(rider, orders = listOf(assigned, pending))
        assertTrue(c.authenticateRider(rider.id, passwordA))

        assertTrue(c.updateOrderStatus(assigned.id, OrderStatus.IN_PROGRESS, actor = rider.id))
        assertTrue(c.updateOrderStatus(assigned.id, OrderStatus.COMPLETED, actor = rider.id))
        assertFalse(c.takeOrder(pending.id, rider.id))
    }

    @Test
    fun `REG-RIDER-ELIG-009 docs no aptas permiten finalizar asignado pero no tomar nuevo`() {
        val rider = approvedRider("RID-DOC-ACTIVE", "Rider Docs Activo").withReview(
            RiderDocumentKey.INSURANCE_CARD,
            DocumentReviewStatus.REJECTED
        )
        val assigned = order("ASSIGNED-DOC", rider.id, OrderStatus.IN_PROGRESS)
        val pending = order("NEW-DOC", null, OrderStatus.PENDING)
        val c = controllerFor(rider, orders = listOf(assigned, pending))
        assertTrue(c.authenticateRider(rider.id, passwordA))

        assertTrue(c.updateOrderStatus(assigned.id, OrderStatus.COMPLETED, actor = rider.id))
        assertFalse(c.takeOrder(pending.id, rider.id))
    }

    @Test
    fun `REG-RIDER-SESSION-005 desactivado no finaliza pedido previamente asignado`() {
        val rider = approvedRider("RID-OFF-ACTIVE", "Rider Off Activo")
        val assigned = order("ASSIGNED-OFF", rider.id, OrderStatus.IN_PROGRESS)
        val c = controllerFor(rider, orders = listOf(assigned))
        assertTrue(c.authenticateRider(rider.id, passwordA))
        c.setRiderActive(rider.id, false)

        assertFalse(c.updateOrderStatus(assigned.id, OrderStatus.COMPLETED, actor = rider.id))
        assertEquals(OrderStatus.IN_PROGRESS, c.order(assigned.id)!!.status)
    }

    private fun assertDeniedEverywhere(c: MandadosController, riderId: String, reason: RiderDenialReason) {
        assertEquals(reason, c.riderCanViewShiftsDecision(riderId).reason)
        assertEquals(reason, c.reserveShiftWithDecision(riderId, allDayShift().id).reason)
        assertEquals(reason, c.setRiderAvailableWithDecision(riderId, true).reason)
        assertEquals(reason, c.riderCanAccessNewOrdersDecision(riderId).reason)
    }

    private fun controllerFor(rider: RiderProfile, orders: List<LocalOrder> = emptyList()): MandadosController {
        seed(
            riders = listOf(rider),
            credentials = listOf(testCredential(rider.id, passwordA)),
            shifts = listOf(allDayShift()),
            orders = orders
        )
        return MandadosController(context)
    }

    private fun seed(
        riders: List<RiderProfile>,
        credentials: List<RiderCredential>,
        shifts: List<ConcreteShift>,
        orders: List<LocalOrder> = emptyList()
    ) {
        val store = LocalStore(context)
        store.saveRiders(riders)
        store.saveRiderCredentials(credentials)
        store.saveOrders(orders)
        val v2 = ShiftStoreV2(context)
        assertTrue(v2.initializeIfNeeded().success)
        assertTrue(v2.saveAll(emptyList(), shifts, emptyList(), emptyList()))
    }

    private fun approvedRider(id: String, name: String): RiderProfile = RiderProfile(
        id = id,
        name = name,
        phone = "2345555001",
        vehicleType = VehicleType.MOTORCYCLE,
        documents = approvedDocuments(),
        approvalStatus = RiderApprovalStatus.APPROVED,
        documentReviews = RiderDocumentKey.entries.associateWith { DocumentReviewStatus.APPROVED },
        active = true,
        available = false
    )

    private fun RiderProfile.withReview(key: RiderDocumentKey, status: DocumentReviewStatus): RiderProfile =
        copy(documentReviews = documentReviews + (key to status))

    private fun approvedDocuments(): RiderDocuments = RiderDocuments(
        dniFrontUri = "content://docs/dni-front",
        dniBackUri = "content://docs/dni-back",
        motorcyclePlateUri = "content://docs/plate",
        driverLicenseFrontUri = "content://docs/license-front",
        driverLicenseBackUri = "content://docs/license-back",
        vehicleCardUri = "content://docs/vehicle-card",
        insuranceCardUri = "content://docs/insurance"
    )

    private fun allDayShift(): ConcreteShift = ConcreteShift(
        id = "CS-ALL-DAY",
        serviceDate = ShiftSchedulePolicy.toIsoDate(LocalDate.now()),
        startMinute = 0,
        endMinute = 24 * 60,
        capacity = 4,
        enabled = true
    )

    private fun order(id: String, riderId: String?, status: OrderStatus): LocalOrder = LocalOrder(
        id = id,
        createdAt = LocalDateTime.now().format(stamp),
        serviceType = ServiceType.DELIVERY,
        category = ServiceCategory.ERRAND,
        operationMode = OperationMode.MULTI_RIDER,
        status = status,
        customerName = "Cliente",
        customerPhone = "2345555999",
        detail = "Fixture",
        baseAmount = 1000,
        baseZoneName = "Urbana",
        prePickupAmount = 0,
        rainAmount = 0,
        totalAmount = 1000,
        whatsappMessage = "",
        assignedRiderId = riderId,
        events = if (riderId == null) emptyList() else listOf(
            OrderEvent(
                type = OrderEventType.RIDER_ASSIGNED,
                at = LocalDateTime.now().minusMinutes(20).format(stamp),
                status = OrderStatus.PENDING,
                riderId = riderId,
                note = "Fixture",
                actor = "ADMIN"
            )
        )
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
            updatedAt = LocalDateTime.now().format(stamp)
        )
    }
}
