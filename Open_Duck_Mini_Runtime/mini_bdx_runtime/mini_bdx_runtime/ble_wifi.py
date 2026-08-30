"""Wi‑Fi du Pi via BLE (D-023). Pas un ControllerFrame, pas un test."""

from __future__ import annotations

import json
import os
import subprocess
import threading
from collections import deque
from pathlib import Path
from typing import Any

_WRAPPER = "/usr/local/bin/bdx-wifi"
_SUDOERS_HINT = (
    "wifi : wrapper absent. Sur le Pi : "
    "bash ~/BDXv2/Open_Duck_Mini_Runtime/scripts/enable_wifi_sudo.sh"
)
_MAX_NOTIFY = 140
_MAX_NETS = 20
_SSID_MAX = 32
_DEFAULT_NAME = "bdx_wifi_default.json"


def parse_nmcli_wifi_list(text: str) -> list[dict[str, Any]]:
    """Parse `nmcli -t -f IN-USE,SSID,SIGNAL,SECURITY,FREQ device wifi list`."""
    best: dict[str, dict[str, Any]] = {}
    for raw in text.splitlines():
        line = raw.strip()
        if not line or line.startswith("---"):
            continue
        parts = line.split(":", 4)
        if len(parts) < 5:
            continue
        in_use, ssid, signal_s, security, freq_s = parts
        ssid = ssid.strip()
        if not ssid or ssid == "--":
            continue
        if "802.1X" in security.upper():
            continue
        freq = _parse_freq(freq_s)
        if freq is not None and freq >= 3000:
            continue
        try:
            rssi = int(signal_s)
        except ValueError:
            rssi = 0
        # nmcli SIGNAL is 0–100 quality, not dBm. Keep as displayed strength.
        sec = "open" if not security or security in ("--", "") else "psk"
        row = {
            "ssid": ssid[:_SSID_MAX],
            "rssi": rssi,
            "sec": sec,
            "in_use": in_use.strip() in ("*", "yes", "oui"),
        }
        prev = best.get(ssid)
        if prev is None or row["rssi"] > prev["rssi"]:
            best[ssid] = row
    nets = sorted(best.values(), key=lambda n: n["rssi"], reverse=True)
    return nets[:_MAX_NETS]


def annotate_nets(nets: list[dict[str, Any]], default_ssid: str | None) -> list[dict[str, Any]]:
    """Marque le défaut et trie : défaut, actuel, puis signal."""
    marked: list[dict[str, Any]] = []
    for net in nets:
        row = dict(net)
        row["is_default"] = bool(default_ssid) and row.get("ssid") == default_ssid
        marked.append(row)
    marked.sort(
        key=lambda n: (
            0 if n.get("is_default") else 1,
            0 if n.get("in_use") else 1,
            -int(n.get("rssi") or 0),
        )
    )
    return marked


def parse_nmcli_device_show(text: str) -> dict[str, Any]:
    """Parse `nmcli -t device show wlan0` (sous-ensemble de clés)."""
    conn = None
    state_raw = ""
    ip = None
    ssid = None
    for raw in text.splitlines():
        if ":" not in raw:
            continue
        key, _, val = raw.partition(":")
        key = key.strip()
        val = val.strip()
        if key.startswith("GENERAL.CONNECTION"):
            conn = None if val in ("", "--") else val
        elif key.startswith("GENERAL.STATE"):
            state_raw = val
        elif key.startswith("IP4.ADDRESS") and val:
            ip = val.split("/")[0] or None
        elif key == "BDX.SSID":
            ssid = None if val in ("", "--") else val
    return {"connection": conn, "state_raw": state_raw, "ip": ip, "ssid": ssid}


def apply_current_ssid(nets: list[dict[str, Any]], current: str | None) -> list[dict[str, Any]]:
    """Le SSID actif vient du profil NM, pas du cache in-use du scan."""
    if not current:
        return nets
    for net in nets:
        net["in_use"] = net.get("ssid") == current
    return nets


def map_nm_state(state_raw: str) -> str:
    token = state_raw.split(" ", 1)[0]
    try:
        code = int(token)
    except ValueError:
        low = state_raw.lower()
        if "connected" in low and "disconnect" not in low:
            return "connected"
        if "connecting" in low:
            return "connecting"
        return "disconnected"
    if code >= 100:
        return "connected"
    if 40 <= code < 100:
        return "connecting"
    return "disconnected"


