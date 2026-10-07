package ar.com.mandados.app

import androidx.compose.runtime.Composable

/**
 * Únicos entry points operativos de Turnos.
 * Las implementaciones v1 quedan compilables sólo como referencia Alpha y no son navegables.
 */
@Composable
internal fun AdminShiftsScreen(c: MandadosController, onBack: () -> Unit) {
    AdminShiftsV2Screen(c, onBack)
}

@Composable
internal fun RiderShifts(c: MandadosController, rider: RiderProfile) {
    RiderShiftsV2(c, rider)
}
