#!/usr/bin/env python3
"""Menu principal des mini-outils SSH (réglages / tests).

Les entrées sont **explicites**. Ne pas scanner scripts/.
N'ajouter une ligne dans ENTRIES que sur demande du PO.
"""

from __future__ import annotations

import subprocess
import sys
from pathlib import Path

SCRIPTS_DIR = Path(__file__).resolve().parent

# Ajouter une entrée uniquement quand le PO le demande.
ENTRIES: list[tuple[str, str, list[str]]] = [
    (
        "1",
        "Banc d'expression (yeux, projecteur, HP, antennes)",
        [sys.executable, str(SCRIPTS_DIR / "bdx_expression_test_menu.py")],
    ),
]


def _print_menu() -> None:
    print()
    print("Outils BDX (SSH) — tests et réglages")
    for key, label, _argv in ENTRIES:
        print(f"  {key}  {label}")
    print("  0 / q  quitter")
    print()


def _run(argv: list[str]) -> None:
    print("---")
    completed = subprocess.run(argv, check=False)
    print("---")
    if completed.returncode != 0:
        print(f"Outil terminé (code {completed.returncode}).")


def main() -> int:
    by_key = {key: (label, argv) for key, label, argv in ENTRIES}
    _print_menu()
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
                _print_menu()
                continue
            if choice in by_key:
                _label, argv = by_key[choice]
                _run(argv)
                _print_menu()
                continue
            print("Pas d'entrée pour ce numéro (ajout au coup par coup, pas un scan auto).")
            _print_menu()
    except KeyboardInterrupt:
        print()
    return 0


if __name__ == "__main__":
    sys.exit(main())
