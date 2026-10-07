package ar.com.mandados.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MapSelectionPolicyTest {
    private val point = GeoPoint(-35.432471, -60.171559)

    @Test
    fun `REG-MAPS-TARGET-001 delivery origin guarda solo originLocation`() {
        val before = seededDraft()
        val after = applyMapSelection(before, MapTarget.DELIVERY_ORIGIN, point)

        assertEquals(point, after.originLocation)
        assertEquals(before.destinationLocation, after.destinationLocation)
        assertEquals(before.storeLocation, after.storeLocation)
        assertEquals(before.prePickupLocation, after.prePickupLocation)
    }

    @Test
    fun `REG-MAPS-TARGET-001 delivery destination guarda destinationLocation`() {
        val before = seededDraft()
        val after = applyMapSelection(before, MapTarget.DELIVERY_DESTINATION, point)

        assertEquals(point, after.destinationLocation)
        assertEquals(before.originLocation, after.originLocation)
        assertEquals(before.storeLocation, after.storeLocation)
        assertEquals(before.prePickupLocation, after.prePickupLocation)
    }

    @Test
    fun `REG-MAPS-TARGET-001 shopping store guarda storeLocation`() {
        val before = seededDraft()
        val after = applyMapSelection(before, MapTarget.SHOPPING_STORE, point)

        assertEquals(point, after.storeLocation)
        assertEquals(before.originLocation, after.originLocation)
        assertEquals(before.destinationLocation, after.destinationLocation)
        assertEquals(before.prePickupLocation, after.prePickupLocation)
    }

    @Test
    fun `REG-MAPS-TARGET-001 shopping destination guarda destinationLocation`() {
        val before = seededDraft()
        val after = applyMapSelection(before, MapTarget.SHOPPING_DESTINATION, point)

        assertEquals(point, after.destinationLocation)
        assertEquals(before.originLocation, after.originLocation)
        assertEquals(before.storeLocation, after.storeLocation)
        assertEquals(before.prePickupLocation, after.prePickupLocation)
    }

    @Test
    fun `REG-MAPS-TARGET-001 shopping pre pickup guarda prePickupLocation`() {
        val before = seededDraft().copy(sameDeliveryAsPrePickup = false)
        val after = applyMapSelection(before, MapTarget.SHOPPING_PRE_PICKUP, point)

        assertEquals(point, after.prePickupLocation)
        assertEquals(before.destinationLocation, after.destinationLocation)
    }

    @Test
    fun `REG-MAPS-SAME-DELIVERY-001 pre pickup copia exactamente el mismo punto a destination`() {
        val before = seededDraft().copy(sameDeliveryAsPrePickup = true)
        val after = applyMapSelection(before, MapTarget.SHOPPING_PRE_PICKUP, point)

        assertSame(point, after.prePickupLocation)
        assertSame(point, after.destinationLocation)
    }

    @Test
    fun `REG-MAPS-TARGET-001 target nulo no atribuye el punto`() {
        val before = seededDraft()
        val after = applyMapSelection(before, null, point)

        assertEquals(before, after)
        assertNull(mapPointForTarget(before, null))
    }

    @Test
    fun `REG-MAPS-SELECTION-001 coordenadas conservan latitud y longitud sin intercambio`() {
        val selected = geoPointFromMapCoordinates(latitude = -35.432471, longitude = -60.171559)

        assertEquals(-35.432471, selected.latitude, 0.0)
        assertEquals(-60.171559, selected.longitude, 0.0)
    }

    @Test
    fun `REG-MAPS-OSS-001 MapLibre OpenFreeMap reemplaza Google Maps operativo`() {
        val gradle = projectFile("app/build.gradle.kts")
        val manifest = projectFile("app/src/main/AndroidManifest.xml")
        val app = projectFile("app/src/main/java/ar/com/mandados/app/MandadosApp.kt")

        assertTrue(gradle.contains("org.maplibre.compose:maplibre-compose:0.19.0"))
        assertTrue(gradle.contains("org.maplibre.compose:maplibre-compose-runtime-opengl-android:0.19.0"))
        assertTrue(gradle.contains("com.google.android.gms:play-services-location:21.4.0"))
        assertFalse(gradle.contains("com.google.maps.android:maps-compose"))
        assertFalse(manifest.contains("com.google.android.geo.API_KEY"))
        assertFalse(manifest.contains("MAPS_API_KEY"))
        assertTrue(app.contains("https://tiles.openfreemap.org/styles/liberty"))
        assertTrue(app.contains("MaplibreMap("))
        assertFalse(app.contains("com.google.maps"))
    }

    @Test
    fun `REG-MAPS-SELECTION-001 selector usa callback geografico de MapInteractions`() {
        val app = projectFile("app/src/main/java/ar/com/mandados/app/MandadosApp.kt")

        assertTrue(app.contains("MapInteractions"))
        assertTrue(app.contains("callbacks {"))
        assertTrue(app.contains("click {"))
        assertTrue(app.contains("event.position?.let"))
        assertTrue(app.contains("geoPointFromMapCoordinates(position.latitude, position.longitude)"))
        assertFalse(app.contains("draggable = true"))
        assertFalse(app.contains("arrastralo"))
    }

    @Test
    fun `REG-MAPS-LOCATION-PERMISSION-001 permiso denegado deja seleccion manual disponible`() {
        val app = projectFile("app/src/main/java/ar/com/mandados/app/MandadosApp.kt")

        assertTrue(app.contains("Permiso de ubicación no concedido. Podés mover el pin manualmente."))
        assertTrue(app.contains("MaplibreMap("))
        assertTrue(app.contains("enabled = target != null"))
    }

    @Test
    fun `REG-MAPS-ATTRIBUTION-001 selector conserva overlay predeterminado de MapLibre`() {
        val app = projectFile("app/src/main/java/ar/com/mandados/app/MandadosApp.kt")
        val picker = app.substringAfter("private fun LocationPickerScreen(").substringBefore("private fun Field(")

        assertTrue(picker.contains("MaplibreMap("))
        assertFalse(picker.contains("overlay ="))
        assertFalse(picker.contains("MapOverlay.None"))
    }

    @Test
    fun `REG-PACKAGING-MAPS-001 packaging no requiere clave cartografica`() {
        val workflow = projectFile(".github/workflows/alpha-apk.yml")

        assertFalse(workflow.contains("MAPS_API_KEY"))
        assertTrue(workflow.contains("FIREBASE_API_KEY"))
        assertTrue(workflow.contains("ALPHA_KEYSTORE_B64"))
    }

    private fun seededDraft(): OrderDraft = OrderDraft(
        originLocation = GeoPoint(1.0, 2.0),
        destinationLocation = GeoPoint(3.0, 4.0),
        storeLocation = GeoPoint(5.0, 6.0),
        prePickupLocation = GeoPoint(7.0, 8.0)
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
