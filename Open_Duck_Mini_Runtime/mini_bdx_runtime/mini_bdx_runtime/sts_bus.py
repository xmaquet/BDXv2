"""État bus STS + tension moyenne (télémétrie servos via adaptateur Waveshare)."""

from __future__ import annotations

import os
import threading
from typing import Any


STS_IDS = (20, 21, 22, 23, 24, 30, 31, 32, 33, 10, 11, 12, 13, 14)
PORTS = ("/dev/ttyACM0", "/dev/ttyUSB0")
SERIAL_TIMEOUT_S = 0.05
VOLT_METHODS = (
    "sync_read_present_voltage",
    "get_present_voltage",
    "read_present_voltage",
)
POS_METHODS = (
    "sync_read_present_position",
    "get_present_position",
    "read_present_position",
)


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


def _flatten_numbers(raw: Any) -> list[Any]:
    out: list[Any] = []
    for item in _as_list(raw):
        if isinstance(item, (list, tuple)) and len(item) == 1:
            item = item[0]
        out.append(item)
    return out


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
        self._ready = threading.Event()

    def snapshot(self) -> dict[str, Any]:
        with self._lock:
            return dict(self._snapshot)

    def start(self) -> None:
        self._ready.set()
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
        self._ready.wait()
        while not self._stop.is_set():
            try:
                ok, volts = self._read()
            except Exception as e:
                self._drop_io()
                self._set("down", 0, None)
                self._log_once(f"lecture impossible: {e}")
            else:
                n = len(STS_IDS)
                mean = (sum(volts) / len(volts)) if volts else None
                if ok == 0:
                    self._set("down", 0, None)
                    self._log_once("aucun servo ne répond")
                elif ok < n:
                    self._set("partial", ok, mean)
                    self._logged_reason = ""
                else:
                    self._set("ok", ok, mean)
                    self._logged_reason = ""
            if self._stop.wait(self.interval_s):
                break

    def _read(self) -> tuple[int, list[float]]:
        io = self._ensure_io()
        if io is None:
            return 0, []
        volt_raw = self._bulk(io, VOLT_METHODS)
        volts: list[float] = []
        for raw in volt_raw:
            v = _to_volts(raw)
            if v is not None:
                volts.append(v)
        if volts:
            return len(volts), volts
        pos_raw = self._bulk(io, POS_METHODS)
        present = [p for p in pos_raw if p is not None]
        if present:
            self._log_once("bus répond, tension illisible")
            return len(present), []
        return 0, []

    def _bulk(self, io: Any, names: tuple[str, ...]) -> list[Any]:
        ids = list(STS_IDS)
        last_err = ""
        for name in names:
            getter = getattr(io, name, None)
            if getter is None:
                continue
            try:
                return _flatten_numbers(getter(ids))
            except TypeError:
                try:
                    if len(ids) == 1:
                        return _flatten_numbers(getter(ids[0]))
                except Exception as e:
                    last_err = f"{name}: {e}"
            except Exception as e:
                last_err = f"{name}: {e}"
        if last_err:
            self._log_once(last_err)
        return []

    def _drop_io(self) -> None:
        io = self._io
        self._io = None
        if io is None:
            return
        for name in ("close", "disconnect"):
            fn = getattr(io, name, None)
            if not callable(fn):
                continue
            try:
                fn()
            except Exception:
                pass
            return

    def _ensure_io(self) -> Any:
        if self._io is not None:
            return self._io
        port = next((p for p in PORTS if os.path.exists(p)), None)
        if port is None:
            self._log_once("pas de port série STS (/dev/ttyACM0)")
            return None
        errors: list[str] = []
        for opener in (self._open_rustypot_feetech, self._open_sts_controller, self._open_pypot):
            try:
                io = opener(port)
            except Exception as e:
                errors.append(f"{opener.__name__}: {e}")
                continue
            if io is None:
                continue
            self._io = io
            return io
        if errors:
            self._log_once("ouverture " + port + " échouée (" + "; ".join(errors) + ")")
        else:
            self._log_once("pypot/rustypot absents du venv (extra hardware)")
        return None

    def _open_rustypot_feetech(self, port: str) -> Any:
        import rustypot

        if not hasattr(rustypot, "feetech"):
            return None
        try:
            io = rustypot.feetech(port, 1_000_000, timeout=SERIAL_TIMEOUT_S)
        except TypeError:
            io = rustypot.feetech(port, 1_000_000)
        self._log_once(f"ouvert {port} (rustypot.feetech)")
        return io

    def _open_sts_controller(self, port: str) -> Any:
        import rustypot

        cls = getattr(rustypot, "Sts3215PyController", None)
        if cls is None:
            return None
        io = cls(serial_port=port, baudrate=1_000_000, timeout=SERIAL_TIMEOUT_S)
        self._log_once(f"ouvert {port} (Sts3215PyController)")
        return io

    def _open_pypot(self, port: str) -> Any:
        from pypot.feetech import FeetechSTS3215IO

        try:
            io = FeetechSTS3215IO(
                port,
                baudrate=1_000_000,
                use_sync_read=True,
                timeout=SERIAL_TIMEOUT_S,
            )
        except TypeError:
            io = FeetechSTS3215IO(port, baudrate=1_000_000, use_sync_read=True)
        self._log_once(f"ouvert {port} (pypot)")
        return io
