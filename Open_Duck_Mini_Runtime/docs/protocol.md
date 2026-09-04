# Protocole de contrôle (Android → Robot)

Ce document définit le **contrat d’échange** entre l’application Android (UI tactile) et le runtime du robot.

## Source de vérité (runtime existant)

Le modèle de commandes et les conventions viennent de :
- `mini_bdx_runtime/mini_bdx_runtime/xbox_controller.py`
- `mini_bdx_runtime/mini_bdx_runtime/buttons.py`
- consommateurs : `scripts/v2_rl_walk_mujoco.py`, `scripts/head_puppet.py`, `scripts/antennas_controller_test.py`

Le runtime “marche RL” utilise :
- `last_commands`: tableau de **7 floats**
- `Buttons`: structure d’états et d’événements (`is_pressed`, `triggered`)
- `left_trigger`, `right_trigger`: **floats [0..1]**

## Objectif du protocole

L’app Android ne doit **pas inventer** un nouveau modèle. Elle doit transmettre une représentation qui permet de reconstruire :
- les mêmes `Buttons.update(...)` (donc les mêmes `triggered`)
- les mêmes commandes analogiques (axes + triggers) utilisées dans `xbox_controller.py`

## Message envoyé (ControllerFrame)

Transport : BLE (GATT write, préférence “write without response” + throttling).

Encodage : **UTF‑8 JSON** (simple, robuste).  
(Une version binaire pourra être ajoutée plus tard si besoin, sans changer la sémantique.)

### Schéma JSON

```json
{
  "v": 1,
  "ts_ms": 1710000000000,
  "seq": 123,
  "axes": { "lx": 0.0, "ly": 0.0, "rx": 0.0, "ry": 0.0 },
  "triggers": { "lt": 0.0, "rt": 0.0 },
  "buttons": { "A": false, "B": false, "X": false, "Y": false, "LB": false, "RB": false },
  "dpad": { "up": false, "down": false },
  "safety": { "estop": false }
}
```

### Contraintes valeurs (sécurité)

- `axes.*` doivent être clampés dans **[-1, 1]**
- `triggers.*` doivent être clampés dans **[0, 1]**
- `triggers.*` : deadzone recommandée **0.1** (comme dans `xbox_controller.py`)
- si `safety.estop == true` : le robot doit considérer la frame comme **neutre**

## Conventions de signe (alignement XboxController)

Dans `xbox_controller.py`, les axes pygame sont multipliés par `-1`.  
Pour émuler le comportement manette physique, l’app Android applique la même convention :

- `lx = -ui.leftStick.x`
- `ly = -ui.leftStick.y`
- `rx = -ui.rightStick.x`
- `ry = -ui.rightStick.y` (actuellement non utilisé côté robot, mais transmis pour compat)

## Reconstruction côté robot (référence)

À réception d’une `ControllerFrame`, le runtime robot doit :

1. Mettre à jour les événements boutons :
   - `Buttons.update(A,B,X,Y,LB,RB,dpad_up,dpad_down)`
2. Produire les commandes analogiques (équivalent `xbox_controller.get_commands()`):
   - mode locomotion (par défaut) :
     - `lin_vel_x = ly * X_RANGE`
     - `lin_vel_y = lx * Y_RANGE`
     - `yaw = rx * YAW_RANGE`
   - mode tête : bascule sur **front montant** de `Y` (comme actuellement)
3. Utiliser `lt/rt` pour les antennes (cf. scripts existants) :
   - `antennas.left = rt`, `antennas.right = lt`

## UI Piloter (APK 1.3.23)

Libellés tablette → `ControllerFrame`. Consommateur prévu : `v2_rl_walk_mujoco.py` via `get_last_command()`. La marche n’est **pas** lancée depuis l’app (lot 4).

Disposition : une rangée **Ant. G** · **Tempo +** · **Tempo −** · **Ant. D** (G à gauche, D à droite) ; sticks **Marche** (gauche) et **Rotation** (droit) alignés en dessous.

