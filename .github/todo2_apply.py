from pathlib import Path

ROOT = Path('.')
OPS = ROOT / 'app/src/main/java/ar/com/mandados/app/OperationsScreens.kt'
APP = ROOT / 'app/src/main/java/ar/com/mandados/app/MandadosApp.kt'
POLICY = ROOT / 'app/src/main/java/ar/com/mandados/app/Punto25DialogPolicy.kt'
TEST = ROOT / 'app/src/test/java/ar/com/mandados/app/DialogDismissPolicyTest.kt'
MATRIX = ROOT / 'REGRESSION_MATRIX.md'


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected exactly 1 occurrence, found {count}')
    return text.replace(old, new, 1)


def replace_section(text: str, start: str, end: str, replacement: str, label: str) -> str:
    i = text.find(start)
    if i < 0:
        raise SystemExit(f'{label}: start marker not found')
    j = text.find(end, i)
    if j < 0:
        raise SystemExit(f'{label}: end marker not found')
    return text[:i] + replacement.rstrip() + '\n\n' + text[j:]


ops = OPS.read_text()
app = APP.read_text()

# All Punto25-controlled Material dialogs share the same outside-dismiss policy.
if ops.count('AlertDialog(') != 12:
    raise SystemExit(f'OperationsScreens AlertDialog count drifted: {ops.count("AlertDialog(")}')
if app.count('AlertDialog(') != 5:
    raise SystemExit(f'MandadosApp AlertDialog count drifted: {app.count("AlertDialog(")}')
ops = ops.replace('AlertDialog(', 'Punto25AlertDialog(')
app = app.replace('AlertDialog(', 'Punto25AlertDialog(')

# Rider save callback must report failure so the dialog cannot silently close.
old_parent_save = '''            onSave = { name, phone, birth, vehicle, address, docs, limit, approval ->
                val savedId = c.saveRider(editingId, name, phone, birth, vehicle, address, docs, limit)
                savedId?.let { c.setRiderApprovalStatus(it, approval) }
                editOpen = false
            }
'''
new_parent_save = '''            onSave = { name, phone, birth, vehicle, address, docs, limit, approval ->
                val savedId = c.saveRider(editingId, name, phone, birth, vehicle, address, docs, limit)
                if (savedId != null) {
                    c.setRiderApprovalStatus(savedId, approval)
                    editOpen = false
                    true
                } else {
                    false
                }
            }
'''
ops = replace_once(ops, old_parent_save, new_parent_save, 'Rider save callback')

order_edit = r'''@Composable
private fun OrderEditDialog(
    c: MandadosController,
    order: LocalOrder,
    onDismiss: () -> Unit,
    onSave: (OrderDraft, String) -> Unit
) {
    val initialEdited = remember(order.id) { c.draftFromOrder(order) }
    var edited by remember(order.id) { mutableStateOf(initialEdited) }
    var reason by rememberSaveable(order.id) { mutableStateOf("") }
    var confirmDiscard by remember(order.id) { mutableStateOf(false) }
    val dirty = edited != initialEdited || reason.isNotEmpty()

    fun requestDismiss(source: PendingEditDismissSource) {
        when (pendingEditDismissDecision(source, dirty)) {
            PendingEditDismissDecision.KEEP_OPEN -> Unit
            PendingEditDismissDecision.CLOSE -> onDismiss()
            PendingEditDismissDecision.CONFIRM_DISCARD -> confirmDiscard = true
        }
    }

    Punto25AlertDialog(
        onDismissRequest = { requestDismiss(PendingEditDismissSource.BACK) },
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
        dismissButton = {
            TextButton(onClick = { requestDismiss(PendingEditDismissSource.CANCEL) }) { Text("CANCELAR") }
        }
    )

    if (confirmDiscard) {
        DiscardChangesDialog(
            onKeepEditing = { confirmDiscard = false },
            onDiscard = { confirmDiscard = false; onDismiss() }
        )
    }
}'''
ops = replace_section(
    ops,
    '@Composable\nprivate fun OrderEditDialog(',
    '\n@Composable\ninternal fun AdminReportsScreen',
    order_edit,
    'OrderEditDialog'
)

