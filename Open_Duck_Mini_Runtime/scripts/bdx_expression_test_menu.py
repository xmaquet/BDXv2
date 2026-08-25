#!/usr/bin/env python3
"""Banc SSH des accessoires d'expression (D-016).

Banc, pas démo salon : le matériel est forcé. ~/duck_config.json et
expression_features ne sont pas lus.

Lot 1 : entrée 3 (projecteur). Les autres numéros restent « pas encore ».
"""

from __future__ import annotations

import sys


MENU = """
Banc d'expression BDXv2 (SSH) — GPIO forcé, duck_config ignoré
  1  Yeux         allumer / éteindre (fixe)
  2  Yeux         clignotement marche / arrêt
  3  Projecteur   allumer / éteindre
  4  Haut-parleur jouer un son
  5  Antennes     oscillation ~2 s puis neutre
  0 / q           quitter (GPIO / PWM relâchés)
"""

NOT_YET = "Pas encore (lot suivant)."


def _fail_not_on_robot(exc: BaseException) -> None:
    print("À lancer sur le robot (Pi Zero 2W).")
    print(f"CircuitPython / board introuvable ou inutilisable : {exc}")


def _load_projector():
    try:
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
    try:
        return Projector()
    except Exception as exc:
        _fail_not_on_robot(exc)
        sys.exit(1)


def _cleanup(projector) -> None:
    if projector is None:
        return
    try:
        projector.stop()
    except Exception as exc:
        print(f"Nettoyage projecteur : {exc}")


def main() -> int:
    print(MENU)
    projector = _load_projector()
    print("Projecteur : OFF (D25)")

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
            if choice in ("1", "2", "4", "5"):
                print(NOT_YET)
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
        _cleanup(projector)
        print("GPIO relâché. Au revoir.")

    return 0


if __name__ == "__main__":
    sys.exit(main())
