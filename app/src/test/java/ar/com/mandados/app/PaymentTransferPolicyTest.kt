package ar.com.mandados.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentTransferPolicyTest {
    private fun order(riderId: String? = "RID-A") = LocalOrder(
        id = "P25-PAY-TEST",
        createdAt = "03/10/2026 04:00:00",
        serviceType = ServiceType.DELIVERY,
        status = OrderStatus.ACCEPTED,
        customerName = "Cliente",
        customerPhone = "2345-555555",
        detail = "Prueba",
        baseAmount = 2500,
        baseZoneName = "Urbana",
        prePickupAmount = 0,
        rainAmount = 0,
        totalAmount = 2500,
        whatsappMessage = "",
        assignedRiderId = riderId,
        customerId = "DEV-2345555555",
        deliveryPayment = DeliveryPaymentMethod.TRANSFER
    )

    private fun payment(
        status: PaymentStatus = PaymentStatus.PENDING,
        riderId: String? = "RID-A",
        channel: PaymentChannel = PaymentChannel.RIDER_TRANSFER,
        proofUri: String? = null
    ) = PaymentRecord(
        id = "PAY-P25-PAY-TEST",
        orderId = "P25-PAY-TEST",
        riderId = riderId,
        channel = channel,
        expectedAmount = 2500,
        status = status,
        proofUri = proofUri,
        createdAt = "03/10/2026 04:00:00",
        updatedAt = "03/10/2026 04:00:00"
    )

    @Test fun newTransferStartsPendingAndCanBeDeclared() {
        val p = payment()
        assertTrue(p.status == PaymentStatus.PENDING)
        assertTrue(canCustomerDeclareTransfer(p))
    }

    @Test fun cashCannotBeDeclaredAsTransfer() {
        assertFalse(canCustomerDeclareTransfer(payment(channel = PaymentChannel.CASH)))
    }

    @Test fun proofCannotBeAttachedBeforeDeclaration() {
        assertFalse(canCustomerAttachTransferProof(payment(PaymentStatus.PENDING)))
    }

    @Test fun proofCanBeAttachedAfterDeclaration() {
        assertTrue(canCustomerAttachTransferProof(payment(PaymentStatus.DECLARED)))
    }

    @Test fun pendingTransferIsPendingButNotAttention() {
        val p = payment()
        assertTrue(p.status in transferPendingStatuses)
        assertFalse(transferRequiresAttention(p))
    }

    @Test fun declaredTransferRequiresAttention() {
        assertTrue(transferRequiresAttention(payment(PaymentStatus.DECLARED)))
    }

    @Test fun proofUploadedRequiresAttention() {
        assertTrue(transferRequiresAttention(payment(PaymentStatus.PROOF_UPLOADED, proofUri = "content://proof")))
    }

    @Test fun inReviewRequiresAttention() {
        assertTrue(transferRequiresAttention(payment(PaymentStatus.IN_REVIEW)))
    }

    @Test fun confirmedIsCompletedNotPending() {
        val p = payment(PaymentStatus.CONFIRMED)
        assertFalse(p.status in transferPendingStatuses)
        assertFalse(transferRequiresAttention(p))
    }

    @Test fun cashNeverRequiresTransferAttention() {
        assertFalse(transferRequiresAttention(payment(PaymentStatus.DECLARED, channel = PaymentChannel.CASH)))
    }

    @Test fun correctRiderOwnsTransfer() {
        assertTrue(riderOwnsTransfer(order(), payment(), "RID-A"))
    }

    @Test fun legacyNullPaymentRiderUsesCurrentAssignment() {
        assertTrue(riderOwnsTransfer(order(), payment(riderId = null), "RID-A"))
    }

    @Test fun wrongRiderCannotAccessTransfer() {
        assertFalse(riderOwnsTransfer(order(), payment(), "RID-B"))
    }

    @Test fun staleContradictoryPaymentRiderFailsClosed() {
        assertFalse(riderOwnsTransfer(order("RID-A"), payment(riderId = "RID-B"), "RID-A"))
    }

    @Test fun pendingCannotBeConfirmedEvenWhenProofOptional() {
        assertFalse(canRiderConfirmTransfer(order(), payment(), "RID-A", false))
    }

    @Test fun declaredCanBeConfirmedWhenProofOptional() {
        assertTrue(canRiderConfirmTransfer(order(), payment(PaymentStatus.DECLARED), "RID-A", false))
    }

    @Test fun requiredProofBlocksDeclarationWithoutProof() {
        assertFalse(canRiderConfirmTransfer(order(), payment(PaymentStatus.DECLARED), "RID-A", true))
    }

    @Test fun requiredProofAllowsConfirmationWhenPresent() {
        assertTrue(canRiderConfirmTransfer(order(), payment(PaymentStatus.PROOF_UPLOADED, proofUri = "content://proof"), "RID-A", true))
    }

    @Test fun inReviewCanLaterBeConfirmed() {
        assertTrue(canRiderConfirmTransfer(order(), payment(PaymentStatus.IN_REVIEW, proofUri = "content://proof"), "RID-A", true))
    }

    @Test fun wrongRiderCannotConfirm() {
        assertFalse(canRiderConfirmTransfer(order(), payment(PaymentStatus.PROOF_UPLOADED, proofUri = "content://proof"), "RID-B", true))
    }

    @Test fun ownerCanReportMissingAccreditation() {
        assertTrue(canRiderReportTransfer(order(), payment(PaymentStatus.DECLARED), "RID-A"))
    }

    @Test fun wrongRiderCannotReportMissingAccreditation() {
        assertFalse(canRiderReportTransfer(order(), payment(PaymentStatus.DECLARED), "RID-B"))
    }
}
