package ar.com.mandados.app

import android.content.Context
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OrderReceptionPolicyTest {
    private lateinit var context: Context

    private val customer = Customer(
        name = "Cliente Recepción",
        areaCode = "2345",
        subscriber = "555555",
        locality = "25 de Mayo",
        accountId = "DEV-RECEPTION",
        whatsappVerified = true,
        whatsappVerifiedAt = "07/10/2026 04:00:00"
    )

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("mandados_alpha1", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `REG-ORDER-RECEPTION-001 pausa rechaza dominio sin efectos persistentes de creacion`() {
        val store = LocalStore(context)
        store.saveCustomer(customer)
        val existing = existingOrder("P25-EXISTING")
        val existingPayment = PaymentRecord(
            id = "PAY-${existing.id}",
            orderId = existing.id,
            channel = PaymentChannel.CASH,
            expectedAmount = 1000,
            createdAt = existing.createdAt,
            updatedAt = existing.createdAt
        )
        store.saveOrders(listOf(existing))
        store.savePayments(listOf(existingPayment))

        val c = MandadosController(context)
        c.draft = completedDraft()
        c.updateConfig(c.config.copy(acceptingOrders = false, closedMessage = "Pausa de prueba"))
        val beforeOrders = c.orders.toList()
        val beforePayments = c.payments.toList()
        val beforeDraft = c.draft

        val result = c.createOrder()

        assertTrue(result is OrderCreationResult.Blocked)
        assertEquals("Pausa de prueba", (result as OrderCreationResult.Blocked).message)
        assertEquals(beforeOrders, c.orders)
        assertEquals(beforePayments, c.payments)
        assertEquals(beforeDraft, c.draft)
        assertEquals(beforeOrders.sumOf { it.events.size }, c.orders.sumOf { it.events.size })

        val restarted = MandadosController(context)
        assertEquals(beforeOrders, restarted.orders)
        assertEquals(beforePayments, restarted.payments)
    }

    @Test
    fun `REG-ORDER-RECEPTION-002 flujo iniciado habilitado queda bloqueado si Admin pausa antes de enviar`() {
        LocalStore(context).saveCustomer(customer)
        val c = MandadosController(context)
        assertTrue(c.config.acceptingOrders)
        c.draft = completedDraft().copy(notes = "Conservar este draft")
        val beforeDraft = c.draft

        c.updateConfig(c.config.copy(acceptingOrders = false, closedMessage = "Recepción pausada durante Review"))
        val result = c.createOrder()

        assertTrue(result is OrderCreationResult.Blocked)
        assertEquals("Recepción pausada durante Review", (result as OrderCreationResult.Blocked).message)
        assertTrue(c.orders.isEmpty())
        assertTrue(c.payments.isEmpty())
        assertEquals(beforeDraft, c.draft)
    }

    @Test
    fun `REG-ORDER-RECEPTION-003 pedidos existentes siguen operables con recepcion pausada`() {
        LocalStore(context).saveCustomer(customer)
        val c = MandadosController(context)
        c.draft = completedDraft()
        val created = c.createOrder() as OrderCreationResult.Created
        val orderId = created.order.id

        c.updateConfig(c.config.copy(acceptingOrders = false))

        assertTrue(c.cancelOrderByCustomer(orderId))
        assertEquals(OrderStatus.CANCELLED, c.order(orderId)!!.status)
        assertTrue(c.paymentForOrder(orderId) != null)
    }

    @Test
    fun `REG-ORDER-RECEPTION-004 reactivar permite crear nuevamente`() {
        LocalStore(context).saveCustomer(customer)
        val c = MandadosController(context)
        c.draft = completedDraft()
        c.updateConfig(c.config.copy(acceptingOrders = false))

        assertTrue(c.createOrder() is OrderCreationResult.Blocked)
        assertTrue(c.orders.isEmpty())
        assertTrue(c.payments.isEmpty())

        c.updateConfig(c.config.copy(acceptingOrders = true))
        val result = c.createOrder()

        assertTrue(result is OrderCreationResult.Created)
        assertEquals(1, c.orders.size)
        assertEquals(1, c.payments.size)
    }

    @Test
    fun `REG-ORDER-RECEPTION-005 mensaje personalizado persiste y toggles no lo pisan`() {
        LocalStore(context).saveCustomer(customer)
        val c = MandadosController(context)
        val custom = "Mensaje personalizado de pausa"
        c.updateConfig(c.config.copy(acceptingOrders = false, closedMessage = custom))

        val blocked = c.createOrder() as OrderCreationResult.Blocked
        assertEquals(custom, blocked.message)

        c.updateConfig(c.config.copy(acceptingOrders = true))
        assertEquals(custom, c.config.closedMessage)
        c.updateConfig(c.config.copy(acceptingOrders = false))
        assertEquals(custom, c.config.closedMessage)

        val restarted = MandadosController(context)
        assertTrue(!restarted.config.acceptingOrders)
        assertEquals(custom, restarted.config.closedMessage)
    }

    @Test
    fun `REG-ORDER-RECEPTION-UI-001 Admin y Review exponen estado actual sin depender solo del color`() {
        val source = projectSource("src/main/java/ar/com/mandados/app/MandadosApp.kt")
        val review = source
            .substringAfter("private fun ReviewScreen(")
            .substringBefore("private fun MandadosController.pricingTextForUi")
        val admin = source
            .substringAfter("private fun AdminScreen(")
            .substringBefore("private fun ZoneAdminDialog(")
        val home = source
            .substringAfter("private fun HomeScreen(")
            .substringBefore("private fun ServiceCard(")

        assertTrue(admin.contains("RECEPCIÓN DE NUEVAS SOLICITUDES"))
        assertTrue(admin.contains("✓ HABILITADA"))
        assertTrue(admin.contains("⛔ PAUSADA"))
        assertTrue(review.contains("AssistBox(c.config.closedMessage)"))
        assertTrue(review.contains("enabled = legalAccepted && c.config.acceptingOrders"))
        assertTrue(home.contains("\"Compras\", \"Compramos en el comercio que necesites\", c.config.acceptingOrders"))
        assertTrue(home.contains("\"Encargos\", \"Retiramos y llevamos lo que necesites\", c.config.acceptingOrders"))
        assertTrue(home.contains("\"Trámites\", \"Gestionamos documentación y diligencias\", c.config.acceptingOrders"))
        assertTrue(home.contains("\"Envíos\", \"Paquetes, sobres y objetos de un punto a otro\", c.config.acceptingOrders"))
    }

    private fun completedDraft(): OrderDraft = OrderDraft(
        serviceType = ServiceType.DELIVERY,
        category = ServiceCategory.SHIPMENT,
        originAddress = "Calle 1",
        originZoneId = "urbana",
        destinationAddress = "Calle 2",
        destinationZoneId = "urbana",
        carriedItem = "Paquete de prueba",
        deliveryPayment = DeliveryPaymentMethod.CASH,
        notes = "Fixture recepción"
    )

    private fun existingOrder(id: String): LocalOrder = LocalOrder(
        id = id,
        createdAt = "07/10/2026 03:00:00",
        serviceType = ServiceType.DELIVERY,
        category = ServiceCategory.SHIPMENT,
        operationMode = OperationMode.MULTI_RIDER,
        status = OrderStatus.PENDING,
        customerName = customer.name,
        customerPhone = customer.displayPhone,
        customerId = customer.id,
        detail = "Pedido preexistente",
        baseAmount = 1000,
        baseZoneName = "Urbana",
        prePickupAmount = 0,
        rainAmount = 0,
        totalAmount = 1000,
        whatsappMessage = "",
        events = listOf(
            OrderEvent(
                type = OrderEventType.CREATED,
                at = "07/10/2026 03:00:00",
                status = OrderStatus.PENDING,
                actor = "CLIENTE"
            )
        )
    )

    private fun projectSource(relativePath: String): String {
        val candidates = listOf(
            File(relativePath),
            File("app/$relativePath"),
            File("../app/$relativePath")
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: error("No se encontró source de regresión: $relativePath")
        return file.readText()
    }
}
