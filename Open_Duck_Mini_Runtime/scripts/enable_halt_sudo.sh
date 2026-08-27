#!/usr/bin/env bash
# Droit unique : bdxv2 peut poweroff sans mot de passe (D-021).
# À lancer UNE FOIS en SSH, en tant que bdxv2. Hors pi-setup/install.sh (D-020).
set -euo pipefail

if [[ "$(id -un)" != "bdxv2" ]]; then
  echo "À lancer en tant que bdxv2 (pas root)."
  exit 1
fi

TMP="$(mktemp)"
trap 'rm -f "${TMP}"' EXIT
cat > "${TMP}" <<'EOF'
bdxv2 ALL=(root) NOPASSWD: /sbin/poweroff, /usr/sbin/poweroff
EOF

sudo visudo -cf "${TMP}"
sudo install -m 440 "${TMP}" /etc/sudoers.d/bdx-halt
sudo visudo -cf /etc/sudoers.d/bdx-halt
echo "OK : sudo -n /sbin/poweroff est autorisé pour bdxv2."
echo "Filet SSH toujours valable : sudo poweroff"
