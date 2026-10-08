package ar.com.mandados.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
internal fun AdminOrderZoneOverridesPanel(
    c: MandadosController,
    order: LocalOrder
) {
    var editingPoint by remember(order.id) { mutableStateOf<OrderZonePoint?>(null) }
    val editable = order.operationMode == OperationMode.MULTI_RIDER &&
        order.status !in setOf(OrderStatus.COMPLETED, OrderStatus.CANCELLED, OrderStatus.REJECTED)

    Spacer(Modifier.height(10.dp))
    Text("ZONAS APLICADAS", fontWeight = FontWeight.Bold)
    applicableOrderZonePoints(order).forEach { point ->
        val declared = declaredOrderZoneLabel(order, point, c.config)
        val applied = appliedOrderZoneLabel(order, point, c.config)
        val differs = order.zoneOverrides.containsKey(point)
        Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text(orderZonePointLabel(point), fontWeight = FontWeight.SemiBold)
            Text("Zona declarada: $declared", style = MaterialTheme.typography.bodySmall)
            Text(
                "Zona aplicada: $applied",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (differs) FontWeight.Bold else FontWeight.Normal
            )
            OutlinedButton(
                onClick = { editingPoint = point },
                enabled = editable,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            ) {
                Text(if (differs) "CAMBIAR / RESTAURAR ZONA" else "CORREGIR ZONA")
            }
        }
    }
    if (!editable) {
        Text(
            "Este pedido no admite correcciones de zona en su estado actual.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 6.dp)
        )
    }

    editingPoint?.let { point ->
        OrderZoneOverrideDialog(
            c = c,
            order = order,
            point = point,
            onClose = { editingPoint = null }
        )
    }
}

@Composable
private fun OrderZoneOverrideDialog(
    c: MandadosController,
    order: LocalOrder,
    point: OrderZonePoint,
    onClose: () -> Unit
) {
    val existing = order.zoneOverrides[point]
    val initialMode = when (existing?.source) {
        OrderZoneOverrideSource.CATALOG -> "CATALOG"
        OrderZoneOverrideSource.AD_HOC -> "AD_HOC"
        null -> "DECLARED"
    }
    val validCatalogZones = c.config.zones.filter { it.enabled && it.price > 0 && it.name.isNotBlank() }
    val initialCatalogId = existing?.catalogZoneId.orEmpty()
        .ifBlank { validCatalogZones.firstOrNull()?.id.orEmpty() }
    val initialAdHocName = existing?.takeIf { it.source == OrderZoneOverrideSource.AD_HOC }?.name.orEmpty()
    val initialAdHocPrice = existing?.takeIf { it.source == OrderZoneOverrideSource.AD_HOC }?.price?.toString().orEmpty()

    var mode by rememberSaveable(order.id, point.name) { mutableStateOf(initialMode) }
    var catalogId by rememberSaveable(order.id, point.name) { mutableStateOf(initialCatalogId) }
    var adHocName by rememberSaveable(order.id, point.name) { mutableStateOf(initialAdHocName) }
    var adHocPrice by rememberSaveable(order.id, point.name) { mutableStateOf(initialAdHocPrice) }
    var reason by rememberSaveable(order.id, point.name) { mutableStateOf("") }
    var catalogExpanded by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }

    val dirty = mode != initialMode ||
        catalogId != initialCatalogId ||
        adHocName != initialAdHocName ||
        adHocPrice != initialAdHocPrice ||
        reason.isNotEmpty()

    val selection: OrderZoneOverrideSelection = when (mode) {
        "CATALOG" -> OrderZoneOverrideSelection.Catalog(catalogId)
        "AD_HOC" -> OrderZoneOverrideSelection.AdHoc(adHocName, adHocPrice.toIntOrNull() ?: 0)
        else -> OrderZoneOverrideSelection.Declared
    }
    val preview = c.previewOrderZoneOverride(order.id, point, selection)

    fun requestDismiss(source: PendingEditDismissSource) {
        when (pendingEditDismissDecision(source, dirty)) {
            PendingEditDismissDecision.KEEP_OPEN -> Unit
            PendingEditDismissDecision.CLOSE -> onClose()
            PendingEditDismissDecision.CONFIRM_DISCARD -> confirmDiscard = true
        }
    }

    Punto25AlertDialog(
        onDismissRequest = { requestDismiss(PendingEditDismissSource.BACK) },
        title = { Text("Corregir zona aplicada · ${orderZonePointLabel(point)}") },
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Zona declarada: ${declaredOrderZoneLabel(order, point, c.config)}")
                Text("Zona aplicada actual: ${appliedOrderZoneLabel(order, point, c.config)}")

                ZoneOverrideModeRow("DECLARED", "Restaurar zona declarada", mode) { mode = "DECLARED" }
                ZoneOverrideModeRow("CATALOG", "Usar zona del catálogo", mode) { mode = "CATALOG" }
                ZoneOverrideModeRow("AD_HOC", "Zona circunstancial", mode) { mode = "AD_HOC" }

                if (mode == "CATALOG") {
                    val selected = c.config.zones.firstOrNull { it.id == catalogId }
                    Column {
                        OutlinedButton(
                            onClick = { catalogExpanded = true },
                            enabled = validCatalogZones.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                selected?.let { "${it.name} · ${formatZoneOverrideMoney(it.price)}" }
                                    ?: "SELECCIONAR ZONA"
                            )
                        }
                        DropdownMenu(
                            expanded = catalogExpanded,
                            onDismissRequest = { catalogExpanded = false }
                        ) {
                            validCatalogZones.forEach { zone ->
                                DropdownMenuItem(
                                    text = { Text("${zone.name} · ${formatZoneOverrideMoney(zone.price)}") },
                                    onClick = {
                                        catalogId = zone.id
                                        catalogExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                if (mode == "AD_HOC") {
                    OutlinedTextField(
                        value = adHocName,
                        onValueChange = { adHocName = it },
                        label = { Text("Nombre de zona circunstancial") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = adHocPrice,
                        onValueChange = { adHocPrice = it.filter(Char::isDigit) },
                        label = { Text("Tarifa") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Motivo obligatorio") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Total actual: ${formatZoneOverrideMoney(order.totalAmount)}")
                Text("Total recalculado: ${formatZoneOverrideMoney(preview?.candidateTotal)}")
                preview?.issue?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                saveError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = reason.trim().isNotBlank() && preview?.allowed == true,
                onClick = {
                    saveError = null
                    if (c.applyOrderZoneOverride(order.id, point, selection, reason)) {
                        onClose()
                    } else {
                        saveError = "No se pudo aplicar la corrección. Revisá el estado del pedido y del pago."
                    }
                }
            ) {
                Text("APLICAR CORRECCIÓN")
            }
        },
        dismissButton = {
            TextButton(onClick = { requestDismiss(PendingEditDismissSource.CANCEL) }) {
                Text("CANCELAR")
            }
        }
    )

    if (confirmDiscard) {
        DiscardChangesDialog(
            onKeepEditing = { confirmDiscard = false },
            onDiscard = {
                confirmDiscard = false
                onClose()
            }
        )
    }
}

@Composable
private fun ZoneOverrideModeRow(
    value: String,
    label: String,
    selected: String,
    onSelect: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        RadioButton(selected = selected == value, onClick = onSelect)
        Text(label)
    }
}

private fun formatZoneOverrideMoney(value: Int?): String =
    value?.let { "$" + "%,d".format(it).replace(',', '.') } ?: "A confirmar"
