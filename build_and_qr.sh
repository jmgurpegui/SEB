#!/usr/bin/env bash

# ==============================================================================
# EducaMadrid SEB Client - Build & QR Distribution Script
# ==============================================================================
# Compila el cliente seguro Android e inicia un servidor local para distribución
# instantánea mediante código QR en terminal (aula / laboratorio BYOD).
# ==============================================================================

set -e

# Colores para la terminal
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m' # No Color

PORT=8080
BUILD_TYPE="debug"

echo -e "${BLUE}${BOLD}================================================================${NC}"
echo -e "${CYAN}${BOLD}       EducaMadrid SEB Client - Compilación y Despliegue QR      ${NC}"
echo -e "${BLUE}${BOLD}================================================================${NC}"

# 1. Comprobación de Java
echo -e "\n${BOLD}[1/5] Verificando entorno de compilación Java...${NC}"
if ! command -v java >/dev/null 2>&1; then
    echo -e "${RED}[ERROR] No se encontró 'java' en el sistema.${NC}"
    echo -e "Instala OpenJDK 17 o 21 (ej: sudo apt install openjdk-17-jdk)"
    exit 1
fi
JAVA_VERSION=$(java -version 2>&1 | head -n 1)
echo -e "${GREEN}✓ Java detectado:${NC} $JAVA_VERSION"

# 2. Comprobación del Wrapper de Gradle
echo -e "\n${BOLD}[2/5] Verificando Gradle Wrapper...${NC}"
chmod +x ./gradlew 2>/dev/null || true

if [ ! -f "./gradlew" ]; then
    echo -e "${RED}[ERROR] No se encontró el script ./gradlew en el directorio raíz.${NC}"
    exit 1
fi

# 3. Compilación del APK
echo -e "\n${BOLD}[3/5] Iniciando compilación de Android APK (${BUILD_TYPE})...${NC}"
if [ "$BUILD_TYPE" = "release" ]; then
    GRADLE_TASK="assembleRelease"
    APK_SEARCH_PATH="app/build/outputs/apk/release/*.apk"
else
    GRADLE_TASK="assembleDebug"
    APK_SEARCH_PATH="app/build/outputs/apk/debug/*.apk"
fi

echo -e "Ejecutando: ${CYAN}./gradlew $GRADLE_TASK${NC}"

if ./gradlew "$GRADLE_TASK"; then
    echo -e "${GREEN}✓ Compilación completada con éxito.${NC}"
else
    BUILD_EXIT_CODE=$?
    echo -e "${YELLOW}\n[AVISO] La compilación mediante CLI requiere Android SDK (ANDROID_HOME).${NC}"
    echo -e "Si estás trabajando sin SDK local en terminal, abre este proyecto directamente en"
    echo -e "${BOLD}Android Studio${NC} y compila desde el menú: ${CYAN}Build -> Build Bundle(s) / APK(s) -> Build APK(s)${NC}."
    echo -e "\nSi ya dispones de un APK previamente compilado en ${APK_SEARCH_PATH}, continuaremos con la distribución."
fi

# 4. Localización del archivo APK
echo -e "\n${BOLD}[4/5] Localizando archivo APK...${NC}"
APK_FILE=$(ls -t $APK_SEARCH_PATH 2>/dev/null | head -n 1 || true)

if [ -z "$APK_FILE" ] || [ ! -f "$APK_FILE" ]; then
    echo -e "${YELLOW}No se encontró ningún APK generado en la ruta estándar.${NC}"
    echo -e "¿Deseas especificar la ruta a un APK existente? (o pulsa Ctrl+C para salir)"
    read -rp "Ruta al APK: " MANUAL_APK
    if [ -f "$MANUAL_APK" ]; then
        APK_FILE="$MANUAL_APK"
    else
        echo -e "${RED}[ERROR] Archivo no encontrado. Abortando.${NC}"
        exit 1
    fi
fi

APK_FILENAME=$(basename "$APK_FILE")
APK_DIR=$(dirname "$APK_FILE")
echo -e "${GREEN}✓ APK preparado:${NC} ${BOLD}$APK_FILE${NC}"

# 5. Obtención de la IP local para la red del aula
echo -e "\n${BOLD}[5/5] Detectando dirección IP de red local...${NC}"
LOCAL_IP=""
if command -v hostname >/dev/null 2>&1; then
    LOCAL_IP=$(hostname -I 2>/dev/null | awk '{print $1}')
fi

if [ -z "$LOCAL_IP" ]; then
    LOCAL_IP="127.0.0.1"
fi

DOWNLOAD_URL="http://${LOCAL_IP}:${PORT}/${APK_FILENAME}"

echo -e "\n${GREEN}${BOLD}================================================================${NC}"
echo -e "${GREEN}${BOLD}             DESCARGA LISTA PARA ALUMNOS / TESTERS              ${NC}"
echo -e "${GREEN}${BOLD}================================================================${NC}"
echo -e "URL de descarga directa: ${CYAN}${BOLD}${DOWNLOAD_URL}${NC}"
echo -e "Asegúrate de que los dispositivos estén conectados a la misma red WiFi.\n"

# Generación del Código QR en la Terminal
echo -e "${BOLD}Escanea este código QR con la cámara del dispositivo Android:${NC}\n"

if curl -s --connect-timeout 2 "https://qrenco.de/${DOWNLOAD_URL}"; then
    echo ""
elif command -v qrencode >/dev/null 2>&1; then
    qrencode -t UTF8 "$DOWNLOAD_URL"
else
    echo -e "${YELLOW}(No se pudo generar el QR en terminal. Accede manualmente a la URL)${NC}"
fi

echo -e "\n${BLUE}Iniciando servidor HTTP local en el puerto ${PORT}...${NC}"
echo -e "Directorio servido: ${APK_DIR}"
echo -e "${YELLOW}Presiona Ctrl+C cuando los alumnos hayan descargado el APK para detener el servidor.${NC}\n"

cd "$APK_DIR"
if command -v python3 >/dev/null 2>&1; then
    python3 -m http.server "$PORT"
elif command -v python >/dev/null 2>&1; then
    python -m SimpleHTTPServer "$PORT"
else
    echo -e "${RED}[ERROR] Se requiere Python para iniciar el servidor HTTP local.${NC}"
fi
