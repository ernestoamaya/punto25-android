package ar.com.mandados.app

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ShiftAlphaResetScopeTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("mandados_alpha1", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `REG-SHIFT-ALPHA-RESET-001 reset v2 conserva cliente config pedidos y pagos`() {
        val store = LocalStore(context)
        val customer = Customer(
            name = "Cliente Reset",
            areaCode = "2345",
            subscriber = "555555",
            locality = "25 de Mayo",
            accountId = "DEV-RESET"
        )
        val config = AdminConfig(acceptingOrders = false, closedMessage = "Mensaje personalizado")
        val order = LocalOrder(
            id = "P25-RESET-SCOPE",
            createdAt = "07/10/2026 08:00:00",
            serviceType = ServiceType.DELIVERY,
            status = OrderStatus.PENDING,
            customerName = customer.name,
            customerPhone = customer.displayPhone,
            customerId = customer.id,
            detail = "Fixture no Turnos",
            baseAmount = 1200,
            baseZoneName = "Urbana",
            prePickupAmount = 0,
            rainAmount = 0,
            totalAmount = 1200,
            whatsappMessage = ""
        )
        val payment = PaymentRecord(
            id = "PAY-${order.id}",
            orderId = order.id,
            channel = PaymentChannel.CASH,
            expectedAmount = 1200,
            createdAt = "07/10/2026 08:00:00",
            updatedAt = "07/10/2026 08:00:00"
        )
        val legacy = ShiftTemplate("LEGACY-RESET", 3, "09:00", "12:00", 1)

        store.saveCustomer(customer)
        store.saveConfig(config)
        store.saveOrders(listOf(order))
        store.savePayments(listOf(payment))
        store.saveShifts(listOf(legacy))

        val controller = MandadosController(context)

        assertTrue(controller.isShiftSubsystemReady())
        assertTrue(controller.concreteShifts.isEmpty())
        assertEquals(customer.id, controller.customer?.id)
        assertFalse(controller.config.acceptingOrders)
        assertEquals("Mensaje personalizado", controller.config.closedMessage)
        assertEquals(listOf(order.id), controller.orders.map { it.id })
        assertEquals(listOf(payment.id), controller.payments.map { it.id })
        assertFalse(context.getSharedPreferences("mandados_alpha1", Context.MODE_PRIVATE).contains("shift_templates"))
    }
}
