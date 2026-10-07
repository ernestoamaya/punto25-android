package ar.com.mandados.app

internal enum class AdminAccessResult {
    AUTHORIZED,
    UNAUTHORIZED,
    UNAVAILABLE
}

internal data class AdminAccessHttpResult(
    val statusCode: Int,
    val authorized: Boolean?
)

internal suspend fun evaluateAdminAccess(
    tokenProvider: suspend () -> String?,
    requester: suspend (String) -> AdminAccessHttpResult
): AdminAccessResult {
    val token = try {
        tokenProvider()
    } catch (_: Exception) {
        return AdminAccessResult.UNAVAILABLE
    }

    if (token.isNullOrBlank()) return AdminAccessResult.UNAUTHORIZED

    return try {
        val response = requester(token)
        when {
            response.statusCode == 200 && response.authorized == true -> AdminAccessResult.AUTHORIZED
            response.statusCode == 401 || response.statusCode == 403 -> AdminAccessResult.UNAUTHORIZED
            else -> AdminAccessResult.UNAVAILABLE
        }
    } catch (_: Exception) {
        AdminAccessResult.UNAVAILABLE
    }
}

internal class AdminAccessSession {
    var authorized: Boolean = false
        private set

    fun apply(result: AdminAccessResult) {
        authorized = result == AdminAccessResult.AUTHORIZED
    }

    fun clear() {
        authorized = false
    }
}
