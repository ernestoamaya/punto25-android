package ar.com.mandados.app

/**
 * Resolves ownership of an order without rewriting historical data.
 *
 * Exact account IDs are authoritative. For Alpha compatibility we also accept only the
 * deterministic phone-based IDs that Punto25 itself generated historically (CLI-/DEV-),
 * and only when the current customer has a verified 10-digit WhatsApp number.
 *
 * Names, addresses and arbitrary account IDs are intentionally never used as identity
 * evidence, so unresolved legacy records fail closed instead of being assigned by guesswork.
 */
internal fun orderBelongsToCustomer(order: LocalOrder, customer: Customer): Boolean {
    val orderCustomerId = order.customerId.trim()
    if (orderCustomerId.isBlank()) return false

    val currentId = customer.id.trim()
    if (currentId.isNotBlank() && orderCustomerId == currentId) return true

    val nationalNumber = customer.nationalNumber
    if (!customer.whatsappVerified || nationalNumber.length != 10 || !nationalNumber.all(Char::isDigit)) {
        return false
    }

    return orderCustomerId == "CLI-$nationalNumber" ||
        orderCustomerId == "DEV-$nationalNumber"
}
