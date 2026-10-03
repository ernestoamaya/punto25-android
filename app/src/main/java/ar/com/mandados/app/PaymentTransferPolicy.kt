package ar.com.mandados.app

internal val transferPendingStatuses: Set<PaymentStatus> = setOf(
    PaymentStatus.PENDING,
    PaymentStatus.DECLARED,
    PaymentStatus.PROOF_UPLOADED,
    PaymentStatus.IN_REVIEW
)

internal val transferAttentionStatuses: Set<PaymentStatus> = setOf(
    PaymentStatus.DECLARED,
    PaymentStatus.PROOF_UPLOADED,
    PaymentStatus.IN_REVIEW
)

internal fun canCustomerDeclareTransfer(payment: PaymentRecord): Boolean =
    payment.channel == PaymentChannel.RIDER_TRANSFER && payment.status == PaymentStatus.PENDING

internal fun canCustomerAttachTransferProof(payment: PaymentRecord): Boolean =
    payment.channel == PaymentChannel.RIDER_TRANSFER &&
        payment.status in setOf(PaymentStatus.DECLARED, PaymentStatus.PROOF_UPLOADED, PaymentStatus.IN_REVIEW)

internal fun transferRequiresAttention(payment: PaymentRecord): Boolean =
    payment.channel == PaymentChannel.RIDER_TRANSFER && payment.status in transferAttentionStatuses

internal fun riderOwnsTransfer(order: LocalOrder, payment: PaymentRecord, riderId: String): Boolean =
    riderId.isNotBlank() &&
        payment.orderId == order.id &&
        payment.channel == PaymentChannel.RIDER_TRANSFER &&
        order.assignedRiderId == riderId &&
        (payment.riderId == null || payment.riderId == riderId)

internal fun canRiderConfirmTransfer(
    order: LocalOrder,
    payment: PaymentRecord,
    riderId: String,
    proofRequired: Boolean
): Boolean =
    riderOwnsTransfer(order, payment, riderId) &&
        payment.status in transferAttentionStatuses &&
        (!proofRequired || !payment.proofUri.isNullOrBlank())

internal fun canRiderReportTransfer(order: LocalOrder, payment: PaymentRecord, riderId: String): Boolean =
    riderOwnsTransfer(order, payment, riderId) && payment.status in transferAttentionStatuses

internal fun transferPaymentStatusLabel(status: PaymentStatus): String = when (status) {
    PaymentStatus.PENDING -> "Pendiente de transferencia"
    PaymentStatus.DECLARED -> "Transferencia informada · pendiente de confirmación"
    PaymentStatus.PROOF_UPLOADED -> "Comprobante adjunto · pendiente de confirmación"
    PaymentStatus.IN_REVIEW -> "En revisión · Repartidor no visualiza acreditación"
    PaymentStatus.CONFIRMED -> "Pago confirmado"
    PaymentStatus.APPROVED -> "Pago aprobado"
    PaymentStatus.REJECTED -> "Pago rechazado"
    PaymentStatus.REFUNDED -> "Pago reintegrado"
}
