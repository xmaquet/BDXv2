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

**Statut :** **`pi-setup/` créé** (2026-08-27) ; **pas exécuté** sur le Pi. Le banc SSH a posé un minimum (Blinka / pygame / I2S), pas encore D-012 complet (BLE / rustypot).

**Objectif :** un script lançable **après** l’OS, qui installe tout le nécessaire BDXv2 sur le Pi (D-012, D-019).

**Canon :** `pi-setup/install.sh`. Wrappers : `Open_Duck_Mini_Runtime/install.sh`, `scripts/bdx_full_install.sh`, `scripts/install_bdx_runtime.sh`.

**Attente :** **reportée** (D-020). Ne pas lancer sur le Pi tant que les devs propres à cette version (surtout l’app de commande, lot 3) n’ont pas commencé. Réinstall complète possible en fin de cycle.

---

## Lot 3 — App Android BLE + sous-menu Tests

**Statut :** lot **3a** (dump TX/RX BLE) **tenu** ; **Tests** actionnables **tenus** (2026-08-27). **Halt (D-021)** et **hello boot** validés sur robot. **Accueil STS** (état bus + tension moyenne) **codé** (2026-08-28), à valider Pi + APK 1.3.6. **D-022 :** UI produit = native. Prompt : `docs/hardware/PROMPT-agent-android-ble.md`. Décisions **D-015**, **D-018**, **D-021**, **D-022**.

**Objectif :** APK connectée en BLE au Pi ; commandes dans les deux sens ; **sous-menu Tests** (yeux, projecteur, HP, antennes) **indépendant de la marche**. Pas de surface Xbox. **Arrêt système** depuis l’UI (D-021), hors Tests, avec confirmation.

**Hors périmètre immédiat :** manette Xbox, **vidéo** (deux temps : affichage puis IA tablette — D-022).

**Install Pi :** `pi-setup/` n’installe pas `[control]` / Xbox ; extras défaut `ble,hardware`.

---

## Parallèle — Banc SSH fonctions virtuelles

**Statut :** **clos** (2026-08-25, PO). Projecteur, yeux, HP, antennes **validés sur le robot**. Prompt : `docs/hardware/PROMPT-agent-ssh-test-menu.md`. Décisions **D-016**, **D-017**.

---

## Lot 4 — Commandes hors Tests (dont parallèle marche)

**Statut :** pas démarré. Dépend de l’app BLE (lot 3). **Pas** de manette Xbox (D-018).

**Objectif :** commandes de démo hors sous-menu Tests ; expressions pendant la marche le cas échéant (`v2_rl_walk_mujoco.py` comme référence d’effets, pas comme UI).

---

**Prochaine étape recommandée :** valider halt sur le robot (script sudoers + arrêt réel), **pas** le lot 2 sur le Pi (D-020).