| Libellé | Xbox | Champ | Effet prévu (runtime marche) |
|---------|------|--------|------------------------------|
| Stick Marche | Stick gauche | `axes.lx` / `axes.ly` | Vitesse latérale / avant-arrière |
| Stick Rotation | Stick droit X | `axes.rx` | Lacet |
| Stick droit Y | Stick droit Y | `axes.ry` | Transmis, **non utilisé** |
| Pause | A | `buttons.A` | Bascule pause |
| Son | B | `buttons.B` | Son aléatoire |
| Proj. | X | `buttons.X` | Projecteur |
| Tête | Y | `buttons.Y` | Mode tête (sticks = tête) |
| Rythme | LB | `buttons.LB` | Cadence ×1,3 tant que maintenu |
| Tempo + / − | Croix | `dpad.up` / `dpad.down` | Offset de cadence |
| Ant. G | RT | `triggers.rt` | Antenne gauche |
| Ant. D | LT | `triggers.lt` | Antenne droite |
| Stop | — | `safety.estop` | Trame **neutre** |
| *(aucun)* | RB | `buttons.RB` | Toujours `false` ; **non utilisé** |

Yeux, antennes wiggle/pulse, WAV à la demande : menu **Tests**, pas Piloter.

## GATT (BLE)

Pour minimiser les changements, on conserve les UUIDs utilisés dans le prototype Figma :

- **Service** : `12345678-1234-5678-1234-56789abcdef0`
- **TX (Android → Robot)** characteristic (write) : `12345678-1234-5678-1234-56789abcdef1`
- **RX (Robot → Android)** characteristic (notify, optionnel) : `12345678-1234-5678-1234-56789abcdef2`

L’app Android écrit des `ControllerFrame` sur TX.  
Le robot peut envoyer des logs/états sur RX (JSON libre, ex. `{ "type": "log", "level": "info", "message": "..." }`).

## Réception côté robot (Python)

- Module **`mini_bdx_runtime.xbox_bridge`** : parse les mêmes lignes JSON et expose une API **`AndroidBridgeController.get_last_command()`** identique à **`XBoxController.get_last_command()`** (à utiliser dans les scripts RL / tête à la place de la manette physique).
- Transport **TCP** intégré (`python -m mini_bdx_runtime.xbox_bridge --tcp-port 8765`) : une ligne JSON UTF-8 terminée par `\n` par trame (compatible avec un relais Wi‑Fi ou un tunnel).

### Serveur GATT sur la Raspberry Pi (BLE direct tablette ↔ Pi)

- **Rôle** : la tablette est le **central BLE** (client), le Pi le **périphérique** (serveur GATT). Pas besoin de Wi‑Fi pour le contrôle : le lien est **Bluetooth Low Energy**.
- **Paquets** : `bluez`, `pip install -e ".[ble]"` (apporte `bluez-peripheral`). Utilisateur dans le groupe `bluetooth` : `sudo usermod -aG bluetooth $USER` (déconnexion / reconnexion).
- **Commande** : `bdx-ble-robot` ou `python -m mini_bdx_runtime.ble_gatt_server` (options `--name`, `--dump`, `--no-agent`, etc.).
- **Si `does not provide the extra 'ble'`** : le `setup.cfg` du clone est trop ancien → `git pull`, ou installe les deps à la main puis réinstalle le paquet pour les scripts console :
  ```bash
  pip install --no-cache-dir -r extras/requirements-ble.txt
  pip install --no-cache-dir -e .
  ```
  Ensuite `bdx-ble-robot` est dans `.venv/bin/` ; sinon : `python -m mini_bdx_runtime.ble_gatt_server --dump`.
- **Pairing** : l’agent `NoIoAgent` peut exiger des droits élevés ; en cas d’échec, lancer avec `sudo` ou `--no-agent` si le pairing est déjà en place.
- L’app Android scanne le **service UUID** ci-dessus puis écrit sur **TX** ; le Pi réassemble les octets (écritures longues / chunks) et met à jour l’état consommé par `AndroidBridgeController`.

### Dépannage (Pi / BlueZ / pip)

