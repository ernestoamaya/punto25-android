package ar.com.mandados.app

import android.content.Context
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CustomerRegistrationPolicyTest {
    private val phone = "2345513240"
    private fun pending(
        id: String = "G-user-1", google: Boolean = true,
        wa: Boolean = false, at: String? = null
    ) = Customer("Cliente", "2345", "513240", "25 de Mayo",
        accountId = id, googleVerified = google, whatsappVerified = wa, whatsappVerifiedAt = at)

    @Before fun clear() {
        RuntimeEnvironment.getApplication().getSharedPreferences("mandados_alpha1", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test fun googleRegistrationWithoutWhatsappSucceedsAndDoesNotMutateVerification() {
        val p = pending()
        assertTrue(canConfirmCustomerRegistration(p, true, "user-1", false))
        assertFalse(p.whatsappVerified)
        assertNull(p.whatsappVerifiedAt)
    }

    @Test fun googleRequiresExactLiveFirebaseUidAndNeverAcceptsForgedIdentity() {
        assertFalse(canConfirmCustomerRegistration(pending(), true, null, true))
        assertFalse(canConfirmCustomerRegistration(pending(), true, "user-2", true))
        assertFalse(canConfirmCustomerRegistration(pending(google = false), true, "user-1", false))
        assertFalse(canConfirmCustomerRegistration(pending("DEV-$phone", false), true, "user-1", true))
        assertFalse(canConfirmCustomerRegistration(pending("CLI-$phone", false), false, null, true))
        assertFalse(canConfirmCustomerRegistration(pending(), false, null, true))
    }

    @Test fun debugFallbackOnlyWhenGoogleNotConfigured() {
        assertTrue(canConfirmCustomerRegistration(pending("DEV-$phone", false), false, null, true))
        assertFalse(canConfirmCustomerRegistration(pending("DEV-$phone", false), false, null, false))
    }

    @Test fun unverifiedGoogleCustomerNeverClaimsLegacyOrders() {
        val c = pending()
        fun order(id: String) = LocalOrder(
            id = "P25-TEST", createdAt = "03/10/2026", serviceType = ServiceType.DELIVERY,
            status = OrderStatus.PENDING, customerName = "Cliente", customerPhone = c.displayPhone,
            detail = "", baseAmount = 0, baseZoneName = "", prePickupAmount = 0,
            rainAmount = 0, totalAmount = 0, whatsappMessage = "", customerId = id)
        assertFalse(orderBelongsToCustomer(order("CLI-$phone"), c))
        assertFalse(orderBelongsToCustomer(order("DEV-$phone"), c))
        assertFalse(orderBelongsToCustomer(order("G-other"), c))
        assertTrue(orderBelongsToCustomer(order("G-user-1"), c))
    }

    @Test fun debugRegistrationPersistsUnverifiedPhoneAcrossControllerRecreation() {
        if (!BuildConfig.DEBUG || GoogleAuthIntegration.isConfigured()) return
        val context = RuntimeEnvironment.getApplication()
        val c = MandadosController(context)
        c.registerPending(pending("G-forged", true, true, "fake"))
        assertEquals("", c.pendingCustomer?.accountId)
        assertFalse(c.pendingCustomer!!.whatsappVerified)
        assertNull(c.pendingCustomer!!.whatsappVerifiedAt)
        c.applyDevelopmentIdentity()
        assertTrue(c.confirmRegistration())
        val restored = MandadosController(context).customer!!
        assertEquals("DEV-$phone", restored.accountId)
        assertFalse(restored.googleVerified)
        assertFalse(restored.whatsappVerified)
        assertNull(restored.whatsappVerifiedAt)
    }

    @Test fun whatsappAvailabilityIsExplicitAndDisabledByDefault() {
        assertFalse(BuildConfig.WHATSAPP_VERIFICATION_ENABLED)
        assertFalse(WhatsAppVerificationApi.isConfigured())
    }
}
