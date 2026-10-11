package ar.com.mandados.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
internal fun RiderBackendAccessScreen(
    c: MandadosController,
    coordinator: RiderAuthSessionCoordinator,
    initialMessage: String = "",
    onBack: () -> Unit,
    onSuccess: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var capabilityMode by remember { mutableStateOf(true) }
    var capability by remember { mutableStateOf("") }
    var riderCode by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember(initialMessage) { mutableStateOf(initialMessage) }
    var activationKeys by remember { mutableStateOf(RiderAuthAttemptKeys()) }
    var loginKeys by remember { mutableStateOf(RiderLoginAttemptKeys()) }

    fun resetActivationIntent() {
        activationKeys = RiderAuthAttemptKeys()
    }

    fun resetLoginIntent() {
        loginKeys = RiderLoginAttemptKeys()
    }

    fun applyAuthorized(outcome: RiderAuthOutcome.Authorized) {
        if (c.applyAuthenticatedRiderSession(outcome.session)) {
            message = ""
            onSuccess(outcome.session.riderCode)
        } else {
            val revocation = coordinator.beginLogout(outcome.session)
            c.clearAuthenticatedRiderSession()
            message = "No pudimos validar tu acceso. Intentá nuevamente."
            scope.launch { coordinator.finishLogout(revocation) }
        }
    }

    Page("Acceso de Repartidor", onBack) {
        AssistBox(
            "El acceso de Repartidor se valida con Punto25 y una sesión Firebase aislada. " +
                "La contraseña no se guarda en el dispositivo."
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = capabilityMode,
                onClick = { if (!busy) { capabilityMode = true; message = "" } },
                label = { Text("TENGO CÓDIGO") },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = !capabilityMode,
                onClick = { if (!busy) { capabilityMode = false; message = "" } },
                label = { Text("YA TENGO ACCESO") },
                modifier = Modifier.weight(1f)
            )
        }

        if (capabilityMode) {
            OutlinedTextField(
                value = capability,
                onValueChange = {
                    capability = it
                    message = ""
                    resetActivationIntent()
                },
                label = { Text("Código de acceso") },
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                singleLine = true,
                enabled = !busy
            )
            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it.take(RIDER_PASSWORD_MAX_LENGTH)
                    message = ""
                    resetActivationIntent()
                },
                label = { Text("Crear contraseña") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                singleLine = true,
                enabled = !busy
            )
            OutlinedTextField(
                value = confirm,
                onValueChange = {
                    confirm = it.take(RIDER_PASSWORD_MAX_LENGTH)
                    message = ""
                    resetActivationIntent()
                },
                label = { Text("Repetir contraseña") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                singleLine = true,
                enabled = !busy
            )
            Text(
                "Entre 15 y 4096 caracteres. No se exige una combinación específica de mayúsculas, minúsculas, números o símbolos.",
                style = MaterialTheme.typography.bodySmall
            )
            Button(
                enabled = !busy && capability.isNotEmpty() && password.isNotEmpty() && confirm.isNotEmpty(),
                onClick = {
                    message = when {
                        password != confirm -> "Las contraseñas no coinciden."
                        !riderPasswordIsValid(password) -> "La contraseña debe tener entre 15 y 4096 caracteres."
                        else -> ""
                    }
                    if (message.isNotEmpty()) return@Button
                    busy = true
                    c.clearAuthenticatedRiderSession()
                    val stableKeys = activationKeys
                    scope.launch {
                        when (val outcome = coordinator.activateWithCapability(capability, password, stableKeys)) {
                            is RiderAuthOutcome.Authorized -> applyAuthorized(outcome)
                            is RiderAuthOutcome.Rejected -> message = outcome.recoverableMessage
                        }
                        busy = false
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            ) { Text(if (busy) "VALIDANDO…" else "ACTIVAR / RECUPERAR ACCESO") }
        } else {
            OutlinedTextField(
                value = riderCode,
                onValueChange = {
                    riderCode = it.take(64)
                    message = ""
                    resetLoginIntent()
                },
                label = { Text("ID de Repartidor") },
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                singleLine = true,
                enabled = !busy
            )
            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it.take(RIDER_PASSWORD_MAX_LENGTH)
                    message = ""
                    resetLoginIntent()
                },
                label = { Text("Contraseña") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                singleLine = true,
                enabled = !busy
            )
            Button(
                enabled = !busy && riderCode.trim().isNotEmpty() && password.isNotEmpty(),
                onClick = {
                    if (!riderPasswordIsValid(password)) {
                        message = "No pudimos validar tu acceso. Revisá los datos o intentá nuevamente."
                        return@Button
                    }
                    busy = true
                    message = ""
                    c.clearAuthenticatedRiderSession()
                    val stableKeys = loginKeys
                    scope.launch {
                        when (val outcome = coordinator.login(riderCode, password, stableKeys)) {
                            is RiderAuthOutcome.Authorized -> applyAuthorized(outcome)
                            is RiderAuthOutcome.Rejected -> message = outcome.recoverableMessage
                        }
                        busy = false
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            ) { Text(if (busy) "VALIDANDO…" else "INGRESAR") }
        }

        if (busy) {
            Spacer(Modifier.height(12.dp))
            CircularProgressIndicator()
        }
        if (message.isNotBlank()) {
            Text(
                message,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}