| Symptôme | Cause probable | Piste |
|----------|----------------|--------|
| `does not provide the extra 'ble'` | Clone sur une branche ou un remote sans section `ble` dans `setup.cfg` | Utiliser la branche **`feature/bdx_webui`** du fork **xmaquet**, ou `pip install -r extras/requirements-ble.txt` puis `pip install -e .` |
| `No matching distribution found for bluez-peripheral>=1` | Ancienne contrainte erronée (PyPI n’a pas de 1.x) | `git pull` le fork à jour (`>=0.1.7,<0.2`) |
| `cannot import name 'Service' from 'bluez_peripheral.gatt'` | API 0.1.7 : `Service` est dans `gatt.service` | Mettre à jour `ble_gatt_server.py` depuis la branche du fork |
| `InterfaceNotFoundError: org.bluez.Adapter1` | BlueZ 5.8x : sous `/org/bluez`, certains nœuds ne sont pas des adaptateurs HCI | Version récente du serveur qui **filtre** les objets sans `Adapter1`, ou lancer avec **`--dbus-adapter /org/bluez/hci0`** |
| Bluetooth éteint / tablette « BLE ÉCHEC » et pas de hello | `bdx-ble-robot` pas lancé (allumage seul insuffisant) | `bash ~/BDXv2/Open_Duck_Mini_Runtime/scripts/run_bdx_ble_robot.sh --dump` ou autostart `enable_ble_robot_boot.sh` |
| Pairing / agent | Droits D-Bus | `sudo usermod -aG bluetooth $USER` + reconnexion ; ou `--no-agent` si déjà appairé |
| Avertissement ONNX « GPU device discovery failed » sur Pi | Import `onnxruntime` ailleurs dans la chaîne | Sans impact sur le BLE ; ignorer ou retarder l’import ONNX si les logs gênent |

### Options CLI utiles (`bdx-ble-robot` / `ble_gatt_server`)

- **`--name`** : nom d’affichage en publicité BLE.
- **`--dump`** : affiche périodiquement `get_last_command()` (test sans script RL).
- **`--freq`** : fréquence de la boucle `AndroidBridgeController` (Hz).
- **`--head-only`** : `only_head_control` côté pont.
- **`--no-agent`** : ne pas enregistrer `NoIoAgent` (pairing déjà géré ou souci de permissions).
- **`--no-hello`** : ne pas jouer la séquence de démarrage.
- **`--dbus-adapter PATH`** : chemin D-Bus explicite de l’adaptateur (ex. `/org/bluez/hci0`).

Au lancement de `bdx-ble-robot` : dès la pub BLE, `BLE_OKAY_mini_BDX.wav`, puis attente (~20 s) de l’association Wi‑Fi défaut → `WIFI_OKAY_mini_BDX.wav` ou `WIFI_PROBLEM_mini_BDX.wav` (alors : **Monitoring** / Wi‑Fi dans l’app). Sauf `--no-hello` : après ~10 s, 3 clignements, 4 oscillations d’antennes, `happy1.wav`. Logs : `journalctl -u bdx-ble-robot` et `/tmp/bdx-boot-hello.log`.

Allumage du Pi : le GATT **n’était pas** autostarté. Une fois, hors `pi-setup/install.sh` :

```bash
bash ~/BDXv2/Open_Duck_Mini_Runtime/scripts/enable_ble_robot_boot.sh
```

Ensuite un reboot joue le hello et annonce le BLE. Lancement manuel : `bash ~/BDXv2/Open_Duck_Mini_Runtime/scripts/run_bdx_ble_robot.sh --dump`. Ne pas lancer deux instances à la fois.

## Messages hors ControllerFrame

Ces canaux **ne sont pas** des champs de `ControllerFrame`.

### Arrêt système (D-021)

Message **dédié** (halt Linux / `poweroff`). **Pas** `safety.estop`, **pas** un bouton Xbox, **pas** `type: test`.

```json
{ "type": "halt", "v": 1, "confirm": true }
```

Règles :

- `confirm` **doit** être le booléen JSON `true`. Toute autre valeur → refus, **pas** d’arrêt.
- Effet : `poweroff` (rangement). **Pas** de reboot.
- L’UI n’envoie ce message qu’après une confirmation explicite (case à cocher).

