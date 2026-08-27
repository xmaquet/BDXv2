#!/usr/bin/env bash
#
# BDXv2 — installation post-OS sur Raspberry Pi (D-012, D-018, D-019).
# Canon : pi-setup/install.sh
#
#   bash ~/BDXv2/pi-setup/install.sh
#   curl -fsSL https://raw.githubusercontent.com/xmaquet/BDXv2/main/pi-setup/install.sh | bash
#
# Variables optionnelles :
#   REPO_URL       défaut https://github.com/xmaquet/BDXv2.git
#   REPO_DIR       défaut $HOME/BDXv2 (ou racine Git détectée)
#   BRANCH         défaut main
#   GIT_SHALLOW    1 (défaut)
#   SKIP_GIT_PULL  1 = jamais de pull
#   PIP_EXTRAS     défaut ble,hardware  (pas control/Xbox, pas rl)
#   I2S_REBOOT     1 = reboot si overlay I2S vient d'être écrit
#
set -euo pipefail

info() { echo "[$(date -Iseconds)] [INFO]  $*"; }
warn() { echo "[$(date -Iseconds)] [WARN]  $*" >&2; }
error() { echo "[$(date -Iseconds)] [ERROR] $*" >&2; exit 1; }

REPO_URL="${REPO_URL:-https://github.com/xmaquet/BDXv2.git}"
BRANCH="${BRANCH:-main}"
GIT_SHALLOW="${GIT_SHALLOW:-1}"
SKIP_GIT_PULL="${SKIP_GIT_PULL:-0}"
PIP_EXTRAS="${PIP_EXTRAS:-ble,hardware}"
RUNTIME_REL="Open_Duck_Mini_Runtime"

APT_UPDATE_DONE=0
_apt_update_once() {
  if [[ "${APT_UPDATE_DONE}" -eq 0 ]]; then
    info "[apt] update…"
    sudo apt-get update -y
    APT_UPDATE_DONE=1
  fi
}

_is_runtime_tree() {
  [[ -f "${1}/setup.cfg" && -d "${1}/mini_bdx_runtime" ]]
}

_have_local_tree() {
  local src="${BASH_SOURCE[0]:-}"
  [[ -n "${src}" && -f "${src}" ]] || return 1
  local here
  here="$(cd "$(dirname "${src}")" && pwd)"
  [[ -d "${here}/../Open_Duck_Mini_Runtime/mini_bdx_runtime" ]]
}

# ---------------------------------------------------------------------------
# Bootstrap si le script arrive par curl | bash
# ---------------------------------------------------------------------------
if [[ "${BDX_INSTALL_INNER:-0}" != "1" ]] && ! _have_local_tree; then
  info "=== BDXv2 pi-setup (bootstrap) ==="
  if [[ "$(uname -s)" != "Linux" ]]; then
    error "Bootstrap prévu sur le Raspberry Pi (Linux)."
  fi
  command -v sudo >/dev/null 2>&1 || [[ "${EUID:-0}" -eq 0 ]] || error "sudo requis."
  REPO_DIR="${REPO_DIR:-${HOME}/BDXv2}"
  if ! command -v git >/dev/null 2>&1; then
    _apt_update_once
    sudo apt-get install -y git ca-certificates
  fi
  if [[ ! -d "${REPO_DIR}/.git" ]]; then
    info "[git] clone sparse → ${REPO_DIR}"
    mkdir -p "$(dirname "${REPO_DIR}")"
    CLONE_ARGS=(--single-branch -b "${BRANCH}" --filter=blob:none --sparse)
    if [[ "${GIT_SHALLOW}" == "1" ]]; then
      CLONE_ARGS+=(--depth 1)
    fi
    git clone "${CLONE_ARGS[@]}" "${REPO_URL}" "${REPO_DIR}"
    git -C "${REPO_DIR}" sparse-checkout set "${RUNTIME_REL}" pi-setup
  else
    info "[git] dépôt déjà présent : ${REPO_DIR}"
  fi
  export BDX_INSTALL_INNER=1
  if [[ ! -f "${REPO_DIR}/pi-setup/install.sh" ]]; then
    error "pi-setup/install.sh absent dans ${REPO_DIR}. Il faut un git pull de main une fois pi-setup poussé, ou un clone neuf."
  fi
  exec bash "${REPO_DIR}/pi-setup/install.sh"
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [[ -n "${REPO_DIR:-}" ]]; then
  :