rider_group = r'''@Composable
private fun RiderEditDialog(
    c: MandadosController,
    rider: RiderProfile?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, VehicleType, String, RiderDocuments, Int?, RiderApprovalStatus) -> Boolean
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
    var confirmDiscard by remember(rider?.id) { mutableStateOf(false) }
    var saveError by remember(rider?.id) { mutableStateOf("") }

    val initialSnapshot = remember(rider?.id) { riderEditSnapshot(rider) }
    val currentSnapshot = riderEditSnapshot(name, phone, birth, vehicle, address, limitText.toIntOrNull(), docs, approval)
    val dirty = isRiderEditDirty(initialSnapshot, currentSnapshot)

    fun requestDismiss(source: PendingEditDismissSource) {
        when (pendingEditDismissDecision(source, dirty)) {
            PendingEditDismissDecision.KEEP_OPEN -> Unit
            PendingEditDismissDecision.CLOSE -> onDismiss()
            PendingEditDismissDecision.CONFIRM_DISCARD -> confirmDiscard = true
        }
    }

    val picker = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        val key = pendingKey
        if (uri != null && key != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            docs = docs.withUri(key, uri.toString())
        }
        pendingKey = null
    }

    Punto25AlertDialog(
        onDismissRequest = { requestDismiss(PendingEditDismissSource.BACK) },
        title = { Text(if (rider == null) "Alta de Repartidor" else "Editar Repartidor") },
        text = {
            Column(Modifier.heightIn(max = 650.dp).verticalScroll(rememberScrollState())) {
                SimpleField("Nombre y apellido completos *", name) { name = it; saveError = "" }
                SimpleField("Teléfono", phone, KeyboardType.Phone) { phone = it.filter(Char::isDigit).take(15); saveError = "" }
                SimpleField("Fecha de nacimiento DD/MM/AAAA", birth, KeyboardType.Number) { birth = it.take(10); saveError = "" }
                SimpleField("Domicilio", address) { address = it; saveError = "" }
                SimpleField("Máximo simultáneo (vacío = general ${c.config.defaultMaxConcurrentOrders})", limitText, KeyboardType.Number) {
                    limitText = it.filter(Char::isDigit).take(2)
                    saveError = ""
                }

                SectionTitle("Estado operativo")
                EnumDropdown(
                    "Estado del Repartidor",
                    approval,
                    RiderApprovalStatus.entries,
                    allLabel = "",
                    labelFor = ::riderApprovalText,
                    onSelect = { if (it != null) { approval = it; saveError = "" } }
                )
                AssistCard("Habilitado permite ponerse Disponible y tomar pedidos. Pendiente o Suspendido bloquean esa operación.")

                SectionTitle("Vehículo")
                RadioLine("Moto", vehicle == VehicleType.MOTORCYCLE) { vehicle = VehicleType.MOTORCYCLE; saveError = "" }
                RadioLine("Bicicleta", vehicle == VehicleType.BICYCLE) { vehicle = VehicleType.BICYCLE; saveError = "" }

                SectionTitle("Carga / reemplazo de documentación")
                DocumentEditLine("DNI · anverso", docs.dniFrontUri, { pendingKey = RiderDocumentKey.DNI_FRONT; picker.launch(PickVisualMediaRequest(ImageOnly)) }) { docs = docs.withUri(RiderDocumentKey.DNI_FRONT, null); saveError = "" }
                DocumentEditLine("DNI · reverso", docs.dniBackUri, { pendingKey = RiderDocumentKey.DNI_BACK; picker.launch(PickVisualMediaRequest(ImageOnly)) }) { docs = docs.withUri(RiderDocumentKey.DNI_BACK, null); saveError = "" }
                if (vehicle == VehicleType.MOTORCYCLE) {
                    DocumentEditLine("Patente", docs.motorcyclePlateUri, { pendingKey = RiderDocumentKey.MOTORCYCLE_PLATE; picker.launch(PickVisualMediaRequest(ImageOnly)) }) { docs = docs.withUri(RiderDocumentKey.MOTORCYCLE_PLATE, null); saveError = "" }
                    DocumentEditLine("Licencia · anverso", docs.driverLicenseFrontUri, { pendingKey = RiderDocumentKey.DRIVER_LICENSE_FRONT; picker.launch(PickVisualMediaRequest(ImageOnly)) }) { docs = docs.withUri(RiderDocumentKey.DRIVER_LICENSE_FRONT, null); saveError = "" }
                    DocumentEditLine("Licencia · reverso", docs.driverLicenseBackUri, { pendingKey = RiderDocumentKey.DRIVER_LICENSE_BACK; picker.launch(PickVisualMediaRequest(ImageOnly)) }) { docs = docs.withUri(RiderDocumentKey.DRIVER_LICENSE_BACK, null); saveError = "" }
                    DocumentEditLine("Tarjeta verde/azul", docs.vehicleCardUri, { pendingKey = RiderDocumentKey.VEHICLE_CARD; picker.launch(PickVisualMediaRequest(ImageOnly)) }) { docs = docs.withUri(RiderDocumentKey.VEHICLE_CARD, null); saveError = "" }
                    DocumentEditLine("Seguro", docs.insuranceCardUri, { pendingKey = RiderDocumentKey.INSURANCE_CARD; picker.launch(PickVisualMediaRequest(ImageOnly)) }) { docs = docs.withUri(RiderDocumentKey.INSURANCE_CARD, null); saveError = "" }
                }
                if (saveError.isNotBlank()) Text(saveError, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val saved = onSave(name, phone, birth, vehicle, address, docs, limitText.toIntOrNull(), approval)
                    if (!saved) saveError = "No se pudo guardar el Repartidor. Revisá los datos e intentá nuevamente."
                },
                enabled = name.trim().isNotBlank()
            ) { Text("GUARDAR") }
        },
        dismissButton = {
            TextButton(onClick = { requestDismiss(PendingEditDismissSource.CANCEL) }) { Text("CANCELAR") }
        }
    )

    if (confirmDiscard) {
        DiscardChangesDialog(
            onKeepEditing = { confirmDiscard = false },
            onDiscard = { confirmDiscard = false; onDismiss() }
        )
    }
}

@Composable
private fun RiderDocumentsDialog(c: MandadosController, rider: RiderProfile, onDismiss: () -> Unit) {
    val keys = remember(rider.id, rider.vehicleType) {
        if (rider.vehicleType == VehicleType.MOTORCYCLE) RiderDocumentKey.entries
        else listOf(RiderDocumentKey.DNI_FRONT, RiderDocumentKey.DNI_BACK)
    }
    val startingNotes = remember(rider.id) { keys.associateWith { rider.documentNotes[it].orEmpty() } }
    var savedNotes by remember(rider.id) { mutableStateOf(startingNotes) }
    var noteDrafts by remember(rider.id) { mutableStateOf(startingNotes) }
    var confirmDiscard by remember(rider.id) { mutableStateOf(false) }
    val dirty = noteDrafts != savedNotes

    fun requestDismiss(source: PendingEditDismissSource) {
        when (pendingEditDismissDecision(source, dirty)) {
            PendingEditDismissDecision.KEEP_OPEN -> Unit
            PendingEditDismissDecision.CLOSE -> onDismiss()
            PendingEditDismissDecision.CONFIRM_DISCARD -> confirmDiscard = true
        }
    }

    Punto25AlertDialog(
        onDismissRequest = { requestDismiss(PendingEditDismissSource.BACK) },
        title = { Text("Documentación · ${rider.name}") },
        text = {
            Column(Modifier.heightIn(max = 650.dp).verticalScroll(rememberScrollState())) {
                Text("Estado del Repartidor: ${riderApprovalText(rider.approvalStatus)}", fontWeight = FontWeight.Bold)
                Text("El estado se administra desde Editar Repartidor.", style = MaterialTheme.typography.bodySmall)
                keys.forEach { key ->
                    DocumentReviewCard(
                        rider = rider,
                        key = key,
                        note = noteDrafts[key].orEmpty(),
                        onNoteChange = { value -> noteDrafts = noteDrafts + (key to value) },
                        onReview = { status ->
                            val persistedNote = noteDrafts[key].orEmpty().trim()
                            c.setRiderDocumentReview(rider.id, key, status, persistedNote)
                            noteDrafts = noteDrafts + (key to persistedNote)
                            savedNotes = savedNotes + (key to persistedNote)
                        }
                    )
                }

                if (rider.approvalStatus != RiderApprovalStatus.APPROVED) {
                    Button(
                        onClick = { c.setRiderApprovalStatus(rider.id, RiderApprovalStatus.APPROVED) },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                    ) { Text("HABILITAR REPARTIDOR") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { requestDismiss(PendingEditDismissSource.CANCEL) }) { Text("CERRAR") }
        }
    )

    if (confirmDiscard) {
        DiscardChangesDialog(
            onKeepEditing = { confirmDiscard = false },
            onDiscard = { confirmDiscard = false; onDismiss() }
        )
    }
}

@Composable
private fun DocumentReviewCard(
    rider: RiderProfile,
    key: RiderDocumentKey,
    note: String,
    onNoteChange: (String) -> Unit,
    onReview: (DocumentReviewStatus) -> Unit
) {
    val uri = rider.documents.uriFor(key)

    Card(Modifier.fillMaxWidth().padding(top = 9.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(documentLabel(key), fontWeight = FontWeight.Bold)
            Text(documentReviewText(rider.reviewFor(key)), color = documentReviewColor(rider.reviewFor(key)))
            if (uri.isNullOrBlank()) {
                Text("Sin imagen cargada.")
            } else {
                LocalDocumentImage(uri)
                OutlinedTextField(note, onNoteChange, label = { Text("Observación del Admin") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(rider.reviewFor(key) == DocumentReviewStatus.PENDING, { onReview(DocumentReviewStatus.PENDING) }, { Text("Pendiente") })
                    FilterChip(rider.reviewFor(key) == DocumentReviewStatus.APPROVED, { onReview(DocumentReviewStatus.APPROVED) }, { Text("Aprobar") })
                    FilterChip(rider.reviewFor(key) == DocumentReviewStatus.REJECTED, { onReview(DocumentReviewStatus.REJECTED) }, { Text("Rechazar") })
                }
            }
        }
    }
}'''
ops = replace_section(
    ops,
    '@Composable\nprivate fun RiderEditDialog(',
    '\n@Composable\nprivate fun LocalDocumentImage',
    rider_group,
    'Rider edit/doc dialogs'
)

