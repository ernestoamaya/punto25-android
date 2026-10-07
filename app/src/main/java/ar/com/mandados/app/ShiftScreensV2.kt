package ar.com.mandados.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

@Composable
internal fun AdminShiftsV2Screen(c: MandadosController, onBack: () -> Unit) {
    var editingRuleId by rememberSaveable { mutableStateOf<String?>(null) }
    var creatingRule by rememberSaveable { mutableStateOf(false) }
    var deletingRuleId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingShiftId by rememberSaveable { mutableStateOf<String?>(null) }
    var suppressShiftId by rememberSaveable { mutableStateOf<String?>(null) }
    var addRiderShiftId by rememberSaveable { mutableStateOf<String?>(null) }
    var removeReservationId by rememberSaveable { mutableStateOf<String?>(null) }
    var message by rememberSaveable { mutableStateOf("") }
    val expandedDates = remember { mutableStateMapOf<String, Boolean>() }

    val today = LocalDate.now()
    var generationFrom by rememberSaveable { mutableStateOf(ShiftSchedulePolicy.toIsoDate(today)) }
    var generationTo by rememberSaveable { mutableStateOf(ShiftSchedulePolicy.toIsoDate(today.plusDays(7))) }
    var fromRuleId by rememberSaveable { mutableStateOf<String?>(null) }
    var toRuleId by rememberSaveable { mutableStateOf<String?>(null) }
    var pickingGenerationFrom by rememberSaveable { mutableStateOf(false) }
    var pickingGenerationTo by rememberSaveable { mutableStateOf(false) }
    var preview by remember { mutableStateOf<ShiftGenerationPreview?>(null) }

    var listFrom by rememberSaveable { mutableStateOf(ShiftSchedulePolicy.toIsoDate(today.minusDays(7))) }
    var listTo by rememberSaveable { mutableStateOf(ShiftSchedulePolicy.toIsoDate(today.plusDays(21))) }
    var pickingListFrom by rememberSaveable { mutableStateOf(false) }
    var pickingListTo by rememberSaveable { mutableStateOf(false) }

    val generationFromDate = ShiftSchedulePolicy.parseIsoDate(generationFrom) ?: today
    val generationToDate = ShiftSchedulePolicy.parseIsoDate(generationTo) ?: today
    val startRules = ShiftSchedulePolicy.applicableRules(generationFromDate, c.shiftRules)
    val endRules = ShiftSchedulePolicy.applicableRules(generationToDate, c.shiftRules)
    if (fromRuleId != null && startRules.none { it.id == fromRuleId }) fromRuleId = null
    if (toRuleId != null && endRules.none { it.id == toRuleId }) toRuleId = null

    val from = ShiftSchedulePolicy.parseIsoDate(listFrom) ?: today.minusDays(7)
    val to = ShiftSchedulePolicy.parseIsoDate(listTo) ?: today.plusDays(21)
    val grouped = c.concreteShifts(from, to).groupBy { it.serviceDate }.toSortedMap()

    ShiftPage("Turnos · Administración", onBack) {
        if (!c.isShiftSubsystemReady()) {
            ShiftAssistCard("El almacenamiento de Turnos no está disponible. No se permiten operaciones hasta recuperar un estado consistente.")
            return@ShiftPage
        }

        ShiftSectionTitle("CONFIGURACIÓN DE CUPOS Y HORARIOS DE TURNOS")
        Text(
            "Estas reglas sólo generan turnos futuros. No son turnos reservables y sus cambios no modifican fechas ya generadas.",
            style = MaterialTheme.typography.bodySmall
        )
        Button(
            onClick = { creatingRule = true; editingRuleId = null },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) { Text("+ AGREGAR REGLA") }
        if (c.shiftRules.isEmpty()) ShiftAssistCard("Todavía no hay reglas de generación configuradas.")
        c.shiftRules.sortedWith(compareBy<ShiftGenerationRule> { it.startMinute }.thenBy { it.id }).forEach { rule ->
            Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(rule.daysOfWeek.sorted().joinToString(" · ") { shiftDayName(it).take(3) }, fontWeight = FontWeight.Bold)
                    Text("${ShiftSchedulePolicy.formatMinute(rule.startMinute)}–${ShiftSchedulePolicy.formatMinute(rule.endMinute)} · Cupo ${rule.capacity}")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { editingRuleId = rule.id }, modifier = Modifier.weight(1f)) { Text("EDITAR") }
                        OutlinedButton(
                            onClick = { deletingRuleId = rule.id },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFB3261E))
                        ) { Text("QUITAR REGLA") }
                    }
                }
            }
        }

        ShiftSectionTitle("GENERAR TURNOS SEMANALES")
        Text("Elegí el período. Los límites de turno son opcionales y muestran sólo reglas aplicables a cada fecha.", style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { pickingGenerationFrom = true }, modifier = Modifier.weight(1f)) {
                Text("Desde\n${ShiftSchedulePolicy.displayDate(generationFrom) ?: generationFrom}")
            }
            OutlinedButton(onClick = { pickingGenerationTo = true }, modifier = Modifier.weight(1f)) {
                Text("Hasta\n${ShiftSchedulePolicy.displayDate(generationTo) ?: generationTo}")
            }
        }
        ShiftRuleLimitDropdown("Desde qué turno (opcional)", fromRuleId, startRules, "Desde el primer turno") { fromRuleId = it }
        ShiftRuleLimitDropdown("Hasta qué turno (opcional)", toRuleId, endRules, "Hasta el último turno") { toRuleId = it }
        Button(
            onClick = {
                message = ""
                preview = c.previewShiftGeneration(ShiftGenerationRequest(generationFrom, generationTo, fromRuleId, toRuleId))
            },
            enabled = c.shiftRules.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) { Text("PREVISUALIZAR") }
        if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp))

        ShiftSectionTitle("TURNOS CONCRETOS POR FECHA")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { pickingListFrom = true }, modifier = Modifier.weight(1f)) {
                Text("Desde\n${ShiftSchedulePolicy.displayDate(listFrom) ?: listFrom}")
            }
            OutlinedButton(onClick = { pickingListTo = true }, modifier = Modifier.weight(1f)) {
                Text("Hasta\n${ShiftSchedulePolicy.displayDate(listTo) ?: listTo}")
            }
        }
        if (grouped.isEmpty()) ShiftAssistCard("No hay turnos concretos en el período seleccionado.")
        grouped.forEach { (dateIso, shifts) ->
            val date = ShiftSchedulePolicy.parseIsoDate(dateIso)
            Card(
                Modifier.fillMaxWidth().padding(top = 8.dp).clickable { expandedDates[dateIso] = !(expandedDates[dateIso] ?: false) },
                colors = CardDefaults.cardColors(
                    containerColor = if (date?.isBefore(today) == true) MaterialTheme.colorScheme.errorContainer.copy(alpha = .40f)
                    else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(ShiftSchedulePolicy.displayDate(dateIso) ?: dateIso, fontWeight = FontWeight.Bold)
                        Text("${date?.dayOfWeek?.value?.let(::shiftDayName).orEmpty()} · ${shifts.size} turno(s)", style = MaterialTheme.typography.bodySmall)
                    }
                    Text(if (expandedDates[dateIso] == true) "⌃" else "⌄", style = MaterialTheme.typography.titleLarge)
                }
            }
            if (expandedDates[dateIso] == true) {
                shifts.sortedBy { it.startMinute }.forEach { shift ->
                    val window = c.shiftWindow(shift)
                    val now = LocalDateTime.now()
                    val finished = window?.second?.let { !it.isAfter(now) } == true
                    val active = shift.enabled && window?.let { !now.isBefore(it.first) && now.isBefore(it.second) } == true
                    val reservations = c.concreteShiftReservations.filter {
                        it.concreteShiftId == shift.id && it.status == ShiftReservationStatus.RESERVED
                    }
                    Card(Modifier.fillMaxWidth().padding(start = 12.dp, top = 6.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("${ShiftSchedulePolicy.formatMinute(shift.startMinute)}–${ShiftSchedulePolicy.formatMinute(shift.endMinute)}", fontWeight = FontWeight.Bold)
                                    Text("Cupo ${reservations.size}/${shift.capacity}${if (shift.isException) " · Excepción" else ""}", style = MaterialTheme.typography.bodySmall)
                                }
                                Text(
                                    when {
                                        !shift.enabled -> "DESHABILITADO"
                                        finished -> "FINALIZADO"
                                        active -> "ACTIVO"
                                        else -> "FUTURO"
                                    },
                                    color = when {
                                        !shift.enabled || finished -> MaterialTheme.colorScheme.error
                                        active -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                            if (window?.let { it.first.toLocalDate() != it.second.toLocalDate() } == true) {
                                Text("Finaliza al día siguiente", style = MaterialTheme.typography.bodySmall)
                            }
                            reservations.forEach { reservation ->
                                Row(Modifier.fillMaxWidth().padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(c.rider(reservation.riderId)?.let { "${it.name} · ${it.id}" } ?: reservation.riderId, Modifier.weight(1f))
                                    if (!finished) TextButton(onClick = { removeReservationId = reservation.id }) { Text("QUITAR") }
                                }
                            }
                            if (shift.enabled && !finished) {
                                OutlinedButton(
                                    onClick = { addRiderShiftId = shift.id },
                                    enabled = reservations.size < shift.capacity,
                                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                                ) { Text("+ AGREGAR REPARTIDOR") }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(onClick = { editingShiftId = shift.id }, modifier = Modifier.weight(1f)) { Text("EDITAR") }
                                OutlinedButton(
                                    onClick = {
                                        if (shift.enabled) suppressShiftId = shift.id
                                        else {
                                            val err = c.updateConcreteShift(
                                                shift.id,
                                                ShiftSchedulePolicy.formatMinute(shift.startMinute),
                                                ShiftSchedulePolicy.formatMinute(shift.endMinute),
                                                shift.capacity,
                                                true
                                            )
                                            message = err ?: "Turno habilitado."
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = if (shift.enabled) Color(0xFFB3261E) else MaterialTheme.colorScheme.primary)
                                ) { Text(if (shift.enabled) "DESHABILITAR" else "HABILITAR") }
                            }
                        }
                    }
                }
            }
        }

        ShiftSectionTitle("Auditoría reciente")
        c.concreteShiftAuditEvents.takeLast(20).reversed().forEach { event ->
            val shift = c.concreteShift(event.concreteShiftId)
            Text(
                "${event.at} · ${shiftEventTextV2(event.type)} · ${c.rider(event.riderId)?.name ?: event.riderId} · " +
                    "${ShiftSchedulePolicy.displayDate(event.serviceDate) ?: event.serviceDate} ${shift?.let { ShiftSchedulePolicy.formatMinute(it.startMinute) }.orEmpty()}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }
    }

    if (creatingRule || editingRuleId != null) {
        val rule = editingRuleId?.let(c::shiftRule)
        ShiftRuleEditorDialog(c, rule) {
            creatingRule = false
            editingRuleId = null
        }
    }

    deletingRuleId?.let { id ->
        Punto25AlertDialog(
            onDismissRequest = { deletingRuleId = null },
            title = { Text("Quitar regla") },
            text = { Text("La regla dejará de generar fechas futuras. Los turnos concretos ya creados y sus reservas se conservan.") },
            confirmButton = {
                TextButton(onClick = {
                    message = if (c.deleteShiftRule(id)) "Regla eliminada. Los turnos ya generados se conservaron." else "No se pudo eliminar la regla."
                    deletingRuleId = null
                }) { Text("QUITAR REGLA") }
            },
            dismissButton = { TextButton(onClick = { deletingRuleId = null }) { Text("CANCELAR") } }
        )
    }

    editingShiftId?.let { id -> c.concreteShift(id)?.let { shift ->
        ConcreteShiftEditorDialog(c, shift) { editingShiftId = null }
    } }

    suppressShiftId?.let { id -> c.concreteShift(id)?.let { shift ->
        Punto25AlertDialog(
            onDismissRequest = { suppressShiftId = null },
            title = { Text("Deshabilitar turno") },
            text = { Text("El turno quedará como excepción de esta fecha y una generación repetida no lo recreará. Si tiene reservas activas, la operación será rechazada.") },
            confirmButton = {
                TextButton(onClick = {
                    val err = c.updateConcreteShift(
                        shift.id,
                        ShiftSchedulePolicy.formatMinute(shift.startMinute),
                        ShiftSchedulePolicy.formatMinute(shift.endMinute),
                        shift.capacity,
                        false
                    )
                    message = err ?: "Turno deshabilitado y preservado como excepción."
                    suppressShiftId = null
                }) { Text("DESHABILITAR") }
            },
            dismissButton = { TextButton(onClick = { suppressShiftId = null }) { Text("CANCELAR") } }
        )
    } }

    addRiderShiftId?.let { shiftId -> c.concreteShift(shiftId)?.let { shift ->
        val existing = c.concreteShiftReservations.filter {
            it.concreteShiftId == shift.id && it.status == ShiftReservationStatus.RESERVED
        }.map { it.riderId }.toSet()
        Punto25AlertDialog(
            onDismissRequest = { addRiderShiftId = null },
            title = { Text("Agregar Repartidor") },
            text = {
                Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                    c.riders.filter { it.active && it.approvalStatus == RiderApprovalStatus.APPROVED && it.id !in existing }.forEach { rider ->
                        TextButton(
                            onClick = { if (c.adminAddRiderToShift(rider.id, shift.id)) addRiderShiftId = null },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("${rider.name} · ${rider.id}") }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { addRiderShiftId = null }) { Text("CERRAR") } }
        )
    } }

    removeReservationId?.let { id ->
        Punto25AlertDialog(
            onDismissRequest = { removeReservationId = null },
            title = { Text("Quitar Repartidor del turno") },
            text = { Text("¿Confirmás que querés quitar al Repartidor de este turno concreto? Si estaba disponible y ya no tiene otro turno activo, pasará a No disponible.") },
            confirmButton = {
                TextButton(onClick = {
                    c.adminRemoveRiderFromConcreteShift(id)
                    removeReservationId = null
                }) { Text("QUITAR") }
            },
            dismissButton = { TextButton(onClick = { removeReservationId = null }) { Text("CANCELAR") } }
        )
    }

    preview?.let { currentPreview ->
        ShiftGenerationPreviewDialog(
            preview = currentPreview,
            onDismiss = { preview = null },
            onConfirm = {
                val result = c.confirmShiftGeneration(currentPreview.request)
                preview = null
                message = when {
                    result.error != null -> result.error
                    result.conflicts.isNotEmpty() -> "Generación rechazada: ${result.conflicts.first()}"
                    result.newCount == 0 -> "El período ya estaba materializado. No se escribieron duplicados."
                    else -> "Se generaron ${result.newCount} turno(s) concreto(s)."
                }
            }
        )
    }

    if (pickingGenerationFrom) RequiredShiftDatePicker(generationFrom, { pickingGenerationFrom = false }) {
        generationFrom = it; fromRuleId = null; preview = null; pickingGenerationFrom = false
    }
    if (pickingGenerationTo) RequiredShiftDatePicker(generationTo, { pickingGenerationTo = false }) {
        generationTo = it; toRuleId = null; preview = null; pickingGenerationTo = false
    }
    if (pickingListFrom) RequiredShiftDatePicker(listFrom, { pickingListFrom = false }) { listFrom = it; pickingListFrom = false }
    if (pickingListTo) RequiredShiftDatePicker(listTo, { pickingListTo = false }) { listTo = it; pickingListTo = false }
}

@Composable
internal fun RiderShiftsV2(c: MandadosController, rider: RiderProfile) {
    val today = LocalDate.now()
    val now = LocalDateTime.now()
    var confirmReservationId by rememberSaveable { mutableStateOf<String?>(null) }
    val all = c.concreteShifts(today.minusDays(1), today.plusDays(8))
    val shifts = all.filter { shift ->
        shift.enabled || c.concreteShiftReservations.any {
            it.riderId == rider.id && it.concreteShiftId == shift.id && it.status == ShiftReservationStatus.RESERVED
        }
    }

    if (shifts.isEmpty()) {
        ShiftAssistCard("Administración todavía no generó turnos concretos para los próximos días.")
        return
    }

    shifts.forEach { shift ->
        val reservation = c.concreteShiftReservations.firstOrNull { it.riderId == rider.id && it.concreteShiftId == shift.id }
        val occupied = c.concreteShiftReservations.count { it.concreteShiftId == shift.id && it.status == ShiftReservationStatus.RESERVED }
        val window = c.shiftWindow(shift)
        val finished = window?.second?.let { !it.isAfter(now) } == true
        val active = shift.enabled && window?.let { !now.isBefore(it.first) && now.isBefore(it.second) } == true
        val container = when {
            finished -> MaterialTheme.colorScheme.errorContainer.copy(alpha = .58f)
            active -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceVariant
        }
        Card(Modifier.fillMaxWidth().padding(top = 8.dp), colors = CardDefaults.cardColors(containerColor = container)) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        val date = ShiftSchedulePolicy.parseIsoDate(shift.serviceDate)
                        Text("${ShiftSchedulePolicy.displayDate(shift.serviceDate) ?: shift.serviceDate} · ${date?.dayOfWeek?.value?.let(::shiftDayName).orEmpty()}", fontWeight = FontWeight.Bold)
                        Text("${ShiftSchedulePolicy.formatMinute(shift.startMinute)}–${ShiftSchedulePolicy.formatMinute(shift.endMinute)}", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        when { finished -> "FINALIZADO"; active -> "ACTIVO"; else -> "PRÓXIMO" },
                        color = when { finished -> MaterialTheme.colorScheme.error; active -> MaterialTheme.colorScheme.primary; else -> MaterialTheme.colorScheme.onSurfaceVariant },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (window?.let { it.first.toLocalDate() != it.second.toLocalDate() } == true) Text("Finaliza al día siguiente", style = MaterialTheme.typography.bodySmall)
                Text("Cupo: $occupied/${shift.capacity}")
                when {
                    reservation?.status == ShiftReservationStatus.RESERVED -> {
                        Text("Turno confirmado · anotado ${reservation.joinedAt}", color = MaterialTheme.colorScheme.primary)
                        if (!finished && c.canCancelConcreteShift(reservation)) {
                            OutlinedButton(
                                onClick = { confirmReservationId = reservation.id },
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFB85F5F))
                            ) { Text("CANCELAR INSCRIPCIÓN") }
                            Text("Si cancelás, deberás esperar 15 minutos para volver a anotarte. Una segunda cancelación en este turno bloquea una nueva reinscripción.", style = MaterialTheme.typography.bodySmall)
                        } else if (!finished && !active) {
                            ShiftAssistCard("La ventana de cancelación de 15 minutos finalizó. Cualquier excepción debe gestionarla Administración.")
                        }
                    }
                    finished -> Text("Este turno ya finalizó.", style = MaterialTheme.typography.bodySmall)
                    !shift.enabled -> ShiftAssistCard("Este turno fue deshabilitado por Administración.")
                    reservation?.blockedRejoin == true -> ShiftAssistCard("Cancelaste dos veces este turno. Ya no podés volver a anotarte; Administración puede agregarte manualmente si corresponde.")
                    reservation?.status == ShiftReservationStatus.CANCELLED && c.minutesUntilRiderCanRejoin(reservation) > 0 -> {
                        ShiftAssistCard("Turno cancelado. Podrás volver a anotarte en aproximadamente ${c.minutesUntilRiderCanRejoin(reservation)} minuto(s).")
                    }
                    occupied >= shift.capacity -> ShiftAssistCard("Turno completo.")
                    else -> Button(
                        onClick = { c.reserveShift(rider.id, shift.id) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if ((reservation?.cancellationCount ?: 0) > 0) "VOLVER A ANOTARME" else "ANOTARME") }
                }
            }
        }
    }

    confirmReservationId?.let { id ->
        val reservation = c.concreteShiftReservations.firstOrNull { it.id == id }
        val secondCancellation = (reservation?.cancellationCount ?: 0) >= 1
        Punto25AlertDialog(
            onDismissRequest = { confirmReservationId = null },
            title = { Text("Cancelar inscripción") },
            text = {
                Text(if (secondCancellation)
                    "¿Confirmás la cancelación? Esta sería tu segunda cancelación de este turno: después no podrás volver a inscribirte en él."
                else "¿Confirmás la cancelación? Después deberás esperar 15 minutos para volver a anotarte. Si te reinscribís y cancelás por segunda vez, no podrás volver a inscribirte en este turno.")
            },
            confirmButton = {
                TextButton(
                    onClick = { c.cancelConcreteShift(id); confirmReservationId = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFB85F5F))
                ) { Text("SÍ, CANCELAR") }
            },
            dismissButton = { TextButton(onClick = { confirmReservationId = null }) { Text("NO") } }
        )
    }
}

