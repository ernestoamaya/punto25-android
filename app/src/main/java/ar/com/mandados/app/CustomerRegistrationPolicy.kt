package ar.com.mandados.app

/**
 * Google/Firebase UID is authoritative. Only unconfigured DEBUG builds retain
 * the existing development identity fallback. Phone verification is optional.
 */
internal fun canConfirmCustomerRegistration(
    pending: Customer,
    googleConfigured: Boolean,
    currentFirebaseUid: String?,
    debugBuild: Boolean
): Boolean {
    if (pending.name.isBlank() || pending.nationalNumber.length != 10 ||
        !pending.nationalNumber.all(Char::isDigit)) return false
    if (googleConfigured) {
        val uid = currentFirebaseUid?.takeIf { it.isNotBlank() } ?: return false
        return pending.googleVerified && pending.accountId == "G-$uid"
    }
    return debugBuild && !pending.googleVerified &&
        pending.accountId == "DEV-${pending.nationalNumber}"
}