shift_edit = r'''@Composable
private fun ShiftEditDialog(c: MandadosController, shift: ShiftTemplate, onDismiss: () -> Unit) {
    val initialRecurring = !shift.isSpecificDate
    val initialDate = shift.specificDate ?: LocalDate.now().format(dateFormatter)
    var recurring by remember(shift.id) { mutableStateOf(initialRecurring) }
    var day by remember(shift.id) { mutableStateOf(shift.dayOfWeek) }
    var date by remember(shift.id) { mutableStateOf(initialDate) }
    var start by remember(shift.id) { mutableStateOf(shift.startTime) }
    var end by remember(shift.id) { mutableStateOf(shift.endTime) }
    var capacity by remember(shift.id) { mutableStateOf(shift.capacity.toString()) }
    var enabled by remember(shift.id) { mutableStateOf(shift.enabled) }
    var error by remember(shift.id) { mutableStateOf("") }
    var confirmDiscard by remember(shift.id) { mutableStateOf(false) }
    val dirty = recurring != initialRecurring || day != shift.dayOfWeek || date != initialDate ||
        start != shift.startTime || end != shift.endTime || capacity != shift.capacity.toString() || enabled != shift.enabled

    fun requestDismiss(source: PendingEditDismissSource) {
        when (pendingEditDismissDecision(source, dirty)) {
            PendingEditDismissDecision.KEEP_OPEN -> Unit
            PendingEditDismissDecision.CLOSE -> onDismiss()
            PendingEditDismissDecision.CONFIRM_DISCARD -> confirmDiscard = true
        }
    }

    Punto25AlertDialog(
        onDismissRequest = { requestDismiss(PendingEditDismissSource.BACK) },
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
        dismissButton = {
            TextButton(onClick = { requestDismiss(PendingEditDismissSource.CANCEL) }) { Text("CANCELAR") }
        }
    )

    if (confirmDiscard) {
        DiscardChangesDialog(
            onKeepEditing = { confirmDiscard = false },
            onDiscard = { confirmDiscard = false; onDismiss() }
        )
    }
}'''
ops = replace_section(
    ops,
    '@Composable\nprivate fun ShiftEditDialog(',
    '\n@Composable\ninternal fun AdminPaymentsScreen',
    shift_edit,
    'ShiftEditDialog'
)

