package ar.com.mandados.app

internal fun canOfferDigitalTip(order: LocalOrder, tipsEnabled: Boolean): Boolean =
    tipsEnabled &&
        order.operationMode == OperationMode.MULTI_RIDER &&
        order.status == OrderStatus.COMPLETED &&
        order.deliveryPayment == DeliveryPaymentMethod.TRANSFER &&
        !order.assignedRiderId.isNullOrBlank()

internal fun normalizedDigitalTipAmount(order: LocalOrder, tipsEnabled: Boolean, requestedAmount: Int): Int =
    if (canOfferDigitalTip(order, tipsEnabled)) requestedAmount.coerceAtLeast(0) else 0

internal fun tipBelongsToAssignedRider(order: LocalOrder, rating: OrderRating, riderId: String): Boolean =
    riderId.isNotBlank() &&
        rating.orderId == order.id &&
        rating.riderId == riderId &&
        order.assignedRiderId == riderId &&
        order.operationMode == OperationMode.MULTI_RIDER &&
        order.status == OrderStatus.COMPLETED &&
        order.deliveryPayment == DeliveryPaymentMethod.TRANSFER &&
        rating.tipAmount > 0

internal fun canCustomerDeclareTipTransfer(order: LocalOrder, rating: OrderRating): Boolean =
    order.operationMode == OperationMode.MULTI_RIDER &&
        order.status == OrderStatus.COMPLETED &&
        order.deliveryPayment == DeliveryPaymentMethod.TRANSFER &&
        order.assignedRiderId != null &&
        rating.orderId == order.id &&
        rating.riderId == order.assignedRiderId &&
        rating.tipAmount > 0 &&
        rating.tipStatus == TipStatus.SELECTED

internal fun isCustomerTipDeclarationIdempotent(order: LocalOrder, rating: OrderRating): Boolean =
    order.operationMode == OperationMode.MULTI_RIDER &&
        order.status == OrderStatus.COMPLETED &&
        order.deliveryPayment == DeliveryPaymentMethod.TRANSFER &&
        order.assignedRiderId != null &&
        rating.orderId == order.id &&
        rating.riderId == order.assignedRiderId &&
        rating.tipAmount > 0 &&
        rating.tipStatus == TipStatus.TRANSFER_DECLARED

internal fun declareTipTransferState(order: LocalOrder, rating: OrderRating): OrderRating? = when {
    isCustomerTipDeclarationIdempotent(order, rating) -> rating
    canCustomerDeclareTipTransfer(order, rating) -> rating.copy(tipStatus = TipStatus.TRANSFER_DECLARED)
    else -> null
}

internal fun canRiderConfirmTipTransfer(order: LocalOrder, rating: OrderRating, riderId: String): Boolean =
    tipBelongsToAssignedRider(order, rating, riderId) && rating.tipStatus == TipStatus.TRANSFER_DECLARED

internal fun isRiderTipConfirmationIdempotent(order: LocalOrder, rating: OrderRating, riderId: String): Boolean =
    tipBelongsToAssignedRider(order, rating, riderId) && rating.tipStatus == TipStatus.CONFIRMED

internal fun confirmTipTransferState(order: LocalOrder, rating: OrderRating, riderId: String): OrderRating? = when {
    isRiderTipConfirmationIdempotent(order, rating, riderId) -> rating
    canRiderConfirmTipTransfer(order, rating, riderId) -> rating.copy(tipStatus = TipStatus.CONFIRMED)
    else -> null
}

internal fun isDigitalTipRecord(order: LocalOrder, rating: OrderRating): Boolean =
    order.operationMode == OperationMode.MULTI_RIDER &&
        order.status == OrderStatus.COMPLETED &&
        order.deliveryPayment == DeliveryPaymentMethod.TRANSFER &&
        order.assignedRiderId != null &&
        rating.orderId == order.id &&
        rating.riderId == order.assignedRiderId &&
        rating.tipAmount > 0 &&
        rating.tipStatus != TipStatus.NONE

internal fun confirmedDigitalTipAmount(order: LocalOrder, rating: OrderRating, riderId: String): Int =
    if (tipBelongsToAssignedRider(order, rating, riderId) && rating.tipStatus == TipStatus.CONFIRMED) {
        rating.tipAmount
    } else {
        0
    }

internal fun calculateRiderBalance(
    riderId: String,
    orders: List<LocalOrder>,
    ratings: List<OrderRating>
): Int {
    val eligibleOrders = orders.filter {
        it.assignedRiderId == riderId && it.status == OrderStatus.COMPLETED
    }
    val byId = eligibleOrders.associateBy { it.id }
    val serviceTotal = eligibleOrders.sumOf { it.totalAmount ?: 0 }
    val tipTotal = ratings.sumOf { rating ->
        val ownOrder = byId[rating.orderId]
        if (ownOrder == null) 0 else confirmedDigitalTipAmount(ownOrder, rating, riderId)
    }
    return serviceTotal + tipTotal
}
