package es.educamadrid.seb.client

import java.net.URI
import java.security.MessageDigest

/**
 * Utilidades criptográficas y de sesión para el protocolo Safe Exam Browser (SEB).
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
     * PIN Docente maestro de respaldo para desbloqueo de emergencia e infracciones.
     */
    const val DEFAULT_TEACHER_PIN: String = "8426"

    /**
     * Configuración de sesión extraída de los parámetros del QR / Intent.
     */
    data class SebSessionConfig(
        val cleanUrl: String,
        val sessionPinHash: String?,
        val autoQuitEnabled: Boolean
    )

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

    /**
     * Extrae seb_pin_hash y auto_quit de la URL y los elimina de la misma
     * para que no alteren el cálculo del RequestHash ni la petición enviada a Moodle.
     */
    fun extractAndCleanSessionParams(rawUrl: String): SebSessionConfig {
        val normalized = normalizeSebUrl(rawUrl)

        // Extraer fragmento si existe
        val hashIndex = normalized.indexOf('#')
        val fragment = if (hashIndex != -1) normalized.substring(hashIndex) else ""
        val urlWithoutFragment = if (hashIndex != -1) normalized.substring(0, hashIndex) else normalized

        val queryIndex = urlWithoutFragment.indexOf('?')
        if (queryIndex == -1) {
            return SebSessionConfig(
                cleanUrl = normalized,
                sessionPinHash = null,
                autoQuitEnabled = false
            )
        }

        val baseUrl = urlWithoutFragment.substring(0, queryIndex)
        val queryString = urlWithoutFragment.substring(queryIndex + 1)
        var sessionPinHash: String? = null
        var autoQuit = false
        val remainingParams = mutableListOf<String>()

        for (param in queryString.split('&')) {
            if (param.isBlank()) continue
            val parts = param.split('=', limit = 2)
            val key = parts[0].trim()
            val value = if (parts.size > 1) parts[1].trim() else ""
            when (key) {
                "seb_pin_hash" -> {
                    sessionPinHash = value.ifBlank { null }
                }
                "auto_quit" -> {
                    autoQuit = (value == "1" || value.equals("true", ignoreCase = true))
                }
                else -> {
                    remainingParams.add(param)
                }
            }
        }

        val newQuery = if (remainingParams.isNotEmpty()) {
            "?" + remainingParams.joinToString("&")
        } else {
            ""
        }

        val cleanedUrl = baseUrl + newQuery + fragment

        return SebSessionConfig(
            cleanUrl = cleanedUrl,
            sessionPinHash = sessionPinHash,
            autoQuitEnabled = autoQuit
        )
    }

    /**
     * Valida si un PIN introducido por el docente es válido:
     * - Coincide con el PIN de sesión dinámico (comparando su SHA-256).
     * - O coincide con el PIN maestro de respaldo ("8426").
     */
    fun isPinValid(inputPin: String, sessionPinHash: String?): Boolean {
        val trimmed = inputPin.trim()
        if (trimmed == DEFAULT_TEACHER_PIN) {
            return true
        }
        if (!sessionPinHash.isNullOrBlank()) {
            val inputHash = sha256(trimmed)
            if (inputHash.equals(sessionPinHash.trim(), ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    /**
     * Whitelist de dominios autorizados de EducaMadrid (*.educa.madrid.org y *.educamadrid.org).
     */
    fun isAllowedDomain(url: String): Boolean {
        return try {
            val trimmed = url.trim().lowercase()
            if (trimmed == "about:blank") return true

            val uri = URI(trimmed)
            val host = uri.host?.lowercase() ?: return false

            host == "educa.madrid.org" ||
                    host.endsWith(".educa.madrid.org") ||
                    host == "educamadrid.org" ||
                    host.endsWith(".educamadrid.org")
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Evalúa si una URL de Moodle debe activar el botón verde de salida automática (auto_quit):
     * - summary.php está expresamente EXCLUIDO (paso intermedio donde el alumno aún debe confirmar el envío).
     * - Retorna true si es una URL de revisión final (review.php).
     * - Retorna true si es un esquema o enlace de salida SEB (seb://quit, sebs://quit, quit.php).
     * - Retorna true si regresa a la portada del cuestionario (mod/quiz/view.php) habiendo procesado
     *   la entrega previamente a través de processattempt.php.
     */
    fun shouldTriggerAutoQuit(url: String, hasProcessedAttempt: Boolean): Boolean {
        val lower = url.trim().lowercase()

        // 1. Exclusión explícita de summary.php (pantalla de resumen previo a enviar)
        if (lower.contains("summary.php")) {
            return false
        }

        // 2. Esquemas o URLs de salida SEB
        if (lower.startsWith("seb://quit") ||
            lower.startsWith("sebs://quit") ||
            lower.contains("quit.php")
        ) {
            return true
        }

        // 3. Pantalla de revisión final posterior a la entrega
        if (lower.contains("review.php")) {
            return true
        }

        // 4. Regreso a la portada del cuestionario tras haber procesado el intento
        if (hasProcessedAttempt && lower.contains("mod/quiz/view.php")) {
            return true
        }

        return false
    }
}
