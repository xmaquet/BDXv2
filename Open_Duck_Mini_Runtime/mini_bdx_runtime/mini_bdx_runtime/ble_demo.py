"""Mode démo : tête (IDs 30–33) + expressions + WAV. Pas de marche, pas de ControllerFrame."""

from __future__ import annotations

import random
import threading
import time
from typing import Any

HEAD_JOINTS = ("neck_pitch", "head_pitch", "head_yaw", "head_roll")
HEAD_IDS = (30, 31, 32, 33)
HEAD_KP = 14
WATCHDOG_S = 30.0
PERIOD_DEFAULT_S = 30.0
PERIOD_MIN_S = 5.0
PERIOD_MAX_S = 300.0
NECK_AMP = 0.15
ROLL_MIN = -0.35
ROLL_MAX = 0.07
STEP_S = 0.05
REST_HOLD_S = 0.7
HEAD_PRESETS = ("nod", "look_around", "curious")
LOOPING_PRESETS = ("idle", "idle_mix")

PRESETS: tuple[tuple[str, str], ...] = (
    ("nod", "Hochement"),
    ("look_around", "Regard autour"),
    ("curious", "Curieux"),
    ("idle", "Attente"),
    ("idle_mix", "Attente mix"),
)


def _clamp(value: float, lo: float, hi: float) -> float:
    return max(lo, min(hi, float(value)))


def clamp_period_s(value: Any) -> float:
    try:
        raw = float(value)
    except (TypeError, ValueError):
        raw = PERIOD_DEFAULT_S
    return _clamp(raw, PERIOD_MIN_S, PERIOD_MAX_S)


def reshuffle_mix(last: str | None) -> list[str]:
    names = list(HEAD_PRESETS)
    random.shuffle(names)
    if last is not None and len(names) > 1 and names[0] == last:
        names[0], names[1] = names[1], names[0]
    return names


def clamp_head(cfg: Any, yaw: float, pitch: float, roll: float, neck: float) -> dict[str, float]:
    yaw_lim = cfg.viable_limit("head_yaw")
    if yaw_lim is None:
        yaw_lim = 0.785398
    pitch_lim = cfg.viable_limit("head_pitch")
    if pitch_lim is None:
        pitch_lim = 0.30
    pitch_rest = cfg.rest_software("head_pitch")
    neck_rest = cfg.rest_software("neck_pitch")
    return {
        "head_yaw": _clamp(yaw, -yaw_lim, yaw_lim),
        "head_pitch": _clamp(pitch, pitch_rest - pitch_lim, pitch_rest + pitch_lim),
        "head_roll": _clamp(roll, ROLL_MIN, ROLL_MAX),
        "neck_pitch": _clamp(neck, neck_rest - NECK_AMP, neck_rest + NECK_AMP),
    }


def rest_pose(cfg: Any) -> dict[str, float]:
    return clamp_head(
        cfg,
        0.0,
        cfg.rest_software("head_pitch"),
        0.0,
        cfg.rest_software("neck_pitch"),
    )


def _lerp(a: float, b: float, u: float) -> float:
    return a + (b - a) * u


def _interp_pose(a: dict[str, float], b: dict[str, float], u: float) -> dict[str, float]:
    u = _clamp(u, 0.0, 1.0)
    return {k: _lerp(a[k], b[k], u) for k in HEAD_JOINTS}


def build_timeline(cfg: Any, preset: str) -> list[dict[str, Any]]:
    rest = rest_pose(cfg)
    pr, nr = rest["head_pitch"], rest["neck_pitch"]
    if preset == "nod":
        return [
            {"t_ms": 0, "head": rest, "sound": "happy1.wav", "eyes_blink": True},
            {"t_ms": 700, "head": clamp_head(cfg, 0.0, pr - 0.20, 0.0, nr)},
            {"t_ms": 1400, "head": clamp_head(cfg, 0.0, pr + 0.18, 0.0, nr)},
            {"t_ms": 2100, "head": clamp_head(cfg, 0.0, pr - 0.20, 0.0, nr)},
            {"t_ms": 2800, "head": rest},
            {"t_ms": 3600, "head": rest, "eyes_blink": False},
        ]
    if preset == "look_around":
        return [
            {"t_ms": 0, "head": rest, "sound": "beep1.wav", "eyes_blink": True},
            {"t_ms": 1100, "head": clamp_head(cfg, 0.52, pr, 0.0, nr)},
            {"t_ms": 2200, "head": clamp_head(cfg, -0.52, pr, 0.0, nr)},
            {"t_ms": 3300, "head": rest},
            {"t_ms": 4200, "head": rest, "eyes_blink": False},
        ]
    if preset == "curious":
        return [
            {"t_ms": 0, "head": rest, "sound": "lamp.wav", "projector": True},
            {
                "t_ms": 800,
                "head": clamp_head(cfg, 0.30, pr + 0.15, 0.0, nr),
            },
            {"t_ms": 1800, "head": clamp_head(cfg, 0.30, pr + 0.15, 0.0, nr), "projector": False},
            {"t_ms": 2800, "head": rest},
            {"t_ms": 3600, "head": rest},
        ]
    raise ValueError(preset)


