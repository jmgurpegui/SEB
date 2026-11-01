package es.educamadrid.seb.client

import android.app.Activity
import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
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
 * - FLAG_SECURE contra capturas y grabación de pantalla.
 * - Screen Pinning (startLockTask) con bucle activo de supervisión.
 * - Registro de infracciones en pérdida de foco y cambio de ventana.
 * - Inyección de cabeceras criptográficas SEB (RequestHash y ConfigKeyHash).
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

    // Constantes criptográficas SEB configurables
    private var currentBrowserExamKey: String = SebCryptoUtils.DEFAULT_BROWSER_EXAM_KEY
    private var currentConfigKeyHash: String = SebCryptoUtils.DEFAULT_CONFIG_KEY_HASH
    private var teacherPin: String = SebCryptoUtils.DEFAULT_TEACHER_PIN

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
                val normalized = SebCryptoUtils.normalizeSebUrl(scannedUrl)
                binding.etExamUrl.setText(normalized)
                startExam(normalized)
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
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupBackPressHandler()
        setupListeners()
        configureSecureWebView()

        // Procesar posibles Deep Links recibidos en el arranque
        handleIntent(intent)
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
            // Si el dispositivo no cuenta con lector ZXing integrado,
            // instruir al alumno para escanear con la cámara nativa de Android
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

            // Bloquea el menú de selección de texto flotante del sistema (Copy/Paste/Share/AI)
            customSelectionActionModeCallback = object : ActionMode.Callback {
                override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean = false
                override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean = false
                override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean = false
                override fun onDestroyActionMode(mode: ActionMode?) {}
            }

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
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

            // WebViewClient con inyección criptográfica SEB y script anti-fraude
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val targetUrl = request?.url?.toString() ?: return false
                    loadUrlWithSebHeaders(targetUrl)
                    return true
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    binding.tvExamHeaderTitle.text = url ?: getString(R.string.status_locked)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    injectAntiAiScript(view)
                }
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
     * Inicia el modo de examen seguro: ancla la pantalla y lanza la URL.
     */
    private fun startExam(rawUrl: String) {
        val url = SebCryptoUtils.normalizeSebUrl(rawUrl)
        isExamActive = true
        isExitingWithTeacherPin = false

        binding.layoutHome.visibility = View.GONE
        binding.layoutPausedScreen.visibility = View.GONE
        binding.layoutInfractionScreen.visibility = View.GONE
        binding.layoutExam.visibility = View.VISIBLE

        // Solicita el anclaje de pantalla (Screen Pinning / Kiosk)
        try {
            startLockTask()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Carga inicial con cabeceras SEB
        loadUrlWithSebHeaders(url)

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
            // El alumno desancló la pantalla o pulsó "No, gracias"
            if (binding.layoutInfractionScreen.visibility != View.VISIBLE) {
                binding.examWebView.visibility = View.GONE
                binding.layoutPausedScreen.visibility = View.VISIBLE
            }
        } else {
            // Pantalla correctamente anclada
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
    private fun registerInfraction(reason: String) {
        // Vaciado preventivo de portapapeles
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
                val inputPin = dialogBinding.etTeacherPin.text?.toString()?.trim()
                if (inputPin == teacherPin) {
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
                val inputPin = dialogBinding.etTeacherPin.text?.toString()?.trim()
                if (inputPin == teacherPin) {
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
     * Procesa Deep Links con esquemas seb:// y sebs://
     */
    private fun handleIntent(intent: Intent?) {
        val data = intent?.data ?: return
        val scheme = data.scheme?.lowercase(Locale.getDefault())

        if (scheme == "seb" || scheme == "sebs") {
            val normalizedUrl = SebCryptoUtils.normalizeSebUrl(data.toString())
            binding.etExamUrl.setText(normalizedUrl)
            startExam(normalizedUrl)
        }
    }

    override fun onDestroy() {
        lockTaskHandler.removeCallbacks(lockTaskCheckRunnable)
        super.onDestroy()
    }
}
