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
