package ar.com.mandados.app

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

internal const val RIDER_SESSION_HEADER = "X-Punto25-Rider-Session"
internal const val RIDER_PASSWORD_MIN_LENGTH = 15
internal const val RIDER_PASSWORD_MAX_LENGTH = 4096

internal data class RiderSessionContext(
    val riderCode: String,
    val sessionId: String,
    val sessionEpoch: Long,
    val expiresAt: Long
) {
    fun isStructurallyValid(nowMillis: Long = System.currentTimeMillis()): Boolean =
        riderCode.isNotBlank() &&
            sessionId.isNotBlank() &&
            sessionEpoch >= 1L &&
            expiresAt > nowMillis
}

internal object RiderSessionAuthority {
    private val stateLock = Any()

    @Volatile
    private var current: RiderSessionContext? = null
    private var logoutFence: Long = 0L

    fun install(context: RiderSessionContext) {
        synchronized(stateLock) {
            current = context.takeIf { it.isStructurallyValid() }
        }
    }

    fun snapshot(nowMillis: Long = System.currentTimeMillis()): RiderSessionContext? =
        synchronized(stateLock) {
            val candidate = current
            if (candidate == null || !candidate.isStructurallyValid(nowMillis)) {
                current = null
                null
            } else {
                candidate
            }
        }

    fun matches(riderCode: String, nowMillis: Long = System.currentTimeMillis()): Boolean =
        snapshot(nowMillis)?.riderCode?.equals(riderCode, ignoreCase = true) == true

    fun captureInstallFence(): Long = synchronized(stateLock) { logoutFence }

    /**
     * Invalidates every asynchronous install attempt that captured an earlier fence and clears
     * process authority atomically with the generation change.
     */
    fun establishLogoutFence() {
        synchronized(stateLock) {
            logoutFence += 1L
            current = null
        }
    }

    /**
     * Cache persistence and process-authority installation share the same lock as the logout
     * fence. Therefore logout either happens after this complete install and clears it, or wins
     * first and prevents both persistence and authority installation by the stale operation.
     */
    fun installIfFenceUnchanged(
        context: RiderSessionContext,
        expectedFence: Long,
        persist: () -> Unit
    ): Boolean = synchronized(stateLock) {
        if (logoutFence != expectedFence || !context.isStructurallyValid()) return@synchronized false
        persist()
        current = context
        true
    }

    fun clear() {
        synchronized(stateLock) {
            current = null
        }
    }
}

internal enum class RiderCapabilityKind {
    ENROLLMENT,
    RECOVERY
}

internal data class RiderCapabilityPreparation(
    val kind: RiderCapabilityKind,
    val riderCode: String,
    val authAlias: String,
    val generation: Long
)

internal data class RiderAdminCapability(
    val riderCode: String,
    val kind: RiderCapabilityKind,
    val generation: Long,
    val capability: String,
    val expiresAt: Long
)

internal data class RiderAuthAttemptKeys(
    val enrollmentPrepare: String = newIdempotencyKey(),
    val recoveryPrepare: String = newIdempotencyKey(),
    val complete: String = newIdempotencyKey(),
    val session: String = newIdempotencyKey()
) {
    private var preparedCapability: String? = null
    private var preparedResult: RiderCapabilityPreparation? = null

    @Synchronized
    fun preparationFor(capability: String): RiderCapabilityPreparation? =
        preparedResult?.takeIf { preparedCapability == capability }

    @Synchronized
    fun rememberPreparation(capability: String, preparation: RiderCapabilityPreparation) {
        preparedCapability = capability
        preparedResult = preparation
    }
}

internal data class RiderLoginAttemptKeys(
    val session: String = newIdempotencyKey()
)

internal fun newIdempotencyKey(): String = UUID.randomUUID().toString()

internal fun riderPasswordIsValid(password: String): Boolean =
    password.length in RIDER_PASSWORD_MIN_LENGTH..RIDER_PASSWORD_MAX_LENGTH

internal class RiderBackendException(
    val statusCode: Int,
    val backendCode: String?
) : Exception("Rider backend request failed")

internal interface RiderFirebaseGateway {
    fun isConfigured(): Boolean
    fun hasCurrentUser(): Boolean
    fun currentUid(): String?
    fun currentEmail(): String?
    suspend fun signIn(authAlias: String, password: String)
    suspend fun createUser(authAlias: String, password: String)
    suspend fun idToken(forceRefresh: Boolean = false): String?
    suspend fun reauthenticateAndUpdatePassword(currentPassword: String, newPassword: String)
    fun signOut()

