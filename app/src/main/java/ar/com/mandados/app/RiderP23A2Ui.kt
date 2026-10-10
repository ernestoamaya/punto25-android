package ar.com.mandados.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Bootstrap gate for Rider authority.
 *
 * A cached session is only a hint. App content is released after the coordinator
 * has either revalidated it with GET /v1/rider/session or failed closed.
 */
@Composable
internal fun RiderSessionBootstrapGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val coordinator = remember(context) { createRiderCoordinator(context.applicationContext) }
    var ready by remember { mutableStateOf(false) }

    LaunchedEffect(coordinator) {
        coordinator.restore()
        ready = true
    }

    if (ready) {
        content()
    } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}

@Composable
internal fun RiderBackendAccessScreen(
    onBack: () -> Unit,
    onSuccess: (String) -> Unit
) {
    val context = LocalContext.current
    val coordinator = remember(context) { createRiderCoordinator(context.applicationContext) }
    val scope = rememberCoroutineScope()

    var capabilityMode by remember { mutableStateOf(false) }
    var riderCode by remember { mutableStateOf("") }
    var loginPassword by remember { mutableStateOf("") }
    var capability by remember { mutableStateOf("") }
    var capabilityPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var loginKeys by remember { mutableStateOf(RiderLoginAttemptKeys()) }
    var capabilityKeys by remember { mutableStateOf(RiderAuthAttemptKeys()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    fun resetLoginIntent() {
        loginKeys = RiderLoginAttemptKeys()
        message = ""
    }

    fun resetCapabilityIntent() {
        capabilityKeys = RiderAuthAttemptKeys()
        message = ""
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Acceso de Repartidor") },
                navigationIcon = { TextButton(onClick = onBack, enabled = !busy) { Text("VOLVER") } }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "El acceso se valida con Firebase Rider y una sesión operativa de Punto25. Los datos guardados en este teléfono no conceden acceso por sí solos.",
                style = MaterialTheme.typography.bodyMedium
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !capabilityMode,
                    onClick = { if (!busy) { capabilityMode = false; message = "" } },
                    label = { Text("YA TENGO ACCESO") },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = capabilityMode,
                    onClick = { if (!busy) { capabilityMode = true; message = "" } },
                    label = { Text("TENGO CÓDIGO") },
                    modifier = Modifier.weight(1f)
                )
            }

            if (capabilityMode) {
                Text(
                    "Ingresá el código exactamente como lo recibiste. Punto25 determinará si corresponde a alta o recuperación.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = capability,
                    onValueChange = {
                        capability = it
                        resetCapabilityIntent()
                    },
                    label = { Text("Código de acceso") },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = capabilityPassword,
                    onValueChange = {
                        capabilityPassword = it
                        resetCapabilityIntent()
                    },
                    label = { Text("Nueva contraseña") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it
                        resetCapabilityIntent()
                    },
                    label = { Text("Repetir nueva contraseña") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "La contraseña debe tener entre $RIDER_PASSWORD_MIN_LENGTH y $RIDER_PASSWORD_MAX_LENGTH caracteres. No se exige una composición específica.",
                    style = MaterialTheme.typography.bodySmall
                )
                Button(
                    onClick = {
                        if (capabilityPassword != confirmPassword) {
                            message = "Las contraseñas no coinciden."
                            return@Button
                        }
                        if (!riderPasswordIsValid(capabilityPassword) || capability.isEmpty()) {
                            message = "Revisá el código y la longitud de la contraseña."
                            return@Button
                        }
                        busy = true
                        message = ""
                        val keys = capabilityKeys
                        scope.launch {
                            when (val outcome = coordinator.activateWithCapability(capability, capabilityPassword, keys)) {
                                is RiderAuthOutcome.Authorized -> {
                                    capabilityPassword = ""
                                    confirmPassword = ""
                                    onSuccess(outcome.session.riderCode)
                                }
                                is RiderAuthOutcome.Rejected -> message = outcome.recoverableMessage
                            }
                            busy = false
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (busy) "VALIDANDO…" else "CONTINUAR") }
            } else {
                OutlinedTextField(
                    value = riderCode,
                    onValueChange = {
                        riderCode = it
                        resetLoginIntent()
                    },
                    label = { Text("Rider ID") },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = loginPassword,
                    onValueChange = {
                        loginPassword = it
                        resetLoginIntent()
                    },
                    label = { Text("Contraseña") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        if (riderCode.isBlank() || !riderPasswordIsValid(loginPassword)) {
                            message = "No pudimos validar tu acceso. Revisá los datos o intentá nuevamente."
                            return@Button
                        }
                        busy = true
                        message = ""
                        val keys = loginKeys
                        scope.launch {
                            when (val outcome = coordinator.login(riderCode, loginPassword, keys)) {
                                is RiderAuthOutcome.Authorized -> {
                                    loginPassword = ""
                                    onSuccess(outcome.session.riderCode)
                                }
                                is RiderAuthOutcome.Rejected -> message = outcome.recoverableMessage
                            }
                            busy = false
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (busy) "VALIDANDO…" else "INGRESAR") }
            }

            if (message.isNotBlank()) {
                Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(4.dp))
            OutlinedButton(onClick = onBack, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text("CANCELAR")
            }
        }
    }
}
