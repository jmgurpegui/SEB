# EducaMadrid SEB Client (Android)

> **Cliente Android Seguro para Exámenes en Moodle (Aula Virtual EducaMadrid)**  
> Compatible con el protocolo **Safe Exam Browser (SEB)**, Kiosk / Screen Pinning estricto y protección activa contra fraude, Inteligencia Artificial y fugas de pantalla en dispositivos personales (BYOD).

---

## 🛡️ Características Principales de Ciberseguridad

### 1. Bloqueo de Sistema y Anti-Fraude Estricto (Kiosk / Screen Pinning)
* **Prevención de Grabación y Capturas (`FLAG_SECURE`):**  
  Activado en `onCreate()` antes de inflar la vista. Bloquea capturas de pantalla, grabación de vídeo del sistema, previsualizaciones en el menú de aplicaciones recientes y retransmisión inalámbrica (Chromecast / Miracast).
* **Bloqueo de Multiventana y Pantalla Dividida:**  
  Protegido a nivel de manifiesto (`resizeableActivity="false"`, `supportsPictureInPicture="false"`, `screenOrientation="portrait"`, `launchMode="singleTask"`), neutralizando capas de personalización con ventanas flotantes (Xiaomi HyperOS/MIUI, Samsung One UI, etc.).
* **Bucle Activo de Supervisión Anti-Bypass (Screen Pinning):**  
  Supervisión en tiempo real cada 1000 ms mediante `ActivityManager.lockTaskModeState`. Si el alumno cancela el diálogo o desancla la pantalla:
  1. El `WebView` se oculta inmediatamente (`View.GONE`).
  2. Se muestra un panel rojo a pantalla completa:  
     `EXAMEN PAUSADO: Es obligatorio aceptar 'Fijar pantalla' para visualizar el cuestionario`
  3. Proporciona el botón *Fijar pantalla ahora* para volver a invocar `startLockTask()`.
* **Detección de Pérdida de Foco e Infracciones:**  
  Intercepta `onWindowFocusChanged(false)` y `onPause()`. Si se produce una superposición o intento de cambio de ventana, se registra la infracción con timestamp (`HH:mm:ss`), se vacía el portapapeles y se bloquea la vista con pantalla roja de infracción. Solo el docente puede reactivar el examen introduciendo el **PIN Docente**.

### 2. Compatibilidad Criptográfica con Moodle Safe Exam Browser (SEB)
* **User-Agent SEB:**  
  Sufijo estricto conforme a la especificación oficial:  
  `Mozilla/... AppleWebKit/... Chrome/... SEB/3.7.1 (Android; EducaMadrid-SEB-Client)`
* **Cabeceras HTTP Criptográficas:**  
  Calcula e inyecta dinámicamente en cada petición interceptada en `WebViewClient`:
  - `X-SafeExamBrowser-RequestHash`: Hash SHA-256 en minúsculas de la concatenación `URL_sin_fragmento + BROWSER_EXAM_KEY`.
  - `X-SafeExamBrowser-ConfigKeyHash`: Clave de configuración del cuestionario (`CONFIG_KEY_HASH`).
* **Soporte de Deep Links (`seb://` y `sebs://`):**  
  Asociación de filtros de intención en el sistema Android para capturar enlaces SEB de EducaMadrid, convirtiendo esquemas a `https://` y activando automáticamente el protocolo de examen seguro.

### 3. Protección Anti-IA y Bloqueo de Portapapeles
* **Anulación de Clic Largo y Menú Contextual:**  
  `setOnLongClickListener { true }`, `isLongClickable = false` y anulación del callback `customSelectionActionModeCallback` para impedir los menús flotantes de selección ("Copiar", "Compartir", "Preguntar a IA", etc.).
* **Inyección JavaScript Anti-Selección:**  
  En `onPageFinished()`, inyecta estilos CSS y listeners con captura que bloquean `copy`, `cut`, `paste`, `contextmenu`, `selectstart` y `dragstart` en todo el documento, preservando únicamente la interacción con campos de texto interactivos (`<input>` y `<textarea>`).
* **Vaciado Proactivo del Portapapeles:**  
  En cualquier evento de infracción o inicio de examen se sobreescribe el contenido del portapapeles con una cadena vacía.

---

## 📁 Estructura del Proyecto

