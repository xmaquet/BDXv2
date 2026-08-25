#!/usr/bin/env bash
set -euo pipefail
RUNTIME="${HOME}/BDXv2/Open_Duck_Mini_Runtime"
# shellcheck source=/dev/null
source "${RUNTIME}/.venv/bin/activate"
export PYTHONPATH="${RUNTIME}/mini_bdx_runtime"
# SSH sans écran : pygame ne doit pas chercher un display.
export SDL_VIDEODRIVER="${SDL_VIDEODRIVER:-dummy}"
export SDL_AUDIODRIVER="${SDL_AUDIODRIVER:-alsa}"
exec python "${RUNTIME}/scripts/bdx_expression_test_menu.py"
