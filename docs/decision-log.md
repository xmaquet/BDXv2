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

La **marche RL n’est pas** le critère de succès de cette phase.

Précisé par **D-007** (quels accessoires) et **D-008** (comment les animer).

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

**Statut :** adoptée par déduction de D-003 (2026-08-24)

Améliorer, fiabiliser ou exposer la **locomotion** n’est pas le travail de la phase accessoires.

Le code de marche **n’est pas à supprimer** ; il n’est simplement pas le livrable.

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

---

## D-009 — Monorepo : copie, un seul Git

**Statut :** adoptée (2026-08-24, PO)

Les dépôts initiaux ne sont plus la source de vérité. Leurs sources sont **copiées** dans `xmaquet/BDXv2`. Les `.git` imbriqués sont **retirés**.

Les dépôts GitHub `xmaquet/Open_Duck_Mini` et `xmaquet/Open_Duck_Mini_Runtime` ne sont **pas** supprimés automatiquement sur GitHub ; ils ne sont plus le lieu du développement BDXv2.

Provenance figée au moment de l’aplatissement :

- Runtime : `518d53bf2257f682be45a017db499fb94326d267` (`feature/bdx_webui`)
- Mini : `5c8e06442e5848239e5b0bc856b1d80e07d4a44c` (`v2`)

---

## Reporté

- **D-C** — Réingénierie large de la stack : phase ultérieure éventuelle, pas engagée.
