"""État bus STS + tension moyenne (télémétrie servos via adaptateur Waveshare)."""

from __future__ import annotations

import os
import threading
from typing import Any


STS_IDS = (20, 21, 22, 23, 24, 30, 31, 32, 33, 10, 11, 12, 13, 14)
PORTS = ("/dev/ttyACM0", "/dev/ttyUSB0")


def _to_volts(raw: Any) -> float | None:
    try:
        v = float(raw)
    except (TypeError, ValueError):
        return None
    if v <= 0:
        return None
    if v > 20:
        v = v * 0.1
    if v < 4.0 or v > 16.0:
        return None
    return round(v, 2)


def _as_list(raw: Any) -> list[Any]:
    if raw is None:
        return []
    if isinstance(raw, (int, float)):
        return [raw]
    try:
        return list(raw)
    except TypeError:
        return [raw]


class StsBusMonitor:
    def __init__(self, interval_s: float = 2.0) -> None:
        self.interval_s = interval_s
        self._lock = threading.Lock()
        self._snapshot: dict[str, Any] = {
            "type": "status",
            "v": 1,
            "sts_bus": "down",
            "sts_ok": 0,
            "sts_n": len(STS_IDS),
            "bus_v": None,
        }
        self._io: Any = None
        self._stop = threading.Event()
        self._logged_reason = ""

    def snapshot(self) -> dict[str, Any]:
        with self._lock:
            return dict(self._snapshot)

    def start(self) -> None:
        threading.Thread(target=self._loop, name="sts_bus", daemon=True).start()

    def _set(self, bus: str, ok: int, voltage: float | None) -> None:
        with self._lock:
            self._snapshot = {
                "type": "status",
                "v": 1,
                "sts_bus": bus,
                "sts_ok": ok,
                "sts_n": len(STS_IDS),
                "bus_v": None if voltage is None else round(voltage, 2),
            }

    def _log_once(self, reason: str) -> None:
        if reason == self._logged_reason:
            return
        self._logged_reason = reason
        print(f"[sts_bus] {reason}", flush=True)

    def _loop(self) -> None:
        while not self._stop.is_set():
            try:
                volts = self._read_volts()
            except Exception as e:
                self._io = None
                self._set("down", 0, None)
                self._log_once(f"lecture impossible: {e}")
            else:
                ok = len(volts)
                n = len(STS_IDS)
                if ok == 0:
                    self._set("down", 0, None)
                    self._log_once("aucun servo ne répond")
                elif ok < n:
                    self._set("partial", ok, sum(volts) / ok)
                    self._logged_reason = ""
                else:
                    self._set("ok", ok, sum(volts) / ok)
                    self._logged_reason = ""
            if self._stop.wait(self.interval_s):
                break

    def _read_volts(self) -> list[float]:
        io = self._ensure_io()
        if io is None:
            return []
        getter = None
        for name in (
            "get_present_voltage",
            "read_present_voltage",
            "sync_read_present_voltage",
        ):
            if hasattr(io, name):
                getter = getattr(io, name)
                break
        if getter is None:
            self._log_once("pas de lecture tension sur l’API servo")
            self._io = None
            return []
        raws = self._invoke(getter, STS_IDS)
        out = []
        for raw in raws:
            v = _to_volts(raw)
            if v is not None:
                out.append(v)
        if out:
            return out
        out = []
        for sid in STS_IDS:
            for raw in self._invoke(getter, (sid,)):
                v = _to_volts(raw)
                if v is not None:
                    out.append(v)
                    break
        return out

    def _invoke(self, getter: Any, ids: tuple[int, ...] | list[int]) -> list[Any]:
        ids_list = list(ids)
        try:
            return _as_list(getter(ids_list))
        except Exception:
            pass
        if len(ids_list) == 1:
            try:
                return _as_list(getter(ids_list[0]))
            except Exception:
                return []
        return []

    def _ensure_io(self) -> Any:
        if self._io is not None:
            return self._io
        port = next((p for p in PORTS if os.path.exists(p)), None)
        if port is None:
            self._log_once("pas de port série STS (/dev/ttyACM0)")
            return None
        pypot_err: Exception | None = None
        try:
            from pypot.feetech import FeetechSTS3215IO

            self._io = FeetechSTS3215IO(port, baudrate=1000000, use_sync_read=True)
            self._log_once(f"ouvert {port} (pypot)")
            return self._io
        except Exception as e:
            pypot_err = e
        try:
            import rustypot

            self._io = rustypot.feetech(port, 1000000)
            self._log_once(f"ouvert {port} (rustypot)")
            return self._io
        except Exception as e:
            self._log_once(f"ouverture {port} échouée (pypot: {pypot_err}; rustypot: {e})")
            return None
