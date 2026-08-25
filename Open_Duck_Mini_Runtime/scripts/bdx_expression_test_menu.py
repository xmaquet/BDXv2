#!/usr/bin/env python3
"""Banc SSH des accessoires d'expression (D-016).

Banc, pas démo salon : le matériel est forcé. ~/duck_config.json et
expression_features ne sont pas lus.

Lots : projecteur (3), yeux (1/2), HP (4), antennes (5).
"""

from __future__ import annotations

import sys


MENU = """
Banc d'expression BDXv2 (SSH) — GPIO forcé, duck_config ignoré
  1  Yeux         allumer / éteindre (fixe)
  2  Yeux         clignotement marche / arrêt
  3  Projecteur   allumer / éteindre
  4  Haut-parleur jouer un son
  5  Antennes     oscillation ~2 s puis PWM relâché
  6  Antennes     90° puis PWM relâché (réglage des lobes)
  0 / q           quitter (GPIO / PWM relâchés)
"""


def _fail_not_on_robot(exc: BaseException) -> None:
    print("À lancer sur le robot (Pi Zero 2W).")
    print(f"CircuitPython / board introuvable ou inutilisable : {exc}")


def _load_hardware():
    try:
        from mini_bdx_runtime.eyes import Eyes
        from mini_bdx_runtime.projector import Projector
    except ImportError as exc:
        missing = getattr(exc, "name", "") or ""
        text = str(exc)
        if missing in ("board", "digitalio") or "board" in text or "digitalio" in text:
            _fail_not_on_robot(exc)
        else:
            print("Package mini_bdx_runtime introuvable. Activer le venv du runtime :")
            print("  cd ~/BDXv2/Open_Duck_Mini_Runtime && source .venv/bin/activate")
            print(f"Détail : {exc}")
        sys.exit(1)

    projector = None
    eyes = None
    try:
        projector = Projector()
        eyes = Eyes(auto_start=False)
        sounds = _load_sounds()
        return projector, eyes, sounds
    except Exception as exc:
        if eyes is not None:
            try:
                eyes.stop()
            except Exception:
                pass
        if projector is not None:
            try:
                projector.stop()
            except Exception:
                pass
        _fail_not_on_robot(exc)
        sys.exit(1)


def _load_sounds():
    try:
        from mini_bdx_runtime.sounds import Sounds, default_assets_directory
    except ImportError as exc:
        print(f"Haut-parleur indisponible : {exc}")
        return None
    sounds = Sounds(volume=1.0, sound_directory=default_assets_directory())
    if not sounds.ok:
        print("Haut-parleur : mixer ou WAV indisponible.")
    return sounds


def _pulse_antennas_90() -> None:
    try:
        from mini_bdx_runtime.antennas import Antennas
    except ImportError as exc:
        print(f"Antennes indisponibles : {exc}")
        return
    antennas = Antennas()
    try:
        print(f"Antennes : consigne 90° ({antennas.backend})…")
        antennas.set_center()
        print("Antennes : 90°, PWM relâché. Tu peux poser les lobes.")
    finally:
        antennas.stop()


def _run_antenna_wiggle() -> None:
    try:
        from mini_bdx_runtime.antennas import Antennas
    except ImportError as exc:
        print(f"Antennes indisponibles : {exc}")
        return
    antennas = Antennas()
    try:
        print(f"Antennes : oscillation 2 s ({antennas.backend})…")
        antennas.oscillate(duration=2.0, frequency=1.0)
        print("Antennes : neutre, PWM relâché")
    finally:
        antennas.stop()


def _cleanup(projector, eyes, sounds) -> None:
    if sounds is not None:
        try:
            sounds.stop()
        except Exception as exc:
            print(f"Nettoyage son : {exc}")
    if eyes is not None:
        try:
            eyes.stop()
        except Exception as exc:
            print(f"Nettoyage yeux : {exc}")
    if projector is not None:
        try:
            projector.stop()
        except Exception as exc:
            print(f"Nettoyage projecteur : {exc}")


def main() -> int:
    print(MENU)
    projector, eyes, sounds = _load_hardware()
    print("Projecteur : OFF (D25)")
    print("Yeux : OFF (D24 / D23)")
    if sounds is not None and sounds.ok:
        print(f"Haut-parleur : {len(sounds.sounds)} WAV chargés")
    else:
        print("Haut-parleur : indisponible")
    print("Antennes : PWM coupé hors geste (5 / 6)")

    try:
        while True:
            try:
                choice = input("Choix : ").strip().lower()
            except EOFError:
                print()
                break

            if choice in ("0", "q"):
                break
            if choice == "":
                print(MENU)
                continue
            if choice == "5":
                _run_antenna_wiggle()
                continue
            if choice == "6":
                _pulse_antennas_90()
                continue
            if choice == "4":
                if sounds is None or not sounds.ok:
                    print("Haut-parleur indisponible.")
                else:
                    sounds.play_random_sound()
                continue
            if choice == "1":
                if eyes.steady_on and not eyes.is_blinking():
                    eyes.set_off()
                    print("Yeux : OFF")
                else:
                    eyes.set_on()
                    print("Yeux : ON (fixe)")
                continue
            if choice == "2":
                if eyes.is_blinking():
                    eyes.set_off()
                    print("Yeux : clignotement OFF")
                else:
                    eyes.start_blink()
                    print("Yeux : clignotement ON")
                continue
            if choice == "3":
                projector.switch()
                etat = "ON" if projector.on else "OFF"
                print(f"Projecteur : {etat}")
                continue

            print("Entrée inconnue. 0 ou q pour quitter.")
            print(MENU)
    except KeyboardInterrupt:
        print()
    finally:
        _cleanup(projector, eyes, sounds)
        print("GPIO relâché. Au revoir.")

    return 0


if __name__ == "__main__":
    sys.exit(main())
