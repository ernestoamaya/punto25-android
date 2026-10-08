package ar.com.mandados.app

internal enum class SupportAudience {
    CUSTOMER,
    RIDER
}

internal fun supportReasonsFor(audience: SupportAudience): List<String> = when (audience) {
    SupportAudience.CUSTOMER -> listOf(
        "Pedido",
        "Pago / cobro",
        "Cuenta / perfil",
        "Problemas con la app",
        "Otro"
    )
    SupportAudience.RIDER -> listOf(
        "Pedido / entrega",
        "Turnos",
        "Balance / pagos",
        "Documentación",
        "Problemas con la app",
        "Otro"
    )
}
