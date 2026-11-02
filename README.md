# EducaMadrid SEB Ecosystem & Client

> **Portal Multiplataforma y Cliente Seguro para Exámenes en Aula Virtual EducaMadrid (Moodle)**  
> Compatible con el protocolo internacional **Safe Exam Browser (SEB)**, Kiosk estricto, detección de sistema operativo, pizarra docente interactiva con códigos QR y cliente Android especializado para entornos personales (BYOD).
> 
> 🌐 **Portal Web:** [https://jmgurpegui.github.io/SEB/](https://jmgurpegui.github.io/SEB/)

---

## 🌐 Portal Web Multiplataforma (`https://jmgurpegui.github.io/SEB/`)

El portal web estático alojado en GitHub Pages centraliza el ecosistema SEB para docentes y estudiantes de la Comunidad de Madrid con:

* **Detección Automática de Sistema Operativo:**  
  Identifica en tiempo real si el usuario navega desde **Windows**, **macOS**, **iOS/iPadOS** o **Android**, recomendando y ofreciendo el botón de descarga directa de su versión certificada.
* **Soporte Multilingüe (i18n):**  
  Selector instantáneo entre **Español (ES)** e **Inglés (EN)** con persistencia en `localStorage`, ideal para centros bilingües y programas internacionales.
* **Pizarra Docente (Lanzador QR y Proyector):**  
  Herramienta interactiva que convierte al instante cualquier URL de examen Moodle EducaMadrid (`https://aulavirtual...`) al protocolo seguro `sebs://`, generando un código QR gigante con modo proyector de pantalla completa para el aula.
* **Guía Paso a Paso para Docentes:**  
  Instrucciones claras para la configuración de cuestionarios en Moodle (BEK, ConfigKey, modo Kiosk).

---

## 💻 Matriz de Clientes Oficiales por Sistema Operativo

| Plataforma | Versión Certificada | Tipo de Paquete | Enlace de Descarga / Fuente | Características Clave |
|---|---|---|---|---|
| 🪟 **Windows** | **SEB v3.10.2** | `SetupBundle.exe` (~361 MB) | [SafeExamBrowser Windows Release](https://github.com/SafeExamBrowser/seb-win-refactoring/releases/download/v3.10.2/SEB_3.10.2.920_SetupBundle.exe) | Motor Chromium WebView2, bloqueo Kiosk, bloqueo de atajos de teclado (Alt+Tab, Win, Ctrl+Esc), cálculo BEK/CKH. |
| 🍏 **macOS** | **SEB v3.7.1** | `SafeExamBrowser-3.7.1.dmg` (~11 MB) | [SafeExamBrowser macOS Release](https://github.com/SafeExamBrowser/seb-mac/releases/download/3.7.1/SafeExamBrowser-3.7.1.dmg) | Apple Silicon (M1/M2/M3/M4) y binario universal, modo evaluación AAC de Apple, bloqueo de Dock y Mission Control. |
| 📱 **iOS / iPadOS** | **SEB Oficial** | App Store | [Apple App Store (id1155002964)](https://apps.apple.com/app/safeexambrowser/id1155002964) | Modo Evaluación Automática de Apple (AAC), restricción de pantalla dividida, ideal para carritos de iPads. |
| 🤖 **Android (BYOD)** | **EducaMadrid SEB v3.7.1** | `educamadrid-seb.apk` (~7 MB) | [GitHub Releases (Cloud Build)](https://github.com/jmgurpegui/SEB/releases/latest/download/educamadrid-seb.apk) | Cliente exclusivo para resolver la ausencia de cliente oficial en Android. Fijación de pantalla obligatoria, anti-IA y anti-capturas. |

---

## 🛡️ Características de Ciberseguridad del Cliente Android

Debido a que el consorcio oficial Safe Exam Browser no proporciona soporte directo para Android, este repositorio incluye la implementación nativa del cliente Android optimizada para BYOD escolar:

### 1. Bloqueo de Sistema y Anti-Fraude Estricto (Kiosk / Screen Pinning)
* **Prevención de Grabación y Capturas (`FLAG_SECURE`):**  
  Activado antes de inflar la vista. Bloquea capturas de pantalla, grabación de vídeo del sistema, miniaturas en el menú de aplicaciones recientes y retransmisión inalámbrica (Chromecast / Miracast).
* **Bloqueo de Multiventana y Pantalla Dividida:**  
  Protegido a nivel de manifiesto (`resizeableActivity="false"`, `supportsPictureInPicture="false"`, `screenOrientation="portrait"`), neutralizando ventanas flotantes de Xiaomi HyperOS/MIUI, Samsung One UI, etc.
* **Bucle Activo Anti-Bypass (Screen Pinning):**  
  Supervisión en tiempo real cada 1000 ms mediante `ActivityManager.lockTaskModeState`. Si el alumno cancela el diálogo o desancla la pantalla, el `WebView` se oculta inmediatamente y se detiene el examen hasta que se vuelva a fijar la pantalla.
* **Detección de Pérdida de Foco e Infracciones:**  
  Intercepta `onWindowFocusChanged(false)` y `onPause()`. Si se produce una superposición, se registra la infracción con timestamp, se vacía el portapapeles y se bloquea la vista con pantalla roja. Solo el docente puede desbloquear con el **PIN Docente Maestro (`8426`)**.

### 2. Compatibilidad Criptográfica con Moodle Safe Exam Browser
* **User-Agent SEB Estricto:**  
  `Mozilla/... AppleWebKit/... Chrome/... SEB/3.7.1 (Android; EducaMadrid-SEB-Client)`
* **Cabeceras HTTP Criptográficas:**  
  Calcula e inyecta dinámicamente en cada petición interceptada:
  - `X-SafeExamBrowser-RequestHash`: Hash SHA-256 en minúsculas de la concatenación `URL_sin_fragmento + BROWSER_EXAM_KEY`.
  - `X-SafeExamBrowser-ConfigKeyHash`: Clave de configuración del cuestionario (`CONFIG_KEY_HASH`).
* **Soporte de Enlaces Profundos (`seb://` y `sebs://`):**  
  Apertura automática desde el navegador, correo o escáner QR.

### 3. Protección Anti-IA y Bloqueo de Portapapeles
* **Anulación de Clic Largo y Menú Contextual:**  
  Neutraliza menús flotantes ("Copiar", "Compartir", "Consultar a Inteligencia Artificial").
* **Inyección JavaScript Anti-Selección:**  
  Bloquea `copy`, `cut`, `paste`, `contextmenu`, `selectstart` y `dragstart` en todo el documento, permitiendo únicamente escribir respuestas en campos interactivos `<input>` y `<textarea>`.

---

## 📁 Estructura del Repositorio

```
SEB/
├── .github/
│   └── workflows/
│       └── deploy-cloud.yml         # CI/CD: Compilación del APK y despliegue a GitHub Pages & Releases
├── docs/
│   └── index.html                   # Portal Web Multiplataforma (i18n ES/EN, Detección OS, Pizarra y QR)
├── app/
│   ├── build.gradle.kts             # Módulo Android (minSdk 26, targetSdk 34)
│   ├── src/main/
│   │   ├── AndroidManifest.xml      # Permisos, filtros de intención seb:// y flags Kiosk
│   │   ├── java/es/educamadrid/seb/client/
│   │   │   ├── MainActivity.kt      # Seguridad Kiosk, Screen Pinning, WebView y ciclo de vida
│   │   │   └── SebCryptoUtils.kt    # Criptografía SHA-256 (BEK, RequestHash, CKH)
│   │   └── res/                     # Layouts, iconos vectoriales y recursos visuales
├── deploy_to_web.sh                 # Automatización de despliegue a GitHub
└── README.md
```

---

## 🏫 Configuración en Moodle (Aula Virtual EducaMadrid)

1. En el cuestionario de tu curso en el Aula Virtual EducaMadrid, haz clic en **Editar ajustes**.
2. Despliega la sección **Safe Exam Browser**.
3. En el campo *Requerir el uso de Safe Exam Browser*, selecciona **Sí - Configurar manualmente** o **Sí - Usar configuración de SEB existente**.
4. En **Claves de examen de Safe Exam Browser (Browser Exam Key)**, introduce la clave SHA-256 de 64 caracteres.
5. Copia la URL del examen y pégala en la **Pizarra Docente** de [https://jmgurpegui.github.io/SEB/](https://jmgurpegui.github.io/SEB/) para proyectar el código QR en el aula.

---

## 🚀 Despliegue y Publicación

Para sincronizar y desplegar actualizaciones del portal y del cliente Android:

```bash
chmod +x deploy_to_web.sh
./deploy_to_web.sh https://github.com/jmgurpegui/SEB.git
```

El flujo de GitHub Actions compilará automáticamente el APK firmado, publicará la Release `latest` y desplegará el portal estático en GitHub Pages.
