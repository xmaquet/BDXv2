#!/usr/bin/env bash
# Minimum local : venv + Blinka sans compiler RPi.GPIO / rpi_ws281x.
# Le shim apt python3-rpi-lgpio suffit pour digitalio (projecteur).
set -euo pipefail

RUNTIME="${HOME}/BDXv2/Open_Duck_Mini_Runtime"
VENV="${RUNTIME}/.venv"
export TMPDIR="${HOME}/tmp"
mkdir -p "${TMPDIR}"

"${VENV}/bin/pip" install --no-cache-dir --no-deps \
  adafruit-blinka \
  Adafruit-PlatformDetect \
  Adafruit-PureIO

export PYTHONPATH="${RUNTIME}/mini_bdx_runtime"
"${VENV}/bin/python" - <<'PY'
import board
import digitalio
from mini_bdx_runtime.projector import Projector

print("board_id", getattr(board, "board_id", "?"))
print("D25", board.D25)
p = Projector()
print("projector_init", p.on)
p.stop()
print("IMPORT_OK")
PY
