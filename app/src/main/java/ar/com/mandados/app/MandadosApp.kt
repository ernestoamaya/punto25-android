package ar.com.mandados.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private enum class Screen {
    REGISTER, WHATSAPP_VERIFY, RIDER_ACCESS, HOME, DELIVERY, SHOPPING, REVIEW, SUBMITTED, HISTORY, ORDER_DETAIL,
    CUSTOMER_PROFILE, CUSTOMER_SUPPORT,
    ADMIN_LOGIN, ADMIN, ADMIN_ORDERS, ADMIN_ORDER_DETAIL, ADMIN_REPORTS, ADMIN_SHIFTS, ADMIN_PAYMENTS, ADMIN_LEGAL,
    RIDERS, RIDER_ADMIN_VIEW, RIDER_WORKSPACE, LOCATION_PICKER
}

private val MandadosLightColors = lightColorScheme(
    primary = Color(0xFF006B5E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD8EEE8),
    onPrimaryContainer = Color(0xFF0A332C),
    secondary = Color(0xFF536B64),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDDE7E2),
    onSecondaryContainer = Color(0xFF253D36),
    tertiary = Color(0xFFB87916),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF6E1B6),
    onTertiaryContainer = Color(0xFF4C350B),
    background = Color(0xFFF4F1E9),
    onBackground = Color(0xFF202522),
    surface = Color(0xFFFCF8F0),
    onSurface = Color(0xFF202522),
    surfaceVariant = Color(0xFFE9E3D8),
    onSurfaceVariant = Color(0xFF565C57),
    outline = Color(0xFF7A817C),
    error = Color(0xFFA84D4D),
    errorContainer = Color(0xFFF4D9D7),
    onErrorContainer = Color(0xFF542323)
)

private val MandadosDarkColors = darkColorScheme(
    primary = Color(0xFF62D7C3),
    onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF174C43),
    onPrimaryContainer = Color(0xFFD2F5ED),
    secondary = Color(0xFFAFCBC2),
    onSecondary = Color(0xFF19372F),
    secondaryContainer = Color(0xFF29453D),
    onSecondaryContainer = Color(0xFFD2E8E1),
    tertiary = Color(0xFFF1B44C),
    onTertiary = Color(0xFF442B00),
    tertiaryContainer = Color(0xFF5F430F),
    onTertiaryContainer = Color(0xFFFFE7B2),
    background = Color(0xFF0D1513),
    onBackground = Color(0xFFE3ECE8),
    surface = Color(0xFF14201D),
    onSurface = Color(0xFFE3ECE8),
    surfaceVariant = Color(0xFF20302C),
    onSurfaceVariant = Color(0xFFBBC9C4),
    outline = Color(0xFF7D918A),
    error = Color(0xFFE18B86),
    errorContainer = Color(0xFF542D2D),
    onErrorContainer = Color(0xFFFFDAD7)
)

private val ReceptionEnabledGreenLight = Color(0xFF146C2E)
private val ReceptionEnabledGreenDark = Color(0xFF6DD58C)
private val ReceptionPausedRedLight = Color(0xFFB3261E)
private val ReceptionPausedRedDark = Color(0xFFFFB4AB)

@Composable
fun MandadosApp() {
    val context = LocalContext.current
    val controller = remember { MandadosController(context.applicationContext) }
    val systemDark = isSystemInDarkTheme()
    val useDark = when (controller.config.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    MaterialTheme(colorScheme = if (useDark) MandadosDarkColors else MandadosLightColors) {
        Surface(modifier = Modifier.fillMaxSize()) {
            MandadosNavigation(controller)
        }
    }
}

@Composable
private fun MandadosNavigation(controller: MandadosController) {
    val context = LocalContext.current
    var screen by rememberSaveable { mutableStateOf(if (controller.customer == null) Screen.REGISTER else Screen.HOME) }
    var selectedOrderId by rememberSaveable { mutableStateOf<String?>(null) }
    var lastOrderId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedRiderId by rememberSaveable { mutableStateOf<String?>(null) }
    var mapTarget by rememberSaveable { mutableStateOf<MapTarget?>(null) }
    var lastRootBackAt by rememberSaveable { mutableStateOf(0L) }
    val adminSession = remember { AdminAccessSession() }
    val protectedAdminScreens = remember {
        setOf(
            Screen.ADMIN,
            Screen.ADMIN_ORDERS,
            Screen.ADMIN_ORDER_DETAIL,
            Screen.ADMIN_REPORTS,
            Screen.ADMIN_SHIFTS,
            Screen.ADMIN_PAYMENTS,
            Screen.ADMIN_LEGAL,
            Screen.RIDERS,
            Screen.RIDER_ADMIN_VIEW
        )
    }
    val requiresAdminReauthorization = screen in protectedAdminScreens &&
        (!adminSession.authorized || !GoogleAuthIntegration.hasCurrentUser(context))

    fun openMap(target: MapTarget) {
        mapTarget = target
        screen = Screen.LOCATION_PICKER
    }

    fun parentForMap(target: MapTarget?): Screen = when (target) {
        MapTarget.DELIVERY_ORIGIN, MapTarget.DELIVERY_DESTINATION -> Screen.DELIVERY
        MapTarget.SHOPPING_PRE_PICKUP, MapTarget.SHOPPING_STORE, MapTarget.SHOPPING_DESTINATION -> Screen.SHOPPING
        null -> Screen.HOME
    }

    fun navigateBack() {
        if (screen == Screen.ADMIN || screen == Screen.ADMIN_LOGIN) adminSession.clear()
        screen = when (screen) {
            Screen.WHATSAPP_VERIFY, Screen.RIDER_ACCESS -> Screen.REGISTER
            Screen.DELIVERY, Screen.SHOPPING, Screen.HISTORY, Screen.CUSTOMER_PROFILE, Screen.CUSTOMER_SUPPORT, Screen.ADMIN_LOGIN -> Screen.HOME
            Screen.REVIEW -> if (controller.draft.serviceType == ServiceType.DELIVERY) Screen.DELIVERY else Screen.SHOPPING
            Screen.SUBMITTED -> Screen.HOME
            Screen.ORDER_DETAIL -> Screen.HISTORY
            Screen.ADMIN -> Screen.HOME
            Screen.ADMIN_ORDERS, Screen.ADMIN_REPORTS, Screen.ADMIN_SHIFTS, Screen.ADMIN_PAYMENTS, Screen.ADMIN_LEGAL, Screen.RIDERS -> Screen.ADMIN
            Screen.ADMIN_ORDER_DETAIL -> Screen.ADMIN_ORDERS
            Screen.RIDER_ADMIN_VIEW -> {
                selectedRiderId = null
                Screen.RIDERS
            }
            Screen.RIDER_WORKSPACE -> {
                controller.logoutRider()
                selectedRiderId = null
                Screen.REGISTER
            }
            Screen.LOCATION_PICKER -> parentForMap(mapTarget)
            Screen.HOME, Screen.REGISTER -> screen
        }
    }

    BackHandler(enabled = true) {
        if (requiresAdminReauthorization) {
            adminSession.clear()
            screen = Screen.HOME
        } else if (screen == Screen.HOME || screen == Screen.REGISTER) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastRootBackAt <= 2_000L) {
                (context as? Activity)?.finish()
            } else {
                lastRootBackAt = now
                Toast.makeText(
                    context,
                    "Presione nuevamente Atrás/Volver para salir de la aplicación",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } else {
            navigateBack()
        }
    }

    if (requiresAdminReauthorization) {
        AdminLoginScreen(
            onBack = {
                adminSession.clear()
                screen = Screen.HOME
            },
            onSuccess = {
                adminSession.apply(AdminAccessResult.AUTHORIZED)
                screen = Screen.ADMIN
            }
        )
    } else when (screen) {
        Screen.REGISTER -> RegisterScreen(
            controller,
            onContinue = { screen = Screen.WHATSAPP_VERIFY },
            onRider = { screen = Screen.RIDER_ACCESS }
        )
        Screen.WHATSAPP_VERIFY -> WhatsAppVerificationScreen(
            controller,
            onVerified = {
                if (controller.confirmRegistration()) screen = Screen.HOME
            },
            onBack = { screen = Screen.REGISTER }
        )
        Screen.RIDER_ACCESS -> RiderAccessScreen(
            controller,
            onBack = { screen = Screen.REGISTER },
            onSuccess = { riderId ->
                selectedRiderId = riderId
                screen = Screen.RIDER_WORKSPACE
            }
        )
        Screen.HOME -> HomeScreen(
            controller,
            onCategory = { category ->
                controller.resetDraftForCategory(category)
                screen = if (category == ServiceCategory.PURCHASE) Screen.SHOPPING else Screen.DELIVERY
            },
            onHistory = { screen = Screen.HISTORY },
            onProfile = { screen = Screen.CUSTOMER_PROFILE },
            onSupport = { screen = Screen.CUSTOMER_SUPPORT },
            onAdmin = {
                adminSession.clear()
                screen = Screen.ADMIN_LOGIN
            },
            onLogout = {
                adminSession.clear()
                GoogleAuthIntegration.signOut(context)
                controller.logoutCustomer()
                screen = Screen.REGISTER
            }
        )
        Screen.DELIVERY -> DeliveryForm(
            controller,
            onBack = { screen = Screen.HOME },
            onContinue = { screen = Screen.REVIEW },
            onMap = ::openMap
        )
        Screen.SHOPPING -> ShoppingForm(
            controller,
            onBack = { screen = Screen.HOME },
            onContinue = { screen = Screen.REVIEW },
            onMap = ::openMap
        )
        Screen.REVIEW -> ReviewScreen(controller, onBack = {
            screen = if (controller.draft.serviceType == ServiceType.DELIVERY) Screen.DELIVERY else Screen.SHOPPING
        }, onSubmit = {
            when (val result = controller.createOrder()) {
                is OrderCreationResult.Blocked -> result.message
                is OrderCreationResult.Created -> {
                    val order = result.order
                    lastOrderId = order.id
                    if (order.operationMode == OperationMode.SIMPLE_WHATSAPP) {
                        openWhatsApp(context, controller.config.whatsappReceiver, order.whatsappMessage)
                    }
                    screen = Screen.SUBMITTED
                    null
                }
            }
        })
        Screen.SUBMITTED -> SubmittedScreen(controller, lastOrderId, onHome = { screen = Screen.HOME }, onWhatsApp = { id ->
            controller.order(id)?.let { openWhatsApp(context, controller.config.whatsappReceiver, it.whatsappMessage) }
        })
        Screen.HISTORY -> HistoryScreen(controller, onBack = { screen = Screen.HOME }, onOrder = { selectedOrderId = it; screen = Screen.ORDER_DETAIL })
        Screen.ORDER_DETAIL -> OrderDetailScreen(controller, selectedOrderId, onBack = { screen = Screen.HISTORY }, onWhatsApp = { order -> openWhatsApp(context, controller.config.whatsappReceiver, order.whatsappMessage) })
        Screen.CUSTOMER_PROFILE -> CustomerProfileScreen(controller, onBack = { screen = Screen.HOME }, onSupport = { screen = Screen.CUSTOMER_SUPPORT })
        Screen.CUSTOMER_SUPPORT -> CustomerSupportScreen(controller, onBack = { screen = Screen.HOME })
        Screen.ADMIN_LOGIN -> AdminLoginScreen(
            onBack = {
                adminSession.clear()
                screen = Screen.HOME
            },
            onSuccess = {
                adminSession.apply(AdminAccessResult.AUTHORIZED)
                screen = Screen.ADMIN
            }
        )
        Screen.ADMIN -> AdminScreen(
            controller,
            onBack = {
                adminSession.clear()
                screen = Screen.HOME
            },
            onOrders = { screen = Screen.ADMIN_ORDERS },
            onRiders = { screen = Screen.RIDERS },
            onReports = { screen = Screen.ADMIN_REPORTS },
            onShifts = { screen = Screen.ADMIN_SHIFTS },
            onPayments = { screen = Screen.ADMIN_PAYMENTS },
            onLegal = { screen = Screen.ADMIN_LEGAL }
        )
        Screen.ADMIN_ORDERS -> AdminOrdersHubScreen(
            controller,
            onBack = { screen = Screen.ADMIN },
            onOrder = { selectedOrderId = it; screen = Screen.ADMIN_ORDER_DETAIL }
        )
        Screen.ADMIN_ORDER_DETAIL -> AdminOrderDetailV2Screen(
            controller,
            selectedOrderId,
            onBack = { screen = Screen.ADMIN_ORDERS }
        )
        Screen.ADMIN_REPORTS -> AdminReportsScreen(controller, onBack = { screen = Screen.ADMIN })
        Screen.ADMIN_SHIFTS -> AdminShiftsScreen(controller, onBack = { screen = Screen.ADMIN })
        Screen.ADMIN_PAYMENTS -> AdminPaymentsScreen(controller, onBack = { screen = Screen.ADMIN })
        Screen.ADMIN_LEGAL -> AdminLegalScreen(controller, onBack = { screen = Screen.ADMIN })
        Screen.RIDERS -> RidersAdminScreenV2(
            controller,
            onBack = { screen = Screen.ADMIN },
            onWorkspace = { riderId ->
                controller.logoutRider()
                selectedRiderId = riderId
                screen = Screen.RIDER_ADMIN_VIEW
            }
        )
        Screen.RIDER_ADMIN_VIEW -> RiderAdminReadOnlyScreen(
            controller,
            selectedRiderId,
            onBack = {
                selectedRiderId = null
                screen = Screen.RIDERS
            }
        )
        Screen.RIDER_WORKSPACE -> {
            val riderId = selectedRiderId
            if (!riderWorkspaceSessionValid(controller, riderId)) {
                LaunchedEffect(riderId) {
                    selectedRiderId = null
                    screen = Screen.RIDER_ACCESS
                }
            } else if (riderId != null && controller.riderOperationalEligibility(riderId).allowed) {
                RiderDashboardScreen(
                    controller,
                    riderId,
                    onBack = {
                        controller.logoutRider()
                        selectedRiderId = null
                        screen = Screen.REGISTER
                    }
                )
            } else if (riderId != null) {
                RiderRestrictedWorkspaceScreen(
                    controller,
                    riderId,
                    onBack = {
                        controller.logoutRider()
                        selectedRiderId = null
                        screen = Screen.RIDER_ACCESS
                    }
                )
            }
        }
        Screen.LOCATION_PICKER -> LocationPickerScreen(
            controller,
            target = mapTarget,
            onBack = { screen = parentForMap(mapTarget) },
            onConfirmed = { screen = parentForMap(mapTarget) }
        )
    }

    val rating = controller.pendingRatingForCustomer()
    val customerContextScreen = screen in setOf(
        Screen.HOME, Screen.DELIVERY, Screen.SHOPPING, Screen.REVIEW, Screen.SUBMITTED,
        Screen.HISTORY, Screen.ORDER_DETAIL, Screen.CUSTOMER_PROFILE, Screen.CUSTOMER_SUPPORT
    )
    if (rating != null && customerContextScreen) {
        Punto25RatingDialog(controller, rating)
    }

    controller.riderDenialFeedback?.let { decision ->
        Punto25AlertDialog(
            onDismissRequest = { controller.clearRiderDenialFeedback() },
            title = { Text("ACCIÓN NO DISPONIBLE") },
            text = { Text(riderDenialMessage(decision)) },
            confirmButton = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    TextButton(onClick = { controller.clearRiderDenialFeedback() }) { Text("ACEPTAR") }
                }
            }
        )
    }
}