legal_edit = r'''@Composable
private fun LegalPdfEditor(c: MandadosController, type: LegalDocumentType, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val existing = c.legalDocuments.filter { it.type == type }.maxByOrNull { parseOrderTime(it.updatedAt) ?: LocalDateTime.MIN }
    val initialVersion = existing?.version ?: "1.0"
    val initialEffective = existing?.effectiveDate ?: LocalDate.now().format(dateFormatter)
    val initialPublished = existing?.published ?: false
    val initialRequireAcceptance = existing?.requireAcceptance ?: false
    val initialUri = existing?.fileUri
    val initialName = existing?.fileName
    var version by remember(type, existing?.id) { mutableStateOf(initialVersion) }
    var effective by remember(type, existing?.id) { mutableStateOf(initialEffective) }
    var published by remember(type, existing?.id) { mutableStateOf(initialPublished) }
    var requireAcceptance by remember(type, existing?.id) { mutableStateOf(initialRequireAcceptance) }
    var selectedUri by remember(type, existing?.id) { mutableStateOf(initialUri) }
    var selectedName by remember(type, existing?.id) { mutableStateOf(initialName) }
    var error by remember { mutableStateOf("") }
    var confirmDiscard by remember(type, existing?.id) { mutableStateOf(false) }
    val dirty = version != initialVersion || effective != initialEffective || published != initialPublished ||
        requireAcceptance != initialRequireAcceptance || selectedUri != initialUri || selectedName != initialName

    fun requestDismiss(source: PendingEditDismissSource) {
        when (pendingEditDismissDecision(source, dirty)) {
            PendingEditDismissDecision.KEEP_OPEN -> Unit
            PendingEditDismissDecision.CLOSE -> onDismiss()
            PendingEditDismissDecision.CONFIRM_DISCARD -> confirmDiscard = true
        }
    }

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

    Punto25AlertDialog(
        onDismissRequest = { requestDismiss(PendingEditDismissSource.BACK) },
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
        dismissButton = {
            TextButton(onClick = { requestDismiss(PendingEditDismissSource.CANCEL) }) { Text("CERRAR") }
        }
    )

    if (confirmDiscard) {
        DiscardChangesDialog(
            onKeepEditing = { confirmDiscard = false },
            onDiscard = { confirmDiscard = false; onDismiss() }
        )
    }
}'''
ops = replace_section(
    ops,
    '@Composable\nprivate fun LegalPdfEditor(',
    '\n@Composable\nprivate fun PermissionsScreen',
    legal_edit,
    'LegalPdfEditor'
)

