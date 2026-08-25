# Prochain travail

Roadmap révisée (2026-08-24, PO). Le lot 1 « accessoires sans marche » est **remplacé** par la séquence ci-dessous.

## Lot 0 — Monorepo BDXv2

**Statut :** fait. Dépôt : [https://github.com/xmaquet/BDXv2](https://github.com/xmaquet/BDXv2)

---

## Parallèle — Programmation STS3215

**Statut :** en cours. Guide : `docs/hardware/sts3215-scservo-debug.md`.

Un servo à la fois. IDs : voir `Open_Duck_Mini/docs/CONFIGURE_MOTORS_FR.md`. Baudrate runtime : **1 000 000**. ID usine typique : **1**.

Hors périmètre : oreilles / antennes (PWM, pas STS3215).

---

## Lot 1 — Réinstall OS Pi Zero 2W

**Statut :** fait (2026-08-25). OS Lite 64-bit flashé ; SSH écoute ; user `bdxv2`, hostname `bdxv2`. Prompt : `docs/hardware/PROMPT-agent-infra-pi.md`.

**Objectif :** Pi Zero 2W avec OS neuf, SSH et réseau joignables par l’agent (D-013).

**Périmètre :** image, flash, utilisateur, Wi‑Fi/SSH, hostname.

**Hors périmètre :** runtime Python, BLE, app Android, marche.

**Critère :** l’agent peut ouvrir une session SSH.

---

## Lot 2 — Script d’install complet

**Statut :** script adapté localement (2026-08-25), **pas encore exécuté** sur le Pi.

**Objectif :** un script lançable **après** l’OS, qui installe tout le nécessaire BDXv2 sur le Pi (D-012).

**Périmètre :** paquets système, venv, runtime, extras BLE / hardware / contrôle, `duck_config.json`, I2C, Bluetooth, groupes.

**Hors périmètre :** finalisation UI Android, mode normal.

**Canon :** `Open_Duck_Mini_Runtime/install.sh` (cible `xmaquet/BDXv2` branche `main`). Les scripts `scripts/bdx_full_install.sh` et `scripts/install_bdx_runtime.sh` délèguent.

---

## Lot 3 — BLE + mode test

**Statut :** à faire dans un **Agent chat dédié**. Prompt : `docs/hardware/PROMPT-agent-android-ble.md`. Décision **D-015**.

**Objectif :** app Android (APK) connectée en BLE au Pi ; commandes **dans les deux sens** ; l’opérateur déclenche **une à une** les fonctions (yeux, projecteur, HP, antennes).

**Périmètre :** contrat `protocol.md`, `RobotBlePlugin`, `ble_gatt_server`, UI mode test, GPIO HAT 2N2222.

**Hors périmètre :** marche, mode normal, **vidéo Picam** (phase B du même agent, plus tard).

**Critère :** chaque accessoire v1 observable sur le robot réel, sans lancer la marche (D-004, D-007, D-010).

---

## Parallèle — Banc SSH fonctions virtuelles

**Statut :** lots 1–3 **validés** (2026-08-25, PO) — projecteur `3`, yeux `1`/`2`, HP `4` (MAX98357A). Suite : antennes (`5`). Prompt : `docs/hardware/PROMPT-agent-ssh-test-menu.md`. Décision **D-016**.

**Objectif :** menu texte en SSH qui lance des mini-scripts d’accessoires, **hors UI**, **hors marche**, **hors STS3215**.

**Périmètre :** yeux, projecteur, HP, antennes PWM ; réutiliser les modules runtime.

**Hors périmètre :** Feetech, ONNX, APK, BLE, caméra.

---

## Lot 4 — Mode normal

**Objectif :** expressions **en parallèle de la marche**, comportement des scripts initiaux (`v2_rl_walk_mujoco.py` : X projecteur, B son, LT/RT antennes, yeux).

**Hors périmètre :** caméra, micro, chorégraphies distinctes du script d’origine.

---

Lot 1 posé. Lot 2 : script prêt, attente go PO pour exécution SSH. Fil servos en parallèle.
