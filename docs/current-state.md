# État actuel — BDXv2

Dernière mise à jour : 2026-08-27 (pi-setup, D-019, D-020).

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

## Tablette / BLE (D-015)

Chaîne TX existante dans le code ; l’UI actuelle est encore une **manette virtuelle** (héritage). **D-018 :** produit = app BLE + sous-menu **Tests** (pas de Xbox). **Aucun APK construit**, **aucun test BLE**. Audit fait ; lot 3a proposé, pas démarré.

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
3. App Android BLE + sous-menu **Tests** (D-018) — **prochaine étape**.
4. Commandes « normales » (dont expressions pendant la marche), **sans** manette Xbox.

## Classification

### CONSERVER

- Modules accessoires, GPIO, polarité HAT vérifiée.
- Banc SSH et overlay I2S.
- Chaîne BLE existante (à étendre, pas à jeter).
- `v2_rl_walk_mujoco.py` comme référence du mode normal.
- `pi-setup/install.sh` (cible BDXv2 `main`) ; wrappers héritage.

### ADAPTER

- UI : app BLE + **sous-menu Tests** (D-018) ; ne pas livrer une manette Xbox.
- RX utile (état / logs).
- `pi-setup` / install : BLE + audio ; **pas** Xbox `[control]`.
- Yeux commandables depuis Tests (déjà au banc SSH).

### REMPLACER

- Surface produit manette Xbox : **abandonnée** (D-018) — le fichier héritage reste.
- Réingénierie large de la stack : **reportée** (D-C).

### À INVESTIGUER

- Licence runtime amont.
- Scan BLE Android 12+ (hypothèse localisation).
- Calibration offsets STS3215 une fois montés.
