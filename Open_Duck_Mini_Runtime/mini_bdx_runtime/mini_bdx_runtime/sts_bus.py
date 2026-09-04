"""État bus STS + tension moyenne (télémétrie servos via adaptateur Waveshare)."""

from __future__ import annotations

import os
import threading
from typing import Any


STS_IDS = (20, 21, 22, 23, 24, 30, 31, 32, 33, 10, 11, 12, 13, 14)
PORTS = ("/dev/ttyACM0", "/dev/ttyUSB0")
SERIAL_TIMEOUT_S = 0.03
VOLTAGE_ADDR = 62  # present voltage, unité 0,1 V
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


class _FeetechSerial:
    """Lecture tension STS en protocole Feetech v1, sans rustypot/pypot."""

    def __init__(self, port: str) -> None:
        import serial

        self._ser = serial.Serial(
            port=port,
            baudrate=1_000_000,
            timeout=SERIAL_TIMEOUT_S,
            write_timeout=SERIAL_TIMEOUT_S,
        )

    def close(self) -> None:
        try:
            self._ser.close()
        except Exception:
            pass

    def get_present_voltage(self, ids: list[int]) -> list[Any]:
        probe_ok = False
        for sid in (20, 10):
            if self._read_u8(sid, VOLTAGE_ADDR) is not None:
                probe_ok = True
                break
        if not probe_ok:
            return [None] * len(ids)
        return [self._read_u8(sid, VOLTAGE_ADDR) for sid in ids]

    def _read_u8(self, sid: int, addr: int) -> int | None:
        payload = bytes((sid, 4, 2, addr, 1))
        pkt = b"\xff\xff" + payload + bytes(((~sum(payload)) & 0xFF,))
        ser = self._ser
        try:
            ser.reset_input_buffer()
            ser.write(pkt)
            data = ser.read(8)
        except Exception:
            return None
        for _ in range(4):
            i = data.find(b"\xff\xff")
            if i < 0 or len(data) < i + 6:
                extra = ser.read(8)
                if not extra:
                    return None
                data += extra
                continue
            length = data[i + 3]
            if length == 4:
                data = data[i + 8 :]
                if len(data) < 6:
                    extra = ser.read(8)
                    if not extra:
                        return None
                    data += extra
                continue
            need = i + 4 + length
            if len(data) < need:
                extra = ser.read(need - len(data))
                if not extra:
                    return None
                data += extra
            if data[i + 2] != sid or length < 3:
                return None
            return data[i + 5]
        return None


