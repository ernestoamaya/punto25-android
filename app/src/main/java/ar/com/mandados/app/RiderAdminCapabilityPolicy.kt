package ar.com.mandados.app

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal suspend fun RiderAdminCapabilityService.createForCurrentState(
    riderCode: String
): Result<RiderAdminCapability> = createForExistingFlow(riderCode)

internal fun riderCapabilityExpiryText(expiresAt: Long): String? {
    if (expiresAt <= 0L) return null
    val instant = runCatching { Instant.ofEpochMilli(expiresAt) }.getOrNull() ?: return null
    return DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(instant)
}
