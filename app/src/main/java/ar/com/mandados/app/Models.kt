package ar.com.mandados.app

const val UNKNOWN_ZONE_ID = "__UNKNOWN__"

data class AreaCode(val code: String, val locality: String, val areaDigits: Int, val subscriberDigits: Int)

data class Customer(
    val name: String,
    val areaCode: String,
    val subscriber: String,
    val locality: String,
    val accountId: String = "",
    val googleEmail: String = "",
    val googleVerified: Boolean = false,
    val whatsappVerified: Boolean = false,
    val whatsappVerifiedAt: String? = null
) {
    val nationalNumber: String get() = areaCode + subscriber
    val displayPhone: String get() = "$areaCode-$subscriber"
    val id: String get() = accountId.ifBlank { "CLI-$nationalNumber" }
}

enum class ServiceType { DELIVERY, SHOPPING }
enum class ServiceCategory { PURCHASE, ERRAND, PROCEDURE, SHIPMENT }
enum class PurchaseInstructionType { IN_APP, PHYSICAL_NOTE, IN_PERSON }
enum class PurchasePaymentMethod { DIRECT_TO_STORE, TRANSFER_TO_RIDER, CASH_PRE_PICKUP }
enum class DeliveryPaymentMethod { CASH, TRANSFER, QR, ONLINE }
enum class PrePickupMode { OFF, FIXED, PERCENT_BASE }
enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class OperationMode { SIMPLE_WHATSAPP, MULTI_RIDER }
enum class VehicleType { MOTORCYCLE, BICYCLE }
enum class RiderApprovalStatus { PENDING, APPROVED, SUSPENDED }
enum class DocumentReviewStatus { NOT_UPLOADED, PENDING, APPROVED, REJECTED }
enum class RiderDocumentKey {
    DNI_FRONT, DNI_BACK, MOTORCYCLE_PLATE, DRIVER_LICENSE_FRONT, DRIVER_LICENSE_BACK, VEHICLE_CARD, INSURANCE_CARD
}
enum class OrderStatus { AWAITING_QUOTE, PENDING, ACCEPTED, IN_PROGRESS, COMPLETED, REJECTED, CANCELLED }
enum class OrderEventType {
    CREATED, RIDER_ASSIGNED, RIDER_UNASSIGNED, ACCEPTED, IN_PROGRESS, COMPLETED, REJECTED, CANCELLED,
    ORDER_EDITED, PAYMENT_UPDATED, PAYMENT_DECLARED, PAYMENT_PROOF_ATTACHED, PAYMENT_CONFIRMED, PAYMENT_REVIEW_REQUESTED,
    TIP_TRANSFER_DECLARED, TIP_TRANSFER_CONFIRMED, RATING_SUBMITTED
}
enum class ShiftReservationStatus { RESERVED, CANCELLED, COMPLETED }
enum class ShiftEventType { RIDER_JOINED, RIDER_CANCELLED, ADMIN_ADDED, ADMIN_REMOVED }
enum class LegalDocumentType { TERMS, PRIVACY, CANCELLATIONS, LOCATION_PERMISSIONS, RIDER_TERMS }
enum class PaymentChannel { CASH, RIDER_TRANSFER, QR_INTEROPERABLE, ONLINE_CHECKOUT }
enum class PaymentStatus { PENDING, DECLARED, PROOF_UPLOADED, CONFIRMED, IN_REVIEW, APPROVED, REJECTED, REFUNDED }
enum class TipStatus { NONE, SELECTED, TRANSFER_DECLARED, CONFIRMED }
enum class RiderInvitationStatus { PENDING, USED, EXPIRED, CANCELLED }
enum class OrderZonePoint { ORIGIN, DESTINATION, STORE, PRE_PICKUP }
enum class OrderZoneOverrideSource { CATALOG, AD_HOC }

data class GeoPoint(val latitude: Double, val longitude: Double)

data class ZoneConfig(
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val price: Int = 0,
    val enabled: Boolean = true
)

data class OrderZoneOverride(
    val source: OrderZoneOverrideSource,
    val catalogZoneId: String? = null,
    val name: String,
    val price: Int
)

