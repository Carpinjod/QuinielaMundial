#!/bin/bash
set -e

# ──────────────────────────────────────────────
#  Quiniela Mundial 2026 — Setup para Hetzner VPS
# ──────────────────────────────────────────────
#  Uso:
#    1. Crear VPS en Hetzner (Ubuntu 24.04)
#    2. Subir el JAR: scp target/quinielamundial-1.0.0-SNAPSHOT.jar root@<IP>:/opt/quiniela/
#    3. Ejecutar este script: bash setup.sh
# ──────────────────────────────────────────────

JAR_NAME="quinielamundial-1.0.0-SNAPSHOT.jar"
APP_DIR="/opt/quiniela"
DATA_DIR="$APP_DIR/data"
SERVICE_USER="quiniela"

echo ""
echo "=============================="
echo "  Quiniela Mundial — Setup"
echo "=============================="
echo ""

# ── IP del servidor (para HTTPS automático) ──
read -rp "IP pública del servidor (déjala vacía si tienes dominio): " SERVER_IP
read -rp "Dominio (opcional, ej: quiniela.midominio.com): " SERVER_DOMAIN

if [ -n "$SERVER_DOMAIN" ]; then
  CADDY_DOMAIN="$SERVER_DOMAIN"
elif [ -n "$SERVER_IP" ]; then
  CADDY_DOMAIN="${SERVER_IP}.nip.io"
else
  CADDY_DOMAIN="localhost"
fi

echo ""
echo "  Usando dominio: $CADDY_DOMAIN"
echo ""

# ── 1. Instalar dependencias ──
echo "[1/5] Instalando Java 17..."
apt-get update -qq
apt-get install -y -qq openjdk-17-jre curl

# ── 2. Crear usuario y directorios ──
echo "[2/5] Creando usuario y directorios..."
id -u $SERVICE_USER &>/dev/null || useradd -r -s /bin/false -m -d $APP_DIR $SERVICE_USER
mkdir -p "$DATA_DIR"

# ── 3. Instalar Caddy ──
echo "[3/5] Instalando Caddy (HTTPS automático)..."
if ! command -v caddy &>/dev/null; then
  apt-get install -y -qq debian-keyring debian-archive-keyring
  curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' | gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
  curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' > /etc/apt/sources.list.d/caddy-stable.list
  apt-get update -qq
  apt-get install -y -qq caddy
fi

# ── 4. Configurar Caddy ──
echo "[4/5] Configurando Caddy..."
cat > /etc/caddy/Caddyfile <<CADDYEOF
$CADDY_DOMAIN {
    reverse_proxy localhost:8080
}
CADDYEOF

# ── 5. Configurar servicio systemd ──
echo "[5/5] Configurando servicio systemd..."
cat > /etc/systemd/system/quiniela.service <<SERVICEOF
[Unit]
Description=Quiniela Mundial 2026
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=$SERVICE_USER
Group=$SERVICE_USER
WorkingDirectory=$APP_DIR
Environment="DATA_DIR=$DATA_DIR"
Environment="PORT=8080"
# ── Email notifications (opcional) ──
# Environment="SMTP_HOST=smtp.gmail.com"
# Environment="SMTP_PORT=587"
# Environment="SMTP_USER="
# Environment="SMTP_PASS="
# Environment="SMTP_FROM="
# Environment="PUBLIC_URL=https://$CADDY_DOMAIN"
ExecStart=/usr/bin/java -jar $APP_DIR/$JAR_NAME
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
SERVICEOF

systemctl daemon-reload

# ── Permisos ──
chown -R $SERVICE_USER:$SERVICE_USER "$APP_DIR"

echo ""
echo "=============================="
echo "  ✅ Setup completado"
echo "=============================="
echo ""
echo "  Ahora copia el JAR al servidor:"
echo ""
echo "    cd QuinielaMundial"
echo "    mvn package"
echo "    scp target/$JAR_NAME root@<IP>:$APP_DIR/"
echo ""
echo "  Luego inicia los servicios:"
echo ""
echo "    sudo systemctl enable --now quiniela"
echo "    sudo systemctl restart caddy"
echo ""
echo "  Tu app estará en:"
echo "    https://$CADDY_DOMAIN"
echo ""
echo "  Para ver los logs:"
echo "    sudo journalctl -u quiniela -f"
echo ""
echo "=============================="
