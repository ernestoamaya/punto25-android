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
        assertTrue(app.contains("onAdmin = {\n                adminSession.clear()\n                screen = Screen.ADMIN_LOGIN"))
        assertTrue(app.contains("Screen.ADMIN_LOGIN -> Screen.REGISTER"))
        assertTrue(app.contains("screen = Screen.REGISTER"))
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
        assertTrue(auth.contains("AdminGoogleAuthIntegration.currentIdToken(context)"))
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
        assertTrue(app.contains("onBack = {\n                adminSession.clear()\n                AdminGoogleAuthIntegration.signOut(context)\n                screen = Screen.REGISTER"))
        assertFalse(app.contains("adminLoginReturnScreen"))
        assertTrue(app.contains("Screen.ADMIN_LOGIN -> Screen.REGISTER"))
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
        assertTrue(app.contains("!AdminGoogleAuthIntegration.hasCurrentUser(context)"))
    }


    @Test
    fun `REG-ADMIN-SESSION-ISOLATION-001 secondary Firebase app is distinct`() {
        val auth = projectFile("app/src/main/java/ar/com/mandados/app/AuthIntegration.kt")
        assertTrue(auth.contains("APP_NAME = \"punto25-admin-auth\""))
        assertTrue(auth.contains("APP_NAME != FirebaseApp.DEFAULT_APP_NAME"))
        assertTrue(auth.contains("FirebaseApp.initializeApp(context.applicationContext, options, APP_NAME)"))
        assertTrue(auth.contains("FirebaseAuth.getInstance(ensureAdminApp(activity))"))
    }

    @Test
    fun `REG-ADMIN-SESSION-ISOLATION-002 backend gate only uses Admin token`() {
        val auth = projectFile("app/src/main/java/ar/com/mandados/app/AuthIntegration.kt")
        val gate = auth.substringAfter("internal object AdminAccessApi {").substringBefore("internal object WhatsAppVerificationApi")
        assertTrue(gate.contains("tokenProvider = { AdminGoogleAuthIntegration.currentIdToken(context) }"))
        val tokenProviderLines = gate.lineSequence().map { it.trim() }
            .filter { it.startsWith("tokenProvider =") }.toList()
        assertEquals(1, tokenProviderLines.size)
        assertEquals(
            "tokenProvider = { AdminGoogleAuthIntegration.currentIdToken(context) }",
            tokenProviderLines.single().removeSuffix(",")
        )
        assertTrue(gate.contains("/v1/admin/access"))
    }

    @Test
    fun `REG-ADMIN-SESSION-ISOLATION-003 Admin never signs out customer`() {
        val auth = projectFile("app/src/main/java/ar/com/mandados/app/AuthIntegration.kt")
        val admin = auth.substringAfter("internal object AdminGoogleAuthIntegration {").substringBefore("internal object AdminAccessApi")
        assertFalse(admin.contains("GoogleAuthIntegration.signOut"))
        assertFalse(admin.contains("FirebaseAuth.getInstance(app).signOut"))
        assertTrue(admin.contains("FirebaseAuth.getInstance(it).signOut()"))
        val app = projectFile("app/src/main/java/ar/com/mandados/app/MandadosApp.kt")
        assertFalse(app.contains("adminLoginReturnScreen"))
    }

    @Test
    fun `REG-ADMIN-ACCOUNT-SWITCH-001 unauthorized offers explicit new selection`() {
        val app = projectFile("app/src/main/java/ar/com/mandados/app/MandadosApp.kt")
        val auth = projectFile("app/src/main/java/ar/com/mandados/app/AuthIntegration.kt")
        assertTrue(app.contains("Text(\"USAR OTRA CUENTA\")"))
        assertFalse(app.contains("VOLVER A VERIFICAR"))
        assertTrue(app.contains("AdminGoogleAuthIntegration.signOut(context)"))
        assertTrue(auth.contains(".setAutoSelectEnabled(false)"))
        assertTrue(app.contains("if (checked == AdminAccessResult.AUTHORIZED)"))
    }

    @Test
    fun `REG-ADMIN-HOME-001 Home does not expose Admin`() {
        val app = projectFile("app/src/main/java/ar/com/mandados/app/MandadosApp.kt")
        val home = app.substringAfter("private fun HomeScreen(").substringBefore("@Composable\nprivate fun ServiceCard")
        assertFalse(home.contains("onAdmin"))
        assertFalse(home.contains("ADMINISTRACIÓN"))
        assertTrue(home.contains("Cambiar usuario"))
    }

    @Test
    fun `REG-ADMIN-ENTRY-002 REGISTER routes through login only`() {
        val app = projectFile("app/src/main/java/ar/com/mandados/app/MandadosApp.kt")
        val register = app.substringAfter("Screen.REGISTER -> RegisterScreen(").substringBefore("Screen.WHATSAPP_VERIFY")
        assertTrue(register.contains("screen = Screen.ADMIN_LOGIN"))
        assertFalse(register.contains("screen = Screen.ADMIN\n"))
    }

    @Test
    fun `REG-ADMIN-FAILCLOSED-001 invalid and missing tokens cannot authorize`() = runBlocking {
        for (token in listOf(null, "", " ")) {
            var called = false
            val result = evaluateAdminAccess(
                tokenProvider = { token },
                requester = { called = true; AdminAccessHttpResult(200, true) }
            )
            assertFalse(AdminAccessSession().also { it.apply(result) }.authorized)
            assertFalse(called)
        }
        for (response in listOf(AdminAccessHttpResult(401, null), AdminAccessHttpResult(403, null),
            AdminAccessHttpResult(200, null), AdminAccessHttpResult(200, false),
            AdminAccessHttpResult(500, true))) {
            val result = evaluateAdminAccess({ "token" }, { response })
            assertFalse(AdminAccessSession().also { it.apply(result) }.authorized)
        }
    }

    @Test
    fun `REG-ADMIN-EXIT-001 exit clears secondary session only`() {
        val app = projectFile("app/src/main/java/ar/com/mandados/app/MandadosApp.kt")
        assertTrue(app.contains("adminSession.clear()\n                AdminGoogleAuthIntegration.signOut(context)\n                screen = Screen.REGISTER"))
        assertFalse(app.contains("screen = Screen.HOME\n            },\n            onOrders"))
    }

    @Test
    fun `REG-ADMIN-RECREATE-001 persisted auth does not silently grant Admin`() {
        val app = projectFile("app/src/main/java/ar/com/mandados/app/MandadosApp.kt")
        val login = app.substringAfter("private fun AdminLoginScreen(").substringBefore("private fun AdminScreen(")
        assertFalse(login.contains("LaunchedEffect"))
        assertTrue(login.contains("INGRESAR CON GOOGLE"))
        assertTrue(app.contains("val adminSession = remember { AdminAccessSession() }"))
        assertFalse(app.contains("rememberSaveable { AdminAccessSession()"))
        val fresh = AdminAccessSession()
        assertFalse(fresh.authorized)
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
