package ar.com.mandados.app

internal fun authorizeRiderForTest(
    riderId: String,
    sessionId: String = "test-session-$riderId",
    sessionEpoch: Long = 1L
): RiderSessionContext {
    val session = RiderSessionContext(
        riderCode = riderId,
        sessionId = sessionId,
        sessionEpoch = sessionEpoch,
        expiresAt = System.currentTimeMillis() + 60 * 60 * 1000L
    )
    RiderSessionAuthority.install(session)
    check(RiderSessionAuthority.matches(riderId))
    return session
}

internal fun clearRiderSessionForTest() {
    RiderSessionAuthority.clear()
}
