# Prochain travail

## Lot 0 — Monorepo BDXv2

**Statut :** réalisé localement (2026-08-24). Push GitHub : voir l’état Git de fin de lot.

**Objectif :** un seul dépôt Git à la racine, remote `https://github.com/xmaquet/BDXv2`, sources des forks copiées, `.git` imbriqués retirés (D-009).

---

## Lot 1 (suivant, pas démarré)

**Objectif :** depuis la tablette, actionner en **live** les quatre accessoires v1 (yeux, projecteur, HP, antennes) sur le robot réel, **sans** passer par la marche.

**Périmètre probable :**

- mode expression / démo côté Pi, indépendant de `v2_rl_walk_mujoco.py` ;
- commandes tablette dédiées (pas seulement boutons Xbox) ;
- yeux commandables (aujourd’hui clignotement autonome seulement).

**Hors périmètre lot 1 :** séquences chorégraphiées (D-008, après le live), caméra/micro/tête, locomotion.

**Critères d’acceptation (à détailler au démarrage du lot) :**

- tablette → yeux / projecteur / son / antennes, chacun observable sur le robot ;
- la marche n’a pas besoin d’être lancée ;
- validation sur robot physique + tablette (D-004).
