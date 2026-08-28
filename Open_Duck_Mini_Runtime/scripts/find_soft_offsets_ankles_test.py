"""
Variante de find_soft_offsets.py — test sur les chevilles uniquement.

Calibre left_ankle (ID 24) et right_ankle (ID 14) dans duck_config.json.
Toutes les articulations vont d'abord au zéro logiciel (0 rad), comme le script original.

Usage:
  python find_soft_offsets_ankles_test.py
  python find_soft_offsets_ankles_test.py --port /dev/ttyACM0
"""

import argparse
import time

from mini_bdx_runtime.duck_config import DuckConfig
from mini_bdx_runtime.rustypot_position_hwi import HWI

# Ordre : jambe gauche puis jambe droite (doc calibration)
TARGET_JOINTS = ["left_ankle", "right_ankle"]  # IDs 24, 14


def safe_hold_current(hwi: HWI) -> None:
    """Enable low torque while holding present positions (no global move)."""
    ids = list(hwi.joints.values())
    low_kp = [2] * len(ids)
    hwi.io.set_kps(ids, low_kp)
    hwi.io.set_kds(ids, [0] * len(ids))
    time.sleep(0.5)
    present = hwi.io.read_present_position(ids)
    hwi.io.write_goal_position(ids, present)
    time.sleep(0.5)
    print("Motors holding current pose (low torque, Kp=2).")


def move_all_to_zero(hwi: HWI) -> None:
    """Original behaviour: all joints to software zero — can spike bus current."""
    hwi.init_pos = hwi.zero_pos
    hwi.set_kds([0] * len(hwi.joints))
    hwi.turn_on()
    hwi.set_position_all(hwi.zero_pos)
    time.sleep(1)


parser = argparse.ArgumentParser(
    description="Calibrate soft offsets for ankle servos only (IDs 24 and 14)"
)
parser.add_argument(
    "--port",
    default="/dev/ttyACM0",
    help="Serial port for the motor controller (default: /dev/ttyACM0)",
)
parser.add_argument(
    "--move-all-to-zero",
    action="store_true",
    help=(
        "Move all 14 servos to software zero at startup (high current). "
        "Default: hold current pose with low torque only."
    ),
)
args = parser.parse_args()

dummy_config = DuckConfig(config_json_path=None, ignore_default=True)

print("======")
print("Ankle offset test — IDs 24 (left_ankle) and 14 (right_ankle) only")
print(
    "Warning: by default motors HOLD their current pose (low torque). "
    "Use --move-all-to-zero only with stable power and legs clear."
)
print("======")
print("")
input(
    "Press Enter to start. Ctrl+C at any time turns all motors off."
)

hwi = HWI(dummy_config, usb_port=args.port)
joint_names = list(hwi.joints.keys())

if args.move_all_to_zero:
    input(
        "CONFIRM: all 14 servos will move to zero (high current). Press Enter to continue..."
    )
    move_all_to_zero(hwi)
else:
    safe_hold_current(hwi)
    input(
        "When ready to calibrate, all servos will be commanded to zero together "
        "(one time, high current). Press Enter to continue, Ctrl+C to abort..."
    )
    hwi.set_position_all(hwi.zero_pos)
    time.sleep(1)

try:
    for joint_name in TARGET_JOINTS:
        if joint_name not in hwi.joints:
            print(f"ERROR: unknown joint {joint_name!r}")
            continue

        joint_id = hwi.joints[joint_name]
        joint_index = joint_names.index(joint_name)
        ok = False

        print("")
        print(f"=== Calibrating {joint_name} (ID {joint_id}) ===")

        while not ok:
            hwi.set_position_all(hwi.zero_pos)
            time.sleep(0.5)

            current_pos = hwi.get_present_positions()[joint_index]
            if current_pos is None:
                print("Could not read position, retrying...")
                continue

            hwi.io.disable_torque([joint_id])
            input(
                f"{joint_name} torque off. Move it to the desired zero position, "
                "then press Enter to confirm the offset."
            )

            new_pos = hwi.get_present_positions()[joint_index]
            offset = new_pos - current_pos
            print(f" ---> Offset: {offset}")
            hwi.joints_offsets[joint_name] = offset

            input(
                "Press Enter to move back to zero with offset applied."
            )
            hwi.set_position_all(hwi.zero_pos)
            time.sleep(0.5)
            hwi.io.enable_torque([joint_id])

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
