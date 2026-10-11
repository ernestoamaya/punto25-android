package ar.com.mandados.app

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RiderLogoutBoundaryP23A2Test {
    @After
    fun tearDown() {
        RiderSessionAuthority.clear()
    }

    @Test
    fun `REG-P23A2-LOGOUT-001 begin boundary prevents restore before any remote work`() {
        val active = RiderSessionContext(
            riderCode = "RID-LOGOUT",
            sessionId = "session-logout",
            sessionEpoch = 7,
            expiresAt = System.currentTimeMillis() + 60_000
        )
        val cache = BoundaryRiderCache(active)
        val firebase = BoundaryRiderFirebase()
        val backend = BoundaryRiderBackend()
        val coordinator = RiderAuthSessionCoordinator(firebase, backend, cache)
        RiderSessionAuthority.install(active)

        // This is intentionally non-suspending: it is the boundary the UI can execute before
        // scheduling token retrieval / DELETE work.
        val revocation = coordinator.beginLogout(active)

        assertNull(RiderSessionAuthority.snapshot())
        assertNull(cache.saved)
        assertTrue(firebase.signedOut)
        assertEquals(0, firebase.tokenRequests)
        assertEquals(0, backend.deleteCalls)

        // Model process death / cancellation before the remote phase: local state alone cannot
        // restore authority even though the backend DELETE has not run.
        val restore = runBlocking { coordinator.restore() }
        assertTrue(restore is RiderAuthOutcome.Rejected)
        assertNull(RiderSessionAuthority.snapshot())
        assertNull(cache.saved)

        // If execution survives, remote revocation still runs only after the local boundary.
        runBlocking { coordinator.finishLogout(revocation) }
        assertEquals(1, firebase.tokenRequests)
        assertEquals(1, backend.deleteCalls)
        assertNull(RiderSessionAuthority.snapshot())
        assertNull(cache.saved)
    }
}

private class BoundaryRiderCache(initial: RiderSessionContext?) : RiderSessionCache {
    var saved: RiderSessionContext? = initial

    override fun load(): RiderSessionContext? = saved
    override fun save(context: RiderSessionContext) { saved = context }
    override fun clear() { saved = null }
}

private class BoundaryRiderFirebase : RiderFirebaseGateway {
    var signedOut = false
    var tokenRequests = 0

    override fun isConfigured(): Boolean = true
    override fun hasCurrentUser(): Boolean = !signedOut
    override fun currentUid(): String? = if (signedOut) null else "uid-rider"
    override fun currentEmail(): String? = if (signedOut) null else "rider@auth.punto25.invalid"
    override suspend fun signIn(authAlias: String, password: String) = Unit
    override suspend fun createUser(authAlias: String, password: String) = Unit
    override suspend fun idToken(forceRefresh: Boolean): String? = if (signedOut) null else "token"
    override suspend fun reauthenticateAndUpdatePassword(currentPassword: String, newPassword: String) = Unit
    override fun signOut() { signedOut = true }

    override fun invalidateForLogout(): suspend () -> String? {
        signOut()
        return {
            tokenRequests++
            "captured-pre-signout-token"
        }
    }
}

private class BoundaryRiderBackend : RiderBackendGateway {
    var deleteCalls = 0

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
    override suspend fun getSession(firebaseIdToken: String, sessionId: String): RiderSessionContext = error("not used")
    override suspend fun deleteSession(firebaseIdToken: String, sessionId: String) {
        deleteCalls++
        throw RiderBackendException(503, "network")
    }
    override suspend fun createAdminCapability(
        adminIdToken: String,
        riderCode: String,
        kind: RiderCapabilityKind
    ): RiderAdminCapability = error("not used")
}