#!/usr/bin/env bash
# Héritage : le canon d’install post-OS est pi-setup/install.sh (D-012, D-019).
set -euo pipefail

_canon_from() {
  local here="$1"
  if [[ -n "${here}" && -f "${here}/../pi-setup/install.sh" ]]; then
    echo "${here}/../pi-setup/install.sh"
    return 0
  fi
  return 1
}

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" 2>/dev/null && pwd)" || HERE=""
if [[ -n "${REPO:-}" ]]; then
  export REPO_URL="${REPO}"
fi

if CANON="$(_canon_from "${HERE}")"; then
  exec bash "${CANON}"
fi

CANON_URL="${CANON_URL:-https://raw.githubusercontent.com/xmaquet/BDXv2/main/pi-setup/install.sh}"
curl -fsSL "${CANON_URL}" | bash
