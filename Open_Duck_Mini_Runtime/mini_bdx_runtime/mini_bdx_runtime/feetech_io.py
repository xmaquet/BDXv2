"""Open a Feetech STS3215 IO handle compatible with HWI.

rustypot >= 1.6 exposes Sts3215PyController; older code expected rustypot.feetech().
"""

from __future__ import annotations

from typing import Any, Iterable, List


def _as_list(values: Iterable[Any]) -> List[Any]:
    return list(values)


def _as_int_list(values: Iterable[Any]) -> List[int]:
    return [int(v) for v in values]


def _as_float_list(values: Iterable[Any]) -> List[float]:
    return [float(v) for v in values]


class _Sts3215Adapter:
    """Wrap Sts3215PyController with the legacy HWI method names."""

    def __init__(self, controller: Any):
        self._c = controller

    def set_kps(self, ids, kps):
        self._c.sync_write_p_coefficient(_as_int_list(ids), _as_int_list(kps))

    def set_kds(self, ids, kds):
        self._c.sync_write_d_coefficient(_as_int_list(ids), _as_int_list(kds))

    def write_goal_position(self, ids, positions):
        self._c.sync_write_goal_position(_as_int_list(ids), _as_float_list(positions))

    def read_present_position(self, ids):
        return list(self._c.sync_read_present_position(_as_int_list(ids)))

    def read_present_velocity(self, ids):
        return list(self._c.sync_read_present_velocity(_as_int_list(ids)))

    def disable_torque(self, ids):
        ids = _as_int_list(ids)
        self._c.sync_write_torque_enable(ids, [False] * len(ids))

    def enable_torque(self, ids):
        ids = _as_int_list(ids)
        self._c.sync_write_torque_enable(ids, [True] * len(ids))


def open_feetech_io(usb_port: str, baudrate: int = 1_000_000, timeout: float = 0.1):
    import rustypot

    if hasattr(rustypot, "feetech"):
        try:
            return rustypot.feetech(usb_port, baudrate, timeout)
        except TypeError:
            return rustypot.feetech(usb_port, baudrate)

    cls = getattr(rustypot, "Sts3215PyController", None)
    if cls is None:
        raise RuntimeError(
            "rustypot is installed but exposes neither feetech nor Sts3215PyController"
        )

    try:
        controller = cls(serial_port=usb_port, baudrate=baudrate, timeout=timeout)
    except TypeError:
        controller = cls(usb_port, baudrate, timeout)

    return _Sts3215Adapter(controller)