def chunk_scan(nets: list[dict[str, Any]], max_bytes: int = _MAX_NOTIFY) -> list[dict[str, Any]]:
    parts: list[list[dict[str, Any]]] = []
    current: list[dict[str, Any]] = []
    for net in nets:
        trial = current + [net]
        probe = {"type": "wifi_scan", "v": 1, "g": 0, "i": 0, "n": 1, "ts_ms": 0, "nets": trial}
        if current and len(json.dumps(probe, separators=(",", ":")).encode("utf-8")) > max_bytes:
            parts.append(current)
            current = [net]
        else:
            current = trial
    parts.append(current)
    n = max(len(parts), 1)
    return [
        {"type": "wifi_scan", "v": 1, "i": i, "n": n, "nets": part}
        for i, part in enumerate(parts)
    ]


def sanitize_ssid(raw: Any) -> str | None:
    if not isinstance(raw, str):
        return None
    ssid = raw.strip()
    if not ssid or len(ssid) > _SSID_MAX:
        return None
    if any(c in ssid for c in "\n\r\0"):
        return None
    return ssid


def _parse_freq(freq_s: str) -> int | None:
    digits = ""
    for ch in freq_s:
        if ch.isdigit():
            digits += ch
        elif digits:
            break
    if not digits:
        return None
    try:
        return int(digits)
    except ValueError:
        return None