Réponse RX :

```json
{ "type": "halt_ack", "v": 1, "accepted": true, "message": "Arrêt demandé" }
```

`accepted: false` : pas d’arrêt (confirmation manquante, `sudo` refusé, etc.). Après `accepted: true`, le BLE tombe ; l’UI affiche **robot éteint**, pas une erreur de connexion.

Limite physique : l’arrêt OS **ne coupe pas** la batterie. Attendre que le Pi soit mort, **puis** couper l’alimentation.

Droits Pi : l’utilisateur `bdxv2` doit pouvoir `sudo -n /usr/local/bin/bdx-poweroff` (wrapper `systemctl poweroff`). **Ne pas** se fier à `sudo -l -- /sbin/poweroff` : sur ce Pi, `poweroff` est un lien vers `systemctl`, et `bdxv2` a `(ALL) ALL` **avec** mot de passe — le listage réussit, l’arrêt non. Une fois, hors `pi-setup/install.sh` (D-020) :

```bash
bash ~/BDXv2/Open_Duck_Mini_Runtime/scripts/enable_halt_sudo.sh
```

Filet : SSH `sudo poweroff`. À intégrer plus tard dans l’install complète (D-012).

### État robot (accueil)

Télémétrie **lente** (notification RX ~1 s, pas un `ControllerFrame`). Tension = moyenne des STS qui répondent. Si la valeur brute servo est > 20, elle est convertie × 0,1 V. Le bus est `ok` (14/14), `partial` (1…13), ou `down` (0 / pas de port / lib absente).

```json
{ "type": "status", "v": 1, "sts_bus": "ok", "sts_ok": 14, "sts_n": 14, "bus_v": 7.6, "sts_msg": "", "sts": [ {"id": 20, "ok": true} ] }
```

`bus_v` est `null` si aucune tension lisible. L’accueil affiche **—** si le BLE est coupé.

`sts` : un objet par ID (ordre runtime 20–24, 30–33, 10–14). `ok` = le servo a répondu. Polling bus **30 s**.

`sts_msg` si `sts_bus` est `down` : `no_lib` (pyserial/rustypot absents), `no_port` (pas d’adaptateur USB), `no_perm` (groupe `dialout`), `no_reply` (servos muets / pas de VIN).

### Tests accessoires (D-018)

Message **dédié**, **pas** un `ControllerFrame`. Aligné banc SSH (yeux, projecteur, HP, antennes), **sans marche**.

```json
{ "type": "test", "v": 1, "action": "eyes_steady" }
```

`action` :

| Valeur | Banc SSH | Effet |
|--------|----------|--------|
| `eyes_steady` | `1` | Yeux fixe ON/OFF |
| `eyes_blink` | `2` | Clignotement ON/OFF |
| `projector` | `3` | Projecteur ON/OFF |
| `speaker` | `4` | Joue **un** WAV nommé (`sound`, ex. `"happy1.wav"`). **Pas** de tirage aléatoire. |
| `list_sounds` | — | Catalogue des WAV (réponse RX `type: test_catalog`) |
| `antennas_wiggle` | `5` | Oscillation 2 s |
| `antennas_pulse` | `6` | Consigne 90° |

Exemple lecture :

```json
{ "type": "test", "v": 1, "action": "speaker", "sound": "happy1.wav" }
```

Réponses RX :

```json
{ "type": "test_state", "v": 1, "action": "eyes_steady", "active": true, "message": "Yeux : ON (fixe)" }
{ "type": "test_state", "v": 1, "action": "speaker", "active": true, "sound": "happy1.wav", "message": "Haut-parleur : happy1.wav" }
{ "type": "test_catalog", "v": 1, "sounds": ["beep1.wav", "happy1.wav"] }
```

Le robot peut aussi renvoyer un `type: log` (`message` = résultat).

### Mode démo (tête + expressions, hors marche)

Message **dédié**, **pas** un `ControllerFrame`, **pas** `type: test`. Robot **sur support**. L’app **déclenche** un preset ; le Pi **joue** la chorégraphie (pas de stream d’angles).

