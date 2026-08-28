"""
Variante de find_soft_offsets.py — test sur les chevilles uniquement.

Calibre left_ankle (ID 24) et right_ankle (ID 14) dans duck_config.json.
Seules les chevilles ciblées bougent ; les 12 autres servos restent à leur
position actuelle (évite le pic de courant / disjonction BMS).

L'offset enregistré est la position **brute** (rad bus) de la pose manuelle :
software 0 → goal bus = offset. Ne pas utiliser un delta software, car le
servo n'a pas forcément atteint le zéro logiciel avant l'ajustement à la main.

Usage:
  python find_soft_offsets_ankles_test.py
  python find_soft_offsets_ankles_test.py --port /dev/ttyACM0
"""

from __future__ import annotations

import argparse
import time

from mini_bdx_runtime.duck_config import DuckConfig
from mini_bdx_runtime.rustypot_position_hwi import HWI

# Ordre : jambe gauche puis jambe droite (doc calibration)
TARGET_JOINTS = ["left_ankle", "right_ankle"]  # IDs 24, 14
VERIFY_KP = 32


def joint_index(hwi: HWI, joint_name: str) -> int:
    return list(hwi.joints.keys()).index(joint_name)


def read_raw_position(hwi: HWI, joint_id: int) -> float:
    raw = hwi.io.read_present_position([joint_id])
    return float(raw[0] if isinstance(raw, (list, tuple)) else raw)


def read_present_goals(hwi: HWI) -> dict[str, float]:
    positions = hwi.get_present_positions()
    if positions is None:
        raise RuntimeError("Could not read present positions")
    names = list(hwi.joints.keys())
    return {name: float(positions[i]) for i, name in enumerate(names)}


def hold_all_at_present(hwi: HWI, goals: dict[str, float] | None = None) -> dict[str, float]:
    """Command all joints to their present positions (no net motion)."""
    if goals is None:
        goals = read_present_goals(hwi)
    hwi.set_position_all(goals)
    return goals


def safe_hold_current(hwi: HWI) -> dict[str, float]:
    """Enable low torque while holding present positions (no global move)."""
    ids = list(hwi.joints.values())
    low_kp = [2] * len(ids)
    hwi.io.set_kps(ids, low_kp)
    hwi.io.set_kds(ids, [0] * len(ids))
    time.sleep(0.5)
    present = hwi.io.read_present_position(ids)
    hwi.io.write_goal_position(ids, present)
    time.sleep(0.5)
    goals = read_present_goals(hwi)
    print("Motors holding current pose (low torque, Kp=2). Only target ankles will move.")
    return goals


def command_software_zero(hwi: HWI, joint_name: str, joint_id: int) -> float:
    """Command software 0 on one ankle (bus goal = offset) and return software reading."""
    hwi.io.set_kps([joint_id], [VERIFY_KP])
    hwi.set_position(joint_name, 0.0)
    time.sleep(1.2)
    idx = joint_index(hwi, joint_name)
    positions = hwi.get_present_positions()
    if positions is None:
        raise RuntimeError("Could not read present positions")
    return float(positions[idx])


parser = argparse.ArgumentParser(
    description="Calibrate soft offsets for ankle servos only (IDs 24 and 14)"
)
parser.add_argument(
    "--port",
    default="/dev/ttyACM0",
    help="Serial port for the motor controller (default: /dev/ttyACM0)",
)
args = parser.parse_args()

dummy_config = DuckConfig(config_json_path=None, ignore_default=True)

print("======")
print("Ankle offset test — IDs 24 (left_ankle) and 14 (right_ankle) only")
print(
    "Place each ankle manually at mechanical zero (torque off). "
    "Offset = raw bus position at that pose; then software 0 should match it."
)
print("======")
print("")
input(
    "Press Enter to start. Ctrl+C at any time turns all motors off."
)

hwi = HWI(dummy_config, usb_port=args.port)
hold_goals = safe_hold_current(hwi)

try:
    for joint_name in TARGET_JOINTS:
        if joint_name not in hwi.joints:
            print(f"ERROR: unknown joint {joint_name!r}")
            continue

        joint_id = hwi.joints[joint_name]
        ok = False

        print("")
        print(f"=== Calibrating {joint_name} (ID {joint_id}) ===")

        while not ok:
            hold_goals = hold_all_at_present(hwi, hold_goals)
            time.sleep(0.3)

            hwi.io.disable_torque([joint_id])
            input(
                f"{joint_name} torque off. Move it to mechanical zero by hand, "
                "then press Enter to record the offset."
            )

            raw_manual = read_raw_position(hwi, joint_id)
            hwi.joints_offsets[joint_name] = raw_manual
            print(f" ---> Offset (raw bus rad): {raw_manual:.4f}")

            input(
                "Press Enter to re-enable torque and command software zero "
                "(should return to your manual zero)."
            )
            hwi.io.enable_torque([joint_id])
            sw_after = command_software_zero(hwi, joint_name, joint_id)
            print(f"     Software position after zero cmd: {sw_after:.4f} (expect ~0)")

            hold_goals = read_present_goals(hwi)

            res = input("Is that ok? (Y/n): ").lower()
            if res in ("y", ""):
                print(f"Ok, keeping offset for {joint_name}")
                ok = True
            else:
                print("Retrying...")
                hwi.joints_offsets[joint_name] = 0.0

    print("")
    print("Done!")
    print("Copy into ~/duck_config.json -> joints_offsets:")
    for name in TARGET_JOINTS:
        print(f'    "{name}": {hwi.joints_offsets[name]:.6f},')

except KeyboardInterrupt:
    print("\nInterrupted — turning motors off.")
    hwi.turn_off()
