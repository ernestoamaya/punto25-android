package ar.com.mandados.app

import android.content.Context
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RiderAuthSessionP23A2Test {
    private lateinit var firebase: FakeRiderFirebase
    private lateinit var backend: FakeRiderBackend
    private lateinit var cache: FakeRiderCache
    private lateinit var coordinator: RiderAuthSessionCoordinator
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("mandados_alpha1", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("punto25_rider_session_cache", Context.MODE_PRIVATE).edit().clear().commit()
        RiderSessionAuthority.clear()
        firebase = FakeRiderFirebase()
        backend = FakeRiderBackend()
        cache = FakeRiderCache()
        coordinator = RiderAuthSessionCoordinator(firebase, backend, cache)
    }

    @After
    fun tearDown() {
        RiderSessionAuthority.clear()
    }

    @Test
    fun `REG-P23A2-FIREBASE-ISOLATION-001 Rider Firebase app is distinct from Customer and Admin`() {
        assertNotEquals(FirebaseApp.DEFAULT_APP_NAME, RiderFirebaseAuthIntegration.APP_NAME)
        assertNotEquals(AdminGoogleAuthIntegration.APP_NAME, RiderFirebaseAuthIntegration.APP_NAME)
    }

    @Test
    fun `REG-P23A2-LOGIN-001 broker alias Firebase sign in and backend session precede authority`() = runBlocking {
        backend.brokerAlias = "rider-v1-alias@auth.punto25.invalid"
        backend.session = session("RID-7")
        backend.onCreateSession = { assertNull(RiderSessionAuthority.snapshot()) }

        val result = coordinator.login("RID-7", "x".repeat(15), RiderLoginAttemptKeys("stable-session"))

        assertTrue(result is RiderAuthOutcome.Authorized)
        assertEquals(listOf("broker:RID-7", "session:stable-session"), backend.events)
        assertEquals(listOf("signIn:rider-v1-alias@auth.punto25.invalid"), firebase.authEvents)
        assertEquals("RID-7", RiderSessionAuthority.snapshot()?.riderCode)
        assertEquals(backend.session, cache.saved)
    }

    @Test
    fun `REG-P23A2-LOGIN-FAILCLOSED-001 broker config or network error has no local fallback`() = runBlocking {
        backend.brokerFailure = RiderBackendException(503, "not_configured")
        val result = coordinator.login("RID-7", "x".repeat(15), RiderLoginAttemptKeys())
        assertTrue(result is RiderAuthOutcome.Rejected)
        assertNull(RiderSessionAuthority.snapshot())
        assertTrue(firebase.authEvents.isEmpty())
    }

    @Test
    fun `REG-P23A2-CAPABILITY-OPAQUE-001 capability remains case sensitive and unmodified`() = runBlocking {
        val capability = "AbC_def-123+OpaqueCaseSensitiveCapability"
        backend.prepareResult = RiderCapabilityPreparation(
            RiderCapabilityKind.ENROLLMENT,
            "RID-CAP",
            "alias-cap@auth.punto25.invalid",
            3
        )
        backend.session = session("RID-CAP")

        val result = coordinator.activateWithCapability(capability, "p".repeat(15), RiderAuthAttemptKeys())

        assertTrue(result is RiderAuthOutcome.Authorized)
        assertEquals(capability, backend.prepareCapabilities.single())
        assertEquals(capability, backend.completeCapabilities.single())
    }

    @Test
    fun `REG-P23A2-ENROLLMENT-001 enrollment creates Firebase user completes backend and creates session`() = runBlocking {
        backend.prepareResult = RiderCapabilityPreparation(
            RiderCapabilityKind.ENROLLMENT,
            "RID-E",
            "alias-e@auth.punto25.invalid",
            1
        )
        backend.session = session("RID-E")
        val keys = RiderAuthAttemptKeys("prep-e", "prep-r", "complete-e", "session-e")

        val result = coordinator.activateWithCapability("EnrollmentSecretAa", "a".repeat(15), keys)

        assertTrue(result is RiderAuthOutcome.Authorized)
        assertEquals("alias-e@auth.punto25.invalid", firebase.createdEmail)
        assertEquals(RiderCapabilityKind.ENROLLMENT, backend.completedKind)
        assertEquals("prep-e", backend.prepareKeys.single())
        assertEquals("complete-e", backend.completeKey)
        assertEquals("session-e", backend.sessionKeys.single())
    }

    @Test
    fun `REG-P23A2-RECOVERY-001 backend determines recovery and binds a new Firebase identity`() = runBlocking {
        backend.enrollmentPrepareFailure = RiderBackendException(401, "invalid_capability")
        backend.recoveryPrepareResult = RiderCapabilityPreparation(
            RiderCapabilityKind.RECOVERY,
            "RID-R",
            "alias-generation-9@auth.punto25.invalid",
            9
        )
        backend.session = session("RID-R", epoch = 12)
        firebase.hasUser = true
        firebase.uid = "uid-old"
        firebase.email = "old@auth.punto25.invalid"

        val result = coordinator.activateWithCapability("RecoverySecretBb", "b".repeat(15), RiderAuthAttemptKeys())

        assertTrue(result is RiderAuthOutcome.Authorized)
        assertEquals(listOf(RiderCapabilityKind.ENROLLMENT, RiderCapabilityKind.RECOVERY), backend.prepareKinds)
        assertEquals(RiderCapabilityKind.RECOVERY, backend.completedKind)
        assertEquals("alias-generation-9@auth.punto25.invalid", firebase.createdEmail)
        assertTrue(firebase.signOutCount >= 1)
    }

    @Test
    fun `REG-P23A2-PASSWORD-POLICY-001 length only policy is 15 through 4096`() {
        assertFalse(riderPasswordIsValid("A1a".repeat(4)))
        assertTrue(riderPasswordIsValid(" ".repeat(15)))
        assertTrue(riderPasswordIsValid("z".repeat(4096)))
        assertFalse(riderPasswordIsValid("z".repeat(4097)))
    }

    @Test
    fun `REG-P23A2-LEGACY-NOAUTH-001 local credentials invitation and redeem never grant Rider authority`() {
        val controller = MandadosController(context)
        val result = controller.authenticateRiderResult("RID-LEGACY", "x".repeat(15))
        assertEquals(RiderAuthenticationStatus.INVALID_CREDENTIALS, result.status)
        assertFalse(controller.authenticateRider("RID-LEGACY", "x".repeat(15)))
        assertNull(controller.createRiderInvitation("RID-LEGACY"))
        assertNull(controller.redeemRiderInvitation("P25-ABC123", "x".repeat(15)))
        assertNull(RiderSessionAuthority.snapshot())
    }

    @Test
    fun `REG-P23A2-SESSION-AUTHORITY-001 epoch milliseconds context is the in-memory authority`() {
        val now = System.currentTimeMillis()
        val valid = RiderSessionContext("RID", "session", 2, now + 10_000)
        val expired = valid.copy(expiresAt = now)
        assertTrue(valid.isStructurallyValid(now))
        assertFalse(expired.isStructurallyValid(now))
        RiderSessionAuthority.install(valid)
        assertEquals(valid, RiderSessionAuthority.snapshot(now))
        assertNull(RiderSessionAuthority.snapshot(now + 20_000))
    }

    @Test
    fun `REG-P23A2-RESTORE-GET-001 disk hint cannot revive authority without exact backend GET`() = runBlocking {
        val hint = session("RID-X", sessionId = "S1", epoch = 4)
        cache.saved = hint
        firebase.hasUser = true
        firebase.email = "alias@auth.punto25.invalid"
        backend.getSessionResult = hint.copy(sessionEpoch = 5)

        val result = coordinator.restore()

        assertTrue(result is RiderAuthOutcome.Rejected)
        assertEquals(listOf("get:S1"), backend.events)
        assertNull(RiderSessionAuthority.snapshot())
    }

    @Test
    fun `REG-P23A2-STALE-FAILCLOSED-001 stale backend session clears authority`() = runBlocking {
        val active = session("RID-S", sessionId = "S-stale", epoch = 2)
        cache.saved = active
        RiderSessionAuthority.install(active)
        firebase.hasUser = true
        backend.getSessionFailure = RiderBackendException(401, "stale_session")

        val result = coordinator.revalidate(active)

        assertTrue(result is RiderAuthOutcome.Rejected)
        assertNull(RiderSessionAuthority.snapshot())
        assertNull(cache.saved)
        assertTrue(firebase.signedOut)
    }

    @Test
    fun `REG-P23A2-LOGOUT-001 logout clears authority and Rider Firebase even if DELETE fails`() = runBlocking {
        val active = session("RID-L", sessionId = "S-logout")
        cache.saved = active
        RiderSessionAuthority.install(active)
        firebase.hasUser = true
        backend.deleteFailure = RiderBackendException(503, "network")
        backend.onDeleteSession = { assertNull(RiderSessionAuthority.snapshot()) }

        coordinator.logout(active)

        assertEquals(listOf("delete:S-logout"), backend.events)
        assertNull(RiderSessionAuthority.snapshot())
        assertNull(cache.saved)
        assertTrue(firebase.signedOut)
    }

    @Test
    fun `REG-P23A2-PASSWORD-CHANGE-001 password change uses Firebase and preserves Rider identity session`() = runBlocking {
        val active = session("RID-P", sessionId = "S-password", epoch = 6)
        firebase.hasUser = true
        firebase.email = "alias@auth.punto25.invalid"
        backend.getSessionResult = active
        cache.saved = active
        RiderSessionAuthority.install(active)

        val changed = coordinator.changePassword(active, "old-password-value", "n".repeat(15))

        assertTrue(changed)
        assertEquals(listOf("get:S-password"), backend.events)
        assertEquals("old-password-value" to "n".repeat(15), firebase.passwordChange)
        assertEquals(active, RiderSessionAuthority.snapshot())
        assertEquals(active, cache.saved)
    }

    @Test
    fun `REG-P23A2-IDEMPOTENCY-001 same uncertain intent keeps session key and new intent gets new key`() = runBlocking {
        val sameIntent = RiderLoginAttemptKeys("stable-key")
        backend.session = session("RID-I")
        backend.createSessionFailuresRemaining = 1

        val first = coordinator.login("RID-I", "i".repeat(15), sameIntent)
        val second = coordinator.login("RID-I", "i".repeat(15), sameIntent)

        assertTrue(first is RiderAuthOutcome.Rejected)
        assertTrue(second is RiderAuthOutcome.Authorized)
        assertEquals(listOf("stable-key", "stable-key"), backend.sessionKeys)
        assertNotEquals(RiderLoginAttemptKeys().session, RiderLoginAttemptKeys().session)
    }

    @Test
    fun `REG-P23A2-CONFIG-FAILCLOSED-001 missing live configuration cannot grant authority`() = runBlocking {
        backend.configured = false
        val result = coordinator.login("RID-C", "c".repeat(15), RiderLoginAttemptKeys())
        assertTrue(result is RiderAuthOutcome.Rejected)
        assertNull(RiderSessionAuthority.snapshot())
    }

    @Test
    fun `REG-P23A2-ADMIN-ENROLLMENT-CAP-001 Admin enrollment capability is issued by backend`() = runBlocking {
        backend.adminCapability = adminCapability(RiderCapabilityKind.ENROLLMENT)
        val service = RiderAdminCapabilityService(backend) { "admin-token" }

        val result = service.create("RID-A", RiderCapabilityKind.ENROLLMENT).getOrThrow()

        assertEquals(RiderCapabilityKind.ENROLLMENT, result.kind)
        assertEquals(listOf(RiderCapabilityKind.ENROLLMENT), backend.adminKinds)
        assertEquals(listOf("admin-token"), backend.adminTokens)
    }

    @Test
    fun `REG-P23A2-ADMIN-RECOVERY-CAP-001 backend switches existing Rider flow to recovery capability`() = runBlocking {
        backend.adminEnrollmentFailure = RiderBackendException(409, "already_enrolled")
        backend.adminCapability = adminCapability(RiderCapabilityKind.RECOVERY)
        val service = RiderAdminCapabilityService(backend) { "admin-token" }

        val result = service.createForExistingFlow("RID-A").getOrThrow()

        assertEquals(RiderCapabilityKind.RECOVERY, result.kind)
        assertEquals(listOf(RiderCapabilityKind.ENROLLMENT, RiderCapabilityKind.RECOVERY), backend.adminKinds)
    }

    @Test
    fun `REG-P23A2-CAPABILITY-NOPERSIST-001 session cache API cannot persist capability or password`() {
        val fieldNames = AndroidRiderSessionCache::class.java.declaredFields.map { it.name.lowercase() }
        assertFalse(fieldNames.any { it.contains("capability") || it.contains("password") })
    }

    @Test
    fun `REG-P23A2-NO-P24-001 Rider auth backend gateway exposes no order payment pricing or balance authority`() {
        val names = RiderBackendGateway::class.java.declaredMethods.map { it.name.lowercase() }
        assertFalse(names.any {
            it.contains("order") || it.contains("payment") || it.contains("pricing") ||
                it.contains("balance") || it.contains("take") || it.contains("assign")
        })
    }

    private fun session(
        riderCode: String,
        sessionId: String = "session-${riderCode.lowercase()}",
        epoch: Long = 1
    ) = RiderSessionContext(riderCode, sessionId, epoch, System.currentTimeMillis() + 3_600_000)

    private fun adminCapability(kind: RiderCapabilityKind) = RiderAdminCapability(
        "RID-A", kind, 2, "OpaqueCapabilityAaBbCc1234567890", System.currentTimeMillis() + 60_000
    )
}

