#!/usr/bin/env bash
# Wrapper Wi‑Fi borné (D-023). Installé dans /usr/local/bin/bdx-wifi.
# Appelé uniquement via sudo -n. Mot de passe join : stdin, jamais les logs.
set -euo pipefail
export LANG=C
export LC_ALL=C

wifi_dev() {
  nmcli -t -f DEVICE,TYPE device status 2>/dev/null | awk -F: '$2=="wifi"{print $1; exit}'
}

active_ssid() {
  local conn
  conn="$(nmcli -g GENERAL.CONNECTION device show "${DEV}" 2>/dev/null | head -n1 || true)"
  if [[ -z "${conn}" || "${conn}" == "--" ]]; then
    return 0
  fi
  nmcli -g 802-11-wireless.ssid connection show "${conn}" 2>/dev/null || true
}

profile_for_ssid() {
  local want="$1"
  local name typ cur
  while IFS=: read -r name typ; do
    [[ "${typ}" == "802-11-wireless" || "${typ}" == "wifi" ]] || continue
    [[ -z "${name}" ]] && continue
    cur="$(nmcli -g 802-11-wireless.ssid connection show "${name}" 2>/dev/null || true)"
    if [[ "${cur}" == "${want}" ]]; then
      printf '%s\n' "${name}"
      return 0
    fi
  done < <(nmcli -t -f NAME,TYPE connection show)
  return 1
}

emit_active_ssid() {
  printf 'BDX.SSID:%s\n' "$(active_ssid)"
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
    emit_active_ssid
    printf '%s\n' "---"
    nmcli -t -f IN-USE,SSID,SIGNAL,SECURITY,FREQ device wifi list ifname "${DEV}"
    ;;
  scan)
    emit_active_ssid
    printf '%s\n' "---"
    # --rescan yes / rescan sans borne peut bloquer le wrapper. Timeout + courte pause.
    timeout 5 nmcli device wifi rescan ifname "${DEV}" >/dev/null 2>&1 || true
    sleep 2
    nmcli -t -f IN-USE,SSID,SIGNAL,SECURITY,FREQ device wifi list ifname "${DEV}"
    ;;
  prefer)
    ssid="${2:-}"
    if [[ -z "${ssid}" ]]; then
      echo "wifi : SSID manquant" >&2
      exit 1
    fi
    nmcli -t -f NAME,TYPE connection show | awk -F: '$2=="802-11-wireless" || $2=="wifi"{print $1}' | while IFS= read -r name; do
      [[ -z "${name}" ]] && continue
      cur="$(nmcli -g 802-11-wireless.ssid connection show "${name}" 2>/dev/null || true)"
      if [[ "${cur}" == "${ssid}" ]]; then
        nmcli connection modify "${name}" connection.autoconnect yes connection.autoconnect-priority 200
      else
        nmcli connection modify "${name}" connection.autoconnect-priority 0
      fi
    done
    ;;
  join)
    ssid="${2:-}"
    if [[ -z "${ssid}" ]]; then
      echo "wifi : SSID manquant" >&2
      exit 1
    fi
    psk=""
    IFS= read -r psk || true
    current="$(active_ssid)"
    if [[ "${current}" == "${ssid}" ]]; then
      exit 0
    fi
    name="$(profile_for_ssid "${ssid}" || true)"
    if [[ -n "${name}" ]]; then
      # Profil déjà connu : on l’active. On ne réécrit pas le mot de passe
      # (un PSK tapé à tort sur la tablette casserait le profil maison).
      nmcli connection up "${name}"
    elif [[ -z "${psk}" ]]; then
      nmcli device wifi connect "${ssid}" ifname "${DEV}"
    else
      nmcli device wifi connect "${ssid}" password "${psk}" ifname "${DEV}"
    fi
    ;;
  *)
    echo "wifi : action inconnue" >&2
    exit 1
    ;;
esac
