package es.educamadrid.seb.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SebCryptoUtilsTest {

    @Test
    fun testStripFragment() {
        val urlWithFragment = "https://aulavirtual33.educamadrid.org/mod/quiz/attempt.php?attempt=123#q4"
        val expected = "https://aulavirtual33.educamadrid.org/mod/quiz/attempt.php?attempt=123"
        assertEquals(expected, SebCryptoUtils.stripFragment(urlWithFragment))

        val urlWithoutFragment = "https://aulavirtual33.educamadrid.org/mod/quiz/view.php?id=99"
        assertEquals(urlWithoutFragment, SebCryptoUtils.stripFragment(urlWithoutFragment))
    }

    @Test
    fun testSha256Calculation() {
        val result = SebCryptoUtils.sha256("hello")
        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", result)
    }

    @Test
    fun testNormalizeSebUrl() {
        assertEquals(
            "https://aulavirtual33.educamadrid.org/mod/quiz/view.php?id=123",
            SebCryptoUtils.normalizeSebUrl("sebs://aulavirtual33.educamadrid.org/mod/quiz/view.php?id=123")
        )
        assertEquals(
            "http://aulavirtual33.educamadrid.org/mod/quiz/view.php?id=123",
            SebCryptoUtils.normalizeSebUrl("seb://aulavirtual33.educamadrid.org/mod/quiz/view.php?id=123")
        )
        assertEquals(
            "https://aulavirtual33.educamadrid.org",
            SebCryptoUtils.normalizeSebUrl("aulavirtual33.educamadrid.org")
        )
    }

    @Test
    fun testBuildSebHeaders() {
        val url = "https://aulavirtual33.educamadrid.org/mod/quiz/attempt.php?attempt=456#page=1"
        val bek = "a1b2c3d4e5f60718293a4b5c6d7e8f90123456789abcdef0123456789abcdef0"
        val ckh = "f0e1d2c3b4a5968778695a4b3c2d1e0f0123456789abcdef0123456789abcdef"

        val headers = SebCryptoUtils.buildSebHeaders(url, bek, ckh)

        assertTrue(headers.containsKey("X-SafeExamBrowser-RequestHash"))
        assertTrue(headers.containsKey("X-SafeExamBrowser-ConfigKeyHash"))
        assertEquals(ckh, headers["X-SafeExamBrowser-ConfigKeyHash"])

        val cleanUrl = "https://aulavirtual33.educamadrid.org/mod/quiz/attempt.php?attempt=456"
        val expectedHash = SebCryptoUtils.sha256(cleanUrl + bek)
        assertEquals(expectedHash, headers["X-SafeExamBrowser-RequestHash"])
    }

    @Test
    fun testExtractAndCleanSessionParamsWithAllParams() {
        val pin = "5599"
        val expectedPinHash = SebCryptoUtils.sha256(pin)
        val rawUrl = "sebs://aulavirtual33.educamadrid.org/mod/quiz/view.php?id=12845&seb_pin_hash=$expectedPinHash&auto_quit=1#targetSection"

        val sessionConfig = SebCryptoUtils.extractAndCleanSessionParams(rawUrl)

        assertEquals("https://aulavirtual33.educamadrid.org/mod/quiz/view.php?id=12845#targetSection", sessionConfig.cleanUrl)
        assertEquals(expectedPinHash, sessionConfig.sessionPinHash)
        assertTrue(sessionConfig.autoQuitEnabled)

        // Verificar que el cálculo de RequestHash sobre la URL limpia NO incluye seb_pin_hash ni auto_quit
        val bek = "test_bek_key"
        val requestHashClean = SebCryptoUtils.computeRequestHash(sessionConfig.cleanUrl, bek)
        val requestHashExpected = SebCryptoUtils.sha256("https://aulavirtual33.educamadrid.org/mod/quiz/view.php?id=12845" + bek)
        assertEquals(requestHashExpected, requestHashClean)
    }

    @Test
    fun testExtractAndCleanSessionParamsWithoutSessionParams() {
        val rawUrl = "https://aulavirtual33.educamadrid.org/mod/quiz/view.php?id=12845"
        val sessionConfig = SebCryptoUtils.extractAndCleanSessionParams(rawUrl)

        assertEquals("https://aulavirtual33.educamadrid.org/mod/quiz/view.php?id=12845", sessionConfig.cleanUrl)
        assertNull(sessionConfig.sessionPinHash)
        assertFalse(sessionConfig.autoQuitEnabled)
    }

    @Test
    fun testPinValidation() {
        val dynamicPin = "1234"
        val dynamicPinHash = SebCryptoUtils.sha256(dynamicPin)

        // PIN maestro siempre válido
        assertTrue(SebCryptoUtils.isPinValid("8426", dynamicPinHash))
        assertTrue(SebCryptoUtils.isPinValid("8426", null))

        // PIN dinámico de la sesión
        assertTrue(SebCryptoUtils.isPinValid(dynamicPin, dynamicPinHash))

        // PIN erróneo
        assertFalse(SebCryptoUtils.isPinValid("9999", dynamicPinHash))
        assertFalse(SebCryptoUtils.isPinValid("0000", null))
    }

    @Test
    fun testDomainWhitelist() {
        // Dominios permitidos
        assertTrue(SebCryptoUtils.isAllowedDomain("https://aulavirtual33.educamadrid.org/mod/quiz/view.php?id=123"))
        assertTrue(SebCryptoUtils.isAllowedDomain("https://aulavirtual.educa.madrid.org/test"))
        assertTrue(SebCryptoUtils.isAllowedDomain("https://educa.madrid.org/login"))
        assertTrue(SebCryptoUtils.isAllowedDomain("https://educamadrid.org"))
        assertTrue(SebCryptoUtils.isAllowedDomain("about:blank"))

        // Dominios externos prohibidos
        assertFalse(SebCryptoUtils.isAllowedDomain("https://www.google.com"))
        assertFalse(SebCryptoUtils.isAllowedDomain("https://chatgpt.com"))
        assertFalse(SebCryptoUtils.isAllowedDomain("https://evil-educamadrid.org.attacker.com"))
    }
}
