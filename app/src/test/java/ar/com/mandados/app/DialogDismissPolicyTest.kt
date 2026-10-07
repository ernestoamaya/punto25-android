package ar.com.mandados.app

import android.content.Context
import java.io.File
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DialogDismissPolicyTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("mandados_alpha1", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `REG-DIALOG-DISMISS-001 outside never dismisses and back remains enabled`() {
        val properties = punto25DialogProperties()
        assertFalse(properties.dismissOnClickOutside)
        assertTrue(properties.dismissOnBackPress)
        assertEquals(
            PendingEditDismissDecision.KEEP_OPEN,
            pendingEditDismissDecision(PendingEditDismissSource.OUTSIDE, dirty = false)
        )
        assertEquals(
            PendingEditDismissDecision.KEEP_OPEN,
            pendingEditDismissDecision(PendingEditDismissSource.OUTSIDE, dirty = true)
        )
    }

    @Test
    fun `REG-RIDER-EDIT-DIRTY-001 Alta limpia y nombre modificado queda dirty`() {
        val initial = riderEditSnapshot(null)
        assertFalse(isRiderEditDirty(initial, initial))
        assertTrue(isRiderEditDirty(initial, initial.copy(name = "Rider Nuevo")))
    }

    @Test
    fun `REG-RIDER-EDIT-DIRTY-002 campos editables y documento se detectan individualmente`() {
        val rider = sampleRider()
        val initial = riderEditSnapshot(rider)
        assertTrue(isRiderEditDirty(initial, initial.copy(phone = "2345550199")))
        assertTrue(isRiderEditDirty(initial, initial.copy(birthDate = "02/02/1992")))
        assertTrue(isRiderEditDirty(initial, initial.copy(vehicleType = VehicleType.BICYCLE)))
        assertTrue(isRiderEditDirty(initial, initial.copy(address = "Otra dirección")))
        assertTrue(isRiderEditDirty(initial, initial.copy(maxConcurrentOrdersOverride = 3)))
        assertTrue(isRiderEditDirty(initial, initial.copy(approvalStatus = RiderApprovalStatus.SUSPENDED)))
        assertTrue(
            isRiderEditDirty(
                initial,
                initial.copy(documents = initial.documents.copy(dniFrontUri = "content://nuevo-dni"))
            )
        )
    }

    @Test
    fun `REG-RIDER-EDIT-REVERT-001 cambio revertido exactamente vuelve a limpio`() {
        val initial = riderEditSnapshot(sampleRider())
        val changed = initial.copy(name = "Temporal")
        assertTrue(isRiderEditDirty(initial, changed))
        assertFalse(isRiderEditDirty(initial, changed.copy(name = initial.name)))
    }

    @Test
    fun `REG-RIDER-EDIT-REVERT-002 multiples cambios y reversion completa vuelve a limpio`() {
        val initial = riderEditSnapshot(sampleRider())
        val changed = initial.copy(
            phone = "1111111111",
            birthDate = "03/03/1993",
            vehicleType = VehicleType.BICYCLE,
            address = "Temporal",
            maxConcurrentOrdersOverride = 5,
            approvalStatus = RiderApprovalStatus.SUSPENDED,
            documents = initial.documents.copy(dniBackUri = "content://temporal")
        )
        assertTrue(isRiderEditDirty(initial, changed))
        assertFalse(isRiderEditDirty(initial, initial.copy()))
    }

    @Test
    fun `REG-RIDER-EDIT-DIRTY-003 evaluar dirty no muta RiderProfile original`() {
        val rider = sampleRider()
        val before = rider.copy(documents = rider.documents.copy())
        val snapshot = riderEditSnapshot(rider)
        val changed = snapshot.copy(name = "Otro", documents = snapshot.documents.copy(dniFrontUri = "content://otro"))
        assertTrue(isRiderEditDirty(snapshot, changed))
        assertEquals(before, rider)
    }

    @Test
    fun `REG-RIDER-EDIT-DISCARD-001 back y cancelar protegen dirty pero cierran clean`() {
        assertEquals(
            PendingEditDismissDecision.CLOSE,
            pendingEditDismissDecision(PendingEditDismissSource.BACK, dirty = false)
        )
        assertEquals(
            PendingEditDismissDecision.CLOSE,
            pendingEditDismissDecision(PendingEditDismissSource.CANCEL, dirty = false)
        )
        assertEquals(
            PendingEditDismissDecision.CONFIRM_DISCARD,
            pendingEditDismissDecision(PendingEditDismissSource.BACK, dirty = true)
        )
        assertEquals(
            PendingEditDismissDecision.CONFIRM_DISCARD,
            pendingEditDismissDecision(PendingEditDismissSource.CANCEL, dirty = true)
        )
    }

    @Test
    fun `REG-RIDER-EDIT-DISCARD-001 Rider wiring preserva seguir editando y descarta solo confirmado`() {
        val policySource = projectSource("src/main/java/ar/com/mandados/app/Punto25DialogPolicy.kt")
        val operationsSource = projectSource("src/main/java/ar/com/mandados/app/OperationsScreens.kt")
        val riderDialog = operationsSource
            .substringAfter("private fun RiderEditDialog(")
            .substringBefore("private fun RiderDocumentsDialog(")

        assertTrue(policySource.contains("TextButton(onClick = onDiscard) { Text(\"DESCARTAR CAMBIOS\") }"))
        assertTrue(policySource.contains("TextButton(onClick = onKeepEditing) { Text(\"SEGUIR EDITANDO\") }"))
        assertTrue(riderDialog.contains("onDismissRequest = { requestDismiss(PendingEditDismissSource.BACK) }"))
        assertTrue(riderDialog.contains("requestDismiss(PendingEditDismissSource.CANCEL)"))
        assertTrue(riderDialog.contains("onKeepEditing = { confirmDiscard = false }"))
        assertTrue(riderDialog.contains("onDiscard = { confirmDiscard = false; onDismiss() }"))
    }

    @Test
    fun `REG-RIDER-EDIT-SAVE-001 guardar Rider sigue persistiendo normalmente`() {
        val c = MandadosController(context)
        val id = c.saveRider(
            id = null,
            name = "Rider Guardado",
            phone = "2345550199",
            birthDate = "01/01/1990",
            vehicleType = VehicleType.MOTORCYCLE,
            address = "25 de Mayo",
            documents = RiderDocuments(dniFrontUri = "content://dni-front"),
            maxConcurrentOrdersOverride = 2
        )
        assertNotNull(id)
        val persisted = c.rider(id)
        assertNotNull(persisted)
        assertEquals("Rider Guardado", persisted!!.name)
        assertEquals("2345550199", persisted.phone)
        assertEquals("content://dni-front", persisted.documents.dniFrontUri)
        assertEquals(2, persisted.maxConcurrentOrdersOverride)
    }

    @Test
    fun `REG-RIDER-EDIT-SAVE-001 fallo de save no cierra Rider dialog`() {
        val operationsSource = projectSource("src/main/java/ar/com/mandados/app/OperationsScreens.kt")
        val adminScreen = operationsSource
            .substringAfter("internal fun RidersAdminScreenV2(")
            .substringBefore("private fun RiderEditDialog(")
        val riderDialog = operationsSource
            .substringAfter("private fun RiderEditDialog(")
            .substringBefore("private fun RiderDocumentsDialog(")

        assertTrue(adminScreen.contains("if (savedId != null)"))
        assertTrue(adminScreen.contains("editOpen = false"))
        assertTrue(adminScreen.contains("} else {\n                    false"))
        assertTrue(riderDialog.contains("val saved = onSave("))
        assertTrue(riderDialog.contains("if (!saved) saveError ="))
        assertFalse(riderDialog.contains("if (!saved) onDismiss()"))
    }

    private fun projectSource(relativePath: String): String {
        val candidates = listOf(
            File(relativePath),
            File("app/$relativePath"),
            File("../app/$relativePath")
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: error("No se encontró source de regresión: $relativePath")
        return file.readText()
    }

    private fun sampleRider(): RiderProfile = RiderProfile(
        id = "RID-DIRTY",
        name = "Rider Original",
        phone = "2345550101",
        birthDate = "01/01/1991",
        vehicleType = VehicleType.MOTORCYCLE,
        address = "Dirección original",
        maxConcurrentOrdersOverride = 2,
        documents = RiderDocuments(
            dniFrontUri = "content://dni-front",
            dniBackUri = "content://dni-back"
        ),
        approvalStatus = RiderApprovalStatus.APPROVED
    )
}