def build_mixed_timeline(cfg: Any) -> list[dict[str, Any]]:
    """Une salve : tête tirée parmi les 3 presets, son / yeux / projecteur indépendants."""
    base = random.choice(HEAD_PRESETS)
    out: list[dict[str, Any]] = []
    for ev in build_timeline(cfg, base):
        item: dict[str, Any] = {"t_ms": ev["t_ms"], "head": dict(ev["head"])}
        out.append(item)
    out[0]["sound"] = random.choice(("happy1.wav", "beep1.wav", "lamp.wav"))
    if random.random() < 0.7:
        out[0]["eyes_blink"] = True
        out[-1]["eyes_blink"] = False
    if random.random() < 0.4:
        out[0]["projector"] = True
        out[max(1, len(out) // 2)]["projector"] = False
    out[0]["antennas"] = "wiggle"
    return out


class DemoMode:
    def __init__(self, sts: Any = None) -> None:
        self.sts = sts
        self.last_state: dict[str, Any] | None = None
        self._lock = threading.Lock()
        self._stop = threading.Event()
        self._thread: threading.Thread | None = None
        self._running = False
        self._preset = ""
        self._phase = "idle"
        self._hwi: Any = None
        self._eyes: Any = None
        self._projector: Any = None
        self._sounds: Any = None
        self._cfg: Any = None
        self._period_s = PERIOD_DEFAULT_S
        self._antennas_busy = False

    @property
    def running(self) -> bool:
        return self._running

    def request(self, obj: dict[str, Any]) -> dict[str, Any]:
        if int(obj.get("v", 0)) != 1:
            return self._ack(False, "status", "", "demo : version inconnue")
        action = str(obj.get("action", ""))
        if action == "list":
            return self._catalog()
        if action == "status":
            return self._emit_state()
        if action == "stop":
            self.stop()
            return self._ack(True, "stop", self._preset, "Démo arrêtée")
        if action == "start":
            preset = str(obj.get("preset", ""))
            ids = {p[0] for p in PRESETS}
            if preset not in ids:
                return self._ack(False, "start", preset, "Preset inconnu")
            with self._lock:
                if self._running:
                    return self._ack(False, "start", preset, "Démo déjà en cours — Stop d’abord")
                self._running = True
                self._preset = preset
                self._phase = "start"
                self._period_s = clamp_period_s(obj.get("period_s", PERIOD_DEFAULT_S))
                self._stop.clear()
            threading.Thread(target=self._run, args=(preset,), name="demo_mode", daemon=True).start()
            if preset in LOOPING_PRESETS:
                return self._ack(
                    True, "start", preset, f"Démo : {preset} · pause {int(self._period_s)} s"
                )
            return self._ack(True, "start", preset, f"Démo : {preset}")
        return self._ack(False, action or "?", "", "Action démo inconnue")

    def stop(self) -> None:
        self._stop.set()

    def _ack(self, accepted: bool, action: str, preset: str, message: str) -> dict[str, Any]:
        state = {
            "type": "demo_ack",
            "v": 1,
            "accepted": accepted,
            "action": action,
            "preset": preset,
            "message": message,
        }
        self.last_state = state
        return state

    def _catalog(self) -> dict[str, Any]:
        state = {
            "type": "demo_catalog",
            "v": 1,
            "presets": [{"id": i, "label": lab} for i, lab in PRESETS],
        }
        self.last_state = state
        return state

    def _emit_state(self, phase: str | None = None) -> dict[str, Any]:
        if phase is not None:
            self._phase = phase
        state = {
            "type": "demo_state",
            "v": 1,
            "running": self._running,
            "preset": self._preset,
            "phase": self._phase,
        }
        self.last_state = state
        return state

    def _run(self, preset: str) -> None:
        self._thread = threading.current_thread()
        try:
            self._play(preset)
        except Exception as e:
            self._ack(False, "start", preset, f"Démo erreur : {e}")
            print(f"[ble_demo] {e}", flush=True)
        finally:
            self._cleanup()
            self._running = False
            self._phase = "idle"
            self._emit_state("idle")
            self._thread = None

    def _play(self, preset: str) -> None:
        from mini_bdx_runtime.duck_config import DuckConfig

        self._cfg = DuckConfig(ignore_default=True)
        if self.sts is not None:
            self.sts.pause()
            time.sleep(0.4)
        self._open_head()
        self._goto(rest_pose(self._cfg))
        if preset in LOOPING_PRESETS:
            self._play_idle(preset)
        else:
            self._emit_state("play")
            self._play_once(build_timeline(self._cfg, preset), watchdog_s=WATCHDOG_S)
            self._emit_state("rest")
            self._hold_rest(REST_HOLD_S)

    def _play_idle(self, preset: str) -> None:
        deck: list[str] = []
        last = ""
        period = self._period_s
        while not self._stop.is_set():
            if preset == "idle_mix":
                if not deck:
                    deck = reshuffle_mix(last or None)
                name = deck.pop(0)
                last = name
                timeline = build_timeline(self._cfg, name)
                timeline[0]["antennas"] = "wiggle"
            else:
                timeline = build_mixed_timeline(self._cfg)
            self._emit_state("play")
            self._play_once(timeline, watchdog_s=12.0)
            self._hold_rest(REST_HOLD_S)
            if self._stop.is_set():
                break
            self._fx_off()
            self._emit_state("wait")
            if self._wait_at_rest(period):
                break
        self._emit_state("rest")
        self._hold_rest(0.3)

    def _hold_rest(self, seconds: float) -> None:
        if self._cfg is None:
            return
        rest = rest_pose(self._cfg)
        t0 = time.monotonic()
        while time.monotonic() - t0 < seconds:
            self._goto(rest)
            time.sleep(STEP_S)

    def _wait_at_rest(self, seconds: float) -> bool:
        """True si Stop pendant l’attente. Réécrit le repos pour ne pas rester sur la dernière salve."""
        if seconds <= 0:
            return self._stop.is_set()
        deadline = time.monotonic() + seconds
        rest = rest_pose(self._cfg)
        while True:
            remain = deadline - time.monotonic()
            if remain <= 0:
                return False
            self._goto(rest)
            if self._stop.wait(min(0.4, remain)):
                return True

    def _play_once(self, timeline: list[dict[str, Any]], watchdog_s: float) -> None:
        t0 = time.monotonic()
        fired: set[int] = set()
        poses = [(int(ev["t_ms"]), ev["head"]) for ev in timeline]
        last_t = poses[-1][0]
        while not self._stop.is_set():
            elapsed = time.monotonic() - t0
            if elapsed >= watchdog_s:
                break
            t_ms = int(elapsed * 1000)
            self._goto(self._pose_at(poses, t_ms))
            for i, ev in enumerate(timeline):
                if i in fired or t_ms < int(ev["t_ms"]):
                    continue
                fired.add(i)
                self._fire_fx(ev)
            if t_ms >= last_t + 200:
                break
            time.sleep(STEP_S)

    def _pose_at(self, poses: list[tuple[int, dict[str, float]]], t_ms: int) -> dict[str, float]:
        if t_ms <= poses[0][0]:
            return poses[0][1]
        for i in range(1, len(poses)):
            t1, p1 = poses[i]
            t0, p0 = poses[i - 1]
            if t_ms <= t1:
                span = max(1, t1 - t0)
                return _interp_pose(p0, p1, (t_ms - t0) / span)
        return poses[-1][1]

    def _open_head(self) -> None:
        from mini_bdx_runtime.rustypot_position_hwi import HWI

        self._hwi = HWI(self._cfg)
        io = self._hwi.io
        enable = getattr(io, "enable_torque", None)
        if callable(enable):
            enable(list(HEAD_IDS))
        self._hwi.io.set_kps(list(HEAD_IDS), [HEAD_KP] * len(HEAD_IDS))

    def _goto(self, pose: dict[str, float]) -> None:
        if self._hwi is None or self._cfg is None:
            return
        clamped = clamp_head(
            self._cfg,
            pose["head_yaw"],
            pose["head_pitch"],
            pose["head_roll"],
            pose["neck_pitch"],
        )
        for name in HEAD_JOINTS:
            self._hwi.set_position(name, clamped[name])

    def _fire_fx(self, ev: dict[str, Any]) -> None:
        sound = ev.get("sound")
        if sound:
            try:
                sounds = self._ensure_sounds()
                if getattr(sounds, "ok", False) and sound in sounds.sounds:
                    sounds.play(sound)
            except Exception as e:
                print(f"[ble_demo] son {e}", flush=True)
        if "eyes_blink" in ev:
            try:
                eyes = self._ensure_eyes()
                if ev["eyes_blink"]:
                    eyes.start_blink()
                else:
                    eyes.set_off()
            except Exception as e:
                print(f"[ble_demo] yeux {e}", flush=True)
        if "projector" in ev:
            try:
                proj = self._ensure_projector()
                want = bool(ev["projector"])
                if bool(getattr(proj, "on", False)) != want:
                    proj.switch()
            except Exception as e:
                print(f"[ble_demo] projecteur {e}", flush=True)
        if ev.get("antennas") == "wiggle":
            self._wiggle_antennas()

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

    def _wiggle_antennas(self) -> None:
        if self._antennas_busy or self._stop.is_set():
            return
        self._antennas_busy = True

        def run() -> None:
            try:
                from mini_bdx_runtime.antennas import Antennas

                ant = Antennas()
                try:
                    ant.oscillate(duration=2.0, frequency=1.0)
                finally:
                    ant.stop()
            except Exception as e:
                print(f"[ble_demo] antennes {e}", flush=True)
            finally:
                self._antennas_busy = False

        threading.Thread(target=run, name="demo_antennas", daemon=True).start()

    def _fx_off(self) -> None:
        try:
            if self._eyes is not None:
                self._eyes.set_off()
        except Exception:
            pass
        try:
            if self._projector is not None and getattr(self._projector, "on", False):
                self._projector.switch()
        except Exception:
            pass

    def _cleanup(self) -> None:
        try:
            if self._cfg is not None and self._hwi is not None:
                self._hold_rest(REST_HOLD_S)
                self._hwi.io.disable_torque(list(HEAD_IDS))
        except Exception as e:
            print(f"[ble_demo] couple tête {e}", flush=True)
        self._fx_off()
        try:
            io = getattr(self._hwi, "io", None) if self._hwi is not None else None
            if io is not None:
                for name in ("close", "disconnect"):
                    fn = getattr(io, name, None)
                    if callable(fn):
                        fn()
                        break
        except Exception:
            pass
        self._hwi = None
        if self.sts is not None:
            self.sts.resume()


def _self_test() -> None:
    from mini_bdx_runtime.duck_config import DuckConfig

    cfg = DuckConfig(config_json_path=None, ignore_default=True)
    cfg.joints_gravity_rest = {"neck_pitch": 0.02, "head_pitch": 0.05}
    cfg.joints_limits_viable = {"head_yaw": 0.785398, "head_pitch": 0.30}
    pose = clamp_head(cfg, 2.0, 2.0, 0.5, 1.0)
    assert pose["head_yaw"] <= 0.785398 + 1e-9
    assert pose["head_pitch"] <= 0.05 + 0.30 + 1e-9
    assert pose["head_roll"] <= ROLL_MAX + 1e-9
    pose2 = clamp_head(cfg, 0.0, 0.05, -1.0, 0.02)
    assert pose2["head_roll"] >= ROLL_MIN - 1e-9
    rest = rest_pose(cfg)
    assert abs(rest["head_pitch"] - 0.05) < 1e-9
    assert clamp_period_s(2) == PERIOD_MIN_S
    assert clamp_period_s(999) == PERIOD_MAX_S
    assert clamp_period_s("nope") == PERIOD_DEFAULT_S
    deck = reshuffle_mix(None)
    assert sorted(deck) == sorted(HEAD_PRESETS)
    for name, _ in PRESETS:
        if name in LOOPING_PRESETS:
            continue
        tl = build_timeline(cfg, name)
        assert tl and tl[0]["head"]["head_pitch"] == rest["head_pitch"]
        for ev in tl:
            h = ev["head"]
            assert abs(h["neck_pitch"] - rest["neck_pitch"]) < 1e-9
            reclamped = clamp_head(cfg, h["head_yaw"], h["head_pitch"], h["head_roll"], h["neck_pitch"])
            for k in HEAD_JOINTS:
                assert abs(h[k] - reclamped[k]) < 1e-9
    for _ in range(8):
        mix = build_mixed_timeline(cfg)
        assert mix and mix[0].get("antennas") == "wiggle"
        for ev in mix:
            h = ev["head"]
            assert abs(h["neck_pitch"] - rest["neck_pitch"]) < 1e-9
            reclamped = clamp_head(cfg, h["head_yaw"], h["head_pitch"], h["head_roll"], h["neck_pitch"])
            for k in HEAD_JOINTS:
                assert abs(h[k] - reclamped[k]) < 1e-9
    demo = DemoMode()
    cat = demo.request({"type": "demo", "v": 1, "action": "list"})
    assert cat["type"] == "demo_catalog" and len(cat["presets"]) == 5
    st = demo.request({"type": "demo", "v": 1, "action": "status"})
    assert st["type"] == "demo_state" and st["running"] is False
    bad = demo.request({"type": "demo", "v": 1, "action": "start", "preset": "nope"})
    assert bad["accepted"] is False
    print("ble_demo self-test OK")


if __name__ == "__main__":
    _self_test()
