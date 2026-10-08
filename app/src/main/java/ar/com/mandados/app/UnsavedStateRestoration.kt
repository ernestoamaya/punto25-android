package ar.com.mandados.app

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver

private const val ORDER_DRAFT_SAVE_FIELD_COUNT = 30

internal val orderDraftSaver: Saver<OrderDraft, Any> = listSaver(
    save = { draft -> encodeOrderDraft(draft) },
    restore = ::decodeOrderDraft
)

internal val orderDraftBaselineStateSaver: Saver<MutableState<OrderDraft?>, Any> = listSaver(
    save = { state ->
        state.value?.let { listOf("1") + encodeOrderDraft(it) } ?: listOf("0")
    },
    restore = { saved ->
        val restored = if (saved.firstOrNull() == "1") {
            decodeOrderDraft(saved.drop(1))
        } else {
            null
        }
        mutableStateOf(restored)
    }
)

internal val geoPointSaver: Saver<GeoPoint, Any> = listSaver(
    save = { point -> listOf(point.latitude.toString(), point.longitude.toString()) },
    restore = { saved ->
        val latitude = saved.getOrNull(0)?.toDoubleOrNull()
        val longitude = saved.getOrNull(1)?.toDoubleOrNull()
        if (latitude != null && longitude != null) GeoPoint(latitude, longitude) else null
    }
)

internal fun mandadosControllerSaver(context: Context): Saver<MandadosController, Any> = listSaver(
    save = { controller -> encodeOrderDraft(controller.draft) },
    restore = { saved ->
        MandadosController(context.applicationContext).also { controller ->
            controller.draft = decodeOrderDraft(saved)
        }
    }
)

private fun encodeOrderDraft(draft: OrderDraft): List<String> = listOf(
    draft.serviceType.name,
    draft.category.name,
    draft.originAddress,
    draft.originReference,
    draft.originZoneId,
    draft.originLocation?.latitude?.toString().orEmpty(),
    draft.originLocation?.longitude?.toString().orEmpty(),
    draft.destinationAddress,
    draft.destinationReference,
    draft.destinationZoneId,
    draft.destinationLocation?.latitude?.toString().orEmpty(),
    draft.destinationLocation?.longitude?.toString().orEmpty(),
    draft.carriedItem,
    draft.instructionType.name,
    draft.purchaseDescription,
    draft.purchaseMaxAmount.toString(),
    draft.storeName,
    draft.storeAddress,
    draft.storeZoneId,
    draft.storeLocation?.latitude?.toString().orEmpty(),
    draft.storeLocation?.longitude?.toString().orEmpty(),
    draft.purchasePayment.name,
    draft.prePickupAddress,
    draft.prePickupReference,
    draft.prePickupZoneId,
    draft.prePickupLocation?.latitude?.toString().orEmpty(),
    draft.prePickupLocation?.longitude?.toString().orEmpty(),
    draft.sameDeliveryAsPrePickup.toString(),
    draft.deliveryPayment.name,
    draft.notes
)

private fun decodeOrderDraft(saved: List<String>): OrderDraft {
    if (saved.size != ORDER_DRAFT_SAVE_FIELD_COUNT) return OrderDraft()

    return OrderDraft(
        serviceType = enumOrDefault(saved[0], ServiceType.DELIVERY),
        category = enumOrDefault(saved[1], ServiceCategory.ERRAND),
        originAddress = saved[2],
        originReference = saved[3],
        originZoneId = saved[4],
        originLocation = decodePoint(saved[5], saved[6]),
        destinationAddress = saved[7],
        destinationReference = saved[8],
        destinationZoneId = saved[9],
        destinationLocation = decodePoint(saved[10], saved[11]),
        carriedItem = saved[12],
        instructionType = enumOrDefault(saved[13], PurchaseInstructionType.IN_APP),
        purchaseDescription = saved[14],
        purchaseMaxAmount = saved[15].toIntOrNull() ?: 0,
        storeName = saved[16],
        storeAddress = saved[17],
        storeZoneId = saved[18],
        storeLocation = decodePoint(saved[19], saved[20]),
        purchasePayment = enumOrDefault(saved[21], PurchasePaymentMethod.DIRECT_TO_STORE),
        prePickupAddress = saved[22],
        prePickupReference = saved[23],
        prePickupZoneId = saved[24],
        prePickupLocation = decodePoint(saved[25], saved[26]),
        sameDeliveryAsPrePickup = saved[27].toBooleanStrictOrNull() ?: false,
        deliveryPayment = enumOrDefault(saved[28], DeliveryPaymentMethod.CASH),
        notes = saved[29]
    )
}

private fun decodePoint(latitude: String, longitude: String): GeoPoint? {
    val lat = latitude.toDoubleOrNull()
    val lon = longitude.toDoubleOrNull()
    return if (lat != null && lon != null) GeoPoint(lat, lon) else null
}

private inline fun <reified T : Enum<T>> enumOrDefault(value: String, default: T): T =
    enumValues<T>().firstOrNull { it.name == value } ?: default
