"""Séquence de démarrage (D-008) : 3 clignements, 4 oscillations d’antennes, happy1.

Init réseau (comme BLE) : WIFI_OKAY si le lien est up, sinon WIFI_PROBLEM (D-024).
"""

from __future__ import annotations

import os
import subprocess
import time

BLE_READY_SOUND = "BLE_OKAY_mini_BDX.wav"
WIFI_OKAY_SOUND = "WIFI_OKAY_mini_BDX.wav"
WIFI_PROBLEM_SOUND = "WIFI_PROBLEM_mini_BDX.wav"
WIFI_WAIT_S = 20.0


def run_boot_hello() -> None:
    os.environ.setdefault("SDL_VIDEODRIVER", "dummy")
    os.environ.setdefault("SDL_AUDIODRIVER", "alsa")
    print("[boot_hello] yeux ×3, antennes ×4, happy1", flush=True)
    _blink_three()
    _wiggle_four()
    _play_happy1()
    print("[boot_hello] terminé", flush=True)


def _blink_three() -> None:
    try:
        from mini_bdx_runtime.eyes import Eyes

        eyes = Eyes(auto_start=False)
        try:
            eyes.blink_times(3)
        finally:
            eyes.stop()
    except Exception as e:
        print(f"[boot_hello] yeux : {e}", flush=True)


def _wiggle_four() -> None:
    try:
        from mini_bdx_runtime.antennas import Antennas

        antennas = Antennas()
        try:
            # 1 Hz pendant 4 s → 4 allers-retours.
            antennas.oscillate(duration=4.0, frequency=1.0)
        finally:
            antennas.stop()
    except Exception as e:
        print(f"[boot_hello] antennes : {e}", flush=True)


def run_ble_ready_sound() -> None:
    """Joué dès que la pub BLE est active (en attente de connexion Android)."""
    os.environ.setdefault("SDL_VIDEODRIVER", "dummy")
    os.environ.setdefault("SDL_AUDIODRIVER", "alsa")
    print(f"[boot_hello] BLE prêt → {BLE_READY_SOUND}", flush=True)
    _play_wav(BLE_READY_SOUND)


def nmcli_wifi_state() -> str:
    """connected | connecting | disconnected."""
    try:
        result = subprocess.run(
            ["nmcli", "-t", "-f", "TYPE,STATE", "device", "status"],
            capture_output=True,
            text=True,
            timeout=5,
        )
    except (OSError, subprocess.TimeoutExpired):
        return "disconnected"
    if result.returncode != 0:
        return "disconnected"
    for line in result.stdout.splitlines():
        typ, _, state = line.partition(":")
        if typ.strip() != "wifi":
            continue
        return _map_nmcli_device_state(state.strip())
    return "disconnected"


def _map_nmcli_device_state(state: str) -> str:
    low = state.lower()
    if low == "connected" or low.startswith("connected"):
        return "connected"
    if "connecting" in low:
        return "connecting"
    return "disconnected"


def wait_wifi_connected(timeout_s: float = WIFI_WAIT_S) -> bool:
    """Attend l’association NM du profil par défaut (autoconnect)."""
    deadline = time.monotonic() + timeout_s
    while True:
        state = nmcli_wifi_state()
        if state == "connected":
            return True
        if time.monotonic() >= deadline:
            return False
        time.sleep(1.0)


def run_wifi_init_sound() -> None:
    """Après le BLE : OK si le Wi‑Fi défaut est up, sinon PROBLEM (UI Paramètres)."""
    os.environ.setdefault("SDL_VIDEODRIVER", "dummy")
    os.environ.setdefault("SDL_AUDIODRIVER", "alsa")
    print("[boot_hello] Wi‑Fi init : attente association…", flush=True)
    ok = wait_wifi_connected()
    name = WIFI_OKAY_SOUND if ok else WIFI_PROBLEM_SOUND
    print(f"[boot_hello] Wi‑Fi {'OK' if ok else 'échec'} → {name}", flush=True)
    _play_wav(name)


def _play_happy1() -> None:
    _play_wav("happy1.wav")


def _play_wav(name: str) -> None:
    try:
        from mini_bdx_runtime.sounds import Sounds, default_assets_directory

        sounds = Sounds(volume=1.0, sound_directory=default_assets_directory())
        if not getattr(sounds, "ok", False):
            print("[boot_hello] haut-parleur indisponible", flush=True)
            return
        sounds.play(name)
        clip = sounds.sounds.get(name)
        wait = 1.5
        if clip is not None:
            try:
                wait = min(float(clip.get_length()) + 0.1, 4.0)
            except Exception:
                pass
        time.sleep(wait)
    except Exception as e:
        print(f"[boot_hello] son ({name}) : {e}", flush=True)


if __name__ == "__main__":
    run_boot_hello()
