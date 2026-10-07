package ar.com.mandados.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ShiftSchedulePolicyTest {
    @Test
    fun `REG-SHIFT-RULE-001 reglas validas dias horario cupo y fin 24`() {
        val rule = ShiftGenerationRule("R1", setOf(1, 2, 3, 4, 5), 9 * 60, 24 * 60, 2)
        assertNull(ShiftSchedulePolicy.validateRule(rule))
        assertEquals("09:00", ShiftSchedulePolicy.formatMinute(rule.startMinute))
        assertEquals("24:00", ShiftSchedulePolicy.formatMinute(rule.endMinute))
        assertEquals(0, ShiftSchedulePolicy.parseClock("00:00", allow24 = false))
        assertEquals(1440, ShiftSchedulePolicy.parseClock("24:00", allow24 = true))
        assertNull(ShiftSchedulePolicy.parseClock("24:00", allow24 = false))
    }

    @Test
    fun `REG-SHIFT-RULE-002 detecta solapamiento incluso domingo a lunes`() {
        val sundayNight = ShiftGenerationRule("SUN", setOf(7), 23 * 60, 2 * 60, 2)
        val mondayEarly = ShiftGenerationRule("MON", setOf(1), 60, 3 * 60, 2)
        val mondayTouching = ShiftGenerationRule("TOUCH", setOf(1), 2 * 60, 4 * 60, 2)
        assertTrue(ShiftSchedulePolicy.rulesOverlap(sundayNight, mondayEarly))
        assertFalse(ShiftSchedulePolicy.rulesOverlap(sundayNight, mondayTouching))
    }

    @Test
    fun `REG-SHIFT-DATE-STRICT-001 fecha imposible rechazada e ISO display consistentes`() {
        assertNull(ShiftSchedulePolicy.parseDisplayDate("31/02/2026"))
        val date = ShiftSchedulePolicy.parseDisplayDate("07/10/2026")
        assertEquals(LocalDate.of(2026, 10, 7), date)
        assertEquals("2026-10-07", ShiftSchedulePolicy.toIsoDate(date!!))
        assertEquals("07/10/2026", ShiftSchedulePolicy.displayDate("2026-10-07"))
        assertNull(ShiftSchedulePolicy.parseIsoDate("2026-02-31"))
    }

    @Test
    fun `REG-SHIFT-OVERNIGHT-001 ventanas reales cruzan fecha y tocar extremo no solapa`() {
        val night = ConcreteShift("N", "2026-10-11", 23 * 60, 2 * 60, 1)
        val monday = ConcreteShift("M", "2026-10-12", 60, 3 * 60, 1)
        val touching = ConcreteShift("T", "2026-10-12", 2 * 60, 4 * 60, 1)
        assertTrue(ShiftSchedulePolicy.overlaps(night, monday))
        assertFalse(ShiftSchedulePolicy.overlaps(night, touching))
        val fullDayEnd = ConcreteShift("E", "2026-10-12", 12 * 60, 24 * 60, 1)
        assertEquals(LocalDate.of(2026, 10, 13), ShiftSchedulePolicy.window(fullDayEnd)!!.second.toLocalDate())
    }

    @Test
    fun `REG-SHIFT-GEN-RANGE-001 rango inclusivo y limites parciales`() {
        val rules = listOf(
            rule("A", 9 * 60, 12 * 60),
            rule("B", 12 * 60, 15 * 60 + 30),
            rule("C", 17 * 60, 19 * 60 + 45),
            rule("D", 19 * 60 + 45, 24 * 60)
        )
        val preview = ShiftSchedulePolicy.buildGenerationPreview(
            ShiftGenerationRequest("2026-10-07", "2026-10-14", fromRuleId = "C", toRuleId = "B"),
            rules,
            emptyList(),
            idFactory = idSequence()
        )
        assertTrue(preview.canConfirm)
        assertEquals(28, preview.candidateCount) // 2 on first + 6 full days + 2 on last
        assertEquals(28, preview.newCount)
        assertEquals("C", preview.newShifts.first().originRuleId)
        assertEquals("2026-10-07", preview.newShifts.first().serviceDate)
        assertEquals("B", preview.newShifts.last().originRuleId)
        assertEquals("2026-10-14", preview.newShifts.last().serviceDate)
    }

    @Test
    fun `REG-SHIFT-GEN-RANGE-001 un solo dia y dia sin reglas son validos`() {
        val mondayOnly = ShiftGenerationRule("M", setOf(1), 9 * 60, 12 * 60, 1)
        val monday = ShiftSchedulePolicy.buildGenerationPreview(
            ShiftGenerationRequest("2026-10-12", "2026-10-12"), listOf(mondayOnly), emptyList(), idSequence()
        )
        val tuesday = ShiftSchedulePolicy.buildGenerationPreview(
            ShiftGenerationRequest("2026-10-13", "2026-10-13"), listOf(mondayOnly), emptyList(), idSequence()
        )
        assertEquals(1, monday.newCount)
        assertEquals(0, tuesday.newCount)
        assertTrue(tuesday.canConfirm)
    }

    @Test
    fun `REG-SHIFT-GEN-IDEMPOTENT-001 lineage regla fecha no depende de hora o cupo`() {
        val generated = ConcreteShift("OLD", "2026-10-12", 10 * 60, 13 * 60, 9, originRuleId = "R1", isException = true)
        val currentRule = ShiftGenerationRule("R1", setOf(1), 9 * 60, 12 * 60, 2)
        val preview = ShiftSchedulePolicy.buildGenerationPreview(
            ShiftGenerationRequest("2026-10-12", "2026-10-12"), listOf(currentRule), listOf(generated), idSequence()
        )
        assertEquals(1, preview.candidateCount)
        assertEquals(0, preview.newCount)
        assertEquals(1, preview.materializedCount)
        assertEquals(1, preview.exceptionCount)
    }

    @Test
    fun `REG-SHIFT-GEN-IDEMPOTENT-002 excepcion deshabilitada se preserva`() {
        val exception = ConcreteShift("OLD", "2026-10-12", 9 * 60, 12 * 60, 2, enabled = false, originRuleId = "R1", isException = true)
        val preview = ShiftSchedulePolicy.buildGenerationPreview(
            ShiftGenerationRequest("2026-10-12", "2026-10-12"), listOf(rule("R1", 9 * 60, 12 * 60)), listOf(exception), idSequence()
        )
        assertTrue(preview.canConfirm)
        assertEquals(0, preview.newCount)
        assertEquals(1, preview.exceptionCount)
    }

    @Test
    fun `REG-SHIFT-GEN-CONFLICT-001 turno ajeno solapado rechaza lote completo`() {
        val foreign = ConcreteShift("F", "2026-10-12", 10 * 60, 11 * 60, 1, originRuleId = null)
        val preview = ShiftSchedulePolicy.buildGenerationPreview(
            ShiftGenerationRequest("2026-10-12", "2026-10-12"), listOf(rule("R1", 9 * 60, 12 * 60)), listOf(foreign), idSequence()
        )
        assertFalse(preview.canConfirm)
        assertTrue(preview.conflicts.isNotEmpty())
    }

    @Test
    fun `REG-SHIFT-GEN-CONFLICT-002 turno ajeno disabled tambien bloquea`() {
        val foreign = ConcreteShift("F", "2026-10-12", 10 * 60, 11 * 60, 1, enabled = false)
        val preview = ShiftSchedulePolicy.buildGenerationPreview(
            ShiftGenerationRequest("2026-10-12", "2026-10-12"), listOf(rule("R1", 9 * 60, 12 * 60)), listOf(foreign), idSequence()
        )
        assertFalse(preview.canConfirm)
        assertNotNull(preview.conflicts.firstOrNull())
    }

    @Test
    fun `REG-SHIFT-GEN-ATOMIC-001 regla invalida invalida todo el plan`() {
        val valid = rule("OK", 9 * 60, 12 * 60)
        val invalid = ShiftGenerationRule("BAD", setOf(1), 13 * 60, 13 * 60, 1)
        val preview = ShiftSchedulePolicy.buildGenerationPreview(
            ShiftGenerationRequest("2026-10-12", "2026-10-19"), listOf(valid, invalid), emptyList(), idSequence()
        )
        assertFalse(preview.canConfirm)
        assertEquals(0, preview.newCount)
        assertNotNull(preview.error)
    }

    private fun rule(id: String, start: Int, end: Int) = ShiftGenerationRule(id, (1..7).toSet(), start, end, 2)

    private fun idSequence(): () -> String {
        var index = 0
        return { "TEST-${index++}" }
    }
}
