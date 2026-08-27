"""Arrêt OS (D-021). Pas d’estop, pas de reboot, pas un test accessoire."""

from __future__ import annotations

import os
import subprocess
import threading
import time
from typing import Any


_POWEROFF_PATHS = ("/sbin/poweroff", "/usr/sbin/poweroff")
_SUDOERS_HINT = (
    "sudo poweroff refusé. Sur le Pi : "
    "bash ~/BDXv2/Open_Duck_Mini_Runtime/scripts/enable_halt_sudo.sh"
)


class SystemHalt:
    def __init__(self) -> None:
        self.last_state: dict[str, Any] | None = None
        self._lock = threading.Lock()
        self._pending = False

    def request(self, obj: dict[str, Any]) -> dict[str, Any]:
        if int(obj.get("v", 0)) != 1:
            return self._ack(False, "halt : version inconnue")
        if obj.get("confirm") is not True:
            return self._ack(False, "halt : confirmation manquante")
        cmd = self._poweroff_cmd()
        if cmd is None:
            return self._ack(False, _SUDOERS_HINT)
        with self._lock:
            if self._pending:
                return self._ack(True, "Arrêt déjà demandé")
            self._pending = True
        ack = self._ack(True, "Arrêt demandé")
        threading.Thread(target=self._run, args=(cmd,), daemon=True).start()
        return ack

    def _ack(self, accepted: bool, message: str) -> dict[str, Any]:
        state = {
            "type": "halt_ack",
            "v": 1,
            "accepted": accepted,
            "message": message,
        }
        self.last_state = state
        return state

    def _poweroff_cmd(self) -> list[str] | None:
        for path in _POWEROFF_PATHS:
            if not os.path.isfile(path):
                continue
            try:
                probe = subprocess.run(
                    ["sudo", "-n", "-l", "--", path],
                    capture_output=True,
                    text=True,
                    timeout=5,
                )
            except Exception:
                continue
            if probe.returncode == 0:
                return ["sudo", "-n", path]
        return None

    def _run(self, cmd: list[str]) -> None:
        time.sleep(2.0)
        try:
            result = subprocess.run(cmd, capture_output=True, text=True, timeout=15)
        except Exception as e:
            self._pending = False
            self._ack(False, f"halt erreur : {e}")
            return
        if result.returncode != 0:
            self._pending = False
            err = (result.stderr or result.stdout or _SUDOERS_HINT).strip()
            self._ack(False, err[:180] or _SUDOERS_HINT)
