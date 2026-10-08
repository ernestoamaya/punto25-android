package ar.com.mandados.app

import android.content.Context
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RiderOrderLocationAuthorizationTest {
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
    fun `REG-ORDER-LOCATION-RIDER-AUTH-001 solo Rider autenticado asignado obtiene ubicaciones`() {
        val riderA = rider("RID-LOC-A")
        val riderB = rider("RID-LOC-B")
        val order = order(riderA.id).copy(
            originLocation = GeoPoint(-35.432471, -60.171559),
            destinationLocation = GeoPoint(-35.430100, -60.170200)
        )
        seed(
            riders = listOf(riderA, riderB),
            credentials = listOf(testCredential(riderA.id, passwordA), testCredential(riderB.id, passwordB)),
            orders = listOf(order)
        )
        val c = MandadosController(context)

        assertTrue(c.authenticateRider(riderA.id, passwordA))
        assertEquals(2, authenticatedRiderOrderLocations(c, riderA.id, order.id).size)
        assertTrue(authenticatedRiderOrderLocations(c, riderB.id, order.id).isEmpty())

        c.logoutRider()
        assertTrue(authenticatedRiderOrderLocations(c, riderA.id, order.id).isEmpty())

        assertTrue(c.authenticateRider(riderB.id, passwordB))
        assertTrue(authenticatedRiderOrderLocations(c, riderB.id, order.id).isEmpty())
    }

    private fun seed(
        riders: List<RiderProfile>,
        credentials: List<RiderCredential>,
        orders: List<LocalOrder>
    ) {
        val store = LocalStore(context)
        store.saveRiders(riders)
        store.saveRiderCredentials(credentials)
        store.saveOrders(orders)
        val shifts = ShiftStoreV2(context)
        assertTrue(shifts.initializeIfNeeded().success)
        assertTrue(shifts.saveAll(emptyList(), emptyList(), emptyList(), emptyList()))
    }

    private fun rider(id: String): RiderProfile = RiderProfile(
        id = id,
        name = id,
        phone = "2345555001",
        approvalStatus = RiderApprovalStatus.APPROVED,
        active = true
    )

    private fun order(riderId: String): LocalOrder = LocalOrder(
        id = "ORDER-RIDER-LOCATION",
        createdAt = LocalDateTime.now().format(stamp),
        serviceType = ServiceType.DELIVERY,
        category = ServiceCategory.ERRAND,
        operationMode = OperationMode.MULTI_RIDER,
        status = OrderStatus.IN_PROGRESS,
        customerName = "Cliente",
        customerPhone = "2345555999",
        detail = "Legacy",
        baseAmount = 1000,
        baseZoneName = "Urbana",
        prePickupAmount = 0,
        rainAmount = 0,
        totalAmount = 1000,
        whatsappMessage = "",
        assignedRiderId = riderId
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
