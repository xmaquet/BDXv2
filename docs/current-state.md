# État actuel — BDXv2

Dernière mise à jour : 2026-09-04 (mode démo D-027, bandeau accueil, APK 1.3.27).

## Situation du projet

**Brownfield cadrée.** Dépôt : [https://github.com/xmaquet/BDXv2](https://github.com/xmaquet/BDXv2) — branche `main`, HEAD `124aeb1`.

Le Pi Zero 2W a un **OS Lite 64-bit**. SSH : user / hostname `bdxv2`. IP **constatée 2026-08-30 : `192.168.10.132`** (DHCP ; a déjà été `192.168.10.131`). Clone `~/BDXv2` **à jour** (`git pull` fast-forward). Venv banc conservé.

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
| Lot 3 app BLE native (D-022) | APK **1.3.27** : accueil (bandeau démo active), Piloter, Tests, **Mode démo**, halt, Monitoring |
| Halt UI (D-021) | Contrat + envoi + `poweroff` Pi **validés sur robot** |
| Hello boot (D-008) | In-process : yeux / antennes / `happy1` ; `BLE_OKAY` ; puis **Wi‑Fi init** `WIFI_OKAY` / `WIFI_PROBLEM` |
| Autostart GATT | `enable_ble_robot_boot.sh` (une fois). SIGTERM souvent ignoré → SIGKILL pour relancer |
| Wi‑Fi BLE (D-023) | **Déployé** sur le banc (scan / join / défaut). WAV init **sur le Pi** |

## App tablette (D-015, D-018, D-022)

Surface produit = **UI Android native** (accueil à cartes). WebView / proto Figma **abandonnés**. `android_ui/` gelé.

Accueil : Piloter · Tests · **Mode démo** · Vidéo (placeholder) · Monitoring · Éteindre.

**FAIT banc / robot :** scan/connexion GATT ; TX `ControllerFrame` ; RX notify ; Tests accessoires ; halt ; badges STS + tension ; hello boot + sons BLE/Wi‑Fi d’init **dans le dépôt et sur le clone Pi**.

**Mode démo (D-027) :** presets `nod` / `look_around` / `curious` / **`idle` (Attente, mix d’effets)** / **`idle_mix` (les 3 presets en ordre aléatoire)**. Pause **entre** salves : `period_s` réglable dans **Monitoring** (5–300 s, défaut 30). Accueil : bandeau jaune **MODE DÉMO EN COURS** tant qu’une séquence tourne. Tête 30–33. Robot **sur support**. `{ "type": "demo" }`. APK **1.3.27**.

**Piloter (APK 1.3.23) :** contrat `ControllerFrame` v1 (héritage Xbox, D-018). Rangée haute : **Ant. G** · Tempo +/− · **Ant. D** (G à gauche, D à droite). Sticks **Marche** et **Rotation** alignés. Pause=A, Son=B, Proj.=X, Tête=Y, Rythme=LB, Tempo=croix, Ant. G=`rt`, Ant. D=`lt`, Stop=`estop`. RB non utilisé. Mapping détaillé : `Open_Duck_Mini_Runtime/docs/protocol.md`. Pendant une démo, les trames Piloter sont **ignorées**. La marche n’est **pas** lancée depuis l’app (lot 4).

**Monitoring — santé Pi :** CPU %, charge 1 min, RAM, température SoC, disque `/`, uptime. Poll `{ "type": "sys" }` ~8 s si l’écran est ouvert.

**Wi‑Fi robot (D-023) :** scan / join / défaut **sur le banc**. Hello init : après `BLE_OKAY`, attente ~20 s NM → `WIFI_OKAY` ou `WIFI_PROBLEM` (alors Monitoring). **Pour entendre le nouveau hello :** relancer `bdx-ble-robot` (SIGKILL) — le `git pull` ne recharge pas le process.

**Pas encore :** commandes « hors Tests » / parallèle marche (lot 4) ; vidéo (D-022, hors BLE) ; **choix de robot** (D-025 : liste + mémoire d’adresse — **ne pas coder** maintenant). Scan actuel = premier appareil au service UUID ; nom annoncé = `Open Duck Mini`.

## STS3215 (D-014)

IDs 10–14, 20–24, 30–33 programmés (FT SCServo Debug). Bus lu depuis le GATT (pyserial, pas rustypot pour la télémétrie accueil).

**Offsets logiciels :** `~/duck_config.json` sur le Pi = **tous à 0**, identique au modèle GitHub `Open_Duck_Mini_Runtime/example_config.json`. **Volontaire (2026-08-30, PO)** : le zéro mécanique a été posé dans le logiciel Feetech, donc pas de correction runtime. Le script interactif reste là si un axe doit être repris plus tard.

Ce n’est pas encore le zéro marche / la locomotion.

## Audio

**Suivis Git (racine `assets/`) :** `happy1–3`, `beep1–2`, `lamp` / `lamp2` / `lamp3`, `motor`, `BLE_OKAY`, `WIFI_OKAY`, `WIFI_PROBLEM`, `ENERGY_PROBLEM`. Plus `random_sounds/` (3 WAV). **Présents sur le Pi** après `git pull` du 2026-08-30.

`sounds.py` charge **uniquement** les `.wav` à la racine (pas `random_sounds/`).

| Fichier | État |
|---------|------|
| `BLE_OKAY_mini_BDX.wav` | **Fait** — pub BLE |
| `WIFI_OKAY` / `WIFI_PROBLEM` | **Fait à l’init** (D-008 / D-024). Rejouer en session = plus tard |
| `ENERGY_PROBLEM_mini_BDX.wav` | **Todo** — événement tension |
| `random_sounds/*.wav` | **Todo** — hello aléatoire + mimiques + yeux, inactivité durable |

Captures locales non suivies : `bdxv2-tablet.png`, `Open_Duck_Mini_Runtime/bdxv2-now.png` (hors canon).

## Contenu du workspace

| Chemin | Rôle |
|--------|------|
| `AGENTS.md` | Règles permanentes des agents |
| `docs/` | Canon + prompts agents |
| `Open_Duck_Mini/` | Mécanique / sim |
| `Open_Duck_Mini_Runtime/` | Runtime Pi + app tablette BLE |
| `pi-setup/` | Orchestration install post-OS (D-019). **Cible D-026 :** y loger le runtime BDXv2 — **ne pas migrer maintenant** |

## Cible (roadmap)

1. OS neuf + SSH — **fait**.
2. Script d’install (`pi-setup/`) — **écrit** ; exécution **reportée** (D-020).
3. App BLE native — **tenue** (Tests, halt, accueil STS, Monitoring, Wi‑Fi banc). Hello Wi‑Fi **codé et tiré** ; valider à l’oreille après restart GATT. Vidéo plus tard.
4. Commandes hors Tests / marche — **découverte** (prompt `docs/hardware/PROMPT-agent-marche.md`) ; **pas de code** tant que le PO n’a pas tranché. L’UI Piloter envoie déjà le `ControllerFrame`.

## Classification

### CONSERVER

- GPIO / polarité HAT vérifiée ; banc SSH.
- Chaîne BLE native (accueil, Piloter, Tests, halt, Monitoring, status).
- `v2_rl_walk_mujoco.py` comme référence d’effets du mode normal.
- `pi-setup/install.sh` ; wrappers héritage.

### ADAPTER

- `pi-setup` plus tard : absorber `enable_halt_sudo.sh`, `enable_wifi_sudo.sh`, `enable_ble_robot_boot.sh`.
- D-026 à terme : runtime BDXv2 **sous** `pi-setup/` ; `Open_Duck_Mini_Runtime/` reste en référence. **Ne pas migrer maintenant.**
- D-024 reste : `ENERGY_PROBLEM`, hello idle `random_sounds/`, sons Wi‑Fi **en session**.
- D-025 plus tard : liste + choix de robot + mémoire d’adresse (noms `--name` distincts).
- Offsets STS interactifs seulement si un axe dérive (zéro Feetech = offsets 0).

### REMPLACER

- Manette Xbox produit (D-018) — fichier héritage conservé.
- WebView / proto Figma (D-022) — `android_ui/` gelé.
- Réingénierie large (D-C) — reportée.

### À INVESTIGUER

- Licence runtime amont.
- Restart systemd `bdx-ble-robot` : le process ignore SIGTERM (peut rester coincé jusqu’au SIGKILL).
- Calibration offsets logiciels : **pas requise** tant que le zéro Feetech tient (`joints_offsets` = 0).
