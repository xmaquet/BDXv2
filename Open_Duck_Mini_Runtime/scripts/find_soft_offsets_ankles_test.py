"""Redirige vers find_soft_offsets_interactive.py (remplace l'ancien test chevilles)."""

from pathlib import Path
import runpy

runpy.run_path(
    str(Path(__file__).with_name("find_soft_offsets_interactive.py")),
    run_name="__main__",
)
