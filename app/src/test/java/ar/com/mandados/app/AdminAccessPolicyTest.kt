package ar.com.mandados.app

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdminAccessPolicyTest {
    @Test
    fun `REG-ADMIN-ENTRY-001 REGISTER expone ruta explicita solo hacia ADMIN_LOGIN`() {
        val app = projectFile("app/src/main/java/ar/com/mandados/app/MandadosApp.kt")

        assertTrue(app.contains("Text(\"ADMINISTRACIÓN\""))
        assertTrue(app.contains("adminLoginReturnScreen = Screen.REGISTER\n                screen = Screen.ADMIN_LOGIN"))
        assertTrue(app.contains("Screen.ADMIN_LOGIN -> adminLoginReturnScreen"))
        assertTrue(app.contains("screen = adminLoginReturnScreen"))
    }

    @Test
    fun `REG-ADMIN-AUTH-001 no existe bypass ni credencial Admin alternativa`() {
        val gradle = projectFile("app/build.gradle.kts")
        val app = projectFile("app/src/main/java/ar/com/mandados/app/MandadosApp.kt")
        val auth = projectFile("app/src/main/java/ar/com/mandados/app/AuthIntegration.kt")

        assertFalse(gradle.contains("ALPHA_ADMIN_PIN"))
        assertFalse(app.contains("ALPHA_ADMIN_PIN"))
        assertFalse(auth.contains("ALPHA_ADMIN_PIN"))
        assertFalse(app.contains("onAdmin = { screen = Screen.ADMIN }"))
        assertFalse(app.contains("onAdmin = {\n                screen = Screen.ADMIN"))
        assertTrue(app.contains("onAdmin = {\n                adminSession.clear()"))
        assertTrue(app.contains("screen = Screen.ADMIN_LOGIN"))
    }

    @Test
    fun `REG-ADMIN-ANDROID-001 acceso Admin no compara PIN local`() {
        val app = projectFile("app/src/main/java/ar/com/mandados/app/MandadosApp.kt")
        val auth = projectFile("app/src/main/java/ar/com/mandados/app/AuthIntegration.kt")

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
    fun `REG-ADMIN-ANDROID-003 backend 401 o 403 no autoriza navegacion`() = runBlocking {
        listOf(401, 403).forEach { status ->
            val result = evaluateAdminAccess(
                tokenProvider = { "synthetic-token" },
                requester = { AdminAccessHttpResult(status, null) }
            )
            val session = AdminAccessSession().also { it.apply(result) }

            assertEquals(AdminAccessResult.UNAUTHORIZED, result)
            assertFalse(session.authorized)
        }
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
        session.apply(falsePayload)
        assertFalse(session.authorized)
        assertEquals(AdminAccessResult.UNAVAILABLE, serverError)
        session.apply(serverError)
        assertFalse(session.authorized)
    }

    @Test
    fun `REG-ADMIN-ANDROID-006 salir invalida autorizacion transitoria`() {
        val session = AdminAccessSession()
        session.apply(AdminAccessResult.AUTHORIZED)
        assertTrue(session.authorized)

        session.clear()
        assertFalse(session.authorized)

        val app = projectFile("app/src/main/java/ar/com/mandados/app/MandadosApp.kt")
        assertTrue(app.contains("onBack = {\n                adminSession.clear()\n                screen = adminLoginReturnScreen"))
        assertTrue(app.contains("adminLoginReturnScreen = Screen.HOME"))
        assertTrue(app.contains("adminLoginReturnScreen = Screen.REGISTER"))
        assertTrue(app.contains("onLogout = {\n                adminSession.clear()"))
    }

    @Test
    fun `REG-ADMIN-ANDROID-007 recreacion no conserva autorizacion Admin`() {
        val previousProcess = AdminAccessSession().also { it.apply(AdminAccessResult.AUTHORIZED) }
        val recreatedProcess = AdminAccessSession()

        assertTrue(previousProcess.authorized)
        assertFalse(recreatedProcess.authorized)

        val app = projectFile("app/src/main/java/ar/com/mandados/app/MandadosApp.kt")
        assertTrue(app.contains("val adminSession = remember { AdminAccessSession() }"))
        assertFalse(app.contains("rememberSaveable { AdminAccessSession()"))
        assertTrue(app.contains("requiresAdminReauthorization"))
        assertTrue(app.contains("!GoogleAuthIntegration.hasCurrentUser(context)"))
    }

    private fun projectFile(repoRelativePath: String): String {
        val candidates = listOf(
            File(repoRelativePath),
            File("../$repoRelativePath"),
            File("../../$repoRelativePath")
        )
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("No se encontró source de regresión: $repoRelativePath")
    }
}
