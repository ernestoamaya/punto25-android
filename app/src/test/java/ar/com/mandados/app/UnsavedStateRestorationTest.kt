package ar.com.mandados.app

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UnsavedStateRestorationTest {
    private lateinit var context: Context

    private val saverScope = SaverScope { value ->
        value is String || value is Int || value is Long || value is Float || value is Double ||
            value is Boolean || value is CharSequence || value is ArrayList<*>
    }

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("mandados_alpha1", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `REG-ORDER-DRAFT-UNSAVED-001 controller recreation restores complete dirty OrderDraft`() {
        val draft = completeShoppingDraft(notes = "Cambio pendiente")
        val controller = MandadosController(context).also { it.draft = draft }

        val restored = roundTripThroughSaveableRegistry(mandadosControllerSaver(context), controller)

        assertNotSame(controller, restored)
        assertEquals(draft, restored.draft)
        assertEquals(ServiceType.SHOPPING, restored.draft.serviceType)
        assertEquals(ServiceCategory.PURCHASE, restored.draft.category)
    }

    @Test
    fun `REG-ORDER-DRAFT-UNSAVED-001 baseline and current draft remain distinct after restoration`() {
        val baseline = completeShoppingDraft(notes = "Original")
        val current = baseline.copy(
            purchaseDescription = "Compra editada",
            destinationAddress = "Destino editado",
            notes = "Cambio pendiente"
        )

        val restoredCurrent = roundTripThroughSaveableRegistry(orderDraftSaver, current)
        val restoredBaselineState = roundTripThroughSaveableRegistry(
            orderDraftBaselineStateSaver,
            mutableStateOf<OrderDraft?>(baseline)
        )
        val restoredBaseline = restoredBaselineState.value

        assertEquals(current, restoredCurrent)
        assertEquals(baseline, restoredBaseline)
        assertTrue(restoredCurrent != restoredBaseline)
    }

    @Test
    fun `REG-ORDER-DRAFT-UNSAVED-001 nullable baseline stays null outside active draft`() {
        val restored = roundTripThroughSaveableRegistry(
            orderDraftBaselineStateSaver,
            mutableStateOf<OrderDraft?>(null)
        )

        assertEquals(null, restored.value)
    }

    @Test
    fun `REG-MAP-UNSAVED-001 unconfirmed map selection survives restoration and stays dirty`() {
        val initial = GeoPoint(-35.432471, -60.171559)
        val selected = GeoPoint(-35.441234, -60.188765)

        val restoredInitial = roundTripThroughSaveableRegistry(geoPointSaver, initial)
        val restoredSelected = roundTripThroughSaveableRegistry(geoPointSaver, selected)

        assertEquals(initial, restoredInitial)
        assertEquals(selected, restoredSelected)
        assertFalse(restoredSelected == restoredInitial)
    }

    private fun completeShoppingDraft(notes: String): OrderDraft = OrderDraft(
        serviceType = ServiceType.SHOPPING,
        category = ServiceCategory.PURCHASE,
        originAddress = "Origen legado estructurado",
        originReference = "Puerta verde",
        originZoneId = "zone-origin",
        originLocation = GeoPoint(-35.431, -60.171),
        destinationAddress = "Destino",
        destinationReference = "Casa azul",
        destinationZoneId = "zone-destination",
        destinationLocation = GeoPoint(-35.442, -60.182),
        carriedItem = "Paquete",
        instructionType = PurchaseInstructionType.PHYSICAL_NOTE,
        purchaseDescription = "Lista de compras",
        purchaseMaxAmount = 43210,
        storeName = "Comercio",
        storeAddress = "Calle comercio 123",
        storeZoneId = "zone-store",
        storeLocation = GeoPoint(-35.437, -60.177),
        purchasePayment = PurchasePaymentMethod.CASH_PRE_PICKUP,
        prePickupAddress = "Retiro previo",
        prePickupReference = "Timbre 2",
        prePickupZoneId = "zone-pre",
        prePickupLocation = GeoPoint(-35.429, -60.169),
        sameDeliveryAsPrePickup = false,
        deliveryPayment = DeliveryPaymentMethod.TRANSFER,
        notes = notes
    )

    private fun <Original> roundTripThroughSaveableRegistry(
        saver: Saver<Original, Any>,
        value: Original
    ): Original {
        val savedValue = with(saver) { saverScope.save(value) }
            ?: error("El Saver no produjo estado restaurable")
        val registry = SaveableStateRegistry(restoredValues = null, canBeSaved = { true })
        val entry = registry.registerProvider("state") { savedValue }
        val savedRegistry = registry.performSave()
        entry.unregister()

        val restoredRegistry = SaveableStateRegistry(savedRegistry, canBeSaved = { true })
        val restoredValue = restoredRegistry.consumeRestored("state")
            ?: error("El registry no restauró el valor")
        return saver.restore(restoredValue)
            ?: error("El Saver no pudo restaurar el estado")
    }
}