date_picker = r'''@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerPopup(current: String?, onDismiss: () -> Unit, onSelected: (String?) -> Unit) {
    val initial = current?.let(::parseDate)?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
    val state = rememberDatePickerState(initialSelectedDateMillis = initial)
    var confirmDiscard by remember(current) { mutableStateOf(false) }
    val dirty = state.selectedDateMillis != initial

    fun requestDismiss() {
        when (pendingEditDismissDecision(PendingEditDismissSource.BACK, dirty)) {
            PendingEditDismissDecision.KEEP_OPEN -> Unit
            PendingEditDismissDecision.CLOSE -> onDismiss()
            PendingEditDismissDecision.CONFIRM_DISCARD -> confirmDiscard = true
        }
    }

    DatePickerDialog(
        onDismissRequest = ::requestDismiss,
        confirmButton = {
            TextButton(onClick = {
                val millis = state.selectedDateMillis
                val date = millis?.let { java.time.Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate() }
                onSelected(date?.format(dateFormatter))
            }) { Text("ACEPTAR") }
        },
        dismissButton = {
            TextButton(onClick = { onSelected(null) }) { Text("SIN LÍMITE") }
        },
        properties = punto25DialogProperties()
    ) { DatePicker(state = state) }

    if (confirmDiscard) {
        DiscardChangesDialog(
            onKeepEditing = { confirmDiscard = false },
            onDiscard = { confirmDiscard = false; onDismiss() }
        )
    }
}'''
ops = replace_section(
    ops,
    '@OptIn(ExperimentalMaterial3Api::class)\n@Composable\nprivate fun DatePickerPopup(',
    '\n@OptIn(ExperimentalMaterial3Api::class)\n@Composable\nprivate fun <T> EnumDropdown',
    date_picker,
    'DatePickerPopup'
)

# Exposed menus: outside dismissal becomes a no-op; item/anchor actions remain explicit close paths.
menu_old = 'ExposedDropdownMenu(expanded, { expanded = false }) {'
if ops.count(menu_old) != 3:
    raise SystemExit(f'ExposedDropdownMenu count drifted: {ops.count(menu_old)}')
ops = ops.replace(menu_old, 'ExposedDropdownMenu(expanded, { }) {')