private class FakeRiderCache : RiderSessionCache {
    var saved: RiderSessionContext? = null
    override fun load(): RiderSessionContext? = saved
    override fun save(context: RiderSessionContext) { saved = context }
    override fun clear() { saved = null }
}

private class FakeRiderFirebase : RiderFirebaseGateway {
    var configured = true
    var hasUser = false
    var uid: String? = null
    var email: String? = null
    var token: String? = "firebase-token"
    var signedOut = false
    var signOutCount = 0
    var createdEmail: String? = null
    var passwordChange: Pair<String, String>? = null
    val authEvents = mutableListOf<String>()

    override fun isConfigured() = configured
    override fun hasCurrentUser() = hasUser
    override fun currentUid() = uid
    override fun currentEmail() = email
    override suspend fun signIn(authAlias: String, password: String) {
        authEvents += "signIn:$authAlias"
        hasUser = true
        signedOut = false
        email = authAlias
        uid = uid ?: "uid-signin"
    }
    override suspend fun createUser(authAlias: String, password: String) {
        createdEmail = authAlias
        hasUser = true
        signedOut = false
        email = authAlias
        uid = "uid-created-${authAlias.hashCode()}"
    }
    override suspend fun idToken(forceRefresh: Boolean) = token
    override suspend fun reauthenticateAndUpdatePassword(currentPassword: String, newPassword: String) {
        passwordChange = currentPassword to newPassword
    }
    override fun signOut() {
        signedOut = true
        signOutCount++
        hasUser = false
        email = null
        uid = null
    }
}

