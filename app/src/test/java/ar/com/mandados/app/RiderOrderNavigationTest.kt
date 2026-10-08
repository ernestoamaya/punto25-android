package ar.com.mandados.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.Locale
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
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
class RiderOrderNavigationTest {
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
    fun `REG-RIDER-NAV-AUTH-001 solo Rider autenticado asignado obtiene destinos`() {
        val riderA = rider("RID-NAV-A")
        val riderB = rider("RID-NAV-B")
        val order = order(ServiceType.DELIVERY, riderA.id).copy(
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
        assertEquals(2, authenticatedRiderNavigationDestinations(c, riderA.id, order.id).size)
        assertTrue(authenticatedRiderNavigationDestinations(c, riderB.id, order.id).isEmpty())

        c.logoutRider()
        assertTrue(authenticatedRiderNavigationDestinations(c, riderA.id, order.id).isEmpty())

        assertTrue(c.authenticateRider(riderB.id, passwordB))
        assertTrue(authenticatedRiderNavigationDestinations(c, riderB.id, order.id).isEmpty())
    }

    @Test
    fun `REG-RIDER-NAV-COORD-001 geo URI conserva latitude longitude y es independiente del Locale`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.FRANCE)
            val uri = riderNavigationUri(GeoPoint(-35.432471, -60.171559))

            assertEquals("geo:-35.432471,-60.171559?q=-35.432471,-60.171559", uri.toString())
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `REG-RIDER-NAV-POINTS-001 DELIVERY expone solo retiro y entrega existentes`() {
        val order = order(ServiceType.DELIVERY, "RID-NAV").copy(
            originLocation = GeoPoint(1.0, 2.0),
            destinationLocation = GeoPoint(3.0, 4.0),
            storeLocation = GeoPoint(5.0, 6.0),
            prePickupLocation = GeoPoint(7.0, 8.0)
        )

        assertEquals(
            listOf(RiderOrderNavigationPoint.ORIGIN, RiderOrderNavigationPoint.DESTINATION),
            riderOrderNavigationDestinations(order).map { it.type }
        )
        assertEquals(listOf("ABRIR RETIRO", "ABRIR ENTREGA"), riderOrderNavigationDestinations(order).map { it.type.buttonLabel })
        assertEquals(
            listOf(RiderOrderNavigationPoint.DESTINATION),
            riderOrderNavigationDestinations(order.copy(originLocation = null)).map { it.type }
        )
        assertTrue(riderOrderNavigationDestinations(order.copy(originLocation = null, destinationLocation = null)).isEmpty())
    }

    @Test
    fun `REG-RIDER-NAV-POINTS-001 SHOPPING expone retiro previo comercio y entrega existentes`() {
        val order = order(ServiceType.SHOPPING, "RID-NAV").copy(
            originLocation = GeoPoint(1.0, 2.0),
            prePickupLocation = GeoPoint(3.0, 4.0),
            storeLocation = GeoPoint(5.0, 6.0),
            destinationLocation = GeoPoint(7.0, 8.0)
        )

        assertEquals(
            listOf(
                RiderOrderNavigationPoint.PRE_PICKUP,
                RiderOrderNavigationPoint.STORE,
                RiderOrderNavigationPoint.DESTINATION
            ),
            riderOrderNavigationDestinations(order).map { it.type }
        )
        assertEquals(
            listOf("ABRIR RETIRO PREVIO", "ABRIR COMERCIO", "ABRIR ENTREGA"),
            riderOrderNavigationDestinations(order).map { it.type.buttonLabel }
        )
        assertEquals(
            listOf(RiderOrderNavigationPoint.STORE),
            riderOrderNavigationDestinations(order.copy(prePickupLocation = null, destinationLocation = null)).map { it.type }
        )
    }

