"""Actions Tests accessoires (D-018), alignées sur le banc SSH. Pas de marche."""

from __future__ import annotations

import os
import threading
from typing import Any


class AccessoryTests:
    def __init__(self) -> None:
        self._lock = threading.Lock()
        self._eyes: Any = None
        self._projector: Any = None
        self._sounds: Any = None
        self.last_result = ""
        self.last_state: dict[str, Any] | None = None

    def dispatch(self, action: str, sound: str | None = None) -> str:
        try:
            if action == "list_sounds":
                return self._list_sounds()
            if action == "speaker":
                return self._speaker(sound)
            fn = {
                "eyes_steady": self._eyes_steady,
                "eyes_blink": self._eyes_blink,
                "projector": self._projector_toggle,
                "antennas_wiggle": self._antennas_wiggle,
                "antennas_pulse": self._antennas_pulse,
            }.get(action)
            if fn is None:
                return f"action inconnue: {action}"
            return fn()
        except Exception as e:
            return f"{action} erreur: {e}"

    def _set_state(self, action: str, active: bool, message: str) -> str:
        self.last_state = {
            "type": "test_state",
            "v": 1,
            "action": action,
            "active": active,
            "message": message,
        }
        return message

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

    def _list_sounds(self) -> str:
        sounds = self._ensure_sounds()
        names = sorted(sounds.sounds.keys()) if getattr(sounds, "ok", False) else []
        self.last_state = {"type": "test_catalog", "v": 1, "sounds": names}
        if not names:
            return "Haut-parleur : aucun WAV"
        return f"sons: {len(names)}"

    def _eyes_steady(self) -> str:
        with self._lock:
            eyes = self._ensure_eyes()
            if eyes.steady_on and not eyes.is_blinking():
                eyes.set_off()
                return self._set_state("eyes_steady", False, "Yeux : OFF")
            eyes.set_on()
            return self._set_state("eyes_steady", True, "Yeux : ON (fixe)")

    def _eyes_blink(self) -> str:
        with self._lock:
            eyes = self._ensure_eyes()
            if eyes.is_blinking():
                eyes.set_off()
                return self._set_state("eyes_blink", False, "Yeux : clignotement OFF")
            eyes.start_blink()
            return self._set_state("eyes_blink", True, "Yeux : clignotement ON")

    def _projector_toggle(self) -> str:
        with self._lock:
            projector = self._ensure_projector()
            projector.switch()
            etat = "ON" if projector.on else "OFF"
            return self._set_state("projector", projector.on, f"Projecteur : {etat}")

    def _speaker(self, sound: str | None) -> str:
        sounds = self._ensure_sounds()
        if not getattr(sounds, "ok", False):
            return "Haut-parleur indisponible"
        if not sound:
            return "Haut-parleur : fichier manquant"
        name = os.path.basename(str(sound))
        if name not in sounds.sounds:
            return f"Son inconnu : {name}"
        sounds.play(name)
        message = self._set_state("speaker", True, f"Haut-parleur : {name}")
        if self.last_state is not None:
            self.last_state["sound"] = name
        return message

    def _antennas_wiggle(self) -> str:
        threading.Thread(target=self._run_wiggle, daemon=True).start()
        return self._set_state("antennas_wiggle", True, "Antennes : oscillation 2 s")

    def _antennas_pulse(self) -> str:
        threading.Thread(target=self._run_pulse, daemon=True).start()
        return self._set_state("antennas_pulse", True, "Antennes : 90°")

    def _run_wiggle(self) -> None:
        from mini_bdx_runtime.antennas import Antennas

        antennas = Antennas()
        try:
            antennas.oscillate(duration=2.0, frequency=1.0)
        finally:
            antennas.stop()
            self.last_state = {
                "type": "test_state",
                "v": 1,
                "action": "antennas_wiggle",
                "active": False,
                "message": "Antennes : oscillation finie",
            }

    def _run_pulse(self) -> None:
        from mini_bdx_runtime.antennas import Antennas

        antennas = Antennas()
        try:
            antennas.set_center()
        finally:
            antennas.stop()
            self.last_state = {
                "type": "test_state",
                "v": 1,
                "action": "antennas_pulse",
                "active": False,
                "message": "Antennes : 90° fini",
            }
