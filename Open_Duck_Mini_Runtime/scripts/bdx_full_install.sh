#!/usr/bin/env bash
# Héritage : le canon d’install post-OS est pi-setup/install.sh (D-012, D-019).
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" 2>/dev/null && pwd)" || HERE=""
if [[ -n "${REPO:-}" ]]; then
  export REPO_URL="${REPO}"
fi

if [[ -n "${HERE}" && -f "${HERE}/../../pi-setup/install.sh" ]]; then
  exec bash "${HERE}/../../pi-setup/install.sh"
fi
if [[ -n "${HERE}" && -f "${HERE}/../install.sh" ]]; then
  exec bash "${HERE}/../install.sh"
fi

CANON_URL="${CANON_URL:-https://raw.githubusercontent.com/xmaquet/BDXv2/main/pi-setup/install.sh}"
curl -fsSL "${CANON_URL}" | bash
