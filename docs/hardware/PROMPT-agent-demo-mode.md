# Prompt — agent mode Démo (tête + expressions + sons, robot sur support)

Copier **tout le bloc ci-dessous** dans un **nouvel Agent chat** dédié UI Android / BLE + runtime Pi (même périmètre que `PROMPT-agent-android-ble.md`).

Périmètre : **mode démo** — tête animée (servos 30–33) synchronisée avec **expressions statiques** (yeux, projecteur, antennes) et **sons**, robot **sur son support**, **hors marche RL**.

---

Tu es l’agent dédié **mode Démo BDX** pour le projet BDXv2.

Workspace : dépôt BDXv2 ouvert. Réponds en **français**. Distingue **FAIT** / **HYPOTHÈSE** / **DÉCISION** / **QUESTION OUVERTE**.  
Le PO valide les choix produit ; tu implémentes Android + serveur BLE Pi de façon **bornée**.

## Besoin métier (DÉCISION PO)

Quand le BDX est **sur son support** (pas en marche), l’app doit pouvoir lancer un **mode démo** qui donne un **semblant de vie** :

- mouvements de **tête** (yaw / pitch / roll, cou si utile) **synchronisés** avec ;
- **expressions statiques** déjà disponibles (yeux, projecteur, antennes) ;
- **sons WAV** nommés.

Ce n’est **pas** la marche ONNX, **pas** le mode Piloter (`ControllerFrame`), **pas** un remplacement du sous-menu Tests : c’est un **mode dédié** (écran ou entrée UI claire) qui **orchestre** tête + accessoires + HP.

## Lire d’abord

- `AGENTS.md`
- `docs/decision-log.md` (**D-007**, **D-008**, **D-010**, **D-015**, **D-018**, **D-022**)
- `docs/next-lot.md`
- `docs/current-state.md`
- `Open_Duck_Mini_Runtime/docs/protocol.md` — **étendre** ce fichier **avant** de coder le contrat démo
- `docs/hardware/PROMPT-agent-android-ble.md` (conventions BLE, APK native, Tests)
- `Open_Duck_Mini_Runtime/mini_bdx_runtime/mini_bdx_runtime/ble_gatt_server.py`
- `Open_Duck_Mini_Runtime/mini_bdx_runtime/mini_bdx_runtime/ble_test_actions.py` (`AccessoryTests`)
- `Open_Duck_Mini_Runtime/mini_bdx_runtime/mini_bdx_runtime/rustypot_position_hwi.py` (`HWI`, `bus_from_software`, chemin bus court)
- `Open_Duck_Mini_Runtime/mini_bdx_runtime/mini_bdx_runtime/duck_config.py` (`joints_gravity_rest`, `joints_limits_viable`)
- `Open_Duck_Mini_Runtime/scripts/head_puppet.py` (référence ancienne commande tête manette — **ne pas** réintroduire Xbox)
- `Open_Duck_Mini_Runtime/android_app/` (APK Capacitor natif, **pas** `android_ui/` gelé)

## Limites tête calibrées sur le robot réel (FAIT banc 2026-09-04)

Servos STS : **30** neck_pitch, **31** head_pitch, **32** head_yaw, **33** head_roll.  
Offsets et limites dans `~/duck_config.json` sur le Pi (à lire via `DuckConfig`, **ne pas** hardcoder ailleurs sans fallback config).

| Joint | ID | Repos sans couple (software) | Amplitude max **viable** démo | Symétrie |
|-------|-----|------------------------------|-------------------------------|----------|
| head_yaw | 32 | **0** rad | **±45°** (= **±0,785398 rad**) | **symétrique** ✓ |
| head_pitch | 31 | **+0,05 rad** (`joints_gravity_rest`) | **±0,30 rad** autour du repos | **symétrique** ✓ |
| neck_pitch | 30 | **+0,02 rad** (`joints_gravity_rest`) | *non exploré* — rester **≤ ±0,15 rad** en v1 | HYPOTHÈSE prudente |
| head_roll | 33 | **0** rad | **−20°** (−0,349 rad) OK ; **+≈4°** (+0,07 rad) seulement | **asymétrique** — horn proche ±π ; **recal horns prévu** |

**Règles d’animation obligatoires :**

1. **Clamp** toutes les consignes tête avec `joints_limits_viable` + `joints_gravity_rest` (via `DuckConfig`, pas de magic numbers dupliqués).
2. **head_pitch** : centre = `rest_software("head_pitch")` (**+0,05**), pas 0 horizontal.
3. **head_roll** : en v1, **n’utiliser que des excursions ≤ −0,35 rad et ≥ −0,07 rad côté « négatif software »**, ou amplitude **≤ 0,07 rad** côté positif — **ne pas** commander +0,30 rad roll tant que les horns ne sont pas recalés (PO).
4. Fin de séquence / torque OFF tête : revenir au **repos gravité** (31/30), pas au zéro mécanique horizontal.
5. **Un seul moteur tête sous couple** si test unitaire ; en démo multi-axes, **kp modéré** (12–16), pas de `set_position_all` sur les 14 servos.
6. **Pas de marche** : jambes en repos, `v2_rl_walk_mujoco.py` **non** lancé ; `bdx-ble-robot` reste le service GATT.

## Périmètre OUI — lot démo v1

### 1. Contrat protocolaire (priorité)

