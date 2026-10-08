package ar.com.mandados.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportRolePolicyTest {

    @Test
    fun `REG-SUPPORT-CUSTOMER-001 customer receives only customer support reasons`() {
        val reasons = supportReasonsFor(SupportAudience.CUSTOMER)

        assertEquals(
            listOf(
                "Pedido",
                "Pago / cobro",
                "Cuenta / perfil",
                "Problemas con la app",
                "Otro"
            ),
            reasons
        )
        assertFalse("Turnos" in reasons)
        assertFalse("Balance / pagos" in reasons)
        assertFalse("Documentación" in reasons)
    }

    @Test
    fun `REG-SUPPORT-RIDER-001 rider receives exact rider support reasons`() {
        assertEquals(
            listOf(
                "Pedido / entrega",
                "Turnos",
                "Balance / pagos",
                "Documentación",
                "Problemas con la app",
                "Otro"
            ),
            supportReasonsFor(SupportAudience.RIDER)
        )
    }

    @Test
    fun `REG-SUPPORT-WIRING-001 entry points use explicit support audiences`() {
        val operations = projectSource("app/src/main/java/ar/com/mandados/app/OperationsScreens.kt")
        val riderRoute = operations
            .substringAfter("internal fun RiderDashboardScreen(")
            .substringBefore("private fun RiderHome(")
        val customerRoute = operations
            .substringAfter("internal fun CustomerSupportScreen(")
            .substringBefore("internal fun Punto25RatingDialog(")
        val support = operations
            .substringAfter("private fun SupportScreen(")
            .substringBefore("private fun PaymentSummary(")

        assertTrue(riderRoute.contains("RiderSection.SUPPORT -> SupportScreen(c, SupportAudience.RIDER)"))
        assertTrue(customerRoute.contains("SupportScreen(c, SupportAudience.CUSTOMER)"))
        assertTrue(support.contains("val reasons = supportReasonsFor(audience)"))
    }

    private fun projectSource(repoRelativePath: String): String {
        val candidates = listOf(
            File(repoRelativePath),
            File("../$repoRelativePath"),
            File("../../$repoRelativePath")
        )
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("No se encontró source de regresión: $repoRelativePath")
    }
}
