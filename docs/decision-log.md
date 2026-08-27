# Décisions

Les identifiants `D-xxx` sont stables. Ne pas les réécrire ailleurs sous une autre formulation.

Statuts : **adoptée** | **proposée** | **reportée**.

---

## D-001 — Nature de BDXv2

**Statut :** adoptée (2026-08-24, PO)

BDXv2 est **à la fois** :

- la continuation / fiabilisation du fork Open Duck Mini **de l’opérateur** (lecture A) ;
- un **produit logiciel** autour de ce robot pour un usage de démonstration (lecture B).

Une **réingénierie large** de la stack (lecture C) n’est **pas** un objectif de la phase actuelle. Elle reste possible plus tard, sans engager l’architecture actuelle.

**Conséquence :** on s’appuie sur l’existant ; on ne réécrit pas pour élégance technique.

---

## D-002 — Utilisateur et contexte de la v1

**Statut :** adoptée (2026-08-24, PO)

L’utilisateur de la première version est **le Product Owner lui-même**.

Contexte : **démonstrations en salon** et **réunions de bot-makers**.

Il n’y a pas, pour la v1, d’autre rôle (public, enfant, technicien distinct) à satisfaire comme utilisateur primaire.

---

## D-003 — Premier critère de succès

**Statut :** adoptée (2026-08-24, PO)

La première version réussit si l’opérateur peut **animer les accessoires autres que la marche** (lumières, sons, et assimilés) sur le robot réel.

La **marche RL n’est pas** le critère de succès du **mode test**.

Amendée par **D-010** : le **mode normal** réintroduit la marche, avec les accessoires en parallèle (comportement des scripts initiaux).

Précisé par **D-007**, **D-008** et **D-010**.

---

## D-004 — Banc de validation v1

**Statut :** adoptée (2026-08-24, PO)

La validation se fait sur **robot physique** et **tablette Android**.

Une preuve uniquement simulée ne suffit pas pour déclarer la v1 réussie.

---

## D-005 — Dépôt canonique

**Statut :** adoptée (2026-08-24, PO)

Le projet se pose sur un **nouveau dépôt** qui rassemble les forks et le développement BDXv2 :