class RobotWifi:
    def __init__(self, runner=None, default_path: str | Path | None = None) -> None:
        self._lock = threading.Lock()
        self._outbox: deque[dict[str, Any]] = deque()
        self._busy = False
        self._run = runner or _run_wrapper
        self._default_path = Path(default_path) if default_path else Path.home() / _DEFAULT_NAME
        self._auto_fail: set[str] = set()
        self._op_lock = threading.Lock()
        self._scan_seq = 0

    def pending(self) -> bool:
        with self._lock:
            return bool(self._outbox)

    def pop(self) -> dict[str, Any] | None:
        with self._lock:
            if self._outbox:
                return self._outbox.popleft()
        return None

    def request(self, obj: dict[str, Any]) -> dict[str, Any]:
        if int(obj.get("v", 0)) != 1:
            return self._ack("status", False, "wifi : version inconnue")
        action = str(obj.get("action", ""))
        if action == "status":
            threading.Thread(target=self._status, daemon=True).start()
            return self._ack("status", True, "État demandé")
        if action == "scan":
            ack = self._ack("scan", True, "Scan demandé")
            threading.Thread(target=self._scan, daemon=True).start()
            return ack
        if action == "join":
            return self._join(obj)
        if action == "set_default":
            return self._set_default(obj)
        return self._ack(action or "unknown", False, "wifi : action inconnue")

    def _push(self, payload: dict[str, Any]) -> None:
        with self._lock:
            self._outbox.append(payload)

    def _ack(self, action: str, accepted: bool, message: str) -> dict[str, Any]:
        state = {
            "type": "wifi_ack",
            "v": 1,
            "action": action,
            "accepted": accepted,
            "message": message,
        }
        self._push(state)
        return state

    def _load_default(self) -> str | None:
        try:
            raw = json.loads(self._default_path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError):
            return None
        if not isinstance(raw, dict):
            return None
        return sanitize_ssid(raw.get("ssid"))

    def _save_default(self, ssid: str | None) -> None:
        self._default_path.parent.mkdir(parents=True, exist_ok=True)
        payload = {"ssid": ssid or ""}
        tmp = self._default_path.with_suffix(".tmp")
        tmp.write_text(json.dumps(payload), encoding="utf-8")
        tmp.replace(self._default_path)

    def _status(self) -> None:
        with self._op_lock:
            self._status_locked()

    def _status_locked(self) -> None:
        default = self._load_default()
        try:
            show, wifi_list = self._run("status")
        except FileNotFoundError:
            self._push(_state(None, "failed", None, None, _SUDOERS_HINT, default))
            return
        except Exception as e:
            self._push(_state(None, "failed", None, None, str(e)[:180], default))
            return
        info = parse_nmcli_device_show(show)
        nets = parse_nmcli_wifi_list(wifi_list)
        current = info.get("ssid") or None
        nets = annotate_nets(apply_current_ssid(nets, current), default)
        in_use = next((n for n in nets if n.get("in_use")), None)
        ssid = current or (in_use or {}).get("ssid") or info.get("connection")
        rssi = (in_use or {}).get("rssi")
        state = map_nm_state(info.get("state_raw") or "")
        self._push(_state(ssid, state, info.get("ip"), rssi, "", default))

    def _scan(self, auto_join: bool = True) -> None:
        default = self._load_default()
        print("[ble_wifi] scan début", flush=True)
        try:
            with self._op_lock:
                head, wifi_list = self._run("scan")
                current = parse_nmcli_device_show(head).get("ssid")
                nets = annotate_nets(apply_current_ssid(parse_nmcli_wifi_list(wifi_list), current), default)
                self._scan_seq += 1
                seq = self._scan_seq
                print(f"[ble_wifi] scan {len(nets)} réseaux g={seq}", flush=True)
                for chunk in chunk_scan(nets):
                    chunk["g"] = seq
                    self._push(chunk)
        except FileNotFoundError:
            self._ack("scan", False, _SUDOERS_HINT)
            return
        except Exception as e:
            self._ack("scan", False, str(e)[:180])
            return
        if auto_join:
            self._maybe_auto_join(nets, current)

    def _maybe_auto_join(self, nets: list[dict[str, Any]], current: str | None = None) -> None:
        default = self._load_default()
        if not default:
            return
        if current == default:
            return
        hit = next((n for n in nets if n.get("ssid") == default), None)
        if hit is None:
            return
        if default in self._auto_fail:
            return
        with self._lock:
            if self._busy:
                return
            self._busy = True
        print(f"[ble_wifi] auto-join défaut {default}", flush=True)
        self._push(
            _state(
                default,
                "connecting",
                None,
                None,
                "Réseau par défaut visible, association…",
                default,
            )
        )
        self._join_run(default, "", True)

    def _set_default(self, obj: dict[str, Any]) -> dict[str, Any]:
        if obj.get("confirm") is not True:
            return self._ack("set_default", False, "wifi : confirmation manquante")
        raw = obj.get("ssid")
        if raw is None:
            raw = ""
        if not isinstance(raw, str):
            return self._ack("set_default", False, "wifi : SSID invalide")
        ssid = raw.strip()
        if ssid:
            clean = sanitize_ssid(ssid)
            if clean is None:
                return self._ack("set_default", False, "wifi : SSID invalide")
            ssid = clean
        else:
            ssid = ""
        try:
            self._save_default(ssid or None)
        except OSError as e:
            return self._ack("set_default", False, str(e)[:180])
        if ssid:
            self._auto_fail.discard(ssid)
            msg = f"Défaut : {ssid}"
        else:
            msg = "Défaut oublié"
        ack = self._ack("set_default", True, msg)
        threading.Thread(target=self._after_set_default, args=(ssid,), daemon=True).start()
        return ack

    def _after_set_default(self, ssid: str) -> None:
        if ssid:
            try:
                self._run("prefer", ssid)
            except FileNotFoundError:
                self._push(_state(ssid, "failed", None, None, _SUDOERS_HINT, ssid))
                return
            except Exception as e:
                self._push(
                    _state(
                        None,
                        "failed",
                        None,
                        None,
                        f"Défaut sauvé, priorité NM : {str(e)[:120]}",
                        ssid,
                    )
                )
        self._status()
        self._scan()

    def _join(self, obj: dict[str, Any]) -> dict[str, Any]:
        if obj.get("confirm") is not True:
            return self._ack("join", False, "wifi : confirmation manquante")
        ssid = sanitize_ssid(obj.get("ssid"))
        if ssid is None:
            return self._ack("join", False, "wifi : SSID invalide")
        psk = obj.get("psk")
        if psk is None:
            psk = ""
        if not isinstance(psk, str):
            return self._ack("join", False, "wifi : mot de passe invalide")
        with self._lock:
            if self._busy:
                return self._ack("join", False, "wifi : association déjà en cours")
            self._busy = True
        ack = self._ack("join", True, "Association demandée")
        self._push(_state(ssid, "connecting", None, None, "Association…", self._load_default()))
        threading.Thread(target=self._join_run, args=(ssid, psk, False), daemon=True).start()
        return ack

    def _join_run(self, ssid: str, psk: str, auto: bool = False) -> None:
        default = self._load_default()
        ok = False
        try:
            self._run("join", ssid, psk)
        except FileNotFoundError:
            if auto:
                self._auto_fail.add(ssid)
            self._push(_state(ssid, "failed", None, None, _SUDOERS_HINT, default))
            self._ack("join", False, _SUDOERS_HINT)
        except Exception as e:
            msg = str(e)[:180]
            if auto:
                self._auto_fail.add(ssid)
                msg = "Réseau par défaut visible, mot de passe requis ou profil inconnu."
            self._push(_state(ssid, "failed", None, None, msg, default))
            self._ack("join", False, msg)
        else:
            self._auto_fail.discard(ssid)
            ok = True
        finally:
            with self._lock:
                self._busy = False
        self._status()
        # Pas de scan auto après join : un notify trop gros bloquait l’UI en « Scan… ».