Ajouter dans `protocol.md` un message **dédié** (comme `type: test`, **pas** un `ControllerFrame`) :

```json
{ "type": "demo", "v": 1, "action": "start", "preset": "curious" }
{ "type": "demo", "v": 1, "action": "stop" }
{ "type": "demo", "v": 1, "action": "status" }
```

**DÉCISION à proposer au PO** (documenter dans protocol.md avant implémentation) :

- **`preset`** : nom de séquence prédéfinie côté Pi (ex. `curious`, `happy`, `look_left`, `nod`) — liste figée v1, extensible.
- Chaque preset = script **déterministe** : timeline `{t_ms, head: {yaw, pitch, roll, neck}, accessory?, sound? }` clampée aux limites viables.
- **`stop`** : arrêt propre, retour repos gravité tête, accessoires OFF, thread démo join.
- **`status`** : RX `{ "type": "demo_state", "v": 1, "running": true, "preset": "curious", "phase": "…" }`.

Ne **pas** streamer des angles en continu depuis l’app en v1 (trop de trafic BLE / risque sécurité). L’app **déclenche** des presets ; le Pi **joue** la chorégraphie.

### 2. Runtime Pi

- Nouveau module ex. `mini_bdx_runtime/demo_mode.py` ou `ble_demo_actions.py`, dispatch depuis `ble_gatt_server.py` (même pattern que `AccessoryTests`).
- Réutiliser **HWI** + **DuckConfig** (offsets, sign, gravity rest, limits viable).
- Réutiliser **Sounds**, **Eyes**, **Projector**, **Antennas** (comme `ble_test_actions.py`).
- **Exclusivité** : si démo en cours, refuser `ControllerFrame` locomotion ou les ignorer (DÉCISION : documenter — recommandation : démo **bloque** Piloter).
- **Watchdog** : durée max preset (ex. 30 s), stop auto si BLE coupé.
- Au moins **2 presets** livrés pour valider la synchro son + tête (ex. « nod + happy wav », « scan yaw ±30° + clignotement yeux »).

### 3. App Android (APK native)

- Entrée UI claire : **« Mode démo »** (accueil ou sous-menu, **pas** dans Tests accessoires isolés).
- Liste des **presets** + bouton **Stop**.
- Feedback RX : `demo_state`, logs, erreurs clamp.
- **Pas** de joystick tête en v1 démo (réservé Piloter / plus tard).
- Confirmation si un preset est déjà en cours.

### 4. Critères d’acceptation v1

- [ ] Robot sur support : preset démo lance mouvement tête **visible**, **sans** bouger les jambes.
- [ ] Sons et au moins **une** expression (yeux ou projecteur) synchronisés sur **un** preset.
- [ ] Stop ramène la tête au **repos gravité** (pitch ≈ +0,05 rad).
- [ ] Aucune consigne tête ne dépasse les limites viables ci-dessus (tests unitaires ou log clamp).
- [ ] `protocol.md` à jour ; pas de régression Tests / halt / Wi‑Fi / Monitoring.
- [ ] APK version bump + procédure test PO documentée en fin de lot.

## Périmètre NON

- Marche RL / `ControllerFrame` locomotion pendant démo (sauf décision PO contraire).
- Vidéo (D-015, D-022).
- Recalibration horns / STS (PO plus tard) — le code doit **respecter** l’asymétrie roll documentée.
- Cheville **14** / jambes (TODO Feetech séparé).
- Commit / push sauf demande explicite PO.
- Réintroduire manette Xbox comme UI (D-018).

## Références techniques utiles

```python
# duck_config.json (extrait FAIT Pi)
"joints_gravity_rest": { "neck_pitch": 0.02, "head_pitch": 0.05 }
"joints_limits_viable": { "head_yaw": 0.785398, "head_pitch": 0.30 }
# head_roll : pas encore dans limits_viable — appliquer règle asymétrique § ci-dessus
```

Scripts banc utiles (lecture seule / inspiration, **ne pas** exposer tels quels à l’app) :

- `scripts/test_joint_oscillate.py` — oscillation autour repos gravité
- `scripts/go_joint_software_zero.py --gravity-rest`
- `scripts/head_puppet.py` — mapping degrés (limites doc **supérieures** au viable réel : **préférer duck_config**)

## TODO projet laissés ouverts (informer PO, ne pas bloquer v1)

- **Servo 14** (cheville droite) : limites Feetech — hors démo tête.
- **Recal horns** (roll 33 notamment) : amélioration future des presets « inclinaison ».
- **D-008** séquences « mode normal » pendant marche : **hors** de ce lot.

## Démarrage agent

1. Lire `docs/current-state.md` + `~/duck_config.json` sur le Pi si joignable (`bdxv2@<IP DHCP>` ; 2026-09-05 : `10.160.173.117`).
2. Proposer le **schéma JSON final** `type: demo` + **liste des 2–3 presets v1** au PO ; attendre accord.
3. Implémenter **protocol.md → Pi → APK** dans cet ordre.
4. Tester sur banc : preset → stop → repos gravité ; vérifier visuellement avec le PO.
5. Restitution : fichiers touchés, version APK, commandes relance `bdx-ble-robot`.

Si le Pi n’est pas joignable, coder avec `DuckConfig` local / example et laisser la validation hardware au PO.
