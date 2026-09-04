#!/bin/bash
# Wrapper halt D-021 : un seul binaire NOPASSWD (LF obligatoire).
set -euo pipefail
if [[ "${1:-}" == "--check" ]]; then
  exit 0
fi
exec /bin/systemctl poweroff
