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
        val operational = buildString {
            append(projectTextTree("app/src/main"))
            append('\n').append(gradle)
            append('\n').append(projectFile(".github/workflows/alpha-apk.yml"))
        }
        val forbidden = listOf(
            "com.google.maps.android",
            "com.google.android.gms.maps",
            "maps-compose",
            "com.google.android.geo.API_KEY",
            "MAPS_API_KEY"
        )

        forbidden.forEach { token -> assertFalse("Referencia operativa prohibida: $token", operational.contains(token)) }
        assertTrue(gradle.contains("implementation(\"org.maplibre.compose:maplibre-compose:0.19.0\")"))
        assertTrue(gradle.contains("runtimeOnly(\"org.maplibre.compose:maplibre-compose-runtime-opengl-android:0.19.0\")"))
        assertTrue(gradle.contains("implementation(\"com.google.android.gms:play-services-location:21.4.0\")"))
        assertFalse(gradle.contains("org.maplibre.compose:maplibre-compose:+"))
        assertFalse(gradle.contains("org.maplibre.compose:maplibre-compose:0.19.0-SNAPSHOT"))
    }

    @Test
    fun `REG-MAPS-OSS-001 compile SDK y workflows quedan compatibles con MapLibre 0190`() {
        val gradle = projectFile("app/build.gradle.kts")
        val workflows = listOf(
            projectFile(".github/workflows/ci.yml"),
            projectFile(".github/workflows/codeql.yml"),
            projectFile(".github/workflows/alpha-apk.yml")
        )

        assertTrue(gradle.contains("compileSdk = 37"))
        assertTrue(gradle.contains("targetSdk = 36"))
        assertTrue(gradle.contains("minSdk = 26"))
        workflows.forEach { workflow ->
            assertTrue(workflow.contains("platforms;android-37.0"))
            assertTrue(workflow.contains("build-tools;36.0.0"))
            assertFalse(workflow.contains("platforms;android-37 "))
            assertFalse(workflow.contains("platforms/android-37"))
            assertFalse(workflow.contains("android sdk install --canary"))
            assertFalse(workflow.contains("Install Android 17 preview SDK"))
            assertFalse(workflow.contains("platforms;android-36"))
        }
    }

    @Test
    fun `REG-MAPS-SELECTION-001 selector usa callback geografico de MapInteractions`() {
        val picker = projectFile("app/src/main/java/ar/com/mandados/app/MapLocationPicker.kt")

        assertTrue(picker.contains("https://tiles.openfreemap.org/styles/liberty"))
        assertTrue(picker.contains("MaplibreMap("))
        assertTrue(picker.contains("MapInteractions"))
        assertTrue(picker.contains("callbacks {"))
        assertTrue(picker.contains("click {"))
        assertTrue(picker.contains("event.position?.let"))
        assertTrue(picker.contains("geoPointFromMapCoordinates(position.latitude, position.longitude)"))
        assertFalse(picker.contains("draggable = true"))
        assertFalse(picker.contains("arrastralo"))
    }

    @Test
    fun `REG-MAPS-LOCATION-PERMISSION-001 permiso denegado deja seleccion manual disponible`() {
        val picker = projectFile("app/src/main/java/ar/com/mandados/app/MapLocationPicker.kt")

        assertTrue(picker.contains("Permiso de ubicación no concedido. Podés mover el pin manualmente."))
        assertTrue(picker.contains("MaplibreMap("))
        assertTrue(picker.contains("enabled = target != null"))
    }

    @Test
    fun `REG-MAP-UNSAVED-001 punto no confirmado queda local y descarte no modifica OrderDraft`() {
        val picker = projectFile("app/src/main/java/ar/com/mandados/app/MapLocationPicker.kt")
        val before = seededDraft()
        val selectedButUnconfirmed = GeoPoint(-35.44, -60.18)

        assertTrue(selectedButUnconfirmed != mapPointForTarget(before, MapTarget.DELIVERY_ORIGIN))
        assertEquals(GeoPoint(1.0, 2.0), before.originLocation)
        assertTrue(picker.contains("val initialPoint = rememberSaveable(target, saver = geoPointSaver)"))
        assertTrue(picker.contains("var selectedPoint by rememberSaveable(target, stateSaver = geoPointSaver)"))
        assertTrue(picker.contains("val dirty = selectedPoint != initialPoint"))
        assertTrue(picker.contains("UnsavedChangesGuard("))
        assertTrue(picker.contains("exitGuard.requestExit(dirty, onBack)"))
        assertEquals(1, Regex("applyMapSelection\\(").findAll(picker).count())
        assertTrue(picker.contains("c.draft = applyMapSelection(c.draft, target, selectedPoint)"))
    }

    @Test
    fun `REG-MAPS-ATTRIBUTION-001 selector conserva overlay predeterminado de MapLibre`() {
        val picker = projectFile("app/src/main/java/ar/com/mandados/app/MapLocationPicker.kt")

        assertTrue(picker.contains("MaplibreMap("))
        assertFalse(picker.contains("overlay ="))
        assertFalse(picker.contains("MapOverlay.None"))
    }

    @Test
    fun `REG-PACKAGING-MAPS-001 packaging no requiere clave cartografica y conserva protecciones`() {
        val workflow = projectFile(".github/workflows/alpha-apk.yml")

        assertFalse(workflow.contains("MAPS_API_KEY"))
        assertTrue(workflow.contains("permissions:\n  contents: read"))
        assertTrue(workflow.contains("cancel-in-progress: false"))
        assertTrue(workflow.contains("persist-credentials: false"))
        assertTrue(workflow.contains("TRUSTED_MAIN_SHA"))
        assertTrue(workflow.contains("SAFE_RELEASE_FLOOR_SHA"))
        assertTrue(workflow.contains("git checkout --detach --force \"\$TARGET_SHA\""))
        assertTrue(workflow.contains("SIGNER_COUNT"))
        assertTrue(workflow.contains("EXPECTED_ALPHA_CERT_SHA256"))
        assertTrue(workflow.contains("SHA256SUMS.txt"))
        assertTrue(workflow.contains("BUILD_PROVENANCE.txt"))
        assertTrue(workflow.contains("ARTIFACT_NAME"))
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

    private fun projectTextTree(repoRelativePath: String): String {
        val root = listOf(
            File(repoRelativePath),
            File("../$repoRelativePath"),
            File("../../$repoRelativePath")
        ).firstOrNull { it.isDirectory }
            ?: error("No se encontró árbol de regresión: $repoRelativePath")
        val textExtensions = setOf("kt", "kts", "xml", "properties", "json", "txt")
        return root.walkTopDown()
            .filter { it.isFile && it.extension.lowercase() in textExtensions }
            .sortedBy { it.path }
            .joinToString("\n") { it.readText() }
    }
}
