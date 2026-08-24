# État actuel — BDXv2

Dernière mise à jour : 2026-08-24 (lot 0 : monorepo Git unique).

## Situation du projet

**Brownfield cadrée** : produit de démonstration autour d’un Open Duck Mini existant, dépôt unique `xmaquet/BDXv2`.

## Contenu du workspace

Dépôt canonique : [https://github.com/xmaquet/BDXv2](https://github.com/xmaquet/BDXv2)

| Chemin | Rôle |
|--------|------|
| `AGENTS.md` | Règles permanentes des agents |
| `docs/` | Canon : état, décisions, prochain lot |
| `Open_Duck_Mini/` | Hub mécanique / sim / politiques (copie, plus de Git propre) |
| `Open_Duck_Mini_Runtime/` | Runtime Pi + app tablette BLE (copie, plus de Git propre) |

Provenance au moment de l’aplatissement : voir **D-009**.

## Ce que le logiciel existant fait vraiment

Un opérateur peut faire **marcher** un Open Duck Mini depuis une **manette Xbox** ou une **tablette Android (BLE)**.

Les **accessoires d’expression** existent côté Pi, mais :

- ils ne démarrent que si `~/duck_config.json` a les flags `expression_features` à `true` ;
- ils sont **pilotés depuis le script de marche** ou `head_puppet.py` — pas depuis un mode « démo accessoires » ;
- la tablette émule une manette, pas un pupitre accessoires.

| Accessoire v1 (D-007) | Comportement actuel |
|----------------------|---------------------|
| Yeux | Clignotement **autonome** (pas de commande opérateur) |
| Projecteur | Bascule ON/OFF via bouton **X** |
| Haut-parleur | Son **aléatoire** via bouton **B** |
| Antennes | Position analogique via **LT/RT** |

## Cible produit (v1)

Démo salon / bot-makers, opérateur = PO.

**Succès :** actionner depuis l’UI tablette les quatre accessoires ci-dessus (live d’abord, séquences ensuite). La marche n’est pas le critère.

## Classification de l’existant

### CONSERVER

- Runtime Pi, modules `eyes.py`, `projector.py`, `sounds.py`, `antennas.py`.
- Chaîne BLE tablette (`feature/bdx_webui`).
- Contrat `ControllerFrame` v1 comme base, à **étendre** pour un pupitre accessoires.

### ADAPTER

- Découpler les accessoires du script de marche.
- Yeux : passer d’un clignotement autonome à une commande tablette.
- UI tablette : actions de démo explicites (pas seulement mapping manette).

### REMPLACER

- Git imbriqués des forks : **fait / en cours** (D-009).
- Réingénierie large de la stack : **reportée** (D-C).

### À INVESTIGUER

- Licence du runtime amont (pas de `LICENSE` à la racine ; Mini = Apache 2.0).
- Maturité réelle du lien BLE tablette ↔ Pi sur le matériel de l’opérateur.