def _state(
    ssid: str | None,
    state: str,
    ip: str | None,
    rssi: int | None,
    message: str,
    default_ssid: str | None = None,
) -> dict[str, Any]:
    return {
        "type": "wifi_state",
        "v": 1,
        "ssid": ssid,
        "state": state,
        "ip": ip,
        "rssi": rssi,
        "default_ssid": default_ssid,
        "message": message,
    }


def _run_wrapper(action: str, ssid: str = "", psk: str = "") -> tuple[str, str]:
    if not os.path.isfile(_WRAPPER):
        raise FileNotFoundError(_WRAPPER)
    sudo = "/usr/bin/sudo"
    if action == "join":
        result = subprocess.run(
            [sudo, "-n", _WRAPPER, "join", ssid],
            input=psk,
            capture_output=True,
            text=True,
            timeout=45,
        )
        if result.returncode != 0:
            err = (result.stderr or result.stdout or "join échoué").strip()
            raise RuntimeError(err[:180] or _SUDOERS_HINT)
        return result.stdout, ""
    if action == "prefer":
        result = subprocess.run(
            [sudo, "-n", _WRAPPER, "prefer", ssid],
            capture_output=True,
            text=True,
            timeout=20,
        )
        if result.returncode != 0:
            err = (result.stderr or result.stdout or "prefer échoué").strip()
            raise RuntimeError(err[:180] or _SUDOERS_HINT)
        return result.stdout, ""
    if action == "scan":
        result = subprocess.run(
            [sudo, "-n", _WRAPPER, "scan"],
            capture_output=True,
            text=True,
            timeout=50,
        )
        if result.returncode != 0:
            err = (result.stderr or result.stdout or "scan échoué").strip()
            raise RuntimeError(err[:180] or _SUDOERS_HINT)
        show, _, wifi_list = result.stdout.partition("\n---\n")
        return show, wifi_list
    timeout = 10
    cmd = [_WRAPPER, action]
    result = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout)
    if result.returncode != 0:
        result = subprocess.run(
            [sudo, "-n", _WRAPPER, action],
            capture_output=True,
            text=True,
            timeout=timeout,
        )
    if result.returncode != 0:
        err = (result.stderr or result.stdout or "wifi échoué").strip()
        raise RuntimeError(err[:180] or _SUDOERS_HINT)
    show, _, wifi_list = result.stdout.partition("\n---\n")
    return show, wifi_list