```json
{ "type": "demo", "v": 1, "action": "start", "preset": "nod" }
{ "type": "demo", "v": 1, "action": "start", "preset": "idle", "period_s": 30 }
{ "type": "demo", "v": 1, "action": "stop" }
{ "type": "demo", "v": 1, "action": "status" }
{ "type": "demo", "v": 1, "action": "list" }
```

`action` :

| Valeur | Effet |
|--------|--------|
| `start` | Lance `preset` (`nod`, `look_around`, `curious`, `idle`, `idle_mix`). Pour `idle` / `idle_mix`, `period_s` optionnel (pause **entre** salves, défaut 30, clamp 5–300). Refus si une démo tourne déjà. |
| `stop` | Arrêt, tête au **repos gravité**, couple tête relâché, accessoires OFF. |
| `status` | État courant (`demo_state`). |
| `list` | Catalogue des presets (`demo_catalog`). |

Presets v1 :

| `preset` | Tête | Expression | Son |
|----------|------|------------|-----|
| `nod` | Hochement pitch autour du repos (+0,05 rad) | Clignotement yeux | `happy1.wav` |
| `look_around` | Yaw ±30° (±0,52 rad) | Clignotement yeux | `beep1.wav` |
| `curious` | Léger yaw + pitch au-dessus du repos ; cou ≤ ±0,15 rad | Projecteur un flash | `lamp.wav` |
| `idle` | **Attente** : boucle jusqu’à Stop. Première salve tout de suite, puis pause `period_s` **entre** salves. Chaque salve **mélange** une chorégraphie tête (tirage `nod` / `look_around` / `curious`) avec son / yeux / projecteur tirés à part. **Antennes : oscillation à chaque salve.** | mix + antennes | mix |
| `idle_mix` | **Attente mix** : boucle jusqu’à Stop. Enchaîne les trois presets **complets** (`nod`, `look_around`, `curious`) dans un **ordre tiré au hasard** (sans répéter le même à cheval sur deux mélanges), avec la même pause `period_s` entre salves. **Antennes : oscillation à chaque salve.** | presets d’origine + antennes | presets d’origine |

Consignes tête **clampées** (`DuckConfig` : `joints_gravity_rest`, `joints_limits_viable`). `head_roll` v1 : **[-0,35 ; +0,07] rad**. Jambes **non** commandées. `HWI.turn_on()` **interdit** (ça couple les 14 axes).

Pendant une démo : `ControllerFrame` **ignoré**. Tests accessoires **refusés**. Halt arrête d’abord la démo. Watchdog **30 s** par preset one-shot ; `idle` / `idle_mix` n’ont pas de limite de durée (Stop ou halt). Poll STS **en pause** (un seul client sur `/dev/ttyACM0`).

Réponses RX :

```json
{ "type": "demo_ack", "v": 1, "accepted": true, "action": "start", "preset": "nod", "message": "Démo : nod" }
{ "type": "demo_state", "v": 1, "running": true, "preset": "nod", "phase": "play" }
{ "type": "demo_catalog", "v": 1, "presets": [{"id": "nod", "label": "Hochement"}] }
```

`accepted: false` : déjà en cours, preset inconnu, bus occupé, etc.

### Santé Pi (Monitoring)

Télémétrie **à la demande**, uniquement quand l’écran Monitoring est ouvert (poll ~8 s). Lectures `/proc` / `statvfs` / thermal sysfs — **pas** de `vcgencmd`, pas de processus listés.

```json
{ "type": "sys", "v": 1 }
```

Réponse RX (JSON court, MTU BLE) :

```json
{ "type": "sys", "v": 1, "load": 0.42, "cpu": 18, "mem": 41, "avail": 280, "tot": 445, "temp": 54.1, "disk": 38, "up": 86400 }
```

| Champ | Sens |
|--------|------|
| `load` | charge 1 min (`/proc/loadavg`) |
| `cpu` | % CPU depuis le poll précédent (`/proc/stat`). **Absent** au premier échantillon. |
| `mem` | % RAM utilisée (`MemAvailable`) |
| `avail` / `tot` | Mo disponibles / totaux |
| `temp` | °C SoC, ou `null` |
| `disk` | % occupé de `/` |
| `up` | secondes depuis boot |