elif git -C "${SCRIPT_DIR}" rev-parse --show-toplevel >/dev/null 2>&1; then
  REPO_DIR="$(git -C "${SCRIPT_DIR}" rev-parse --show-toplevel)"
else
  REPO_DIR="${HOME}/BDXv2"
fi

info "=== BDXv2 pi-setup — installation post-OS ==="
info "REPO_DIR=${REPO_DIR}"

if [[ "$(uname -s)" != "Linux" ]]; then
  warn "OS non-Linux : poursuite à tes risques."
fi
command -v apt-get >/dev/null 2>&1 || error "apt-get introuvable."
command -v sudo >/dev/null 2>&1 || [[ "${EUID:-0}" -eq 0 ]] || error "sudo requis."

# ---------------------------------------------------------------------------
# Git (idempotent)
# ---------------------------------------------------------------------------
if ! command -v git >/dev/null 2>&1; then
  _apt_update_once
  sudo apt-get install -y git ca-certificates
fi

if [[ ! -d "${REPO_DIR}/.git" ]]; then
  info "[git] clone sparse → ${REPO_DIR}"
  mkdir -p "$(dirname "${REPO_DIR}")"
  CLONE_ARGS=(--single-branch -b "${BRANCH}" --filter=blob:none --sparse)
  if [[ "${GIT_SHALLOW}" == "1" ]]; then
    CLONE_ARGS+=(--depth 1)
  fi
  git clone "${CLONE_ARGS[@]}" "${REPO_URL}" "${REPO_DIR}"
  git -C "${REPO_DIR}" sparse-checkout set "${RUNTIME_REL}" pi-setup
else
  info "[git] dépôt existant — pas de re-clone"
  # Ne jamais convertir un clone complet en sparse (le Pi a déjà un checkout plein).
  sparse_on="$(git -C "${REPO_DIR}" config --get core.sparseCheckout 2>/dev/null || true)"
  if [[ "${sparse_on}" == "true" ]]; then
    if ! git -C "${REPO_DIR}" sparse-checkout list 2>/dev/null | grep -qE '^pi-setup/?$'; then
      info "[git] sparse déjà actif — ajout pi-setup + ${RUNTIME_REL}"
      git -C "${REPO_DIR}" sparse-checkout set "${RUNTIME_REL}" pi-setup || warn "[git] sparse-checkout set a échoué."
    fi
  else
    info "[git] checkout complet — sparse non forcé (D-017 : docs inutiles sur le Pi, mais on n'élague pas un clone existant)."
  fi
  git -C "${REPO_DIR}" fetch --all --prune || warn "[git] fetch a échoué."
  if [[ "${SKIP_GIT_PULL}" == "1" ]]; then
    info "[git] SKIP_GIT_PULL=1"
  elif [[ -n "$(git -C "${REPO_DIR}" status --porcelain 2>/dev/null)" ]]; then
    warn "[git] working tree non propre — pas de pull."
  else
    git -C "${REPO_DIR}" checkout "${BRANCH}" 2>/dev/null || true
    if git -C "${REPO_DIR}" rev-parse --abbrev-ref '@{u}' >/dev/null 2>&1; then
      git -C "${REPO_DIR}" pull --ff-only || warn "[git] pull --ff-only a échoué."
    fi
  fi
fi

[[ -d "${REPO_DIR}/.git" ]] || error "Pas de dépôt Git dans ${REPO_DIR}."

if _is_runtime_tree "${REPO_DIR}/${RUNTIME_REL}"; then
  RUNTIME_DIR="${REPO_DIR}/${RUNTIME_REL}"
