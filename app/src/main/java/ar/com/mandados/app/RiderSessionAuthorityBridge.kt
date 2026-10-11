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

/**
 * Real Rider logout/Back boundary. Local cache, in-memory authority and Firebase Rider are
 * invalidated synchronously by [RiderAuthSessionCoordinator.beginLogout] before controller
 * navigation cleanup and before callers are allowed to launch best-effort remote revocation.
 */
internal fun MandadosController.beginAuthenticatedRiderLogout(
    coordinator: RiderAuthSessionCoordinator
): RiderLogoutRevocation {
    val session = currentRiderSession()
    val revocation = coordinator.beginLogout(session)
    clearAuthenticatedRiderSession()
    return revocation
}

internal fun MandadosController.clearAuthenticatedRiderSession() {
    // Compatibility cleanup for non-logout paths. The real Rider logout/Back path above already
    // performs the coordinator boundary first. Keeping this conditional preserves startup restore:
    // process recreation legitimately has no in-memory authority while cache + Firebase remain only
    // a hint that still must be revalidated against backend.
    if (RiderSessionAuthority.snapshot() != null) {
        RiderFirebaseAuthIntegration.invalidateBoundLocalLogoutState()
    }
    RiderSessionAuthority.clear()
    logoutRider()
}