### Wi‑Fi robot (D-023)

Configuration **Wi‑Fi du Pi** via BLE, pour changer de réseau sans câble. **Pas** un `ControllerFrame`, **pas** `type: test`. Le lien tablette ↔ robot **reste BLE** pendant l’association.

```json
{ "type": "wifi", "v": 1, "action": "status" }
{ "type": "wifi", "v": 1, "action": "scan" }
{ "type": "wifi", "v": 1, "action": "join", "ssid": "Maison", "psk": "…", "confirm": true }
{ "type": "wifi", "v": 1, "action": "set_default", "ssid": "Maison", "confirm": true }
```

Règles :

- `action` : `status` | `scan` | `join` | `set_default`.
- `join` : `confirm` **doit** être le booléen JSON `true`. Sinon refus, **aucune** modification réseau.
- `set_default` : `confirm` **doit** être `true`. `ssid` = réseau **par défaut** (prioritaire si visible). `ssid` vide = oublier le défaut.
- `psk` omis ou `""` = réseau ouvert, ou profil NetworkManager déjà connu. Le mot de passe **ne revient jamais** en RX, ni dans les logs robot.
- Pi Zero 2W : **2,4 GHz seulement**. Les BSS 5 GHz ne sont pas proposés.
- Plusieurs profils NetworkManager **conservés** (on n’efface pas l’ancien réseau).
- Si un défaut est enregistré et qu’il apparaît au **scan** alors que le robot est sur un autre SSID, le robot **tente** de s’y associer (profil déjà connu, sans redemander le mot de passe).

Réponses RX :

```json
{ "type": "wifi_state", "v": 1, "ssid": "Maison", "state": "connected", "ip": "192.168.1.12", "rssi": -55, "default_ssid": "Maison", "message": "" }
{ "type": "wifi_scan", "v": 1, "i": 0, "n": 2, "nets": [ { "ssid": "Maison", "rssi": -40, "sec": "psk", "in_use": true, "is_default": true } ] }
{ "type": "wifi_ack", "v": 1, "action": "join", "accepted": true, "message": "Association demandée" }
```

`state` : `connected` | `connecting` | `disconnected` | `failed`.  
`sec` : `open` | `psk`.  
`ssid` / `ip` / `rssi` peuvent être `null` si inconnus.

Scan **asynchrone** : `scan` → `wifi_ack` immédiat, puis un ou plusieurs `wifi_scan` (`i` / `n`, découpage **≤ ~140 octets** pour passer le MTU BLE). Champ optionnel `g` : génération du scan (l’UI ignore un scan plus ancien). L’UI concatène. `n >= 1` ; une liste vide = un chunk `nets: []` (l’UI conserve alors la liste précédente si elle n’était pas vide).

Échec d’association : `wifi_state.state = failed` (et/ou `wifi_ack.accepted = false`). Pas de reconnexion magique vers un réseau quelconque : seule l’association vers le **SSID par défaut**, s’il est visible et déjà connu de NetworkManager, est automatique après un scan.

Le défaut est un SSID mémorisé sur le Pi (`~/bdx_wifi_default.json`), **sans mot de passe**. L’UI le choisit par appui long (ou bouton « Par défaut »).

Droits Pi : wrapper `bdx-wifi` uniquement (pas `nmcli` libre). Une fois, hors `pi-setup/install.sh` (D-020) :

```bash
bash ~/BDXv2/Open_Duck_Mini_Runtime/scripts/enable_wifi_sudo.sh
```

À intégrer plus tard dans l’install complète (D-012). **HYPOTHÈSE** runtime : NetworkManager (`nmcli`) sur Lite Bookworm+. Le JSON ci-dessus ne dépend pas de l’outil.

### Vidéo (D-015, D-022)

**Hors BLE GATT.** Deux temps, lots ultérieurs :
1. Afficher ce que voit le robot (caméra → tablette).
2. Interpréter l’image **sur la tablette** (pseudo-mode IA) pour lancer des actions.

Ne pas encoder de JPEG / flux vidéo sur la caractéristique RX.