elif _is_runtime_tree "${REPO_DIR}"; then
  RUNTIME_DIR="${REPO_DIR}"
  warn "[git] arbre runtime à la racine (ancien dépôt autonome)."
else
  error "Runtime introuvable (${REPO_DIR}/${RUNTIME_REL})."
fi
info "Runtime : ${RUNTIME_DIR}"

# ---------------------------------------------------------------------------
# Paquets (idempotent : apt réinstalle sans casser)
# ---------------------------------------------------------------------------
info "[apt] paquets système (BLE, GPIO, audio apt, pas Xbox)"
_apt_update_once
sudo apt-get install -y \
  git ca-certificates \
  pkg-config bluez \
  python3 python3-venv python3-dev swig \
  python3-numpy python3-scipy python3-pygame python3-opencv \
  python3-rpi-lgpio \
  libsdl2-dev libsdl2-image-dev libsdl2-mixer-dev libsdl2-ttf-dev \
  libfreetype6-dev libportmidi-dev libjpeg-dev libpng-dev

command -v python3 >/dev/null 2>&1 || error "python3 introuvable."
PY_VER="$(python3 -c 'import sys; print(f"{sys.version_info.major}.{sys.version_info.minor}.{sys.version_info.micro}")')"
info "Python : ${PY_VER}"
python3 -c 'import sys; sys.exit(0 if (3, 11) <= sys.version_info < (3, 14) else 1)' \
  || error "Python 3.11 à 3.13 requis (setup.cfg)."

# ---------------------------------------------------------------------------
# I2C, udev, Bluetooth, I2S
# ---------------------------------------------------------------------------
INSTALL_USER="${SUDO_USER:-${USER}}"
if [[ "${INSTALL_USER}" == "root" ]]; then
  warn "[user] root — groupes non modifiés."
  INSTALL_USER=""
fi

if [[ -r /proc/device-tree/model ]] && grep -qi "Raspberry Pi" /proc/device-tree/model; then
  if command -v raspi-config >/dev/null 2>&1; then
    info "[i2c] raspi-config nonint do_i2c 0"
    sudo raspi-config nonint do_i2c 0
  else
    warn "[i2c] raspi-config absent."
  fi
  I2S_SCRIPT="${SCRIPT_DIR}/enable_i2s_max98357.sh"
  if [[ -f "${I2S_SCRIPT}" ]]; then
    info "[i2s] overlay MAX98357 (idempotent, reboot seulement si I2S_REBOOT=1)"
    sudo env I2S_REBOOT="${I2S_REBOOT:-0}" bash "${I2S_SCRIPT}" || warn "[i2s] script a échoué."
  fi
else
  warn "[i2c/i2s] pas un Raspberry Pi — étapes GPIO boot ignorées."
fi

UDEV_FILE="/etc/udev/rules.d/99-usb-serial.rules"
UDEV_RULE='SUBSYSTEM=="usb-serial", DRIVER=="ftdi_sio", ATTR{latency_timer}="1"'
if [[ -f "${UDEV_FILE}" ]] && grep -qF 'ATTR{latency_timer}="1"' "${UDEV_FILE}"; then
  info "[udev] règle FTDI déjà présente."
else
  info "[udev] écriture ${UDEV_FILE}"
  echo "${UDEV_RULE}" | sudo tee "${UDEV_FILE}" >/dev/null
  if command -v udevadm >/dev/null 2>&1; then
    sudo udevadm control --reload-rules || true
    sudo udevadm trigger || true
  fi
fi

if command -v systemctl >/dev/null 2>&1; then
  sudo systemctl enable bluetooth >/dev/null 2>&1 || warn "[bluetooth] enable a échoué."
  sudo systemctl start bluetooth >/dev/null 2>&1 || warn "[bluetooth] start a échoué."
fi

if [[ -n "${INSTALL_USER}" ]]; then
  for grp in bluetooth i2c gpio; do
    if getent group "${grp}" >/dev/null 2>&1; then
      sudo usermod -aG "${grp}" "${INSTALL_USER}"
      info "[group] ${INSTALL_USER} → ${grp}"
    else
      warn "[group] ${grp} absent."
    fi
  done
  warn "[group] nouveaux groupes : déconnexion SSH ou reboot."
