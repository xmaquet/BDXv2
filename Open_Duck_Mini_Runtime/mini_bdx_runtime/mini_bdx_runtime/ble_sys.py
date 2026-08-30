"""Télémétrie Pi légère pour l’écran Paramètres. Lectures /proc uniquement."""

from __future__ import annotations

import os
from typing import Any

_PROC_STAT = "/proc/stat"
_PROC_MEM = "/proc/meminfo"
_PROC_LOAD = "/proc/loadavg"
_PROC_UPTIME = "/proc/uptime"
_THERMAL = "/sys/class/thermal/thermal_zone0/temp"


def _kb_to_mb(kb: int) -> int:
    return max(0, kb // 1024)


def snapshot(prev_idle: int | None = None, prev_total: int | None = None) -> dict[str, Any]:
    """Mesures cheap. `cpu` est omis au premier échantillon (besoin d’un delta)."""
    load = _load1()
    total_kb, avail_kb = _mem()
    tot = _kb_to_mb(total_kb) if total_kb else 0
    avail = _kb_to_mb(avail_kb) if avail_kb is not None else 0
    used_pct = 0
    if tot > 0:
        used_pct = min(100, max(0, int(round(100.0 * (tot - avail) / tot))))
    idle, total = _cpu_times()
    cpu = None
    if prev_idle is not None and prev_total is not None and total > prev_total:
        didle = idle - prev_idle
        dtotal = total - prev_total
        if dtotal > 0:
            cpu = int(round(100.0 * (1.0 - didle / dtotal)))
            cpu = min(100, max(0, cpu))
    out: dict[str, Any] = {
        "type": "sys",
        "v": 1,
        "load": load,
        "mem": used_pct,
        "avail": avail,
        "tot": tot,
        "up": _uptime_s(),
    }
    temp = _temp_c()
    if temp is not None:
        out["temp"] = temp
    disk = _disk_used_pct("/")
    if disk is not None:
        out["disk"] = disk
    if cpu is not None:
        out["cpu"] = cpu
    return out


class RobotSys:
    def __init__(self) -> None:
        self.last_state: dict[str, Any] | None = None
        self._idle: int | None = None
        self._total: int | None = None

    def request(self, obj: dict[str, Any]) -> dict[str, Any]:
        if int(obj.get("v", 0)) != 1:
            state = {"type": "sys", "v": 1, "message": "sys : version inconnue"}
            self.last_state = state
            return state
        idle, total = _cpu_times()
        state = snapshot(self._idle, self._total)
        self._idle, self._total = idle, total
        self.last_state = state
        return state


def _load1() -> float:
    try:
        raw = open(_PROC_LOAD, encoding="ascii").read().split()[0]
        return round(float(raw), 2)
    except (OSError, ValueError, IndexError):
        return 0.0


def _mem() -> tuple[int | None, int | None]:
    total = avail = None
    try:
        with open(_PROC_MEM, encoding="ascii") as fh:
            for line in fh:
                if line.startswith("MemTotal:"):
                    total = int(line.split()[1])
                elif line.startswith("MemAvailable:"):
                    avail = int(line.split()[1])
                if total is not None and avail is not None:
                    break
    except (OSError, ValueError):
        return None, None
    return total, avail


def _cpu_times() -> tuple[int, int]:
    try:
        with open(_PROC_STAT, encoding="ascii") as fh:
            parts = fh.readline().split()
        nums = [int(x) for x in parts[1:8]]
        idle = nums[3] + nums[4]
        return idle, sum(nums)
    except (OSError, ValueError, IndexError):
        return 0, 0


def _temp_c() -> float | None:
    try:
        raw = int(open(_THERMAL, encoding="ascii").read().strip())
        return round(raw / 1000.0, 1)
    except (OSError, ValueError):
        return None


def _disk_used_pct(path: str) -> int | None:
    try:
        st = os.statvfs(path)
    except (OSError, AttributeError):
        return None
    total = st.f_blocks * st.f_frsize
    if total <= 0:
        return None
    used = (st.f_blocks - st.f_bavail) * st.f_frsize
    return min(100, max(0, int(round(100.0 * used / total))))


def _uptime_s() -> int:
    try:
        return int(float(open(_PROC_UPTIME, encoding="ascii").read().split()[0]))
    except (OSError, ValueError, IndexError):
        return 0


def _self_test() -> None:
    sysd = RobotSys()
    a = sysd.request({"type": "sys", "v": 1})
    assert a["type"] == "sys" and a["v"] == 1
    assert "load" in a and "mem" in a and "tot" in a and "up" in a
    b = sysd.request({"type": "sys", "v": 1})
    assert b["type"] == "sys"
    bad = sysd.request({"type": "sys", "v": 2})
    assert "message" in bad
    print("ble_sys self-test OK")


if __name__ == "__main__":
    _self_test()
