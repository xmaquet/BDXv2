# BDXv2

Logiciel de démonstration autour d’un [Open Duck Mini](https://github.com/apirrone/Open_Duck_Mini) : salons et réunions de bot-makers.

La première version vise à **actionner depuis une tablette Android** les yeux, le projecteur, le haut-parleur et les antennes — pas la marche.

## Contenu

| Chemin | Rôle |
|--------|------|
| `docs/` | État, décisions, prochain lot |
| `AGENTS.md` | Règles de travail pour les agents |
| `Open_Duck_Mini/` | Mécanique, simulation, politiques (copie) |
| `Open_Duck_Mini_Runtime/` | Runtime Raspberry Pi et app tablette BLE (copie) |
| `pi-setup/` | Installation post-OS sur le Pi |

Les décisions produit sont dans [`docs/decision-log.md`](docs/decision-log.md). L’état réel est dans [`docs/current-state.md`](docs/current-state.md).

## Provenance

Ce dépôt rassemble des copies des forks `xmaquet/Open_Duck_Mini` et `xmaquet/Open_Duck_Mini_Runtime`. Il est désormais la source de vérité. Voir **D-009**.

Open Duck Mini amont : licence Apache 2.0 (fichier `Open_Duck_Mini/LICENSE`).
