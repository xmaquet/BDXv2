#!/usr/bin/env bash
# Point d'entrée SSH des mini-outils (menu). Pas de docs à lancer ici.
set -euo pipefail
RUNTIME="${HOME}/BDXv2/Open_Duck_Mini_Runtime"
# shellcheck source=/dev/null
source "${RUNTIME}/.venv/bin/activate"
export PYTHONPATH="${RUNTIME}/mini_bdx_runtime"
export SDL_VIDEODRIVER="${SDL_VIDEODRIVER:-dummy}"
export SDL_AUDIODRIVER="${SDL_AUDIODRIVER:-alsa}"
exec python "${RUNTIME}/scripts/bdx_lab_menu.py"
