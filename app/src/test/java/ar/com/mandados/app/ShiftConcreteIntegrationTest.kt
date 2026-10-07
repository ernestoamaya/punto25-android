package ar.com.mandados.app

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ShiftConcreteIntegrationTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("mandados_alpha1", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `REG-SHIFT-CONCRETE-001 fecha concreta es canonica e inmutable al editar`() {
        val shift = ConcreteShift("CS-1", "2026-10-12", 9 * 60, 12 * 60, 2, originRuleId = "R1")
        seedV2(shifts = listOf(shift))
        val c = MandadosController(context)

        assertNull(c.updateConcreteShift(shift.id, "10:00", "13:00", 3, true))
        val edited = c.concreteShift(shift.id)!!
        assertEquals("2026-10-12", edited.serviceDate)
        assertEquals(10 * 60, edited.startMinute)
        assertEquals(13 * 60, edited.endMinute)
        assertEquals(3, edited.capacity)
        assertTrue(edited.isException)
    }

    @Test
    fun `REG-SHIFT-GEN-IDEMPOTENT-001 doble confirmacion no duplica lineage`() {
        val c = MandadosController(context)
        assertNull(c.addShiftRule(setOf(1), "09:00", "12:00", 2))
        val monday = nextOrSame(Day.MONDAY)
        val request = ShiftGenerationRequest(iso(monday), iso(monday))

        val first = c.confirmShiftGeneration(request)
        val afterFirst = c.concreteShifts.toList()
        val second = c.confirmShiftGeneration(request)

        assertTrue(first.canConfirm)
        assertEquals(1, first.newCount)
        assertTrue(second.canConfirm)
        assertEquals(0, second.newCount)
        assertEquals(afterFirst, c.concreteShifts)
        assertEquals(1, c.concreteShifts.count { it.originRuleId == c.shiftRules.single().id && it.serviceDate == iso(monday) })
    }

    @Test
    fun `REG-SHIFT-GEN-IDEMPOTENT-002 excepcion editada y deshabilitada se preserva`() {
        val c = MandadosController(context)
        assertNull(c.addShiftRule(setOf(1), "09:00", "12:00", 2))
        val monday = nextOrSame(Day.MONDAY)
        val request = ShiftGenerationRequest(iso(monday), iso(monday))
        assertEquals(1, c.confirmShiftGeneration(request).newCount)
        val original = c.concreteShifts.single()

        assertNull(c.updateConcreteShift(original.id, "10:00", "13:00", 5, false))
        val exception = c.concreteShift(original.id)!!
        assertTrue(exception.isException)
        assertFalse(exception.enabled)

        val repeated = c.confirmShiftGeneration(request)
        assertTrue(repeated.canConfirm)
        assertEquals(0, repeated.newCount)
        assertEquals(1, repeated.exceptionCount)
        assertEquals(exception, c.concreteShift(original.id))
        assertEquals(1, c.concreteShifts.size)
    }

    @Test
    fun `REG-SHIFT-GEN-PERSIST-001 reglas turnos reservas y excepciones sobreviven recarga`() {
        val rider = approvedRider("RID-PERSIST")
        LocalStore(context).saveRiders(listOf(rider))
        val first = MandadosController(context)
        assertNull(first.addShiftRule(setOf(1), "09:00", "12:00", 2))
        val monday = nextOrSame(Day.MONDAY)
        val request = ShiftGenerationRequest(iso(monday), iso(monday))
        assertEquals(1, first.confirmShiftGeneration(request).newCount)
        val shift = first.concreteShifts.single()
        assertTrue(first.adminAddRiderToShift(rider.id, shift.id))
        assertNull(first.updateConcreteShift(shift.id, "09:00", "12:00", 3, true))

        val restarted = MandadosController(context)
        assertTrue(restarted.isShiftSubsystemReady())
        assertEquals(first.shiftRules, restarted.shiftRules)
        assertEquals(first.concreteShifts, restarted.concreteShifts)
        assertEquals(first.concreteShiftReservations, restarted.concreteShiftReservations)
        assertEquals(first.concreteShiftAuditEvents, restarted.concreteShiftAuditEvents)
        assertTrue(restarted.concreteShift(shift.id)!!.isException)
    }

    @Test
    fun `REG-SHIFT-TEMPLATE-FUTURE-001 editar regla no altera turno ya generado`() {
        val c = MandadosController(context)
        assertNull(c.addShiftRule(setOf(1), "09:00", "12:00", 2))
        val rule = c.shiftRules.single()
        val monday = nextOrSame(Day.MONDAY)
        assertEquals(1, c.confirmShiftGeneration(ShiftGenerationRequest(iso(monday), iso(monday))).newCount)
        val generated = c.concreteShifts.single()

        assertNull(c.updateShiftRule(rule.id, setOf(1), "10:00", "14:00", 5))
        assertEquals(generated, c.concreteShift(generated.id))

        val nextMonday = monday.plusWeeks(1)
        assertEquals(1, c.confirmShiftGeneration(ShiftGenerationRequest(iso(nextMonday), iso(nextMonday))).newCount)
        val future = c.concreteShifts.single { it.serviceDate == iso(nextMonday) }
        assertEquals(10 * 60, future.startMinute)
        assertEquals(14 * 60, future.endMinute)
        assertEquals(5, future.capacity)
        assertEquals(rule.id, future.originRuleId)
    }

    @Test
    fun `REG-SHIFT-TEMPLATE-DELETE-001 eliminar regla preserva turno y reserva existentes`() {
        val rider = approvedRider("RID-DELETE")
        LocalStore(context).saveRiders(listOf(rider))
        val c = MandadosController(context)
        assertNull(c.addShiftRule(setOf(1), "09:00", "12:00", 2))
        val rule = c.shiftRules.single()
        val monday = nextOrSame(Day.MONDAY)
        assertEquals(1, c.confirmShiftGeneration(ShiftGenerationRequest(iso(monday), iso(monday))).newCount)
        val shift = c.concreteShifts.single()
        assertTrue(c.adminAddRiderToShift(rider.id, shift.id))
        val reservation = c.concreteShiftReservations.single()

        assertTrue(c.deleteShiftRule(rule.id))
        assertTrue(c.shiftRules.isEmpty())
        assertEquals(shift, c.concreteShift(shift.id))
        assertEquals(reservation, c.concreteShiftReservations.single())
        assertEquals(rule.id, c.concreteShift(shift.id)!!.originRuleId)
    }

    @Test
    fun `REG-SHIFT-OVERNIGHT-001 turno reservado cruza medianoche y queda activo al dia siguiente`() {
        val rider = approvedRider("RID-NIGHT")
        val startDate = LocalDate.of(2026, 10, 11)
        val shift = ConcreteShift("CS-NIGHT", iso(startDate), 23 * 60, 2 * 60, 2)
        val reservation = ConcreteShiftReservation("CSR-NIGHT", rider.id, shift.id, "11/10/2026 22:30:00")
        LocalStore(context).saveRiders(listOf(rider))
        seedV2(shifts = listOf(shift), reservations = listOf(reservation))
        val c = MandadosController(context)

        assertTrue(c.riderHasActiveShiftNow(rider.id, LocalDateTime.of(2026, 10, 12, 1, 30)))
        assertFalse(c.riderHasActiveShiftNow(rider.id, LocalDateTime.of(2026, 10, 12, 2, 0)))
    }

    @Test
    fun `REG-SHIFT-EDIT-RESERVED-001 horario bloqueado con reservas activas`() {
        val fixture = reservedFixture(capacity = 2)
        val c = fixture.controller
        val before = c.concreteShift(fixture.shift.id)!!

        val error = c.updateConcreteShift(before.id, "10:00", "13:00", 2, true)

        assertNotNull(error)
        assertEquals(before, c.concreteShift(before.id))
    }

    @Test
    fun `REG-SHIFT-EDIT-RESERVED-002 cupo respeta reservas y permite aumento`() {
        val fixture = reservedFixture(capacity = 2)
        val c = fixture.controller

        assertNotNull(c.updateConcreteShift(fixture.shift.id, "09:00", "12:00", 0, true))
        assertNull(c.updateConcreteShift(fixture.shift.id, "09:00", "12:00", 4, true))
        assertEquals(4, c.concreteShift(fixture.shift.id)!!.capacity)
        assertEquals(1, c.concreteShiftReservations.count { it.status == ShiftReservationStatus.RESERVED })
    }

    @Test
    fun `REG-SHIFT-DISABLE-001 no deshabilita turno con reserva activa`() {
        val fixture = reservedFixture(capacity = 2)
        val c = fixture.controller

        val error = c.updateConcreteShift(fixture.shift.id, "09:00", "12:00", 2, false)

        assertNotNull(error)
        assertTrue(c.concreteShift(fixture.shift.id)!!.enabled)
    }

    @Test
    fun `REG-SHIFT-RESERVATION-DATE-001 reserva deriva fecha exclusivamente del ConcreteShift`() {
        val rider = approvedRider("RID-DATE")
        val password = "RiderDate123"
        LocalStore(context).saveRiders(listOf(rider))
        LocalStore(context).saveRiderCredentials(listOf(testCredential(rider.id, password)))
        val shift = ConcreteShift("CS-DATE", iso(LocalDate.now().plusDays(1)), 9 * 60, 12 * 60, 2)
        seedV2(shifts = listOf(shift))
        val c = MandadosController(context)
        assertTrue(c.authenticateRider(rider.id, password))

        assertTrue(c.reserveShift(rider.id, shift.id))
        val reservation = c.concreteShiftReservations.single()
        assertEquals(shift.id, reservation.concreteShiftId)
        assertFalse(ConcreteShiftReservation::class.java.declaredFields.any { it.name == "serviceDate" })
        assertEquals(shift.serviceDate, c.concreteShift(reservation.concreteShiftId)!!.serviceDate)
    }

    @Test
    fun `REG-SHIFT-ALPHA-RESET-001 elimina legado de Turnos reconcilia disponibilidad y conserva otros datos`() {
        val prefs = context.getSharedPreferences("mandados_alpha1", Context.MODE_PRIVATE)
        val rider = approvedRider("RID-LEGACY").copy(available = true, availableUntilAt = "31/12/2099 23:59:59")
        val customer = Customer("Cliente Persistente", "2345", "555555", "25 de Mayo", accountId = "DEV-CUSTOMER")
        val legacy = ShiftTemplate("LEGACY", 1, "09:00", "12:00", 2)
        val store = LocalStore(context)
        store.saveRiders(listOf(rider))
        store.saveCustomer(customer)
        store.saveShifts(listOf(legacy))
        store.saveShiftReservations(
            listOf(RiderShiftReservation("SHR-LEGACY", rider.id, legacy.id, "12/10/2026", "07/10/2026 08:00:00"))
        )

        val c = MandadosController(context)

        assertTrue(c.isShiftSubsystemReady())
        assertTrue(c.shiftRules.isEmpty())
        assertTrue(c.concreteShifts.isEmpty())
        assertTrue(c.concreteShiftReservations.isEmpty())
        assertFalse(c.rider(rider.id)!!.available)
        assertNull(c.rider(rider.id)!!.availableUntilAt)
        assertEquals(customer.id, c.customer?.id)
        assertFalse(prefs.contains("shift_templates"))
        assertFalse(prefs.contains("shift_reservations"))
        assertTrue(prefs.getBoolean("shift_v2_initialized", false))
    }

    @Test
    fun `REG-SHIFT-GEN-ATOMIC-001 conflicto en un candidato deja persistencia exactamente igual`() {
        val monday = nextOrSame(Day.MONDAY)
        val rules = listOf(
            ShiftGenerationRule("R1", setOf(1), 9 * 60, 12 * 60, 2),
            ShiftGenerationRule("R2", setOf(1), 13 * 60, 16 * 60, 2)
        )
        val foreign = ConcreteShift("FOREIGN", iso(monday), 14 * 60, 15 * 60, 1)
        seedV2(rules = rules, shifts = listOf(foreign))
        val c = MandadosController(context)
        val before = c.concreteShifts.toList()

        val result = c.confirmShiftGeneration(ShiftGenerationRequest(iso(monday), iso(monday)))

        assertFalse(result.canConfirm)
        assertTrue(result.conflicts.isNotEmpty())
        assertEquals(before, c.concreteShifts)
        assertEquals(before, MandadosController(context).concreteShifts)
    }

    private data class ReservedFixture(val controller: MandadosController, val shift: ConcreteShift)

    private fun reservedFixture(capacity: Int): ReservedFixture {
        val rider = approvedRider("RID-RESERVED")
        val shift = ConcreteShift("CS-RESERVED", iso(LocalDate.now().plusDays(1)), 9 * 60, 12 * 60, capacity, originRuleId = "R1")
        val reservation = ConcreteShiftReservation("CSR-RESERVED", rider.id, shift.id, "07/10/2026 08:00:00")
        LocalStore(context).saveRiders(listOf(rider))
        seedV2(
            rules = listOf(ShiftGenerationRule("R1", setOf(shiftDate(shift).dayOfWeek.value), 9 * 60, 12 * 60, capacity)),
            shifts = listOf(shift),
            reservations = listOf(reservation)
        )
        return ReservedFixture(MandadosController(context), shift)
    }

    private fun seedV2(
        rules: List<ShiftGenerationRule> = emptyList(),
        shifts: List<ConcreteShift> = emptyList(),
        reservations: List<ConcreteShiftReservation> = emptyList(),
        audit: List<ConcreteShiftAuditEvent> = emptyList()
    ) {
        val store = ShiftStoreV2(context)
        assertTrue(store.initializeIfNeeded().success)
        assertTrue(store.saveAll(rules, shifts, reservations, audit))
    }

    private fun approvedRider(id: String): RiderProfile = RiderProfile(
        id = id,
        name = "Repartidor $id",
        phone = "2345555001",
        vehicleType = VehicleType.MOTORCYCLE,
        documents = RiderDocuments(
            dniFrontUri = "content://dni-front",
            dniBackUri = "content://dni-back",
            motorcyclePlateUri = "content://plate",
            driverLicenseFrontUri = "content://license-front",
            driverLicenseBackUri = "content://license-back",
            vehicleCardUri = "content://vehicle-card",
            insuranceCardUri = "content://insurance"
        ),
        approvalStatus = RiderApprovalStatus.APPROVED,
        documentReviews = RiderDocumentKey.entries.associateWith { DocumentReviewStatus.APPROVED },
        active = true
    )

    private fun testCredential(riderId: String, password: String): RiderCredential {
        val salt = ("p25-$riderId-shift").toByteArray().copyOf(16)
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
            updatedAt = "07/10/2026 08:00:00"
        )
    }

    private fun iso(date: LocalDate): String = ShiftSchedulePolicy.toIsoDate(date)
    private fun shiftDate(shift: ConcreteShift): LocalDate = requireNotNull(ShiftSchedulePolicy.parseIsoDate(shift.serviceDate))

    private enum class Day(val value: Int) { MONDAY(1) }

    private fun nextOrSame(day: Day): LocalDate {
        var date = LocalDate.now()
        while (date.dayOfWeek.value != day.value) date = date.plusDays(1)
        return date
    }
}
