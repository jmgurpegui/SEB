package es.educamadrid.seb.client

import android.app.Activity
import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ActionMode
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import es.educamadrid.seb.client.databinding.ActivityMainBinding
import es.educamadrid.seb.client.databinding.DialogTeacherPinBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * MainActivity: EducaMadrid Safe Exam Browser Client.
 *
 * Implementa seguridad estricta para exámenes en Moodle (Aula Virtual EducaMadrid):
 * - Detección y bloqueo activo de emuladores de PC.
 * - FLAG_SECURE contra capturas y grabación de pantalla.
 * - Screen Pinning (startLockTask) con bucle activo de supervisión.
 * - Registro de infracciones en pérdida de foco y cambio de ventana.
 * - Inyección de cabeceras criptográficas SEB (RequestHash y ConfigKeyHash).
 * - PIN dinámico de sesión transmitido por QR con SHA-256 (y PIN maestro de respaldo).
 * - Whitelist estricta de dominios (*.educa.madrid.org).
 * - Detección automática de entrega de examen (Quit URL / review.php) sin pedir PIN.
 * - User-Agent SEB 3.7.1.
 * - Inyección JS anti-copia, anti-selección y bloqueo total de portapapeles.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        /**
         * URL pública de descarga directa del APK en GitHub Releases.
         * Actualizado automáticamente por el script deploy_to_web.sh.
         */
        const val APK_DOWNLOAD_URL: String =
            "https://github.com/jmgurpegui/SEB/releases/latest/download/educamadrid-seb.apk"
    }

    private lateinit var binding: ActivityMainBinding

    // Estado del examen y Kiosk
    private var isExamActive: Boolean = false
    private var isTeacherDialogShowing: Boolean = false
    private var isExitingWithTeacherPin: Boolean = false
    private var infractionCount: Int = 0

    // Parámetros de sesión configurados dinámicamente por QR o Intent
    private var currentSessionPinHash: String? = null
    private var isAutoQuitEnabled: Boolean = false
    private var hasEnteredQuizAttempt: Boolean = false

    // Constantes criptográficas SEB configurables
    private var currentBrowserExamKey: String = SebCryptoUtils.DEFAULT_BROWSER_EXAM_KEY
    private var currentConfigKeyHash: String = SebCryptoUtils.DEFAULT_CONFIG_KEY_HASH

    // Bucle de supervisión continua de Screen Pinning (cada 1000 ms)
    private val lockTaskHandler = Handler(Looper.getMainLooper())
    private val lockTaskCheckRunnable = object : Runnable {
        override fun run() {
            if (isExamActive && !isExitingWithTeacherPin) {
                verifyScreenPinningState()
                lockTaskHandler.postDelayed(this, 1000)
            }
        }
    }

    // Receptor para escáner QR de examen
    private val qrScanLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scannedUrl = result.data?.getStringExtra("SCAN_RESULT")
            if (!scannedUrl.isNullOrBlank()) {
                startExam(scannedUrl)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 1. BLOQUEO ESTRICTO DE CAPTURAS Y GRABACIÓN (ANTES DE CARGAR CUALQUIER VISTA)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        // Mantener pantalla encendida durante el examen
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        super.onCreate(savedInstanceState)

        // 2. DETECCIÓN Y BLOQUEO DE EMULADORES DE PC
        if (isRunningOnEmulator()) {
            showEmulatorBlockedDialog()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupBackPressHandler()
        setupListeners()
        configureSecureWebView()

        // Procesar posibles Deep Links recibidos en el arranque
        handleIntent(intent)
    }

    /**
     * Detecta si la aplicación se está ejecutando dentro de un emulador de PC
     * (BlueStacks, Genymotion, Windows Subsystem for Android, Android Studio Emulator, etc.)
     */
    private fun isRunningOnEmulator(): Boolean {
        val fingerprint = Build.FINGERPRINT.lowercase(Locale.getDefault())
        val model = Build.MODEL.lowercase(Locale.getDefault())
        val manufacturer = Build.MANUFACTURER.lowercase(Locale.getDefault())
        val hardware = Build.HARDWARE.lowercase(Locale.getDefault())
        val product = Build.PRODUCT.lowercase(Locale.getDefault())
        val board = Build.BOARD.lowercase(Locale.getDefault())
        val brand = Build.BRAND.lowercase(Locale.getDefault())
        val device = Build.DEVICE.lowercase(Locale.getDefault())

        return fingerprint.contains("generic") ||
                fingerprint.startsWith("unknown") ||
                model.contains("google_sdk") ||
                model.contains("emulator") ||
                model.contains("android sdk built for") ||
                model.contains("sdk") ||
                hardware.contains("goldfish") ||
                hardware.contains("ranchu") ||
                product.contains("sdk") ||
                product.contains("google_sdk") ||
                product.contains("sdk_x86") ||
                product.contains("vbox86p") ||
                product.contains("emulator") ||
                manufacturer.contains("genymotion") ||
                (brand.startsWith("generic") && device.startsWith("generic")) ||
                board == "goldfish"
    }

    /**
     * Muestra alerta bloqueante si se ejecuta en emulador y cierra la aplicación.
     */
    private fun showEmulatorBlockedDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Dispositivo no compatible")
            .setMessage("No se permite ejecutar EducaMadrid SEB Client en emuladores de PC para garantizar la integridad y seguridad del examen.\n\nPor favor, utiliza un teléfono móvil o tablet física.")
            .setCancelable(false)
            .setPositiveButton("Cerrar") { _, _ ->
                finishAffinity()
            }
            .show()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /**
     * Intercepta el botón Atrás del sistema para evitar que el alumno abandone el examen.
     */
    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isExamActive) {
                    if (binding.examWebView.canGoBack()) {
                        binding.examWebView.goBack()
                    } else {
                        Toast.makeText(
                            this@MainActivity,
                            "Modo examen bloqueado. Solo el docente puede finalizar.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    /**
     * Configuración de eventos de UI y botones.
     */
    private fun setupListeners() {
        binding.btnStartExam.setOnClickListener {
            val url = binding.etExamUrl.text?.toString()?.trim()
            if (!url.isNullOrEmpty()) {
                startExam(url)
            } else {
                binding.tilExamUrl.error = "Introduce una URL válida"
            }
        }

        binding.btnScanQr.setOnClickListener {
            launchQrScanner()
        }

        binding.btnPinScreenNow.setOnClickListener {
            try {
                startLockTask()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        binding.btnTeacherUnlock.setOnClickListener {
            showTeacherPinDialog()
        }

        binding.btnTeacherExit.setOnClickListener {
            showTeacherPinDialog()
        }

        binding.btnAutoQuit.setOnClickListener {
            triggerAutoQuitExit()
        }
    }

    /**
     * Inicia el escaneo de código QR del examen desde la pizarra del aula.
     */
    private fun launchQrScanner() {
        try {
            val intent = Intent("com.google.zxing.client.android.SCAN")
            intent.putExtra("SCAN_MODE", "QR_CODE_MODE")
            qrScanLauncher.launch(intent)
        } catch (_: Exception) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Escanear QR de la Pizarra")
                .setMessage("Apunta con la aplicación de Cámara de tu dispositivo al código QR proyectado por el docente en la pizarra.\n\nAl reconocer el enlace 'sebs://', tu dispositivo ofrecerá abrirlo directamente con esta aplicación.")
                .setPositiveButton("Entendido", null)
                .show()
        }
    }

    /**
     * Configuración avanzada y segura del WebView.
     */
    private fun configureSecureWebView() {
        binding.examWebView.apply {
            // Protección contra clics largos y menús contextuales en Android
            setOnLongClickListener { true }
            isLongClickable = false
            isHapticFeedbackEnabled = false

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                @Suppress("DEPRECATION")
                saveFormData = false
                setSupportZoom(false)
                builtInZoomControls = false
                displayZoomControls = false
                allowFileAccess = false
                allowContentAccess = false
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

                // Configuración de User-Agent con sufijo SEB estándar
                val defaultUa = userAgentString
                userAgentString = "$defaultUa SEB/3.7.1 (Android; EducaMadrid-SEB-Client)"
            }

            // Deshabilitar depuración web
            WebView.setWebContentsDebuggingEnabled(false)

            // Soporte completo de Cookies de sesión para Aula Virtual (Moodle)
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(this, true)

            // WebChromeClient para barra de progreso
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    if (newProgress < 100) {
                        binding.examProgressBar.visibility = View.VISIBLE
                        binding.examProgressBar.progress = newProgress
                    } else {
                        binding.examProgressBar.visibility = View.GONE
                    }
                }
            }

            // WebViewClient con inyección criptográfica SEB, whitelist de dominios y script anti-fraude
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val targetUrl = request?.url?.toString() ?: return false

                    // 1. Detección de Quit URL / seb://quit / sebs://quit
                    if (targetUrl.startsWith("seb://quit", ignoreCase = true) ||
                        targetUrl.startsWith("sebs://quit", ignoreCase = true) ||
                        targetUrl.contains("quit.php", ignoreCase = true)
                    ) {
                        triggerAutoQuitExit()
                        return true
                    }

                    // 2. Whitelist estricta de dominios de EducaMadrid (*.educa.madrid.org)
                    if (!SebCryptoUtils.isAllowedDomain(targetUrl)) {
                        Toast.makeText(
                            this@MainActivity,
                            "Navegación bloqueada: Solo se permite acceso a dominios autorizados (*.educa.madrid.org)",
                            Toast.LENGTH_LONG
                        ).show()
                        return true
                    }

                    loadUrlWithSebHeaders(targetUrl)
                    return true
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    binding.tvExamHeaderTitle.text = url ?: getString(R.string.status_locked)
                    url?.let { checkAutoQuitProgression(it) }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    injectAntiAiScript(view)
                    url?.let { checkAutoQuitProgression(it) }
                }
            }
        }
    }

    /**
     * Evalúa el progreso del examen en Moodle para activar el botón verde de salida automática.
     */
    private fun checkAutoQuitProgression(url: String) {
        val lower = url.lowercase(Locale.getDefault())

        // El alumno ha entrado en la resolución del intento
        if (lower.contains("attempt.php") || lower.contains("processattempt.php")) {
            hasEnteredQuizAttempt = true
        }

        // Si auto_quit está habilitado y el alumno ha navegado a la página de revisión o finalización
        if (hasEnteredQuizAttempt && isAutoQuitEnabled) {
            if (lower.contains("review.php") ||
                lower.contains("view.php") ||
                lower.contains("summary.php")
            ) {
                binding.btnAutoQuit.visibility = View.VISIBLE
            }
        }
    }

    /**
     * Inyecta cabeceras HTTP X-SafeExamBrowser-RequestHash y X-SafeExamBrowser-ConfigKeyHash
     * calculadas sobre la URL destino.
     */
    private fun loadUrlWithSebHeaders(url: String) {
        val cleanUrl = SebCryptoUtils.normalizeSebUrl(url)
        val headers = SebCryptoUtils.buildSebHeaders(
            cleanUrl,
            currentBrowserExamKey,
            currentConfigKeyHash
        )
        binding.examWebView.loadUrl(cleanUrl, headers)
    }

    /**
     * Inyecta script JavaScript en cada carga de página para anular selección,
     * copia, pegado o menús contextuales en preguntas, permitiendo escribir en inputs.
     */
    private fun injectAntiAiScript(webView: WebView?) {
        val script = """
            (function() {
              var style = document.createElement('style');
              style.innerHTML = '* { -webkit-touch-callout: none !important; -webkit-user-select: none !important; user-select: none !important; } input, textarea { -webkit-user-select: text !important; user-select: text !important; }';
              document.head.appendChild(style);
              ['copy', 'cut', 'paste', 'contextmenu', 'selectstart', 'dragstart'].forEach(function(evt) {
                document.addEventListener(evt, function(e) { e.preventDefault(); }, true);
              });
            })();
        """.trimIndent()

        webView?.evaluateJavascript(script, null)
    }

    /**
     * Inicia el modo de examen seguro:
     * - Extrae y almacena en memoria seb_pin_hash y auto_quit.
     * - Elimina estos parámetros de la URL para no alterar el cálculo de hashes de Moodle.
     * - Ancla la pantalla y lanza la URL limpia.
     */
    private fun startExam(rawUrl: String) {
        // Extracción y limpieza de parámetros de sesión
        val sessionConfig = SebCryptoUtils.extractAndCleanSessionParams(rawUrl)
        currentSessionPinHash = sessionConfig.sessionPinHash
        isAutoQuitEnabled = sessionConfig.autoQuitEnabled
        hasEnteredQuizAttempt = false

        val cleanUrl = sessionConfig.cleanUrl

        isExamActive = true
        isExitingWithTeacherPin = false

        binding.btnAutoQuit.visibility = View.GONE
        binding.layoutHome.visibility = View.GONE
        binding.layoutPausedScreen.visibility = View.GONE
        binding.layoutInfractionScreen.visibility = View.GONE
        binding.layoutExam.visibility = View.VISIBLE

        binding.etExamUrl.setText(cleanUrl)

        // Solicita el anclaje de pantalla (Screen Pinning / Kiosk)
        try {
            startLockTask()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Carga inicial con cabeceras SEB y URL limpia
        loadUrlWithSebHeaders(cleanUrl)

        // Arranca el bucle de verificación de 1000 ms
        lockTaskHandler.removeCallbacks(lockTaskCheckRunnable)
        lockTaskHandler.post(lockTaskCheckRunnable)
    }

    /**
     * Comprueba activamente el estado de fijación de pantalla (Screen Pinning).
     * Si el alumno desancló o rechazó el anclaje, oculta el WebView y muestra pantalla roja.
     */
    private fun verifyScreenPinningState() {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val lockMode = activityManager.lockTaskModeState
        val isPinned = (lockMode != ActivityManager.LOCK_TASK_MODE_NONE)

        if (!isPinned) {
            if (binding.layoutInfractionScreen.visibility != View.VISIBLE) {
                binding.examWebView.visibility = View.GONE
                binding.layoutPausedScreen.visibility = View.VISIBLE
            }
        } else {
            if (binding.layoutInfractionScreen.visibility != View.VISIBLE) {
                binding.layoutPausedScreen.visibility = View.GONE
                binding.examWebView.visibility = View.VISIBLE
            }
        }
    }

    /**
     * Detección de pérdida de foco en la ventana (superposiciones, apps flotantes, división de pantalla).
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (isExamActive && !isExitingWithTeacherPin && !isTeacherDialogShowing) {
            if (!hasFocus) {
                registerInfraction("Pérdida de foco / Superposición o cambio de ventana")
            }
        }
    }

    /**
     * Detección de paso a segundo plano.
     */
    override fun onPause() {
        super.onPause()
        if (isExamActive && !isExitingWithTeacherPin && !isTeacherDialogShowing) {
            registerInfraction("Paso a segundo plano o cambio de aplicación")
        }
    }

    /**
     * Registra una infracción de seguridad y bloquea la interfaz de examen.
     */
    private fun registerInfraction(@Suppress("UNUSED_PARAMETER") reason: String) {
        clearClipboard()

        infractionCount++
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

        binding.examWebView.visibility = View.GONE
        binding.layoutPausedScreen.visibility = View.GONE
        binding.layoutInfractionScreen.visibility = View.VISIBLE

        binding.tvInfractionTimestamp.text = getString(R.string.msg_infraction_detected, timestamp)
        binding.tvInfractionTotalCount.text = getString(R.string.label_infraction_count, infractionCount)
    }

    /**
     * Limpia completamente el portapapeles del dispositivo.
     */
    private fun clearClipboard() {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
        } catch (_: Exception) {}
    }

    /**
     * Muestra el diálogo de control docente para desbloquear infracciones o salir del examen.
     * Valida el PIN contra el PIN de sesión dinámico (SHA-256) o el PIN maestro ("8426").
     */
    private fun showTeacherPinDialog() {
        isTeacherDialogShowing = true
        val dialogBinding = DialogTeacherPinBinding.inflate(layoutInflater)

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogBinding.root)
            .setCancelable(false)
            .setPositiveButton(R.string.btn_resume_exam, null)
            .setNegativeButton(R.string.btn_exit_exam, null)
            .setNeutralButton(R.string.btn_cancel) { d, _ ->
                d.dismiss()
                isTeacherDialogShowing = false
            }
            .create()

        dialog.setOnShowListener {
            val positiveBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val negativeBtn = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)

            // Reanudar examen
            positiveBtn.setOnClickListener {
                val inputPin = dialogBinding.etTeacherPin.text?.toString()?.trim() ?: ""
                if (SebCryptoUtils.isPinValid(inputPin, currentSessionPinHash)) {
                    dialogBinding.tvPinError.visibility = View.GONE
                    dialog.dismiss()
                    isTeacherDialogShowing = false
                    resumeExamAfterInfraction()
                } else {
                    dialogBinding.tvPinError.visibility = View.VISIBLE
                    dialogBinding.tvPinError.text = getString(R.string.error_invalid_pin)
                }
            }

            // Finalizar y salir limpiamente
            negativeBtn.setOnClickListener {
                val inputPin = dialogBinding.etTeacherPin.text?.toString()?.trim() ?: ""
                if (SebCryptoUtils.isPinValid(inputPin, currentSessionPinHash)) {
                    dialogBinding.tvPinError.visibility = View.GONE
                    dialog.dismiss()
                    isTeacherDialogShowing = false
                    exitExamSafely()
                } else {
                    dialogBinding.tvPinError.visibility = View.VISIBLE
                    dialogBinding.tvPinError.text = getString(R.string.error_invalid_pin)
                }
            }
        }

        dialog.show()
    }

    /**
     * Reanuda el examen tras verificación exitosa del docente.
     */
    private fun resumeExamAfterInfraction() {
        binding.layoutInfractionScreen.visibility = View.GONE
        binding.layoutPausedScreen.visibility = View.GONE
        binding.layoutExam.visibility = View.VISIBLE
        binding.examWebView.visibility = View.VISIBLE

        try {
            startLockTask()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Salida segura autorizada por el profesor: libera el anclaje de pantalla.
     */
    private fun exitExamSafely() {
        isExitingWithTeacherPin = true
        isExamActive = false
        lockTaskHandler.removeCallbacks(lockTaskCheckRunnable)

        try {
            stopLockTask()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        binding.layoutExam.visibility = View.GONE
        binding.layoutPausedScreen.visibility = View.GONE
        binding.layoutInfractionScreen.visibility = View.GONE
        binding.layoutHome.visibility = View.VISIBLE
        binding.examWebView.loadUrl("about:blank")
    }

    /**
     * Salida automática al entregar el examen (auto_quit activo):
     * Libera Screen Pinning, borra cookies de sesión y cierra la app sin pedir PIN.
     */
    private fun triggerAutoQuitExit() {
        isExitingWithTeacherPin = true
        isExamActive = false
        lockTaskHandler.removeCallbacks(lockTaskCheckRunnable)

        try {
            stopLockTask()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            val cookieManager = CookieManager.getInstance()
            cookieManager.removeAllCookies(null)
            cookieManager.flush()
            binding.examWebView.clearCache(true)
            binding.examWebView.loadUrl("about:blank")
        } catch (_: Exception) {}

        Toast.makeText(this, "Examen entregado correctamente. Sesión finalizada.", Toast.LENGTH_LONG).show()
        finishAffinity()
    }

    /**
     * Procesa Deep Links con esquemas seb:// y sebs://
     */
    private fun handleIntent(intent: Intent?) {
        val data = intent?.data ?: return
        val scheme = data.scheme?.lowercase(Locale.getDefault())

        if (scheme == "seb" || scheme == "sebs") {
            startExam(data.toString())
        }
    }

    override fun onActionModeStarted(mode: ActionMode?) {
        if (isExamActive) {
            mode?.finish()
        }
        super.onActionModeStarted(mode)
    }

    override fun onDestroy() {
        lockTaskHandler.removeCallbacks(lockTaskCheckRunnable)
        super.onDestroy()
    }
}
