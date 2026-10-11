package ar.com.mandados.app

import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class RiderLogoutRestoreRaceP23A2Test {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("mandados_alpha1", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("punto25_rider_session_cache", Context.MODE_PRIVATE).edit().clear().commit()
        RiderSessionAuthority.clear()
    }

    @After
    fun tearDown() {
        RiderSessionAuthority.clear()
        context.getSharedPreferences("punto25_rider_session_cache", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `REG-P23A2-LOGOUT-RACE-001 real Back logout fences in-flight startup restore`() = runBlocking {
        val active = RiderSessionContext(
            riderCode = "RID-LOGOUT-RACE",
            sessionId = "session-logout-race",
            sessionEpoch = 9,
            expiresAt = System.currentTimeMillis() + 3_600_000
        )
        val cache = AndroidRiderSessionCache(context)
        cache.save(active)
        val firebase = RaceRiderFirebase()
        val backend = RaceRiderBackend(active)
        val coordinator = RiderAuthSessionCoordinator(firebase, backend, cache)
        val controller = MandadosController(context)

        assertEquals(active, cache.load())
        assertTrue(firebase.hasCurrentUser())
        assertNull(RiderSessionAuthority.snapshot())

        val restoring = async { coordinator.restore() }
        backend.getSessionStarted.await()

        // Startup restore has consumed the durable hint and Firebase token but authority is still null.
        assertEquals(1, backend.getSessionCalls)
        assertEquals(active, cache.load())
        assertTrue(firebase.hasCurrentUser())
        assertNull(RiderSessionAuthority.snapshot())

        // This is the same synchronous boundary used by Rider workspace Back/logout.
        val revocation = controller.beginAuthenticatedRiderLogout(coordinator)

        assertEquals(active.sessionId, revocation.sessionId)
        assertNull(RiderSessionAuthority.snapshot())
        assertNull(cache.load())
        assertFalse(firebase.hasCurrentUser())
        assertTrue(firebase.signedOut)
        assertEquals(0, backend.deleteCalls)

        // A valid GET that started before logout is released only after the fence exists.
        backend.releaseGetSession.complete(Unit)
        val delayedResult = restoring.await()

        assertTrue(delayedResult is RiderAuthOutcome.Rejected)
        assertNull(RiderSessionAuthority.snapshot())
        assertNull(cache.load())
        assertFalse(firebase.hasCurrentUser())

        // Remote revocation remains best effort and starts only after local invalidation.
        coordinator.finishLogout(revocation)
        assertEquals(1, backend.deleteCalls)
        assertEquals(active.sessionId, backend.deletedSessionId)
        assertNull(RiderSessionAuthority.snapshot())
        assertNull(cache.load())
        assertFalse(firebase.hasCurrentUser())

        // A subsequent startup restore cannot revive the logged-out session.
        val subsequentRestore = coordinator.restore()
        assertTrue(subsequentRestore is RiderAuthOutcome.Rejected)
        assertEquals(1, backend.getSessionCalls)
        assertNull(RiderSessionAuthority.snapshot())
        assertNull(cache.load())
        assertFalse(firebase.hasCurrentUser())
    }
}

private class RaceRiderFirebase : RiderFirebaseGateway {
    private var current = true
    var signedOut = false
        private set

    override fun isConfigured(): Boolean = true
    override fun hasCurrentUser(): Boolean = current
    override fun currentUid(): String? = if (current) "uid-race" else null
    override fun currentEmail(): String? = if (current) "race@auth.punto25.invalid" else null
    override suspend fun signIn(authAlias: String, password: String) {
        current = true
        signedOut = false
    }
    override suspend fun createUser(authAlias: String, password: String) {
        current = true
        signedOut = false
    }
    override suspend fun idToken(forceRefresh: Boolean): String? = if (current) "firebase-race-token" else null
    override suspend fun reauthenticateAndUpdatePassword(currentPassword: String, newPassword: String) = Unit
    override fun signOut() {
        current = false
        signedOut = true
    }

    override fun invalidateForLogout(): suspend () -> String? {
        val capturedToken = if (current) "firebase-race-token" else null
        signOut()
        return { capturedToken }
    }
}

private class RaceRiderBackend(
    private val validSession: RiderSessionContext
) : RiderBackendGateway {
    val getSessionStarted = CompletableDeferred<Unit>()
    val releaseGetSession = CompletableDeferred<Unit>()
    var getSessionCalls = 0
        private set
    var deleteCalls = 0
        private set
    var deletedSessionId: String? = null
        private set

    override fun isConfigured(): Boolean = true
    override suspend fun broker(riderCode: String, password: String): String = error("not used")
    override suspend fun prepare(
        kind: RiderCapabilityKind,
        capability: String,
        idempotencyKey: String
    ): RiderCapabilityPreparation = error("not used")
    override suspend fun complete(
        kind: RiderCapabilityKind,
        capability: String,
        firebaseIdToken: String,
        idempotencyKey: String
    ) = error("not used")
    override suspend fun createSession(firebaseIdToken: String, idempotencyKey: String): RiderSessionContext =
        error("not used")

    override suspend fun getSession(firebaseIdToken: String, sessionId: String): RiderSessionContext {
        getSessionCalls++
        getSessionStarted.complete(Unit)
        releaseGetSession.await()
        return validSession
    }

    override suspend fun deleteSession(firebaseIdToken: String, sessionId: String) {
        deleteCalls++
        deletedSessionId = sessionId
    }

    override suspend fun createAdminCapability(
        adminIdToken: String,
        riderCode: String,
        kind: RiderCapabilityKind
    ): RiderAdminCapability = error("not used")
}