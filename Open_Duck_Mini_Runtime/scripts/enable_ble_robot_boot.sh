#!/usr/bin/env bash
# Autostart GATT + hello à l’allumage du Pi (D-008).
# À lancer UNE FOIS en SSH, en tant que bdxv2. Hors pi-setup/install.sh (D-020).
set -euo pipefail

if [[ "$(id -un)" != "bdxv2" ]]; then
  echo "À lancer en tant que bdxv2 (pas root)."
  exit 1
fi

RUNTIME="${HOME}/BDXv2/Open_Duck_Mini_Runtime"
START="${RUNTIME}/scripts/run_bdx_ble_robot.sh"
if [[ ! -x "${START}" ]]; then
  chmod +x "${START}" || true
fi
if [[ ! -f "${RUNTIME}/.venv/bin/bdx-ble-robot" ]]; then
  echo "bdx-ble-robot introuvable dans le venv. D’abord : source .venv && pip install -e \".[ble]\""
  exit 1
fi

UNIT="$(mktemp)"
trap 'rm -f "${UNIT}"' EXIT
cat > "${UNIT}" <<EOF
[Unit]
Description=BDXv2 BLE GATT (tablette)
After=bluetooth.service
Wants=bluetooth.service

[Service]
Type=simple
User=bdxv2
Group=bdxv2
Environment=HOME=/home/bdxv2
WorkingDirectory=${RUNTIME}
ExecStart=${START}
Restart=on-failure
RestartSec=3

[Install]
WantedBy=multi-user.target
EOF

sudo install -m 644 "${UNIT}" /etc/systemd/system/bdx-ble-robot.service
sudo systemctl daemon-reload
sudo systemctl enable bdx-ble-robot.service
sudo systemctl restart bdx-ble-robot.service
echo "OK : bdx-ble-robot démarre au boot. Logs : journalctl -u bdx-ble-robot -f"
echo "Ne pas lancer un second bdx-ble-robot en SSH tant que ce service tourne."
