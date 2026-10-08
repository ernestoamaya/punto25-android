package ar.com.mandados.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderLocationPresentationTest {

    @Test
    fun `REG-ORDER-LOCATION-PRESENTATION-001 Cliente usa presentacion estructurada sin detail legacy`() {
        val order = deliveryOrder().copy(
            detail = "Pin retiro: -35.432471, -60.171559\nhttps://maps.google.com/?q=-35.432471,-60.171559",
            originAddress = "Retiro 123",
            destinationAddress = "Entrega 456"
        )
        val rendered = orderPresentationLines(order) { it }.joinToString("\n") { "${it.label}: ${it.value}" }
        val clientUi = projectFile("app/src/main/java/ar/com/mandados/app/MandadosApp.kt")

        assertFalse(rendered.contains("Pin retiro", ignoreCase = true))
        assertFalse(rendered.contains("maps.google.com", ignoreCase = true))
        assertTrue(rendered.contains("Retiro 123"))
        assertTrue(rendered.contains("Entrega 456"))
        assertTrue(clientUi.contains("StructuredOrderPresentation(c, o)"))
        assertTrue(clientUi.contains("OrderLocationsMapDialog"))
    }

    @Test
    fun `REG-ORDER-LOCATION-PRESENTATION-002 Rider no renderiza detail crudo ni URLs legacy`() {
        val operations = projectFile("app/src/main/java/ar/com/mandados/app/OperationsScreens.kt")
        val presentation = projectFile("app/src/main/java/ar/com/mandados/app/OrderLocationsMapScreen.kt")

        assertFalse(operations.contains("Text(order.detail"))
        assertTrue(operations.contains("RiderStructuredOrderPresentation(c, order)"))
        assertTrue(operations.contains("authenticatedAssignedRiderOrder(c, rider.id, payment.orderId)"))
        assertTrue(presentation.contains("authenticatedRiderOrderLocations"))
    }

    @Test
    fun `REG-ORDER-LOCATION-PRESENTATION-004 Rider restringido usa presentacion estructurada y conserva auth`() {
        val restricted = projectFile("app/src/main/java/ar/com/mandados/app/RiderRestrictedWorkspaceScreen.kt")
        val presentation = projectFile("app/src/main/java/ar/com/mandados/app/OrderLocationsMapScreen.kt")
        val access = projectFile("app/src/main/java/ar/com/mandados/app/RiderAccessPolicy.kt")

        assertFalse(restricted.contains("Text(order.detail"))
        assertFalse(restricted.contains("order.detail"))
        assertFalse(restricted.contains("maps.google.com", ignoreCase = true))
        assertFalse(restricted.contains("Uri.parse("))
        assertFalse(restricted.contains("Regex("))
        assertTrue(restricted.contains("RiderStructuredOrderPresentation(c, order)"))
        assertTrue(restricted.contains("if (rider == null || !c.hasAuthenticatedRiderSession(riderId))"))
        assertTrue(restricted.contains("val activeOrders = authenticatedRiderActiveOrders(c, rider.id)"))
        assertTrue(presentation.contains("authenticatedRiderOrderLocations(c, assignedRiderId, order.id)"))
        assertTrue(access.contains("authenticatedAssignedRiderOrder"))
        assertTrue(access.contains("it.assignedRiderId == riderId"))
    }

    @Test
    fun `REG-ORDER-LOCATION-PRESENTATION-003 Admin usa presentacion estructurada y controles read only`() {
        val operations = projectFile("app/src/main/java/ar/com/mandados/app/OperationsScreens.kt")
        val presentation = projectFile("app/src/main/java/ar/com/mandados/app/OrderLocationsMapScreen.kt")

        assertFalse(operations.contains("Text(order.detail"))
        assertTrue(operations.contains("AdminStructuredOrderPresentation(c, order)"))
        assertTrue(presentation.contains("location.kind.adminButtonLabel"))
        assertTrue(presentation.contains("VER TODAS LAS UBICACIONES"))
    }

    @Test
    fun `REG-ORDER-LOCATION-STRUCTURED-001 ubicaciones salen solo de GeoPoint estructurados`() {
        val order = deliveryOrder().copy(
            detail = "https://maps.google.com/?q=88.8,77.7",
            originAddress = "88.8,77.7",
            originLocation = GeoPoint(-35.432471, -60.171559),
            destinationLocation = GeoPoint(-35.430100, -60.170200),
            storeLocation = GeoPoint(10.0, 20.0),
            prePickupLocation = GeoPoint(30.0, 40.0)
        )

        assertEquals(
            listOf(
                OrderLocationPoint(OrderLocationKind.ORIGIN, GeoPoint(-35.432471, -60.171559)),
                OrderLocationPoint(OrderLocationKind.DESTINATION, GeoPoint(-35.430100, -60.170200))
            ),
            orderLocationPoints(order)
        )
    }

    @Test
    fun `REG-ORDER-LOCATION-READONLY-001 visor no tiene dependencias de mutacion ni permisos de ubicacion`() {
        val source = projectFile("app/src/main/java/ar/com/mandados/app/OrderLocationsMapScreen.kt")
        val forbidden = listOf(
            "LocationServices",
            "Manifest.permission",
            "requestPermissions",
            "applyMapSelection",
            "updateOrderStatus(",
            "editOrder(",
            "assignRider("
        )

        forbidden.forEach { token -> assertFalse("Visor read-only contiene $token", source.contains(token)) }
        assertTrue(source.contains("Vista de consulta. Los puntos no se pueden editar"))
        assertTrue(source.contains("BaseStyle.Uri(ORDER_LOCATIONS_OPENFREEMAP_STYLE)"))
    }

    @Test
    fun `REG-ORDER-LOCATION-POINTS-001 DELIVERY muestra solo puntos existentes`() {
        val order = deliveryOrder().copy(
            originLocation = null,
            destinationLocation = GeoPoint(-35.43, -60.17),
            storeLocation = GeoPoint(1.0, 2.0),
            prePickupLocation = GeoPoint(3.0, 4.0)
        )

        assertEquals(
            listOf(OrderLocationPoint(OrderLocationKind.DESTINATION, GeoPoint(-35.43, -60.17))),
            orderLocationPoints(order)
        )
        assertTrue(orderLocationPoints(order.copy(destinationLocation = null)).isEmpty())
    }

    @Test
    fun `REG-ORDER-LOCATION-POINTS-002 SHOPPING muestra retiro previo comercio y entrega existentes`() {
        val order = shoppingOrder().copy(
            originLocation = GeoPoint(1.0, 2.0),
            prePickupLocation = GeoPoint(-35.44, -60.18),
            storeLocation = null,
            destinationLocation = GeoPoint(-35.42, -60.16)
        )

        assertEquals(
            listOf(OrderLocationKind.PRE_PICKUP, OrderLocationKind.DESTINATION),
            orderLocationPoints(order).map { it.kind }
        )
    }

    @Test
    fun `REG-ORDER-LOCATION-LEGACY-001 URL legacy sin GeoPoint no crea ubicacion ficticia`() {
        val order = deliveryOrder().copy(
            detail = "Pin retiro: -35.432471,-60.171559 https://maps.google.com/?q=-35.432471,-60.171559",
            originLocation = null,
            destinationLocation = null
        )

        assertTrue(orderLocationPoints(order).isEmpty())
        assertNull(orderLocationsViewport(orderLocationPoints(order)))
    }

    @Test
    fun `REG-ORDER-LOCATION-MAP-001 GeoPoint a MapLibre conserva longitude latitude`() {
        val mapped = orderMapCoordinate(GeoPoint(latitude = -35.432471, longitude = -60.171559))

        assertEquals(-60.171559, mapped.longitude, 0.0)
        assertEquals(-35.432471, mapped.latitude, 0.0)
    }

    @Test
    fun `REG-ORDER-LOCATION-NOMUTATION-001 consultar ubicaciones y viewport no muta LocalOrder`() {
        val order = deliveryOrder().copy(
            originLocation = GeoPoint(-35.432471, -60.171559),
            destinationLocation = GeoPoint(-35.430100, -60.170200)
        )
        val before = order.copy()

        val points = orderLocationPoints(order)
        val viewport = orderLocationsViewport(points)
        points.forEach { orderMapCoordinate(it.point) }

        assertEquals(before, order)
        assertNotNull(viewport)
        assertEquals(2, points.size)
    }

    @Test
    fun `viewport con un punto funciona y cero puntos no abre mapa`() {
        val point = OrderLocationPoint(OrderLocationKind.ORIGIN, GeoPoint(-35.43, -60.17))

        assertNull(orderLocationsViewport(emptyList()))
        val viewport = orderLocationsViewport(listOf(point))
        assertNotNull(viewport)
        assertEquals(point.point, viewport?.target)
        assertEquals(15.0, viewport?.zoom ?: 0.0, 0.0)
    }

    private fun deliveryOrder(): LocalOrder = LocalOrder(
        id = "ORDER-LOCATION-DELIVERY",
        createdAt = "08/10/2026 04:00:00",
        serviceType = ServiceType.DELIVERY,
        category = ServiceCategory.ERRAND,
        status = OrderStatus.IN_PROGRESS,
        customerName = "Cliente",
        customerPhone = "2345555000",
        detail = "Legacy",
        baseAmount = 1000,
        baseZoneName = "Urbana",
        prePickupAmount = 0,
        rainAmount = 0,
        totalAmount = 1000,
        whatsappMessage = ""
    )

    private fun shoppingOrder(): LocalOrder = LocalOrder(
        id = "ORDER-LOCATION-SHOPPING",
        createdAt = "08/10/2026 04:00:00",
        serviceType = ServiceType.SHOPPING,
        category = ServiceCategory.PURCHASE,
        status = OrderStatus.IN_PROGRESS,
        customerName = "Cliente",
        customerPhone = "2345555000",
        detail = "Legacy",
        baseAmount = 1000,
        baseZoneName = "Urbana",
        prePickupAmount = 0,
        rainAmount = 0,
        totalAmount = 1000,
        whatsappMessage = ""
    )

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