data class PaymentConfig(
    val cashEnabled: Boolean = true,
    val riderTransferEnabled: Boolean = true,
    val qrEnabled: Boolean = false,
    val onlineCheckoutEnabled: Boolean = false,
    val transferProofRequired: Boolean = true,
    val riderConfirmationRequired: Boolean = true,
    val centralAlias: String = "",
    val qrProvider: String = "Mercado Pago",
    val qrMode: String = "DESACTIVADO",
    val tipsEnabled: Boolean = true,
    val tipSuggestions: List<Int> = listOf(500, 1000, 2000),
    val customTipEnabled: Boolean = true
)

data class LegalProfile(
    val businessName: String = "Punto25",
    val legalName: String = "",
    val taxId: String = "",
    val legalAddress: String = "",
    val commercialAddress: String = "",
    val city: String = "25 de Mayo",
    val province: String = "Buenos Aires",
    val country: String = "Argentina",
    val supportEmail: String = "",
    val privacyEmail: String = "",
    val supportWhatsapp: String = "",
    val serviceArea: String = "25 de Mayo, Provincia de Buenos Aires"
)

data class AdminConfig(
    val acceptingOrders: Boolean = true,
    val closedMessage: String = "En este momento no estamos recibiendo nuevas solicitudes. Podés seguir consultando y gestionando las que ya tenés en curso. Disculpá las molestias.",
    val maxPurchaseAmount: Int = 50000,
    val maxWeightKg: Int = 5,
    val rainEnabled: Boolean = false,
    val rainAmount: Int = 0,
    val prePickupMode: PrePickupMode = PrePickupMode.OFF,
    val prePickupValue: Int = 0,
    val whatsappReceiver: String = "",
    val supportWhatsapp: String = "",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val operationMode: OperationMode = OperationMode.MULTI_RIDER,
    val defaultMaxConcurrentOrders: Int = 2,
    val paymentConfig: PaymentConfig = PaymentConfig(),
    val legalProfile: LegalProfile = LegalProfile(),
    val zones: List<ZoneConfig> = defaultZones()
)

data class RiderDocuments(
    val dniFrontUri: String? = null,
    val dniBackUri: String? = null,
    val motorcyclePlateUri: String? = null,
    val driverLicenseFrontUri: String? = null,
    val driverLicenseBackUri: String? = null,
    val vehicleCardUri: String? = null,
    val insuranceCardUri: String? = null
) {
    fun uriFor(key: RiderDocumentKey): String? = when (key) {
        RiderDocumentKey.DNI_FRONT -> dniFrontUri
        RiderDocumentKey.DNI_BACK -> dniBackUri
        RiderDocumentKey.MOTORCYCLE_PLATE -> motorcyclePlateUri
        RiderDocumentKey.DRIVER_LICENSE_FRONT -> driverLicenseFrontUri
        RiderDocumentKey.DRIVER_LICENSE_BACK -> driverLicenseBackUri
        RiderDocumentKey.VEHICLE_CARD -> vehicleCardUri
        RiderDocumentKey.INSURANCE_CARD -> insuranceCardUri
    }
}

data class RiderProfile(
    val id: String,
    val name: String,
    val phone: String,
    val birthDate: String = "",
    val vehicleType: VehicleType = VehicleType.MOTORCYCLE,
    val address: String = "",
    val transferAlias: String = "",
    val maxConcurrentOrdersOverride: Int? = null,
    val documents: RiderDocuments = RiderDocuments(),
    val approvalStatus: RiderApprovalStatus = RiderApprovalStatus.PENDING,
    val documentReviews: Map<RiderDocumentKey, DocumentReviewStatus> = emptyMap(),
    val documentNotes: Map<RiderDocumentKey, String> = emptyMap(),
    val active: Boolean = true,
    val available: Boolean = false,
    val availableUntilAt: String? = null
) {
    fun reviewFor(key: RiderDocumentKey): DocumentReviewStatus =
        documentReviews[key] ?: if (documents.uriFor(key).isNullOrBlank()) DocumentReviewStatus.NOT_UPLOADED
        else DocumentReviewStatus.PENDING
}

