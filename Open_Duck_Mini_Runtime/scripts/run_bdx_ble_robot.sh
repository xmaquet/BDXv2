#!/usr/bin/env bash
# Lance le serveur GATT (hello au démarrage inclus). SSH ou systemd.
set -euo pipefail
RUNTIME="${HOME}/BDXv2/Open_Duck_Mini_Runtime"
# shellcheck source=/dev/null
source "${RUNTIME}/.venv/bin/activate"
export PYTHONPATH="${RUNTIME}/mini_bdx_runtime"
export SDL_VIDEODRIVER="${SDL_VIDEODRIVER:-dummy}"
export SDL_AUDIODRIVER="${SDL_AUDIODRIVER:-alsa}"
exec bdx-ble-robot "$@"
