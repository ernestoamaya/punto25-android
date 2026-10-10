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
    RiderSessionAuthority.clear()
    logoutRider()
}
