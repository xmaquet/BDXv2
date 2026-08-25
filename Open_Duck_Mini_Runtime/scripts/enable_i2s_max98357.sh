#!/usr/bin/env bash
# Active I2S MAX98357A pour le HP (README runtime + Adafruit).
# Ne pose PAS le service /dev/zero (consigne projet).
# À lancer : sudo bash ~/BDXv2/Open_Duck_Mini_Runtime/scripts/enable_i2s_max98357.sh
set -euo pipefail

if [[ "$(id -u)" -ne 0 ]]; then
  echo "À lancer avec sudo."
  exit 1
fi

CFG="/boot/firmware/config.txt"
if [[ ! -f "${CFG}" ]]; then
  CFG="/boot/config.txt"
fi
if [[ ! -f "${CFG}" ]]; then
  echo "config.txt introuvable."
  exit 1
fi

BACKUP="${CFG}.bak-i2s-$(date +%Y%m%d%H%M%S)"
cp -a "${CFG}" "${BACKUP}"
echo "Sauvegarde : ${BACKUP}"

# HDMI / audio Broadcom : Adafruit demande de le désactiver pour l'I2S.
if grep -q '^dtparam=audio=on' "${CFG}"; then
  sed -i 's/^dtparam=audio=on/#dtparam=audio=on/' "${CFG}"
fi

if grep -q '^#dtparam=i2s=on' "${CFG}"; then
  sed -i 's/^#dtparam=i2s=on/dtparam=i2s=on/' "${CFG}"
elif ! grep -qE '^dtparam=i2s=on' "${CFG}"; then
  printf '\ndtparam=i2s=on\n' >> "${CFG}"
fi

if ! grep -qE '^dtoverlay=max98357a' "${CFG}"; then
  printf 'dtoverlay=max98357a\n' >> "${CFG}"
fi

echo "=== lignes audio / I2S ==="
grep -E 'dtparam=audio|dtparam=i2s|dtoverlay=max98357' "${CFG}" || true
echo
echo "Reboot dans 3 secondes (Ctrl+C pour annuler)…"
sleep 3
reboot