class StsBusMonitor:
    def __init__(self, interval_s: float = 30.0) -> None:
        self.interval_s = interval_s
        self._lock = threading.Lock()
        self._snapshot: dict[str, Any] = {
            "type": "status",
            "v": 1,
            "sts_bus": "down",
            "sts_ok": 0,
            "sts_n": len(STS_IDS),
            "bus_v": None,
            "sts_msg": "no_lib",
            "sts": [{"id": sid, "ok": False} for sid in STS_IDS],
        }
        self._io: Any = None
        self._stop = threading.Event()
        self._logged_reason = ""
        self._down_code = "no_lib"
        self._ready = threading.Event()
        self._paused = threading.Event()

    def snapshot(self) -> dict[str, Any]:
        with self._lock:
            return dict(self._snapshot)

    def start(self) -> None:
        self._ready.set()
        threading.Thread(target=self._loop, name="sts_bus", daemon=True).start()

    def pause(self) -> None:
        """Libère le port série (démo tête / rustypot)."""
        self._paused.set()
        self._drop_io()

    def resume(self) -> None:
        self._paused.clear()

    def _empty_sts(self) -> list[dict[str, Any]]:
        return [{"id": sid, "ok": False} for sid in STS_IDS]

    def _set(
        self,
        bus: str,
        ok: int,
        voltage: float | None,
        msg: str = "",
        sts: list[dict[str, Any]] | None = None,
    ) -> None:
        with self._lock:
            self._snapshot = {
                "type": "status",
                "v": 1,
                "sts_bus": bus,
                "sts_ok": ok,
                "sts_n": len(STS_IDS),
                "bus_v": None if voltage is None else round(voltage, 2),
                "sts_msg": msg,
                "sts": list(sts) if sts is not None else self._empty_sts(),
            }

    def _log_once(self, reason: str) -> None:
        if reason == self._logged_reason:
            return
        self._logged_reason = reason
        print(f"[sts_bus] {reason}", flush=True)

    def _loop(self) -> None:
        self._ready.wait()
        while not self._stop.is_set():
            if self._paused.is_set():
                if self._stop.wait(0.25):
                    break
                continue
            try:
                ok, volts, sts = self._read()
            except Exception as e:
                self._drop_io()
                self._set("down", 0, None, "no_reply")
                self._log_once(f"lecture impossible: {e}")
            else:
                n = len(STS_IDS)
                mean = (sum(volts) / len(volts)) if volts else None
                if ok == 0:
                    self._set("down", 0, None, self._down_code, sts)
                    self._log_once(
                        {
                            "no_lib": "pyserial/rustypot/pypot absents",
                            "no_port": "pas de port série STS (/dev/ttyACM0)",
                            "no_perm": "accès série refusé (groupe dialout)",
                            "no_reply": "aucun servo ne répond",
                        }.get(self._down_code, self._down_code)
                    )
                elif ok < n:
                    self._set("partial", ok, mean, "", sts)
                    self._logged_reason = ""
                else:
                    self._set("ok", ok, mean, "", sts)
                    self._logged_reason = ""
            if self._stop.wait(self.interval_s):
                break

    def _read(self) -> tuple[int, list[float], list[dict[str, Any]]]:
        io = self._ensure_io()
        if io is None:
            return 0, [], self._empty_sts()
        volt_raw = self._bulk(io, VOLT_METHODS)
        sts, volts = self._map_servos(volt_raw, voltages=True)
        if volts:
            return len(volts), volts, sts
        pos_raw = self._bulk(io, POS_METHODS)
        sts_pos, present = self._map_servos(pos_raw, voltages=False)
        if present:
            self._log_once("bus répond, tension illisible")
            return len(present), [], sts_pos
        self._down_code = "no_reply"
        return 0, [], self._empty_sts()

    def _map_servos(
        self, raws: list[Any], voltages: bool
    ) -> tuple[list[dict[str, Any]], list[float]]:
        sts = self._empty_sts()
        good: list[float] = []
        if len(raws) != len(STS_IDS):
            return sts, good
        for i, sid in enumerate(STS_IDS):
            raw = raws[i]
            if voltages:
                v = _to_volts(raw)
                ok = v is not None
                if ok:
                    good.append(v)
            else:
                ok = raw is not None
                if ok:
                    good.append(0.0)
            sts[i] = {"id": sid, "ok": ok}
        return sts, good

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
            self._down_code = "no_port"
            self._log_once("pas de port série STS (/dev/ttyACM0)")
            return None
        errors: list[str] = []
        for opener in (
            self._open_pyserial,
            self._open_sts_controller,
            self._open_rustypot_feetech,
            self._open_pypot,
        ):
            try:
                io = opener(port)
            except PermissionError as e:
                self._down_code = "no_perm"
                errors.append(f"{opener.__name__}: {e}")
                continue
            except Exception as e:
                text = str(e).lower()
                if "permission" in text or "denied" in text:
                    self._down_code = "no_perm"
                errors.append(f"{opener.__name__}: {e}")
                continue
            if io is None:
                continue
            self._io = io
            return io
        if not errors:
            self._down_code = "no_lib"
            self._log_once("pyserial/rustypot/pypot absents du venv")
        elif self._down_code != "no_perm":
            self._down_code = "no_lib"
            self._log_once("ouverture " + port + " échouée (" + "; ".join(errors) + ")")
        else:
            self._log_once("accès série refusé (groupe dialout)")
        return None

    def _open_pyserial(self, port: str) -> Any:
        io = _FeetechSerial(port)
        self._log_once(f"ouvert {port} (pyserial)")
        return io

    def _open_rustypot_feetech(self, port: str) -> Any:
        import rustypot

        if not hasattr(rustypot, "feetech"):
            return None
        try:
            io = rustypot.feetech(port, 1_000_000, timeout=0.1)
        except TypeError:
            io = rustypot.feetech(port, 1_000_000)
        self._log_once(f"ouvert {port} (rustypot.feetech)")
        return io

    def _open_sts_controller(self, port: str) -> Any:
        import rustypot

        cls = getattr(rustypot, "Sts3215PyController", None)
        if cls is None:
            return None
        io = cls(serial_port=port, baudrate=1_000_000, timeout=0.1)
        self._log_once(f"ouvert {port} (Sts3215PyController)")
        return io

    def _open_pypot(self, port: str) -> Any:
        from pypot.feetech import FeetechSTS3215IO

        try:
            io = FeetechSTS3215IO(
                port,
                baudrate=1_000_000,
                use_sync_read=True,
                timeout=0.1,
            )
        except TypeError:
            io = FeetechSTS3215IO(port, baudrate=1_000_000, use_sync_read=True)
        self._log_once(f"ouvert {port} (pypot)")
        return io