data class OrderDraft(
    val serviceType: ServiceType = ServiceType.DELIVERY,
    val category: ServiceCategory = ServiceCategory.ERRAND,
    val originAddress: String = "",
    val originReference: String = "",
    val originZoneId: String = "",
    val originLocation: GeoPoint? = null,
    val destinationAddress: String = "",
    val destinationReference: String = "",
    val destinationZoneId: String = "",
    val destinationLocation: GeoPoint? = null,
    val carriedItem: String = "",
    val instructionType: PurchaseInstructionType = PurchaseInstructionType.IN_APP,
    val purchaseDescription: String = "",
    val purchaseMaxAmount: Int = 0,
    val storeName: String = "",
    val storeAddress: String = "",
    val storeZoneId: String = "",
    val storeLocation: GeoPoint? = null,
    val purchasePayment: PurchasePaymentMethod = PurchasePaymentMethod.DIRECT_TO_STORE,
    val prePickupAddress: String = "",
    val prePickupReference: String = "",
    val prePickupZoneId: String = "",
    val prePickupLocation: GeoPoint? = null,
    val sameDeliveryAsPrePickup: Boolean = false,
    val deliveryPayment: DeliveryPaymentMethod = DeliveryPaymentMethod.CASH,
    val notes: String = ""
) {
    fun requiresPrePickup(): Boolean =
        instructionType != PurchaseInstructionType.IN_APP || purchasePayment == PurchasePaymentMethod.CASH_PRE_PICKUP
}

data class PricingResult(
    val needsQuote: Boolean,
    val baseAmount: Int?,
    val baseZoneName: String?,
    val prePickupAmount: Int?,
    val rainAmount: Int?,
    val totalAmount: Int?,
    val issue: String? = null
)

data class OrderEvent(
    val type: OrderEventType,
    val at: String,
    val status: OrderStatus? = null,
    val riderId: String? = null,
    val note: String? = null,
    val actor: String? = null
)

data class LocalOrder(
    val id: String,
    val createdAt: String,
    val serviceType: ServiceType,
    val category: ServiceCategory = ServiceCategory.ERRAND,
    val operationMode: OperationMode = OperationMode.MULTI_RIDER,
    val status: OrderStatus,
    val customerName: String,
    val customerPhone: String,
    val detail: String,
    val baseAmount: Int?,
    val baseZoneName: String?,
    val prePickupAmount: Int?,
    val rainAmount: Int?,
    val totalAmount: Int?,
    val whatsappMessage: String,
    val assignedRiderId: String? = null,
    val originLocation: GeoPoint? = null,
    val destinationLocation: GeoPoint? = null,
    val storeLocation: GeoPoint? = null,
    val prePickupLocation: GeoPoint? = null,
    val customerId: String = "",
    val events: List<OrderEvent> = emptyList(),
    val originAddress: String = "",
    val originReference: String = "",
    val originZoneId: String = "",
    val destinationAddress: String = "",
    val destinationReference: String = "",
    val destinationZoneId: String = "",
    val carriedItem: String = "",
    val instructionType: PurchaseInstructionType = PurchaseInstructionType.IN_APP,
    val purchaseDescription: String = "",
    val purchaseMaxAmount: Int = 0,
    val storeName: String = "",
    val storeAddress: String = "",
    val storeZoneId: String = "",
    val purchasePayment: PurchasePaymentMethod = PurchasePaymentMethod.DIRECT_TO_STORE,
    val prePickupAddress: String = "",
    val prePickupReference: String = "",
    val prePickupZoneId: String = "",
    val sameDeliveryAsPrePickup: Boolean = false,
    val deliveryPayment: DeliveryPaymentMethod = DeliveryPaymentMethod.CASH,
    val notes: String = "",
    val zoneOverrides: Map<OrderZonePoint, OrderZoneOverride> = emptyMap()
)

sealed interface OrderCreationResult {
    data class Created(val order: LocalOrder) : OrderCreationResult
    data class Blocked(val message: String) : OrderCreationResult
}

data class ShiftTemplate(
    val id: String,
    val dayOfWeek: Int,
    val startTime: String,
    val endTime: String,
    val capacity: Int = 1,
    val enabled: Boolean = true,
    val specificDate: String? = null
) {
    val isSpecificDate: Boolean get() = !specificDate.isNullOrBlank()
}

