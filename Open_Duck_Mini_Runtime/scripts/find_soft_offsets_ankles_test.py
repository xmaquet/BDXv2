"""
Variante de find_soft_offsets.py — test sur les chevilles uniquement.

Calibre left_ankle (ID 24) et right_ankle (ID 14) dans duck_config.json.
Seules les chevilles ciblées bougent ; les 12 autres servos restent à leur
position actuelle (évite le pic de courant / disjonction BMS).

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


def joint_index(hwi: HWI, joint_name: str) -> int:
    return list(hwi.joints.keys()).index(joint_name)


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


def move_joint_to_zero(hwi: HWI, joint_name: str, goals: dict[str, float] | None = None) -> None:
    """Move one ankle to software zero; all other joints keep their current goal."""
    if goals is None:
        goals = read_present_goals(hwi)
    goals = dict(goals)
    goals[joint_name] = 0.0
    hwi.set_position_all(goals)


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
    "Only the ankle being calibrated moves to zero. "
    "The other 13 servos stay at their current position."
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
        idx = joint_index(hwi, joint_name)
        ok = False

        print("")
        print(f"=== Calibrating {joint_name} (ID {joint_id}) ===")

        while not ok:
            hold_goals = hold_all_at_present(hwi, hold_goals)
            time.sleep(0.3)
            input(
                f"Press Enter to move ONLY {joint_name} to software zero "
                f"(other servos stay put)..."
            )
            move_joint_to_zero(hwi, joint_name, hold_goals)
            time.sleep(0.8)
            hold_goals = read_present_goals(hwi)

            current_pos = hwi.get_present_positions()[idx]
            if current_pos is None:
                print("Could not read position, retrying...")
                continue

            hwi.io.disable_torque([joint_id])
            input(
                f"{joint_name} torque off. Move it to the desired zero position, "
                "then press Enter to confirm the offset."
            )

            new_pos = hwi.get_present_positions()[idx]
            offset = new_pos - current_pos
            print(f" ---> Offset: {offset}")
            hwi.joints_offsets[joint_name] = offset
            hold_goals[joint_name] = float(new_pos)

            input(
                "Press Enter to move this ankle back to zero with offset applied."
            )
            move_joint_to_zero(hwi, joint_name, hold_goals)
            time.sleep(0.5)
            hwi.io.enable_torque([joint_id])
            hold_goals = read_present_goals(hwi)

            res = input("Is that ok? (Y/n): ").lower()
            if res in ("y", ""):
                print(f"Ok, keeping offset for {joint_name}")
                ok = True
            else:
                print("Retrying...")
                hwi.joints_offsets[joint_name] = 0

    print("")
    print("Done!")
    print("Copy into ~/duck_config.json -> joints_offsets:")
    for name in TARGET_JOINTS:
        print(f'    "{name}": {hwi.joints_offsets[name]},')

except KeyboardInterrupt:
    print("\nInterrupted — turning motors off.")
    hwi.turn_off()
