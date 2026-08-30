# État actuel — BDXv2

Dernière mise à jour : 2026-08-30 (menu Monitoring).

## Situation du projet

**Brownfield cadrée.** Dépôt : [https://github.com/xmaquet/BDXv2](https://github.com/xmaquet/BDXv2) — branche `main`, HEAD `fb5f796`.

Le Pi Zero 2W a un **OS Lite 64-bit**. SSH joignable (user / hostname `bdxv2`, IP constatée `192.168.10.131`). Clone `~/BDXv2`. Venv banc conservé.

Le robot sert de **banc de dev** (D-020). L’install complète `pi-setup/install.sh` est **écrite, pas exécutée**.

## Ce qui est tenu

| Lot / volet | État |
|-------------|------|
| Lot 0 monorepo | Fait |
| Lot 1 OS + SSH | Fait |
| Lot 2 `pi-setup` | Script écrit ; **pas** lancé (D-020) |
| Banc SSH accessoires (D-016) | **Clos** — yeux, projecteur, HP, antennes validés (polarité **active-high**) |
| IDs STS3215 (D-014) | 14 IDs **déclarés programmés** |
| Lecture bus STS | **Validée PO** (2026-08-28) : accueil **STS OK · 7,7 V** (14/14, pyserial `/dev/ttyACM0`) |
| Lot 3 app BLE native (D-022) | APK **1.3.22** : accueil, Tests, halt, Monitoring (santé Pi + Wi‑Fi) |
| Halt UI (D-021) | Contrat + envoi + `poweroff` Pi **validés sur robot** |
| Hello boot (D-008) | In-process GATT ; son BLE prêt `BLE_OKAY_mini_BDX.wav` |
| Autostart GATT | `enable_ble_robot_boot.sh` (une fois) |
| Wi‑Fi BLE (D-023) | **Déployé** sur le banc (scan / join / défaut) |

## App tablette (D-015, D-018, D-022)

Surface produit = **UI Android native** (accueil à cartes). WebView / proto Figma **abandonnés**. `android_ui/` gelé.

Accueil : Piloter · Tests · Vidéo (placeholder) · Monitoring · Éteindre.

**FAIT banc / robot :** scan/connexion GATT ; TX `ControllerFrame` ; RX notify ; Tests accessoires (WAV nommés, style 2 s) ; halt avec confirmation ; badges STS + tension ; hello boot.

**Monitoring — santé Pi :** CPU %, charge 1 min, RAM, température SoC, disque `/`, uptime. Poll BLE `{ "type": "sys" }` ~8 s **seulement** tant que l’écran est ouvert. Lectures `/proc` / sysfs, pas de `vcgencmd`. APK **1.3.22**.

**Wi‑Fi robot (D-023) :** protocole + UI + Pi déployés sur le banc (scan / join / défaut). Validation hotspot 2,4 GHz en cours selon le réseau.

**Pas encore :** commandes « hors Tests » / parallèle marche (lot 4) ; vidéo (D-022, hors BLE).

## STS3215 (D-014)

IDs 10–14, 20–24, 30–33 programmés (FT SCServo Debug). Bus lu depuis le GATT (pyserial, pas rustypot pour la télémétrie accueil).

**Offsets :** scripts **écrits** — `find_soft_offsets_interactive.py` (un servo à la fois, pose manuelle → `duck_config.json`). Ancien test chevilles redirige vers ce script. **Calibration complète 14 axes non déclarée faite.**

Ce n’est pas encore le zéro marche / la locomotion.

## Audio

**Suivis Git (racine `assets/`) :** `happy1–3`, `beep1–2`, `lamp` / `lamp2` / `lamp3`, `motor`, `BLE_OKAY_mini_BDX.wav`.

`sounds.py` charge **uniquement** les `.wav` à la racine de `assets/` (pas les sous-dossiers).

**Rôle produit (D-024, todo — ne pas coder) :**

| Fichier | Rôle à terme |
|---------|----------------|
| `WIFI_OKAY_mini_BDX.wav` | Événement Wi‑Fi OK |
| `WIFI_PROBLEM_mini_BDX.wav` | Événement problème Wi‑Fi |
| `ENERGY_PROBLEM_mini_BDX.wav` | Événement problème énergie / tension |
| `random_sounds/*.wav` | Hello **aléatoire** + mimiques + yeux, **inactivité durable** (pas le hello boot) |

**Non suivis (2026-08-29, working tree).** Racine : chargés par `Sounds` dès qu’ils sont sur le Pi, **aucun événement ne les joue**. `random_sounds/` : **non chargé**.

Captures locales non suivies : `bdxv2-tablet.png`, `Open_Duck_Mini_Runtime/bdxv2-now.png` (hors canon).

## Contenu du workspace

| Chemin | Rôle |
|--------|------|
| `AGENTS.md` | Règles permanentes des agents |
| `docs/` | Canon + prompts agents |
| `Open_Duck_Mini/` | Mécanique / sim |
| `Open_Duck_Mini_Runtime/` | Runtime Pi + app tablette BLE |
| `pi-setup/` | Orchestration install post-OS (D-019) |

## Cible (roadmap)

1. OS neuf + SSH — **fait**.
2. Script d’install (`pi-setup/`) — **écrit** ; exécution **reportée** (D-020).
3. App BLE native — **tenue** pour Tests / halt / accueil STS ; Wi‑Fi **à déployer et valider** ; vidéo plus tard.
4. Commandes hors Tests (dont parallèle marche) — **pas démarré**.

## Classification

### CONSERVER

- GPIO / polarité HAT vérifiée ; banc SSH.
- Chaîne BLE native (accueil, Tests, halt, status).
- `v2_rl_walk_mujoco.py` comme référence d’effets du mode normal.
- `pi-setup/install.sh` ; wrappers héritage.

### ADAPTER

- Deploy Wi‑Fi BLE (D-023) sur le Pi.
- `pi-setup` plus tard : absorber `enable_halt_sudo.sh`, `enable_wifi_sudo.sh`, `enable_ble_robot_boot.sh`.
- Sons D-024 (événements + hello d’inactivité) : **todo**, pas maintenant.
- Offsets STS interactifs jusqu’aux 14 axes.

### REMPLACER

- Manette Xbox produit (D-018) — fichier héritage conservé.
- WebView / proto Figma (D-022) — `android_ui/` gelé.
- Réingénierie large (D-C) — reportée.

### À INVESTIGUER

- Licence runtime amont.
- Restart systemd `bdx-ble-robot` : le process ignore SIGTERM (peut rester coincé jusqu’au SIGKILL).
- Calibration offsets une fois posée la mécanique.
