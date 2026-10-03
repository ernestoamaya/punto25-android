package ar.com.mandados.app

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal data class GoogleIdentity(
    val uid: String,
    val email: String?,
    val displayName: String?
)

internal data class WhatsAppVerificationChallenge(
    val code: String,
    val whatsappNumber: String
)

internal data class WhatsAppVerificationStatus(
    val status: String,
    val verifiedPhone: String? = null
)

internal object GoogleAuthIntegration {
    fun isConfigured(): Boolean =
        BuildConfig.FIREBASE_API_KEY.isNotBlank() &&
            BuildConfig.FIREBASE_APP_ID.isNotBlank() &&
            BuildConfig.FIREBASE_PROJECT_ID.isNotBlank() &&
            BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()

    private fun ensureFirebase(context: Context): FirebaseApp {
        FirebaseApp.getApps(context).firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }?.let { return it }
        check(isConfigured()) { "Firebase/Google no está configurado para esta compilación." }
        val options = FirebaseOptions.Builder()
            .setApiKey(BuildConfig.FIREBASE_API_KEY)
            .setApplicationId(BuildConfig.FIREBASE_APP_ID)
            .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
            .build()
        return FirebaseApp.initializeApp(context.applicationContext, options)
    }

    suspend fun signIn(activity: Activity): Result<GoogleIdentity> = runCatching {
        val app = ensureFirebase(activity)
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .setAutoSelectEnabled(false)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
        val response = CredentialManager.create(activity).getCredential(
            context = activity,
            request = request
        )
        val credential = response.credential
        check(
            credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) { "Google no devolvió una credencial compatible." }
        val google = GoogleIdTokenCredential.createFrom(credential.data)
        val firebaseCredential = GoogleAuthProvider.getCredential(google.idToken, null)
        val result = FirebaseAuth.getInstance(app).signInWithCredential(firebaseCredential).await()
        val user = result.user ?: error("Firebase no devolvió un usuario.")
        GoogleIdentity(user.uid, user.email, user.displayName)
    }

    fun signOut(context: Context) {
        if (!isConfigured()) return
        val app = runCatching { ensureFirebase(context) }.getOrNull() ?: return
        FirebaseAuth.getInstance(app).signOut()
    }

    suspend fun currentIdToken(context: Context): String? {
        if (!isConfigured()) return null
        val app = runCatching { ensureFirebase(context) }.getOrNull() ?: return null
        return FirebaseAuth.getInstance(app).currentUser?.getIdToken(false)?.await()?.token
    }
}

internal object WhatsAppVerificationApi {
    fun isConfigured(): Boolean =
        BuildConfig.PUNTO25_API_BASE_URL.isNotBlank() && GoogleAuthIntegration.isConfigured()

    suspend fun start(context: Context): Result<WhatsAppVerificationChallenge> = runCatching {
        val token = GoogleAuthIntegration.currentIdToken(context) ?: error("No hay una sesión Google válida.")
        val json = request(
            method = "POST",
            path = "/v1/whatsapp/verification/start",
            bearer = token,
            body = "{}"
        )
        WhatsAppVerificationChallenge(
            code = json.getString("code"),
            whatsappNumber = json.optString("whatsappNumber", BuildConfig.WHATSAPP_VERIFY_NUMBER)
        )
    }

    suspend fun status(context: Context, code: String): Result<WhatsAppVerificationStatus> = runCatching {
        val token = GoogleAuthIntegration.currentIdToken(context) ?: error("No hay una sesión Google válida.")
        val json = request(
            method = "GET",
            path = "/v1/whatsapp/verification/status?code=" + java.net.URLEncoder.encode(code, "UTF-8"),
            bearer = token,
            body = null
        )
        WhatsAppVerificationStatus(
            status = json.optString("status", "pending"),
            verifiedPhone = json.optString("verifiedPhone").takeIf { it.isNotBlank() }
        )
    }

    private suspend fun request(method: String, path: String, bearer: String, body: String?): JSONObject =
        withContext(Dispatchers.IO) {
            val base = BuildConfig.PUNTO25_API_BASE_URL.trimEnd('/')
            check(base.startsWith("https://")) { "El backend de producción debe usar HTTPS." }
            val connection = (URL(base + path).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 12_000
                readTimeout = 12_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Authorization", "Bearer " + bearer)
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
            }
            try {
                if (body != null) {
                    connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (code !in 200..299) {
                    val message = runCatching { JSONObject(text).optString("error") }.getOrNull()
                    error(message?.takeIf { it.isNotBlank() } ?: "Error del backend (" + code + ").")
                }
                JSONObject(text.ifBlank { "{}" })
            } finally {
                connection.disconnect()
            }
        }
}
