package ar.com.mandados.app

import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog as MaterialAlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

internal class UnsavedChangesGuardState {
    private var pendingExit by mutableStateOf<(() -> Unit)?>(null)

    internal val hasPendingExit: Boolean
        get() = pendingExit != null

    internal fun requestExit(dirty: Boolean, exit: () -> Unit) {
        if (dirty) {
            if (pendingExit == null) pendingExit = exit
        } else {
            exit()
        }
    }

    internal fun keepEditing() {
        pendingExit = null
    }

    internal fun discard(onDiscard: () -> Unit = {}) {
        val exit = pendingExit ?: return
        pendingExit = null
        onDiscard()
        exit()
    }
}

@Composable
internal fun rememberUnsavedChangesGuardState(): UnsavedChangesGuardState =
    remember { UnsavedChangesGuardState() }

@Composable
internal fun UnsavedChangesGuard(
    dirty: Boolean,
    state: UnsavedChangesGuardState,
    onBack: () -> Unit,
    onDiscard: () -> Unit = {},
    enabled: Boolean = true
) {
    BackHandler(enabled = enabled) {
        state.requestExit(dirty, onBack)
    }
    if (state.hasPendingExit) {
        DiscardChangesDialog(
            onKeepEditing = state::keepEditing,
            onDiscard = { state.discard(onDiscard) }
        )
    }
}

internal data class RiderProfileEditSnapshot(
    val alias: String,
    val currentPassword: String = "",
    val newPassword: String = "",
    val confirmPassword: String = ""
)

internal fun isRiderProfileEditDirty(
    persistedAlias: String,
    current: RiderProfileEditSnapshot
): Boolean =
    current.alias != persistedAlias ||
        current.currentPassword.isNotEmpty() ||
        current.newPassword.isNotEmpty() ||
        current.confirmPassword.isNotEmpty()

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
