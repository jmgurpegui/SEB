#!/usr/bin/env bash

# ==============================================================================
# EducaMadrid SEB Client - Cloud Deployment Script (GitHub Pages & Releases)
# ==============================================================================
# Sustituye la necesidad de servidores locales mediante compilación automatizada
# en la nube (GitHub Actions) y publicación web estática (GitHub Pages).
# ==============================================================================

set -e

# Paleta de colores para la consola
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
MAGENTA='\033[0;35m'
BOLD='\033[1m'
NC='\033[0m' # No Color

MAIN_ACTIVITY_PATH="app/src/main/java/es/educamadrid/seb/client/MainActivity.kt"
INDEX_HTML_PATH="docs/index.html"

echo -e "${BLUE}${BOLD}====================================================================${NC}"
echo -e "${CYAN}${BOLD}    EducaMadrid SEB Client - Despliegue Cloud (Pages & Actions)     ${NC}"
echo -e "${BLUE}${BOLD}====================================================================${NC}"

# 1. Comprobar Git
if ! command -v git >/dev/null 2>&1; then
    echo -e "${RED}[ERROR] 'git' no está instalado en este entorno.${NC}"
    exit 1
fi

# 2. Inicializar repositorio si no existe
echo -e "\n${BOLD}[1/5] Inicializando y verificando repositorio Git...${NC}"
if [ ! -d ".git" ]; then
    git init
    echo -e "${GREEN}✓ Repositorio Git inicializado.${NC}"
else
    echo -e "${GREEN}✓ Repositorio Git existente detectado.${NC}"
fi

git branch -M main

# 3. Detectar o solicitar URL remota de GitHub
echo -e "\n${BOLD}[2/5] Configurando repositorio remoto de GitHub...${NC}"
CURRENT_REMOTE=$(git remote get-url origin 2>/dev/null || true)
REMOTE_URL="$1"

if [ -z "$REMOTE_URL" ]; then
    if [ -n "$CURRENT_REMOTE" ]; then
        echo -e "Remoto actual detectado: ${CYAN}${CURRENT_REMOTE}${NC}"
        read -rp "¿Deseas usar este remoto? (S/n): " KEEP_REMOTE
        if [[ "$KEEP_REMOTE" =~ ^[Nn]$ ]]; then
            read -rp "Introduce la nueva URL del repositorio GitHub (ej: https://github.com/usuario/SEB.git): " REMOTE_URL
        else
            REMOTE_URL="$CURRENT_REMOTE"
        fi
    else
        echo -e "${YELLOW}No se ha detectado ningún remoto 'origin'.${NC}"
        read -rp "Introduce la URL del repositorio GitHub (ej: https://github.com/usuario/SEB.git): " REMOTE_URL
    fi
fi

if [ -z "$REMOTE_URL" ]; then
    echo -e "${RED}[ERROR] Se requiere una URL remota de GitHub para continuar.${NC}"
    exit 1
fi

# Asignar o actualizar remoto 'origin'
if [ -n "$CURRENT_REMOTE" ]; then
    git remote set-url origin "$REMOTE_URL"
else
    git remote add origin "$REMOTE_URL"
fi
echo -e "${GREEN}✓ Remoto origin configurado:${NC} ${BOLD}$REMOTE_URL${NC}"

# 4. Extraer usuario y nombre de repositorio
# Soporta https://github.com/USER/REPO(.git) y git@github.com:USER/REPO(.git)
CLEANED_URL=$(echo "$REMOTE_URL" | sed -E 's/\.git$//')
if [[ "$CLEANED_URL" =~ github\.com[:/]([^/]+)/([^/]+) ]]; then
    GITHUB_USER="${BASH_REMATCH[1]}"
    GITHUB_REPO="${BASH_REMATCH[2]}"
else
    echo -e "${YELLOW}[AVISO] No se pudo parsear usuario/repositorio automáticamente de la URL.${NC}"
    read -rp "Introduce el usuario o entidad de GitHub: " GITHUB_USER
    read -rp "Introduce el nombre del repositorio: " GITHUB_REPO
fi

echo -e "Propietario: ${CYAN}${GITHUB_USER}${NC} | Repositorio: ${CYAN}${GITHUB_REPO}${NC}"

