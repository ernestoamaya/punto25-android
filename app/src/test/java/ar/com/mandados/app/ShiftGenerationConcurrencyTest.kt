package ar.com.mandados.app

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ShiftGenerationConcurrencyTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("mandados_alpha1", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `REG-SHIFT-GEN-PREVIEW-001 preview no persiste ni altera estado`() {
        val monday = nextMonday()
        seedRule(monday)
        val c = MandadosController(context)
        val request = ShiftGenerationRequest(iso(monday), iso(monday))

        val preview = c.previewShiftGeneration(request)

        assertTrue(preview.canConfirm)
        assertEquals(1, preview.newCount)
        assertTrue(c.concreteShifts.isEmpty())
        assertTrue(MandadosController(context).concreteShifts.isEmpty())
    }

    @Test
    fun `REG-SHIFT-GEN-CONCURRENCY-001 dos controllers no pueden sobrescribir con snapshot viejo`() {
        val monday = nextMonday()
        seedRule(monday)
        val first = MandadosController(context)
        val stale = MandadosController(context)
        val request = ShiftGenerationRequest(iso(monday), iso(monday))

        assertEquals(1, first.previewShiftGeneration(request).newCount)
        assertEquals(1, stale.previewShiftGeneration(request).newCount)

        val firstResult = first.confirmShiftGeneration(request)
        val staleResult = stale.confirmShiftGeneration(request)

        assertTrue(firstResult.canConfirm)
        assertEquals(1, firstResult.newCount)
        assertNotNull(staleResult.error)
        assertFalse(staleResult.canConfirm)

        val restarted = MandadosController(context)
        assertTrue(restarted.isShiftSubsystemReady())
        assertEquals(1, restarted.concreteShifts.size)
        assertEquals(1, restarted.concreteShifts.count {
            it.originRuleId == restarted.shiftRules.single().id && it.serviceDate == iso(monday)
        })
    }

    @Test
    fun `REG-SHIFT-STORE-CAS-001 store obsoleto falla sin pisar snapshot vigente`() {
        val firstStore = ShiftStoreV2(context)
        assertTrue(firstStore.initializeIfNeeded().success)
        val staleStore = ShiftStoreV2(context)
        assertTrue(firstStore.loadSnapshot().healthy)
        assertTrue(staleStore.loadSnapshot().healthy)

        val firstRule = ShiftGenerationRule("R-FIRST", setOf(1), 9 * 60, 12 * 60, 2)
        val staleRule = ShiftGenerationRule("R-STALE", setOf(2), 13 * 60, 16 * 60, 3)
        assertTrue(firstStore.saveRules(listOf(firstRule)))
        assertFalse(staleStore.saveRules(listOf(staleRule)))

        val check = ShiftStoreV2(context)
        assertTrue(check.initializeIfNeeded().success)
        val persisted = check.loadSnapshot()
        assertEquals(listOf(firstRule), persisted.rules)
    }

    private fun seedRule(monday: LocalDate) {
        val store = ShiftStoreV2(context)
        assertTrue(store.initializeIfNeeded().success)
        val rule = ShiftGenerationRule(
            id = "R-CONCURRENT",
            daysOfWeek = setOf(monday.dayOfWeek.value),
            startMinute = 9 * 60,
            endMinute = 12 * 60,
            capacity = 2
        )
        assertTrue(store.saveAll(listOf(rule), emptyList(), emptyList(), emptyList()))
    }

    private fun nextMonday(): LocalDate {
        var date = LocalDate.now()
        while (date.dayOfWeek.value != 1) date = date.plusDays(1)
        return date
    }

    private fun iso(date: LocalDate): String = ShiftSchedulePolicy.toIsoDate(date)
}