zone_admin = r'''@Composable
private fun ZoneAdminDialog(
    zone: ZoneConfig?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, Int) -> Unit
) {
    val initialName = zone?.name ?: ""
    val initialDescription = zone?.description ?: ""
    val initialCategory = zone?.category ?: "OTRAS"
    val initialPriceText = zone?.price?.takeIf { it > 0 }?.toString() ?: ""
    var name by remember(zone?.id) { mutableStateOf(initialName) }
    var description by remember(zone?.id) { mutableStateOf(initialDescription) }
    var category by remember(zone?.id) { mutableStateOf(initialCategory) }
    var priceText by remember(zone?.id) { mutableStateOf(initialPriceText) }
    var confirmDiscard by remember(zone?.id) { mutableStateOf(false) }
    val dirty = name != initialName || description != initialDescription || category != initialCategory || priceText != initialPriceText

    fun requestDismiss(source: PendingEditDismissSource) {
        when (pendingEditDismissDecision(source, dirty)) {
            PendingEditDismissDecision.KEEP_OPEN -> Unit
            PendingEditDismissDecision.CLOSE -> onDismiss()
            PendingEditDismissDecision.CONFIRM_DISCARD -> confirmDiscard = true
        }
    }

    Punto25AlertDialog(
        onDismissRequest = { requestDismiss(PendingEditDismissSource.BACK) },
        title = { Text(if (zone == null) "Agregar zona" else "Editar zona") },
        text = {
            Column {
                Field("Nombre *", name) { name = it }
                Field("Descripción", description, singleLine = false) { description = it }
                Field("Categoría", category) { category = it }
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it.filter(Char::isDigit).take(9) },
                    label = { Text("Tarifa") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name.trim(), description.trim(), category.trim(), priceText.toIntOrNull() ?: 0) },
                enabled = name.trim().isNotBlank()
            ) { Text("GUARDAR") }
        },
        dismissButton = {
            TextButton(onClick = { requestDismiss(PendingEditDismissSource.CANCEL) }) { Text("CANCELAR") }
        }
    )

    if (confirmDiscard) {
        DiscardChangesDialog(
            onKeepEditing = { confirmDiscard = false },
            onDiscard = { confirmDiscard = false; onDismiss() }
        )
    }
}'''
app = replace_section(
    app,
    '@Composable\nprivate fun ZoneAdminDialog(',
    '\n@Composable\nprivate fun LocationField',
    zone_admin,
    'ZoneAdminDialog'
)

POLICY.write_text(r'''package ar.com.mandados.app

import androidx.compose.material3.AlertDialog as MaterialAlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties

internal enum class PendingEditDismissSource { OUTSIDE, BACK, CANCEL }
internal enum class PendingEditDismissDecision { KEEP_OPEN, CLOSE, CONFIRM_DISCARD }

internal fun pendingEditDismissDecision(
    source: PendingEditDismissSource,
    dirty: Boolean
): PendingEditDismissDecision = when (source) {
    PendingEditDismissSource.OUTSIDE -> PendingEditDismissDecision.KEEP_OPEN
    PendingEditDismissSource.BACK,
    PendingEditDismissSource.CANCEL -> if (dirty) PendingEditDismissDecision.CONFIRM_DISCARD else PendingEditDismissDecision.CLOSE
}

internal fun punto25DialogProperties(dismissOnBackPress: Boolean = true): DialogProperties =
    DialogProperties(
        dismissOnBackPress = dismissOnBackPress,
        dismissOnClickOutside = false
    )

@Composable
internal fun Punto25AlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null
) {
    MaterialAlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier,
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = text,
        properties = punto25DialogProperties()
    )
}

@Composable
internal fun DiscardChangesDialog(
    onKeepEditing: () -> Unit,
    onDiscard: () -> Unit
) {
    Punto25AlertDialog(
        onDismissRequest = onKeepEditing,
        title = { Text("Descartar cambios") },
        text = { Text("Hay cambios sin guardar. ¿Querés descartarlos?") },
        confirmButton = {
            TextButton(onClick = onDiscard) { Text("DESCARTAR CAMBIOS") }
        },
        dismissButton = {
            TextButton(onClick = onKeepEditing) { Text("SEGUIR EDITANDO") }
        }
    )
}

internal data class RiderEditSnapshot(
    val name: String,
    val phone: String,
    val birthDate: String,
    val vehicleType: VehicleType,
    val address: String,
    val maxConcurrentOrdersOverride: Int?,
    val documents: RiderDocuments,
    val approvalStatus: RiderApprovalStatus
)

internal fun riderEditSnapshot(rider: RiderProfile?): RiderEditSnapshot = RiderEditSnapshot(
    name = rider?.name ?: "",
    phone = rider?.phone ?: "",
    birthDate = rider?.birthDate ?: "",
    vehicleType = rider?.vehicleType ?: VehicleType.MOTORCYCLE,
    address = rider?.address ?: "",
    maxConcurrentOrdersOverride = rider?.maxConcurrentOrdersOverride,
    documents = rider?.documents ?: RiderDocuments(),
    approvalStatus = rider?.approvalStatus ?: RiderApprovalStatus.PENDING
)

internal fun riderEditSnapshot(
    name: String,
    phone: String,
    birthDate: String,
    vehicleType: VehicleType,
    address: String,
    maxConcurrentOrdersOverride: Int?,
    documents: RiderDocuments,
    approvalStatus: RiderApprovalStatus
): RiderEditSnapshot = RiderEditSnapshot(
    name = name,
    phone = phone,
    birthDate = birthDate,
    vehicleType = vehicleType,
    address = address,
    maxConcurrentOrdersOverride = maxConcurrentOrdersOverride,
    documents = documents,
    approvalStatus = approvalStatus
)

internal fun isRiderEditDirty(initial: RiderEditSnapshot, current: RiderEditSnapshot): Boolean = initial != current
''')