@Composable
private fun ShiftRuleEditorDialog(c: MandadosController, rule: ShiftGenerationRule?, onDismiss: () -> Unit) {
    val initialDays = rule?.daysOfWeek ?: emptySet()
    val initialStart = rule?.let { ShiftSchedulePolicy.formatMinute(it.startMinute) } ?: ""
    val initialEnd = rule?.let { ShiftSchedulePolicy.formatMinute(it.endMinute) } ?: ""
    val initialCapacity = rule?.capacity?.toString() ?: "1"
    var days by remember(rule?.id) { mutableStateOf(initialDays) }
    var start by remember(rule?.id) { mutableStateOf(initialStart) }
    var end by remember(rule?.id) { mutableStateOf(initialEnd) }
    var capacity by remember(rule?.id) { mutableStateOf(initialCapacity) }
    var error by remember(rule?.id) { mutableStateOf("") }
    var confirmDiscard by remember(rule?.id) { mutableStateOf(false) }
    val dirty = days != initialDays || start != initialStart || end != initialEnd || capacity != initialCapacity

    fun requestDismiss(source: PendingEditDismissSource) {
        when (pendingEditDismissDecision(source, dirty)) {
            PendingEditDismissDecision.KEEP_OPEN -> Unit
            PendingEditDismissDecision.CLOSE -> onDismiss()
            PendingEditDismissDecision.CONFIRM_DISCARD -> confirmDiscard = true
        }
    }

    Punto25AlertDialog(
        onDismissRequest = { requestDismiss(PendingEditDismissSource.BACK) },
        title = { Text(if (rule == null) "Agregar regla de turnos" else "Editar regla de turnos") },
        text = {
            Column(Modifier.heightIn(max = 600.dp).verticalScroll(rememberScrollState())) {
                Text("Días de aplicación", fontWeight = FontWeight.Bold)
                (1..7).chunked(4).forEach { rowDays ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        rowDays.forEach { day ->
                            Row(
                                Modifier.weight(1f).clickable { days = if (day in days) days - day else days + day },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(day in days, { checked -> days = if (checked) days + day else days - day })
                                Text(shiftDayName(day).take(3), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        repeat(4 - rowDays.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ShiftTimeInput("Inicio", start, Modifier.weight(1f)) { start = it; error = "" }
                    ShiftTimeInput("Fin", end, Modifier.weight(1f)) { end = it; error = "" }
                }
                ShiftSimpleField("Cupo de Repartidores", capacity, KeyboardType.Number) { capacity = it.filter(Char::isDigit).take(3); error = "" }
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                ShiftAssistCard("Editar esta regla sólo afecta generaciones futuras. Los turnos concretos ya creados no se modifican.")
            }
        },
        confirmButton = {
            TextButton(onClick = {
                error = if (rule == null) c.addShiftRule(days, start, end, capacity.toIntOrNull() ?: 0) ?: ""
                else c.updateShiftRule(rule.id, days, start, end, capacity.toIntOrNull() ?: 0) ?: ""
                if (error.isBlank()) onDismiss()
            }) { Text("GUARDAR") }
        },
        dismissButton = { TextButton(onClick = { requestDismiss(PendingEditDismissSource.CANCEL) }) { Text("CANCELAR") } }
    )
    if (confirmDiscard) DiscardChangesDialog(
        onKeepEditing = { confirmDiscard = false },
        onDiscard = { confirmDiscard = false; onDismiss() }
    )
}

@Composable
private fun ConcreteShiftEditorDialog(c: MandadosController, shift: ConcreteShift, onDismiss: () -> Unit) {
    val initialStart = ShiftSchedulePolicy.formatMinute(shift.startMinute)
    val initialEnd = ShiftSchedulePolicy.formatMinute(shift.endMinute)
    val initialCapacity = shift.capacity.toString()
    val initialEnabled = shift.enabled
    var start by remember(shift.id) { mutableStateOf(initialStart) }
    var end by remember(shift.id) { mutableStateOf(initialEnd) }
    var capacity by remember(shift.id) { mutableStateOf(initialCapacity) }
    var enabled by remember(shift.id) { mutableStateOf(initialEnabled) }
    var error by remember(shift.id) { mutableStateOf("") }
    var confirmDiscard by remember(shift.id) { mutableStateOf(false) }
    val dirty = start != initialStart || end != initialEnd || capacity != initialCapacity || enabled != initialEnabled

    fun requestDismiss(source: PendingEditDismissSource) {
        when (pendingEditDismissDecision(source, dirty)) {
            PendingEditDismissDecision.KEEP_OPEN -> Unit
            PendingEditDismissDecision.CLOSE -> onDismiss()
            PendingEditDismissDecision.CONFIRM_DISCARD -> confirmDiscard = true
        }
    }

    val reservedCount = c.concreteShiftReservations.count { it.concreteShiftId == shift.id && it.status == ShiftReservationStatus.RESERVED }
    Punto25AlertDialog(
        onDismissRequest = { requestDismiss(PendingEditDismissSource.BACK) },
        title = { Text("Editar turno concreto") },
        text = {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState())) {
                Text("Fecha: ${ShiftSchedulePolicy.displayDate(shift.serviceDate) ?: shift.serviceDate}", fontWeight = FontWeight.Bold)
                Text("La fecha de este turno es inmutable.", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ShiftTimeInput("Inicio", start, Modifier.weight(1f)) { start = it; error = "" }
                    ShiftTimeInput("Fin", end, Modifier.weight(1f)) { end = it; error = "" }
                }
                ShiftSimpleField("Cupo", capacity, KeyboardType.Number) { capacity = it.filter(Char::isDigit).take(3); error = "" }
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Habilitado", Modifier.weight(1f)); Switch(enabled, { enabled = it; error = "" })
                }
                if (reservedCount > 0) ShiftAssistCard("Hay $reservedCount reserva(s) activa(s): el horario y la deshabilitación están bloqueados; el cupo nunca puede quedar por debajo de las reservas.")
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                ShiftAssistCard("Una edición manual de horario, cupo o estado se preserva como excepción y no será sobrescrita por generaciones futuras.")
            }
        },
        confirmButton = {
            TextButton(onClick = {
                error = c.updateConcreteShift(shift.id, start, end, capacity.toIntOrNull() ?: 0, enabled) ?: ""
                if (error.isBlank()) onDismiss()
            }) { Text("GUARDAR") }
        },
        dismissButton = { TextButton(onClick = { requestDismiss(PendingEditDismissSource.CANCEL) }) { Text("CANCELAR") } }
    )
    if (confirmDiscard) DiscardChangesDialog(
        onKeepEditing = { confirmDiscard = false },
        onDiscard = { confirmDiscard = false; onDismiss() }
    )
}

@Composable
private fun ShiftGenerationPreviewDialog(preview: ShiftGenerationPreview, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    Punto25AlertDialog(
        onDismissRequest = { /* outside tap is blocked by Punto25 policy; explicit actions close this preview */ },
        title = { Text("Previsualización de generación") },
        text = {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState())) {
                Text("${ShiftSchedulePolicy.displayDate(preview.request.fromDate) ?: preview.request.fromDate} → ${ShiftSchedulePolicy.displayDate(preview.request.toDate) ?: preview.request.toDate}", fontWeight = FontWeight.Bold)
                Text("Candidatos: ${preview.candidateCount}")
                Text("Nuevos a crear: ${preview.newCount}")
                Text("Ya materializados: ${preview.materializedCount}")
                Text("Excepciones preservadas: ${preview.exceptionCount}")
                preview.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
                if (preview.conflicts.isNotEmpty()) {
                    Text("Conflictos:", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                    preview.conflicts.forEach { Text("• $it", color = MaterialTheme.colorScheme.error) }
                }
                if (preview.canConfirm) Text("Al confirmar se reconstruirá y validará nuevamente todo el lote contra el estado actual.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = { TextButton(onClick = onConfirm, enabled = preview.canConfirm) { Text("CONFIRMAR GENERACIÓN") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCELAR") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RequiredShiftDatePicker(currentIso: String, onDismiss: () -> Unit, onSelected: (String) -> Unit) {
    val initialDate = ShiftSchedulePolicy.parseIsoDate(currentIso) ?: LocalDate.now()
    val initialMillis = ShiftSchedulePolicy.epochMillisUtc(initialDate)
    val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    var confirmDiscard by remember(currentIso) { mutableStateOf(false) }
    val dirty = state.selectedDateMillis != initialMillis

    fun requestDismiss(source: PendingEditDismissSource) {
        when (pendingEditDismissDecision(source, dirty)) {
            PendingEditDismissDecision.KEEP_OPEN -> Unit
            PendingEditDismissDecision.CLOSE -> onDismiss()
            PendingEditDismissDecision.CONFIRM_DISCARD -> confirmDiscard = true
        }
    }

    DatePickerDialog(
        onDismissRequest = { requestDismiss(PendingEditDismissSource.BACK) },
        confirmButton = {
            TextButton(onClick = {
                val millis = state.selectedDateMillis ?: return@TextButton
                val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                onSelected(ShiftSchedulePolicy.toIsoDate(date))
            }) { Text("ACEPTAR") }
        },
        dismissButton = { TextButton(onClick = { requestDismiss(PendingEditDismissSource.CANCEL) }) { Text("CANCELAR") } },
        properties = punto25DialogProperties()
    ) { DatePicker(state = state) }

    if (confirmDiscard) DiscardChangesDialog(
        onKeepEditing = { confirmDiscard = false },
        onDiscard = { confirmDiscard = false; onDismiss() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShiftRuleLimitDropdown(
    label: String,
    selectedId: String?,
    rules: List<ShiftGenerationRule>,
    emptyLabel: String,
    onSelect: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = rules.firstOrNull { it.id == selectedId }
    ExposedDropdownMenuBox(expanded, { expanded = it }) {
        OutlinedTextField(
            value = selected?.let(::shiftRuleLabel) ?: emptyLabel,
            onValueChange = {}, readOnly = true, label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth().padding(top = 7.dp)
        )
        ExposedDropdownMenu(expanded, { expanded = false }) {
            DropdownMenuItem({ Text(emptyLabel) }, { onSelect(null); expanded = false })
            rules.forEach { rule -> DropdownMenuItem({ Text(shiftRuleLabel(rule)) }, { onSelect(rule.id); expanded = false }) }
        }
    }
}

private fun shiftRuleLabel(rule: ShiftGenerationRule): String =
    "${ShiftSchedulePolicy.formatMinute(rule.startMinute)}–${ShiftSchedulePolicy.formatMinute(rule.endMinute)} · cupo ${rule.capacity}"

@Composable
private fun ShiftPage(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
            Text(title, Modifier.padding(14.dp), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), content = content)
        HorizontalDivider(Modifier.padding(top = 8.dp))
        TextButton(onClick = onBack) { Text("← VOLVER", fontWeight = FontWeight.ExtraBold) }
    }
}

@Composable private fun ShiftSectionTitle(text: String) = Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
@Composable private fun ShiftAssistCard(text: String) { Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) { Text(text, Modifier.padding(12.dp)) } }
@Composable private fun ShiftSimpleField(label: String, value: String, keyboardType: KeyboardType = KeyboardType.Text, onValue: (String) -> Unit) {
    OutlinedTextField(value, onValue, label = { Text(label) }, keyboardOptions = KeyboardOptions(keyboardType = keyboardType), singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 7.dp))
}

@Composable
private fun ShiftTimeInput(label: String, value: String, modifier: Modifier = Modifier, onValue: (String) -> Unit) {
    var field by remember(value) { mutableStateOf(TextFieldValue(value, selection = TextRange(value.length))) }
    OutlinedTextField(
        value = field,
        onValueChange = { incoming ->
            val digits = incoming.text.filter(Char::isDigit).take(4)
            val formatted = if (digits.length <= 2) digits else digits.substring(0, 2) + ":" + digits.substring(2)
            field = TextFieldValue(formatted, selection = TextRange(formatted.length))
            onValue(formatted)
        },
        label = { Text(label) }, placeholder = { Text("HH:mm") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true,
        modifier = modifier.padding(top = 7.dp)
    )
}

private fun shiftDayName(day: Int): String = when (day) {
    1 -> "Lunes"; 2 -> "Martes"; 3 -> "Miércoles"; 4 -> "Jueves"; 5 -> "Viernes"; 6 -> "Sábado"; else -> "Domingo"
}

private fun shiftEventTextV2(type: ShiftEventType): String = when (type) {
    ShiftEventType.RIDER_JOINED -> "Repartidor se anotó"
    ShiftEventType.RIDER_CANCELLED -> "Repartidor canceló"
    ShiftEventType.ADMIN_ADDED -> "Admin agregó"
    ShiftEventType.ADMIN_REMOVED -> "Admin quitó"
}
