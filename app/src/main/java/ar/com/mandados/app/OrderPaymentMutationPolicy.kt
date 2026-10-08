package ar.com.mandados.app

internal data class OrderPaymentEconomicSnapshot(
    val channel: PaymentChannel,
    val expectedAmount: Int
)

internal enum class OrderPaymentMutationAction {
    DENY,
    ORDER_ONLY,
    ORDER_AND_PAYMENT
}

internal fun paymentChannelFor(method: DeliveryPaymentMethod): PaymentChannel = when (method) {
    DeliveryPaymentMethod.CASH -> PaymentChannel.CASH
    DeliveryPaymentMethod.TRANSFER -> PaymentChannel.RIDER_TRANSFER
    DeliveryPaymentMethod.QR -> PaymentChannel.QR_INTEROPERABLE
    DeliveryPaymentMethod.ONLINE -> PaymentChannel.ONLINE_CHECKOUT
}

internal fun expectedPaymentAmountFor(order: LocalOrder): Int = order.totalAmount ?: 0

internal fun economicSnapshotFor(order: LocalOrder): OrderPaymentEconomicSnapshot =
    OrderPaymentEconomicSnapshot(
        channel = paymentChannelFor(order.deliveryPayment),
        expectedAmount = expectedPaymentAmountFor(order)
    )

internal fun economicSnapshotFor(payment: PaymentRecord): OrderPaymentEconomicSnapshot =
    OrderPaymentEconomicSnapshot(
        channel = payment.channel,
        expectedAmount = payment.expectedAmount
    )

internal fun isPaymentFinanciallyCommitted(payment: PaymentRecord): Boolean =
    payment.status != PaymentStatus.PENDING ||
        !payment.proofUri.isNullOrBlank() ||
        !payment.providerPaymentId.isNullOrBlank()

internal fun orderHasConsistentKnownPriceState(order: LocalOrder): Boolean =
    order.totalAmount != null || order.status == OrderStatus.AWAITING_QUOTE

internal fun evaluateOrderPaymentMutation(
    currentOrder: LocalOrder,
    candidateOrder: LocalOrder,
    payment: PaymentRecord?
): OrderPaymentMutationAction {
    if (currentOrder.id != candidateOrder.id) return OrderPaymentMutationAction.DENY
    if (currentOrder.totalAmount != null && candidateOrder.totalAmount == null) {
        return OrderPaymentMutationAction.DENY
    }
    if (!orderHasConsistentKnownPriceState(candidateOrder)) return OrderPaymentMutationAction.DENY
    if (payment == null) return OrderPaymentMutationAction.ORDER_AND_PAYMENT
    if (payment.orderId != currentOrder.id) return OrderPaymentMutationAction.DENY

    val currentEconomic = economicSnapshotFor(currentOrder)
    val candidateEconomic = economicSnapshotFor(candidateOrder)
    val paymentEconomic = economicSnapshotFor(payment)
    if (isPaymentFinanciallyCommitted(payment)) {
        return if (
            currentEconomic == candidateEconomic &&
            candidateEconomic == paymentEconomic
        ) {
            OrderPaymentMutationAction.ORDER_ONLY
        } else {
            OrderPaymentMutationAction.DENY
        }
    }

    return if (
        candidateEconomic == paymentEconomic &&
        payment.riderId == candidateOrder.assignedRiderId
    ) {
        OrderPaymentMutationAction.ORDER_ONLY
    } else {
        OrderPaymentMutationAction.ORDER_AND_PAYMENT
    }
}

internal fun isCommittedRiderTransferAssignmentLocked(
    currentOrder: LocalOrder,
    candidateRiderId: String?,
    payment: PaymentRecord?
): Boolean =
    payment != null &&
        payment.orderId == currentOrder.id &&
        payment.channel == PaymentChannel.RIDER_TRANSFER &&
        isPaymentFinanciallyCommitted(payment) &&
        currentOrder.assignedRiderId != candidateRiderId

internal fun isCleanPendingPaymentForTake(order: LocalOrder, payment: PaymentRecord): Boolean =
    payment.orderId == order.id &&
        payment.status == PaymentStatus.PENDING &&
        !isPaymentFinanciallyCommitted(payment) &&
        orderHasConsistentKnownPriceState(order) &&
        economicSnapshotFor(order) == economicSnapshotFor(payment) &&
        payment.riderId == order.assignedRiderId

internal fun paymentRecordSynchronizedToOrder(
    order: LocalOrder,
    existing: PaymentRecord?,
    timestamp: String
): PaymentRecord {
    val economic = economicSnapshotFor(order)
    return if (existing == null) {
        PaymentRecord(
            id = "PAY-${order.id}",
            orderId = order.id,
            riderId = order.assignedRiderId,
            channel = economic.channel,
            expectedAmount = economic.expectedAmount,
            createdAt = timestamp,
            updatedAt = timestamp
        )
    } else {
        existing.copy(
            riderId = order.assignedRiderId,
            channel = economic.channel,
            expectedAmount = economic.expectedAmount,
            updatedAt = timestamp
        )
    }
}