TEST.write_text(r'''package ar.com.mandados.app

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DialogDismissPolicyTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("mandados_alpha1", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `REG-DIALOG-DISMISS-001 outside never dismisses and back remains enabled`() {
        val properties = punto25DialogProperties()
        assertFalse(properties.dismissOnClickOutside)
        assertTrue(properties.dismissOnBackPress)
        assertEquals(
            PendingEditDismissDecision.KEEP_OPEN,
            pendingEditDismissDecision(PendingEditDismissSource.OUTSIDE, dirty = false)
        )
        assertEquals(
            PendingEditDismissDecision.KEEP_OPEN,
            pendingEditDismissDecision(PendingEditDismissSource.OUTSIDE, dirty = true)
        )
    }

    @Test
    fun `REG-RIDER-EDIT-DIRTY-001 Alta limpia y nombre modificado queda dirty`() {
        val initial = riderEditSnapshot(null)
        assertFalse(isRiderEditDirty(initial, initial))
        assertTrue(isRiderEditDirty(initial, initial.copy(name = "Rider Nuevo")))
    }

    @Test
    fun `REG-RIDER-EDIT-DIRTY-002 campos editables y documento se detectan individualmente`() {
        val rider = sampleRider()
        val initial = riderEditSnapshot(rider)
        assertTrue(isRiderEditDirty(initial, initial.copy(phone = "2345550199")))
        assertTrue(isRiderEditDirty(initial, initial.copy(birthDate = "02/02/1992")))
        assertTrue(isRiderEditDirty(initial, initial.copy(vehicleType = VehicleType.BICYCLE)))
        assertTrue(isRiderEditDirty(initial, initial.copy(address = "Otra dirección")))
        assertTrue(isRiderEditDirty(initial, initial.copy(maxConcurrentOrdersOverride = 3)))
        assertTrue(isRiderEditDirty(initial, initial.copy(approvalStatus = RiderApprovalStatus.SUSPENDED)))
        assertTrue(
            isRiderEditDirty(
                initial,
                initial.copy(documents = initial.documents.copy(dniFrontUri = "content://nuevo-dni"))
            )
        )
    }

    @Test
    fun `REG-RIDER-EDIT-REVERT-001 cambio revertido exactamente vuelve a limpio`() {
        val initial = riderEditSnapshot(sampleRider())
        val changed = initial.copy(name = "Temporal")
        assertTrue(isRiderEditDirty(initial, changed))
        assertFalse(isRiderEditDirty(initial, changed.copy(name = initial.name)))
    }

    @Test
    fun `REG-RIDER-EDIT-REVERT-002 multiples cambios y reversion completa vuelve a limpio`() {
        val initial = riderEditSnapshot(sampleRider())
        val changed = initial.copy(
            phone = "1111111111",
            birthDate = "03/03/1993",
            vehicleType = VehicleType.BICYCLE,
            address = "Temporal",
            maxConcurrentOrdersOverride = 5,
            approvalStatus = RiderApprovalStatus.SUSPENDED,
            documents = initial.documents.copy(dniBackUri = "content://temporal")
        )
        assertTrue(isRiderEditDirty(initial, changed))
        assertFalse(isRiderEditDirty(initial, initial.copy()))
    }

    @Test
    fun `REG-RIDER-EDIT-DIRTY-003 evaluar dirty no muta RiderProfile original`() {
        val rider = sampleRider()
        val before = rider.copy(documents = rider.documents.copy())
        val snapshot = riderEditSnapshot(rider)
        val changed = snapshot.copy(name = "Otro", documents = snapshot.documents.copy(dniFrontUri = "content://otro"))
        assertTrue(isRiderEditDirty(snapshot, changed))
        assertEquals(before, rider)
    }

    @Test
    fun `REG-RIDER-EDIT-DISCARD-001 back y cancelar protegen dirty pero cierran clean`() {
        assertEquals(
            PendingEditDismissDecision.CLOSE,
            pendingEditDismissDecision(PendingEditDismissSource.BACK, dirty = false)
        )
        assertEquals(
            PendingEditDismissDecision.CLOSE,
            pendingEditDismissDecision(PendingEditDismissSource.CANCEL, dirty = false)
        )
        assertEquals(
            PendingEditDismissDecision.CONFIRM_DISCARD,
            pendingEditDismissDecision(PendingEditDismissSource.BACK, dirty = true)
        )
        assertEquals(
            PendingEditDismissDecision.CONFIRM_DISCARD,
            pendingEditDismissDecision(PendingEditDismissSource.CANCEL, dirty = true)
        )
    }

    @Test
    fun `REG-RIDER-EDIT-SAVE-001 guardar Rider sigue persistiendo normalmente`() {
        val c = MandadosController(context)
        val id = c.saveRider(
            id = null,
            name = "Rider Guardado",
            phone = "2345550199",
            birthDate = "01/01/1990",
            vehicleType = VehicleType.MOTORCYCLE,
            address = "25 de Mayo",
            documents = RiderDocuments(dniFrontUri = "content://dni-front"),
            maxConcurrentOrdersOverride = 2
        )
        assertNotNull(id)
        val persisted = c.rider(id)
        assertNotNull(persisted)
        assertEquals("Rider Guardado", persisted!!.name)
        assertEquals("2345550199", persisted.phone)
        assertEquals("content://dni-front", persisted.documents.dniFrontUri)
        assertEquals(2, persisted.maxConcurrentOrdersOverride)
    }

    private fun sampleRider(): RiderProfile = RiderProfile(
        id = "RID-DIRTY",
        name = "Rider Original",
        phone = "2345550101",
        birthDate = "01/01/1991",
        vehicleType = VehicleType.MOTORCYCLE,
        address = "Dirección original",
        maxConcurrentOrdersOverride = 2,
        documents = RiderDocuments(
            dniFrontUri = "content://dni-front",
            dniBackUri = "content://dni-back"
        ),
        approvalStatus = RiderApprovalStatus.APPROVED
    )
}
''')

