# État actuel — BDXv2

Dernière mise à jour : 2026-08-26 (canon aligné sur le bilan agents).

## Situation du projet

**Brownfield cadrée.** Dépôt unique : [https://github.com/xmaquet/BDXv2](https://github.com/xmaquet/BDXv2)

Le Pi Zero 2W a un **OS Lite 64-bit neuf**. SSH joignable (user `bdxv2`, hostname `bdxv2`, IP constatée `192.168.10.131`). Clone Git : `~/BDXv2`. Venv banc conservé.

Install runtime **complète** (D-012 / `install.sh`) : script **adapté** vers BDXv2, **pas exécuté** sur le Pi. Le robot a un **minimum** pour le banc SSH (Blinka, pygame, `lgpio`), pas l’install BLE/hardware/RL.

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

## Tablette / BLE (D-015)

Chaîne TX existante dans le code ; UI = manette virtuelle, pas encore modes test/normal. **Aucun APK construit**, **aucun test BLE** sur tablette. Audit fait ; lot 3a proposé, pas démarré.

## Contenu du workspace

| Chemin | Rôle |
|--------|------|
| `AGENTS.md` | Règles permanentes des agents |
| `docs/` | Canon + prompts agents (`docs/hardware/`) |
| `Open_Duck_Mini/` | Mécanique / sim |
| `Open_Duck_Mini_Runtime/` | Runtime Pi + app tablette BLE |

## Cible (roadmap)

1. OS neuf + SSH — **fait**.
2. Script d’install complet sur le Pi — **à lancer**.
3. UI **mode test** BLE.
4. UI **mode normal** (expressions pendant la marche).

## Classification

### CONSERVER

- Modules accessoires, GPIO, polarité HAT vérifiée.
- Banc SSH et overlay I2S.
- Chaîne BLE existante (à étendre, pas à jeter).
- `v2_rl_walk_mujoco.py` comme référence du mode normal.
- `install.sh` (cible BDXv2 `main`).

### ADAPTER

- UI : modes test / normal (D-010) ; RX utile.
- Yeux : commande opérateur en mode test tablette (déjà possible au banc SSH).
- `install.sh` : l’exécuter sur le Pi (BLE, hardware, I2C, groupes).

### REMPLACER

- Réingénierie large : **reportée** (D-C).

### À INVESTIGUER

- Licence runtime amont.
- Scan BLE Android 12+ (hypothèse localisation).
- Calibration offsets STS3215 une fois montés.
