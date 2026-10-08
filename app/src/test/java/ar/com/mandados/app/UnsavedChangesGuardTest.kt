package ar.com.mandados.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnsavedChangesGuardTest {

    @Test
    fun `REG-UNSAVED-GUARD-001 clean exits and dirty intercepts exit`() {
        var exits = 0
        val clean = UnsavedChangesGuardState()
        clean.requestExit(dirty = false) { exits += 1 }
        assertEquals(1, exits)
        assertFalse(clean.hasPendingExit)

        val dirty = UnsavedChangesGuardState()
        dirty.requestExit(dirty = true) { exits += 1 }
        assertEquals(1, exits)
        assertTrue(dirty.hasPendingExit)

        val policy = projectSource("app/src/main/java/ar/com/mandados/app/Punto25DialogPolicy.kt")
        assertTrue(policy.contains("BackHandler(enabled = enabled)"))
        assertTrue(policy.contains("state.requestExit(dirty, onBack)"))
        assertTrue(policy.contains("DiscardChangesDialog("))
    }

    @Test
    fun `REG-UNSAVED-KEEP-001 keep editing preserves snapshot and cancels pending exit`() {
        val baseline = OrderDraft(originAddress = "Original")
        val current = baseline.copy(originAddress = "Editado", notes = "Pendiente")
        var editable = current
        var exits = 0
        val guard = UnsavedChangesGuardState()

        guard.requestExit(dirty = editable != baseline) { exits += 1 }
        guard.keepEditing()

        assertEquals(current, editable)
        assertEquals(0, exits)
        assertFalse(guard.hasPendingExit)
    }

    @Test
    fun `REG-UNSAVED-DISCARD-001 discard runs no persistence and exits exactly once`() {
        var firstExit = 0
        var secondExit = 0
        var discardCallbacks = 0
        var persistedWrites = 0
        val guard = UnsavedChangesGuardState()

        guard.requestExit(dirty = true) { firstExit += 1 }
        guard.requestExit(dirty = true) { secondExit += 1 }
        guard.discard { discardCallbacks += 1 }
        guard.discard {
            discardCallbacks += 1
            persistedWrites += 1
        }

        assertEquals(1, firstExit)
        assertEquals(0, secondExit)
        assertEquals(1, discardCallbacks)
        assertEquals(0, persistedWrites)
        assertFalse(guard.hasPendingExit)
    }

    @Test
    fun `REG-ORDER-DRAFT-UNSAVED-001 full OrderDraft baseline survives map and review wiring`() {
        val baseline = OrderDraft(
            category = ServiceCategory.PURCHASE,
            purchaseDescription = "Original",
            storeLocation = GeoPoint(-35.43, -60.17),
            destinationAddress = "Destino",
            notes = "Nota"
        )
        val changed = baseline.copy(notes = "Otra nota")
        assertTrue(changed != baseline)
        assertEquals(baseline, changed.copy(notes = baseline.notes))

        val app = projectSource("app/src/main/java/ar/com/mandados/app/MandadosApp.kt")
        assertTrue(app.contains("val controller = rememberSaveable("))
        assertTrue(app.contains("saver = mandadosControllerSaver(context.applicationContext)"))
        assertTrue(app.contains("var orderDraftBaseline by rememberSaveable(saver = orderDraftBaselineStateSaver)"))
        assertTrue(app.contains("controller.resetDraftForCategory(category)\n                orderDraftBaseline = controller.draft"))
        assertTrue(app.contains("baseline = orderDraftBaseline ?: controller.draft"))
        assertTrue(app.contains("val dirty = d != baseline"))
        assertTrue(app.contains("onDiscard = { c.draft = baseline }"))

        val openMap = app.substringAfter("fun openMap(target: MapTarget)").substringBefore("fun parentForMap")
        assertFalse(openMap.contains("orderDraftBaseline ="))
        val reviewRoute = app.substringAfter("Screen.REVIEW -> ReviewScreen").substringBefore("Screen.SUBMITTED")
        assertFalse(reviewRoute.substringBefore("OrderCreationResult.Created").contains("orderDraftBaseline ="))
        assertTrue(reviewRoute.contains("orderDraftBaseline = null"))
    }

    @Test
    fun `REG-RIDER-PROFILE-UNSAVED-001 alias and every password field are dirty and exits are guarded`() {
        val clean = RiderProfileEditSnapshot(alias = "rider.alias")
        assertFalse(isRiderProfileEditDirty("rider.alias", clean))
        assertTrue(isRiderProfileEditDirty("rider.alias", clean.copy(alias = "otro.alias")))
        assertTrue(isRiderProfileEditDirty("rider.alias", clean.copy(currentPassword = "Actual123")))
        assertTrue(isRiderProfileEditDirty("rider.alias", clean.copy(newPassword = "Nueva123")))
        assertTrue(isRiderProfileEditDirty("rider.alias", clean.copy(confirmPassword = "Nueva123")))

        val operations = projectSource("app/src/main/java/ar/com/mandados/app/OperationsScreens.kt")
        val dashboard = operations.substringAfter("internal fun RiderDashboardScreen(").substringBefore("private fun RiderHome(")
        val profile = operations.substringAfter("private fun RiderProfileView(").substringBefore("internal fun AdminShiftsLegacyScreen(")
        assertTrue(dashboard.contains("profileExitGuard.requestExit(profileDirty)"))
        assertTrue(dashboard.contains("enabled = c.hasAuthenticatedRiderSession(rider.id)"))
        assertTrue(profile.contains("onSection(RiderSection.PERMISSIONS)"))
        assertTrue(profile.contains("onSection(RiderSection.SUPPORT)"))
        assertTrue(profile.contains("No se pudo actualizar el alias."))
        assertTrue(profile.contains("La contraseña actual no es correcta."))
    }

    @Test
    fun `REG-CUSTOMER-REG-UNSAVED-001 registration guards exits and discard clears only pending registration`() {
        val app = projectSource("app/src/main/java/ar/com/mandados/app/MandadosApp.kt")
        val register = app.substringAfter("private fun RegisterScreen(").substringBefore("private fun WhatsAppVerificationScreen(")
        val discard = register.substringAfter("fun discardRegistrationEdits()").substringBefore("UnsavedChangesGuard(")

        assertTrue(register.contains("name.isNotBlank() || area.isNotBlank() || sub.isNotBlank() || c.pendingCustomer != null"))
        assertTrue(register.contains("exitGuard.requestExit(dirty, onRider)"))
        assertTrue(register.contains("exitGuard.requestExit(dirty, onAdmin)"))
        assertTrue(register.contains("onBack = onBack"))
        assertTrue(discard.contains("c.pendingCustomer = null"))
        assertFalse(discard.contains("logoutCustomer"))
        assertFalse(discard.contains("signOut"))
        assertFalse(discard.contains("customer ="))
        assertFalse(discard.contains("applyGoogleIdentity"))
    }

    @Test
    fun `REG-UNSAVED-AUTH-001 session invalidation stays ahead of dirty guards`() {
        val app = projectSource("app/src/main/java/ar/com/mandados/app/MandadosApp.kt")
        val operations = projectSource("app/src/main/java/ar/com/mandados/app/OperationsScreens.kt")

        val backHandler = app.substringAfter("BackHandler(enabled = true)").substringBefore("if (requiresAdminReauthorization) {\n        AdminLoginScreen")
        assertTrue(backHandler.trimStart().startsWith("{\n        if (requiresAdminReauthorization)"))

        val riderRoute = app.substringAfterLast("Screen.RIDER_WORKSPACE ->").substringBefore("Screen.LOCATION_PICKER")
        assertTrue(riderRoute.contains("if (!riderWorkspaceSessionValid(controller, riderId))"))
        assertTrue(riderRoute.indexOf("if (!riderWorkspaceSessionValid(controller, riderId))") < riderRoute.indexOf("RiderDashboardScreen("))
        assertTrue(operations.contains("enabled = c.hasAuthenticatedRiderSession(rider.id)"))
    }

    @Test
    fun `REG-UNSAVED-SAVEFAIL-001 failed saves do not clean baseline or close editable state`() {
        val app = projectSource("app/src/main/java/ar/com/mandados/app/MandadosApp.kt")
        val shifts = projectSource("app/src/main/java/ar/com/mandados/app/ShiftScreensV2.kt")
        val operations = projectSource("app/src/main/java/ar/com/mandados/app/OperationsScreens.kt")

        val review = app.substringAfter("Screen.REVIEW -> ReviewScreen").substringBefore("Screen.SUBMITTED")
        assertTrue(review.contains("is OrderCreationResult.Blocked -> result.message"))
        assertFalse(review.substringAfter("is OrderCreationResult.Blocked -> result.message").substringBefore("is OrderCreationResult.Created").contains("orderDraftBaseline = null"))

        val generationConfirm = shifts.substringAfter("val result = c.confirmShiftGeneration(currentPreview.request)").substringBefore("message = when")
        assertTrue(generationConfirm.contains("if (result.error == null && result.conflicts.isEmpty())"))
        assertTrue(generationConfirm.contains("generationBaseline ="))

        val profile = operations.substringAfter("private fun RiderProfileView(").substringBefore("internal fun AdminShiftsLegacyScreen(")
        assertTrue(profile.contains("No se pudo actualizar el alias."))
        assertTrue(profile.contains("La contraseña actual no es correcta."))
        assertFalse(profile.contains("No se pudo actualizar el alias.\"\n            }\n            onEdit("))
    }

    private fun projectSource(repoRelativePath: String): String {
        val candidates = listOf(
            File(repoRelativePath),
            File("../$repoRelativePath"),
            File("../../$repoRelativePath")
        )
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("No se encontró source de regresión: $repoRelativePath")
    }
}
