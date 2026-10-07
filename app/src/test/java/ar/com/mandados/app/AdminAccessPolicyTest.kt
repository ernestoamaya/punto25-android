package ar.com.mandados.app

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdminAccessPolicyTest {
    @Test
    fun `REG-ADMIN-AUTH-001 no existe ALPHA_ADMIN_PIN como credencial operativa`() {
        val gradle = projectSource("build.gradle.kts")
        val app = projectSource("src/main/java/ar/com/mandados/app/MandadosApp.kt")
        val auth = projectSource("src/main/java/ar/com/mandados/app/AuthIntegration.kt")

        assertFalse(gradle.contains("ALPHA_ADMIN_PIN"))
        assertFalse(app.contains("ALPHA_ADMIN_PIN"))
        assertFalse(auth.contains("ALPHA_ADMIN_PIN"))
    }

    @Test
    fun `REG-ADMIN-ANDROID-001 acceso Admin no compara PIN local`() {
        val app = projectSource("src/main/java/ar/com/mandados/app/MandadosApp.kt")
        val auth = projectSource("src/main/java/ar/com/mandados/app/AuthIntegration.kt")

        assertFalse(app.contains("PIN Alpha"))
        assertFalse(app.contains("expectedPin"))
        assertFalse(app.contains("BuildConfig.ALPHA_ADMIN_PIN"))
        assertTrue(app.contains("AdminAccessApi.check(context)"))
        assertTrue(auth.contains("/v1/admin/access"))
        assertTrue(auth.contains("GoogleAuthIntegration.currentIdToken(context)"))
    }

    @Test
    fun `REG-ADMIN-ANDROID-002 sin sesion Firebase acceso denegado`() = runBlocking {
        var requesterCalled = false
        val result = evaluateAdminAccess(
            tokenProvider = { null },
            requester = {
                requesterCalled = true
                AdminAccessHttpResult(200, true)
            }
        )

        assertEquals(AdminAccessResult.UNAUTHORIZED, result)
        assertFalse(requesterCalled)
    }

    @Test
    fun `REG-ADMIN-ANDROID-003 backend 403 no autoriza navegacion`() = runBlocking {
        val result = evaluateAdminAccess(
            tokenProvider = { "synthetic-token" },
            requester = { AdminAccessHttpResult(403, null) }
        )
        val session = AdminAccessSession().also { it.apply(result) }

        assertEquals(AdminAccessResult.UNAUTHORIZED, result)
        assertFalse(session.authorized)
    }

    @Test
    fun `REG-ADMIN-ANDROID-004 error de red o backend no autoriza`() = runBlocking {
        val result = evaluateAdminAccess(
            tokenProvider = { "synthetic-token" },
            requester = { throw IllegalStateException("synthetic network failure") }
        )
        val session = AdminAccessSession().also { it.apply(result) }

        assertEquals(AdminAccessResult.UNAVAILABLE, result)
        assertFalse(session.authorized)
    }

    @Test
    fun `REG-ADMIN-ANDROID-005 solo respuesta positiva autoriza`() = runBlocking {
        val authorized = evaluateAdminAccess(
            tokenProvider = { "synthetic-token" },
            requester = { AdminAccessHttpResult(200, true) }
        )
        val falsePayload = evaluateAdminAccess(
            tokenProvider = { "synthetic-token" },
            requester = { AdminAccessHttpResult(200, false) }
        )
        val serverError = evaluateAdminAccess(
            tokenProvider = { "synthetic-token" },
            requester = { AdminAccessHttpResult(500, null) }
        )

        val session = AdminAccessSession().also { it.apply(authorized) }
        assertEquals(AdminAccessResult.AUTHORIZED, authorized)
        assertTrue(session.authorized)
        assertEquals(AdminAccessResult.UNAVAILABLE, falsePayload)
        assertEquals(AdminAccessResult.UNAVAILABLE, serverError)
    }

    @Test
    fun `REG-ADMIN-ANDROID-006 salir invalida autorizacion transitoria`() {
        val session = AdminAccessSession()
        session.apply(AdminAccessResult.AUTHORIZED)
        assertTrue(session.authorized)

        session.clear()
        assertFalse(session.authorized)

        val app = projectSource("src/main/java/ar/com/mandados/app/MandadosApp.kt")
        assertTrue(app.contains("onBack = {\n                adminSession.clear()\n                screen = Screen.HOME"))
        assertTrue(app.contains("onLogout = {\n                adminSession.clear()"))
    }

    @Test
    fun `REG-ADMIN-ANDROID-007 recreacion no conserva autorizacion Admin`() {
        val previousProcess = AdminAccessSession().also { it.apply(AdminAccessResult.AUTHORIZED) }
        val recreatedProcess = AdminAccessSession()

        assertTrue(previousProcess.authorized)
        assertFalse(recreatedProcess.authorized)

        val app = projectSource("src/main/java/ar/com/mandados/app/MandadosApp.kt")
        assertTrue(app.contains("val adminSession = remember { AdminAccessSession() }"))
        assertFalse(app.contains("rememberSaveable { AdminAccessSession()"))
        assertTrue(app.contains("requiresAdminReauthorization"))
        assertTrue(app.contains("!GoogleAuthIntegration.hasCurrentUser(context)"))
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