    /**
     * Invalidates the local Firebase Rider session synchronously and returns a best-effort
     * token provider backed by the user reference captured before sign-out. The returned
     * provider is only for backend logout revocation after all local Rider authority is gone.
     */
    fun invalidateForLogout(): suspend () -> String? {
        signOut()
        return { null }
    }
}

internal interface RiderBackendGateway {
    fun isConfigured(): Boolean
    suspend fun broker(riderCode: String, password: String): String
    suspend fun prepare(
        kind: RiderCapabilityKind,
        capability: String,
        idempotencyKey: String
    ): RiderCapabilityPreparation
    suspend fun complete(
        kind: RiderCapabilityKind,
        capability: String,
        firebaseIdToken: String,
        idempotencyKey: String
    )
    suspend fun createSession(firebaseIdToken: String, idempotencyKey: String): RiderSessionContext
    suspend fun getSession(firebaseIdToken: String, sessionId: String): RiderSessionContext
    suspend fun deleteSession(firebaseIdToken: String, sessionId: String)
    suspend fun createAdminCapability(
        adminIdToken: String,
        riderCode: String,
        kind: RiderCapabilityKind
    ): RiderAdminCapability
}

internal interface RiderSessionCache {
    fun load(): RiderSessionContext?
    fun save(context: RiderSessionContext)
    fun clear()
}

internal class AndroidRiderSessionCache(context: Context) : RiderSessionCache {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun load(): RiderSessionContext? {
        val riderCode = prefs.getString(KEY_RIDER_CODE, null) ?: return null
        val sessionId = prefs.getString(KEY_SESSION_ID, null) ?: return null
        val sessionEpoch = prefs.getLong(KEY_SESSION_EPOCH, -1L)
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, -1L)
        return RiderSessionContext(riderCode, sessionId, sessionEpoch, expiresAt)
    }

    override fun save(context: RiderSessionContext) {
        prefs.edit()
            .putString(KEY_RIDER_CODE, context.riderCode)
            .putString(KEY_SESSION_ID, context.sessionId)
            .putLong(KEY_SESSION_EPOCH, context.sessionEpoch)
            .putLong(KEY_EXPIRES_AT, context.expiresAt)
            .apply()
    }

    override fun clear() {
        // Logout/invalid-session paths require the persisted restore hint to be gone
        // before any coroutine suspension or network work can begin.
        prefs.edit().clear().commit()
    }

    private companion object {
        const val PREFS = "punto25_rider_session_cache"
        const val KEY_RIDER_CODE = "rider_code"
        const val KEY_SESSION_ID = "session_id"
        const val KEY_SESSION_EPOCH = "session_epoch"
        const val KEY_EXPIRES_AT = "expires_at"
    }
}

internal sealed class RiderAuthOutcome {
    data class Authorized(val session: RiderSessionContext) : RiderAuthOutcome()
    data class Rejected(val recoverableMessage: String) : RiderAuthOutcome()
}

internal data class RiderLogoutRevocation(
    val sessionId: String?,
    val tokenProvider: suspend () -> String?
)

