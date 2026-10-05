package ar.com.mandados.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun RiderAdminReadOnlyScreen(
    c: MandadosController,
    riderId: String?,
    onBack: () -> Unit
) {
    val rider = c.rider(riderId)
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text("Repartidor · Vista administrativa", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Sólo lectura · actor ADMIN", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(10.dp))
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            if (rider == null) {
                ReadOnlyCard("Repartidor no encontrado.")
            } else {
                Text(rider.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(rider.id)
                Text("Estado: ${rider.approvalStatus.name}")
                Text("Cuenta: ${if (rider.active) "Activa" else "Desactivada"}")
                Text("Vehículo: ${rider.vehicleType.name}")
                if (rider.phone.isNotBlank()) Text("Teléfono: ${rider.phone}")
                if (rider.address.isNotBlank()) Text("Domicilio: ${rider.address}")
                if (rider.transferAlias.isNotBlank()) Text("Alias: ${rider.transferAlias}")

                Spacer(Modifier.height(12.dp))
                Text("Documentación", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                requiredRiderDocumentKeys(rider).forEach { key ->
                    Text("${adminDocumentLabel(key)}: ${rider.reviewFor(key).name}")
                }

                Spacer(Modifier.height(12.dp))
                Text("Pedidos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                val ownOrders = c.orders.filter { it.assignedRiderId == rider.id }
                Text("Completados: ${ownOrders.count { it.status == OrderStatus.COMPLETED }}")
                Text("Activos: ${ownOrders.count { it.status in setOf(OrderStatus.PENDING, OrderStatus.ACCEPTED, OrderStatus.IN_PROGRESS) }}")
                ownOrders.take(20).forEach { order ->
                    ReadOnlyCard("${order.id} · ${order.status.name} · ${order.createdAt}")
                }

                Spacer(Modifier.height(12.dp))
                ReadOnlyCard(
                    "Esta vista no crea una sesión Rider y no permite reservar turnos, cambiar Disponibilidad, tomar pedidos, confirmar pagos/propinas ni modificar datos como si fuera el Repartidor."
                )
            }
        }
        HorizontalDivider()
        TextButton(onClick = onBack) { Text("← VOLVER") }
    }
}

@Composable
private fun ReadOnlyCard(text: String) {
    Card(Modifier.fillMaxWidth().padding(top = 7.dp)) {
        Text(text, Modifier.padding(12.dp))
    }
}

private fun adminDocumentLabel(key: RiderDocumentKey): String = when (key) {
    RiderDocumentKey.DNI_FRONT -> "DNI anverso"
    RiderDocumentKey.DNI_BACK -> "DNI reverso"
    RiderDocumentKey.MOTORCYCLE_PLATE -> "Patente"
    RiderDocumentKey.DRIVER_LICENSE_FRONT -> "Licencia anverso"
    RiderDocumentKey.DRIVER_LICENSE_BACK -> "Licencia reverso"
    RiderDocumentKey.VEHICLE_CARD -> "Tarjeta del vehículo"
    RiderDocumentKey.INSURANCE_CARD -> "Seguro"
}