    @Test
    fun `REG-RIDER-NAV-READONLY-001 consultar y lanzar navegacion no muta dominio`() {
        val target = rider("RID-READONLY")
        val order = order(ServiceType.DELIVERY, target.id).copy(
            originLocation = GeoPoint(-35.432471, -60.171559),
            destinationLocation = GeoPoint(-35.430100, -60.170200)
        )
        seed(listOf(target), listOf(testCredential(target.id, passwordA)), listOf(order))
        val c = MandadosController(context)
        assertTrue(c.authenticateRider(target.id, passwordA))

        val ordersBefore = c.orders
        val draftBefore = c.draft
        val ridersBefore = c.riders
        val paymentsBefore = c.payments
        val ratingsBefore = c.ratings
        val sessionBefore = c.hasAuthenticatedRiderSession(target.id)

        val destination = authenticatedRiderNavigationDestinations(c, target.id, order.id).first()
        val intent = riderNavigationIntent(destination)
        assertEquals(RiderNavigationLaunchResult.OPENED, launchRiderNavigation(intent) { })

        assertEquals(ordersBefore, c.orders)
        assertEquals(draftBefore, c.draft)
        assertEquals(ridersBefore, c.riders)
        assertEquals(paymentsBefore, c.payments)
        assertEquals(ratingsBefore, c.ratings)
        assertEquals(sessionBefore, c.hasAuthenticatedRiderSession(target.id))
    }

    @Test
    fun `REG-RIDER-NAV-INTENT-001 usa ACTION_VIEW geo sin package fijado`() {
        val destination = RiderOrderNavigationDestination(
            RiderOrderNavigationPoint.ORIGIN,
            GeoPoint(-35.432471, -60.171559)
        )
        val intent = riderNavigationIntent(destination)

        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("geo", intent.data?.scheme)
        assertNull(intent.`package`)
    }

    @Test
    fun `REG-RIDER-NAV-NOHANDLER-001 ActivityNotFound devuelve resultado controlado`() {
        val intent = riderNavigationIntent(
            RiderOrderNavigationDestination(RiderOrderNavigationPoint.DESTINATION, GeoPoint(-35.4, -60.1))
        )

        val result = launchRiderNavigation(intent) { throw ActivityNotFoundException("fixture") }

        assertEquals(RiderNavigationLaunchResult.NO_HANDLER, result)
        val ui = projectFile("app/src/main/java/ar/com/mandados/app/OperationsScreens.kt")
        assertTrue(ui.contains("No hay una aplicación de mapas compatible instalada."))
    }

    @Test
    fun `REG-RIDER-NAV-GOOGLE-001 no introduce Google Maps SDK API key ni package forzado`() {
        val navigation = projectFile("app/src/main/java/ar/com/mandados/app/RiderOrderNavigation.kt")
        val gradle = projectFile("app/build.gradle.kts")
        val manifest = projectFile("app/src/main/AndroidManifest.xml")
        val workflow = projectFile(".github/workflows/alpha-apk.yml")
        val forbidden = listOf(
            "com.google.maps.android",
            "com.google.android.gms.maps",
            "maps-compose",
            "com.google.android.geo.API_KEY",
            "MAPS_API_KEY"
        )

        forbidden.forEach { token ->
            assertFalse("Dependencia Google Maps prohibida: $token", navigation.contains(token))
            assertFalse("Dependencia Google Maps prohibida: $token", gradle.contains(token))
            assertFalse("Dependencia Google Maps prohibida: $token", manifest.contains(token))
            assertFalse("Dependencia Google Maps prohibida: $token", workflow.contains(token))
        }
        assertTrue(navigation.contains("Intent.ACTION_VIEW"))
        assertTrue(navigation.contains("geo:"))
        assertFalse(navigation.contains("setPackage("))
        assertFalse(navigation.contains("com.google.android.apps.maps"))
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

    private fun order(serviceType: ServiceType, riderId: String?): LocalOrder = LocalOrder(
        id = "ORDER-${serviceType.name}",
        createdAt = LocalDateTime.now().format(stamp),
        serviceType = serviceType,
        category = if (serviceType == ServiceType.SHOPPING) ServiceCategory.PURCHASE else ServiceCategory.ERRAND,
        operationMode = OperationMode.MULTI_RIDER,
        status = OrderStatus.IN_PROGRESS,
        customerName = "Cliente",
        customerPhone = "2345555999",
        detail = "Fixture",
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

    private fun projectFile(repoRelativePath: String): String {
        val candidates = listOf(
            File(repoRelativePath),
            File("../$repoRelativePath"),
            File("../../$repoRelativePath")
        )
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("No se encontró source de regresión: $repoRelativePath")
    }
}
