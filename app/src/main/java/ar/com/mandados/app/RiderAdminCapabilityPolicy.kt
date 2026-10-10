package ar.com.mandados.app

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal suspend fun RiderAdminCapabilityService.createForCurrentState(
    riderCode: String
): Result<RiderAdminCapability> {
    val enrollment = create(riderCode, RiderCapabilityKind.ENROLLMENT)
    if (enrollment.isSuccess) return enrollment

    val error = enrollment.exceptionOrNull()
    return if (error is RiderBackendException && error.statusCode == 409 && error.backendCode == "already_enrolled") {
        create(riderCode, RiderCapabilityKind.RECOVERY)
    } else {
        enrollment
    }
}

internal fun riderCapabilityExpiryText(expiresAt: String): String? {
    val instant = runCatching { Instant.parse(expiresAt) }.getOrNull() ?: return null
    return DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(instant)
}
