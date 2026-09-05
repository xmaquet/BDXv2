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

refresh_scan() {
  # --rescan yes / rescan sans borne peut bloquer. Timeout + courte pause.
  timeout 5 nmcli device wifi rescan ifname "${DEV}" >/dev/null 2>&1 || true
  sleep 2
}

# Meilleur BSSID 2,4 GHz pour un SSID (sortie terse nmcli, champs échappés).
best_bssid_24() {
  local want="$1"
  local line c cur
  local -a fields
  local best_bssid="" best_sig=-1
  local ssid bssid signal freq digits
  while IFS= read -r line; do
    [[ -z "${line}" ]] && continue
    fields=()
    cur=""
    local i=0
    while (( i < ${#line} )); do
      c="${line:i:1}"
      if [[ "${c}" == '\' && $((i + 1)) -lt ${#line} ]]; then
        cur+="${line:i+1:1}"
        i=$((i + 2))
      elif [[ "${c}" == ':' ]]; then
        fields+=("${cur}")
        cur=""
        i=$((i + 1))
      else
        cur+="${c}"
        i=$((i + 1))
      fi
    done
    fields+=("${cur}")
    (( ${#fields[@]} >= 4 )) || continue
    ssid="${fields[0]}"
    bssid="${fields[1]}"
    signal="${fields[2]}"
    freq="${fields[3]}"
    [[ "${ssid}" == "${want}" ]] || continue
    digits="${freq%%[^0-9]*}"
    [[ -n "${digits}" && "${digits}" -lt 3000 ]] || continue
    [[ "${signal}" =~ ^[0-9]+$ ]] || signal=0
    if (( signal > best_sig )); then
      best_sig="${signal}"
      best_bssid="${bssid}"
    fi
  done < <(nmcli -t -e yes -f SSID,BSSID,SIGNAL,FREQ device wifi list ifname "${DEV}" 2>/dev/null || true)
  [[ -n "${best_bssid}" ]] || return 1
  printf '%s\n' "${best_bssid}"
}

connect_ssid() {
  local ssid="$1" psk="$2" bssid="${3:-}"
  local args=(device wifi connect "${ssid}" ifname "${DEV}")
  if [[ -n "${psk}" ]]; then
    args+=(password "${psk}")
  fi
  if [[ -n "${bssid}" ]]; then
    args+=(bssid "${bssid}")
  fi
  nmcli "${args[@]}"
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
    refresh_scan
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
    # Scan tablette ≠ cache NM au join : sans rescan, nmcli répond
    # « No network with SSID found » alors que la liste vient de l’afficher.
    refresh_scan
    name="$(profile_for_ssid "${ssid}" || true)"
    if [[ -n "${name}" ]]; then
      # Profil déjà connu : on l’active. On ne réécrit pas le mot de passe
      # (un PSK tapé à tort sur la tablette casserait le profil maison).
      # BSSID / bande figés (mesh, 5 GHz) : le 2,4 GHz est visible au scan
      # mais `connection up` ne trouve aucun BSS compatible.
      nmcli connection modify "${name}" \
        802-11-wireless.bssid '' \
        802-11-wireless.band bg \
        connection.interface-name "${DEV}" >/dev/null 2>&1 || true
      if nmcli connection up "${name}"; then
        exit 0
      fi
    fi
    bssid="$(best_bssid_24 "${ssid}" || true)"
    connect_ssid "${ssid}" "${psk}" "${bssid}"
    ;;
  *)
    echo "wifi : action inconnue" >&2
    exit 1
    ;;
esac