fi

# ---------------------------------------------------------------------------
# duck_config (non destructif)
# ---------------------------------------------------------------------------
EXAMPLE_CFG="${RUNTIME_DIR}/example_config.json"
USER_CFG="${HOME}/duck_config.json"
if [[ -f "${EXAMPLE_CFG}" ]]; then
  if [[ -f "${USER_CFG}" ]]; then
    info "[config] ${USER_CFG} existe — conservé."
  else
    cp "${EXAMPLE_CFG}" "${USER_CFG}"
    info "[config] créé ${USER_CFG}"
  fi
fi

# ---------------------------------------------------------------------------
# venv + pip
# ---------------------------------------------------------------------------
mkdir -p "${HOME}/tmp"
export TMPDIR="${HOME}/tmp"

if [[ ! -d "${RUNTIME_DIR}/.venv" ]]; then
  info "[venv] création --system-site-packages"
  python3 -m venv "${RUNTIME_DIR}/.venv" --system-site-packages
elif ! grep -q '^include-system-site-packages = true' "${RUNTIME_DIR}/.venv/pyvenv.cfg" 2>/dev/null; then
  warn "[venv] system-site-packages absent — recréation (pygame apt sinon invisible)"
  rm -rf "${RUNTIME_DIR}/.venv"
  python3 -m venv "${RUNTIME_DIR}/.venv" --system-site-packages
else
  info "[venv] réutilisation ${RUNTIME_DIR}/.venv"
fi

# shellcheck source=/dev/null
source "${RUNTIME_DIR}/.venv/bin/activate"
python -m pip install --upgrade pip setuptools wheel

pushd "${RUNTIME_DIR}" >/dev/null

if [[ -n "${PIP_EXTRAS}" ]]; then
  info "[pip] editable extras=${PIP_EXTRAS} (pas control, pas rl)"
  pip install --no-cache-dir -e ".[${PIP_EXTRAS}]"
else
  pip install --no-cache-dir -e .
fi

# Blinka pour GPIO expression (yeux / projecteur) — sans compiler RPi.GPIO
if python -c "import board, digitalio" >/dev/null 2>&1; then
  info "[blinka] import board OK — skip."
else
  info "[blinka] install --no-deps (shim apt python3-rpi-lgpio)"
  pip install --no-cache-dir --no-deps adafruit-blinka Adafruit-PlatformDetect Adafruit-PureIO
fi

if [[ -r /proc/device-tree/model ]] && grep -qi "Raspberry Pi 5" /proc/device-tree/model; then
  pip uninstall -y RPi.GPIO || true
  pip install lgpio
fi

info "[check] imports…"
post_ok=1
python -c "import numpy" || { warn "numpy"; post_ok=0; }
python -c "import pygame" || { warn "pygame (apt python3-pygame / extra audio)"; post_ok=0; }
if [[ "${PIP_EXTRAS}" == *ble* ]]; then
  python -c "import bluez_peripheral" || { warn "bluez_peripheral"; post_ok=0; }
fi
if [[ "${PIP_EXTRAS}" == *hardware* ]]; then
  python -c "import rustypot" || { warn "rustypot"; post_ok=0; }
fi
python -c "import board, digitalio" || { warn "blinka/board"; post_ok=0; }
if [[ "${post_ok}" -eq 1 ]]; then
  info "[check] OK."
else
  warn "[check] au moins un import a échoué."
fi

popd >/dev/null

info "=== Terminé ==="
info "Monorepo : ${REPO_DIR}"
info "Runtime  : ${RUNTIME_DIR}"
info "venv     : source ${RUNTIME_DIR}/.venv/bin/activate"
info "Lab SSH  : bash ${RUNTIME_DIR}/scripts/run_bdx_lab.sh"
info "BLE      : bdx-ble-robot   (non démarré ici, lot 3)"
info "Xbox     : non installé (D-018)"
info "RL/ONNX  : non installé"
info "Config   : ${USER_CFG}"
