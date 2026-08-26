# Prochain travail

Roadmap révisée (2026-08-24, PO). Point agents 2026-08-26.

## Lot 0 — Monorepo BDXv2

**Statut :** fait. Dépôt : [https://github.com/xmaquet/BDXv2](https://github.com/xmaquet/BDXv2)

---

## Parallèle — Programmation STS3215

**Statut :** **IDs 10–14, 20–24, 30–33 déclarés faits** (2026-08-26, PO + agent STS3215). Guide : `docs/hardware/sts3215-scservo-debug.md`. Prompt : `docs/hardware/PROMPT-agent-sts3215.md`.

**Suite (pas démarrée) :** montage, puis offsets `find_soft_offsets.py`. Pas de test bus complet monté.

Hors périmètre : oreilles / antennes (PWM, pas STS3215).

---

## Lot 1 — Réinstall OS Pi Zero 2W

**Statut :** fait (2026-08-25). OS Lite 64-bit ; SSH écoute ; user / hostname `bdxv2` ; IP constatée `192.168.10.131`. Prompt : `docs/hardware/PROMPT-agent-infra-pi.md`.

---

## Lot 2 — Script d’install complet

**Statut :** script **adapté** (cible `xmaquet/BDXv2` branche `main`), **pas exécuté** sur le Pi. Le banc SSH a posé un minimum (Blinka / pygame / I2S), pas D-012.

**Objectif :** un script lançable **après** l’OS, qui installe tout le nécessaire BDXv2 sur le Pi (D-012).

**Canon :** `Open_Duck_Mini_Runtime/install.sh`. `scripts/bdx_full_install.sh` et `scripts/install_bdx_runtime.sh` délèguent.

**Attente :** go PO pour exécution SSH.

---

## Lot 3 — BLE + mode test

**Statut :** audit fait (2026-08-25) ; **aucun code**, **aucun APK**, **aucun test tablette**. Prompt : `docs/hardware/PROMPT-agent-android-ble.md`. Décision **D-015**.

**Objectif :** APK connectée en BLE au Pi ; commandes **dans les deux sens** ; mode test (yeux, projecteur, HP, antennes une à une).

**Premier lot proposé (3a, pas autorisé) :** build APK + scan/connexion + dump TX/RX.

**Hors périmètre immédiat :** marche, mode normal, vidéo Picam.

---

## Parallèle — Banc SSH fonctions virtuelles

**Statut :** **clos** (2026-08-25, PO). Projecteur, yeux, HP, antennes **validés sur le robot**. Prompt : `docs/hardware/PROMPT-agent-ssh-test-menu.md`. Décisions **D-016**, **D-017**.

---

## Lot 4 — Mode normal

**Statut :** pas démarré.

**Objectif :** expressions **en parallèle de la marche**, scripts initiaux (`v2_rl_walk_mujoco.py`).

---

**Prochaine étape recommandée :** lot 2 (`install.sh` sur le Pi), puis lot 3a (APK / BLE).