internal class RiderAuthSessionCoordinator(
    private val firebase: RiderFirebaseGateway,
    private val backend: RiderBackendGateway,
    private val cache: RiderSessionCache
) {
    suspend fun login(
        riderCode: String,
        password: String,
        keys: RiderLoginAttemptKeys
    ): RiderAuthOutcome = guarded {
        requireConfigured()
        if (!riderPasswordIsValid(password)) return@guarded rejected()
        val normalizedRiderCode = riderCode.trim()
        if (normalizedRiderCode.isBlank()) return@guarded rejected()

        val authAlias = backend.broker(normalizedRiderCode, password)
        if (authAlias.isBlank()) return@guarded rejected()
        firebase.signIn(authAlias, password)
        val token = firebase.idToken(forceRefresh = true).orEmpty()
        if (token.isBlank()) return@guarded rejected()
        val session = backend.createSession(token, keys.session)
        if (!session.isStructurallyValid() || !session.riderCode.equals(normalizedRiderCode, ignoreCase = true)) {
            return@guarded rejected()
        }
        installVerified(session)
        RiderAuthOutcome.Authorized(session)
    }

    suspend fun activateWithCapability(
        capability: String,
        password: String,
        keys: RiderAuthAttemptKeys
    ): RiderAuthOutcome = guarded {
        requireConfigured()
        if (capability.isEmpty() || !riderPasswordIsValid(password)) return@guarded rejected()

        val preparation = prepareCapability(capability, keys)
        if (preparation.authAlias.isBlank() || preparation.riderCode.isBlank() || preparation.generation < 1L) {
            return@guarded rejected()
        }

        ensureFirebaseUser(preparation.authAlias, password)
        val completeToken = firebase.idToken(forceRefresh = true).orEmpty()
        if (completeToken.isBlank()) return@guarded rejected()
        backend.complete(preparation.kind, capability, completeToken, keys.complete)

        val sessionToken = firebase.idToken(forceRefresh = true).orEmpty()
        if (sessionToken.isBlank()) return@guarded rejected()
        val session = backend.createSession(sessionToken, keys.session)
        if (!session.isStructurallyValid() || !session.riderCode.equals(preparation.riderCode, ignoreCase = true)) {
            return@guarded rejected()
        }
        installVerified(session)
        RiderAuthOutcome.Authorized(session)
    }

    suspend fun restore(): RiderAuthOutcome {
        val installFence = RiderSessionAuthority.captureInstallFence()
        val hint = cache.load()
            ?: return rejectAndInvalidate("Volvé a iniciar sesión para continuar.")
        return revalidate(hint, installFence)
    }

    suspend fun revalidate(session: RiderSessionContext?): RiderAuthOutcome =
        revalidate(session, RiderSessionAuthority.captureInstallFence())

    private suspend fun revalidate(
        session: RiderSessionContext?,
        installFence: Long
    ): RiderAuthOutcome = guarded {
        requireConfigured()
        val hint = session ?: return@guarded rejectAndInvalidate()
        if (!hint.isStructurallyValid() || !firebase.hasCurrentUser()) return@guarded rejectAndInvalidate()
        val token = firebase.idToken(forceRefresh = true).orEmpty()
        if (token.isBlank()) return@guarded rejectAndInvalidate()
        val verified = backend.getSession(token, hint.sessionId)
        if (!verified.isStructurallyValid() ||
            verified.sessionId != hint.sessionId ||
            verified.sessionEpoch != hint.sessionEpoch ||
            verified.riderCode != hint.riderCode
        ) {
            return@guarded rejectAndInvalidate()
        }
        if (!installVerified(verified, installFence)) {
            return@guarded rejected("Volvé a iniciar sesión para continuar.")
        }
        RiderAuthOutcome.Authorized(verified)
    }

    /**
     * Synchronous logout boundary. A generation fence is established first, then persisted
     * restore state, process authority and Firebase Rider are invalidated. No token retrieval or
     * network work occurs here, so callers can execute this before launching any coroutine.
     */
    fun beginLogout(session: RiderSessionContext?): RiderLogoutRevocation {
        val candidate = session ?: RiderSessionAuthority.snapshot() ?: cache.load()
        RiderSessionAuthority.establishLogoutFence()
        runCatching { cache.clear() }
        val tokenProvider: suspend () -> String? = try {
            firebase.invalidateForLogout()
        } catch (_: Exception) {
            runCatching { firebase.signOut() }
            val emptyProvider: suspend () -> String? = { null }
            emptyProvider
        }
        return RiderLogoutRevocation(
            sessionId = candidate?.sessionId?.takeIf { it.isNotBlank() },
            tokenProvider = tokenProvider
        )
    }

    /** Best-effort remote revocation, safe to schedule only after [beginLogout] returned. */
    suspend fun finishLogout(revocation: RiderLogoutRevocation) {
        val sessionId = revocation.sessionId ?: return
        if (!backend.isConfigured()) return
        val token = runCatching { revocation.tokenProvider() }.getOrNull().orEmpty()
        if (token.isNotBlank()) runCatching { backend.deleteSession(token, sessionId) }
    }

    suspend fun logout(session: RiderSessionContext?) {
        finishLogout(beginLogout(session))
    }

    suspend fun changePassword(
        session: RiderSessionContext?,
        currentPassword: String,
        newPassword: String
    ): Boolean {
        if (!riderPasswordIsValid(newPassword)) return false
        val verified = when (val outcome = revalidate(session)) {
            is RiderAuthOutcome.Authorized -> outcome.session
            is RiderAuthOutcome.Rejected -> return false
        }
        return runCatching {
            firebase.reauthenticateAndUpdatePassword(currentPassword, newPassword)
            RiderSessionAuthority.install(verified)
            true
        }.getOrElse {
            invalidateLocalState()
            false
        }
    }

    private suspend fun prepareCapability(
        capability: String,
        keys: RiderAuthAttemptKeys
    ): RiderCapabilityPreparation {
        keys.preparationFor(capability)?.let { return it }
        val preparation = try {
            backend.prepare(RiderCapabilityKind.ENROLLMENT, capability, keys.enrollmentPrepare)
        } catch (error: RiderBackendException) {
            if (error.statusCode != 401 || error.backendCode != "invalid_capability") throw error
            backend.prepare(RiderCapabilityKind.RECOVERY, capability, keys.recoveryPrepare)
        }
        keys.rememberPreparation(capability, preparation)
        return preparation
    }

    private suspend fun ensureFirebaseUser(authAlias: String, password: String) {
        if (firebase.hasCurrentUser() && firebase.currentEmail() == authAlias) return
        firebase.signOut()
        try {
            firebase.createUser(authAlias, password)
        } catch (_: Exception) {
            // A retry may arrive after Firebase user creation succeeded but before backend completion.
            firebase.signIn(authAlias, password)
        }
        if (!firebase.currentEmail().orEmpty().equals(authAlias, ignoreCase = false)) {
            throw IllegalStateException("Rider Firebase user does not match backend alias")
        }
    }

    private fun installVerified(session: RiderSessionContext) {
        cache.save(session)
        RiderSessionAuthority.install(session)
    }

    private fun installVerified(session: RiderSessionContext, installFence: Long): Boolean =
        RiderSessionAuthority.installIfFenceUnchanged(session, installFence) {
            cache.save(session)
        }

    private fun invalidateLocalState() {
        runCatching { cache.clear() }
        RiderSessionAuthority.clear()
        runCatching { firebase.signOut() }
    }

    private fun rejectAndInvalidate(
        message: String = "No pudimos validar tu acceso. Revisá los datos o intentá nuevamente."
    ): RiderAuthOutcome.Rejected {
        invalidateLocalState()
        return rejected(message)
    }

    private fun requireConfigured() {
        if (!firebase.isConfigured() || !backend.isConfigured()) throw IllegalStateException("Rider auth is not configured")
    }

    private suspend fun guarded(block: suspend () -> RiderAuthOutcome): RiderAuthOutcome {
        return try {
            block()
        } catch (_: Exception) {
            invalidateLocalState()
            rejected()
        }
    }

    private fun rejected(message: String = "No pudimos validar tu acceso. Revisá los datos o intentá nuevamente.") =
        RiderAuthOutcome.Rejected(message)
}

