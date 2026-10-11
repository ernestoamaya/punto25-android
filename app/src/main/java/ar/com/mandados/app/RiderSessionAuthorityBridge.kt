package ar.com.mandados.app

/**
 * Compatibility bridge used by the existing P23-A2 navigation/controller wiring.
 * Authority itself lives exclusively in RiderSessionAuthority (process memory).
 */
internal fun RiderSessionAuthority.current(): RiderSessionContext? = snapshot()

internal fun RiderSessionAuthority.currentFor(riderCode: String): RiderSessionContext? =
    snapshot()?.takeIf { it.riderCode.equals(riderCode, ignoreCase = true) }

internal fun MandadosController.applyAuthenticatedRiderSession(session: RiderSessionContext): Boolean {
    if (!session.isStructurallyValid()) return false
    val localRider = rider(session.riderCode) ?: return false
    if (!localRider.active || localRider.approvalStatus == RiderApprovalStatus.SUSPENDED) return false
    RiderSessionAuthority.install(session)
    return RiderSessionAuthority.matches(localRider.id)
}

internal fun MandadosController.currentRiderSession(): RiderSessionContext? =
    RiderSessionAuthority.snapshot()

internal fun MandadosController.clearAuthenticatedRiderSession() {
    // The live UI invokes this synchronously before scheduling remote logout work. When an
    // authenticated Rider authority exists, remove the durable restore hint and Firebase
    // current-user state now; the coordinator retains only a one-shot token provider for the
    // later best-effort backend DELETE. On process recreation there is no in-memory authority,
    // so startup restore remains allowed to validate an existing cache hint against backend.
    if (RiderSessionAuthority.snapshot() != null) {
        RiderFirebaseAuthIntegration.invalidateBoundLocalLogoutState()
    }
    RiderSessionAuthority.clear()
    logoutRider()
}