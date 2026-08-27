#!/usr/bin/env bash
# Active I2S MAX98357A (HP). Idempotent. Pas de service /dev/zero.
# Pas de reboot sauf I2S_REBOOT=1 *et* modification réelle du config.txt.
set -euo pipefail

info() { echo "[i2s] $*"; }
warn() { echo "[i2s] WARN: $*" >&2; }

if [[ "$(id -u)" -ne 0 ]]; then
  echo "À lancer avec sudo." >&2
  exit 1
fi

CFG="/boot/firmware/config.txt"
if [[ ! -f "${CFG}" ]]; then
  CFG="/boot/config.txt"
fi
if [[ ! -f "${CFG}" ]]; then
  echo "config.txt introuvable." >&2
  exit 1
fi

changed=0

_ensure_line() {
  local pattern="$1"
  local line="$2"
  if grep -qE "${pattern}" "${CFG}"; then
    info "déjà présent : ${line}"
    return 0
  fi
  printf '\n%s\n' "${line}" >> "${CFG}"
  changed=1
  info "ajouté : ${line}"
}

if grep -q '^dtparam=audio=on' "${CFG}"; then
  sed -i 's/^dtparam=audio=on/#dtparam=audio=on/' "${CFG}"
  changed=1
  info "dtparam=audio=on commenté (HDMI, consigne Adafruit)"
fi

if grep -q '^#dtparam=i2s=on' "${CFG}"; then
  sed -i 's/^#dtparam=i2s=on/dtparam=i2s=on/' "${CFG}"
  changed=1
  info "dtparam=i2s=on décommenté"
elif ! grep -qE '^dtparam=i2s=on' "${CFG}"; then
  _ensure_line '^dtparam=i2s=on' 'dtparam=i2s=on'
fi

if ! grep -qE '^dtoverlay=max98357a' "${CFG}"; then
  _ensure_line '^dtoverlay=max98357a' 'dtoverlay=max98357a'
fi

if [[ "${changed}" -eq 0 ]]; then
  info "overlay MAX98357 déjà en place — rien à faire."
  exit 0
fi

info "config.txt modifié. Un reboot est nécessaire pour l'I2S."
if [[ "${I2S_REBOOT:-0}" == "1" ]]; then
  info "I2S_REBOOT=1 — reboot dans 3 s."
  sleep 3
  reboot
else
  info "Pas de reboot automatique. Relancer le Pi quand tu peux."
fi
