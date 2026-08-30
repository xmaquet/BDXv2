# Prochain travail

Roadmap révisée (2026-08-24, PO). Point 2026-08-29.

## Lot 0 — Monorepo BDXv2

**Statut :** fait. [https://github.com/xmaquet/BDXv2](https://github.com/xmaquet/BDXv2)

---

## Parallèle — STS3215

**Statut :** IDs 10–14, 20–24, 30–33 **déclarés programmés** (2026-08-26). Bus **lu sur robot** (2026-08-28) : 14/14, ~7,7 V. Guide : `docs/hardware/sts3215-scservo-debug.md`.

**Offsets :** `joints_offsets` **tous à 0** (Pi `~/duck_config.json` = modèle GitHub). **Volontaire** : zéro méca posé dans Feetech. Script interactif disponible seulement si un axe dérive.

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

**Hors périmètre immédiat :** Xbox, vidéo (D-022), reste D-024 (énergie, idle).

---

## Plus tard — Sons D-024 (suite)

**Statut :** init Wi‑Fi **faite**. Le reste : **pas autorisé à coder** maintenant.

- `WIFI_OKAY` / `WIFI_PROBLEM` à l’**init hello** : **fait** (2026-08-30). Reste : les rejouer sur join / perte de lien **en cours de session**.
- `ENERGY_PROBLEM` sur événement tension.
- `random_sounds/` : hello **aléatoire** + mimiques + yeux, seulement après une **inactivité durable** (seuil à définir alors).
- Préalable technique : charger le sous-dossier (aujourd’hui `sounds.py` ne le fait pas). Commiter les WAV quand le PO le demandera.

---

## Parallèle — Banc SSH

**Statut :** **clos** (2026-08-25).

---

## Lot 4 — Commandes hors Tests (dont parallèle marche)

**Statut :** pas démarré. **Pas** de manette Xbox (D-018).

---

**Prochaine étape recommandée :** déployer et valider le Wi‑Fi BLE (D-023) sur le Pi (les WAV `WIFI_*` doivent être sur le robot pour l’init). **Pas** le lot 2. D-024 : init Wi‑Fi **faite** ; le reste (énergie, idle) **pas maintenant**.