private class FakeRiderBackend : RiderBackendGateway {
    var configured = true
    var brokerAlias = "alias@auth.punto25.invalid"
    var brokerFailure: Throwable? = null
    var prepareResult = RiderCapabilityPreparation(RiderCapabilityKind.ENROLLMENT, "RID", "alias@auth.punto25.invalid", 1)
    var recoveryPrepareResult = RiderCapabilityPreparation(RiderCapabilityKind.RECOVERY, "RID", "alias-recovery@auth.punto25.invalid", 2)
    var enrollmentPrepareFailure: Throwable? = null
    var session = RiderSessionContext("RID", "session", 1, System.currentTimeMillis() + 3_600_000)
    var getSessionResult: RiderSessionContext? = null
    var getSessionFailure: Throwable? = null
    var deleteFailure: Throwable? = null
    var createSessionFailuresRemaining = 0
    var adminCapability = RiderAdminCapability("RID-A", RiderCapabilityKind.ENROLLMENT, 1, "capability", System.currentTimeMillis() + 60_000)
    var adminEnrollmentFailure: Throwable? = null
    var completedKind: RiderCapabilityKind? = null
    var completeKey: String? = null
    val events = mutableListOf<String>()
    val prepareCapabilities = mutableListOf<String>()
    val completeCapabilities = mutableListOf<String>()
    val prepareKinds = mutableListOf<RiderCapabilityKind>()
    val prepareKeys = mutableListOf<String>()
    val sessionKeys = mutableListOf<String>()
    val adminKinds = mutableListOf<RiderCapabilityKind>()
    val adminTokens = mutableListOf<String>()
    var onCreateSession: (() -> Unit)? = null
    var onDeleteSession: (() -> Unit)? = null

