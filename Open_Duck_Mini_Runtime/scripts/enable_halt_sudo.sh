#!/usr/bin/env bash
# Droit unique : bdxv2 peut éteindre le Pi sans mot de passe (D-021).
# À lancer UNE FOIS en SSH, en tant que bdxv2. Hors pi-setup/install.sh (D-020).
set -euo pipefail

if [[ "$(id -un)" != "bdxv2" ]]; then
  echo "À lancer en tant que bdxv2 (pas root)."
  exit 1
fi

ROOT="$(cd "$(dirname "$0")" && pwd)"
SRC="${ROOT}/bdx-poweroff.sh"
if [[ ! -f "${SRC}" ]]; then
  echo "Manque ${SRC}"
  exit 1
fi

sudo install -m 755 "${SRC}" /usr/local/bin/bdx-poweroff

TMP="$(mktemp)"
trap 'rm -f "${TMP}"' EXIT
cat > "${TMP}" <<'EOF'
bdxv2 ALL=(root) NOPASSWD: /usr/local/bin/bdx-poweroff
EOF

sudo visudo -cf "${TMP}"
sudo install -m 440 "${TMP}" /etc/sudoers.d/bdx-halt
sudo visudo -cf /etc/sudoers.d/bdx-halt
echo "OK : sudo -n /usr/local/bin/bdx-poweroff est autorisé pour bdxv2."
echo "Filet SSH toujours valable : sudo poweroff"
