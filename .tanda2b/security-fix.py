from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected exactly one match, found {count}")
    p.write_text(text.replace(old, new, 1), encoding="utf-8")


controller = "app/src/main/java/ar/com/mandados/app/MandadosController.kt"
app = "app/src/main/java/ar/com/mandados/app/MandadosApp.kt"
policy_test = "app/src/test/java/ar/com/mandados/app/TipTransferPolicyTest.kt"
matrix = "REGRESSION_MATRIX.md"
policy = "app/src/main/java/ar/com/mandados/app/TipTransferPolicy.kt"

replace_once(
    controller,
    "    var pendingCustomer by mutableStateOf<Customer?>(null)\n\n    init {",
    "    var pendingCustomer by mutableStateOf<Customer?>(null)\n    private var authenticatedRiderId: String? = null\n\n    init {",
)

replace_once(
    controller,
    "    fun riderDigitalTipForOrder(orderId: String, riderId: String): OrderRating? {\n        if (rider(riderId) == null) return null",
    "    fun riderDigitalTipForOrder(orderId: String, riderId: String): OrderRating? {\n        if (!hasAuthenticatedRiderSession(riderId)) return null",
)

replace_once(
    controller,
    "    fun pendingTipsForRider(riderId: String): List<OrderRating> {\n        if (rider(riderId) == null) return emptyList()",
    "    fun pendingTipsForRider(riderId: String): List<OrderRating> {\n        if (!hasAuthenticatedRiderSession(riderId)) return emptyList()",
)

replace_once(
    controller,
    "    fun riderCanConfirmTip(orderId: String, riderId: String): Boolean {\n        if (rider(riderId) == null) return false",
    "    fun riderCanConfirmTip(orderId: String, riderId: String): Boolean {\n        if (!hasAuthenticatedRiderSession(riderId)) return false",
)

replace_once(
    controller,
    "    fun confirmTip(orderId: String, riderId: String): Boolean {\n        if (rider(riderId) == null) return false",
    "    fun confirmTip(orderId: String, riderId: String): Boolean {\n        if (!hasAuthenticatedRiderSession(riderId)) return false",
)

replace_once(
    controller,
    "        store.saveRiderCredentials(riderCredentials)\n        store.saveRiderInvitations(riderInvitations)\n        return invitation.riderId\n    }\n\n    fun authenticateRider(riderId: String, password: String): Boolean {\n        val rider = rider(riderId) ?: return false\n        if (!rider.active) return false\n        val credential = riderCredentials.firstOrNull { it.riderId.equals(riderId.trim(), ignoreCase = true) } ?: return false\n        return verifyRiderPassword(password, credential)\n    }",
    "        store.saveRiderCredentials(riderCredentials)\n        store.saveRiderInvitations(riderInvitations)\n        authenticatedRiderId = invitation.riderId\n        return invitation.riderId\n    }\n\n    fun authenticateRider(riderId: String, password: String): Boolean {\n        authenticatedRiderId = null\n        val rider = rider(riderId) ?: return false\n        if (!rider.active) return false\n        val credential = riderCredentials.firstOrNull { it.riderId.equals(riderId.trim(), ignoreCase = true) } ?: return false\n        val authenticated = verifyRiderPassword(password, credential)\n        if (authenticated) authenticatedRiderId = rider.id\n        return authenticated\n    }\n\n    fun logoutRider() {\n        authenticatedRiderId = null\n    }\n\n    private fun hasAuthenticatedRiderSession(riderId: String): Boolean =\n        riderActorMatchesAuthenticatedSession(authenticatedRiderId, riderId) &&\n            rider(riderId) != null &&\n            hasRiderCredential(riderId)",
)

replace_once(
    app,
    "            Screen.RIDER_WORKSPACE -> if (riderStandalone) Screen.REGISTER else Screen.RIDERS",
    "            Screen.RIDER_WORKSPACE -> {\n                controller.logoutRider()\n                if (riderStandalone) Screen.REGISTER else Screen.RIDERS\n            }",
)

replace_once(
    app,
    "            onBack = { screen = if (riderStandalone) Screen.REGISTER else Screen.RIDERS }\n        )",
    "            onBack = {\n                controller.logoutRider()\n                screen = if (riderStandalone) Screen.REGISTER else Screen.RIDERS\n            }\n        )",
)

replace_once(
    policy,
    "internal fun canOfferDigitalTip(order: LocalOrder, tipsEnabled: Boolean): Boolean =",
    "internal fun riderActorMatchesAuthenticatedSession(authenticatedRiderId: String?, riderId: String): Boolean =\n    !authenticatedRiderId.isNullOrBlank() && authenticatedRiderId == riderId\n\ninternal fun canOfferDigitalTip(order: LocalOrder, tipsEnabled: Boolean): Boolean =",
)

replace_once(
    policy_test,
    "    @Test fun `REG-PAY-TIP-015 balance source includes only confirmed transfer tips`() {",
    "    @Test fun `REG-PAY-TIP-016 authenticated Rider identity must match claimed actor`() {\n        assertTrue(riderActorMatchesAuthenticatedSession(\"RID-A\", \"RID-A\"))\n        assertFalse(riderActorMatchesAuthenticatedSession(null, \"RID-A\"))\n        assertFalse(riderActorMatchesAuthenticatedSession(\"RID-B\", \"RID-A\"))\n    }\n\n    @Test fun `REG-PAY-TIP-015 balance source includes only confirmed transfer tips`() {",
)

replace_once(
    matrix,
    "| REG-PAY-TIP-015 | El balance usa una única regla y sólo suma propinas de transferencia confirmadas. | `TipTransferPolicyTest.REG-PAY-TIP-015…` |\n",
    "| REG-PAY-TIP-015 | El balance usa una única regla y sólo suma propinas de transferencia confirmadas. | `TipTransferPolicyTest.REG-PAY-TIP-015…` |\n| REG-PAY-TIP-016 | La identidad autenticada del Repartidor debe coincidir con el actor que opera la propina; conocer otro RID no autoriza. | `TipTransferPolicyTest.REG-PAY-TIP-016…` |\n",
)