internal object RiderFirebaseAuthIntegration : RiderFirebaseGateway {
    internal const val APP_NAME = "punto25-rider-auth"

    private fun optionsOrNull(): FirebaseOptions? {
        if (BuildConfig.FIREBASE_API_KEY.isBlank() || BuildConfig.FIREBASE_APP_ID.isBlank() || BuildConfig.FIREBASE_PROJECT_ID.isBlank()) {
            return null
        }
        return FirebaseOptions.Builder()
            .setApiKey(BuildConfig.FIREBASE_API_KEY)
            .setApplicationId(BuildConfig.FIREBASE_APP_ID)
            .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
            .build()
    }

    private fun app(context: Context): FirebaseApp? {
        val appContext = context.applicationContext
        val options = optionsOrNull() ?: return null
        return FirebaseApp.getApps(appContext).firstOrNull { it.name == APP_NAME }
            ?: synchronized(this) {
                FirebaseApp.getApps(appContext).firstOrNull { it.name == APP_NAME }
                    ?: FirebaseApp.initializeApp(appContext, options, APP_NAME)
            }
    }

    private fun auth(context: Context): FirebaseAuth? = app(context)?.let(FirebaseAuth::getInstance)

    private var applicationContext: Context? = null
    private var pendingLogoutTokenProvider: (suspend () -> String?)? = null

    fun bind(context: Context): RiderFirebaseAuthIntegration {
        applicationContext = context.applicationContext
        return this
    }

    private fun boundAuth(): FirebaseAuth? = applicationContext?.let(::auth)

    /**
     * Called from the non-suspending controller bridge before UI navigation/coroutine launch.
     * It removes the durable restore hint and Firebase current-user state synchronously while
     * retaining only a one-shot provider for the later best-effort backend DELETE.
     */
    fun invalidateBoundLocalLogoutState() {
        applicationContext?.let { runCatching { AndroidRiderSessionCache(it).clear() } }
        val auth = boundAuth()
        val user = auth?.currentUser
        if (user != null) {
            val provider: suspend () -> String? = { user.getIdToken(false).await()?.token }
            synchronized(this) { pendingLogoutTokenProvider = provider }
        }
        auth?.signOut()
    }

