#!/usr/bin/env bash
# Wrapper Wi‑Fi borné (D-023). Installé dans /usr/local/bin/bdx-wifi.
# Appelé uniquement via sudo -n. Mot de passe join : stdin, jamais les logs.
set -euo pipefail
export LANG=C
export LC_ALL=C

wifi_dev() {
  nmcli -t -f DEVICE,TYPE device status 2>/dev/null | awk -F: '$2=="wifi"{print $1; exit}'
}

action="${1:-}"
DEV="$(wifi_dev || true)"
if [[ -z "${DEV}" ]]; then
  echo "wifi : pas d’interface wlan" >&2
  exit 1
fi

case "${action}" in
  status)
    nmcli -t -f GENERAL.CONNECTION,GENERAL.STATE,IP4.ADDRESS device show "${DEV}"
    printf '%s\n' "---"
    nmcli -t -f IN-USE,SSID,SIGNAL,SECURITY,FREQ device wifi list ifname "${DEV}"
    ;;
  scan)
    nmcli device wifi rescan ifname "${DEV}" >/dev/null 2>&1 || true
    sleep 2
    nmcli -t -f IN-USE,SSID,SIGNAL,SECURITY,FREQ device wifi list ifname "${DEV}"
    ;;
  join)
    ssid="${2:-}"
    if [[ -z "${ssid}" ]]; then
      echo "wifi : SSID manquant" >&2
      exit 1
    fi
    psk=""
    IFS= read -r psk || true
    if [[ -z "${psk}" ]]; then
      nmcli device wifi connect "${ssid}" ifname "${DEV}"
    else
      # Residual : nmcli prend le PSK en argument (pas de passwd-file portable).
      nmcli device wifi connect "${ssid}" password "${psk}" ifname "${DEV}"
    fi
    ;;
  *)
    echo "wifi : action inconnue" >&2
    exit 1
    ;;
esac
