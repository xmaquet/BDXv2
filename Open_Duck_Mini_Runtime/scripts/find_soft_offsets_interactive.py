"""
Calibration interactive des soft offsets (duck_config.json → joints_offsets).

Choisir un ID servo au début de chaque cycle ; le script annonce le nom du joint.
Torque OFF sur les 14 servos pendant l'ajustement manuel ; couple bref sur le
servo ciblé uniquement pour la vérification software 0.

L'offset = position brute bus (rad) à la pose manuelle. Valeurs proches de 0
sont normales si le zéro mécanique a déjà été réglé via Feetech SCServo Debug.

Usage:
  python find_soft_offsets_interactive.py
  python find_soft_offsets_interactive.py --port /dev/ttyACM0
"""

from __future__ import annotations

import argparse
import time

from mini_bdx_runtime.duck_config import DuckConfig
from mini_bdx_runtime.rustypot_position_hwi import HWI

VERIFY_KP = 32


def all_servo_ids(hwi: HWI) -> list[int]:
    return list(hwi.joints.values())


def id_to_name(hwi: HWI) -> dict[int, str]:
    return {sid: name for name, sid in hwi.joints.items()}


def joint_index(hwi: HWI, joint_name: str) -> int:
    return list(hwi.joints.keys()).index(joint_name)


def print_servo_map(hwi: HWI) -> None:
    print("Servos connus (ID → joint) :")
    for sid in sorted(id_to_name(hwi)):
        print(f"  {sid:2d} → {id_to_name(hwi)[sid]}")
    print("")


def read_raw_position(hwi: HWI, joint_id: int) -> float:
    raw = hwi.io.read_present_position([joint_id])
    return float(raw[0] if isinstance(raw, (list, tuple)) else raw)


def disable_all_torque(hwi: HWI) -> None:
    ids = all_servo_ids(hwi)
    hwi.io.disable_torque(ids)
    print(f"Torque OFF sur les {len(ids)} servos.")


def verify_software_zero(hwi: HWI, joint_name: str, joint_id: int) -> float:
    """Couple ON sur un seul servo, commande software 0, puis OFF."""
    hwi.io.enable_torque([joint_id])
    try:
        hwi.io.set_kps([joint_id], [VERIFY_KP])
        hwi.io.set_kds([joint_id], [0])
        hwi.set_position(joint_name, 0.0)
        time.sleep(1.2)
        idx = joint_index(hwi, joint_name)
        positions = hwi.get_present_positions()
        if positions is None:
            raise RuntimeError("Could not read present positions")
        return float(positions[idx])
    finally:
        hwi.io.disable_torque([joint_id])


def prompt_servo_id(hwi: HWI) -> int | None:
    """Retourne l'ID choisi, ou None pour quitter."""
    id_map = id_to_name(hwi)
    while True:
        raw = input("ID servo à calibrer (q = terminer) : ").strip().lower()
        if raw in ("q", "quit", "exit"):
            return None
        try:
            sid = int(raw)
        except ValueError:
            print("  Entrez un nombre entier (ex. 14, 24) ou q.")
            continue
        if sid not in id_map:
            print(f"  ID {sid} inconnu. IDs valides : {sorted(id_map)}")
            continue
        print(f"  → {id_map[sid]} (ID {sid})")
        return sid


def calibrate_joint(hwi: HWI, joint_name: str, joint_id: int) -> float | None:
    """Cycle complet ; retourne l'offset retenu ou None si abandonné."""
    ok = False
    offset = 0.0

    while not ok:
        disable_all_torque(hwi)

        input(
            f"Tous servos OFF. Place {joint_name} (ID {joint_id}) au zéro mécanique, "
            "puis Enter pour enregistrer l'offset."
        )

        raw_manual = read_raw_position(hwi, joint_id)
        hwi.joints_offsets[joint_name] = raw_manual
        print(f"  Offset brut (rad bus) : {raw_manual:.8f}")

        input(
            f"Enter : couple ON sur {joint_name} seul, vérif software 0, puis OFF."
        )
        sw_after = verify_software_zero(hwi, joint_name, joint_id)
        disable_all_torque(hwi)
        print(f"  Position software après cmd 0 : {sw_after:.8f} (attendu ~0)")

        res = input("OK ? (Y/n) : ").lower()
        if res in ("y", ""):
            offset = raw_manual
            print(f"  Conservé pour {joint_name}.")
            ok = True
        else:
            print("  Nouvel essai…")
            hwi.joints_offsets[joint_name] = 0.0

    return offset


parser = argparse.ArgumentParser(
    description="Calibrate soft offsets joint par joint (choix ID interactif)"
)
parser.add_argument(
    "--port",
    default="/dev/ttyACM0",
    help="Port série contrôleur moteurs (défaut : /dev/ttyACM0)",
)
args = parser.parse_args()

dummy_config = DuckConfig(config_json_path=None, ignore_default=True)

print("======")
print("Calibration soft offsets — choix ID par cycle")
print(
    "Couple OFF sur tous les servos pendant la manip. "
    "Vérification sur le servo choisi uniquement."
)
print("======")
print("")

input("Enter pour démarrer. Ctrl+C coupe tous les moteurs.")

hwi = HWI(dummy_config, usb_port=args.port)
calibrated: dict[str, float] = {}

try:
    print_servo_map(hwi)
    hwi.io.read_present_position(all_servo_ids(hwi))
    disable_all_torque(hwi)

    while True:
        joint_id = prompt_servo_id(hwi)
        if joint_id is None:
            break

        joint_name = id_to_name(hwi)[joint_id]
        print("")
        print(f"=== {joint_name} (ID {joint_id}) ===")

        offset = calibrate_joint(hwi, joint_name, joint_id)
        if offset is not None:
            calibrated[joint_name] = offset

        print("")

    print("======")
    if calibrated:
        print("Offsets de cette session — copier dans ~/duck_config.json → joints_offsets :")
        for name, value in calibrated.items():
            print(f'    "{name}": {value:.8f},')
    else:
        print("Aucun offset enregistré cette session.")
    print("======")

except KeyboardInterrupt:
    print("\nInterrompu — moteurs OFF.")
finally:
    hwi.turn_off()
