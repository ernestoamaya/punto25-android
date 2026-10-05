package ar.com.mandados.app

internal fun riderDenialMessage(decision: RiderEligibilityDecision): String {
    val document = decision.documentKey?.let(::riderDocumentLabel).orEmpty()
    return when (decision.reason) {
        null -> ""
        RiderDenialReason.SESSION_REQUIRED ->
            "Tu sesión de Repartidor no es válida. Volvé a ingresar."
        RiderDenialReason.ACCOUNT_DEACTIVATED ->
            "Tu usuario está desactivado. Volvé a Acceso de Repartidor y comunicate con Administración de Punto25."
        RiderDenialReason.PENDING_APPROVAL ->
            "Tu usuario todavía está pendiente de aprobación. Podés consultar tu información, pero no iniciar trabajo nuevo."
        RiderDenialReason.SUSPENDED ->
            "Tu usuario está suspendido. No podés iniciar trabajo nuevo ni operar Turnos mientras continúe suspendido."
        RiderDenialReason.DOCUMENT_NOT_UPLOADED ->
            "Falta cargar ${document.ifBlank { "documentación obligatoria" }}. No podés iniciar trabajo nuevo hasta regularizarla."
        RiderDenialReason.DOCUMENT_PENDING ->
            "${document.ifBlank { "La documentación obligatoria" }} está pendiente de aprobación. No podés iniciar trabajo nuevo todavía."
        RiderDenialReason.DOCUMENT_REJECTED ->
            "${document.ifBlank { "La documentación obligatoria" }} fue rechazada. No podés iniciar trabajo nuevo hasta regularizarla."
        RiderDenialReason.OPERATION_MODE_UNAVAILABLE ->
            "La operación Multi-Repartidor no está disponible en este momento."
        RiderDenialReason.NO_ACTIVE_SHIFT ->
            "No tenés un turno activo en este momento."
        RiderDenialReason.SHIFT_NOT_AVAILABLE ->
            "Ese turno ya no está disponible para inscripción."
        RiderDenialReason.SHIFT_FULL ->
            "Ese turno ya completó su cupo."
        RiderDenialReason.REJOIN_BLOCKED ->
            "Ya no podés volver a inscribirte en esta ocurrencia del turno."
        RiderDenialReason.REJOIN_COOLDOWN ->
            "Todavía debés esperar antes de volver a inscribirte en este turno."
        RiderDenialReason.NO_CAPACITY ->
            "Alcanzaste el máximo de pedidos simultáneos permitido."
        RiderDenialReason.RIDER_NOT_AVAILABLE ->
            "Para tomar un pedido primero debés estar Disponible."
        RiderDenialReason.ORDER_NOT_AVAILABLE ->
            "Ese pedido ya no está disponible para tomar."
    }
}

internal fun riderDocumentLabel(key: RiderDocumentKey): String = when (key) {
    RiderDocumentKey.DNI_FRONT -> "DNI · anverso"
    RiderDocumentKey.DNI_BACK -> "DNI · reverso"
    RiderDocumentKey.MOTORCYCLE_PLATE -> "Patente"
    RiderDocumentKey.DRIVER_LICENSE_FRONT -> "Licencia · anverso"
    RiderDocumentKey.DRIVER_LICENSE_BACK -> "Licencia · reverso"
    RiderDocumentKey.VEHICLE_CARD -> "Tarjeta verde/azul"
    RiderDocumentKey.INSURANCE_CARD -> "Seguro"
}
