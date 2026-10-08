package ar.com.mandados.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomerOrderOwnershipTest {
    private fun customer(
        areaCode: String = "2345",
        subscriber: String = "513240",
        accountId: String = "DEV-2345513240",
        verified: Boolean = true,
        name: String = "Ernesto"
    ) = Customer(
        name = name,
        areaCode = areaCode,
        subscriber = subscriber,
        locality = "25 de Mayo",
        accountId = accountId,
        whatsappVerified = verified
    )

    private fun order(
        customerId: String,
        customerPhone: String = "2345-513240",
        customerName: String = "Ernesto"
    ) = LocalOrder(
        id = "P25-TEST",
        createdAt = "03/10/2026 00:00:00",
        serviceType = ServiceType.DELIVERY,
        status = OrderStatus.PENDING,
        customerName = customerName,
        customerPhone = customerPhone,
        detail = "Prueba",
        baseAmount = 1000,
        baseZoneName = "Urbana",
        prePickupAmount = 0,
        rainAmount = 0,
        totalAmount = 1000,
        whatsappMessage = "",
        customerId = customerId
    )

    @Test
    fun exactCurrentIdMatches() {
        assertTrue(orderBelongsToCustomer(order("DEV-2345513240"), customer()))
    }

    @Test
    fun legacyCliIdMatchesVerifiedCurrentCustomer() {
        assertTrue(orderBelongsToCustomer(order("CLI-2345513240"), customer()))
    }

    @Test
    fun previousDevIdMatchesVerifiedGoogleCustomerWithSameNumber() {
        val current = customer(accountId = "G-current-user")
        assertTrue(orderBelongsToCustomer(order("DEV-2345513240"), current))
    }

    @Test
    fun legacyCliIdMatchesVerifiedGoogleCustomerWithSameNumber() {
        val current = customer(accountId = "G-current-user")
        assertTrue(orderBelongsToCustomer(order("CLI-2345513240"), current))
    }

    @Test
    fun differentCustomerNeverMatchesEvenWithSameVisibleName() {
        val other = customer(areaCode = "2345", subscriber = "519999", accountId = "DEV-2345519999", name = "Ernesto")
        assertFalse(orderBelongsToCustomer(order("CLI-2345513240", customerName = "Ernesto"), other))
    }

    @Test
    fun arbitraryAccountIdIsNotMatchedByPhone() {
        val current = customer(accountId = "G-current-user")
        assertFalse(orderBelongsToCustomer(order("G-old-unrelated-user", customerPhone = "2345-513240"), current))
    }

    @Test
    fun unverifiedCustomerCannotClaimNonExactPhoneBasedLegacyId() {
        val current = customer(accountId = "G-current-user", verified = false)
        assertFalse(orderBelongsToCustomer(order("CLI-2345513240"), current))
    }

    @Test
    fun blankLegacyIdentityNeverMatches() {
        assertFalse(orderBelongsToCustomer(order(""), customer()))
    }

    @Test
    fun `REG-ORDER-LOCATION-CLIENT-AUTH-001 GeoPoint no altera aislamiento de Cliente`() {
        val own = order("DEV-2345513240").copy(
            originLocation = GeoPoint(-35.432471, -60.171559),
            destinationLocation = GeoPoint(-35.430100, -60.170200)
        )
        val foreign = own.copy(
            id = "P25-FOREIGN",
            customerId = "DEV-2345599999",
            customerPhone = "2345-599999"
        )
        val current = customer()

        assertTrue(orderBelongsToCustomer(own, current))
        assertFalse(orderBelongsToCustomer(foreign, current))
    }
}