[https://github.com/xmaquet/BDXv2](https://github.com/xmaquet/BDXv2)

Mode d’inclusion : **D-009** (copie dans le monorepo, Git imbriqués abandonnés).

---

## D-006 — Marche hors périmètre immédiat

**Statut :** amendée (2026-08-24, PO — D-010)

La locomotion n’est **pas** le livrable du mode test.

Le **mode normal** doit faire les actions d’expression **en parallèle de la marche**, conformément aux scripts initiaux. Le code de marche est conservé et sera branché dans ce mode.

---

## D-007 — Accessoires de la v1

**Statut :** adoptée (2026-08-24, PO)

La tablette doit pouvoir **actionner** :

- les yeux ;
- le projecteur ;
- le haut-parleur (sons) ;
- les antennes.

Tout le reste (caméra, micro, tête, etc.) **vient après**. Ce n’est pas un oubli : c’est hors périmètre v1.

---

## D-008 — Mode d’animation

**Statut :** adoptée (2026-08-24, PO)

Les deux modes existent :

1. **Live** — l’opérateur déclenche depuis l’UI tablette ;
2. **Séquences** — petite chorégraphie start/stop.

Le **live tablette d’abord**. Les séquences suivent, dans la même phase produit, sans précéder le pupitre.

**Précision D-010 :** le live se matérialise d’abord par le **mode test** (fonctions une à une). Les « séquences » ne sont pas le mode normal : le mode normal = expressions **pendant** la marche, comme les scripts d’origine.

---

## D-009 — Monorepo : copie, un seul Git

**Statut :** adoptée (2026-08-24, PO)

Les dépôts initiaux ne sont plus la source de vérité. Leurs sources sont **copiées** dans `xmaquet/BDXv2`. Les `.git` imbriqués sont **retirés**.

Les dépôts GitHub `xmaquet/Open_Duck_Mini` et `xmaquet/Open_Duck_Mini_Runtime` ne sont **pas** supprimés automatiquement sur GitHub ; ils ne sont plus le lieu du développement BDXv2.

Provenance figée au moment de l’aplatissement :

- Runtime : `518d53bf2257f682be45a017db499fb94326d267` (`feature/bdx_webui`)
- Mini : `5c8e06442e5848239e5b0bc856b1d80e07d4a44c` (`v2`)

---

## D-010 — Deux modes UI tablette : test et normal

**Statut :** adoptée (2026-08-24, PO)

L’application Android expose deux modes :

- **Test** : l’opérateur lance **une fonction à la fois** (yeux, projecteur, HP, antennes).
- **Normal** : le droid exécute ces actions **en parallèle de la marche**, effet réaliste, **conforme aux scripts initiaux**.

Le mode test est le premier objectif logiciel après que le Pi soit installé et joignable. Le mode normal vient ensuite.

**Précision D-018 (2026-08-27) :** le « mode test » est un **sous-menu Tests** de l’app Android, **indépendant de la marche**. La commande opérateur n’est **plus** une manette Xbox (physique ou virtuelle comme produit).

---

## D-011 — HAT opérateur, GPIO d’origine, 2N2222

**Statut :** adoptée (2026-08-24, PO)

L’opérateur a réalisé un **HAT** pour le Pi Zero 2W.

Les lumières (yeux, projecteur) sont commutées via des **2N2222**. Les **broches GPIO restent celles d’origine** du runtime (`eyes.py` : D23 / D24 ; `projector.py` : D25).

Les **servos d’oreilles** (antennes) sont branchés.

**Hypothèse technique (à vérifier sur le HAT) :** commande GPIO active-high vers la base du NPN, lumière allumée quand le GPIO est à 1 — compatible avec le code actuel. À confirmer au premier test GPIO.

**Vérifié (2026-08-25, PO, banc SSH D-016) :** polarité **active-high** confirmée pour le projecteur **D25** et les yeux **D23 / D24** (fixe ON/OFF et clignotement nominaux).

---

## D-012 — Script d’installation Pi comme livrable

**Statut :** adoptée (2026-08-24, PO)

Après une install OS neuve, l’opérateur doit pouvoir lancer **un script d’install complet** sur le Pi.

Les scripts existants sont un point de départ (**ADAPTER**, pas réécrire sans besoin).

**Avancement (2026-08-27) :** canon = **`pi-setup/install.sh`** (D-019). Les anciens chemins `Open_Duck_Mini_Runtime/install.sh`, `scripts/bdx_full_install.sh` et `scripts/install_bdx_runtime.sh` **délèguent**. **Pas exécuté** sur le Pi : séquence **D-020**.

---

## D-013 — Pilotage SSH par l’agent après l’OS

**Statut :** adoptée (2026-08-24, PO)

Une fois l’OS réinstallé, l’agent se connecte au Pi en **SSH** pour piloter installation, tests et actions.

**Avancement (2026-08-25) :** OS posé ; SSH joignable (`bdxv2@192.168.10.131` constaté). La clause « pas de SSH avant l’OS » est **satisfaite**.

---

## D-014 — Programmation STS3215 via FT SCServo Debug

**Statut :** adoptée (2026-08-24, PO)

Les servos corps / tête **STS3215** se programment avec le logiciel **FT SCServo Debug v1.9.8.1** (un servo à la fois, IDs du projet).

Ce n’est **pas** le même bus que les servos d’oreilles (PWM GPIO D12/D13 dans `antennas.py`).

**Avancement (2026-08-26) :** les 14 IDs (10–14, 20–24, 30–33) sont **déclarés programmés** (offset EEPROM 0, D=0, Lock=1). Calibration `find_soft_offsets.py` **pas commencée**. Pas de test bus une fois montés.

---

## D-015 — Tablette APK + BLE bidirectionnel ; vidéo plus tard

**Statut :** adoptée (2026-08-25, PO)

Le volet tablette est une **application Android (APK)** plus un **module sur le BDX** (Pi).

Les **commandes** circulent en **Bluetooth Low Energy dans les deux sens** (tablette ↔ robot).

La **vidéo** (Picam déjà en place) est un objectif **ultérieur**, pas le critère du premier livrable BLE.

**Avancement (2026-08-25) :** audit code fait ; **pas** d’APK, **pas** de test BLE. Lot 3a (dump TX/RX) proposé, non autorisé.

---

## D-016 — Banc de test SSH (fonctions virtuelles, hors UI)

**Statut :** adoptée (2026-08-25, PO)

Pour tester les accessoires **sans** l’UI tablette : menu texte en SSH qui lance de **mini-scripts**.

Menu figé (2026-08-25) : `1` yeux fixe, `2` yeux clignotement, `3` projecteur, `4` HP, `5` antennes, `0`/`q` quitter.

**Inclus :** fonctions d’expression hors locomotion (yeux, projecteur, HP, antennes / oreilles PWM).

**Exclus :** marche RL, bus **STS3215**.

**Avancement :** banc SSH **clos** (2026-08-25, PO) — projecteur `3`, yeux `1`/`2`, HP `4`, antennes `5`/`6`. Pi : clone Git `~/BDXv2` (venv conservé).

---

## D-017 — Mini-outils SSH + pas de docs sur le robot

**Statut :** adoptée (2026-08-25, PO)

Les tests / réglages passent par un **menu principal** (`run_bdx_lab.sh`) qui lance des mini-scripts **choisis un par un**. Aucune entrée n’est ajoutée par scan du dépôt : seulement sur demande explicite du PO. Premier outil : banc d’expression (D-016).

Le checkout Git **sur le Pi** n’inclut pas `docs/` (inutile à l’exécution). Les docs restent dans le dépôt GitHub / le workspace PC.

---

## D-018 — Commande = app Android BLE ; Xbox hors produit ; Tests dans l’app

**Statut :** adoptée (2026-08-27, PO)

Dans **cette** version BDXv2 :

- la **commande opérateur** passe par l’**application Android** connectée en **BT BLE** au BDX ;
- le chemin **manette Xbox** (Bluetooth HID / pygame joystick) est **abandonné** comme surface produit ;
- l’app comporte un **sous-menu Tests** : fonctions d’accessoires **indépendantes de la marche** (yeux, projecteur, HP, antennes), complémentaires du banc SSH (D-016).

Le code héritage `xbox_controller.py` **n’est pas à supprimer** (référence / marche amont). Il n’est **pas** un prérequis d’install ni d’UI.

Le protocole JSON interne peut rester aligné sur `ControllerFrame` v1 tant qu’une évolution n’est pas décidée ; l’UI ne doit **pas** se présenter comme une manette Xbox.

**Conséquence install :** extra pip **`[ble]`** et **`[hardware]`** ; **pas** `[control]` / appairage Xbox. pygame via **apt** (`python3-pygame`) pour **l’audio** (`sounds.py`), pas pour une manette.

---

## D-019 — Orchestration Pi dans `pi-setup/`

**Statut :** adoptée (2026-08-27, PO)

L’installation post-OS vit dans **`pi-setup/`** à la racine du monorepo. Ce n’est **pas** un deuxième runtime Python : le paquet reste `Open_Duck_Mini_Runtime/` (`pip install -e .`).

**Clone neuf sur le Pi :** sparse checkout `Open_Duck_Mini_Runtime` + `pi-setup` (pas de `Open_Duck_Mini/` CAO, pas de `docs/` — D-017). Un clone **déjà complet** n’est **pas** réduit.

**Idempotence :** venv, overlay I2S, `~/duck_config.json`, groupes et clone Git existants sont réutilisés. Pas d’écrasement de config. Pas de reboot automatique (I2S : reboot manuel si overlay nouvellement écrit).

Les scripts héritage sous `Open_Duck_Mini_Runtime/` restent des **wrappers**.

---

## D-020 — Le robot sert de banc de dev ; install complète plus tard

**Statut :** adoptée (2026-08-27, PO)

Le BDX est **à la fois** plateforme de développement et de production. L’install post-OS (`pi-setup/install.sh`) **n’est pas** à lancer tant que les développements propres à cette version n’ont **pas commencé** — en particulier l’**app de commande** (lot 3) — afin de les **tester en place** sur le robot.

Une **réinstallation complète** reste possible **en fin de cycle**, pour vérifier que le script d’install (D-012) reconstitue un Pi propre.

Le banc SSH actuel (D-016) et le venv minimum **restent** le socle de travail jusqu’à ce go.

---

## Reporté

- **D-C** — Réingénierie large de la stack : phase ultérieure éventuelle, pas engagée.