@Composable
private fun Page(title: String, onBack: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            content = content
        )
        if (onBack != null) {
            HorizontalDivider(Modifier.padding(top = 8.dp))
            TextButton(onClick = onBack, modifier = Modifier.align(Alignment.Start)) {
                Text(
                    "← VOLVER",
                    color = MaterialTheme.colorScheme.tertiary,
                    fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Composable
private fun RegisterScreen(c: MandadosController, onContinue: () -> Unit, onRider: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf("") }
    var area by rememberSaveable { mutableStateOf("") }
    var sub by rememberSaveable { mutableStateOf("") }
    var busy by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf("") }
    val info = c.areaCodes[area]
    val valid = name.trim().isNotEmpty() && info != null && sub.length == info.subscriberDigits && !area.startsWith("0")
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedBorderColor = Color(0xFF39D1BA),
        unfocusedBorderColor = Color.White.copy(alpha = 0.62f),
        focusedLabelColor = Color.White,
        unfocusedLabelColor = Color.White.copy(alpha = 0.78f),
        cursorColor = Color(0xFF39D1BA),
        disabledTextColor = Color.White.copy(alpha = 0.5f),
        disabledBorderColor = Color.White.copy(alpha = 0.30f),
        disabledLabelColor = Color.White.copy(alpha = 0.45f)
    )

    Box(Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.punto25_login_church),
            contentDescription = "Iglesia de 25 de Mayo",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Surface(
            color = Color(0x2E000D0A),
            modifier = Modifier.fillMaxSize()
        ) {}

        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(34.dp))
            Surface(
                color = Color(0xB8152723),
                contentColor = Color.White,
                shape = RoundedCornerShape(34.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    Modifier.padding(horizontal = 22.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        painter = painterResource(R.drawable.punto25_login_logo),
                        contentDescription = "Punto25",
                        modifier = Modifier.size(112.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("Punto", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
                        Text("25", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold, color = Color(0xFFF4B538))
                    }
                    Spacer(Modifier.height(24.dp))
                    Text("Creá tu cuenta", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                    Text(
                        "Registrate para pedir compras, encargos, trámites y envíos en 25 de Mayo.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.83f),
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Spacer(Modifier.height(18.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(80); error = "" },
                        label = { Text("Nombre y apellido") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = fieldColors
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Número de celular",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "Usá el mismo número que tenés en WhatsApp.",
                        color = Color.White.copy(alpha = 0.77f),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 8.dp)
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = area,
                            onValueChange = { v ->
                                area = v.filter(Char::isDigit).take(4)
                                sub = ""
                                error = ""
                            },
                            label = { Text("Código") },
                            modifier = Modifier.weight(0.42f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = fieldColors
                        )
                        OutlinedTextField(
                            value = sub,
                            onValueChange = { v ->
                                sub = v.filter(Char::isDigit).take(info?.subscriberDigits ?: 8)
                                error = ""
                            },
                            label = { Text("Número") },
                            modifier = Modifier.weight(0.58f),
                            singleLine = true,
                            enabled = info != null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = fieldColors
                        )
                    }
                    if (area.startsWith("0")) {
                        Text("El código de área no puede comenzar con 0.", color = Color(0xFFFFB4AB), style = MaterialTheme.typography.bodySmall)
                    } else if (area.length >= 2 && info == null) {
                        Text("Código de área no reconocido.", color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.bodySmall)
                    } else if (info != null) {
                        Text(
                            info.locality + " · " + info.subscriberDigits + " dígitos locales",
                            color = Color(0xFF76E7D3),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxWidth().padding(top = 5.dp)
                        )
                    }
                    if (error.isNotBlank()) {
                        Text(error, color = Color(0xFFFFB4AB), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                    }
                    Button(
                        enabled = valid && !busy,
                        onClick = {
                            val customer = Customer(name.trim(), area, sub, info!!.locality)
                            c.registerPending(customer)
                            if (GoogleAuthIntegration.isConfigured() && activity != null) {
                                busy = true
                                scope.launch {
                                    GoogleAuthIntegration.signIn(activity)
                                        .onSuccess { identity ->
                                            c.applyGoogleIdentity(identity.uid, identity.email, identity.displayName)
                                            onContinue()
                                        }
                                        .onFailure { error = it.message ?: "No se pudo iniciar sesión con Google." }
                                    busy = false
                                }
                            } else if (BuildConfig.DEBUG) {
                                c.applyDevelopmentIdentity()
                                onContinue()
                            } else {
                                error = "El acceso con Google todavía no está configurado en esta compilación."
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF079E8A),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth().height(54.dp).padding(top = 8.dp)
                    ) {
                        Text(if (busy) "CONECTANDO…" else "CONTINUAR  →", fontWeight = FontWeight.ExtraBold)
                    }
                    Text(
                        "Tu cuenta se protege con Google y verificamos tu WhatsApp antes del primer pedido.",
                        color = Color.White.copy(alpha = 0.64f),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                    HorizontalDivider(Modifier.padding(top = 18.dp), color = Color.White.copy(alpha = 0.20f))
                    Text("25 de Mayo · Buenos Aires", color = Color.White.copy(alpha = 0.78f), modifier = Modifier.padding(top = 12.dp))
                    Text("Tu ciudad en movimiento", color = Color(0xFFF4B538), fontWeight = FontWeight.Bold)
                }
            }
            TextButton(onClick = onRider, modifier = Modifier.padding(top = 8.dp)) {
                Text("SOY REPARTIDOR", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun WhatsAppVerificationScreen(c: MandadosController, onVerified: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var code by rememberSaveable { mutableStateOf("") }
    var targetNumber by rememberSaveable {
        mutableStateOf(BuildConfig.WHATSAPP_VERIFY_NUMBER.ifBlank { c.config.whatsappReceiver })
    }
    var busy by rememberSaveable { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf("") }

    Page("Verificá tu WhatsApp", onBack) {
        Text("Confirmamos que el número de contacto realmente te pertenece antes de habilitar tus pedidos.")
        Spacer(Modifier.height(8.dp))
        AssistBox("Número declarado: " + (c.pendingCustomer?.displayPhone ?: "Sin número") + ". Si verificás desde otro WhatsApp, Punto25 utilizará el número que confirme el webhook.")
        Spacer(Modifier.height(12.dp))

        Button(
            onClick = {
                busy = true
                scope.launch {
                    if (WhatsAppVerificationApi.isConfigured()) {
                        WhatsAppVerificationApi.start(context)
                            .onSuccess { challenge ->
                                code = challenge.code
                                targetNumber = challenge.whatsappNumber
                                openWhatsApp(
                                    context,
                                    targetNumber,
                                    "Hola, quiero verificar mi WhatsApp en Punto25. Código: " + challenge.code
                                )
                                message = "Enviá el mensaje en WhatsApp y volvé a Punto25 para completar la verificación."
                            }
                            .onFailure { message = it.message ?: "No se pudo iniciar la verificación." }
                    } else if (BuildConfig.DEBUG) {
                        code = "P25-DEV"
                        if (targetNumber.isNotBlank()) {
                            openWhatsApp(context, targetNumber, "Hola, quiero verificar mi WhatsApp en Punto25. Código: P25-DEV")
                        }
                        message = "Compilación de prueba: el webhook real se activará al cargar Firebase/Meta. Podés simular la confirmación para continuar el test."
                    } else {
                        message = "La verificación por WhatsApp todavía no está configurada."
                    }
                    busy = false
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (code.isBlank()) "ABRIR WHATSAPP Y VERIFICAR" else "VOLVER A ABRIR WHATSAPP") }

        if (code.isNotBlank() && WhatsAppVerificationApi.isConfigured()) {
            OutlinedButton(
                onClick = {
                    busy = true
                    scope.launch {
                        WhatsAppVerificationApi.status(context, code)
                            .onSuccess { status ->
                                if (status.status.equals("verified", ignoreCase = true) && c.markPendingWhatsappVerified(status.verifiedPhone)) {
                                    onVerified()
                                } else {
                                    message = "Todavía no recibimos la verificación. Enviá el mensaje desde WhatsApp y probá nuevamente."
                                }
                            }
                            .onFailure { message = it.message ?: "No se pudo consultar la verificación." }
                        busy = false
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) { Text("YA ENVIÉ EL MENSAJE · COMPROBAR") }
        }

        if (BuildConfig.DEBUG && !WhatsAppVerificationApi.isConfigured()) {
            OutlinedButton(
                onClick = {
                    if (c.markPendingWhatsappVerified()) onVerified()
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) { Text("SIMULAR RESPUESTA DEL WEBHOOK (DEV)") }
        }
        if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp))
        AssistBox("Punto25 no necesita conocer tu contraseña de Google ni tu contraseña de WhatsApp. La verificación real usa una sesión Google y el número del remitente informado por WhatsApp Business.")
    }
}

@Composable
private fun RiderAccessScreen(c: MandadosController, onBack: () -> Unit, onSuccess: (String) -> Unit) {
    var redeemMode by rememberSaveable { mutableStateOf(true) }
    var code by rememberSaveable { mutableStateOf("") }
    var riderId by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf("") }
    var deactivatedRiderName by rememberSaveable { mutableStateOf<String?>(null) }

    Page("Acceso de Repartidor", onBack) {
        AssistBox("El alta de Repartidores es únicamente por invitación de Administración. Nadie, incluido el Admin, puede ver tu contraseña.")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(redeemMode, { redeemMode = true; error = "" }, { Text("TENGO CÓDIGO") }, modifier = Modifier.weight(1f))
            FilterChip(!redeemMode, { redeemMode = false; error = "" }, { Text("YA TENGO ACCESO") }, modifier = Modifier.weight(1f))
        }

        if (redeemMode) {
            OutlinedTextField(
                code,
                { code = it.uppercase().take(16); error = "" },
                label = { Text("Código de invitación") },
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                singleLine = true
            )
            OutlinedTextField(
                password,
                { password = it; error = "" },
                label = { Text("Crear contraseña") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                singleLine = true
            )
            OutlinedTextField(
                confirm,
                { confirm = it; error = "" },
                label = { Text("Repetir contraseña") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                singleLine = true
            )
            Text("Mínimo 8 caracteres, con mayúscula, minúscula y número.", style = MaterialTheme.typography.bodySmall)
            Button(
                onClick = {
                    error = when {
                        password != confirm -> "Las contraseñas no coinciden."
                        !c.passwordIsStrong(password) -> "La contraseña no cumple los requisitos."
                        else -> {
                            val id = c.redeemRiderInvitation(code, password)
                            if (id != null) {
                                onSuccess(id)
                                ""
                            } else "Código inválido, vencido o ya utilizado."
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            ) { Text("ACTIVAR ACCESO") }
        } else {
            OutlinedTextField(
                riderId,
                { riderId = it.uppercase().take(20); error = "" },
                label = { Text("ID de Repartidor") },
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                singleLine = true
            )
            OutlinedTextField(
                password,
                { password = it; error = "" },
                label = { Text("Contraseña") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                singleLine = true
            )
            Button(
                onClick = {
                    val id = riderId.trim()
                    when (val result = c.authenticateRiderResult(id, password)) {
                        is RiderAuthenticationResult -> when (result.status) {
                            RiderAuthenticationStatus.AUTHENTICATED -> {
                                error = ""
                                onSuccess(result.riderId ?: id)
                            }
                            RiderAuthenticationStatus.DEACTIVATED -> {
                                error = ""
                                deactivatedRiderName = result.riderName.orEmpty()
                            }
                            RiderAuthenticationStatus.INVALID_CREDENTIALS -> {
                                error = "ID o contraseña incorrectos."
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            ) { Text("INGRESAR") }
        }
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
    }

    deactivatedRiderName?.let { name ->
        Punto25AlertDialog(
            onDismissRequest = {},
            title = { Text("USUARIO DESACTIVADO") },
            text = {
                Text("Hola $name. Tu usuario fue desactivado. Para más información, comunicate con el Administrador de Punto25. Muchas gracias")
            },
            confirmButton = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    TextButton(onClick = {
                        deactivatedRiderName = null
                        password = ""
                        confirm = ""
                        c.logoutRider()
                    }) { Text("ACEPTAR") }
                }
            }
        )
    }
}

@Composable
private fun HomeScreen(
    c: MandadosController,
    onCategory: (ServiceCategory) -> Unit,
    onHistory: () -> Unit,
    onProfile: () -> Unit,
    onSupport: () -> Unit,
    onAdmin: () -> Unit,
    onLogout: () -> Unit
) {
    Page("Punto25") {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(R.drawable.ic_punto25_brand),
                        contentDescription = "Punto25",
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("Punto25", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
                        Text("Tu ciudad en movimiento", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("25 de Mayo · Provincia de Buenos Aires", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    if (c.config.operationMode == OperationMode.SIMPLE_WHATSAPP)
                        "Modo Simple · las solicitudes se envían por WhatsApp"
                    else
                        "Compras, encargos, trámites y envíos en tu ciudad",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Text("Hola, ${c.customer?.name ?: ""}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("¿Qué necesitás hoy?", style = MaterialTheme.typography.titleLarge)

        if (!c.config.acceptingOrders) {
            Spacer(Modifier.height(10.dp))
            AssistBox(c.config.closedMessage)
        }

        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ServiceCard("🛒", "Compras", "Compramos en el comercio que necesites", c.config.acceptingOrders, Modifier.weight(1f)) {
                onCategory(ServiceCategory.PURCHASE)
            }
            ServiceCard("📦", "Encargos", "Retiramos y llevamos lo que necesites", c.config.acceptingOrders, Modifier.weight(1f)) {
                onCategory(ServiceCategory.ERRAND)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ServiceCard("📄", "Trámites", "Gestionamos documentación y diligencias", c.config.acceptingOrders, Modifier.weight(1f)) {
                onCategory(ServiceCategory.PROCEDURE)
            }
            ServiceCard("🚚", "Envíos", "Paquetes, sobres y objetos de un punto a otro", c.config.acceptingOrders, Modifier.weight(1f)) {
                onCategory(ServiceCategory.SHIPMENT)
            }
        }

        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onHistory, modifier = Modifier.fillMaxWidth()) { Text("MIS PEDIDOS") }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onSupport, modifier = Modifier.weight(1f)) { Text("SOPORTE") }
            TextButton(onClick = onProfile, modifier = Modifier.weight(1f)) { Text("PERFIL") }
        }

        val recent = c.orders.filter { it.customerId == c.customer?.id }.take(3)
        if (recent.isNotEmpty()) {
            Text("Actividad reciente", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            recent.forEach { o ->
                Card(Modifier.fillMaxWidth().padding(top = 7.dp).clickable { onHistory() }) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(o.id, fontWeight = FontWeight.Bold)
                            Text("${o.createdAt} · ${when(o.category){ServiceCategory.PURCHASE->"Compra";ServiceCategory.ERRAND->"Encargo";ServiceCategory.PROCEDURE->"Trámite";ServiceCategory.SHIPMENT->"Envío"}}")
                        }
                        Text(
                            if (o.operationMode == OperationMode.SIMPLE_WHATSAPP) "WhatsApp" else when(o.status){
                                OrderStatus.AWAITING_QUOTE->"A confirmar"; OrderStatus.PENDING->"Nuevo"; OrderStatus.ACCEPTED->"Aceptado";
                                OrderStatus.IN_PROGRESS->"En curso"; OrderStatus.COMPLETED->"Entregado"; OrderStatus.REJECTED->"Rechazado"; OrderStatus.CANCELLED->"Cancelado"
                            },
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Punto25 · 25 de Mayo", fontWeight = FontWeight.Bold)
                Text("Tu ciudad en movimiento", color = MaterialTheme.colorScheme.primary)
                Text("Movemos lo que necesitás, donde lo necesitás.", style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("Peso máximo: ${c.config.maxWeightKg} kg · Compras hasta ${money(c.config.maxPurchaseAmount)}", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = onAdmin, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) { Text("ADMINISTRACIÓN") }
        TextButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) { Text("Cambiar usuario") }
    }
}

@Composable
private fun ServiceCard(
    icon: String,
    title: String,
    subtitle: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier.clickable(enabled = enabled, onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(icon, style = MaterialTheme.typography.headlineSmall)
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun BigAction(title: String, subtitle: String, enabled: Boolean, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick)) {
        Column(Modifier.padding(18.dp)) { Text(title, fontWeight = FontWeight.Bold); Text(subtitle) }
    }
}

@Composable
private fun DeliveryForm(
    c: MandadosController,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onMap: (MapTarget) -> Unit
) {
    val d = c.draft
    val originOk = d.originAddress.isNotBlank() || d.originLocation != null
    val destinationOk = d.destinationAddress.isNotBlank() || d.destinationLocation != null

    val title = when (d.category) {
        ServiceCategory.ERRAND -> "Encargo"
        ServiceCategory.PROCEDURE -> "Trámite"
        ServiceCategory.SHIPMENT -> "Envío"
        ServiceCategory.PURCHASE -> "Compra"
    }
    Page(title, onBack) {
        Text("Retiro", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Field("Dirección (opcional si marcás el pin)", d.originAddress) { c.draft = c.draft.copy(originAddress = it) }
        Field("Referencia (opcional)", d.originReference) { c.draft = c.draft.copy(originReference = it) }
        ZoneField(c, d.originZoneId, "Zona de retiro") { c.draft = c.draft.copy(originZoneId = it) }
        LocationField("Ubicación de retiro", d.originLocation, onOpen = { onMap(MapTarget.DELIVERY_ORIGIN) }) {
            c.draft = c.draft.copy(originLocation = null)
        }

        Spacer(Modifier.height(16.dp))
        Text("Entrega", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Field("Dirección (opcional si marcás el pin)", d.destinationAddress) { c.draft = c.draft.copy(destinationAddress = it) }
        Field("Referencia (opcional)", d.destinationReference) { c.draft = c.draft.copy(destinationReference = it) }
        ZoneField(c, d.destinationZoneId, "Zona de entrega") { c.draft = c.draft.copy(destinationZoneId = it) }
        LocationField("Ubicación de entrega", d.destinationLocation, onOpen = { onMap(MapTarget.DELIVERY_DESTINATION) }) {
            c.draft = c.draft.copy(destinationLocation = null)
        }

        Spacer(Modifier.height(16.dp))
        Field("¿Qué vamos a transportar? (opcional)", d.carriedItem) { c.draft = c.draft.copy(carriedItem = it) }
        Text("Peso máximo permitido: ${c.config.maxWeightKg} kg", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))
        DeliveryPaymentSelector(c, d.deliveryPayment) { c.draft = c.draft.copy(deliveryPayment = it) }
        Field("Aclaraciones para el Repartidor (opcional)", d.notes, singleLine = false) { c.draft = c.draft.copy(notes = it) }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onContinue,
            enabled = originOk && destinationOk && d.originZoneId.isNotBlank() && d.destinationZoneId.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("REVISAR SOLICITUD") }
        Spacer(Modifier.height(12.dp))
        AssistBox("Cada parada puede identificarse por dirección escrita, por un pin confirmado en el mapa o por ambas. La zona tarifaria sigue siendo una selección independiente.")
    }
}

@Composable
private fun ShoppingForm(
    c: MandadosController,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onMap: (MapTarget) -> Unit
) {
    val d = c.draft
    val needsPre = d.requiresPrePickup()

    fun setInstruction(value: PurchaseInstructionType) {
        var next = c.draft.copy(instructionType = value)
        if (!next.requiresPrePickup() && next.sameDeliveryAsPrePickup) {
            next = next.copy(
                sameDeliveryAsPrePickup = false,
                destinationAddress = "",
                destinationReference = "",
                destinationZoneId = "",
                destinationLocation = null
            )
        }
        c.draft = next
    }

    fun setPurchasePayment(value: PurchasePaymentMethod) {
        var next = c.draft.copy(purchasePayment = value)
        if (!next.requiresPrePickup() && next.sameDeliveryAsPrePickup) {
            next = next.copy(
                sameDeliveryAsPrePickup = false,
                destinationAddress = "",
                destinationReference = "",
                destinationZoneId = "",
                destinationLocation = null
            )
        }
        c.draft = next
    }

    fun setSameDelivery(checked: Boolean) {
        val current = c.draft
        c.draft = if (checked) {
            current.copy(
                sameDeliveryAsPrePickup = true,
                destinationAddress = current.prePickupAddress,
                destinationReference = current.prePickupReference,
                destinationZoneId = current.prePickupZoneId,
                destinationLocation = current.prePickupLocation
            )
        } else {
            current.copy(
                sameDeliveryAsPrePickup = false,
                destinationAddress = "",
                destinationReference = "",
                destinationZoneId = "",
                destinationLocation = null
            )
        }
    }

    Page("Realizar un pedido", onBack) {
        Text("¿Cómo nos vas a indicar qué necesitás?", fontWeight = FontWeight.Bold)
        EnumRadio("Escribir el pedido en la app", d.instructionType == PurchaseInstructionType.IN_APP) { setInstruction(PurchaseInstructionType.IN_APP) }
        EnumRadio("Entregar lista / nota / receta al Repartidor", d.instructionType == PurchaseInstructionType.PHYSICAL_NOTE) { setInstruction(PurchaseInstructionType.PHYSICAL_NOTE) }
        EnumRadio("Indicarlo personalmente cuando pase el Repartidor", d.instructionType == PurchaseInstructionType.IN_PERSON) { setInstruction(PurchaseInstructionType.IN_PERSON) }

        if (d.instructionType == PurchaseInstructionType.IN_APP) {
            Field("¿Qué necesitás? *", d.purchaseDescription, singleLine = false) { c.draft = c.draft.copy(purchaseDescription = it) }
        }

        MoneyField("Presupuesto máximo de compra (opcional)", d.purchaseMaxAmount, c.config.maxPurchaseAmount) {
            c.draft = c.draft.copy(purchaseMaxAmount = it)
        }
        Text(
            "Tope permitido: ${money(c.config.maxPurchaseAmount)} · Peso máximo: ${c.config.maxWeightKg} kg",
            style = MaterialTheme.typography.bodySmall
        )

        Spacer(Modifier.height(16.dp))
        Text("Comercio de retiro", fontWeight = FontWeight.Bold)
        Field("Nombre del comercio", d.storeName) { c.draft = c.draft.copy(storeName = it) }
        Field("Dirección del comercio", d.storeAddress) { c.draft = c.draft.copy(storeAddress = it) }
        ZoneField(c, d.storeZoneId, "Zona del comercio (opcional)", allowBlank = true) {
            c.draft = c.draft.copy(storeZoneId = it)
        }
        LocationField("Ubicación del comercio", d.storeLocation, onOpen = { onMap(MapTarget.SHOPPING_STORE) }) {
            c.draft = c.draft.copy(storeLocation = null)
        }

        Spacer(Modifier.height(16.dp))
        Text("¿Cómo se abonará la compra?", fontWeight = FontWeight.Bold)
        EnumRadio("Ya está paga / pago directo al comercio", d.purchasePayment == PurchasePaymentMethod.DIRECT_TO_STORE) {
            setPurchasePayment(PurchasePaymentMethod.DIRECT_TO_STORE)
        }
        EnumRadio("Transferencia previa al Repartidor", d.purchasePayment == PurchasePaymentMethod.TRANSFER_TO_RIDER) {
            setPurchasePayment(PurchasePaymentMethod.TRANSFER_TO_RIDER)
        }
        EnumRadio("Efectivo al Repartidor antes de comprar", d.purchasePayment == PurchasePaymentMethod.CASH_PRE_PICKUP) {
            setPurchasePayment(PurchasePaymentMethod.CASH_PRE_PICKUP)
        }

        if (needsPre) {
            Spacer(Modifier.height(14.dp))
            Text("Retiro previo", fontWeight = FontWeight.Bold)
            Text("Necesario para retirar efectivo y/o lista, nota, receta o indicaciones.")
            Field("Dirección (opcional si marcás el pin)", d.prePickupAddress) { value ->
                val current = c.draft
                c.draft = if (current.sameDeliveryAsPrePickup) {
                    current.copy(prePickupAddress = value, destinationAddress = value)
                } else {
                    current.copy(prePickupAddress = value)
                }
            }
            Field("Referencia", d.prePickupReference) { value ->
                val current = c.draft
                c.draft = if (current.sameDeliveryAsPrePickup) {
                    current.copy(prePickupReference = value, destinationReference = value)
                } else {
                    current.copy(prePickupReference = value)
                }
            }
            ZoneField(c, d.prePickupZoneId, "Zona del retiro previo") { value ->
                val current = c.draft
                c.draft = if (current.sameDeliveryAsPrePickup) {
                    current.copy(prePickupZoneId = value, destinationZoneId = value)
                } else {
                    current.copy(prePickupZoneId = value)
                }
            }
            LocationField(
                "Ubicación del retiro previo",
                d.prePickupLocation,
                onOpen = { onMap(MapTarget.SHOPPING_PRE_PICKUP) }
            ) {
                val current = c.draft
                c.draft = if (current.sameDeliveryAsPrePickup) {
                    current.copy(prePickupLocation = null, destinationLocation = null)
                } else {
                    current.copy(prePickupLocation = null)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Entrega", fontWeight = FontWeight.Bold)
        if (needsPre) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { setSameDelivery(!d.sameDeliveryAsPrePickup) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = d.sameDeliveryAsPrePickup, onCheckedChange = null)
                Spacer(Modifier.width(6.dp))
                Text("La entrega es en la misma dirección del retiro previo")
            }
        }

        Field(
            "Dirección de entrega (opcional si marcás el pin)",
            d.destinationAddress,
            enabled = !d.sameDeliveryAsPrePickup
        ) { c.draft = c.draft.copy(destinationAddress = it) }
        Field(
            "Referencia",
            d.destinationReference,
            enabled = !d.sameDeliveryAsPrePickup
        ) { c.draft = c.draft.copy(destinationReference = it) }
        ZoneField(
            c,
            d.destinationZoneId,
            "Zona de entrega",
            enabled = !d.sameDeliveryAsPrePickup
        ) { c.draft = c.draft.copy(destinationZoneId = it) }
        LocationField(
            "Ubicación de entrega",
            d.destinationLocation,
            enabled = !d.sameDeliveryAsPrePickup,
            onOpen = { onMap(MapTarget.SHOPPING_DESTINATION) }
        ) {
            c.draft = c.draft.copy(destinationLocation = null)
        }

        Spacer(Modifier.height(12.dp))
        DeliveryPaymentSelector(c, d.deliveryPayment) { c.draft = c.draft.copy(deliveryPayment = it) }
        Field("Aclaraciones para el Repartidor (opcional)", d.notes, singleLine = false) {
            c.draft = c.draft.copy(notes = it)
        }

        val descOk = d.instructionType != PurchaseInstructionType.IN_APP || d.purchaseDescription.isNotBlank()
        val preOk = !needsPre || ((d.prePickupAddress.isNotBlank() || d.prePickupLocation != null) && d.prePickupZoneId.isNotBlank())
        val destinationOk = (d.destinationAddress.isNotBlank() || d.destinationLocation != null) && d.destinationZoneId.isNotBlank()
        val amountOk = d.purchaseMaxAmount <= c.config.maxPurchaseAmount

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onContinue,
            enabled = descOk && preOk && destinationOk && amountOk,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("REVISAR SOLICITUD")
        }
    }
}

@Composable
private fun ReviewScreen(c: MandadosController, onBack: () -> Unit, onSubmit: () -> String?) {
    val context = LocalContext.current
    val p = c.pricing()
    val pendingLegal = c.requiredLegalDocumentsForCustomer()
    var legalAccepted by rememberSaveable(pendingLegal.joinToString("|") { it.id }) { mutableStateOf(pendingLegal.isEmpty()) }
    var submitError by rememberSaveable { mutableStateOf("") }

    Page("Confirmar solicitud", onBack) {
        Text(
            when (c.draft.category) {
                ServiceCategory.PURCHASE -> "Compra"
                ServiceCategory.ERRAND -> "Encargo"
                ServiceCategory.PROCEDURE -> "Trámite"
                ServiceCategory.SHIPMENT -> "Envío"
            },
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(12.dp))
        Text(c.pricingTextForUi(), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(14.dp))
        if (p.needsQuote) AssistBox("La tarifa queda a confirmar: ${p.issue ?: "zona sin definir"}.")
        PriceLine("Tarifa base", p.baseAmount)
        if ((p.prePickupAmount ?: 0) > 0) PriceLine("Retiro previo", p.prePickupAmount)
        if ((p.rainAmount ?: 0) > 0) PriceLine("Lluvia / zona con barro", p.rainAmount)
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        PriceLine("TOTAL SERVICIO", p.totalAmount, bold = true)

        if (pendingLegal.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("Documentos legales", fontWeight = FontWeight.Bold)
            pendingLegal.forEach { doc ->
                Card(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(doc.title, fontWeight = FontWeight.SemiBold)
                            Text("Versión ${doc.version}", style = MaterialTheme.typography.bodySmall)
                        }
                        if (doc.fileUri != null) {
                            TextButton(onClick = { openPdfUri(context, doc.fileUri) }) { Text("VER PDF") }
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().clickable { legalAccepted = !legalAccepted }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = legalAccepted, onCheckedChange = null)
                Spacer(Modifier.width(6.dp))
                Text("Leí y acepto las versiones vigentes indicadas arriba.")
            }
        }

        if (!c.config.acceptingOrders) {
            Spacer(Modifier.height(12.dp))
            AssistBox(c.config.closedMessage)
        } else if (submitError.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            AssistBox(submitError)
        }

        Spacer(Modifier.height(12.dp))
        AssistBox(
            if (c.config.operationMode == OperationMode.SIMPLE_WHATSAPP)
                "Al enviar, Punto25 abrirá WhatsApp con el detalle de la solicitud. La gestión continuará por ese canal."
            else
                "Al enviar, la solicitud queda PENDIENTE DE ACEPTACIÓN. No significa que el servicio ya haya sido tomado."
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                if (legalAccepted && c.config.acceptingOrders) {
                    c.acceptRequiredLegalDocuments()
                    submitError = onSubmit().orEmpty()
                }
            },
            enabled = legalAccepted && c.config.acceptingOrders,
            modifier = Modifier.fillMaxWidth()
        ) { Text("ENVIAR SOLICITUD") }
    }
}

private fun MandadosController.pricingTextForUi(): String = buildString {
    val d = draft
    fun pointLabel(point: GeoPoint?): String =
        point?.let { " · pin ${"%.5f".format(it.latitude)}, ${"%.5f".format(it.longitude)}" } ?: ""

    if (d.serviceType == ServiceType.DELIVERY) {
        appendLine("Retiro: ${d.originAddress.ifBlank { "Ubicación indicada en mapa" }}${pointLabel(d.originLocation)}")
        appendLine("Entrega: ${d.destinationAddress.ifBlank { "Ubicación indicada en mapa" }}${pointLabel(d.destinationLocation)}")
        if (d.carriedItem.isNotBlank()) appendLine("Contenido: ${d.carriedItem}")
    } else {
        appendLine("Pedido: ${instructionText(d.instructionType)}")
        if (d.purchaseDescription.isNotBlank()) appendLine(d.purchaseDescription)
        if (d.requiresPrePickup()) {
            val purpose = prePickupPurposeText(d)
            val label = when {
                purpose == "efectivo" -> "Retiro de efectivo"
                purpose.isNotBlank() -> "Retiro previo — $purpose"
                else -> "Retiro previo"
            }
            appendLine("$label: ${d.prePickupAddress.ifBlank { "Ubicación indicada en mapa" }}${pointLabel(d.prePickupLocation)}")
        }
        val store = listOf(d.storeName, d.storeAddress).filter { it.isNotBlank() }.joinToString(" · ")
        appendLine("Comercio de retiro: ${store.ifBlank { "No especificado" }}${pointLabel(d.storeLocation)}")
        appendLine("Entrega: ${d.destinationAddress.ifBlank { "Ubicación indicada en mapa" }}${pointLabel(d.destinationLocation)}")
        appendLine("Pago compra: ${purchasePaymentText(d.purchasePayment)}")
    }
    appendLine("Pago delivery: ${deliveryPaymentText(d.deliveryPayment)}")
    if (d.notes.isNotBlank()) appendLine("Aclaraciones: ${d.notes}")
}

@Composable
private fun SubmittedScreen(c: MandadosController, id: String?, onHome: () -> Unit, onWhatsApp: (String) -> Unit) {
    val order = c.orders.firstOrNull { it.id == id }
    Page(if (order?.operationMode == OperationMode.SIMPLE_WHATSAPP) "Solicitud lista para WhatsApp" else "Solicitud registrada") {
        Text(order?.id ?: "", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        if (order?.operationMode == OperationMode.SIMPLE_WHATSAPP) {
            Text("MODO SIMPLE · WHATSAPP", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text("Punto25 abrió WhatsApp automáticamente con el pedido preparado. Si no llegaste a enviarlo o WhatsApp no se abrió, usá el botón de abajo para reintentarlo.")
        } else {
            Text(if (order?.status == OrderStatus.AWAITING_QUOTE) "PENDIENTE DE CONFIRMACIÓN DE TARIFA" else "PENDIENTE DE ACEPTACIÓN", color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(10.dp))
            Text("El pedido quedó registrado para la operación Multi-Repartidor.")
        }
        Spacer(Modifier.height(14.dp))
        Button(onClick = { if (order != null) onWhatsApp(order.id) }, enabled = order != null, modifier = Modifier.fillMaxWidth()) {
            Text(if (order?.operationMode == OperationMode.SIMPLE_WHATSAPP) "ENVIAR / REABRIR EN WHATSAPP" else "ABRIR WHATSAPP Y ENVIAR DETALLE")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onHome, modifier = Modifier.fillMaxWidth()) { Text("VOLVER AL INICIO") }
        if (order?.operationMode != OperationMode.SIMPLE_WHATSAPP) {
            Spacer(Modifier.height(14.dp))
            AssistBox("La sincronización entre dispositivos y las notificaciones push todavía dependen de la futura conexión con Firebase.")
        }
    }
}

@Composable
private fun HistoryScreen(c: MandadosController, onBack: () -> Unit, onOrder: (String) -> Unit) {
    val context = LocalContext.current
    val customerId = c.customer?.id
    var fromDate by rememberSaveable { mutableStateOf(defaultReportFrom()) }
    var toDate by rememberSaveable { mutableStateOf(defaultReportTo()) }
    val all = c.orders.filter { customerId == null || it.customerId == customerId }
    val items = filterOrdersForPeriod(all, fromDate, toDate)
        .sortedByDescending { c.parseTimestamp(it.createdAt) ?: java.time.LocalDateTime.MIN }

    Page("Mis pedidos", onBack) {
        DateRangePicker(fromDate, toDate, { if (it != null) fromDate = it }, { if (it != null) toDate = it })
        OutlinedButton(
            onClick = {
                PdfReports.shareOrders(
                    context,
                    "Mis_Pedidos_Punto25",
                    fromDate,
                    toDate,
                    items,
                    c,
                    extraSummary = listOf("Cliente" to (c.customer?.name ?: ""), "Pedidos" to items.size.toString())
                )
            },
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
        ) { Text("EXPORTAR PDF") }

        if (items.isEmpty()) Text("No hay solicitudes en el período seleccionado.", modifier = Modifier.padding(top = 12.dp))
        items.forEach { o ->
            Card(Modifier.fillMaxWidth().padding(top = 10.dp).clickable { onOrder(o.id) }) {
                Column(Modifier.padding(14.dp)) {
                    Text(o.id, fontWeight = FontWeight.Bold)
                    Text(o.createdAt)
                    Text(
                        when (o.category) {
                            ServiceCategory.PURCHASE -> "Compra"
                            ServiceCategory.ERRAND -> "Encargo"
                            ServiceCategory.PROCEDURE -> "Trámite"
                            ServiceCategory.SHIPMENT -> "Envío"
                        }
                    )
                    Text(
                        if (o.operationMode == OperationMode.SIMPLE_WHATSAPP) "Solicitud por WhatsApp"
                        else statusText(o.status),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text("Servicio: ${moneyNullable(o.totalAmount)}")
                    c.deliveryDurationSeconds(o)?.let { seconds ->
                        Text("Tiempo de entrega: " + formatDurationUi(seconds))
                    }
                }
            }
        }
    }
}

@Composable
private fun OrderDetailScreen(c: MandadosController, id: String?, onBack: () -> Unit, onWhatsApp: (LocalOrder) -> Unit) {
    val o = c.order(id)
    val context = LocalContext.current
    var confirmCancel by rememberSaveable { mutableStateOf(false) }

    val proofPicker = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        if (uri != null && o != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            c.attachTransferProof(o.id, uri.toString())
        }
    }

    Page("Detalle del pedido", onBack) {
        if (o == null) {
            Text("Pedido no encontrado")
        } else {
            Text(o.id, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(o.createdAt)
            Text(
                if (o.operationMode == OperationMode.SIMPLE_WHATSAPP) "Solicitud por WhatsApp" else statusText(o.status),
                color = MaterialTheme.colorScheme.primary
            )
            if (o.operationMode == OperationMode.MULTI_RIDER) {
                c.rider(o.assignedRiderId)?.let { Text("Repartidor asignado: ${it.name}") }
                c.deliveryDurationSeconds(o)?.let { Text("Tiempo de entrega: " + formatDurationUi(it), fontWeight = FontWeight.Bold) }
            }
            Spacer(Modifier.height(12.dp))
            Text(o.detail)

            Spacer(Modifier.height(12.dp))
            val payment = if (o.operationMode == OperationMode.MULTI_RIDER) c.paymentForOrder(o.id) else null
            if (payment != null) {
                Text("Pago", fontWeight = FontWeight.Bold)
                Text(if (payment.channel == PaymentChannel.RIDER_TRANSFER) transferPaymentStatusLabel(payment.status) else "${payment.channel} · ${payment.status}", color = if (payment.channel == PaymentChannel.RIDER_TRANSFER) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                Text("Importe esperado: ${moneyNullable(payment.expectedAmount)}")

                if (payment.channel == PaymentChannel.RIDER_TRANSFER) {
                    val rider = c.rider(o.assignedRiderId)
                    val alias = rider?.transferAlias.orEmpty().ifBlank { c.config.paymentConfig.centralAlias }
                    if (alias.isNotBlank()) {
                        Text("Alias: $alias", color = MaterialTheme.colorScheme.primary)
                    }

                    if (payment.status == PaymentStatus.PENDING) {
                        OutlinedButton(
                            onClick = { c.declarePayment(o.id) },
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                        ) { Text("YA TRANSFERÍ") }
                    }

                    if (payment.status in setOf(PaymentStatus.DECLARED, PaymentStatus.PROOF_UPLOADED, PaymentStatus.IN_REVIEW)) {
                        OutlinedButton(
                            onClick = { proofPicker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) },
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                        ) { Text(if (payment.proofUri.isNullOrBlank()) if (c.config.paymentConfig.transferProofRequired) "ADJUNTAR COMPROBANTE" else "ADJUNTAR COMPROBANTE (OPCIONAL)" else "REEMPLAZAR COMPROBANTE") }
                    }
                    if (payment.status == PaymentStatus.DECLARED) AssistBox("Transferencia informada · pendiente de confirmación del Repartidor.")
    if (payment.status == PaymentStatus.PROOF_UPLOADED) AssistBox("Comprobante adjunto · pendiente de confirmación del Repartidor.")
    if (payment.status == PaymentStatus.IN_REVIEW) AssistBox("Transferencia en revisión · el Repartidor todavía no visualiza la acreditación.")
    if (!payment.proofUri.isNullOrBlank()) Text("Comprobante adjunto a este pedido.")
    if (payment.status == PaymentStatus.CONFIRMED) AssistBox("Pago confirmado por el Repartidor.")
                }
            }

            val digitalTip = if (o.operationMode == OperationMode.MULTI_RIDER) c.customerDigitalTipForOrder(o.id) else null
            if (digitalTip != null) {
                Spacer(Modifier.height(12.dp))
                Text("Propina por transferencia", fontWeight = FontWeight.Bold)
                Text("Importe: ${moneyNullable(digitalTip.tipAmount)}")
                val tipRider = c.rider(o.assignedRiderId)
                val tipAlias = tipRider?.transferAlias.orEmpty().ifBlank { c.config.paymentConfig.centralAlias }
                if (tipAlias.isNotBlank()) {
                    Text("Alias: $tipAlias", color = MaterialTheme.colorScheme.primary)
                }
                when (digitalTip.tipStatus) {
                    TipStatus.SELECTED -> {
                        AssistBox("La propina es una transferencia adicional separada del pago principal.")
                        OutlinedButton(
                            onClick = { c.declareTipTransfer(o.id) },
                            enabled = c.customerCanDeclareTipTransfer(o.id),
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                        ) { Text("YA TRANSFERÍ LA PROPINA") }
                    }
                    TipStatus.TRANSFER_DECLARED -> AssistBox("Transferencia de propina informada · pendiente de confirmación del Repartidor.")
                    TipStatus.CONFIRMED -> AssistBox("Propina confirmada por el Repartidor.")
                    TipStatus.NONE -> Unit
                }
            }

            Spacer(Modifier.height(12.dp))
            Button(onClick = { onWhatsApp(o) }, modifier = Modifier.fillMaxWidth()) {
                Text("CONTACTAR / ENVIAR POR WHATSAPP")
            }

            if (o.operationMode == OperationMode.SIMPLE_WHATSAPP) {
                Spacer(Modifier.height(8.dp))
                AssistBox("Esta solicitud pertenece al modo Simple. Cualquier cambio o cancelación se coordina por WhatsApp.")
            } else if (o.status == OrderStatus.PENDING || o.status == OrderStatus.AWAITING_QUOTE) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { confirmCancel = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("CANCELAR SOLICITUD")
                }
            } else if (o.status == OrderStatus.ACCEPTED || o.status == OrderStatus.IN_PROGRESS) {
                Spacer(Modifier.height(8.dp))
                AssistBox("El pedido ya fue aceptado. Para cancelarlo, contactá al Repartidor/Administración.")
            }
        }
    }

    if (confirmCancel && o != null) {
        Punto25AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text("Cancelar solicitud") },
            text = { Text("¿Querés cancelar este pedido? Quedará registrado como Cancelado en el historial.") },
            confirmButton = {
                TextButton(onClick = {
                    c.cancelOrderByCustomer(o.id)
                    confirmCancel = false
                }) { Text("SÍ, CANCELAR") }
            },
            dismissButton = {
                TextButton(onClick = { confirmCancel = false }) { Text("NO") }
            }
        )
    }
}

@Composable
private fun AdminLoginScreen(onBack: () -> Unit, onSuccess: () -> Unit) {
    val context = LocalContext.current
    var attempt by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf<AdminAccessResult?>(null) }

    LaunchedEffect(attempt) {
        result = null
        val checked = AdminAccessApi.check(context)
        result = checked
        if (checked == AdminAccessResult.AUTHORIZED) onSuccess()
    }

    Page("Administración", onBack) {
        when (result) {
            null -> {
                CircularProgressIndicator()
                Text("Verificando acceso…", modifier = Modifier.padding(top = 12.dp))
            }
            AdminAccessResult.AUTHORIZED -> Text("Acceso autorizado.")
            AdminAccessResult.UNAUTHORIZED -> {
                Text("Acceso no autorizado.", color = MaterialTheme.colorScheme.error)
                OutlinedButton(
                    onClick = { attempt += 1 },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                ) { Text("VOLVER A VERIFICAR") }
            }
            AdminAccessResult.UNAVAILABLE -> {
                Text("Servicio temporalmente no disponible.", color = MaterialTheme.colorScheme.error)
                OutlinedButton(
                    onClick = { attempt += 1 },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                ) { Text("REINTENTAR") }
            }
        }
    }
}

@Composable
private fun AdminScreen(
    c: MandadosController,
    onBack: () -> Unit,
    onOrders: () -> Unit,
    onRiders: () -> Unit,
    onReports: () -> Unit,
    onShifts: () -> Unit,
    onPayments: () -> Unit,
    onLegal: () -> Unit
) {
    val cfg = c.config
    val darkReceptionPalette = MaterialTheme.colorScheme.background == MandadosDarkColors.background
    val receptionEnabledGreen = if (darkReceptionPalette) ReceptionEnabledGreenDark else ReceptionEnabledGreenLight
    val receptionPausedRed = if (darkReceptionPalette) ReceptionPausedRedDark else ReceptionPausedRedLight
    var editingZoneId by rememberSaveable { mutableStateOf<String?>(null) }
    var newZoneOpen by rememberSaveable { mutableStateOf(false) }

    Page("Panel de Administración", onBack) {
        Text("Modo operativo", fontWeight = FontWeight.Bold)
        EnumRadio("Simple · pedidos por WhatsApp", cfg.operationMode == OperationMode.SIMPLE_WHATSAPP) {
            c.updateConfig(c.config.copy(operationMode = OperationMode.SIMPLE_WHATSAPP))
        }
        EnumRadio("Multi-Repartidor", cfg.operationMode == OperationMode.MULTI_RIDER) {
            c.updateConfig(c.config.copy(operationMode = OperationMode.MULTI_RIDER))
        }
        AssistBox(
            if (cfg.operationMode == OperationMode.SIMPLE_WHATSAPP)
                "Modo Simple: el cliente arma la solicitud y Punto25 la envía por WhatsApp. No se usan turnos, disponibilidad ni toma interna de pedidos."
            else
                "Modo Multi-Repartidor: se habilitan turnos, disponibilidad, asignación, balances y operación interna."
        )

        Spacer(Modifier.height(12.dp))
        Button(onClick = onOrders, modifier = Modifier.fillMaxWidth()) { Text("PEDIDOS (${c.orders.size})") }
        if (cfg.operationMode == OperationMode.MULTI_RIDER) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onRiders, modifier = Modifier.fillMaxWidth()) { Text("GESTIÓN DE REPARTIDORES (${c.riders.size})") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onReports, modifier = Modifier.fillMaxWidth()) { Text("REPORTES") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onShifts, modifier = Modifier.fillMaxWidth()) { Text("TURNOS") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onPayments, modifier = Modifier.fillMaxWidth()) { Text("PAGOS Y PROPINAS") }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onLegal, modifier = Modifier.fillMaxWidth()) { Text("LEGAL Y PRIVACIDAD") }

        Spacer(Modifier.height(18.dp))
        Text("Apariencia", fontWeight = FontWeight.Bold)
        EnumRadio("Predeterminado del sistema", cfg.themeMode == ThemeMode.SYSTEM) {
            c.updateConfig(c.config.copy(themeMode = ThemeMode.SYSTEM))
        }
        EnumRadio("Claro", cfg.themeMode == ThemeMode.LIGHT) { c.updateConfig(c.config.copy(themeMode = ThemeMode.LIGHT)) }
        EnumRadio("Oscuro", cfg.themeMode == ThemeMode.DARK) { c.updateConfig(c.config.copy(themeMode = ThemeMode.DARK)) }

        Spacer(Modifier.height(14.dp))
        Text("RECEPCIÓN DE NUEVAS SOLICITUDES", fontWeight = FontWeight.Bold)
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (cfg.acceptingOrders) "✓ HABILITADA" else "⛔ PAUSADA",
                    color = if (cfg.acceptingOrders) receptionEnabledGreen else receptionPausedRed,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = cfg.acceptingOrders,
                    onCheckedChange = { c.updateConfig(c.config.copy(acceptingOrders = it)) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.surface,
                        checkedTrackColor = receptionEnabledGreen,
                        checkedBorderColor = receptionEnabledGreen,
                        uncheckedThumbColor = MaterialTheme.colorScheme.surface,
                        uncheckedTrackColor = receptionPausedRed,
                        uncheckedBorderColor = receptionPausedRed
                    )
                )
            }
        }
        Field("Mensaje cuando la recepción está pausada", cfg.closedMessage, singleLine = false) {
            c.updateConfig(c.config.copy(closedMessage = it))
        }

        Spacer(Modifier.height(14.dp))
        Text("WhatsApp receptor", fontWeight = FontWeight.Bold)
        Field("Número (10 dígitos nacionales o 54...)", cfg.whatsappReceiver) {
            c.updateConfig(c.config.copy(whatsappReceiver = it.filter(Char::isDigit)))
        }

        Spacer(Modifier.height(14.dp))
        Text("Límites", fontWeight = FontWeight.Bold)
        IntConfigField("Compra máxima", cfg.maxPurchaseAmount) { c.updateConfig(c.config.copy(maxPurchaseAmount = it)) }
        IntConfigField("Peso máximo (kg)", cfg.maxWeightKg) { c.updateConfig(c.config.copy(maxWeightKg = it)) }
        if (cfg.operationMode == OperationMode.MULTI_RIDER) {
            IntConfigField("Máximo simultáneo general por Repartidor", cfg.defaultMaxConcurrentOrders) {
                c.updateConfig(c.config.copy(defaultMaxConcurrentOrders = it.coerceAtLeast(1)))
            }
        }

        Spacer(Modifier.height(14.dp))
        Text("Soporte", fontWeight = FontWeight.Bold)
        Field("WhatsApp de soporte", cfg.supportWhatsapp) {
            c.updateConfig(c.config.copy(supportWhatsapp = it.filter(Char::isDigit)))
        }

        Spacer(Modifier.height(14.dp))
        Text("Adicional lluvia / zona con barro", fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Activo", modifier = Modifier.weight(1f))
            Switch(cfg.rainEnabled, { c.updateConfig(c.config.copy(rainEnabled = it)) })
        }
        IntConfigField("Importe lluvia/barro", cfg.rainAmount) { c.updateConfig(c.config.copy(rainAmount = it)) }

        Spacer(Modifier.height(14.dp))
        Text("Retiro previo de efectivo/lista/receta", fontWeight = FontWeight.Bold)
        EnumRadio("Inactivo", cfg.prePickupMode == PrePickupMode.OFF) { c.updateConfig(c.config.copy(prePickupMode = PrePickupMode.OFF)) }
        EnumRadio("Monto fijo", cfg.prePickupMode == PrePickupMode.FIXED) { c.updateConfig(c.config.copy(prePickupMode = PrePickupMode.FIXED)) }
        EnumRadio("Porcentaje de tarifa base", cfg.prePickupMode == PrePickupMode.PERCENT_BASE) { c.updateConfig(c.config.copy(prePickupMode = PrePickupMode.PERCENT_BASE)) }
        if (cfg.prePickupMode != PrePickupMode.OFF) {
            IntConfigField(if (cfg.prePickupMode == PrePickupMode.FIXED) "Monto fijo" else "Porcentaje", cfg.prePickupValue) {
                c.updateConfig(c.config.copy(prePickupValue = it))
            }
        }

        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Tarifas por zona", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = { newZoneOpen = true }) { Text("+ AGREGAR") }
        }
        Text("Las zonas utilizadas por pedidos históricos no se eliminan: pueden renombrarse o desactivarse sin cambiar su ID interno.", style = MaterialTheme.typography.bodySmall)

        zonesForPresentation(cfg.zones).forEach { z ->
            Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(z.name, fontWeight = FontWeight.Bold)
                            Text("${z.category} · ${if (z.price > 0) money(z.price) else "Sin tarifa"}", style = MaterialTheme.typography.bodySmall)
                            if (z.description.isNotBlank()) Text(z.description, style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(z.enabled, { enabled ->
                            c.updateZone(z.id, z.name, z.description, z.category, z.price, enabled)
                        })
                    }
                    OutlinedButton(onClick = { editingZoneId = z.id }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                        Text("EDITAR ZONA")
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        AssistBox("Esta Alpha continúa guardando los datos localmente en el dispositivo. Firebase/Firestore sigue siendo la siguiente etapa para sincronización multi-dispositivo.")
    }

    if (newZoneOpen) {
        ZoneAdminDialog(
            zone = null,
            onDismiss = { newZoneOpen = false },
            onSave = { name, description, category, price ->
                if (c.addZone(name, description, category, price)) newZoneOpen = false
            }
        )
    }
    editingZoneId?.let { id ->
        c.zone(id)?.let { zone ->
            ZoneAdminDialog(
                zone = zone,
                onDismiss = { editingZoneId = null },
                onSave = { name, description, category, price ->
                    if (c.updateZone(zone.id, name, description, category, price, zone.enabled)) editingZoneId = null
                }
            )
        }
    }
}

@Composable
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
}


@Composable
private fun LocationField(
    label: String,
    point: GeoPoint?,
    enabled: Boolean = true,
    onOpen: () -> Unit,
    onClear: () -> Unit
) {
    Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Column(Modifier.padding(10.dp)) {
            Text(label, fontWeight = FontWeight.SemiBold)
            Text(
                point?.let { "Pin: ${"%.5f".format(it.latitude)}, ${"%.5f".format(it.longitude)}" } ?: "Sin pin confirmado",
                style = MaterialTheme.typography.bodySmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpen, enabled = enabled) {
                    Text(if (point == null) "MARCAR EN MAPA" else "EDITAR PIN")
                }
                if (point != null && enabled) {
                    TextButton(onClick = onClear) { Text("QUITAR") }
                }
            }
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    onValue: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        enabled = enabled
    )
}

@Composable
private fun MoneyField(label: String, value: Int, max: Int, onValue: (Int) -> Unit) {
    OutlinedTextField(if (value == 0) "" else value.toString(), { v -> onValue((v.filter(Char::isDigit).toIntOrNull() ?: 0).coerceAtMost(max)) }, label = { Text(label) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().padding(top = 8.dp), singleLine = true)
}

@Composable
private fun IntConfigField(
    label: String,
    value: Int,
    modifier: Modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    onValue: (Int) -> Unit
) {
    OutlinedTextField(
        value = if (value == 0) "" else value.toString(),
        onValueChange = { v -> onValue(v.filter(Char::isDigit).toIntOrNull() ?: 0) },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
        singleLine = true
    )
}

@Composable
private fun DeliveryPaymentSelector(c: MandadosController, current: DeliveryPaymentMethod, onSelect: (DeliveryPaymentMethod) -> Unit) {
    Text("¿Cómo pagarás el servicio?", fontWeight = FontWeight.Bold)
    val p = c.config.paymentConfig
    if (p.cashEnabled) EnumRadio("Efectivo", current == DeliveryPaymentMethod.CASH) { onSelect(DeliveryPaymentMethod.CASH) }
    if (p.riderTransferEnabled) EnumRadio("Transferencia / alias", current == DeliveryPaymentMethod.TRANSFER) { onSelect(DeliveryPaymentMethod.TRANSFER) }
    if (p.qrEnabled && p.qrMode == "OPERATIVO") EnumRadio("QR interoperable", current == DeliveryPaymentMethod.QR) { onSelect(DeliveryPaymentMethod.QR) }
    if (p.onlineCheckoutEnabled && p.qrMode == "OPERATIVO") EnumRadio("Pago online", current == DeliveryPaymentMethod.ONLINE) { onSelect(DeliveryPaymentMethod.ONLINE) }
}

@Composable
private fun EnumRadio(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick); Text(text)
    }
}

@Composable
private fun ZoneField(
    c: MandadosController,
    selectedId: String,
    label: String,
    allowBlank: Boolean = false,
    enabled: Boolean = true,
    onSelect: (String) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    val selected = if (selectedId == UNKNOWN_ZONE_ID) "No sé qué zona corresponde" else c.zone(selectedId)?.let { "${it.name}${if (it.price > 0) " · ${money(it.price)}" else " · A configurar"}" } ?: if (allowBlank) "Sin especificar" else "Seleccionar"
    OutlinedButton(
        onClick = { open = true },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
    ) { Text("$label: $selected") }
    if (open) {
        Punto25AlertDialog(onDismissRequest = { open = false }, confirmButton = { TextButton(onClick = { open = false }) { Text("Cerrar") } }, title = { Text(label) }, text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                if (allowBlank) EnumRadio("Sin especificar", selectedId.isBlank()) { onSelect(""); open = false }
                var lastCategoryKey: String? = null
                zonesForPresentation(c.config.zones.filter { it.enabled }).forEach { z ->
                    val categoryKey = zonePresentationKey(z.category)
                    if (categoryKey != lastCategoryKey) {
                        lastCategoryKey = categoryKey
                        Spacer(Modifier.height(8.dp))
                        Text(z.category, fontWeight = FontWeight.Bold)
                    }
                    EnumRadio("${z.name}${if (z.price > 0) " — ${money(z.price)}" else " — A configurar"}${if (z.description.isNotBlank()) "\n${z.description}" else ""}", selectedId == z.id) { onSelect(z.id); open = false }
                }
                Spacer(Modifier.height(8.dp))
                EnumRadio("No sé qué zona corresponde — tarifa a confirmar", selectedId == UNKNOWN_ZONE_ID) { onSelect(UNKNOWN_ZONE_ID); open = false }
            }
        })
    }
}

@Composable
private fun PriceLine(label: String, amount: Int?, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, modifier = Modifier.weight(1f), fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
        Text(moneyNullable(amount), fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun AssistBox(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) { Text(text, Modifier.padding(12.dp)) }
}

private fun statusText(s: OrderStatus): String = when (s) {
    OrderStatus.AWAITING_QUOTE -> "Pendiente de confirmación de tarifa"
    OrderStatus.PENDING -> "Pendiente de aceptación"
    OrderStatus.ACCEPTED -> "Aceptado"
    OrderStatus.IN_PROGRESS -> "En curso"
    OrderStatus.COMPLETED -> "Completado"
    OrderStatus.REJECTED -> "Rechazado"
    OrderStatus.CANCELLED -> "Cancelado"
}

private fun money(v: Int): String = "$" + "%,d".format(v).replace(',', '.')
private fun moneyNullable(v: Int?): String = if (v == null) "A confirmar" else money(v)

private fun openWhatsApp(context: Context, receiver: String, message: String) {
    val digits = receiver.filter(Char::isDigit)
    val normalized = when {
        digits.length == 10 -> "549$digits"
        digits.startsWith("54") -> digits
        else -> digits
    }
    try {
        val intent = if (normalized.isNotBlank()) {
            Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$normalized?text=${Uri.encode(message)}"))
        } else {
            Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, message); setPackage("com.whatsapp") }
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        val share = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, message) }
        context.startActivity(Intent.createChooser(share, "Compartir solicitud"))
    }
}


private fun formatDurationUi(seconds: Long): String {
    val s = seconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return "%02d:%02d:%02d".format(h, m, sec)
}