    override fun isConfigured(): Boolean = optionsOrNull() != null && applicationContext != null
    override fun hasCurrentUser(): Boolean = boundAuth()?.currentUser != null
    override fun currentUid(): String? = boundAuth()?.currentUser?.uid
    override fun currentEmail(): String? = boundAuth()?.currentUser?.email

    override suspend fun signIn(authAlias: String, password: String) {
        val auth = boundAuth() ?: error("Rider Firebase is not configured")
        auth.signInWithEmailAndPassword(authAlias, password).await()
    }

    override suspend fun createUser(authAlias: String, password: String) {
        val auth = boundAuth() ?: error("Rider Firebase is not configured")
        auth.createUserWithEmailAndPassword(authAlias, password).await()
    }

    override suspend fun idToken(forceRefresh: Boolean): String? =
        boundAuth()?.currentUser?.getIdToken(forceRefresh)?.await()?.token

    override suspend fun reauthenticateAndUpdatePassword(currentPassword: String, newPassword: String) {
        val user = boundAuth()?.currentUser ?: error("Rider Firebase user missing")
        val email = user.email ?: error("Rider Firebase email missing")
        user.reauthenticate(EmailAuthProvider.getCredential(email, currentPassword)).await()
        user.updatePassword(newPassword).await()
    }

    override fun signOut() {
        boundAuth()?.signOut()
    }

    override fun invalidateForLogout(): suspend () -> String? {
        synchronized(this) {
            pendingLogoutTokenProvider?.let { pending ->
                pendingLogoutTokenProvider = null
                return pending
            }
        }
        val auth = boundAuth()
        val user = auth?.currentUser
        auth?.signOut()
        return { user?.getIdToken(false)?.await()?.token }
    }
}

internal class HttpRiderBackendGateway(private val context: Context) : RiderBackendGateway {
    private val baseUrl: String
        get() = BuildConfig.PUNTO25_API_BASE_URL.trimEnd('/')

    override fun isConfigured(): Boolean = baseUrl.startsWith("https://")

    override suspend fun broker(riderCode: String, password: String): String {
        val response = request(
            method = "POST",
            path = "/v1/rider/auth/broker",
            body = JSONObject().put("riderCode", riderCode).put("password", password)
        )
        return response.optString("authAlias").takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Missing authAlias")
    }

    override suspend fun prepare(
        kind: RiderCapabilityKind,
        capability: String,
        idempotencyKey: String
    ): RiderCapabilityPreparation {
        val response = request(
            method = "POST",
            path = kind.path("prepare"),
            idempotencyKey = idempotencyKey,
            body = JSONObject().put("capability", capability)
        )
        return RiderCapabilityPreparation(
            kind = kind,
            riderCode = response.optString("riderCode"),
            authAlias = response.optString("authAlias"),
            generation = response.optLong("generation", -1L)
        )
    }

    override suspend fun complete(
        kind: RiderCapabilityKind,
        capability: String,
        firebaseIdToken: String,
        idempotencyKey: String
    ) {
        request(
            method = "POST",
            path = kind.path("complete"),
            bearer = firebaseIdToken,
            idempotencyKey = idempotencyKey,
            body = JSONObject().put("capability", capability)
        )
    }

    override suspend fun createSession(firebaseIdToken: String, idempotencyKey: String): RiderSessionContext =
        parseSession(
            request(
                method = "POST",
                path = "/v1/rider/session",
                bearer = firebaseIdToken,
                idempotencyKey = idempotencyKey,
                body = JSONObject()
            )
        )

    override suspend fun getSession(firebaseIdToken: String, sessionId: String): RiderSessionContext =
        parseSession(
            request(
                method = "GET",
                path = "/v1/rider/session",
                bearer = firebaseIdToken,
                riderSessionId = sessionId
            )
        )

    override suspend fun deleteSession(firebaseIdToken: String, sessionId: String) {
        request(
            method = "DELETE",
            path = "/v1/rider/session",
            bearer = firebaseIdToken,
            riderSessionId = sessionId
        )
    }

