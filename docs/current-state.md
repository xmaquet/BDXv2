# État actuel — BDXv2

Dernière mise à jour : 2026-08-27 (D-021 halt).

## Situation du projet

**Brownfield cadrée.** Dépôt unique : [https://github.com/xmaquet/BDXv2](https://github.com/xmaquet/BDXv2)

Le Pi Zero 2W a un **OS Lite 64-bit neuf**. SSH joignable (user `bdxv2`, hostname `bdxv2`, IP constatée `192.168.10.131`). Clone Git : `~/BDXv2`. Venv banc conservé.

Install runtime **complète** (D-012 / D-019) : canon **`pi-setup/install.sh`**, **écrit, pas exécuté** sur le Pi (**D-020** : attendre les devs version, surtout l’app de commande). Le robot a un **minimum** pour le banc SSH (Blinka, pygame, `lgpio`), pas l’install BLE/hardware/RL.

## Banc SSH (D-016) — clos

Menu : `bash ~/BDXv2/Open_Duck_Mini_Runtime/scripts/run_bdx_expression_menu.sh`  
Lab : `bash ~/BDXv2/Open_Duck_Mini_Runtime/scripts/run_bdx_lab.sh`

**FAIT robot (PO) :**

| Entrée | Fonction | Résultat |
|--------|----------|----------|
| `1` | Yeux fixe | Nominal ; polarité **active-high** D23/D24 |
| `2` | Yeux clignotement | Nominal |
| `3` | Projecteur | Nominal ; polarité **active-high** D25 |
| `4` | HP | Nominal après overlay **MAX98357A** ; câblage I2S correct |
| `5` / `6` | Antennes PWM | Nominal |

I2S : `dtoverlay=max98357a`. GPIO d’origine inchangés.

## STS3215 (D-014)

Les **14** IDs (10–14, 20–24, 30–33) sont **déclarés programmés** (FT SCServo Debug) : offset EEPROM 0, D=0, Lock=1. Zéro robot = **plus tard** (`find_soft_offsets.py`). Pas de test bus complet une fois montés.

## Tablette / BLE (D-015, D-022)

**D-022 :** surface produit = **UI Android native** (accueil à menus). WebView Capacitor / proto Figma **abandonnés** comme UI. `android_ui/` gelé.

**FAIT banc (2026-08-27) :** APK native ; scan/connexion GATT ; TX `ControllerFrame` ; RX notify après CCCD (cache GATT : rediscovery générique). Pas de manette Xbox (D-018). **Tests** accessoires actionnables (sons nommés, style actif). **Halt** : contrat figé, envoi UI + `poweroff` Pi (sudoers une fois via `enable_halt_sudo.sh`). **Hello boot :** à chaque lancement de `bdx-ble-robot` (yeux ×3, antennes ×4, `happy1`).

**Pas encore :** vidéo (D-022, hors BLE).

## Contenu du workspace

| Chemin | Rôle |
|--------|------|
| `AGENTS.md` | Règles permanentes des agents |
| `docs/` | Canon + prompts agents (`docs/hardware/`) |
| `Open_Duck_Mini/` | Mécanique / sim |
| `Open_Duck_Mini_Runtime/` | Runtime Pi + app tablette BLE |
| `pi-setup/` | Orchestration install post-OS (D-019) |

## Cible (roadmap)

1. OS neuf + SSH — **fait**.
2. Script d’install (`pi-setup/`) — **écrit** ; exécution Pi **reportée** (D-020).
3. App Android BLE native (D-022) : accueil à menus **fait** ; **Tests** actionnables **fait** ; halt envoyé (D-021) **codé** (à valider sur robot : sudoers + poweroff).
4. Commandes « normales » (dont expressions pendant la marche), **sans** manette Xbox.

## Classification

### CONSERVER

- Modules accessoires, GPIO, polarité HAT vérifiée.
- Banc SSH et overlay I2S.
- Chaîne BLE existante (à étendre, pas à jeter).
- `v2_rl_walk_mujoco.py` comme référence du mode normal.
- `pi-setup/install.sh` (cible BDXv2 `main`) ; wrappers héritage.

### ADAPTER

- UI : **native** (D-022) ; **Tests** (D-018) ; **Éteindre le robot** (D-021) hors Tests, avec confirmation.
- RX utile (état / logs).
- `pi-setup` / install : BLE + audio ; **pas** Xbox `[control]`.
- Yeux commandables depuis Tests (déjà au banc SSH).

### REMPLACER

- Surface produit manette Xbox : **abandonnée** (D-018) — le fichier héritage reste.
- Surface produit WebView / proto Figma : **abandonnée** (D-022) — `android_ui/` gelé.
- Réingénierie large de la stack : **reportée** (D-C).

### À INVESTIGUER

- Licence runtime amont.
- Scan BLE Android 12+ (hypothèse localisation).
- Calibration offsets STS3215 une fois montés.