    override fun isConfigured() = configured
    override suspend fun broker(riderCode: String, password: String): String {
        brokerFailure?.let { throw it }
        events += "broker:$riderCode"
        return brokerAlias
    }
    override suspend fun prepare(kind: RiderCapabilityKind, capability: String, idempotencyKey: String): RiderCapabilityPreparation {
        prepareKinds += kind
        prepareCapabilities += capability
        prepareKeys += idempotencyKey
        if (kind == RiderCapabilityKind.ENROLLMENT) {
            enrollmentPrepareFailure?.let { throw it }
            return prepareResult.copy(kind = kind)
        }
        return recoveryPrepareResult
    }
    override suspend fun complete(kind: RiderCapabilityKind, capability: String, firebaseIdToken: String, idempotencyKey: String) {
        completedKind = kind
        completeCapabilities += capability
        completeKey = idempotencyKey
    }
    override suspend fun createSession(firebaseIdToken: String, idempotencyKey: String): RiderSessionContext {
        sessionKeys += idempotencyKey
        events += "session:$idempotencyKey"
        onCreateSession?.invoke()
        if (createSessionFailuresRemaining > 0) {
            createSessionFailuresRemaining--
            throw RiderBackendException(503, "network")
        }
        return session
    }
    override suspend fun getSession(firebaseIdToken: String, sessionId: String): RiderSessionContext {
        events += "get:$sessionId"
        getSessionFailure?.let { throw it }
        return getSessionResult ?: session
    }
    override suspend fun deleteSession(firebaseIdToken: String, sessionId: String) {
        events += "delete:$sessionId"
        onDeleteSession?.invoke()
        deleteFailure?.let { throw it }
    }
    override suspend fun createAdminCapability(adminIdToken: String, riderCode: String, kind: RiderCapabilityKind): RiderAdminCapability {
        adminKinds += kind
        adminTokens += adminIdToken
        if (kind == RiderCapabilityKind.ENROLLMENT) adminEnrollmentFailure?.let { throw it }
        return adminCapability.copy(riderCode = riderCode, kind = kind)
    }
}
