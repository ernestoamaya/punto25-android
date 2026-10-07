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
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ShiftReservationV2IntegrationTest {
    private lateinit var context: Context
    private val timestamp = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("mandados_alpha1", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `REG-SHIFT-EDIT-RESERVED-002 cupo puede bajar hasta reservedCount y no menos`() {
        val shift = futureShift("CS-CAP", capacity = 3)
        val riders = listOf(approvedRider("RID-CAP-A"), approvedRider("RID-CAP-B"))
        seed(
            riders = riders,
            shifts = listOf(shift),
            reservations = listOf(
                ConcreteShiftReservation("CSR-CAP-A", riders[0].id, shift.id, nowText()),
                ConcreteShiftReservation("CSR-CAP-B", riders[1].id, shift.id, nowText())
            )
        )
        val c = MandadosController(context)

        assertNotNull(c.updateConcreteShift(shift.id, "09:00", "12:00", 1, true))
        assertEquals(3, c.concreteShift(shift.id)!!.capacity)
        assertEquals(null, c.updateConcreteShift(shift.id, "09:00", "12:00", 2, true))
        assertEquals(2, c.concreteShift(shift.id)!!.capacity)
        assertEquals(null, c.updateConcreteShift(shift.id, "09:00", "12:00", 4, true))
        assertEquals(4, c.concreteShift(shift.id)!!.capacity)
    }

    @Test
    fun `REG-SHIFT-CANCEL-001 cancelacion reciente impone cooldown de 15 minutos`() {
        val rider = approvedRider("RID-COOLDOWN")
        val password = "Cooldown123"
        val shift = futureShift("CS-COOLDOWN", capacity = 2)
        val cancelled = ConcreteShiftReservation(
            id = "CSR-COOLDOWN",
            riderId = rider.id,
            concreteShiftId = shift.id,
            joinedAt = LocalDateTime.now().minusMinutes(2).format(timestamp),
            status = ShiftReservationStatus.CANCELLED,
            cancelledAt = nowText(),
            cancellationCount = 1,
            lastCancelledAt = nowText(),
            blockedRejoin = false
        )
        seed(listOf(rider), listOf(shift), listOf(cancelled), credentials = mapOf(rider.id to password))
        val c = MandadosController(context)
        assertTrue(c.authenticateRider(rider.id, password))

        val decision = c.reserveShiftWithDecision(rider.id, shift.id)

        assertFalse(decision.allowed)
        assertEquals(RiderDenialReason.REJOIN_COOLDOWN, decision.reason)
        assertEquals(ShiftReservationStatus.CANCELLED, c.concreteShiftReservations.single().status)
    }

    @Test
    fun `REG-SHIFT-CANCEL-002 segunda cancelacion bloquea nueva reinscripcion`() {
        val rider = approvedRider("RID-SECOND-CANCEL")
        val password = "SecondCancel123"
        val shift = futureShift("CS-SECOND-CANCEL", capacity = 2)
        val oldCancellation = ConcreteShiftReservation(
            id = "CSR-SECOND-CANCEL",
            riderId = rider.id,
            concreteShiftId = shift.id,
            joinedAt = LocalDateTime.now().minusHours(1).format(timestamp),
            status = ShiftReservationStatus.CANCELLED,
            cancelledAt = LocalDateTime.now().minusMinutes(16).format(timestamp),
            cancellationCount = 1,
            lastCancelledAt = LocalDateTime.now().minusMinutes(16).format(timestamp),
            blockedRejoin = false
        )
        seed(listOf(rider), listOf(shift), listOf(oldCancellation), credentials = mapOf(rider.id to password))
        val c = MandadosController(context)
        assertTrue(c.authenticateRider(rider.id, password))

        assertTrue(c.reserveShift(rider.id, shift.id))
        assertTrue(c.cancelConcreteShift(oldCancellation.id))
        val cancelledTwice = c.concreteShiftReservations.single()
        assertEquals(2, cancelledTwice.cancellationCount)
        assertTrue(cancelledTwice.blockedRejoin)

        val thirdAttempt = c.reserveShiftWithDecision(rider.id, shift.id)
        assertFalse(thirdAttempt.allowed)
        assertEquals(RiderDenialReason.REJOIN_BLOCKED, thirdAttempt.reason)
    }

    @Test
    fun `REG-SHIFT-CANCEL-003 cancelar unico turno activo desactiva disponibilidad y persiste auditoria`() {
        val rider = approvedRider("RID-ACTIVE-CANCEL").copy(
            available = true,
            availableUntilAt = LocalDateTime.now().plusMinutes(30).format(timestamp)
        )
        val password = "ActiveCancel123"
        val shift = activeShift("CS-ACTIVE-CANCEL")
        val reservation = ConcreteShiftReservation(
            id = "CSR-ACTIVE-CANCEL",
            riderId = rider.id,
            concreteShiftId = shift.id,
            joinedAt = LocalDateTime.now().minusMinutes(1).format(timestamp)
        )
        seed(listOf(rider), listOf(shift), listOf(reservation), credentials = mapOf(rider.id to password))
        val c = MandadosController(context)
        assertTrue(c.authenticateRider(rider.id, password))
        assertTrue(c.riderHasActiveShiftNow(rider.id))
        assertTrue(c.rider(rider.id)!!.available)

        assertTrue(c.cancelConcreteShift(reservation.id))

        assertFalse(c.riderHasActiveShiftNow(rider.id))
        assertFalse(c.rider(rider.id)!!.available)
        assertEquals(ShiftReservationStatus.CANCELLED, c.concreteShiftReservations.single().status)
        assertEquals(ShiftEventType.RIDER_CANCELLED, c.concreteShiftAuditEvents.single().type)

        val restarted = MandadosController(context)
        assertFalse(restarted.rider(rider.id)!!.available)
        assertEquals(ShiftReservationStatus.CANCELLED, restarted.concreteShiftReservations.single().status)
        assertEquals(ShiftEventType.RIDER_CANCELLED, restarted.concreteShiftAuditEvents.single().type)
    }

    private fun seed(
        riders: List<RiderProfile>,
        shifts: List<ConcreteShift>,
        reservations: List<ConcreteShiftReservation>,
        credentials: Map<String, String> = emptyMap()
    ) {
        val shiftStore = ShiftStoreV2(context)
        assertTrue(shiftStore.initializeIfNeeded().success)
        LocalStore(context).saveRiders(riders)
        if (credentials.isNotEmpty()) {
            LocalStore(context).saveRiderCredentials(credentials.map { (riderId, password) -> credential(riderId, password) })
        }
        assertTrue(shiftStore.saveAll(emptyList(), shifts, reservations, emptyList()))
    }

    private fun futureShift(id: String, capacity: Int): ConcreteShift = ConcreteShift(
        id = id,
        serviceDate = ShiftSchedulePolicy.toIsoDate(LocalDate.now().plusDays(1)),
        startMinute = 9 * 60,
        endMinute = 12 * 60,
        capacity = capacity
    )

    private fun activeShift(id: String): ConcreteShift {
        val now = LocalDateTime.now()
        val start = (now.hour * 60 + now.minute - 30).coerceAtLeast(0)
        var end = now.hour * 60 + now.minute + 30
        var date = now.toLocalDate()
        if (end > 1_440) end -= 1_440
        if (start > now.hour * 60 + now.minute) date = date.minusDays(1)
        if (start == end) end = (end + 60) % 1_440
        return ConcreteShift(id, ShiftSchedulePolicy.toIsoDate(date), start, end, 2)
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

    private fun credential(riderId: String, password: String): RiderCredential {
        val salt = ("p25-$riderId-reservation").toByteArray().copyOf(16)
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
            updatedAt = nowText()
        )
    }

    private fun nowText(): String = LocalDateTime.now().format(timestamp)
}
