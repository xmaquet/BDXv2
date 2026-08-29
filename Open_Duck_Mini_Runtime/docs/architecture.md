## Architecture Android (UI tablette) → Robot

Objectif : piloter le robot depuis une **tablette Android** avec une UI tactile **native**, en restant aligné sur le modèle runtime existant (`xbox_controller.py` / `Buttons` / `get_last_command()`). Le BLE est un **plugin Kotlin**, pas du Web Bluetooth.

### Vue d’ensemble des composants

| Composant | Rôle |
|-----------|------|
| **`android_app/`** | App **native** (layouts XML + `MainActivity`) : accueil à menus (D-022), plugin **RobotBlePlugin**. Capacitor n’est plus la surface UI ; il peut rester hôte du plugin. |
| **`android_ui/`** | Proto React / Figma **gelé** (D-022). Ne plus y ajouter Tests, halt ni vidéo. |
| **`android_app/.../RobotBlePlugin.kt`** | Scan / connexion / **écriture GATT** sur la caractéristique TX, **notifications** RX, permissions Android modernes, **watchdog natif** et **reconnexion** best-effort. |
| **`mini_bdx_runtime/ble_gatt_server.py`** | Sur la **Raspberry Pi** (Linux + BlueZ) : serveur GATT (`bluez-peripheral`), mêmes **UUID** que le plugin ; réassemble les écritures JSON et met à jour un **`VirtualJoystickState`**. Messages hors pad : tests, halt, **wifi** (D-023), file RX. |
| **`mini_bdx_runtime/xbox_bridge.py`** | **`AndroidBridgeController`** : lit le joystick « virtuel » et expose **`get_last_command()`** comme la manette Xbox pour les scripts RL / tête / antennes. Option **TCP** (`--tcp-port`) pour un relais réseau. |

### Flux de données (BLE direct)

1. L’utilisateur agit dans l’UI **native**.
2. L’activité construit une ligne **JSON** conforme à **`docs/protocol.md`** (`v`, `axes`, `triggers`, `buttons`, `safety`, etc.).
3. **RobotBlePlugin** **écrit** sur la caractéristique **TX** (préférence write-without-response).
4. Sur la Pi, **`ble_gatt_server`** reçoit les octets (y compris **écritures fragmentées**), décode un ou plusieurs objets JSON et appelle **`VirtualJoystickState.apply_json()`**.
5. **`AndroidBridgeController`** (même processus quand lancé via `bdx-ble-robot`) interroge périodiquement l’état et produit les **mêmes `last_commands`** / événements boutons que la chaîne manette.

Schéma simplifié :

```text
[ UI TS ] → [ RobotBlePlugin (Kotlin) ] ──BLE GATT──► [ ble_gatt_server.py ]
                                                              │
                                                              ▼
                                                    [ VirtualJoystickState ]
                                                              │
                                                              ▼
                                                    [ AndroidBridgeController ]
                                                              │
                                                              ▼
                                              scripts RL / tête (get_last_command)
```

### Sécurité et robustesse (rappel)

**Côté Android (déjà intégré dans cette branche)**

- **Clamp** : axes dans \([-1,1]\), triggers dans \([0,1]\).
- **Deadzone** sur les triggers (alignée `xbox_controller.py`).
- **Watchdog** : si l’UI ne fournit plus de frames, envoi de commandes **neutres** (natif + logique TS selon les couches).
- **E-Stop** : `safety.estop` + combo matérielle/logicielle côté natif ; trames neutres lorsque actif.
- **Reconnexion BLE** après perte de lien (best-effort).

**Côté Pi**

- Le robot doit traiter **`safety.estop`** comme une consigne **neutre** (voir `protocol.md`).
- Droits **D-Bus** : utilisateur dans le groupe **`bluetooth`** ; service **`bluetooth`** actif (`systemctl status bluetooth`).

### Build / exécution Android

Prérequis : **Node.js**, **Android Studio**, SDK Android configuré.

Depuis **`android_app/`** :

1. Build de l’UI web : `npm run build:web` (compile `android_ui` et sort les assets dans le dossier attendu par Capacitor).
2. Copie / sync des assets : `npm run sync:web` puis `npm run cap:sync`.
3. Ouvrir le projet Android : `npm run android:open` (ou ouvrir le dossier `android_app/android` dans Android Studio).
4. Déployer sur tablette : run depuis Android Studio (USB ou debug réseau).

Les scripts exacts sont définis dans le `package.json` de `android_app/` ; les noms ci-dessus sont ceux prévus dans cette branche.

### Build / exécution sur la Raspberry Pi

```bash
sudo apt install bluez
sudo usermod -aG bluetooth $USER   # puis re-login
cd Open_Duck_Mini_Runtime
source .venv/bin/activate
pip install --no-cache-dir -e ".[ble]"
bdx-ble-robot
# ou : python -m mini_bdx_runtime.ble_gatt_server --dump
```

Détails des options (`--dbus-adapter`, `--no-agent`, dépannage BlueZ) : **`docs/protocol.md`**.

### Références code

- Contrat JSON et UUID GATT : **`docs/protocol.md`**
- Manette physique de référence : **`mini_bdx_runtime/xbox_controller.py`**, **`buttons.py`**
