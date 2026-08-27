"""Actions Tests accessoires (D-018), alignées sur le banc SSH. Pas de marche."""

from __future__ import annotations

import threading
from typing import Any


class AccessoryTests:
    def __init__(self) -> None:
        self._lock = threading.Lock()
        self._eyes: Any = None
        self._projector: Any = None
        self._sounds: Any = None
        self.last_result = ""

    def dispatch(self, action: str) -> str:
        fn = {
            "eyes_steady": self._eyes_steady,
            "eyes_blink": self._eyes_blink,
            "projector": self._projector_toggle,
            "speaker": self._speaker,
            "antennas_wiggle": self._antennas_wiggle,
            "antennas_pulse": self._antennas_pulse,
        }.get(action)
        if fn is None:
            return f"action inconnue: {action}"
        try:
            return fn()
        except Exception as e:
            return f"{action} erreur: {e}"

    def _ensure_eyes(self):
        if self._eyes is None:
            from mini_bdx_runtime.eyes import Eyes

            self._eyes = Eyes(auto_start=False)
        return self._eyes

    def _ensure_projector(self):
        if self._projector is None:
            from mini_bdx_runtime.projector import Projector

            self._projector = Projector()
        return self._projector

    def _ensure_sounds(self):
        if self._sounds is None:
            from mini_bdx_runtime.sounds import Sounds, default_assets_directory

            self._sounds = Sounds(volume=1.0, sound_directory=default_assets_directory())
        return self._sounds

    def _eyes_steady(self) -> str:
        with self._lock:
            eyes = self._ensure_eyes()
            if eyes.steady_on and not eyes.is_blinking():
                eyes.set_off()
                return "Yeux : OFF"
            eyes.set_on()
            return "Yeux : ON (fixe)"

    def _eyes_blink(self) -> str:
        with self._lock:
            eyes = self._ensure_eyes()
            if eyes.is_blinking():
                eyes.set_off()
                return "Yeux : clignotement OFF"
            eyes.start_blink()
            return "Yeux : clignotement ON"

    def _projector_toggle(self) -> str:
        with self._lock:
            projector = self._ensure_projector()
            projector.switch()
            etat = "ON" if projector.on else "OFF"
            return f"Projecteur : {etat}"

    def _speaker(self) -> str:
        sounds = self._ensure_sounds()
        if not getattr(sounds, "ok", False):
            return "Haut-parleur indisponible"
        sounds.play_random_sound()
        return "Haut-parleur : lecture"

    def _antennas_wiggle(self) -> str:
        threading.Thread(target=self._run_wiggle, daemon=True).start()
        return "Antennes : oscillation 2 s"

    def _antennas_pulse(self) -> str:
        threading.Thread(target=self._run_pulse, daemon=True).start()
        return "Antennes : 90°"

    def _run_wiggle(self) -> None:
        from mini_bdx_runtime.antennas import Antennas

        antennas = Antennas()
        try:
            antennas.oscillate(duration=2.0, frequency=1.0)
        finally:
            antennas.stop()

    def _run_pulse(self) -> None:
        from mini_bdx_runtime.antennas import Antennas

        antennas = Antennas()
        try:
            antennas.set_center()
        finally:
            antennas.stop()
