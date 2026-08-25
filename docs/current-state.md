# État actuel — BDXv2

Dernière mise à jour : 2026-08-25 (banc SSH : projecteur, yeux, HP validés ; install runtime complète lot 2 pas lancée).

## Situation du projet

**Brownfield cadrée.** Dépôt unique : [https://github.com/xmaquet/BDXv2](https://github.com/xmaquet/BDXv2)

Le Pi Zero 2W a un **OS Lite 64-bit neuf**. SSH joignable (user `bdxv2`, IP constatée `192.168.10.131`). Install runtime **complète** (D-012) **pas lancée**.

Minimum banc SSH : venv + Blinka + pygame + `Projector` / `Eyes` / `Sounds` dans `~/BDXv2/Open_Duck_Mini_Runtime/`. I2S : `dtoverlay=max98357a`. Menu : `bash ~/BDXv2/Open_Duck_Mini_Runtime/scripts/run_bdx_expression_menu.sh`. **FAIT robot :** `3` projecteur ; `1`/`2` yeux ; `4` HP — nominaux. Polarité lumières **active-high**. Câblage MAX98357 **correct**.

## Contenu du workspace

| Chemin | Rôle |
|--------|------|
| `AGENTS.md` | Règles permanentes des agents |
| `docs/` | Canon projet |
| `Open_Duck_Mini/` | Mécanique / sim |
| `Open_Duck_Mini_Runtime/` | Runtime Pi + app tablette BLE |

## Matériel opérateur (déclaré)

- HAT Pi : lumières yeux/projecteur via **2N2222**, GPIO **d’origine** (polarité active-high **vérifiée**).
- HP I2S **MAX98357A** : overlay actif, câblage **vérifié** (banc SSH `4`).
- Servos d’oreilles **branchés** (PWM D12/D13 dans le runtime, pas STS3215) — pas encore testés au banc.
- STS3215 programmés avec **FT SCServo Debug v1.9.8.1**.

## Logiciel existant (inchangé)

Accessoires encore collés au script de marche ; tablette = manette virtuelle ; flags `expression_features` faux par défaut.

GPIO d’origine : yeux D23/D24, projecteur D25, antennes PWM D12/D13.

## Cible (roadmap révisée)

1. OS neuf + SSH.
2. Script d’install complet (D-012).
3. UI **mode test** : fonctions une à une en BLE.
4. UI **mode normal** : expressions pendant la marche (scripts initiaux).

## Classification

### CONSERVER

- Modules accessoires et GPIO.
- Chaîne BLE existante.
- Scripts `v2_rl_walk_mujoco.py` comme référence du mode normal.
- `install.sh` / `bdx_full_install.sh` comme base du script d’install.

### ADAPTER

- Scripts d’install : cibler `xmaquet/BDXv2` au lieu du fork runtime `v2`.
- UI : modes test / normal (D-010).
- Yeux en mode test : commande opérateur, pas seulement clignotement autonome.

### REMPLACER

- Rien de nouveau. Réingénierie large reportée (D-C).

### À INVESTIGUER

- Licence runtime amont.
- Maturité BLE sur le matériel réel.
