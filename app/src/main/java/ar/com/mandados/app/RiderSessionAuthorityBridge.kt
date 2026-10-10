package ar.com.mandados.app

/**
 * Autoridad Rider exclusiva de memoria de proceso.
 *
 * El cache persistido de sesión es sólo un hint para revalidar contra backend después de recrear
 * proceso/app. Nunca se consulta aquí: una recreación arranca sin autoridad hasta que GET
 * /v1/rider/session valida token Firebase + session id exactos.
 */
internal object RiderSessionAuthority {
    @Volatile
    private var session: RiderSessionContext? = null

    @Synchronized
    fun install(candidate: RiderSessionContext) {
        session = candidate.takeIf { it.isStructurallyValid() }
    }

    @Synchronized
    fun clear() {
        session = null
    }

    fun current(): RiderSessionContext? {
        val current = session ?: return null
        if (!current.isStructurallyValid()) {
            clear()
            return null
        }
        return current
    }

    fun currentFor(riderCode: String): RiderSessionContext? =
        current()?.takeIf { it.riderCode.equals(riderCode, ignoreCase = true) }
}

/**
 * Puente transitorio P23-A2 mientras pedidos/turnos/pagos siguen siendo locales hasta P24.
 *
 * El controlador legacy todavía contiene un chequeo de existencia de RiderCredential además del
 * chequeo de sesión. Esta sobrecarga para List<RiderCredential> permite satisfacer únicamente ese
 * chequeo con un marcador efímero derivado de la sesión backend ya validada. No persiste hash,
 * password ni capability y no concede autoridad por sí sola: el mismo flujo exige además
 * riderActorMatchesAuthenticatedSession(), ligado a RiderSessionAuthority.
 */
internal inline fun List<RiderCredential>.any(predicate: (RiderCredential) -> Boolean): Boolean {
    for (credential in this) {
        if (predicate(credential)) return true
    }
    val session = RiderSessionAuthority.current() ?: return false
    val marker = RiderCredential(
        riderId = session.riderCode,
        saltBase64 = "",
        passwordHashBase64 = "",
        iterations = 1,
        updatedAt = session.expiresAt
    )
    return predicate(marker)
}

internal fun MandadosController.applyAuthenticatedRiderSession(session: RiderSessionContext): Boolean {
    if (!session.isStructurallyValid()) return false
    val localRider = rider(session.riderCode) ?: return false
    if (!localRider.active || localRider.approvalStatus == RiderApprovalStatus.SUSPENDED) return false
    RiderSessionAuthority.install(session)
    return RiderSessionAuthority.currentFor(localRider.id) != null
}

internal fun MandadosController.currentRiderSession(): RiderSessionContext? = RiderSessionAuthority.current()

internal fun MandadosController.clearAuthenticatedRiderSession() {
    RiderSessionAuthority.clear()
    logoutRider()
}
