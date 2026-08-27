"""Séquence de démarrage (D-008) : 3 clignements, 4 oscillations d’antennes, happy1."""

from __future__ import annotations

import os
import time


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


def _play_happy1() -> None:
    try:
        from mini_bdx_runtime.sounds import Sounds, default_assets_directory

        sounds = Sounds(volume=1.0, sound_directory=default_assets_directory())
        if not getattr(sounds, "ok", False):
            print("[boot_hello] haut-parleur indisponible", flush=True)
            return
        sounds.play("happy1.wav")
        clip = sounds.sounds.get("happy1.wav")
        wait = 1.5
        if clip is not None:
            try:
                wait = min(float(clip.get_length()) + 0.1, 4.0)
            except Exception:
                pass
        time.sleep(wait)
    except Exception as e:
        print(f"[boot_hello] son : {e}", flush=True)


if __name__ == "__main__":
    run_boot_hello()
