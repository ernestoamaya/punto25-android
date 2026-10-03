package ar.com.mandados.app

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private val fullTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
private val oldTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

private enum class RiderSection {
    MENU, SHIFTS, NEW_ORDERS, ACTIVE_ORDERS, TRANSFERS, HISTORY, BALANCE, BALANCES, PENDING_TIPS, PROFILE, PERMISSIONS, SUPPORT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AdminOrdersHubScreen(
    c: MandadosController,
    onBack: () -> Unit,
    onOrder: (String) -> Unit
) {
    var codeFilter by rememberSaveable { mutableStateOf("") }
    var riderFilter by rememberSaveable { mutableStateOf("") }
    var customerFilter by rememberSaveable { mutableStateOf("") }
    var statusFilter by rememberSaveable { mutableStateOf<OrderStatus?>(null) }
    var categoryFilter by rememberSaveable { mutableStateOf<ServiceCategory?>(null) }
    var fromDate by rememberSaveable { mutableStateOf<String?>(null) }
    var toDate by rememberSaveable { mutableStateOf<String?>(null) }

    val filtered = c.orders.filter { order ->
        val rider = c.rider(order.assignedRiderId)
        val created = parseOrderTime(order.createdAt)?.toLocalDate()
        val from = fromDate?.let(::parseDate)
        val to = toDate?.let(::parseDate)
        val matchesCode = codeFilter.isBlank() || order.id.contains(codeFilter.trim(), true)
        val matchesRider = riderFilter.isBlank() ||
            order.assignedRiderId.orEmpty().contains(riderFilter.trim(), true) ||
            rider?.name.orEmpty().contains(riderFilter.trim(), true)
        val matchesCustomer = customerFilter.isBlank() ||
            order.customerId.contains(customerFilter.trim(), true) ||
            order.customerName.contains(customerFilter.trim(), true) ||
            order.customerPhone.contains(customerFilter.trim(), true)
        val matchesStatus = statusFilter == null || order.status == statusFilter
        val matchesCategory = categoryFilter == null || order.category == categoryFilter
        val matchesFrom = from == null || (created != null && !created.isBefore(from))
        val matchesTo = to == null || (created != null && !created.isAfter(to))
        matchesCode && matchesRider && matchesCustomer && matchesStatus && matchesCategory && matchesFrom && matchesTo
    }.sortedByDescending { parseOrderTime(it.createdAt) ?: LocalDateTime.MIN }

    OpsPage("Pedidos · Administración", onBack) {
        Text("Filtros", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        SimpleField("Código de pedido", codeFilter) { codeFilter = it }
        SimpleField("RID o nombre del Repartidor", riderFilter) { riderFilter = it }
        SimpleField("ID, nombre o teléfono del cliente", customerFilter) { customerFilter = it }

        EnumDropdown(
            label = "Estado",
            value = statusFilter,
            options = OrderStatus.entries,
            allLabel = "Todos",
            labelFor = ::opsStatusText,
            onSelect = { statusFilter = it }
        )
        EnumDropdown(
            label = "Tipo de servicio",
            value = categoryFilter,
            options = ServiceCategory.entries,
            allLabel = "Todos",
            labelFor = ::categoryText,
            onSelect = { categoryFilter = it }
        )

        DateRangePicker(
            from = fromDate,
            to = toDate,
            onFrom = { fromDate = it },
            onTo = { toDate = it }
        )

        TextButton(onClick = {
            codeFilter = ""
            riderFilter = ""
            customerFilter = ""
            statusFilter = null
            categoryFilter = null
            fromDate = null
            toDate = null
        }) { Text("LIMPIAR FILTROS") }

        Text("${filtered.size} pedido(s)", style = MaterialTheme.typography.bodySmall)
        if (filtered.isEmpty()) {
            AssistCard("No hay pedidos que coincidan con los filtros.")
        } else {
            filtered.forEach { order -> AdminOrderCard(c, order, onOrder) }
        }
    }
}

@Composable
private fun AdminOrderCard(c: MandadosController, order: LocalOrder, onOrder: (String) -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(top = 9.dp).clickable { onOrder(order.id) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(order.id, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                StatusPill(order.status)
            }
            Text(order.createdAt, style = MaterialTheme.typography.bodySmall)
            Text("${categoryText(order.category)} · ${order.customerName}")
            Text(order.customerId.ifBlank { "Cliente legado" }, style = MaterialTheme.typography.bodySmall)
            Text(c.rider(order.assignedRiderId)?.let { "Repartidor: ${it.name} · ${it.id}" } ?: "Sin Repartidor")
            Text("Total servicio: ${money(order.totalAmount)}", fontWeight = FontWeight.SemiBold)
            c.deliveryDurationSeconds(order)?.let {
                Text("Tiempo de entrega: ${formatDuration(it)}", color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
internal fun AdminOrderDetailV2Screen(c: MandadosController, id: String?, onBack: () -> Unit) {
    val order = c.order(id)
    var assignOpen by rememberSaveable { mutableStateOf(false) }
    var editOpen by rememberSaveable { mutableStateOf(false) }

    OpsPage("Gestionar pedido", onBack) {
        if (order == null) {
            AssistCard("Pedido no encontrado.")
            return@OpsPage
        }

        Text(order.id, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(order.createdAt)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusPill(order.status)
            CategoryPill(order.category)
        }

        Spacer(Modifier.height(12.dp))
        SectionTitle("Cliente")
        Text("${order.customerName} · ${order.customerPhone}")
        Text(order.customerId.ifBlank { "ID cliente legado" }, style = MaterialTheme.typography.bodySmall)

        Spacer(Modifier.height(12.dp))
        SectionTitle("Repartidor")
        val assigned = c.rider(order.assignedRiderId)
        Text(assigned?.let {
            "${it.name} · ${it.id} · ${c.riderActiveCount(it.id)}/${c.effectiveRiderLimit(it)} activos"
        } ?: order.assignedRiderId ?: "Sin Repartidor asignado")
        if (c.canAssignRider(order)) {
            OutlinedButton(onClick = { assignOpen = true }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Text("ASIGNAR / CAMBIAR REPARTIDOR")
            }
        }

        if (c.canEditOrder(order)) {
            OutlinedButton(onClick = { editOpen = true }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Text("EDITAR DATOS DEL PEDIDO")
            }
        } else if (order.operationMode == OperationMode.SIMPLE_WHATSAPP) {
            AssistCard("Los pedidos del Modo Simple conservan el detalle enviado por WhatsApp y no se editan dentro del flujo Multi-Repartidor.")
        } else {
            AssistCard("Los pedidos cancelados o completados quedan bloqueados para preservar la auditoría. Cualquier corrección económica posterior debe registrarse como ajuste/conciliación independiente.")
        }

        Spacer(Modifier.height(14.dp))
        SectionTitle("Detalle")
        Card(Modifier.fillMaxWidth()) { Text(order.detail, Modifier.padding(12.dp)) }

        c.deliveryDurationSeconds(order)?.let {
            Spacer(Modifier.height(10.dp))
            ReportMetric("Tiempo de entrega", formatDuration(it), bold = true)
        }

        Spacer(Modifier.height(14.dp))
        SectionTitle("Estado operativo")
        if (order.operationMode == OperationMode.SIMPLE_WHATSAPP) {
            AssistCard("Pedido creado en Modo Simple. La gestión operativa se realiza por WhatsApp y este registro no utiliza estados internos, asignación ni seguimiento Multi-Repartidor.")
        } else {
            when (order.status) {
                OrderStatus.AWAITING_QUOTE -> {
                    ActionButton("RECHAZAR") { c.updateOrderStatus(order.id, OrderStatus.REJECTED, actor = "ADMIN") }
                    ActionButton("CANCELAR", true) { c.updateOrderStatus(order.id, OrderStatus.CANCELLED, actor = "ADMIN") }
                }
                OrderStatus.PENDING -> {
                    ActionButton("ACEPTAR PEDIDO") { c.updateOrderStatus(order.id, OrderStatus.ACCEPTED, actor = "ADMIN") }
                    ActionButton("RECHAZAR", true) { c.updateOrderStatus(order.id, OrderStatus.REJECTED, actor = "ADMIN") }
                    ActionButton("CANCELAR", true) { c.updateOrderStatus(order.id, OrderStatus.CANCELLED, actor = "ADMIN") }
                }
                OrderStatus.ACCEPTED -> {
                    ActionButton("INICIAR / EN CURSO") { c.updateOrderStatus(order.id, OrderStatus.IN_PROGRESS, actor = "ADMIN") }
                    ActionButton("CANCELAR", true) { c.updateOrderStatus(order.id, OrderStatus.CANCELLED, actor = "ADMIN") }
                }
                OrderStatus.IN_PROGRESS -> {
                    ActionButton("MARCAR ENTREGADO") { c.updateOrderStatus(order.id, OrderStatus.COMPLETED, actor = "ADMIN") }
                    ActionButton("CANCELAR", true) { c.updateOrderStatus(order.id, OrderStatus.CANCELLED, actor = "ADMIN") }
                }
                OrderStatus.COMPLETED, OrderStatus.REJECTED, OrderStatus.CANCELLED -> Unit
            }
        }

        Spacer(Modifier.height(14.dp))
        SectionTitle("Importes")
        MoneyBreakdown(order)
        PaymentSummary(c, order)

        Spacer(Modifier.height(14.dp))
        SectionTitle("Historial")
        OrderTimeline(c, order)
    }

    if (assignOpen && order != null && c.canAssignRider(order)) {
        AlertDialog(
            onDismissRequest = { assignOpen = false },
            title = { Text("Asignar Repartidor") },
            text = {
                Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                    RadioLine("Sin asignar", order.assignedRiderId == null) {
                        c.assignRider(order.id, null); assignOpen = false
                    }
                    c.riders.filter {
                        it.active && it.approvalStatus == RiderApprovalStatus.APPROVED &&
                            c.riderHasActiveShiftNow(it.id)
                    }.forEach { rider ->
                        val load = c.riderActiveCount(rider.id)
                        val limit = c.effectiveRiderLimit(rider)
                        RadioLine(
                            "${rider.name} · ${rider.id} · $load/$limit${if (rider.available) " · Disponible" else ""}",
                            order.assignedRiderId == rider.id
                        ) {
                            if (c.assignRider(order.id, rider.id)) assignOpen = false
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { assignOpen = false }) { Text("CERRAR") } }
        )
    }

    if (editOpen && order != null) {
        OrderEditDialog(c, order, onDismiss = { editOpen = false }) { edited, reason ->
            if (c.editOrder(order.id, edited, reason)) editOpen = false
        }
    }
}

@Composable
private fun OrderEditDialog(
    c: MandadosController,
    order: LocalOrder,
    onDismiss: () -> Unit,
    onSave: (OrderDraft, String) -> Unit
) {
    var edited by remember(order.id) { mutableStateOf(c.draftFromOrder(order)) }
    var reason by rememberSaveable(order.id) { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar pedido · ${order.id}") },
        text = {
            Column(Modifier.heightIn(max = 650.dp).verticalScroll(rememberScrollState())) {
                if (order.originAddress.isBlank() && order.destinationAddress.isBlank() && order.events.isEmpty()) {
                    AssistCard("Pedido legado: algunos campos estructurados no existían en la versión en que fue creado. No se completan datos automáticamente.")
                }

                if (edited.serviceType == ServiceType.DELIVERY) {
                    SimpleField("Dirección de retiro", edited.originAddress) { edited = edited.copy(originAddress = it) }
                    SimpleField("Referencia de retiro", edited.originReference) { edited = edited.copy(originReference = it) }
                    ZoneDropdown(c, "Zona de retiro", edited.originZoneId) { edited = edited.copy(originZoneId = it) }
                } else {
                    SimpleField("Comercio", edited.storeName) { edited = edited.copy(storeName = it) }
                    SimpleField("Dirección del comercio", edited.storeAddress) { edited = edited.copy(storeAddress = it) }
                    ZoneDropdown(c, "Zona del comercio", edited.storeZoneId, true) { edited = edited.copy(storeZoneId = it) }
                    SimpleField("Detalle del pedido", edited.purchaseDescription) { edited = edited.copy(purchaseDescription = it) }
                    if (edited.requiresPrePickup()) {
                        SimpleField("Dirección retiro previo", edited.prePickupAddress) { edited = edited.copy(prePickupAddress = it) }
                        ZoneDropdown(c, "Zona retiro previo", edited.prePickupZoneId) { edited = edited.copy(prePickupZoneId = it) }
                    }
                }

                SimpleField("Dirección de entrega", edited.destinationAddress) { edited = edited.copy(destinationAddress = it) }
                SimpleField("Referencia de entrega", edited.destinationReference) { edited = edited.copy(destinationReference = it) }
                ZoneDropdown(c, "Zona de entrega", edited.destinationZoneId) { edited = edited.copy(destinationZoneId = it) }
                SimpleField("Aclaraciones", edited.notes) { edited = edited.copy(notes = it) }
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Motivo de la modificación *") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                Text(
                    "Al guardar se recalculan tarifa base y adicionales con la configuración vigente y el cambio queda en el historial.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(edited, reason) }, enabled = reason.trim().isNotBlank()) { Text("GUARDAR") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCELAR") } }
    )
}

@Composable
internal fun AdminReportsScreen(c: MandadosController, onBack: () -> Unit) {
    val context = LocalContext.current
    var fromDate by rememberSaveable { mutableStateOf(defaultReportFrom()) }
    var toDate by rememberSaveable { mutableStateOf(defaultReportTo()) }

    val periodOrders = remember(c.orders, fromDate, toDate) {
        filterOrdersForPeriod(c.orders, fromDate, toDate)
    }
    val completed = periodOrders.filter { it.status == OrderStatus.COMPLETED }
    val avg = c.averageDeliverySeconds(completed)
    val base = completed.sumOf { it.baseAmount ?: 0 }
    val pre = completed.sumOf { it.prePickupAmount ?: 0 }
    val rain = completed.sumOf { it.rainAmount ?: 0 }
    val total = completed.sumOf { it.totalAmount ?: 0 }

    OpsPage("Reportes · Administración", onBack) {
        SectionTitle("Período")
        DateRangePicker(fromDate, toDate, { if (it != null) fromDate = it }, { if (it != null) toDate = it })

        Button(
            onClick = {
                PdfReports.shareOrders(
                    context,
                    "Reporte_Admin_Punto25",
                    fromDate,
                    toDate,
                    periodOrders,
                    c,
                    extraSummary = listOf(
                        "Pedidos totales" to periodOrders.size.toString(),
                        "Completados" to completed.size.toString(),
                        "Promedio de entrega" to (avg?.let(::formatDuration) ?: "Sin datos"),
                        "Total servicio completado" to money(total)
                    )
                )
            },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) { Text("EXPORTAR PDF") }

        SectionTitle("Resumen general")
        ReportMetric("Pedidos totales", periodOrders.size.toString())
        ReportMetric("Completados", completed.size.toString())
        ReportMetric("Cancelados", periodOrders.count { it.status == OrderStatus.CANCELLED }.toString())
        ReportMetric("Rechazados", periodOrders.count { it.status == OrderStatus.REJECTED }.toString())
        ReportMetric("Promedio de entrega", avg?.let(::formatDuration) ?: "Sin datos", bold = true)

        Spacer(Modifier.height(12.dp))
        SectionTitle("Importes completados")
        ReportMetric("Tarifa base", money(base))
        ReportMetric("Retiros previos", money(pre))
        ReportMetric("Lluvia / barro", money(rain))
        ReportMetric("Total servicio", money(total), true)

        Spacer(Modifier.height(14.dp))
        SectionTitle("Por Repartidor")
        c.riders.forEach { rider ->
            val own = completed.filter { it.assignedRiderId == rider.id }
            if (own.isNotEmpty()) {
                Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("${rider.name} · ${rider.id}", fontWeight = FontWeight.Bold)
                        Text("${own.size} entregado(s)")
                        Text("Promedio: ${c.averageDeliverySeconds(own)?.let(::formatDuration) ?: "Sin datos"}")
                        Text("Total: ${money(own.sumOf { it.totalAmount ?: 0 })}")
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        SectionTitle("Pedidos del período")
        periodOrders.sortedByDescending { parseOrderTime(it.createdAt) ?: LocalDateTime.MIN }.forEach { order ->
            Card(Modifier.fillMaxWidth().padding(top = 7.dp)) {
                Column(Modifier.padding(10.dp)) {
                    Text(order.id, fontWeight = FontWeight.Bold)
                    Text(order.createdAt)
                    Text("${categoryText(order.category)} · ${opsStatusText(order.status)}")
                    Text(c.rider(order.assignedRiderId)?.let { "Repartidor: ${it.name}" } ?: "Sin Repartidor")
                    c.deliveryDurationSeconds(order)?.let { Text("Tiempo: ${formatDuration(it)}") }
                    Text("Total: ${money(order.totalAmount)}")
                }
            }
        }
    }
}

@Composable
internal fun RidersAdminScreenV2(c: MandadosController, onBack: () -> Unit, onWorkspace: (String) -> Unit) {
    val context = LocalContext.current
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var editOpen by rememberSaveable { mutableStateOf(false) }
    var docsId by rememberSaveable { mutableStateOf<String?>(null) }
    var accessRiderId by rememberSaveable { mutableStateOf<String?>(null) }

    OpsPage("Gestión de Repartidores", onBack) {
        Button(onClick = { editingId = null; editOpen = true }, modifier = Modifier.fillMaxWidth()) {
            Text("+ ALTA DE REPARTIDOR")
        }

        c.riders.forEach { rider ->
            val load = c.riderActiveCount(rider.id)
            val limit = c.effectiveRiderLimit(rider)
            Card(Modifier.fillMaxWidth().padding(top = 10.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(rider.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(rider.id)
                        }
                        RiderApprovalPill(rider.approvalStatus)
                    }
                    Text("Carga: $load/$limit pedidos activos", color = if (load >= limit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    if (rider.transferAlias.isNotBlank()) Text("Alias: ${rider.transferAlias}")
                    Text("Vehículo: ${if (rider.vehicleType == VehicleType.MOTORCYCLE) "Moto" else "Bicicleta"}")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Activo", Modifier.weight(1f)); Switch(rider.active, { c.setRiderActive(rider.id, it) })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Presencia", Modifier.weight(1f))
                        Text(
                            if (c.isRiderCurrentlyAvailable(rider.id)) "Disponible" else "No disponible",
                            color = if (c.isRiderCurrentlyAvailable(rider.id)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        if (c.riderHasActiveShiftNow(rider.id)) "Turno vigente" else "Sin turno vigente",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { editingId = rider.id; editOpen = true }, modifier = Modifier.weight(1f)) { Text("EDITAR") }
                        OutlinedButton(onClick = { docsId = rider.id }, modifier = Modifier.weight(1f)) { Text("DOCUMENTOS") }
                    }
                    OutlinedButton(
                        onClick = { accessRiderId = rider.id },
                        enabled = rider.phone.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                    ) {
                        Text(if (c.hasRiderCredential(rider.id)) "RESETEAR ACCESO Y ENVIAR CÓDIGO" else "ENVIAR INVITACIÓN POR WHATSAPP")
                    }
                    Text(
                        "Administración sólo gestiona códigos de invitación/reset. La contraseña nunca se muestra ni se almacena en texto plano.",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Button(
                        onClick = { onWorkspace(rider.id) },
                        enabled = rider.active && rider.approvalStatus == RiderApprovalStatus.APPROVED,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                    ) { Text("ABRIR PANEL REPARTIDOR") }
                }
            }
        }
    }

    accessRiderId?.let { riderId ->
        c.rider(riderId)?.let { rider ->
            val reset = c.hasRiderCredential(rider.id)
            AlertDialog(
                onDismissRequest = { accessRiderId = null },
                title = { Text(if (reset) "Resetear acceso" else "Enviar invitación") },
                text = {
                    Text(
                        if (reset)
                            "Se invalidará la contraseña actual y se generará un nuevo código de un solo uso. El Repartidor no podrá volver a ingresar hasta crear una contraseña nueva."
                        else
                            "Se generará un código de un solo uso, válido por 7 días, y se abrirá WhatsApp para enviárselo al Repartidor."
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val invitation = c.createRiderInvitation(rider.id, resetAccess = reset)
                        if (invitation != null) {
                            val verb = if (reset) "restablecer" else "crear"
                            val message = "Hola " + rider.name.substringBefore(" ") +
                                ". Te enviamos tu código de Punto25 para " + verb +
                                " el acceso de Repartidor: " + invitation.code +
                                ". Vence en 7 días. Abrí Punto25, elegí Soy Repartidor y seguí los pasos. No compartas tu contraseña con nadie."
                            openWhatsAppTo(context, rider.phone, message)
                        } else {
                            Toast.makeText(context, "Configurá primero el WhatsApp del Repartidor.", Toast.LENGTH_LONG).show()
                        }
                        accessRiderId = null
                    }) { Text(if (reset) "RESETEAR Y ENVIAR" else "GENERAR Y ENVIAR") }
                },
                dismissButton = { TextButton(onClick = { accessRiderId = null }) { Text("CANCELAR") } }
            )
        }
    }

    if (editOpen) {
        RiderEditDialog(
            c = c,
            rider = c.rider(editingId),
            onDismiss = { editOpen = false },
            onSave = { name, phone, birth, vehicle, address, docs, limit, approval ->
                val savedId = c.saveRider(editingId, name, phone, birth, vehicle, address, docs, limit)
                savedId?.let { c.setRiderApprovalStatus(it, approval) }
                editOpen = false
            }
        )
    }

    c.rider(docsId)?.let { rider ->
        RiderDocumentsDialog(c, rider) { docsId = null }
    }
}

@Composable
private fun RiderEditDialog(
    c: MandadosController,
    rider: RiderProfile?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, VehicleType, String, RiderDocuments, Int?, RiderApprovalStatus) -> Unit
) {
    val context = LocalContext.current
    var name by remember(rider?.id) { mutableStateOf(rider?.name ?: "") }
    var phone by remember(rider?.id) { mutableStateOf(rider?.phone ?: "") }
    var birth by remember(rider?.id) { mutableStateOf(rider?.birthDate ?: "") }
    var vehicle by remember(rider?.id) { mutableStateOf(rider?.vehicleType ?: VehicleType.MOTORCYCLE) }
    var address by remember(rider?.id) { mutableStateOf(rider?.address ?: "") }
    var limitText by remember(rider?.id) { mutableStateOf(rider?.maxConcurrentOrdersOverride?.toString() ?: "") }
    var approval by remember(rider?.id) { mutableStateOf(rider?.approvalStatus ?: RiderApprovalStatus.PENDING) }
    var docs by remember(rider?.id) { mutableStateOf(rider?.documents ?: RiderDocuments()) }
    var pendingKey by remember { mutableStateOf<RiderDocumentKey?>(null) }

    val picker = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        val key = pendingKey
        if (uri != null && key != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            docs = docs.withUri(key, uri.toString())
        }
        pendingKey = null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (rider == null) "Alta de Repartidor" else "Editar Repartidor") },
        text = {
            Column(Modifier.heightIn(max = 650.dp).verticalScroll(rememberScrollState())) {
                SimpleField("Nombre y apellido completos *", name) { name = it }
                SimpleField("Teléfono", phone, KeyboardType.Phone) { phone = it.filter(Char::isDigit).take(15) }
                SimpleField("Fecha de nacimiento DD/MM/AAAA", birth, KeyboardType.Number) { birth = it.take(10) }
                SimpleField("Domicilio", address) { address = it }
                SimpleField("Máximo simultáneo (vacío = general ${c.config.defaultMaxConcurrentOrders})", limitText, KeyboardType.Number) {
                    limitText = it.filter(Char::isDigit).take(2)
                }

                SectionTitle("Estado operativo")
                EnumDropdown(
                    "Estado del Repartidor",
                    approval,
                    RiderApprovalStatus.entries,
                    allLabel = "",
                    labelFor = ::riderApprovalText,
                    onSelect = { if (it != null) approval = it }
                )
                AssistCard("Habilitado permite ponerse Disponible y tomar pedidos. Pendiente o Suspendido bloquean esa operación.")

                SectionTitle("Vehículo")
                RadioLine("Moto", vehicle == VehicleType.MOTORCYCLE) { vehicle = VehicleType.MOTORCYCLE }
                RadioLine("Bicicleta", vehicle == VehicleType.BICYCLE) { vehicle = VehicleType.BICYCLE }

                SectionTitle("Carga / reemplazo de documentación")
                DocumentEditLine("DNI · anverso", docs.dniFrontUri, { pendingKey = RiderDocumentKey.DNI_FRONT; picker.launch(PickVisualMediaRequest(ImageOnly)) }) { docs = docs.withUri(RiderDocumentKey.DNI_FRONT, null) }
                DocumentEditLine("DNI · reverso", docs.dniBackUri, { pendingKey = RiderDocumentKey.DNI_BACK; picker.launch(PickVisualMediaRequest(ImageOnly)) }) { docs = docs.withUri(RiderDocumentKey.DNI_BACK, null) }
                if (vehicle == VehicleType.MOTORCYCLE) {
                    DocumentEditLine("Patente", docs.motorcyclePlateUri, { pendingKey = RiderDocumentKey.MOTORCYCLE_PLATE; picker.launch(PickVisualMediaRequest(ImageOnly)) }) { docs = docs.withUri(RiderDocumentKey.MOTORCYCLE_PLATE, null) }
                    DocumentEditLine("Licencia · anverso", docs.driverLicenseFrontUri, { pendingKey = RiderDocumentKey.DRIVER_LICENSE_FRONT; picker.launch(PickVisualMediaRequest(ImageOnly)) }) { docs = docs.withUri(RiderDocumentKey.DRIVER_LICENSE_FRONT, null) }
                    DocumentEditLine("Licencia · reverso", docs.driverLicenseBackUri, { pendingKey = RiderDocumentKey.DRIVER_LICENSE_BACK; picker.launch(PickVisualMediaRequest(ImageOnly)) }) { docs = docs.withUri(RiderDocumentKey.DRIVER_LICENSE_BACK, null) }
                    DocumentEditLine("Tarjeta verde/azul", docs.vehicleCardUri, { pendingKey = RiderDocumentKey.VEHICLE_CARD; picker.launch(PickVisualMediaRequest(ImageOnly)) }) { docs = docs.withUri(RiderDocumentKey.VEHICLE_CARD, null) }
                    DocumentEditLine("Seguro", docs.insuranceCardUri, { pendingKey = RiderDocumentKey.INSURANCE_CARD; picker.launch(PickVisualMediaRequest(ImageOnly)) }) { docs = docs.withUri(RiderDocumentKey.INSURANCE_CARD, null) }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, phone, birth, vehicle, address, docs, limitText.toIntOrNull(), approval) },
                enabled = name.trim().isNotBlank()
            ) { Text("GUARDAR") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCELAR") } }
    )
}

@Composable
private fun RiderDocumentsDialog(c: MandadosController, rider: RiderProfile, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Documentación · ${rider.name}") },
        text = {
            Column(Modifier.heightIn(max = 650.dp).verticalScroll(rememberScrollState())) {
                Text("Estado del Repartidor: ${riderApprovalText(rider.approvalStatus)}", fontWeight = FontWeight.Bold)
                Text("El estado se administra desde Editar Repartidor.", style = MaterialTheme.typography.bodySmall)
                val keys = if (rider.vehicleType == VehicleType.MOTORCYCLE) RiderDocumentKey.entries
                else listOf(RiderDocumentKey.DNI_FRONT, RiderDocumentKey.DNI_BACK)
                keys.forEach { key -> DocumentReviewCard(c, rider, key) }

                if (rider.approvalStatus != RiderApprovalStatus.APPROVED) {
                    Button(
                        onClick = { c.setRiderApprovalStatus(rider.id, RiderApprovalStatus.APPROVED) },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                    ) { Text("HABILITAR REPARTIDOR") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("CERRAR") } }
    )
}

@Composable
private fun DocumentReviewCard(c: MandadosController, rider: RiderProfile, key: RiderDocumentKey) {
    val uri = rider.documents.uriFor(key)
    var note by remember(rider.id, key, rider.documentNotes[key]) { mutableStateOf(rider.documentNotes[key] ?: "") }

    Card(Modifier.fillMaxWidth().padding(top = 9.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(documentLabel(key), fontWeight = FontWeight.Bold)
            Text(documentReviewText(rider.reviewFor(key)), color = documentReviewColor(rider.reviewFor(key)))
            if (uri.isNullOrBlank()) {
                Text("Sin imagen cargada.")
            } else {
                LocalDocumentImage(uri)
                OutlinedTextField(note, { note = it }, label = { Text("Observación del Admin") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(rider.reviewFor(key) == DocumentReviewStatus.PENDING, { c.setRiderDocumentReview(rider.id, key, DocumentReviewStatus.PENDING, note) }, { Text("Pendiente") })
                    FilterChip(rider.reviewFor(key) == DocumentReviewStatus.APPROVED, { c.setRiderDocumentReview(rider.id, key, DocumentReviewStatus.APPROVED, note) }, { Text("Aprobar") })
                    FilterChip(rider.reviewFor(key) == DocumentReviewStatus.REJECTED, { c.setRiderDocumentReview(rider.id, key, DocumentReviewStatus.REJECTED, note) }, { Text("Rechazar") })
                }
            }
        }
    }
}

@Composable
private fun LocalDocumentImage(uriString: String) {
    val context = LocalContext.current
    val bitmap = remember(uriString) {
        runCatching { context.contentResolver.openInputStream(Uri.parse(uriString))?.use(BitmapFactory::decodeStream) }.getOrNull()
    }
    if (bitmap != null) {
        Image(bitmap.asImageBitmap(), "Documento", Modifier.fillMaxWidth().heightIn(min = 150.dp, max = 350.dp), contentScale = ContentScale.Fit)
    } else AssistCard("No se pudo abrir la imagen local.")
}

@Composable
internal fun RiderDashboardScreen(c: MandadosController, riderId: String?, onBack: () -> Unit) {
    riderId?.let { c.isRiderCurrentlyAvailable(it) }
    val rider = c.rider(riderId)
    var section by rememberSaveable { mutableStateOf(RiderSection.MENU) }
    BackHandler(enabled = section != RiderSection.MENU) { section = RiderSection.MENU }

    val title = when (section) {
        RiderSection.MENU -> "Punto25 · Repartidor"
        RiderSection.SHIFTS -> "Turnos"
        RiderSection.NEW_ORDERS -> "Pedidos nuevos"
        RiderSection.ACTIVE_ORDERS -> "Mis pedidos"
        RiderSection.TRANSFERS -> "Transferencias"
        RiderSection.HISTORY -> "Historial"
        RiderSection.BALANCE -> "Balance actual"
        RiderSection.BALANCES -> "Mis balances"
        RiderSection.PENDING_TIPS -> "Propinas sin confirmar"
        RiderSection.PROFILE -> "Mi perfil"
        RiderSection.PERMISSIONS -> "Privacidad y permisos"
        RiderSection.SUPPORT -> "Soporte"
    }

    OpsPage(title, if (section == RiderSection.MENU) onBack else ({ section = RiderSection.MENU })) {
        if (rider == null) {
            AssistCard("Repartidor no encontrado.")
            return@OpsPage
        }
        when (section) {
            RiderSection.MENU -> RiderHome(c, rider) { section = it }
            RiderSection.SHIFTS -> RiderShifts(c, rider)
            RiderSection.NEW_ORDERS -> RiderNewOrders(c, rider)
            RiderSection.ACTIVE_ORDERS -> RiderActiveOrders(c, rider)
            RiderSection.TRANSFERS -> RiderTransfers(c, rider)
            RiderSection.HISTORY -> RiderHistory(c, rider)
            RiderSection.BALANCE -> RiderBalance(c, rider)
            RiderSection.BALANCES -> RiderBalances(c, rider)
            RiderSection.PENDING_TIPS -> RiderPendingTips(c, rider)
            RiderSection.PROFILE -> RiderProfileView(c, rider) { section = it }
            RiderSection.PERMISSIONS -> PermissionsScreen()
            RiderSection.SUPPORT -> SupportScreen(c)
        }
    }
}

@Composable
private fun RiderHome(c: MandadosController, rider: RiderProfile, onSection: (RiderSection) -> Unit) {
    val load = c.riderActiveCount(rider.id)
    val limit = c.effectiveRiderLimit(rider)
    val completed = c.riderCompletedOrders(rider.id)
    val hasShift = c.riderHasActiveShiftNow(rider.id)
    val available = c.isRiderCurrentlyAvailable(rider.id)

    LaunchedEffect(rider.id, rider.availableUntilAt, hasShift) {
        while (true) {
            delay(15_000)
            c.isRiderCurrentlyAvailable(rider.id)
        }
    }

    Text("Hola, ${rider.name.substringBefore(" ")}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Text("${rider.id} · ${if (rider.vehicleType == VehicleType.MOTORCYCLE) "Moto" else "Bicicleta"}")
    RiderApprovalPill(rider.approvalStatus)

    if (!hasShift) {
        AssistCard("No tenés un turno activo en este momento. Para ver Pedidos nuevos o ponerte Disponible primero debés estar inscripto en un turno vigente.")
    }

    Card(Modifier.fillMaxWidth().padding(top = 10.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (available) "Disponible" else "No disponible", fontWeight = FontWeight.Bold)
                Text("$load/$limit pedidos activos", style = MaterialTheme.typography.bodySmall)
                if (available) Text("La disponibilidad vence a los 30 minutos y debe renovarse manualmente.", style = MaterialTheme.typography.bodySmall)
            }
            Switch(
                checked = available,
                onCheckedChange = { c.setRiderAvailable(rider.id, it) },
                enabled = rider.active && rider.approvalStatus == RiderApprovalStatus.APPROVED && hasShift
            )
        }
    }

    if (hasShift) {
        val newCount = c.orders.count { it.status == OrderStatus.PENDING && it.assignedRiderId == null }
        RiderMenuCard("🛒", "Pedidos nuevos", "$newCount disponible(s)", MaterialTheme.colorScheme.primary) { onSection(RiderSection.NEW_ORDERS) }
    }
    RiderMenuCard("📦", "Mis pedidos", "$load/$limit activos", MaterialTheme.colorScheme.secondary) { onSection(RiderSection.ACTIVE_ORDERS) }
    RiderMenuCard("🕒", "Turnos", "Horarios e inscripciones", MaterialTheme.colorScheme.tertiary) { onSection(RiderSection.SHIFTS) }
    RiderMenuCard("💰", "Balance actual", money(c.riderCurrentBalance(rider.id)), MaterialTheme.colorScheme.primary) { onSection(RiderSection.BALANCE) }
    val pendingTransfers = c.riderPendingTransfers(rider.id).size
    val transferAttention = c.riderTransferAttentionCount(rider.id)
    RiderMenuCard(
        "🏦",
        "Transferencias",
        if (transferAttention > 0) "$transferAttention requieren atención · $pendingTransfers pendientes" else "$pendingTransfers pendientes",
        MaterialTheme.colorScheme.primary
    ) { onSection(RiderSection.TRANSFERS) }
    val pendingTips = c.pendingTipsForRider(rider.id)
    RiderMenuCard("💵", "Propinas sin confirmar", pendingTips.size.toString() + " pendiente(s)", MaterialTheme.colorScheme.tertiary) {
        onSection(RiderSection.PENDING_TIPS)
    }
    RiderMenuCard("📊", "Mis balances", "Historial por período", MaterialTheme.colorScheme.tertiary) { onSection(RiderSection.BALANCES) }
    RiderMenuCard("🧾", "Historial", "${completed.size} entregado(s)", MaterialTheme.colorScheme.onSurfaceVariant) { onSection(RiderSection.HISTORY) }

    Spacer(Modifier.height(12.dp))
    SectionTitle("Rendimiento")
    ReportMetric("Pedidos completados", completed.size.toString())
    ReportMetric("Total desde el primer pedido", c.riderTotalOrders(rider.id).toString())
    ReportMetric("Tiempo promedio", c.riderAverageDeliverySeconds(rider.id)?.let(::formatDuration) ?: "Sin datos")
    c.riderRatingAverage(rider.id)?.let {
        ReportMetric("Calificación", "${String.format(Locale.US, "%.1f", it)} ★ (${c.riderRatingCount(rider.id)})")
    }

    TextButton(onClick = { onSection(RiderSection.PROFILE) }) { Text("PERFIL Y DOCUMENTACIÓN") }
}

@Composable
private fun RiderNewOrders(c: MandadosController, rider: RiderProfile) {
    if (!c.riderCanAccessNewOrders(rider.id)) {
        AssistCard("Pedidos nuevos sólo está disponible durante un turno vigente en el que estés inscripto.")
        return
    }
    val orders = c.orders.filter { it.status == OrderStatus.PENDING && it.assignedRiderId == null }
        .sortedByDescending { parseOrderTime(it.createdAt) ?: LocalDateTime.MIN }
    val hasCapacity = c.riderHasCapacity(rider)
    val available = c.isRiderCurrentlyAvailable(rider.id)

    if (!available) AssistCard("Para tomar pedidos debés activar Disponible. La confirmación dura 30 minutos.")
    if (!hasCapacity) AssistCard("Alcanzaste el máximo de pedidos simultáneos: ${c.riderActiveCount(rider.id)}/${c.effectiveRiderLimit(rider)}.")
    if (orders.isEmpty()) AssistCard("No hay pedidos nuevos disponibles.")

    orders.forEach { order ->
        RiderOrderCard(c, order) {
            Button(
                onClick = { c.takeOrder(order.id, rider.id) },
                enabled = available && hasCapacity,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (hasCapacity) "TOMAR PEDIDO" else "LÍMITE ALCANZADO") }
        }
    }
}

@Composable
private fun RiderActiveOrders(c: MandadosController, rider: RiderProfile) {
    val context = LocalContext.current
    val orders = c.orders.filter {
        it.assignedRiderId == rider.id && it.status in setOf(OrderStatus.PENDING, OrderStatus.ACCEPTED, OrderStatus.IN_PROGRESS)
    }.sortedByDescending { parseOrderTime(it.createdAt) ?: LocalDateTime.MIN }

    if (orders.isEmpty()) AssistCard("No tenés pedidos activos.")
    orders.forEach { order ->
        RiderOrderCard(c, order) {
            SectionTitle("Contacto con el cliente")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { dial(context, order.customerPhone) }, modifier = Modifier.weight(1f)) { Text("LLAMAR") }
                OutlinedButton(onClick = { openWhatsAppTo(context, order.customerPhone, "") }, modifier = Modifier.weight(1f)) { Text("WHATSAPP") }
            }
            OutlinedButton(
                onClick = { openWhatsAppTo(context, order.customerPhone, c.requestLocationMessage(rider)) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("SOLICITAR UBICACIÓN") }

            if (order.status == OrderStatus.PENDING || order.status == OrderStatus.ACCEPTED) {
                Button(onClick = { c.updateOrderStatus(order.id, OrderStatus.IN_PROGRESS, actor = rider.id) }, modifier = Modifier.fillMaxWidth()) {
                    Text("INICIAR / EN CURSO")
                }
            } else if (order.status == OrderStatus.IN_PROGRESS) {
                Button(onClick = { c.updateOrderStatus(order.id, OrderStatus.COMPLETED, actor = rider.id) }, modifier = Modifier.fillMaxWidth()) {
                    Text("MARCAR ENTREGADO")
                }
            }

            c.deliveryDurationSeconds(order)?.let { ReportMetric("Tiempo de entrega", formatDuration(it), true) }
            PaymentSummary(c, order, rider = rider)
            OrderTimeline(c, order)
        }
    }
}

@Composable
private fun RiderHistory(c: MandadosController, rider: RiderProfile) {
    val context = LocalContext.current
    var fromDate by rememberSaveable { mutableStateOf(defaultReportFrom()) }
    var toDate by rememberSaveable { mutableStateOf(defaultReportTo()) }
    val finals = setOf(OrderStatus.COMPLETED, OrderStatus.CANCELLED, OrderStatus.REJECTED)
    val items = filterOrdersForPeriod(
        c.orders.filter { it.assignedRiderId == rider.id && it.status in finals },
        fromDate,
        toDate
    ).sortedByDescending { parseOrderTime(it.createdAt) ?: LocalDateTime.MIN }

    DateRangePicker(fromDate, toDate, { if (it != null) fromDate = it }, { if (it != null) toDate = it })
    OutlinedButton(
        onClick = { PdfReports.shareOrders(context, "Historial_${rider.id}", fromDate, toDate, items, c) },
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
    ) { Text("EXPORTAR PDF") }

    if (items.isEmpty()) AssistCard("No hay pedidos finalizados en el período.")
    items.forEach { order ->
        RiderOrderCard(c, order) {
            c.deliveryDurationSeconds(order)?.let { ReportMetric("Tiempo de entrega", formatDuration(it), true) }
            val rating = c.ratings.firstOrNull { it.orderId == order.id }
            if (rating != null) {
                ReportMetric("Calificación", "${rating.stars}/5")
                if (rating.tipAmount > 0) {
                    ReportMetric("Propina informada", money(rating.tipAmount))
                    if (rating.tipStatus == TipStatus.SELECTED) {
                        Button(
                            onClick = { c.confirmTip(order.id) },
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                        ) { Text("CONFIRMAR PROPINA RECIBIDA") }
                    } else if (rating.tipStatus == TipStatus.CONFIRMED) {
                        Text("Propina confirmada", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
            OrderTimeline(c, order)
        }
    }
}

@Composable
private fun RiderPendingTips(c: MandadosController, rider: RiderProfile) {
    val items = c.pendingTipsForRider(rider.id)
    if (items.isEmpty()) {
        AssistCard("No tenés propinas pendientes de confirmar.")
        return
    }
    AssistCard("Confirmá únicamente las propinas que realmente recibiste. Una vez confirmadas se incorporan a tu balance.")
    items.forEach { rating ->
        val order = c.order(rating.orderId)
        Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Column(Modifier.padding(12.dp)) {
                Text(rating.orderId, fontWeight = FontWeight.Bold)
                Text(order?.createdAt ?: rating.createdAt, style = MaterialTheme.typography.bodySmall)
                ReportMetric("Propina informada", money(rating.tipAmount), true)
                Button(
                    onClick = { c.confirmTip(rating.orderId) },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                ) { Text("CONFIRMAR PROPINA RECIBIDA") }
            }
        }
    }
}

@Composable
private fun RiderBalance(c: MandadosController, rider: RiderProfile) {
    val context = LocalContext.current
    var fromDate by rememberSaveable { mutableStateOf(defaultReportFrom()) }
    var toDate by rememberSaveable { mutableStateOf(defaultReportTo()) }
    val orders = filterOrdersForPeriod(c.riderCompletedOrders(rider.id), fromDate, toDate)
        .sortedByDescending { parseOrderTime(it.createdAt) ?: LocalDateTime.MIN }
    val total = orders.sumOf { it.totalAmount ?: 0 } +
        c.ratings.filter { rating ->
            rating.riderId == rider.id && rating.tipStatus == TipStatus.CONFIRMED &&
                orders.any { it.id == rating.orderId }
        }.sumOf { it.tipAmount }

    DateRangePicker(fromDate, toDate, { if (it != null) fromDate = it }, { if (it != null) toDate = it })
    Button(
        onClick = {
            PdfReports.shareOrders(
                context, "Balance_${rider.id}", fromDate, toDate, orders, c,
                extraSummary = listOf("Total" to money(total), "Pedidos completados" to orders.size.toString())
            )
        },
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
    ) { Text("EXPORTAR PDF") }

    ReportMetric("Total del período", money(total), true)
    ReportMetric("Pedidos completados", orders.size.toString())
    ReportMetric("Promedio de entrega", c.averageDeliverySeconds(orders)?.let(::formatDuration) ?: "Sin datos")

    orders.forEach { order ->
        Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Column(Modifier.padding(12.dp)) {
                Text(order.id, fontWeight = FontWeight.Bold)
                Text(order.createdAt)
                MoneyBreakdown(order)
                val tip = c.ratings.firstOrNull { it.orderId == order.id && it.tipStatus == TipStatus.CONFIRMED }?.tipAmount ?: 0
                if (tip > 0) ReportMetric("Propina recibida", money(tip))
                ReportMetric("Tiempo", c.deliveryDurationSeconds(order)?.let(::formatDuration) ?: "Sin datos")
            }
        }
    }
    AssistCard("El balance incluye servicio y adicionales. No incluye el valor de la mercadería.")
}

@Composable
private fun RiderBalances(c: MandadosController, rider: RiderProfile) {
    val context = LocalContext.current
    var fromDate by rememberSaveable { mutableStateOf(defaultReportFrom()) }
    var toDate by rememberSaveable { mutableStateOf(defaultReportTo()) }
    var selectedDay by rememberSaveable { mutableStateOf<String?>(null) }

    val orders = filterOrdersForPeriod(c.riderCompletedOrders(rider.id), fromDate, toDate)
    val grouped = orders.groupBy { completionDate(it) ?: (parseOrderTime(it.createdAt)?.toLocalDate()?.format(dateFormatter) ?: "Sin fecha") }
        .toSortedMap(compareByDescending { parseDate(it) ?: LocalDate.MIN })

    DateRangePicker(fromDate, toDate, {
        if (it != null) { fromDate = it; selectedDay = null }
    }, {
        if (it != null) { toDate = it; selectedDay = null }
    })
    OutlinedButton(
        onClick = { PdfReports.shareOrders(context, "Balances_${rider.id}", fromDate, toDate, orders, c) },
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
    ) { Text("EXPORTAR PDF") }

    if (selectedDay == null) {
        if (grouped.isEmpty()) AssistCard("Todavía no hay balances históricos en el período.")
        grouped.forEach { (day, dayOrders) ->
            Card(Modifier.fillMaxWidth().padding(top = 8.dp).clickable { selectedDay = day }) {
                Column(Modifier.padding(12.dp)) {
                    Text(day, fontWeight = FontWeight.Bold)
                    Text("${dayOrders.size} pedido(s)")
                    Text("Total: ${money(dayOrders.sumOf { it.totalAmount ?: 0 })}")
                    Text("Promedio: ${c.averageDeliverySeconds(dayOrders)?.let(::formatDuration) ?: "Sin datos"}")
                    Text("Tocar para ver pedidos", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    } else {
        TextButton(onClick = { selectedDay = null }) { Text("← VOLVER AL RESUMEN") }
        SectionTitle(selectedDay ?: "")
        grouped[selectedDay].orEmpty().sortedByDescending { parseOrderTime(it.createdAt) ?: LocalDateTime.MIN }.forEach { order ->
            Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(order.id, fontWeight = FontWeight.Bold)
                    Text(order.createdAt)
                    Text(categoryText(order.category))
                    MoneyBreakdown(order)
                    ReportMetric("Tiempo", c.deliveryDurationSeconds(order)?.let(::formatDuration) ?: "Sin datos")
                }
            }
        }
    }
}

@Composable
private fun RiderTransfers(c: MandadosController, rider: RiderProfile) {
    var completed by rememberSaveable { mutableStateOf(false) }
    val pending = c.riderPendingTransfers(rider.id)
    val completedItems = c.riderCompletedTransfers(rider.id)
    val items = if (completed) completedItems else pending

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
  selected = !completed,
  onClick = { completed = false },
  label = { Text("PENDIENTES (${pending.size})") },
  modifier = Modifier.weight(1f)
        )
        FilterChip(
  selected = completed,
  onClick = { completed = true },
  label = { Text("COMPLETADAS (${completedItems.size})") },
  modifier = Modifier.weight(1f)
        )
    }

    val attention = c.riderTransferAttentionCount(rider.id)
    if (!completed && attention > 0) {
        AssistCard("$attention transferencia(s) requieren tu atención.")
    }
    if (items.isEmpty()) {
        AssistCard(if (completed) "No tenés transferencias confirmadas." else "No tenés transferencias pendientes.")
        return
    }

    items.forEach { payment ->
        val order = c.order(payment.orderId) ?: return@forEach
        var expanded by rememberSaveable(payment.id) { mutableStateOf(false) }
        Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
  Column(Modifier.padding(12.dp)) {
      Text(order.id, fontWeight = FontWeight.Bold)
      Text(order.customerName)
      ReportMetric("Importe esperado", money(payment.expectedAmount), true)
      Text(transferPaymentStatusLabel(payment.status), color = MaterialTheme.colorScheme.primary)
      Text("Actualizado: ${payment.updatedAt}", style = MaterialTheme.typography.bodySmall)
      Text("Comprobante: ${if (payment.proofUri.isNullOrBlank()) "No" else "Sí"}", style = MaterialTheme.typography.bodySmall)
      OutlinedButton(
          onClick = { expanded = !expanded },
          modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
      ) { Text(if (expanded) "OCULTAR DETALLE" else "VER DETALLE DEL PEDIDO") }
      if (expanded) {
          Text(order.detail, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
          PaymentSummary(c, order, rider = rider)
      }
  }
        }
    }
}

@Composable
private fun RiderShifts(c: MandadosController, rider: RiderProfile) {
    val today = LocalDate.now()
    val now = LocalDateTime.now()
    var confirmReservationId by rememberSaveable { mutableStateOf<String?>(null) }
    val occurrences = c.shiftOccurrences(today.minusDays(1), today.plusDays(8))

    if (occurrences.isEmpty()) {
        AssistCard("Administración todavía no configuró turnos para los próximos días.")
        return
    }

    occurrences.forEach { (shift, dateText) ->
        val reservation = c.shiftReservations.firstOrNull {
            it.riderId == rider.id && it.shiftTemplateId == shift.id && it.serviceDate == dateText
        }
        val occupied = c.shiftReservations.count {
            it.shiftTemplateId == shift.id && it.serviceDate == dateText && it.status == ShiftReservationStatus.RESERVED
        }
        val window = c.shiftWindow(shift, dateText)
        val finished = window?.second?.let { !it.isAfter(now) } == true
        val active = window?.let { !now.isBefore(it.first) && now.isBefore(it.second) } == true
        val container = when {
            finished -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.58f)
            active -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceVariant
        }

        Card(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            colors = CardDefaults.cardColors(containerColor = container)
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(dateText + " · " + dayName(shift.dayOfWeek), fontWeight = FontWeight.Bold)
                        Text(shift.startTime + "–" + shift.endTime, style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        when {
                            finished -> "FINALIZADO"
                            active -> "ACTIVO"
                            shift.isSpecificDate -> "FECHA ESPECIAL"
                            else -> "PRÓXIMO"
                        },
                        color = when {
                            finished -> MaterialTheme.colorScheme.error
                            active -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (window?.let { it.second.toLocalDate() != it.first.toLocalDate() } == true) {
                    Text("Finaliza al día siguiente", style = MaterialTheme.typography.bodySmall)
                }
                Text("Cupo: " + occupied + "/" + shift.capacity)

                when {
                    reservation?.status == ShiftReservationStatus.RESERVED -> {
                        Text("Turno confirmado · anotado " + reservation.joinedAt, color = MaterialTheme.colorScheme.primary)
                        if (!finished && c.canCancelShift(reservation)) {
                            OutlinedButton(
                                onClick = { confirmReservationId = reservation.id },
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFB85F5F))
                            ) { Text("CANCELAR INSCRIPCIÓN") }
                            Text(
                                "Si cancelás, deberás esperar 15 minutos para volver a anotarte. Una segunda cancelación en esta misma ocurrencia bloquea una nueva reinscripción.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else if (!finished && !active) {
                            AssistCard("La ventana de cancelación de 15 minutos finalizó. Cualquier excepción debe gestionarla Administración.")
                        }
                    }
                    finished -> Text("Este turno ya finalizó.", style = MaterialTheme.typography.bodySmall)
                    reservation?.blockedRejoin == true -> {
                        AssistCard("Cancelaste dos veces esta misma ocurrencia. Ya no podés volver a anotarte; Administración puede agregarte manualmente si corresponde.")
                    }
                    reservation?.status == ShiftReservationStatus.CANCELLED && c.minutesUntilRiderCanRejoin(reservation) > 0 -> {
                        val min = c.minutesUntilRiderCanRejoin(reservation)
                        AssistCard("Turno cancelado. Podrás volver a anotarte en aproximadamente " + min + " minuto(s).")
                    }
                    occupied >= shift.capacity -> AssistCard("Turno completo.")
                    else -> {
                        Button(
                            onClick = { c.reserveShift(rider.id, shift.id, dateText) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if ((reservation?.cancellationCount ?: 0) > 0) "VOLVER A ANOTARME" else "ANOTARME") }
                    }
                }
            }
        }
    }

    confirmReservationId?.let { id ->
        val reservation = c.shiftReservations.firstOrNull { it.id == id }
        val secondCancellation = (reservation?.cancellationCount ?: 0) >= 1
        AlertDialog(
            onDismissRequest = { confirmReservationId = null },
            title = { Text("Cancelar inscripción") },
            text = {
                Text(
                    if (secondCancellation)
                        "¿Confirmás la cancelación? Esta sería tu segunda cancelación de esta ocurrencia: después no podrás volver a inscribirte en este mismo turno."
                    else
                        "¿Confirmás la cancelación? Después deberás esperar 15 minutos para volver a anotarte. Si te reinscribís y cancelás por segunda vez, no podrás volver a inscribirte en este mismo turno."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        c.cancelShift(id)
                        confirmReservationId = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFB85F5F))
                ) { Text("SÍ, CANCELAR") }
            },
            dismissButton = { TextButton(onClick = { confirmReservationId = null }) { Text("NO") } }
        )
    }
}

@Composable
private fun RiderProfileView(c: MandadosController, rider: RiderProfile, onSection: (RiderSection) -> Unit) {
    var alias by remember(rider.id, rider.transferAlias) { mutableStateOf(rider.transferAlias) }
    var aliasMessage by remember { mutableStateOf("") }

    Text(rider.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Text(rider.id)
    RiderApprovalPill(rider.approvalStatus)
    Text("Carga: ${c.riderActiveCount(rider.id)}/${c.effectiveRiderLimit(rider)}")
    if (rider.phone.isNotBlank()) Text("Teléfono: ${rider.phone}")
    if (rider.address.isNotBlank()) Text("Domicilio: ${rider.address}")

    SectionTitle("Datos de cobro")
    OutlinedTextField(
        value = alias,
        onValueChange = { alias = it.trim().take(80); aliasMessage = "" },
        label = { Text("Alias de transferencia") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    Button(
        onClick = {
            if (c.updateRiderTransferAlias(rider.id, alias)) aliasMessage = "Alias actualizado."
        },
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
    ) { Text("GUARDAR ALIAS") }
    if (aliasMessage.isNotBlank()) Text(aliasMessage, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
    AssistCard("El alias sólo puede modificarse desde el perfil del propio Repartidor. Administración puede visualizarlo para conciliaciones, pero no cambiarlo.")

    SectionTitle("Seguridad de acceso")
    var currentPassword by remember(rider.id) { mutableStateOf("") }
    var newPassword by remember(rider.id) { mutableStateOf("") }
    var confirmPassword by remember(rider.id) { mutableStateOf("") }
    var passwordMessage by remember(rider.id) { mutableStateOf("") }
    OutlinedTextField(
        currentPassword,
        { currentPassword = it; passwordMessage = "" },
        label = { Text("Contraseña actual") },
        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        newPassword,
        { newPassword = it; passwordMessage = "" },
        label = { Text("Nueva contraseña") },
        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
    )
    OutlinedTextField(
        confirmPassword,
        { confirmPassword = it; passwordMessage = "" },
        label = { Text("Repetir nueva contraseña") },
        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
    )
    Button(
        onClick = {
            passwordMessage = when {
                newPassword != confirmPassword -> "Las nuevas contraseñas no coinciden."
                !c.passwordIsStrong(newPassword) -> "Usá al menos 8 caracteres, con mayúscula, minúscula y número."
                c.changeRiderPassword(rider.id, currentPassword, newPassword) -> {
                    currentPassword = ""; newPassword = ""; confirmPassword = ""
                    "Contraseña actualizada."
                }
                else -> "La contraseña actual no es correcta."
            }
        },
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
    ) { Text("CAMBIAR CONTRASEÑA") }
    if (passwordMessage.isNotBlank()) {
        Text(
            passwordMessage,
            color = if (passwordMessage == "Contraseña actualizada.") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall
        )
    }

    SectionTitle("Documentación")
    val keys = if (rider.vehicleType == VehicleType.MOTORCYCLE) RiderDocumentKey.entries
    else listOf(RiderDocumentKey.DNI_FRONT, RiderDocumentKey.DNI_BACK)
    keys.forEach { key ->
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text(documentLabel(key), Modifier.weight(1f))
            Text(documentReviewText(rider.reviewFor(key)), color = documentReviewColor(rider.reviewFor(key)))
        }
    }

    OutlinedButton(onClick = { onSection(RiderSection.PERMISSIONS) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("PRIVACIDAD Y PERMISOS") }
    OutlinedButton(onClick = { onSection(RiderSection.SUPPORT) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("SOPORTE") }
}

@Composable
internal fun AdminShiftsScreen(c: MandadosController, onBack: () -> Unit) {
    val selectedDays = remember { mutableStateMapOf<Int, Boolean>() }
    val expandedDates = remember { mutableStateMapOf<String, Boolean>() }
    var ranges by remember { mutableStateOf(listOf("" to "")) }
    var capacityText by rememberSaveable { mutableStateOf("1") }
    var specificMode by rememberSaveable { mutableStateOf(false) }
    var specificDate by rememberSaveable { mutableStateOf(LocalDate.now().format(dateFormatter)) }
    var fromDate by rememberSaveable { mutableStateOf(LocalDate.now().minusDays(7).format(dateFormatter)) }
    var toDate by rememberSaveable { mutableStateOf(LocalDate.now().plusDays(21).format(dateFormatter)) }
    var error by rememberSaveable { mutableStateOf("") }
    var addToShift by remember { mutableStateOf<Pair<ShiftTemplate, String>?>(null) }
    var removeReservationId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteShift by remember { mutableStateOf<ShiftTemplate?>(null) }
    var editingShift by remember { mutableStateOf<ShiftTemplate?>(null) }

    val from = parseDate(fromDate) ?: LocalDate.now().minusDays(7)
    val to = parseDate(toDate) ?: LocalDate.now().plusDays(21)
    val occurrences = c.shiftOccurrences(from, to)
    val grouped = occurrences.groupBy { it.second }
        .toSortedMap(compareBy { parseDate(it) ?: LocalDate.MAX })

    OpsPage("Turnos · Administración", onBack) {
        SectionTitle("Crear turnos")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !specificMode,
                onClick = { specificMode = false; error = "" },
                label = { Text("SEMANAL") },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = specificMode,
                onClick = { specificMode = true; error = "" },
                label = { Text("FECHA ESPECÍFICA") },
                modifier = Modifier.weight(1f)
            )
        }

        if (!specificMode) {
            Text(
                "Seleccioná uno o varios días. Estos horarios se repiten semanalmente.",
                style = MaterialTheme.typography.bodySmall
            )
            (1..7).chunked(4).forEach { rowDays ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    rowDays.forEach { day ->
                        Row(
                            Modifier.weight(1f).clickable { selectedDays[day] = !(selectedDays[day] ?: false) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(selectedDays[day] ?: false, { selectedDays[day] = it })
                            Text(dayName(day).take(3), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    repeat(4 - rowDays.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        } else {
            AssistCard("Usá una fecha específica para feriados, eventos o días que necesiten una dotación distinta. Si coincide con un turno semanal, el horario específico tiene prioridad en esa fecha.")
            SimpleField("Fecha DD/MM/AAAA", specificDate, KeyboardType.Number) {
                specificDate = it.filter { ch -> ch.isDigit() || ch == '/' }.take(10)
            }
        }

        SectionTitle("Horarios")
        ranges.forEachIndexed { index, pair ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TimeInputField(
                    label = "Inicio",
                    value = pair.first,
                    modifier = Modifier.weight(1f),
                    onValue = { v -> ranges = ranges.mapIndexed { i, old -> if (i == index) v to old.second else old } }
                )
                TimeInputField(
                    label = "Fin",
                    value = pair.second,
                    modifier = Modifier.weight(1f),
                    onValue = { v -> ranges = ranges.mapIndexed { i, old -> if (i == index) old.first to v else old } }
                )
                if (ranges.size > 1) {
                    TextButton(onClick = { ranges = ranges.filterIndexed { i, _ -> i != index } }) { Text("X") }
                }
            }
        }
        TextButton(onClick = { ranges = ranges + ("" to "") }) { Text("+ AGREGAR HORARIO") }
        SimpleField("Cupo de Repartidores", capacityText, KeyboardType.Number) {
            capacityText = it.filter(Char::isDigit).take(2)
        }
        if (error.isNotBlank()) {
            Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp))
        }

        Button(
            onClick = {
                val capacity = capacityText.toIntOrNull() ?: 0
                error = if (specificMode) {
                    c.addSpecificDateShifts(specificDate, ranges, capacity) ?: ""
                } else {
                    c.addShiftTemplatesBulk(selectedDays.filterValues { it }.keys, ranges, capacity) ?: ""
                }
                if (error.isBlank()) {
                    ranges = listOf("" to "")
                    capacityText = "1"
                    selectedDays.clear()
                    if (specificMode) specificDate = LocalDate.now().format(dateFormatter)
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) { Text(if (specificMode) "AGREGAR TURNO ESPECIAL" else "AGREGAR TURNOS") }

        SectionTitle("Calendario de turnos")
        DateRangePicker(fromDate, toDate, { if (it != null) fromDate = it }, { if (it != null) toDate = it })
        Text("Ordenados de la fecha más antigua a la más nueva.", style = MaterialTheme.typography.bodySmall)

        if (grouped.isEmpty()) {
            AssistCard("No hay turnos configurados en el período seleccionado.")
        }

        grouped.forEach { (dateText, items) ->
            val date = parseDate(dateText)
            val isPastDay = date?.isBefore(LocalDate.now()) == true
            Card(
                Modifier.fillMaxWidth().padding(top = 8.dp).clickable {
                    expandedDates[dateText] = !(expandedDates[dateText] ?: false)
                },
                colors = CardDefaults.cardColors(
                    containerColor = if (isPastDay) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.42f)
                    else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(dateText, fontWeight = FontWeight.Bold)
                        Text(
                            (date?.dayOfWeek?.value?.let(::dayName) ?: "") + " · " + items.size + " horario(s)",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Text(if (expandedDates[dateText] == true) "⌃" else "⌄", style = MaterialTheme.typography.titleLarge)
                }
            }

            if (expandedDates[dateText] == true) {
                items.sortedBy { (shift, d) -> c.shiftWindow(shift, d)?.first ?: LocalDateTime.MAX }.forEach { (shift, occurrenceDate) ->
                    val window = c.shiftWindow(shift, occurrenceDate)
                    val now = LocalDateTime.now()
                    val finished = window?.second?.let { !it.isAfter(now) } == true
                    val active = window?.let { !now.isBefore(it.first) && now.isBefore(it.second) } == true
                    val reservations = c.shiftReservations.filter {
                        it.shiftTemplateId == shift.id &&
                            it.serviceDate == occurrenceDate &&
                            it.status == ShiftReservationStatus.RESERVED
                    }
                    val cardColor = when {
                        finished -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.60f)
                        active -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.surface
                    }

                    Card(
                        Modifier.fillMaxWidth().padding(start = 12.dp, top = 6.dp),
                        colors = CardDefaults.cardColors(containerColor = cardColor)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(shift.startTime + "–" + shift.endTime, fontWeight = FontWeight.Bold)
                                    Text(
                                        when {
                                            shift.isSpecificDate -> "Fecha específica"
                                            else -> "Turno semanal"
                                        } + " · Cupo " + reservations.size + "/" + shift.capacity,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Text(
                                    when {
                                        finished -> "FINALIZADO"
                                        active -> "ACTIVO"
                                        else -> "FUTURO"
                                    },
                                    color = when {
                                        finished -> MaterialTheme.colorScheme.error
                                        active -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                            if (window?.let { it.second.toLocalDate() != it.first.toLocalDate() } == true) {
                                Text("Finaliza al día siguiente", style = MaterialTheme.typography.bodySmall)
                            }

                            reservations.forEach { reservation ->
                                val rider = c.rider(reservation.riderId)
                                Row(
                                    Modifier.fillMaxWidth().padding(top = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        rider?.let { it.name + " · " + it.id } ?: reservation.riderId,
                                        Modifier.weight(1f)
                                    )
                                    if (!finished) {
                                        TextButton(onClick = { removeReservationId = reservation.id }) { Text("QUITAR") }
                                    }
                                }
                            }

                            if (!finished) {
                                OutlinedButton(
                                    onClick = { addToShift = shift to occurrenceDate },
                                    enabled = reservations.size < shift.capacity,
                                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                                ) { Text("+ AGREGAR REPARTIDOR") }
                            }

                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(
                                    onClick = { editingShift = shift },
                                    modifier = Modifier.weight(1f)
                                ) { Text("EDITAR") }
                                OutlinedButton(
                                    onClick = { deleteShift = shift },
                                    enabled = c.canDeleteShiftTemplate(shift.id),
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFB85F5F))
                                ) { Text("ELIMINAR") }
                            }
                            if (!c.canDeleteShiftTemplate(shift.id)) {
                                Text(
                                    "No puede eliminarse mientras esté activo o tenga inscripciones vigentes/futuras.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }

        SectionTitle("Auditoría reciente")
        c.shiftAuditEvents.takeLast(20).reversed().forEach { e ->
            val shift = c.shifts.firstOrNull { it.id == e.shiftTemplateId }
            Text(
                e.at + " · " + shiftEventText(e.type) + " · " +
                    (c.rider(e.riderId)?.name ?: e.riderId) + " · " +
                    e.serviceDate + " " + (shift?.startTime ?: ""),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }
    }

    editingShift?.let { shift ->
        ShiftEditDialog(c, shift) { editingShift = null }
    }

    deleteShift?.let { shift ->
        AlertDialog(
            onDismissRequest = { deleteShift = null },
            title = { Text("Eliminar turno") },
            text = { Text("¿Confirmás que querés eliminar este turno? Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        c.deleteShiftTemplate(shift.id)
                        deleteShift = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFB85F5F))
                ) { Text("ELIMINAR") }
            },
            dismissButton = { TextButton(onClick = { deleteShift = null }) { Text("CANCELAR") } }
        )
    }

    addToShift?.let { (shift, date) ->
        val existing = c.shiftReservations.filter {
            it.shiftTemplateId == shift.id && it.serviceDate == date && it.status == ShiftReservationStatus.RESERVED
        }.map { it.riderId }.toSet()
        AlertDialog(
            onDismissRequest = { addToShift = null },
            title = { Text("Agregar Repartidor") },
            text = {
                Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                    c.riders.filter {
                        it.active && it.approvalStatus == RiderApprovalStatus.APPROVED && it.id !in existing
                    }.forEach { rider ->
                        TextButton(
                            onClick = {
                                if (c.adminAddRiderToShift(rider.id, shift.id, date)) addToShift = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(rider.name + " · " + rider.id) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { addToShift = null }) { Text("CERRAR") } }
        )
    }

    removeReservationId?.let { id ->
        AlertDialog(
            onDismissRequest = { removeReservationId = null },
            title = { Text("Quitar Repartidor del turno") },
            text = { Text("¿Confirmás que querés quitar al Repartidor de esta ocurrencia? Si el turno está en curso pasará a No disponible, pero conservará sus pedidos activos.") },
            confirmButton = {
                TextButton(onClick = {
                    c.adminRemoveRiderFromShift(id)
                    removeReservationId = null
                }) { Text("QUITAR") }
            },
            dismissButton = { TextButton(onClick = { removeReservationId = null }) { Text("CANCELAR") } }
        )
    }
}

@Composable
private fun ShiftEditDialog(c: MandadosController, shift: ShiftTemplate, onDismiss: () -> Unit) {
    var recurring by remember(shift.id) { mutableStateOf(!shift.isSpecificDate) }
    var day by remember(shift.id) { mutableStateOf(shift.dayOfWeek) }
    var date by remember(shift.id) { mutableStateOf(shift.specificDate ?: LocalDate.now().format(dateFormatter)) }
    var start by remember(shift.id) { mutableStateOf(shift.startTime) }
    var end by remember(shift.id) { mutableStateOf(shift.endTime) }
    var capacity by remember(shift.id) { mutableStateOf(shift.capacity.toString()) }
    var enabled by remember(shift.id) { mutableStateOf(shift.enabled) }
    var error by remember(shift.id) { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar turno") },
        text = {
            Column(Modifier.heightIn(max = 600.dp).verticalScroll(rememberScrollState())) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(recurring, { recurring = true }, { Text("SEMANAL") })
                    FilterChip(!recurring, { recurring = false }, { Text("FECHA ESPECÍFICA") })
                }
                if (recurring) {
                    IntDropdown("Día", day, (1..7).toList(), ::dayName) { day = it }
                } else {
                    SimpleField("Fecha DD/MM/AAAA", date, KeyboardType.Number) {
                        date = it.filter { ch -> ch.isDigit() || ch == '/' }.take(10)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TimeInputField("Inicio", start, Modifier.weight(1f)) { start = it }
                    TimeInputField("Fin", end, Modifier.weight(1f)) { end = it }
                }
                SimpleField("Cupo", capacity, KeyboardType.Number) { capacity = it.filter(Char::isDigit).take(2) }
                ToggleSetting("Habilitado", enabled) { enabled = it }
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                AssistCard("La edición afecta todas las ocurrencias vinculadas a este turno. Las inscripciones existentes se conservan.")
            }
        },
        confirmButton = {
            TextButton(onClick = {
                error = c.updateShiftTemplate(
                    id = shift.id,
                    recurring = recurring,
                    dayOfWeek = day,
                    specificDate = if (recurring) null else date,
                    rawStart = start,
                    rawEnd = end,
                    capacity = capacity.toIntOrNull() ?: 0,
                    enabled = enabled
                ) ?: ""
                if (error.isBlank()) onDismiss()
            }) { Text("GUARDAR") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCELAR") } }
    )
}

@Composable
internal fun AdminPaymentsScreen(c: MandadosController, onBack: () -> Unit) {
    val context = LocalContext.current
    val p = c.config.paymentConfig
    var fromDate by rememberSaveable { mutableStateOf(defaultReportFrom()) }
    var toDate by rememberSaveable { mutableStateOf(defaultReportTo()) }
    val from = parseDate(fromDate)
    val to = parseDate(toDate)
    val filteredPayments = c.payments.filter { payment ->
        val date = parseOrderTime(payment.updatedAt)?.toLocalDate()
        date != null && (from == null || !date.isBefore(from)) && (to == null || !date.isAfter(to))
    }.sortedByDescending { parseOrderTime(it.updatedAt) ?: LocalDateTime.MIN }

    OpsPage("Pagos · Administración", onBack) {
        SectionTitle("Medios habilitados")
        ToggleSetting("Efectivo", p.cashEnabled) { c.updateConfig(c.config.copy(paymentConfig = p.copy(cashEnabled = it))) }
        ToggleSetting("Transferencia / alias", p.riderTransferEnabled) { c.updateConfig(c.config.copy(paymentConfig = p.copy(riderTransferEnabled = it))) }
        ToggleSetting("Exigir comprobante", p.transferProofRequired) { c.updateConfig(c.config.copy(paymentConfig = p.copy(transferProofRequired = it))) }
        ToggleSetting("Confirmación del Repartidor", p.riderConfirmationRequired) { c.updateConfig(c.config.copy(paymentConfig = p.copy(riderConfirmationRequired = it))) }
        SimpleField("Alias central Punto25 (opcional)", p.centralAlias) {
            c.updateConfig(c.config.copy(paymentConfig = c.config.paymentConfig.copy(centralAlias = it)))
        }

        SectionTitle("QR / pasarela")
        ToggleSetting("QR interoperable", p.qrEnabled) { c.updateConfig(c.config.copy(paymentConfig = p.copy(qrEnabled = it))) }
        ToggleSetting("Checkout online", p.onlineCheckoutEnabled) { c.updateConfig(c.config.copy(paymentConfig = p.copy(onlineCheckoutEnabled = it))) }
        SimpleField("Proveedor QR", p.qrProvider) { c.updateConfig(c.config.copy(paymentConfig = c.config.paymentConfig.copy(qrProvider = it))) }
        Text("Modo: ${p.qrMode}", fontWeight = FontWeight.Bold)
        AssistCard("La arquitectura QR/OAuth/webhook queda preparada, pero esta Alpha no procesa cobros reales: no hay credenciales privadas ni backend conectado.")

        SectionTitle("Propinas")
        ToggleSetting("Propinas habilitadas", p.tipsEnabled) { c.updateConfig(c.config.copy(paymentConfig = p.copy(tipsEnabled = it))) }
        SimpleField("Sugerencias separadas por coma", p.tipSuggestions.joinToString(","), KeyboardType.Number) { raw ->
            val values = raw.split(",").mapNotNull { it.trim().toIntOrNull() }.filter { it > 0 }.take(3)
            if (values.isNotEmpty()) c.updateConfig(c.config.copy(paymentConfig = c.config.paymentConfig.copy(tipSuggestions = values)))
        }
        ToggleSetting("Permitir otro importe", p.customTipEnabled) { c.updateConfig(c.config.copy(paymentConfig = p.copy(customTipEnabled = it))) }

        SectionTitle("Conciliación")
        DateRangePicker(fromDate, toDate, { if (it != null) fromDate = it }, { if (it != null) toDate = it })
        Button(
            onClick = {
                PdfReports.shareLines(
                    context,
                    "Conciliacion_Pagos_Punto25",
                    fromDate,
                    toDate,
                    summary = listOf(
                        "Movimientos" to filteredPayments.size.toString(),
                        "Importe esperado" to money(filteredPayments.sumOf { it.expectedAmount })
                    ),
                    lines = filteredPayments.map {
                        "${it.updatedAt} · ${it.orderId} · ${it.channel} · ${it.status} · ${money(it.expectedAmount)}"
                    }
                )
            },
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
        ) { Text("EXPORTAR PDF") }

        if (filteredPayments.isEmpty()) {
            AssistCard("No hay movimientos en el período seleccionado.")
        } else {
            filteredPayments.forEach { payment ->
                Card(Modifier.fillMaxWidth().padding(top = 7.dp)) {
                    Column(Modifier.padding(10.dp)) {
                        Text(payment.orderId, fontWeight = FontWeight.Bold)
                        Text("${payment.channel} · ${payment.status}")
                        Text("Esperado: ${money(payment.expectedAmount)} · ${payment.updatedAt}")
                        payment.riderId?.let { rid ->
                            Text(c.rider(rid)?.let { "Repartidor: ${it.name} · $rid" } ?: "Repartidor: $rid")
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun AdminLegalScreen(c: MandadosController, onBack: () -> Unit) {
    var editingType by rememberSaveable { mutableStateOf<LegalDocumentType?>(null) }
    val profile = c.config.legalProfile

    OpsPage("Legal y privacidad", onBack) {
        SectionTitle("Datos del responsable")
        SimpleField("Nombre comercial", profile.businessName) {
            c.updateConfig(c.config.copy(legalProfile = c.config.legalProfile.copy(businessName = it)))
        }
        SimpleField("Razón social / responsable", profile.legalName) {
            c.updateConfig(c.config.copy(legalProfile = c.config.legalProfile.copy(legalName = it)))
        }
        SimpleField("CUIT", profile.taxId) {
            c.updateConfig(c.config.copy(legalProfile = c.config.legalProfile.copy(taxId = it)))
        }
        SimpleField("Domicilio legal", profile.legalAddress) {
            c.updateConfig(c.config.copy(legalProfile = c.config.legalProfile.copy(legalAddress = it)))
        }
        SimpleField("Correo para privacidad y datos personales", profile.privacyEmail) {
            c.updateConfig(c.config.copy(legalProfile = c.config.legalProfile.copy(privacyEmail = it)))
        }
        SimpleField("WhatsApp soporte", c.config.supportWhatsapp) {
            c.updateConfig(c.config.copy(
                supportWhatsapp = it.filter(Char::isDigit),
                legalProfile = c.config.legalProfile.copy(supportWhatsapp = it.filter(Char::isDigit))
            ))
        }
        SimpleField("Área de servicio", profile.serviceArea) {
            c.updateConfig(c.config.copy(legalProfile = c.config.legalProfile.copy(serviceArea = it)))
        }

        SectionTitle("Documentos PDF")
        LegalDocumentType.entries.forEach { type ->
            val doc = c.legalDocuments.filter { it.type == type }.maxByOrNull { parseOrderTime(it.updatedAt) ?: LocalDateTime.MIN }
            Card(Modifier.fillMaxWidth().padding(top = 8.dp).clickable { editingType = type }) {
                Column(Modifier.padding(12.dp)) {
                    Text(legalTypeText(type), fontWeight = FontWeight.Bold)
                    Text(doc?.let { "Versión ${it.version} · ${if (it.published) "Publicado" else "Borrador"}" } ?: "Sin documento")
                    if (doc?.fileName != null) Text(doc.fileName, style = MaterialTheme.typography.bodySmall)
                    Text(if (doc?.fileUri != null) "Tocar para gestionar PDF" else "Tocar para cargar PDF", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        AssistCard("Los documentos legales se cargan como PDF. Una versión publicada no se sobreescribe silenciosamente: si cambia el archivo, versión o vigencia, Punto25 conserva la versión anterior y crea una nueva.")
    }

    editingType?.let { type ->
        LegalPdfEditor(c, type) { editingType = null }
    }
}

@Composable
private fun LegalPdfEditor(c: MandadosController, type: LegalDocumentType, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val existing = c.legalDocuments.filter { it.type == type }.maxByOrNull { parseOrderTime(it.updatedAt) ?: LocalDateTime.MIN }
    var version by remember(type, existing?.id) { mutableStateOf(existing?.version ?: "1.0") }
    var effective by remember(type, existing?.id) { mutableStateOf(existing?.effectiveDate ?: LocalDate.now().format(dateFormatter)) }
    var published by remember(type, existing?.id) { mutableStateOf(existing?.published ?: false) }
    var requireAcceptance by remember(type, existing?.id) { mutableStateOf(existing?.requireAcceptance ?: false) }
    var selectedUri by remember(type, existing?.id) { mutableStateOf(existing?.fileUri) }
    var selectedName by remember(type, existing?.id) { mutableStateOf(existing?.fileName) }
    var error by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            selectedUri = uri.toString()
            selectedName = uri.lastPathSegment?.substringAfterLast('/') ?: "documento.pdf"
            error = ""
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(legalTypeText(type)) },
        text = {
            Column(Modifier.heightIn(max = 600.dp).verticalScroll(rememberScrollState())) {
                SimpleField("Versión", version) { version = it }
                SimpleField("Fecha de vigencia DD/MM/AAAA", effective) { effective = it.take(10) }

                Card(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Archivo PDF", fontWeight = FontWeight.Bold)
                        Text(selectedName ?: "Sin archivo cargado", style = MaterialTheme.typography.bodySmall)
                        existing?.fileSha256?.let {
                            Text("SHA-256: ${it.take(16)}…", style = MaterialTheme.typography.labelSmall)
                        }
                        Button(
                            onClick = { picker.launch(arrayOf("application/pdf")) },
                            modifier = Modifier.fillMaxWidth().padding(top = 7.dp)
                        ) { Text(if (selectedUri == null) "CARGAR PDF" else "REEMPLAZAR PDF") }
                        if (selectedUri != null) {
                            OutlinedButton(
                                onClick = { openPdfUri(context, selectedUri!!) },
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                            ) { Text("VER DOCUMENTO") }
                        }
                    }
                }

                ToggleSetting("Publicado", published) { published = it }
                ToggleSetting("Exigir nueva aceptación", requireAcceptance) { requireAcceptance = it }
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val uri = selectedUri
                    if (uri == null) {
                        error = "Cargá un archivo PDF antes de guardar."
                    } else if (c.saveLegalPdf(type, version, effective, published, requireAcceptance, uri, selectedName)) {
                        onDismiss()
                    } else {
                        error = "No se pudo leer o guardar el PDF."
                    }
                },
                enabled = selectedUri != null
            ) { Text(if (existing?.published == true && existing.fileUri != selectedUri) "CREAR NUEVA VERSIÓN" else "GUARDAR") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CERRAR") } }
    )
}

@Composable
private fun PermissionsScreen() {
    val context = LocalContext.current
    Text("Punto25 solicita los permisos cuando una función realmente los necesita.", style = MaterialTheme.typography.bodyLarge)
    PermissionInfo("Ubicación", "Se usa para centrar el mapa o tomar tu ubicación como punto inicial. Dirección y pin manual siguen disponibles.")
    PermissionInfo("Notificaciones", "Se usarán para cambios de pedidos y turnos cuando FCM esté conectado.")
    PermissionInfo("Fotos / documentos", "Se utiliza el selector seguro de Android; no hace falta acceso general a tu galería.")
    OutlinedButton(
        onClick = {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
            context.startActivity(intent)
        },
        modifier = Modifier.fillMaxWidth()
    ) { Text("ABRIR CONFIGURACIÓN DEL SISTEMA") }
}

@Composable
private fun SupportScreen(c: MandadosController) {
    val context = LocalContext.current
    val number = c.config.supportWhatsapp.ifBlank { c.config.legalProfile.supportWhatsapp }
    Text("¿En qué podemos ayudarte?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    val reasons = listOf("Problema con un pedido", "Turnos", "Balance / pagos", "Documentación", "Problemas con la app", "Otro motivo")
    reasons.forEach { reason ->
        Card(
            Modifier.fillMaxWidth().padding(top = 7.dp).clickable(enabled = number.isNotBlank()) {
                openWhatsAppTo(context, number, "Hola, necesito soporte de Punto25 por: $reason.")
            }
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(reason, Modifier.weight(1f))
                Text("›", color = MaterialTheme.colorScheme.primary)
            }
        }
    }
    if (number.isBlank()) AssistCard("Administración todavía no configuró el WhatsApp de soporte.")
}

@Composable
private fun PaymentSummary(c: MandadosController, order: LocalOrder, rider: RiderProfile? = null) {
    val payment = c.paymentForOrder(order.id) ?: return
    val isTransfer = payment.channel == PaymentChannel.RIDER_TRANSFER
    val riderCanAccess = rider?.let { c.riderCanAccessTransfer(order.id, it.id) } == true
    var showProof by rememberSaveable(order.id, payment.proofUri) { mutableStateOf(false) }

    Spacer(Modifier.height(8.dp))
    SectionTitle("Pago")
    Text(if (isTransfer) "Transferencia · ${transferPaymentStatusLabel(payment.status)}" else "${payment.channel} · ${payment.status}")
    Text("Importe esperado: ${money(payment.expectedAmount)}")
    if (!payment.proofUri.isNullOrBlank()) Text("Comprobante adjunto")

    if (rider != null && isTransfer && riderCanAccess) {
        payment.proofUri?.takeIf { it.isNotBlank() }?.let { proofUri ->
  OutlinedButton(
      onClick = { showProof = !showProof },
      modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
  ) { Text(if (showProof) "OCULTAR COMPROBANTE" else "VER COMPROBANTE") }
  if (showProof) LocalDocumentImage(proofUri)
        }

        if (payment.status == PaymentStatus.PENDING) {
  AssistCard("Esperando que el Cliente informe la transferencia.")
        } else if (payment.status in transferAttentionStatuses) {
  if (c.config.paymentConfig.transferProofRequired && payment.proofUri.isNullOrBlank()) {
      AssistCard("Esperando comprobante. La configuración actual exige comprobante antes de confirmar la acreditación.")
  }
  Button(
      onClick = { c.confirmPaymentByRider(order.id, rider.id) },
      enabled = c.riderCanConfirmTransfer(order.id, rider.id),
      modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
  ) { Text("CONFIRMAR ACREDITACIÓN") }
  OutlinedButton(
      onClick = { c.reportPaymentProblem(order.id, rider.id) },
      enabled = c.riderCanReportTransfer(order.id, rider.id),
      modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
  ) { Text("NO VEO ACREDITADO") }
  if (payment.status == PaymentStatus.IN_REVIEW) {
      Text(
          "El pago sigue pendiente y marcado para revisión. Podés confirmarlo más adelante si verificás la acreditación.",
          style = MaterialTheme.typography.bodySmall,
          modifier = Modifier.padding(top = 4.dp)
      )
  }
        } else if (payment.status == PaymentStatus.CONFIRMED) {
  Text("Acreditación confirmada.", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateRangePicker(from: String?, to: String?, onFrom: (String?) -> Unit, onTo: (String?) -> Unit) {
    var pickingFrom by remember { mutableStateOf(false) }
    var pickingTo by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { pickingFrom = true }, modifier = Modifier.weight(1f)) { Text("Desde\n${from ?: "Sin límite"}") }
        OutlinedButton(onClick = { pickingTo = true }, modifier = Modifier.weight(1f)) { Text("Hasta\n${to ?: "Sin límite"}") }
    }
    if (pickingFrom) DatePickerPopup(from, { pickingFrom = false }) { onFrom(it); pickingFrom = false }
    if (pickingTo) DatePickerPopup(to, { pickingTo = false }) { onTo(it); pickingTo = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerPopup(current: String?, onDismiss: () -> Unit, onSelected: (String?) -> Unit) {
    val initial = current?.let(::parseDate)?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
    val state = rememberDatePickerState(initialSelectedDateMillis = initial)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val millis = state.selectedDateMillis
                val date = millis?.let { java.time.Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate() }
                onSelected(date?.format(dateFormatter))
            }) { Text("ACEPTAR") }
        },
        dismissButton = {
            TextButton(onClick = { onSelected(null) }) { Text("SIN LÍMITE") }
        }
    ) { DatePicker(state = state) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> EnumDropdown(
    label: String,
    value: T?,
    options: List<T>,
    allLabel: String,
    labelFor: (T) -> String,
    onSelect: (T?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value?.let(labelFor) ?: allLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth().padding(top = 7.dp)
        )
        ExposedDropdownMenu(expanded, { expanded = false }) {
            if (allLabel.isNotBlank()) DropdownMenuItem({ Text(allLabel) }, { onSelect(null); expanded = false })
            options.forEach { option ->
                DropdownMenuItem({ Text(labelFor(option)) }, { onSelect(option); expanded = false })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ZoneDropdown(c: MandadosController, label: String, current: String, allowBlank: Boolean = false, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val text = if (current.isBlank()) if (allowBlank) "Sin zona" else "Seleccionar" else c.zone(current)?.name ?: current
    ExposedDropdownMenuBox(expanded, { expanded = it }) {
        OutlinedTextField(
            value = text, onValueChange = {}, readOnly = true, label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth().padding(top = 7.dp)
        )
        ExposedDropdownMenu(expanded, { expanded = false }) {
            if (allowBlank) DropdownMenuItem({ Text("Sin zona") }, { onSelect(""); expanded = false })
            c.config.zones.forEach { z ->
                DropdownMenuItem({ Text(z.name) }, { onSelect(z.id); expanded = false })
            }
            DropdownMenuItem({ Text("No sé qué zona corresponde") }, { onSelect(UNKNOWN_ZONE_ID); expanded = false })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IntDropdown(label: String, value: Int, options: List<Int>, labelFor: (Int) -> String, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded, { expanded = it }) {
        OutlinedTextField(
            value = labelFor(value), onValueChange = {}, readOnly = true, label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth().padding(top = 7.dp)
        )
        ExposedDropdownMenu(expanded, { expanded = false }) {
            options.forEach { option -> DropdownMenuItem({ Text(labelFor(option)) }, { onSelect(option); expanded = false }) }
        }
    }
}

@Composable
private fun RiderOrderCard(c: MandadosController, order: LocalOrder, actions: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth().padding(top = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(order.id, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                StatusPill(order.status)
            }
            Text(order.createdAt)
            Text(categoryText(order.category), color = MaterialTheme.colorScheme.primary)
            Text(order.detail)
            c.deliveryDurationSeconds(order)?.let { ReportMetric("Tiempo", formatDuration(it)) }
            Spacer(Modifier.height(8.dp))
            actions()
        }
    }
}

@Composable
private fun RiderMenuCard(icon: String, title: String, subtitle: String, accent: Color, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(top = 9.dp).clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(icon, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = accent)
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
            Text("›", style = MaterialTheme.typography.headlineSmall, color = accent)
        }
    }
}

@Composable
private fun OrderTimeline(c: MandadosController, order: LocalOrder) {
    if (order.events.isEmpty()) {
        AssistCard("Pedido anterior al registro de eventos con segundos: se conserva la información histórica disponible sin inventar timestamps.")
        return
    }
    order.events.forEach { event ->
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
            Text("●", color = eventColor(event.type), modifier = Modifier.padding(end = 8.dp))
            Column {
                Text(eventLabel(event), fontWeight = FontWeight.SemiBold)
                Text(event.at)
                event.riderId?.let { rid -> Text(c.rider(rid)?.let { "Repartidor: ${it.name} ($rid)" } ?: "Repartidor: $rid") }
                event.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                event.actor?.let { Text("Actor: $it", style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}

@Composable
private fun MoneyBreakdown(order: LocalOrder) {
    ReportMetric("Servicio base", money(order.baseAmount))
    if ((order.prePickupAmount ?: 0) > 0) ReportMetric("Retiro previo", money(order.prePickupAmount))
    if ((order.rainAmount ?: 0) > 0) ReportMetric("Lluvia / barro", money(order.rainAmount))
    ReportMetric("Total", money(order.totalAmount), true)
}

@Composable
private fun StatusPill(status: OrderStatus) {
    val color = statusColor(status)
    Surface(color = color.copy(alpha = 0.15f), shape = MaterialTheme.shapes.small) {
        Text(opsStatusText(status), Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CategoryPill(category: ServiceCategory) {
    Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = .12f), shape = MaterialTheme.shapes.small) {
        Text(categoryText(category), Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RiderApprovalPill(status: RiderApprovalStatus) {
    val color = when (status) {
        RiderApprovalStatus.PENDING -> Color(0xFFD97706)
        RiderApprovalStatus.APPROVED -> Color(0xFF059669)
        RiderApprovalStatus.SUSPENDED -> Color(0xFFDC2626)
    }
    Surface(color = color.copy(alpha = .15f), shape = MaterialTheme.shapes.small) {
        Text(riderApprovalText(status), Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun OpsPage(title: String, onBack: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
            Text(title, Modifier.padding(14.dp), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), content = content)
        if (onBack != null) {
            HorizontalDivider(Modifier.padding(top = 8.dp))
            TextButton(onClick = onBack) { Text("← VOLVER", fontWeight = FontWeight.ExtraBold) }
        }
    }
}

@Composable
private fun AssistCard(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Text(text, Modifier.padding(12.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
}

@Composable
private fun ReportMetric(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, Modifier.weight(1f), fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
        Text(value, fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold)
    }
}

@Composable
private fun ActionButton(text: String, outlined: Boolean = false, action: () -> Unit) {
    Spacer(Modifier.height(6.dp))
    if (outlined) OutlinedButton(action, Modifier.fillMaxWidth()) { Text(text) }
    else Button(action, Modifier.fillMaxWidth()) { Text(text) }
}

@Composable
private fun RadioLine(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick)
        Text(text)
    }
}

@Composable
private fun SimpleField(label: String, value: String, keyboardType: KeyboardType = KeyboardType.Text, onValue: (String) -> Unit) {
    OutlinedTextField(
        value, onValue, label = { Text(label) }, keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 7.dp)
    )
}

@Composable
private fun ToggleSetting(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f)); Switch(checked, onChange)
    }
}

@Composable
private fun PermissionInfo(title: String, detail: String) {
    Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Column(Modifier.padding(12.dp)) { Text(title, fontWeight = FontWeight.Bold); Text(detail) }
    }
}

@Composable
private fun DocumentEditLine(label: String, uri: String?, onPick: () -> Unit, onClear: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(top = 7.dp)) {
        Column(Modifier.padding(10.dp)) {
            Text(label, fontWeight = FontWeight.SemiBold)
            Text(if (uri.isNullOrBlank()) "Sin foto" else "Foto cargada")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onPick) { Text(if (uri.isNullOrBlank()) "CARGAR" else "REEMPLAZAR") }
                if (!uri.isNullOrBlank()) TextButton(onClick = onClear) { Text("QUITAR") }
            }
        }
    }
}

private fun RiderDocuments.withUri(key: RiderDocumentKey, uri: String?): RiderDocuments = when (key) {
    RiderDocumentKey.DNI_FRONT -> copy(dniFrontUri = uri)
    RiderDocumentKey.DNI_BACK -> copy(dniBackUri = uri)
    RiderDocumentKey.MOTORCYCLE_PLATE -> copy(motorcyclePlateUri = uri)
    RiderDocumentKey.DRIVER_LICENSE_FRONT -> copy(driverLicenseFrontUri = uri)
    RiderDocumentKey.DRIVER_LICENSE_BACK -> copy(driverLicenseBackUri = uri)
    RiderDocumentKey.VEHICLE_CARD -> copy(vehicleCardUri = uri)
    RiderDocumentKey.INSURANCE_CARD -> copy(insuranceCardUri = uri)
}

@Composable
private fun TimeInputField(label: String, value: String, modifier: Modifier = Modifier, onValue: (String) -> Unit) {
    var field by remember(value) { mutableStateOf(TextFieldValue(value, selection = TextRange(value.length))) }
    OutlinedTextField(
        value = field,
        onValueChange = { incoming ->
            val digits = incoming.text.filter(Char::isDigit).take(4)
            val formatted = when {
                digits.length <= 2 -> digits
                else -> digits.substring(0, 2) + ":" + digits.substring(2)
            }
            field = TextFieldValue(formatted, selection = TextRange(formatted.length))
            onValue(formatted)
        },
        label = { Text(label) },
        placeholder = { Text("HH:mm") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = modifier.padding(top = 7.dp)
    )
}

private fun shiftEventText(type: ShiftEventType): String = when (type) {
    ShiftEventType.RIDER_JOINED -> "Repartidor se anotó"
    ShiftEventType.RIDER_CANCELLED -> "Repartidor canceló"
    ShiftEventType.ADMIN_ADDED -> "Admin agregó"
    ShiftEventType.ADMIN_REMOVED -> "Admin quitó"
}

private fun categoryText(v: ServiceCategory) = when (v) {
    ServiceCategory.PURCHASE -> "Compras"
    ServiceCategory.ERRAND -> "Encargos"
    ServiceCategory.PROCEDURE -> "Trámites"
    ServiceCategory.SHIPMENT -> "Envíos"
}

private fun opsStatusText(v: OrderStatus) = when (v) {
    OrderStatus.AWAITING_QUOTE -> "A confirmar"
    OrderStatus.PENDING -> "Nuevo"
    OrderStatus.ACCEPTED -> "Aceptado"
    OrderStatus.IN_PROGRESS -> "En curso"
    OrderStatus.COMPLETED -> "Completado"
    OrderStatus.REJECTED -> "Rechazado"
    OrderStatus.CANCELLED -> "Cancelado"
}

private fun statusColor(v: OrderStatus) = when (v) {
    OrderStatus.AWAITING_QUOTE -> Color(0xFFD97706)
    OrderStatus.PENDING -> Color(0xFFF97316)
    OrderStatus.ACCEPTED -> Color(0xFF3B82F6)
    OrderStatus.IN_PROGRESS -> Color(0xFF06B6D4)
    OrderStatus.COMPLETED -> Color(0xFF10B981)
    OrderStatus.REJECTED -> Color(0xFFEF4444)
    OrderStatus.CANCELLED -> Color(0xFF64748B)
}

private fun riderApprovalText(v: RiderApprovalStatus) = when (v) {
    RiderApprovalStatus.PENDING -> "Pendiente"
    RiderApprovalStatus.APPROVED -> "Habilitado"
    RiderApprovalStatus.SUSPENDED -> "Suspendido"
}

private fun documentLabel(v: RiderDocumentKey) = when (v) {
    RiderDocumentKey.DNI_FRONT -> "DNI · anverso"
    RiderDocumentKey.DNI_BACK -> "DNI · reverso"
    RiderDocumentKey.MOTORCYCLE_PLATE -> "Patente"
    RiderDocumentKey.DRIVER_LICENSE_FRONT -> "Licencia · anverso"
    RiderDocumentKey.DRIVER_LICENSE_BACK -> "Licencia · reverso"
    RiderDocumentKey.VEHICLE_CARD -> "Tarjeta verde/azul"
    RiderDocumentKey.INSURANCE_CARD -> "Seguro"
}

private fun documentReviewText(v: DocumentReviewStatus) = when (v) {
    DocumentReviewStatus.NOT_UPLOADED -> "Sin cargar"
    DocumentReviewStatus.PENDING -> "Pendiente"
    DocumentReviewStatus.APPROVED -> "Aprobado"
    DocumentReviewStatus.REJECTED -> "Rechazado"
}

private fun documentReviewColor(v: DocumentReviewStatus) = when (v) {
    DocumentReviewStatus.NOT_UPLOADED -> Color(0xFF64748B)
    DocumentReviewStatus.PENDING -> Color(0xFFD97706)
    DocumentReviewStatus.APPROVED -> Color(0xFF10B981)
    DocumentReviewStatus.REJECTED -> Color(0xFFEF4444)
}

private fun eventLabel(e: OrderEvent) = when (e.type) {
    OrderEventType.CREATED -> "Pedido nuevo"
    OrderEventType.RIDER_ASSIGNED -> "Repartidor asignado"
    OrderEventType.RIDER_UNASSIGNED -> "Repartidor desasignado"
    OrderEventType.ACCEPTED -> "Pedido aceptado"
    OrderEventType.IN_PROGRESS -> "En curso"
    OrderEventType.COMPLETED -> "Entregado"
    OrderEventType.REJECTED -> "Rechazado"
    OrderEventType.CANCELLED -> "Cancelado"
    OrderEventType.ORDER_EDITED -> "Pedido editado por Administración"
    OrderEventType.PAYMENT_UPDATED -> "Pago actualizado"
    OrderEventType.PAYMENT_DECLARED -> "Transferencia informada"
    OrderEventType.PAYMENT_PROOF_ATTACHED -> "Comprobante de transferencia adjunto"
    OrderEventType.PAYMENT_CONFIRMED -> "Pago confirmado por Repartidor"
    OrderEventType.PAYMENT_REVIEW_REQUESTED -> "Transferencia en revisión"
    OrderEventType.RATING_SUBMITTED -> "Calificación recibida"
}

private fun eventColor(type: OrderEventType) = when (type) {
    OrderEventType.CREATED -> Color(0xFFF97316)
    OrderEventType.RIDER_ASSIGNED, OrderEventType.RIDER_UNASSIGNED, OrderEventType.ORDER_EDITED -> Color(0xFF8B5CF6)
    OrderEventType.ACCEPTED -> Color(0xFF3B82F6)
    OrderEventType.IN_PROGRESS -> Color(0xFF06B6D4)
    OrderEventType.COMPLETED -> Color(0xFF10B981)
    OrderEventType.REJECTED -> Color(0xFFEF4444)
    OrderEventType.CANCELLED -> Color(0xFF64748B)
    OrderEventType.PAYMENT_UPDATED,
    OrderEventType.PAYMENT_DECLARED,
    OrderEventType.PAYMENT_PROOF_ATTACHED,
    OrderEventType.PAYMENT_CONFIRMED,
    OrderEventType.PAYMENT_REVIEW_REQUESTED -> Color(0xFFF59E0B)
    OrderEventType.RATING_SUBMITTED -> Color(0xFFEAB308)
}

private fun legalTypeText(type: LegalDocumentType) = when (type) {
    LegalDocumentType.TERMS -> "Términos y Condiciones"
    LegalDocumentType.PRIVACY -> "Política de Privacidad"
    LegalDocumentType.CANCELLATIONS -> "Cancelaciones, arrepentimiento y reembolsos"
    LegalDocumentType.LOCATION_PERMISSIONS -> "Ubicación y permisos"
    LegalDocumentType.RIDER_TERMS -> "Condiciones del Repartidor"
}

private fun defaultLegalDraft(type: LegalDocumentType, p: LegalProfile): String = when (type) {
    LegalDocumentType.TERMS -> "TÉRMINOS Y CONDICIONES DE ${p.businessName}\n\nResponsable: ${p.legalName.ifBlank { "[COMPLETAR]" }}\nÁrea de servicio: ${p.serviceArea}\n\n[BORRADOR EDITABLE. REVISAR JURÍDICAMENTE ANTES DE PUBLICAR.]"
    LegalDocumentType.PRIVACY -> "POLÍTICA DE PRIVACIDAD DE ${p.businessName}\n\nContacto de privacidad: ${p.privacyEmail.ifBlank { "[COMPLETAR]" }}\n\nPunto25 puede tratar nombre, teléfono, direcciones, pines de ubicación, historial de pedidos y datos necesarios para prestar el servicio.\n\n[BORRADOR EDITABLE. REVISAR JURÍDICAMENTE.]"
    LegalDocumentType.CANCELLATIONS -> "POLÍTICA DE CANCELACIONES Y REEMBOLSOS\n\n[BORRADOR EDITABLE]"
    LegalDocumentType.LOCATION_PERMISSIONS -> "UBICACIÓN Y PERMISOS\n\nLa ubicación se solicita de forma contextual para facilitar pines y mapas. El usuario puede usar dirección/pin manual cuando corresponda.\n\n[BORRADOR EDITABLE]"
    LegalDocumentType.RIDER_TERMS -> "CONDICIONES DEL REPARTIDOR\n\n[BORRADOR EDITABLE. REVISAR JURÍDICAMENTE.]"
}

internal fun defaultReportFrom(): String =
    LocalDate.now().withDayOfMonth(1).format(dateFormatter)

internal fun defaultReportTo(): String =
    LocalDate.now().format(dateFormatter)

internal fun filterOrdersForPeriod(items: List<LocalOrder>, fromText: String, toText: String): List<LocalOrder> {
    val from = parseDate(fromText)
    val to = parseDate(toText)
    return items.filter { order ->
        val eventDate = if (order.status == OrderStatus.COMPLETED) {
            order.events.lastOrNull { it.type == OrderEventType.COMPLETED }?.at?.let(::parseOrderTime)?.toLocalDate()
        } else null
        val date = eventDate ?: parseOrderTime(order.createdAt)?.toLocalDate()
        date != null && (from == null || !date.isBefore(from)) && (to == null || !date.isAfter(to))
    }
}

private fun parseOrderTime(value: String): LocalDateTime? =
    runCatching { LocalDateTime.parse(value, fullTimeFormatter) }.getOrNull()
        ?: runCatching { LocalDateTime.parse(value, oldTimeFormatter) }.getOrNull()

private fun parseDate(value: String): LocalDate? = runCatching { LocalDate.parse(value, dateFormatter) }.getOrNull()

private fun completionDate(order: LocalOrder): String? =
    order.events.lastOrNull { it.type == OrderEventType.COMPLETED }?.at?.let(::parseOrderTime)?.toLocalDate()?.format(dateFormatter)

private fun formatDuration(seconds: Long): String {
    val s = seconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return "%02d:%02d:%02d".format(h, m, sec)
}

private fun money(v: Int?): String = if (v == null) "A confirmar" else "$" + "%,d".format(v).replace(',', '.')

private fun nextDateForDay(today: LocalDate, day: Int): LocalDate {
    val target = DayOfWeek.of(day.coerceIn(1, 7))
    var date = today
    while (date.dayOfWeek != target) date = date.plusDays(1)
    return date
}

private fun dayName(day: Int): String = when (day) {
    1 -> "Lunes"; 2 -> "Martes"; 3 -> "Miércoles"; 4 -> "Jueves"; 5 -> "Viernes"; 6 -> "Sábado"; else -> "Domingo"
}

internal fun openPdfUri(context: android.content.Context, uriString: String) {
    runCatching {
        val uri = Uri.parse(uriString)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }.onFailure {
        Toast.makeText(context, "No hay una aplicación disponible para abrir este PDF.", Toast.LENGTH_LONG).show()
    }
}

private fun openWhatsAppTo(context: android.content.Context, rawPhone: String, message: String) {
    val digits = rawPhone.filter(Char::isDigit)
    val normalized = when {
        digits.startsWith("549") -> digits
        digits.startsWith("54") -> digits
        digits.length == 10 -> "549$digits"
        else -> digits
    }
    val uri = Uri.parse("https://wa.me/$normalized?text=${Uri.encode(message)}")
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
}

private fun dial(context: android.content.Context, rawPhone: String) {
    val digits = rawPhone.filter(Char::isDigit)
    runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$digits"))) }
}


@Composable
internal fun CustomerProfileScreen(c: MandadosController, onBack: () -> Unit, onSupport: () -> Unit) {
    OpsPage("Mi perfil · Punto25", onBack) {
        val customer = c.customer
        if (customer != null) {
            Text(customer.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(customer.displayPhone)
            Text(customer.id, style = MaterialTheme.typography.bodySmall)
            Text(customer.locality, color = MaterialTheme.colorScheme.primary)
        }

        SectionTitle("Apariencia")
        RadioLine("Usar tema del sistema", c.config.themeMode == ThemeMode.SYSTEM) {
            c.updateConfig(c.config.copy(themeMode = ThemeMode.SYSTEM))
        }
        RadioLine("Tema claro", c.config.themeMode == ThemeMode.LIGHT) {
            c.updateConfig(c.config.copy(themeMode = ThemeMode.LIGHT))
        }
        RadioLine("Tema oscuro", c.config.themeMode == ThemeMode.DARK) {
            c.updateConfig(c.config.copy(themeMode = ThemeMode.DARK))
        }

        SectionTitle("Privacidad y permisos")
        PermissionInfo("Ubicación", "Se solicita sólo cuando elegís usar tu ubicación. Podés escribir la dirección y mover el pin manualmente.")
        PermissionInfo("Notificaciones", "Se activarán con FCM para avisarte cambios importantes de tus pedidos.")
        PermissionInfo("Fotos", "Para comprobantes/documentos se utiliza el selector seguro de Android.")

        val context = LocalContext.current
        OutlinedButton(
            onClick = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:${context.packageName}")
                    )
                )
            },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) { Text("ABRIR PERMISOS DEL SISTEMA") }

        SectionTitle("Legal")
        LegalDocumentType.entries.filter { it != LegalDocumentType.RIDER_TERMS }.forEach { type ->
            val doc = c.publishedLegalDocument(type)
            Card(Modifier.fillMaxWidth().padding(top = 7.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(legalTypeText(type), fontWeight = FontWeight.Bold)
                    Text(doc?.let { "Versión ${it.version} · vigente ${it.effectiveDate}" } ?: "Todavía no publicado", style = MaterialTheme.typography.bodySmall)
                    if (doc?.fileUri != null) {
                        OutlinedButton(
                            onClick = { openPdfUri(context, doc.fileUri) },
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                        ) { Text("VER DOCUMENTO") }
                    }
                }
            }
        }

        OutlinedButton(onClick = onSupport, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Text("AYUDA Y SOPORTE")
        }
    }
}

@Composable
internal fun CustomerSupportScreen(c: MandadosController, onBack: () -> Unit) {
    OpsPage("Soporte · Punto25", onBack) {
        SupportScreen(c)
    }
}

@Composable
internal fun Punto25RatingDialog(c: MandadosController, order: LocalOrder) {
    val rider = c.rider(order.assignedRiderId)
    var stars by rememberSaveable(order.id) { mutableStateOf(0) }
    var comment by rememberSaveable(order.id) { mutableStateOf("") }
    var selectedTags by remember(order.id) { mutableStateOf(setOf<String>()) }
    var tip by rememberSaveable(order.id) { mutableStateOf(0) }
    var customTip by rememberSaveable(order.id) { mutableStateOf("") }
    val suggestions = c.config.paymentConfig.tipSuggestions

    AlertDialog(
        onDismissRequest = {},
        title = { Text("Pedido entregado") },
        text = {
            Column(Modifier.heightIn(max = 620.dp).verticalScroll(rememberScrollState())) {
                Text(order.id, style = MaterialTheme.typography.bodySmall)
                Text(
                    "¿Cómo fue tu experiencia con ${rider?.name ?: "el Repartidor"}?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text("La calificación es obligatoria para cerrar el pedido.", style = MaterialTheme.typography.bodySmall)

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    (1..5).forEach { value ->
                        Text(
                            if (value <= stars) "★" else "☆",
                            style = MaterialTheme.typography.headlineMedium,
                            color = if (value <= stars) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable { stars = value }
                        )
                    }
                }

                val tags = if (stars in 1..2) {
                    listOf("Demora", "Trato", "Problema con el pedido", "No siguió indicaciones")
                } else {
                    listOf("Amable", "Rápido", "Buena comunicación", "Cuidadoso")
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    tags.forEach { tag ->
                        FilterChip(
                            selected = tag in selectedTags,
                            onClick = {
                                selectedTags = if (tag in selectedTags) selectedTags - tag else selectedTags + tag
                            },
                            label = { Text(tag) }
                        )
                    }
                }

                OutlinedTextField(
                    comment,
                    { comment = it },
                    label = { Text("Comentario (opcional)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                if (c.config.paymentConfig.tipsEnabled) {
                    SectionTitle("Propina opcional")
                    FilterChip(tip == 0 && customTip.isBlank(), {
                        tip = 0; customTip = ""
                    }, { Text("Sin propina") })
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        suggestions.forEach { amount ->
                            FilterChip(
                                selected = tip == amount,
                                onClick = { tip = amount; customTip = "" },
                                label = { Text(money(amount)) }
                            )
                        }
                    }
                    if (c.config.paymentConfig.customTipEnabled) {
                        OutlinedTextField(
                            customTip,
                            {
                                customTip = it.filter(Char::isDigit).take(7)
                                tip = customTip.toIntOrNull() ?: 0
                            },
                            label = { Text("Otro importe") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth().padding(top = 7.dp)
                        )
                    }
                    if (tip > 0) {
                        AssistCard("La propina queda registrada como opcional. Hasta conectar la pasarela, su recepción deberá confirmarse por el medio de pago utilizado.")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { c.submitRating(order.id, stars, selectedTags.toList(), comment, tip) },
                enabled = stars in 1..5
            ) { Text("ENVIAR CALIFICACIÓN") }
        }
    )
}
