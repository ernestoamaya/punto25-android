package ar.com.mandados.app

import org.junit.Assert.assertFalse
import org.junit.Test

class OrderZoneOverrideBoundaryTest {
    @Test
    fun `REG-ZONE-OVERRIDE-CATALOG-001 rejects missing disabled and zero-price catalog zones`() {
        val config = AdminConfig(
            zones = listOf(
                ZoneConfig("valid", "Válida", "", "TEST", 1000, true),
                ZoneConfig("disabled", "Deshabilitada", "", "TEST", 1200, false),
                ZoneConfig("zero", "Sin tarifa", "", "TEST", 0, true)
            )
        )

        assertFalse(resolveOrderZoneOverrideSelection(OrderZoneOverrideSelection.Catalog("missing"), config).isSuccess)
        assertFalse(resolveOrderZoneOverrideSelection(OrderZoneOverrideSelection.Catalog("disabled"), config).isSuccess)
        assertFalse(resolveOrderZoneOverrideSelection(OrderZoneOverrideSelection.Catalog("zero"), config).isSuccess)
    }

    @Test
    fun `REG-ZONE-OVERRIDE-TERMINAL-001 simple whatsapp is fail-closed for zone override`() {
        val config = AdminConfig(
            operationMode = OperationMode.MULTI_RIDER,
            zones = listOf(ZoneConfig("a", "Zona A", "", "TEST", 1000, true))
        )
        val order = LocalOrder(
            id = "SIMPLE",
            createdAt = "08/10/2026 12:00:00",
            serviceType = ServiceType.DELIVERY,
            operationMode = OperationMode.SIMPLE_WHATSAPP,
            status = OrderStatus.PENDING,
            customerName = "Cliente",
            customerPhone = "234-5555999",
            detail = "Fixture",
            baseAmount = 1000,
            baseZoneName = "Zona A",
            prePickupAmount = 0,
            rainAmount = 0,
            totalAmount = 1000,
            whatsappMessage = "",
            originZoneId = "a",
            destinationZoneId = "a"
        )
        val payment = PaymentRecord(
            id = "PAY-SIMPLE",
            orderId = order.id,
            channel = PaymentChannel.CASH,
            expectedAmount = 1000,
            createdAt = order.createdAt,
            updatedAt = order.createdAt
        )

        val evaluation = evaluateOrderZoneOverride(
            currentOrder = order,
            point = OrderZonePoint.ORIGIN,
            selection = OrderZoneOverrideSelection.AdHoc("Temporal", 1500),
            config = config,
            paymentMatches = listOf(payment)
        )

        assertFalse(evaluation.allowed)
    }
}
