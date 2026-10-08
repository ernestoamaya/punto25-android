package ar.com.mandados.app

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShiftV2UiPolicyTest {
    @Test
    fun `REG-SHIFT-DIALOG-001 editores y calendario preservan politica TODO2`() {
        val source = projectSource("src/main/java/ar/com/mandados/app/ShiftScreensV2.kt")
        val ruleEditor = source
            .substringAfter("private fun ShiftRuleEditorDialog(")
            .substringBefore("private fun ConcreteShiftEditorDialog(")
        val shiftEditor = source
            .substringAfter("private fun ConcreteShiftEditorDialog(")
            .substringBefore("private fun ShiftGenerationPreviewDialog(")

        assertTrue(ruleEditor.contains("pendingEditDismissDecision(source, dirty)"))
        assertTrue(ruleEditor.contains("PendingEditDismissSource.BACK"))
        assertTrue(ruleEditor.contains("PendingEditDismissSource.CANCEL"))
        assertTrue(ruleEditor.contains("DiscardChangesDialog("))

        assertTrue(shiftEditor.contains("pendingEditDismissDecision(source, dirty)"))
        assertTrue(shiftEditor.contains("PendingEditDismissSource.BACK"))
        assertTrue(shiftEditor.contains("PendingEditDismissSource.CANCEL"))
        assertTrue(shiftEditor.contains("DiscardChangesDialog("))

        assertTrue(source.contains("DatePickerDialog("))
        assertTrue(source.contains("properties = punto25DialogProperties()"))
        assertTrue(source.contains("requestDismiss(PendingEditDismissSource.BACK)"))
        assertTrue(source.contains("Previsualización de generación"))
        assertTrue(source.contains("onDismissRequest = { /* outside tap is blocked by Punto25 policy"))
    }

    @Test
    fun `REG-SHIFT-GEN-UNSAVED-001 generation dirty uses only persistence parameters and success resets baseline`() {
        val initial = shiftGenerationEditSnapshot("2026-10-08", "2026-10-15", null, null)
        assertFalse(initial != shiftGenerationEditSnapshot("2026-10-08", "2026-10-15", null, null))
        assertTrue(initial != initial.copy(generationFrom = "2026-10-09"))
        assertTrue(initial != initial.copy(generationTo = "2026-10-16"))
        assertTrue(initial != initial.copy(fromRuleId = "RULE-A"))
        assertTrue(initial != initial.copy(toRuleId = "RULE-B"))

        val source = projectSource("src/main/java/ar/com/mandados/app/ShiftScreensV2.kt")
        val snapshotType = source.substringAfter("internal data class ShiftGenerationEditSnapshot(").substringBefore(")\n\ninternal fun shiftGenerationEditSnapshot")
        assertFalse(snapshotType.contains("listFrom"))
        assertFalse(snapshotType.contains("listTo"))

        val generation = source.substringAfter("internal fun AdminShiftsV2Screen(").substringBefore("@Composable\ninternal fun RiderShiftsV2")
        assertTrue(generation.contains("val generationDirty = generationCurrent != generationBaseline"))
        assertTrue(generation.contains("generationExitGuard.requestExit(generationDirty, onBack)"))
        assertTrue(generation.contains("preview = c.previewShiftGeneration("))
        val previewAction = generation.substringAfter("Text(\"PREVISUALIZAR\")").substringBefore("ShiftSectionTitle(\"TURNOS CONCRETOS POR FECHA\")")
        assertFalse(previewAction.contains("generationBaseline ="))

        val confirm = generation.substringAfter("val result = c.confirmShiftGeneration(currentPreview.request)").substringBefore("message = when")
        assertTrue(confirm.contains("if (result.error == null && result.conflicts.isEmpty())"))
        assertTrue(confirm.contains("generationBaseline = shiftGenerationEditSnapshot("))
    }

    @Test
    fun `REG-SHIFT-UI-001 Admin v2 distingue reglas generacion y turnos concretos sin exponer lineage`() {
        val source = projectSource("src/main/java/ar/com/mandados/app/ShiftScreensV2.kt")

        assertTrue(source.contains("CONFIGURACIÓN DE CUPOS Y HORARIOS DE TURNOS"))
        assertTrue(source.contains("GENERAR TURNOS SEMANALES"))
        assertTrue(source.contains("TURNOS CONCRETOS POR FECHA"))
        assertTrue(source.contains("PREVISUALIZAR"))
        assertTrue(source.contains("Repartidor"))
        assertFalse(source.contains("Text(\"originRuleId"))
        assertFalse(source.contains("Text(\"lineage"))
        assertFalse(source.contains("Text(\"business key"))
    }

    @Test
    fun `REG-SHIFT-WIRING-001 entry points operativos enrutan exclusivamente a UI v2`() {
        val app = projectSource("src/main/java/ar/com/mandados/app/MandadosApp.kt")
        val operations = projectSource("src/main/java/ar/com/mandados/app/OperationsScreens.kt")
        val routing = projectSource("src/main/java/ar/com/mandados/app/ShiftOperationalRouting.kt")

        assertTrue(app.contains("Screen.ADMIN_SHIFTS -> AdminShiftsScreen("))
        assertTrue(operations.contains("RiderSection.SHIFTS -> RiderShifts(c, rider)"))
        assertTrue(routing.contains("AdminShiftsV2Screen(c, onBack)"))
        assertTrue(routing.contains("RiderShiftsV2(c, rider)"))

        assertTrue(operations.contains("internal fun AdminShiftsLegacyScreen("))
        assertTrue(operations.contains("private fun RiderShiftsLegacy("))
        assertFalse(operations.contains("internal fun AdminShiftsScreen("))
        assertFalse(operations.contains("private fun RiderShifts("))

        assertFalse(routing.contains("shiftOccurrences("))
        assertFalse(routing.contains("shiftReservations"))
        assertFalse(routing.contains("addShiftTemplatesBulk("))
        assertFalse(routing.contains("addSpecificDateShifts("))
        assertFalse(routing.contains("reserveShift(rider.id, shift.id,"))
    }

    private fun projectSource(relativePath: String): String {
        val candidates = listOf(
            File(relativePath),
            File("app/$relativePath"),
            File("../app/$relativePath")
        )
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("No se encontró source de regresión: $relativePath")
    }
}
