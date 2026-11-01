package es.educamadrid.seb.client

import android.net.Uri
import java.security.MessageDigest

/**
 * Utilidades criptográficas para el protocolo Safe Exam Browser (SEB).
 * Cumple con la especificación de verificación de cabeceras en Moodle Quiz SEB Plugin.
 */
object SebCryptoUtils {

    /**
     * Browser Exam Key (BEK) por defecto (hash SHA-256 en minúsculas de 64 caracteres).
     * Se puede modificar o configurar según los ajustes del cuestionario en Aula Virtual.
     */
    const val DEFAULT_BROWSER_EXAM_KEY: String =
        "a1b2c3d4e5f60718293a4b5c6d7e8f90123456789abcdef0123456789abcdef0"

    /**
     * Config Key Hash (CKH) por defecto (hash SHA-256 en minúsculas de 64 caracteres).
     */
    const val DEFAULT_CONFIG_KEY_HASH: String =
        "f0e1d2c3b4a5968778695a4b3c2d1e0f0123456789abcdef0123456789abcdef"

    /**
     * PIN Docente por defecto para desbloqueo de emergencia e infracciones.
     */
    const val DEFAULT_TEACHER_PIN: String = "8426"

    /**
     * Limpia el fragmento (#...) de una URL según el estándar SEB.
     */
    fun stripFragment(url: String): String {
        return try {
            val hashIndex = url.indexOf('#')
            if (hashIndex != -1) {
                url.substring(0, hashIndex)
            } else {
                url
            }
        } catch (_: Exception) {
            url
        }
    }

    /**
     * Calcula el hash SHA-256 en hexadecimal en minúsculas.
     */
    fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Calcula la cabecera X-SafeExamBrowser-RequestHash:
     * SHA-256(urlSinFragmento + BROWSER_EXAM_KEY)
     */
    fun computeRequestHash(url: String, browserExamKey: String): String {
        val cleanUrl = stripFragment(url)
        return sha256(cleanUrl + browserExamKey)
    }

    /**
     * Genera el mapa de cabeceras HTTP que deben inyectarse en cada petición SEB a Moodle.
     */
    fun buildSebHeaders(url: String, browserExamKey: String, configKeyHash: String): Map<String, String> {
        val requestHash = computeRequestHash(url, browserExamKey)
        return mapOf(
            "X-SafeExamBrowser-RequestHash" to requestHash,
            "X-SafeExamBrowser-ConfigKeyHash" to configKeyHash
        )
    }

    /**
     * Valida y normaliza esquemas Deep Links (seb:// y sebs://) hacia HTTP/HTTPS.
     */
    fun normalizeSebUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        return when {
            trimmed.startsWith("sebs://", ignoreCase = true) -> {
                "https://" + trimmed.substring(7)
            }
            trimmed.startsWith("seb://", ignoreCase = true) -> {
                "http://" + trimmed.substring(6)
            }
            !trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true) -> {
                "https://$trimmed"
            }
            else -> trimmed
        }
    }
}