matrix = MATRIX.read_text()
anchor = '| REG-ZONE-PRESENTATION-001 | Las zonas se ordenan sólo para presentación por categoría y nombre, ignorando mayúsculas/tildes, con estabilidad determinista y sin mutar el orden persistido. | `ZonePresentationTest` |\n'
if matrix.count(anchor) != 1:
    raise SystemExit('Regression matrix anchor drifted')
addition = anchor + '''| REG-DIALOG-DISMISS-001 | Las ventanas propias de Punto25 no se cierran ni ejecutan acciones por toque exterior; Atrás sigue habilitado salvo bloqueos deliberados o protección de cambios pendientes. | `DialogDismissPolicyTest.REG-DIALOG-DISMISS-001…` |\n| REG-RIDER-EDIT-DIRTY-001 | Alta de Repartidor parte limpia y cualquier cambio pendiente relevante activa dirty state. | `DialogDismissPolicyTest.REG-RIDER-EDIT-DIRTY-001…` |\n| REG-RIDER-EDIT-DIRTY-002 | Teléfono, fecha, vehículo, domicilio, límite, aprobación y documentos forman parte del dirty state de Alta/Editar Repartidor. | `DialogDismissPolicyTest.REG-RIDER-EDIT-DIRTY-002…` |\n| REG-RIDER-EDIT-DIRTY-003 | Evaluar dirty state no muta el `RiderProfile` original. | `DialogDismissPolicyTest.REG-RIDER-EDIT-DIRTY-003…` |\n| REG-RIDER-EDIT-DISCARD-001 | Atrás/CANCELAR cierran limpio y exigen confirmación cuando hay cambios pendientes; toque exterior nunca descarta. | `DialogDismissPolicyTest.REG-RIDER-EDIT-DISCARD-001…` |\n| REG-RIDER-EDIT-REVERT-001 | Revertir exactamente un cambio al estado inicial devuelve el formulario a limpio. | `DialogDismissPolicyTest.REG-RIDER-EDIT-REVERT-001…` |\n| REG-RIDER-EDIT-REVERT-002 | Revertir por completo múltiples cambios devuelve el formulario a limpio. | `DialogDismissPolicyTest.REG-RIDER-EDIT-REVERT-002…` |\n| REG-RIDER-EDIT-SAVE-001 | Guardar un Repartidor continúa persistiendo los campos/documentos normalmente; un fallo de guardado no debe cerrar el formulario. | `DialogDismissPolicyTest.REG-RIDER-EDIT-SAVE-001…` |\n'''
matrix = matrix.replace(anchor, addition, 1)

OPS.write_text(ops)
APP.write_text(app)
MATRIX.write_text(matrix)

# Final static invariants before Gradle compilation.
assert ops.count('AlertDialog(') == 0
assert app.count('AlertDialog(') == 0
assert ops.count('Punto25AlertDialog(') >= 12
assert app.count('Punto25AlertDialog(') == 5
assert 'ExposedDropdownMenu(expanded, { expanded = false }) {' not in ops
assert 'onDismissRequest = onDismiss,\n        title = { Text(if (rider == null)' not in ops
