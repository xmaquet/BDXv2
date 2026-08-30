# Prochain travail

Roadmap révisée (2026-08-24, PO). Point 2026-08-30.

## Lot 0 — Monorepo BDXv2

**Statut :** fait. [https://github.com/xmaquet/BDXv2](https://github.com/xmaquet/BDXv2) — `main` @ `124aeb1`.

---

## Parallèle — STS3215

**Statut :** IDs 10–14, 20–24, 30–33 **déclarés programmés**. Bus **lu** (14/14, ~7,7 V). Zéro méca **Feetech** ; `joints_offsets` = **0**.

Hors périmètre : oreilles / antennes (PWM).

---

## Lot 1 — Réinstall OS Pi Zero 2W

**Statut :** fait (2026-08-25). SSH `bdxv2` ; IP récente **`192.168.10.132`**.

---

## Lot 2 — Script d’install complet

**Statut :** `pi-setup/` créé ; **pas exécuté** (D-020).

**Canon :** `pi-setup/install.sh`. Plus tard : y absorber `enable_halt_sudo.sh`, `enable_wifi_sudo.sh`, `enable_ble_robot_boot.sh`.

**Cible (D-026), pas maintenant :** migrer le runtime BDXv2 **sous** `pi-setup/` pour que l’install n’ait plus besoin d’aller chercher le paquet dans `Open_Duck_Mini_Runtime/`. Les arbres d’origine restent en référence.

---

## Lot 3 — App Android BLE native

**Statut :** **tenu** : 3a, Tests, halt, hello, accueil STS, Monitoring, Wi‑Fi banc (D-023). Hello Wi‑Fi init **codé**, **commité**, **pull Pi** (`124aeb1`). APK **1.3.23** (Piloter : Ant. G / Tempo / Ant. D, sticks alignés). Prompt : `docs/hardware/PROMPT-agent-android-ble.md`.

**Reste lot 3 :** relancer `bdx-ble-robot` (SIGKILL) pour **entendre** BLE puis Wi‑Fi OK/échec. Valider à l’oreille sur robot.

**Hors périmètre immédiat :** Xbox, vidéo (D-022), reste D-024 (énergie, idle, Wi‑Fi en session), **D-025** (liste de robots).

---

## Plus tard — Choix de robot BLE (D-025)

**Statut :** décidé, **pas autorisé à coder** maintenant.

- Liste des appareils qui annoncent le service BDX (noms `--name`).
- Choix dans l’app, puis **mémoire de l’adresse** Bluetooth.
- Noms uniques par machine (aujourd’hui tous = `Open Duck Mini`).
- Pas de pairing PIN / pas d’UUID par robot.

---

## Plus tard — Sons D-024 (suite)

**Statut :** init Wi‑Fi **faite** (code + WAV sur GitHub et sur le Pi). Le reste : **pas autorisé à coder** maintenant.

- Rejouer `WIFI_OKAY` / `WIFI_PROBLEM` sur join / perte de lien **en session**.
- `ENERGY_PROBLEM` sur événement tension.
- `random_sounds/` : hello aléatoire + mimiques + yeux après **inactivité durable** (seuil à définir). Chargeur récursif requis.

---

## Parallèle — Banc SSH

**Statut :** **clos** (2026-08-25).

---

## Lot 4 — Commandes hors Tests (dont parallèle marche)

**Statut :** **découverte**, pas de code. Prompt : `docs/hardware/PROMPT-agent-marche.md`. **Pas** de manette Xbox (D-018).

L’écran **Piloter** envoie déjà `ControllerFrame` v1 (`protocol.md`). La consommation par `RLWalk` / GATT et le **moment d’activation** (init vs UI) sont **à trancher par le PO** après explication.

**IMU :** découvrir les scripts existants (`raw_imu.py` = marche ; `calibrate_imu.py` ; `imu_server` / `imu_client`). Vérification **quand le BDX est on**. **Maintenant : robot off (accus)** — ne rien lancer.

**Hors périmètre immédiat de ce lot :** coder avant que le PO ait compris le processus et adapté l’UI.

---

**Prochaine étape recommandée :** sur le Pi, SIGKILL + start de `bdx-ble-robot` pour jouer le nouveau hello. **Pas** le lot 2. **Pas** le reste D-024. **Pas** D-025. **Pas** D-026.
