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
class RiderEligibilityBoundaryRegressionTest {
    private lateinit var context: Context
    private val password = "RiderA123"

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("mandados_alpha1", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `REG-RIDER-ELIG-010 pendiente no usa excepcion de pedido previamente asignado`() {
        val rider = pendingRider("RID-PENDING-ASSIGNED")
        val store = LocalStore(context)
        store.saveRiders(listOf(rider))
        store.saveOrders(listOf(assignedOrder("ASSIGNED-PENDING", rider.id)))

        val c = MandadosController(context)
        val invitation = c.createRiderInvitation(rider.id)!!
        assertEquals(rider.id, c.redeemRiderInvitation(invitation.code, password))
        assertTrue(c.hasAuthenticatedRiderSession(rider.id))
        assertEquals(RiderDenialReason.PENDING_APPROVAL, c.riderExistingOrderContinuationDecision(rider.id).reason)

        assertFalse(c.updateOrderStatus("ASSIGNED-PENDING", OrderStatus.IN_PROGRESS, actor = rider.id))
        assertEquals(OrderStatus.ACCEPTED, c.order("ASSIGNED-PENDING")!!.status)
    }

    @Test
    fun `REG-RIDER-DENIAL-002 wrappers publican motivo tipado y mensaje utilizable`() {
        val rider = pendingRider("RID-PENDING-FEEDBACK")
        val store = LocalStore(context)
        store.saveRiders(listOf(rider))

        val c = MandadosController(context)
        val invitation = c.createRiderInvitation(rider.id)!!
        assertEquals(rider.id, c.redeemRiderInvitation(invitation.code, password))

        assertFalse(c.setRiderAvailable(rider.id, true))
        val feedback = c.riderDenialFeedback!!
        assertEquals(RiderDenialReason.PENDING_APPROVAL, feedback.reason)
        assertTrue(riderDenialMessage(feedback).isNotBlank())

        c.clearRiderDenialFeedback()
        assertEquals(null, c.riderDenialFeedback)
    }

    @Test
    fun `REG-RIDER-AUTH-003 lectura de pedidos activos respeta sesion Rider A B`() {
        val riderA = pendingRider("RID-ACTIVE-A")
        val riderB = pendingRider("RID-ACTIVE-B").copy(phone = "2345555002")
        val store = LocalStore(context)
        store.saveRiders(listOf(riderA, riderB))
        store.saveOrders(
            listOf(
                assignedOrder("ACTIVE-A", riderA.id),
                assignedOrder("ACTIVE-B", riderB.id)
            )
        )

        val c = MandadosController(context)
        val invitation = c.createRiderInvitation(riderA.id)!!
        assertEquals(riderA.id, c.redeemRiderInvitation(invitation.code, password))

        assertEquals(listOf("ACTIVE-A"), authenticatedRiderActiveOrders(c, riderA.id).map { it.id })
        assertTrue(authenticatedRiderActiveOrders(c, riderB.id).isEmpty())
    }

    private fun pendingRider(id: String): RiderProfile = RiderProfile(
        id = id,
        name = "Rider Pendiente",
        phone = "2345555001",
        vehicleType = VehicleType.BICYCLE,
        documents = RiderDocuments(
            dniFrontUri = "content://dni/front",
            dniBackUri = "content://dni/back"
        ),
        approvalStatus = RiderApprovalStatus.PENDING,
        documentReviews = mapOf(
            RiderDocumentKey.DNI_FRONT to DocumentReviewStatus.APPROVED,
            RiderDocumentKey.DNI_BACK to DocumentReviewStatus.APPROVED
        ),
        active = true
    )

    private fun assignedOrder(id: String, riderId: String): LocalOrder = LocalOrder(
        id = id,
        createdAt = "05/10/2026 06:00:00",
        serviceType = ServiceType.DELIVERY,
        status = OrderStatus.ACCEPTED,
        customerName = "Cliente Test",
        customerPhone = "2345-555555",
        detail = "Pedido asignado",
        baseAmount = 1000,
        baseZoneName = "Urbana",
        prePickupAmount = 0,
        rainAmount = 0,
        totalAmount = 1000,
        whatsappMessage = "",
        assignedRiderId = riderId
    )
}