    override suspend fun createAdminCapability(
        adminIdToken: String,
        riderCode: String,
        kind: RiderCapabilityKind
    ): RiderAdminCapability {
        val pathKind = if (kind == RiderCapabilityKind.ENROLLMENT) "enrollment" else "recovery"
        val response = request(
            method = "POST",
            path = "/v1/admin/riders/${riderCode.trim()}/$pathKind-capabilities",
            bearer = adminIdToken,
            body = JSONObject()
        )
        val responseKind = when (response.optString("kind")) {
            "ENROLLMENT" -> RiderCapabilityKind.ENROLLMENT
            "RECOVERY" -> RiderCapabilityKind.RECOVERY
            else -> throw IllegalStateException("Unknown Rider capability kind")
        }
        return RiderAdminCapability(
            riderCode = response.optString("riderCode"),
            kind = responseKind,
            generation = response.optLong("generation", -1L),
            capability = response.optString("capability"),
            expiresAt = response.optLong("expiresAt", -1L)
        ).also {
            if (it.riderCode.isBlank() || it.generation < 1L || it.capability.isBlank() || it.expiresAt <= System.currentTimeMillis()) {
                throw IllegalStateException("Invalid Rider capability response")
            }
        }
    }

    private fun RiderCapabilityKind.path(action: String): String = when (this) {
        RiderCapabilityKind.ENROLLMENT -> "/v1/rider/enrollment/$action"
        RiderCapabilityKind.RECOVERY -> "/v1/rider/recovery/$action"
    }

    private fun parseSession(json: JSONObject): RiderSessionContext = RiderSessionContext(
        riderCode = json.optString("riderCode"),
        sessionId = json.optString("sessionId"),
        sessionEpoch = json.optLong("sessionEpoch", -1L),
        expiresAt = json.optLong("expiresAt", -1L)
    )

    private suspend fun request(
        method: String,
        path: String,
        bearer: String? = null,
        idempotencyKey: String? = null,
        riderSessionId: String? = null,
        body: JSONObject? = null
    ): JSONObject = withContext(Dispatchers.IO) {
        if (!isConfigured()) throw IllegalStateException("Punto25 backend is not configured")
        val connection = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/json")
            if (!bearer.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $bearer")
            if (!idempotencyKey.isNullOrBlank()) setRequestProperty("Idempotency-Key", idempotencyKey)
            if (!riderSessionId.isNullOrBlank()) setRequestProperty(RIDER_SESSION_HEADER, riderSessionId)
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        try {
            if (body != null) {
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body.toString()) }
            }
            val status = connection.responseCode
            val raw = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val json = if (raw.isBlank()) JSONObject() else runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
            if (status !in 200..299) {
                throw RiderBackendException(status, json.optString("error").takeIf { it.isNotBlank() })
            }
            json
        } finally {
            connection.disconnect()
        }
    }
}

internal class RiderAdminCapabilityService(
    private val backend: RiderBackendGateway,
    private val adminTokenProvider: suspend () -> String?
) {
    constructor(context: Context) : this(
        backend = HttpRiderBackendGateway(context.applicationContext),
        adminTokenProvider = { AdminGoogleAuthIntegration.currentIdToken(context.applicationContext) }
    )

    suspend fun create(riderCode: String, kind: RiderCapabilityKind): Result<RiderAdminCapability> = runCatching {
        if (!backend.isConfigured()) error("Backend no configurado")
        val token = adminTokenProvider().orEmpty()
        if (token.isBlank()) error("Sesión Admin no disponible")
        backend.createAdminCapability(token, riderCode, kind)
    }

    suspend fun createForExistingFlow(riderCode: String): Result<RiderAdminCapability> = runCatching {
        if (!backend.isConfigured()) error("Backend no configurado")
        val token = adminTokenProvider().orEmpty()
        if (token.isBlank()) error("Sesión Admin no disponible")
        try {
            backend.createAdminCapability(token, riderCode, RiderCapabilityKind.ENROLLMENT)
        } catch (error: RiderBackendException) {
            if (error.statusCode != 409 || error.backendCode != "already_enrolled") throw error
            backend.createAdminCapability(token, riderCode, RiderCapabilityKind.RECOVERY)
        }
    }
}

internal fun createRiderCoordinator(context: Context): RiderAuthSessionCoordinator {
    val appContext = context.applicationContext
    return RiderAuthSessionCoordinator(
        firebase = RiderFirebaseAuthIntegration.bind(appContext),
        backend = HttpRiderBackendGateway(appContext),
        cache = AndroidRiderSessionCache(appContext)
    )
}