def _self_test() -> None:
    import tempfile

    sample = (
        "*:Maison:80:WPA2:2412 MHz\n"
        " :Cafe:60:WPA2 WPA3:2462 MHz\n"
        " :Box5G:90:WPA3:5180 MHz\n"
        " :Entreprise:50:WPA2 802.1X:2412 MHz\n"
        " :Libre:40:--:2412 MHz\n"
        " ::10::2412 MHz\n"
    )
    nets = parse_nmcli_wifi_list(sample)
    ssids = [n["ssid"] for n in nets]
    assert ssids == ["Maison", "Cafe", "Libre"], ssids
    assert nets[0]["in_use"] is True
    assert nets[2]["sec"] == "open"
    ranked = annotate_nets(nets, "Libre")
    assert ranked[0]["ssid"] == "Libre" and ranked[0]["is_default"] is True
    assert ranked[1]["ssid"] == "Maison" and ranked[1]["in_use"] is True
    show = (
        "GENERAL.CONNECTION:netplan-wlan0-Maison\n"
        "GENERAL.STATE:100 (connected)\n"
        "IP4.ADDRESS[1]:192.168.1.12/24\n"
        "BDX.SSID:Maison\n"
    )
    info = parse_nmcli_device_show(show)
    assert info["connection"] == "netplan-wlan0-Maison"
    assert info["ssid"] == "Maison"
    assert info["ip"] == "192.168.1.12"
    stale = (
        " :Cafe:90:WPA2:2412 MHz\n"
        "*:Cafe:80:WPA2:2412 MHz\n"
        " :Maison:70:WPA2:2412 MHz\n"
    )
    corrected = apply_current_ssid(parse_nmcli_wifi_list(stale), "Maison")
    assert [n["ssid"] for n in corrected if n["in_use"]] == ["Maison"]
    assert map_nm_state(info["state_raw"]) == "connected"
    assert map_nm_state("30 (disconnected)") == "disconnected"
    assert map_nm_state("50 (connecting)") == "connecting"
    chunks = chunk_scan(
        [{"ssid": f"n{i}", "rssi": i, "sec": "psk", "in_use": False} for i in range(30)],
        max_bytes=180,
    )
    assert chunks[0]["n"] == len(chunks)
    assert all(len(json.dumps(c, separators=(",", ":")).encode("utf-8")) <= 220 or len(c["nets"]) == 1 for c in chunks)
    long_nets = [{"ssid": "x" * 20, "rssi": 1, "sec": "psk", "in_use": False}] * 8
    chunks = chunk_scan(long_nets, max_bytes=220)
    assert chunks[-1]["i"] == chunks[-1]["n"] - 1
    assert sanitize_ssid("ok") == "ok"
    assert sanitize_ssid("a" * 33) is None
    assert sanitize_ssid("a\nb") is None

    class Fake:
        def __init__(self) -> None:
            self.calls = []

        def __call__(self, action, ssid="", psk=""):
            self.calls.append((action, ssid, psk))
            if action == "status":
                return show, sample
            if action == "scan":
                return "", sample
            if action == "prefer":
                return "", ""
            if psk == "bad":
                raise RuntimeError("association refusée")
            return "", ""

    fake = Fake()
    with tempfile.TemporaryDirectory() as tmp:
        path = Path(tmp) / "default.json"
        wifi = RobotWifi(runner=fake, default_path=path)
        denied = wifi.request({"type": "wifi", "v": 1, "action": "join", "ssid": "Maison"})
        assert denied["accepted"] is False
        while wifi.pending():
            wifi.pop()
        wifi._status()
        st = wifi.pop()
        assert st and st["type"] == "wifi_state" and st["ssid"] == "Maison"
        assert st.get("default_ssid") is None
        wifi._scan()
        scan = wifi.pop()
        assert scan and scan["type"] == "wifi_scan" and scan["nets"]
        assert not any(n.get("is_default") for n in scan["nets"])
        wifi._join_run("Maison", "secret")
        assert ("join", "Maison", "secret") in fake.calls

        no_confirm = wifi.request({"type": "wifi", "v": 1, "action": "set_default", "ssid": "Maison"})
        assert no_confirm["accepted"] is False
        while wifi.pending():
            wifi.pop()
        wifi._save_default("Libre")
        saved = json.loads(path.read_text(encoding="utf-8"))
        assert saved["ssid"] == "Libre"
        wifi._after_set_default("Libre")
        assert ("prefer", "Libre", "") in fake.calls
        types = []
        while wifi.pending():
            types.append(wifi.pop()["type"])
        assert "wifi_state" in types and "wifi_scan" in types

        while wifi.pending():
            wifi.pop()
        wifi._scan()
        scan = wifi.pop()
        assert scan["nets"][0]["ssid"] == "Libre"
        assert scan["nets"][0]["is_default"] is True
        auto = None
        while wifi.pending():
            msg = wifi.pop()
            if msg["type"] == "wifi_state":
                auto = msg
                break
        assert auto and auto["state"] == "connecting"
        assert ("join", "Libre", "") in fake.calls
    print("ble_wifi self-test OK")


if __name__ == "__main__":
    _self_test()