RELEASE_APK_URL="https://github.com/${GITHUB_USER}/${GITHUB_REPO}/releases/latest/download/educamadrid-seb.apk"
PAGES_PORTAL_URL="https://${GITHUB_USER}.github.io/${GITHUB_REPO}/"
ACTIONS_URL="https://github.com/${GITHUB_USER}/${GITHUB_REPO}/actions"

# 5. Actualizar la constante APK_DOWNLOAD_URL en MainActivity.kt
echo -e "\n${BOLD}[3/5] Actualizando constante APK_DOWNLOAD_URL en código Android...${NC}"
if [ -f "$MAIN_ACTIVITY_PATH" ]; then
    python3 -c "
import re

path = '$MAIN_ACTIVITY_PATH'
new_url = '$RELEASE_APK_URL'

with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

pattern = r'const val APK_DOWNLOAD_URL:\s*String\s*=\s*\"[^\"]*\"'
replacement = f'const val APK_DOWNLOAD_URL: String =\n            \"{new_url}\"'

if re.search(pattern, content):
    content = re.sub(pattern, replacement, content)
    with open(path, 'w', encoding='utf-8') as f:
        f.write(content)
    print('✓ Constante APK_DOWNLOAD_URL actualizada en MainActivity.kt')
else:
    print('[AVISO] Patrón APK_DOWNLOAD_URL no encontrado en MainActivity.kt')
"
fi

# Actualizar el fallback predeterminado en docs/index.html
if [ -f "$INDEX_HTML_PATH" ]; then
    python3 -c "
import re

path = '$INDEX_HTML_PATH'
user = '$GITHUB_USER'
repo = '$GITHUB_REPO'

with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

pattern = r\"return \{ user: '[^']*', repo: '[^']*'[^}]*\};\"
replacement = f\"return {{ user: '{user}', repo: '{repo}' }};\"

if re.search(pattern, content):
    content = re.sub(pattern, replacement, content)
    with open(path, 'w', encoding='utf-8') as f:
        f.write(content)
    print('✓ Repositorio predeterminado actualizado en docs/index.html')
else:
    print('[AVISO] Patrón de repositorio no encontrado en docs/index.html')
"
fi

# 6. Preparar commit y subir a la nube
echo -e "\n${BOLD}[4/5] Añadiendo cambios y creando commit en Git...${NC}"
git add .
git commit -m "chore(cloud): portal web estatico en docs y pipeline de compilacion automatica en la nube" || {
    echo -e "${YELLOW}No hay cambios nuevos para commitear o ya están registrados.${NC}"
}

echo -e "\n${BOLD}[5/5] Publicando en GitHub (push a main)...${NC}"
echo -e "Ejecutando: ${CYAN}git push -u origin main${NC}"
git push -u origin main

# Resumen Final
echo -e "\n${GREEN}${BOLD}====================================================================${NC}"
echo -e "${GREEN}${BOLD}           ¡DESPLIEGUE CLOUD CONFIGURADO Y LANZADO CON ÉXITO!       ${NC}"
echo -e "${GREEN}${BOLD}====================================================================${NC}"
echo -e "La compilación en la nube y el despliegue web se están ejecutando ahora:"
echo -e "\n1. ${BOLD}Flujo de Compilación (GitHub Actions):${NC}"
echo -e "   ${CYAN}${ACTIONS_URL}${NC}"
echo -e "   -> Compila el APK firmado y lo publica en GitHub Releases."

echo -e "\n2. ${BOLD}Descarga Directa Permanente de Alta Velocidad (Release):${NC}"
echo -e "   ${CYAN}${RELEASE_APK_URL}${NC}"

echo -e "\n3. ${BOLD}Portal Web y Pizarra Docente (GitHub Pages):${NC}"
echo -e "   ${CYAN}${PAGES_PORTAL_URL}${NC}"

echo -e "\n${YELLOW}${BOLD}[IMPORTANTE - CONFIGURACIÓN ÚNICA EN GITHUB]:${NC}"
echo -e "Asegúrate de que GitHub Pages esté activado en tu repositorio:"
echo -e "  1. Entra a: ${BOLD}https://github.com/${GITHUB_USER}/${GITHUB_REPO}/settings/pages${NC}"
echo -e "  2. En ${BOLD}Build and deployment -> Source${NC}, selecciona: ${MAGENTA}${BOLD}GitHub Actions${NC}"
echo -e "====================================================================\n"
