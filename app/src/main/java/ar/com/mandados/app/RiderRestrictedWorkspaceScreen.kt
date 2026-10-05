package ar.com.mandados.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun RiderRestrictedWorkspaceScreen(
    c: MandadosController,
    riderId: String,
    onBack: () -> Unit
) {
    val rider = c.rider(riderId)
    val eligibility = c.riderOperationalEligibility(riderId)
    val continuation = c.riderExistingOrderContinuationDecision(riderId)
    var actionMessage by rememberSaveable(riderId) { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "Punto25 · Repartidor",
                Modifier.padding(14.dp),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(10.dp))
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            if (rider == null || !c.hasAuthenticatedRiderSession(riderId)) {
                RestrictedAssist("La sesión de Repartidor dejó de ser válida.")
                return@Column
            }

            Text("Hola, ${rider.name.substringBefore(" ")}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("${rider.id} · ${if (rider.vehicleType == VehicleType.MOTORCYCLE) "Moto" else "Bicicleta"}")
            Spacer(Modifier.height(8.dp))
            RestrictedAssist(riderDenialMessage(eligibility))

            Spacer(Modifier.height(12.dp))
            Text("Mi información", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (rider.phone.isNotBlank()) Text("Teléfono: ${rider.phone}")
            if (rider.address.isNotBlank()) Text("Domicilio: ${rider.address}")
            if (rider.transferAlias.isNotBlank()) Text("Alias: ${rider.transferAlias}")
            Text("Estado: ${restrictedApprovalText(rider.approvalStatus)}")

            Spacer(Modifier.height(12.dp))
            Text("Documentación", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            requiredRiderDocumentKeys(rider).forEach { key ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text(riderDocumentLabel(key), Modifier.weight(1f))
                    Text(restrictedDocumentStatus(rider.reviewFor(key)))
                }
            }

            val activeOrders = authenticatedRiderActiveOrders(c, rider.id)
            Spacer(Modifier.height(12.dp))
            Text("Pedido ya asignado", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (activeOrders.isEmpty()) {
                RestrictedAssist("No tenés pedidos activos asignados.")
            } else {
                activeOrders.forEach { order ->
                    Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(order.id, fontWeight = FontWeight.Bold)
                            Text(restrictedOrderStatus(order.status))
                            Text(order.detail, style = MaterialTheme.typography.bodySmall)
                            if (continuation.allowed) {
                                when (order.status) {
                                    OrderStatus.PENDING, OrderStatus.ACCEPTED -> Button(
                                        onClick = {
                                            actionMessage = if (c.updateOrderStatus(order.id, OrderStatus.IN_PROGRESS, actor = rider.id)) {
                                                "Pedido actualizado a En curso."
                                            } else {
                                                "No se pudo actualizar el pedido. Verificá que siga asignado a tu usuario."
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                                    ) { Text("INICIAR / EN CURSO") }
                                    OrderStatus.IN_PROGRESS -> Button(
                                        onClick = {
                                            actionMessage = if (c.updateOrderStatus(order.id, OrderStatus.COMPLETED, actor = rider.id)) {
                                                "Pedido marcado como entregado."
                                            } else {
                                                "No se pudo finalizar el pedido. Verificá que siga asignado a tu usuario."
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                                    ) { Text("MARCAR ENTREGADO") }
                                    else -> Unit
                                }
                            } else {
                                RestrictedAssist(riderDenialMessage(continuation))
                            }
                        }
                    }
                }
            }
            if (actionMessage.isNotBlank()) {
                Text(actionMessage, modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.primary)
            }

            val completed = c.riderCompletedOrders(rider.id)
            val history = c.riderHistoryOrders(rider.id)
            Spacer(Modifier.height(12.dp))
            Text("Historial", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("${completed.size} entrega(s)", fontWeight = FontWeight.SemiBold)
            history.sortedByDescending { c.parseTimestamp(it.createdAt) }.take(20).forEach { order ->
                Card(Modifier.fillMaxWidth().padding(top = 7.dp)) {
                    Column(Modifier.padding(10.dp)) {
                        Text(order.id, fontWeight = FontWeight.Bold)
                        Text(restrictedOrderStatus(order.status))
                        Text(order.createdAt, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Text("Balance actual", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(restrictedMoney(c.riderCurrentBalance(rider.id)), style = MaterialTheme.typography.titleLarge)
            RestrictedAssist("Mientras tu usuario no sea elegible para nuevo trabajo, Turnos, Disponibilidad y Pedidos nuevos permanecen bloqueados.")
        }

        HorizontalDivider(Modifier.padding(top = 8.dp))
        TextButton(onClick = onBack, modifier = Modifier.align(Alignment.Start)) {
            Text("← VOLVER", fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun RestrictedAssist(text: String) {
    if (text.isBlank()) return
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
    ) {
        Text(text, Modifier.padding(12.dp))
    }
}

private fun restrictedApprovalText(status: RiderApprovalStatus): String = when (status) {
    RiderApprovalStatus.PENDING -> "Pendiente"
    RiderApprovalStatus.APPROVED -> "Aprobado"
    RiderApprovalStatus.SUSPENDED -> "Suspendido"
}

private fun restrictedDocumentStatus(status: DocumentReviewStatus): String = when (status) {
    DocumentReviewStatus.NOT_UPLOADED -> "Sin cargar"
    DocumentReviewStatus.PENDING -> "Pendiente"
    DocumentReviewStatus.APPROVED -> "Aprobado"
    DocumentReviewStatus.REJECTED -> "Rechazado"
}

private fun restrictedOrderStatus(status: OrderStatus): String = when (status) {
    OrderStatus.AWAITING_QUOTE -> "A confirmar"
    OrderStatus.PENDING -> "Nuevo"
    OrderStatus.ACCEPTED -> "Aceptado"
    OrderStatus.IN_PROGRESS -> "En curso"
    OrderStatus.COMPLETED -> "Completado"
    OrderStatus.REJECTED -> "Rechazado · no cuenta como entrega"
    OrderStatus.CANCELLED -> "Cancelado · no cuenta como entrega"
}

private fun restrictedMoney(value: Int): String = "$" + "%,d".format(value).replace(',', '.')
