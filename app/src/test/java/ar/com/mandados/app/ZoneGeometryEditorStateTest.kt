package ar.com.mandados.app

import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoneGeometryEditorStateTest {
    private val saverScope = SaverScope { value ->
        value is String || value is Int || value is Long || value is Float || value is Double ||
            value is Boolean || value is CharSequence || value is ArrayList<*>
    }

    @Test
    fun `REG-ZONE-GEOMETRY-UNSAVED-001 keep preserves draft discard does not mutate baseline and invalid save keeps drawing`() {
        val baseline = listOf(square(0.0, 0.0, 1.0))
        var state = initialZoneGeometryEditorSnapshot(baseline)
        state = addZoneGeometryVertex(state, GeoPoint(2.0, 2.0))
        assertTrue(isZoneGeometryEditorDirty(state))
        assertEquals(ZoneGeometryExitDecision.CONFIRM_DISCARD, zoneGeometryExitDecision(true, true))

        val guard = UnsavedChangesGuardState()
        var exits = 0
        guard.requestExit(true) { exits++ }
        guard.keepEditing()
        assertEquals(0, exits)
        assertEquals(baseline, state.baseline)
        assertEquals(GeoPoint(2.0, 2.0), state.drawing.single())

        guard.requestExit(true) { exits++ }
        guard.discard()
        assertEquals(1, exits)
        assertEquals(baseline, state.baseline)

        val invalid = finishZoneGeometryDrawing(state)
        assertEquals(state, invalid.snapshot)
        assertTrue(invalid.message?.isNotBlank() == true)
    }

    @Test
    fun `REG-ZONE-GEOMETRY-RESTORE-001 saveable recreation preserves baseline draft drawing redraw and dirty`() {
        val baseline = listOf(square(0.0, 0.0, 1.0))
        val draft = listOf(square(0.0, 0.0, 1.0), square(3.0, 3.0, 1.0))
        val state = ZoneGeometryEditorSnapshot(
            baseline = baseline,
            draft = draft,
            drawing = listOf(GeoPoint(5.0, 5.0), GeoPoint(5.0, 6.0)),
            redrawingIndex = 1
        )

        val restored = roundTrip(zoneGeometryEditorSnapshotSaver, state)

        assertEquals(state, restored)
        assertTrue(isZoneGeometryEditorDirty(restored))
        assertEquals(1, restored.redrawingIndex)
        assertEquals(2, restored.drawing.size)
        assertEquals(baseline, restored.baseline)
        assertEquals(draft, restored.draft)
    }

    @Test
    fun `REG-ZONE-GEOMETRY-AUTH-001 auth loss exits fail closed before dirty guard`() {
        assertEquals(
            ZoneGeometryExitDecision.EXIT_FAIL_CLOSED,
            zoneGeometryExitDecision(adminAuthorized = false, dirty = true)
        )
        assertEquals(
            ZoneGeometryExitDecision.EXIT_FAIL_CLOSED,
            zoneGeometryExitDecision(adminAuthorized = false, dirty = false)
        )
        assertEquals(
            ZoneGeometryExitDecision.CONFIRM_DISCARD,
            zoneGeometryExitDecision(adminAuthorized = true, dirty = true)
        )
        assertEquals(
            ZoneGeometryExitDecision.EXIT,
            zoneGeometryExitDecision(adminAuthorized = true, dirty = false)
        )
    }

    @Test
    fun `editor operations remain draft only until explicit save`() {
        val baseline = listOf(square(0.0, 0.0, 1.0))
        var state = initialZoneGeometryEditorSnapshot(baseline)
        state = beginZoneGeometryRedraw(state, 0)
        state = addZoneGeometryVertex(state, GeoPoint(10.0, 10.0))
        state = addZoneGeometryVertex(state, GeoPoint(10.0, 11.0))
        state = addZoneGeometryVertex(state, GeoPoint(11.0, 10.0))
        val finished = finishZoneGeometryDrawing(state)

        assertEquals(baseline, finished.snapshot.baseline)
        assertFalse(finished.snapshot.draft == baseline)
        assertTrue(isZoneGeometryEditorDirty(finished.snapshot))

        val saved = markZoneGeometrySaved(finished.snapshot)
        assertEquals(saved.baseline, saved.draft)
        assertFalse(isZoneGeometryEditorDirty(saved))
    }

    private fun <T> roundTrip(saver: Saver<T, Any>, value: T): T {
        val saved = with(saver) { saverScope.save(value) } ?: error("Saver failed")
        val registry = SaveableStateRegistry(restoredValues = null, canBeSaved = { true })
        val entry = registry.registerProvider("state") { saved }
        val bundle = registry.performSave()
        entry.unregister()
        val restoredRegistry = SaveableStateRegistry(bundle, canBeSaved = { true })
        val restored = restoredRegistry.consumeRestored("state") ?: error("Missing state")
        return saver.restore(restored) ?: error("Restore failed")
    }

    private fun square(lat: Double, lon: Double, size: Double): List<GeoPoint> = listOf(
        GeoPoint(lat, lon), GeoPoint(lat, lon + size),
        GeoPoint(lat + size, lon + size), GeoPoint(lat + size, lon)
    )
}
