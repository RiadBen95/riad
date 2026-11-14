package com.riad.voyage

import com.riad.voyage.data.LitePayment
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentTest {
    @Test fun paymentWindowOnlyAllowsLiteApiAndStripe() {
        assertTrue(LitePayment.allowed("https://js.stripe.com/v3/"))
        assertTrue(LitePayment.allowed("https://payment-wrapper.liteapi.travel/x"))
        assertTrue(!LitePayment.allowed("http://js.stripe.com/"))
        assertTrue(!LitePayment.allowed("https://stripe.com.evil.example/"))
        assertTrue(!LitePayment.allowed("intent://scan#Intent;end"))
    }

    @Test fun sandboxKeysAreRecognised() {
        assertTrue(LitePayment.isSandbox("sand_c0155ab8-0000"))
        assertTrue(!LitePayment.isSandbox("prod_1234"))
    }

    @Test fun paymentPageCarriesKeysAndReturnUrl() {
        val live = LitePayment.html("sec_123'\"", sandbox = false)
        assertTrue(live.contains("publicKey: \"live\""))
        assertTrue(live.contains(LitePayment.RETURN_URL))
        assertTrue(live.contains("sec_123'\\\""))   // valeur échappée pour le JavaScript
        assertTrue(LitePayment.html("s", sandbox = true).contains("publicKey: \"sandbox\""))
    }
}

class AiSearchTest {
    @Test fun semanticHitsAndRateOrder() {
        val hits = com.riad.voyage.data.LiteApi.parseSemantic(
            """{"data":[{"hotelId":"lp1","name":"Spa Hotel","tags":["spa","calme"],"style":"boutique"},{"id":"lp2","name":"B"}]}""")
        org.junit.Assert.assertEquals(listOf("lp1", "lp2"), hits.map { it.id })
        org.junit.Assert.assertEquals("spa · calme · boutique", hits[0].tags)
        val order = com.riad.voyage.data.LiteApi.rateOrder("""{"data":[{"hotelId":"b"},{"hotelId":"a"},{"hotelId":"b"}]}""")
        org.junit.Assert.assertEquals(listOf("b", "a"), order)
    }
}