```
SEB/
├── .github/
│   └── workflows/
│       └── deploy-cloud.yml             # CI/CD: Compilación en runner de GitHub y despliegue a Pages & Releases
├── docs/
│   └── index.html                       # Portal web estático (TailwindCSS + QRCode.js): Vista Alumno & Pizarra Docente
├── app/
│   ├── build.gradle.kts                 # Configuración del módulo, minSdk 26, targetSdk 34
│   ├── proguard-rules.pro               # Reglas de ofuscación y conservación de WebViews/Crypto
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml      # Permisos, filtros de intención SEB y flags Kiosk
│       │   ├── java/es/educamadrid/seb/client/
│       │   │   ├── MainActivity.kt      # Lógica de seguridad, Screen Pinning, WebView y APK_DOWNLOAD_URL
│       │   │   └── SebCryptoUtils.kt    # SHA-256, hash de peticiones y normalización de URLs
│       │   └── res/
│       │       ├── layout/              # activity_main.xml y dialog_teacher_pin.xml
│       │       ├── values/              # strings.xml, colors.xml, themes.xml
│       │       └── drawable/            # Iconos vectoriales y estilos visuales
│       └── test/java/.../               # Pruebas unitarias de cálculo criptográfico SEB
├── gradle/
│   ├── libs.versions.toml               # Catálogo de dependencias de Gradle
│   └── wrapper/                         # Gradle Wrapper (Gradle 8.4)
├── build.gradle.kts                     # Configuración raíz
├── settings.gradle.kts                  # Configuración de repositorios y proyectos
├── deploy_to_web.sh                     # Script de inicialización, enlace remoto y despliegue cloud
├── build_and_qr.sh                      # Script legacy para pruebas locales
└── README.md
```

---

## ⚙️ Configuración y Credenciales Docentes

| Parámetro | Valor por Defecto | Archivo / Ubicación |
|---|---|---|
| **PIN Docente de Desbloqueo** | `8426` | `SebCryptoUtils.DEFAULT_TEACHER_PIN` |
| **Browser Exam Key (BEK)** | Hash SHA-256 (64 hex) | `SebCryptoUtils.DEFAULT_BROWSER_EXAM_KEY` |
| **Config Key Hash (CKH)** | Hash SHA-256 (64 hex) | `SebCryptoUtils.DEFAULT_CONFIG_KEY_HASH` |
| **User-Agent SEB** | `... SEB/3.7.1 (Android; EducaMadrid-SEB-Client)` | `MainActivity.kt` |
| **URL Descarga APK (Cloud)** | `https://github.com/jmgurpegui/SEB/releases/latest/download/educamadrid-seb.apk` | `MainActivity.APK_DOWNLOAD_URL` |
| **Portal Web (GitHub Pages)** | `https://jmgurpegui.github.io/SEB/` | `docs/index.html` |

---

## ☁️ Arquitectura Cloud: GitHub Actions y GitHub Pages

El proyecto prescinde por completo de servidores locales (LAN/WSL2) y funciona bajo una infraestructura en la nube:

1. **Compilación y Publicación Automática (`.github/workflows/deploy-cloud.yml`):**
   - Se ejecuta en cada `push` a la rama `main`.
   - Dispone de Java 17 y Android SDK en el runner de Ubuntu de GitHub.
   - Genera el keystore de producción y compila el APK firmado en modo Release (`./gradlew assembleRelease`).
   - Publica el archivo `educamadrid-seb.apk` en **GitHub Releases** bajo el tag permanente `latest`.
   - Despliega automáticamente el directorio `docs/` en **GitHub Pages**.

2. **Portal Web Estático y Pizarra Docente (`docs/index.html`):**
   - **Vista Alumno:** Botón de descarga de alta velocidad desde GitHub Releases, código QR dinámico de instalación y guía explicativa en 3 pasos.
   - **Pizarra Docente:** Campo interactivo donde el profesor pega la URL de Moodle del examen; la herramienta la transforma automáticamente al protocolo seguro `sebs://` y genera un QR de alto contraste optimizado para pantalla gigante o proyector de aula.

---

## 🚀 Despliegue en 1 Clic (`deploy_to_web.sh`)

Para vincular tu repositorio y lanzar la primera compilación en la nube:

```bash
chmod +x deploy_to_web.sh
./deploy_to_web.sh https://github.com/jmgurpegui/SEB.git
```

El script:
1. Inicializa Git y configura la rama `main`.
2. Asocia la URL remota de GitHub.
3. Actualiza automáticamente la constante `APK_DOWNLOAD_URL` en `MainActivity.kt` y la configuración de `docs/index.html`.
4. Realiza el `commit` y `push` activando el flujo de GitHub Actions.
5. Imprime en pantalla las URLs de descarga directa, acciones y portal de GitHub Pages.

> **Nota:** En tu repositorio de GitHub, asegúrate de activar GitHub Pages en:  
> **Settings -> Pages -> Build and deployment -> Source: GitHub Actions**.

---

## 🏫 Configuración en Moodle (Aula Virtual EducaMadrid)

1. En el cuestionario de Moodle, ir a **Editar ajustes** -> **Safe Exam Browser**.
2. En *Requerir el uso de Safe Exam Browser*, seleccionar: **Sí - Configurar manualmente** o **Sí - Usar configuración de SEB existente**.
3. En la sección **Claves de examen de Safe Exam Browser (Browser Exam Key)**, añade la clave de 64 caracteres configurada en `SebCryptoUtils.kt`.
4. Los alumnos podrán abrir el enlace directo o escanear el enlace con protocolo `sebs://aulavirtual33.educamadrid.org/...` para iniciar el examen en modo bloqueado.
