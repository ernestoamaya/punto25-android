package ar.com.mandados.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TipTransferPolicyTest {
    private fun order(
        payment: DeliveryPaymentMethod = DeliveryPaymentMethod.TRANSFER,
        riderId: String? = "RID-A",
        status: OrderStatus = OrderStatus.COMPLETED
    ) = LocalOrder(
        id = "P25-TIP-TEST",
        createdAt = "04/10/2026 06:00:00",
        serviceType = ServiceType.DELIVERY,
        status = status,
        customerName = "Cliente A",
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
        deliveryPayment = payment
    )

    private fun rating(
        status: TipStatus = TipStatus.SELECTED,
        riderId: String = "RID-A",
        amount: Int = 1000
    ) = OrderRating(
        orderId = "P25-TIP-TEST",
        customerId = "DEV-2345555555",
        riderId = riderId,
        stars = 5,
        tipAmount = amount,
        tipStatus = status,
        createdAt = "04/10/2026 06:10:00"
    )

    @Test fun `REG-PAY-TIP-001 transfer tip follows separate declared and confirmed states`() {
        val o = order()
        val selected = rating()
        val declared = declareTipTransferState(o, selected)
        requireNotNull(declared)
        assertEquals(TipStatus.TRANSFER_DECLARED, declared.tipStatus)
        val confirmed = confirmTipTransferState(o, declared, "RID-A")
        requireNotNull(confirmed)
        assertEquals(TipStatus.CONFIRMED, confirmed.tipStatus)
    }

    @Test fun `REG-PAY-TIP-002 cash order never creates a digital tip amount`() {
        val o = order(payment = DeliveryPaymentMethod.CASH)
        assertFalse(canOfferDigitalTip(o, true))
        assertEquals(0, normalizedDigitalTipAmount(o, true, 2000))
    }

    @Test fun `REG-PAY-TIP-003 tip transition is independent from confirmed service PaymentRecord`() {
        val servicePayment = PaymentRecord(
            id = "PAY-P25-TIP-TEST",
            orderId = "P25-TIP-TEST",
            riderId = "RID-A",
            channel = PaymentChannel.RIDER_TRANSFER,
            expectedAmount = 2500,
            status = PaymentStatus.CONFIRMED,
            proofUri = "content://service-proof",
            createdAt = "04/10/2026 06:00:00",
            updatedAt = "04/10/2026 06:05:00"
        )
        val before = servicePayment.copy()
        val declared = requireNotNull(declareTipTransferState(order(), rating()))
        requireNotNull(confirmTipTransferState(order(), declared, "RID-A"))
        assertEquals(before, servicePayment)
        assertEquals(PaymentStatus.CONFIRMED, servicePayment.status)
        assertEquals(2500, servicePayment.expectedAmount)
        assertEquals("content://service-proof", servicePayment.proofUri)
    }

    @Test fun `REG-PAY-TIP-004 wrong Rider cannot confirm a tip`() {
        assertEquals(null, confirmTipTransferState(order(), rating(TipStatus.TRANSFER_DECLARED), "RID-B"))
    }

    @Test fun `REG-PAY-TIP-005 tip with mismatched assigned Rider fails closed`() {
        assertEquals(null, declareTipTransferState(order(riderId = "RID-B"), rating(riderId = "RID-A")))
    }

    @Test fun `REG-PAY-TIP-006 selected and declared tips do not count in balance`() {
        val o = order()
        assertEquals(0, confirmedDigitalTipAmount(o, rating(TipStatus.SELECTED), "RID-A"))
        assertEquals(0, confirmedDigitalTipAmount(o, rating(TipStatus.TRANSFER_DECLARED), "RID-A"))
    }

    @Test fun `REG-PAY-TIP-007 only confirmed transfer tip counts in balance`() {
        assertEquals(1000, confirmedDigitalTipAmount(order(), rating(TipStatus.CONFIRMED), "RID-A"))
    }

    @Test fun `REG-PAY-TIP-008 legacy confirmed cash tip is preserved but excluded from balance`() {
        val legacy = rating(TipStatus.CONFIRMED, amount = 1500)
        assertEquals(0, confirmedDigitalTipAmount(order(payment = DeliveryPaymentMethod.CASH), legacy, "RID-A"))
    }

    @Test fun `REG-PAY-TIP-009 duplicate declaration is idempotent`() {
        val declared = rating(TipStatus.TRANSFER_DECLARED)
        assertSame(declared, declareTipTransferState(order(), declared))
    }

    @Test fun `REG-PAY-TIP-010 duplicate confirmation is idempotent`() {
        val confirmed = rating(TipStatus.CONFIRMED)
        assertSame(confirmed, confirmTipTransferState(order(), confirmed, "RID-A"))
    }

    @Test fun `REG-PAY-TIP-011 disabled tips force submitted amount to zero`() {
        assertEquals(0, normalizedDigitalTipAmount(order(), false, 1000))
    }

    @Test fun `REG-PAY-TIP-012 incomplete order cannot enter digital tip flow`() {
        val active = order(status = OrderStatus.IN_PROGRESS)
        assertFalse(canOfferDigitalTip(active, true))
        assertEquals(null, declareTipTransferState(active, rating()))
    }

    @Test fun `REG-PAY-TIP-013 zero tip never enters transfer flow`() {
        assertEquals(null, declareTipTransferState(order(), rating(amount = 0)))
    }

    @Test fun `REG-PAY-TIP-014 digital tip record excludes cash legacy data`() {
        assertTrue(isDigitalTipRecord(order(), rating(TipStatus.TRANSFER_DECLARED)))
        assertFalse(isDigitalTipRecord(order(payment = DeliveryPaymentMethod.CASH), rating(TipStatus.CONFIRMED)))
    }

    @Test fun `REG-PAY-TIP-016 authenticated Rider identity must match claimed actor`() {
        assertTrue(riderActorMatchesAuthenticatedSession("RID-A", "RID-A"))
        assertFalse(riderActorMatchesAuthenticatedSession(null, "RID-A"))
        assertFalse(riderActorMatchesAuthenticatedSession("RID-B", "RID-A"))
    }

    @Test fun `REG-PAY-TIP-015 balance source includes only confirmed transfer tips`() {
        val transferOrder = order()
        val cashOrder = order(payment = DeliveryPaymentMethod.CASH).copy(id = "P25-CASH", totalAmount = 3000)
        val transferTip = rating(TipStatus.CONFIRMED, amount = 1000)
        val cashLegacyTip = rating(TipStatus.CONFIRMED, amount = 5000).copy(orderId = "P25-CASH")
        assertEquals(6500, calculateRiderBalance("RID-A", listOf(transferOrder, cashOrder), listOf(transferTip, cashLegacyTip)))
    }
}
