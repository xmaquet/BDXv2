# Prochain travail

Roadmap révisée (2026-08-24, PO). Point 2026-08-29.

## Lot 0 — Monorepo BDXv2

**Statut :** fait. [https://github.com/xmaquet/BDXv2](https://github.com/xmaquet/BDXv2)

---

## Parallèle — STS3215

**Statut :** IDs 10–14, 20–24, 30–33 **déclarés programmés** (2026-08-26). Bus **lu sur robot** (2026-08-28) : 14/14, ~7,7 V. Guide : `docs/hardware/sts3215-scservo-debug.md`.

**Offsets :** script interactif `Open_Duck_Mini_Runtime/scripts/find_soft_offsets_interactive.py` **écrit**. Calibration **14 axes non déclarée faite**.

Hors périmètre : oreilles / antennes (PWM).

---

## Lot 1 — Réinstall OS Pi Zero 2W

**Statut :** fait (2026-08-25). OS Lite 64-bit ; SSH ; user / hostname `bdxv2`.

---

## Lot 2 — Script d’install complet

**Statut :** `pi-setup/` créé ; **pas exécuté** (D-020).

**Canon :** `pi-setup/install.sh`. Plus tard : y absorber `enable_halt_sudo.sh`, `enable_wifi_sudo.sh`, `enable_ble_robot_boot.sh`.

---

## Lot 3 — App Android BLE native

**Statut :** **tenu** pour 3a, Tests, halt, hello, accueil STS. **D-022** UI native. **D-023** Wi‑Fi BLE **sur le banc**. Santé Pi (Monitoring, `{type:sys}`) **codée** APK **1.3.22**. Prompt : `docs/hardware/PROMPT-agent-android-ble.md`.

**Reste lot 3 :** valider Wi‑Fi BLE **sur robot** (`git pull` + `enable_wifi_sudo.sh` + restart GATT).

**Hors périmètre immédiat :** Xbox, vidéo (D-022), **sons D-024** (ne pas coder).

---

## Plus tard — Sons D-024 (todo, ne pas démarrer)

**Statut :** décidé, **pas autorisé à coder**.

- Jouer `WIFI_OKAY` / `WIFI_PROBLEM` / `ENERGY_PROBLEM` sur les événements correspondants.
- `random_sounds/` : hello **aléatoire** + mimiques + yeux, seulement après une **inactivité durable** (seuil à définir alors).
- Préalable technique : charger le sous-dossier (aujourd’hui `sounds.py` ne le fait pas). Commiter les WAV quand le PO le demandera.

---

## Parallèle — Banc SSH

**Statut :** **clos** (2026-08-25).

---

## Lot 4 — Commandes hors Tests (dont parallèle marche)

**Statut :** pas démarré. **Pas** de manette Xbox (D-018).

---

**Prochaine étape recommandée :** déployer et valider le Wi‑Fi BLE (D-023) sur le Pi quand il est joignable. **Pas** le lot 2. **Pas** D-024.
