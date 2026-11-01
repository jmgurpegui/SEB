package es.educamadrid.seb.client

import org.junit.Assert.assertEquals
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
        // SHA-256 de "hello" conocido: 2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824
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
}
