package com.riad.voyage

import com.riad.voyage.data.LitePayment
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentTest {
    @Test fun paymentPageCarriesKeysAndReturnUrl() {
        val live = LitePayment.html("sec_123'\"", sandbox = false)
        assertTrue(live.contains("publicKey: \"live\""))
        assertTrue(live.contains(LitePayment.RETURN_URL))
        assertTrue(live.contains("sec_123'\\\""))   // valeur échappée pour le JavaScript
        assertTrue(LitePayment.html("s", sandbox = true).contains("publicKey: \"sandbox\""))
    }
}
