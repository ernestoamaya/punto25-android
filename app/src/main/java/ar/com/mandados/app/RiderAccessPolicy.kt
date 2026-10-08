package ar.com.mandados.app

enum class RiderAuthenticationStatus {
    AUTHENTICATED,
    INVALID_CREDENTIALS,
    DEACTIVATED
}

data class RiderAuthenticationResult(
    val status: RiderAuthenticationStatus,
    val riderId: String? = null,
    val riderName: String? = null
)

enum class RiderDenialReason {
    SESSION_REQUIRED,
    ACCOUNT_DEACTIVATED,
    PENDING_APPROVAL,
    SUSPENDED,
    DOCUMENT_NOT_UPLOADED,
    DOCUMENT_PENDING,
    DOCUMENT_REJECTED,
    OPERATION_MODE_UNAVAILABLE,
    NO_ACTIVE_SHIFT,
    SHIFT_NOT_AVAILABLE,
    SHIFT_FULL,
    REJOIN_BLOCKED,
    REJOIN_COOLDOWN,
    NO_CAPACITY,
    RIDER_NOT_AVAILABLE,
    ORDER_NOT_AVAILABLE
}

data class RiderEligibilityDecision(
    val allowed: Boolean,
    val reason: RiderDenialReason? = null,
    val documentKey: RiderDocumentKey? = null
) {
    companion object {
        val ALLOWED = RiderEligibilityDecision(true)

        fun denied(reason: RiderDenialReason, documentKey: RiderDocumentKey? = null) =
            RiderEligibilityDecision(false, reason, documentKey)
    }
}

fun requiredRiderDocumentKeys(rider: RiderProfile): List<RiderDocumentKey> =
    if (rider.vehicleType == VehicleType.MOTORCYCLE) {
        RiderDocumentKey.entries
    } else {
        listOf(RiderDocumentKey.DNI_FRONT, RiderDocumentKey.DNI_BACK)
    }

fun riderDocumentEligibility(rider: RiderProfile): RiderEligibilityDecision {
    for (key in requiredRiderDocumentKeys(rider)) {
        when (rider.reviewFor(key)) {
            DocumentReviewStatus.NOT_UPLOADED ->
                return RiderEligibilityDecision.denied(RiderDenialReason.DOCUMENT_NOT_UPLOADED, key)
            DocumentReviewStatus.PENDING ->
                return RiderEligibilityDecision.denied(RiderDenialReason.DOCUMENT_PENDING, key)
            DocumentReviewStatus.REJECTED ->
                return RiderEligibilityDecision.denied(RiderDenialReason.DOCUMENT_REJECTED, key)
            DocumentReviewStatus.APPROVED -> Unit
        }
    }
    return RiderEligibilityDecision.ALLOWED
}

fun riderAccountEligibility(rider: RiderProfile): RiderEligibilityDecision {
    if (!rider.active) return RiderEligibilityDecision.denied(RiderDenialReason.ACCOUNT_DEACTIVATED)
    when (rider.approvalStatus) {
        RiderApprovalStatus.PENDING ->
            return RiderEligibilityDecision.denied(RiderDenialReason.PENDING_APPROVAL)
        RiderApprovalStatus.SUSPENDED ->
            return RiderEligibilityDecision.denied(RiderDenialReason.SUSPENDED)
        RiderApprovalStatus.APPROVED -> Unit
    }
    return riderDocumentEligibility(rider)
}

fun riderHasApprovedRequiredDocuments(rider: RiderProfile): Boolean =
    riderDocumentEligibility(rider).allowed

fun riderWorkspaceSessionValid(controller: MandadosController, riderId: String?): Boolean =
    !riderId.isNullOrBlank() && controller.hasAuthenticatedRiderSession(riderId)

fun authenticatedRiderActiveOrders(controller: MandadosController, riderId: String): List<LocalOrder> {
    if (!controller.hasAuthenticatedRiderSession(riderId)) return emptyList()
    return controller.orders.filter {
        it.assignedRiderId == riderId &&
            it.status in setOf(OrderStatus.PENDING, OrderStatus.ACCEPTED, OrderStatus.IN_PROGRESS)
    }
}

internal fun authenticatedAssignedRiderOrder(
    controller: MandadosController,
    riderId: String,
    orderId: String
): LocalOrder? {
    if (!controller.hasAuthenticatedRiderSession(riderId)) return null
    val order = controller.order(orderId) ?: return null
    return order.takeIf { it.assignedRiderId == riderId }
}

internal fun authenticatedRiderNavigationDestinations(
    controller: MandadosController,
    riderId: String,
    orderId: String
): List<RiderOrderNavigationDestination> =
    authenticatedAssignedRiderOrder(controller, riderId, orderId)
        ?.let(::riderOrderNavigationDestinations)
        .orEmpty()

internal fun authenticatedRiderOrderLocations(
    controller: MandadosController,
    riderId: String,
    orderId: String
): List<OrderLocationPoint> =
    authenticatedAssignedRiderOrder(controller, riderId, orderId)
        ?.let(::orderLocationPoints)
        .orEmpty()