data class RiderShiftReservation(
    val id: String,
    val riderId: String,
    val shiftTemplateId: String,
    val serviceDate: String,
    val joinedAt: String,
    val status: ShiftReservationStatus = ShiftReservationStatus.RESERVED,
    val cancelledAt: String? = null,
    val cancellationCount: Int = 0,
    val lastCancelledAt: String? = null,
    val blockedRejoin: Boolean = false
)

data class ShiftAuditEvent(
    val id: String,
    val reservationId: String,
    val shiftTemplateId: String,
    val riderId: String,
    val serviceDate: String,
    val type: ShiftEventType,
    val at: String,
    val actor: String
)

data class RiderInvitation(
    val id: String,
    val code: String,
    val riderId: String,
    val phone: String,
    val createdAt: String,
    val expiresAt: String,
    val status: RiderInvitationStatus = RiderInvitationStatus.PENDING,
    val usedAt: String? = null,
    val resetAccess: Boolean = false
)

data class RiderCredential(
    val riderId: String,
    val saltBase64: String,
    val passwordHashBase64: String,
    val iterations: Int = 120000,
    val updatedAt: String,
    val mustChangePassword: Boolean = false
)

data class LegalDocument(
    val id: String,
    val type: LegalDocumentType,
    val version: String,
    val title: String,
    val content: String = "",
    val effectiveDate: String,
    val published: Boolean = false,
    val requireAcceptance: Boolean = false,
    val updatedAt: String = "",
    val fileUri: String? = null,
    val fileName: String? = null,
    val fileSha256: String? = null
)

data class LegalAcceptance(
    val customerId: String,
    val documentId: String,
    val documentType: LegalDocumentType,
    val version: String,
    val acceptedAt: String
)

data class PaymentRecord(
    val id: String,
    val orderId: String,
    val riderId: String? = null,
    val channel: PaymentChannel,
    val expectedAmount: Int,
    val status: PaymentStatus = PaymentStatus.PENDING,
    val proofUri: String? = null,
    val provider: String? = null,
    val providerPaymentId: String? = null,
    val createdAt: String,
    val updatedAt: String
)

data class OrderRating(
    val orderId: String,
    val customerId: String,
    val riderId: String,
    val stars: Int,
    val tags: List<String> = emptyList(),
    val comment: String = "",
    val tipAmount: Int = 0,
    val tipStatus: TipStatus = TipStatus.NONE,
    val createdAt: String
)

fun defaultZones(): List<ZoneConfig> = listOf(
    ZoneConfig("urbana", "Urbana", "Entrega desde calles 1 a 18 y 19 a 36", "ZONA URBANA"),
    ZoneConfig("fuera_urbana", "Fuera de urbana", "Entregas desde calles 105 a 204 y 41 a 304, pero que no entren en Urbana", "ZONA URBANA"),
    ZoneConfig("agumin", "Barrio Agumín", "", "BARRIOS"),
    ZoneConfig("cementerio", "Barrio Cementerio", "", "BARRIOS"),
    ZoneConfig("la_morocha", "Barrio La Morocha", "", "BARRIOS"),
    ZoneConfig("club_golf", "Barrio Club de Golf", "", "BARRIOS"),
    ZoneConfig("8_noviembre", "Barrio 8 de Noviembre", "", "BARRIOS"),
    ZoneConfig("las_palmeras", "Barrio Las Palmeras", "", "BARRIOS"),
    ZoneConfig("quevedo", "Barrio Quevedo", "", "BARRIOS"),
    ZoneConfig("lebensohn_36_r46", "Acceso Lebensohn entre Calle 36 y Ruta 46", "", "ACCESOS Y RUTAS"),
    ZoneConfig("lebensohn_r46_r51", "Acceso Lebensohn entre Ruta 46 y Ruta 51", "", "ACCESOS Y RUTAS"),
    ZoneConfig("illia_parque", "Acceso Illia hasta Parque industrial", "", "ACCESOS Y RUTAS"),
    ZoneConfig("illia_r46", "Acceso Illia hasta Ruta 46", "", "ACCESOS Y RUTAS"),
    ZoneConfig("ruta46_illia_lebensohn", "Ruta 46 entre Acceso Illia y Acceso Lebensohn", "", "ACCESOS Y RUTAS")
)
