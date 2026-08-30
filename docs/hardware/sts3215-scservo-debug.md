# Programmation STS3215 avec FT SCServo Debug v1.9.8.1

Guide procédural pour Open Duck Mini v2 / BDXv2.

**Outil :** logiciel Windows **FT SCServo Debug v1.9.8.1** (pas les scripts Python).  
Les scripts du runtime servent uniquement de **valeurs cibles**.

**Hors périmètre de cette séance :**

- Oreilles / antennes : servos **SG90 PWM** sur GPIO **D12** (droite) et **D13** (gauche) — voir `antennas.py`. **Ne pas** les brancher ni les programmer avec SCServo Debug.
- Offsets d’articulation (`find_soft_offsets.py`, EEPROM offset, `duck_config.json`) : **étape ultérieure**, pas aujourd’hui.
- HAT 2N2222, yeux, projecteur, OS Pi, BLE, app Android.

---

## Légende FAIT / HYPOTHÈSE / DÉCISION

| Marque | Sens |
|--------|------|
| **FAIT** | Vérifié dans les docs / le code du dépôt, ou dans la table mémoire STS publique. |
| **HYPOTHÈSE** | Nom de bouton, onglet ou libellé exact du logiciel — l’UI n’a pas été ouverte ici. |
| **DÉCISION** | Choix local de cette séance, réversible. |

---

## 1. Prérequis matériel

- Un **seul** STS3215 branché sur le dongle USB / carte de debug (URT-1, SCPC-2, ou équivalent).
- Alimentation servos **adaptée et stable** (typiquement 6–8,4 V selon la carte ; **FAIT** Feetech : ne pas alimenter uniquement par l’USB du PC).
- PC Windows avec **FT SCServo Debug v1.9.8.1**, port COM visible dans le Gestionnaire de périphériques.
- Horn **pas encore vissé** (recommandé), ou retirable. Le zéro mécanique n’a pas besoin d’être parfait.
- Marqueur pour écrire l’ID sur le servo une fois programmé.

**Sécurité :**

- Jamais deux servos avec le **même ID** sur le bus.
- Ne **pas forcer** un servo à la main si le couple est activé.
- Si le servo est déjà monté : soutenir la pièce, laisser l’articulation **libre** avant tout mouvement.
- Couper l’alim / désactiver le couple avant de changer de servo.

---

## 2. Table des IDs (FAIT docs + runtime)

Sources : `Open_Duck_Mini/docs/CONFIGURE_MOTORS_FR.md`, `configure_motors.md`, `rustypot_position_hwi.py`.

**14 STS3215** (corps + tête). Aucune antenne ici.

| Articulation | Nom runtime | ID |
|---|---|---|
| Hanche droite yaw | `right_hip_yaw` | **10** |
| Hanche droite roll | `right_hip_roll` | **11** |
| Hanche droite pitch | `right_hip_pitch` | **12** |
| Genou droit | `right_knee` | **13** |
| Cheville droite | `right_ankle` | **14** |
| Hanche gauche yaw | `left_hip_yaw` | **20** |
| Hanche gauche roll | `left_hip_roll` | **21** |
| Hanche gauche pitch | `left_hip_pitch` | **22** |
| Genou gauche | `left_knee` | **23** |
| Cheville gauche | `left_ankle` | **24** |
| Cou pitch | `neck_pitch` | **30** |
| Tête pitch | `head_pitch` | **31** |
| Tête yaw | `head_yaw` | **32** |
| Tête roll | `head_roll` | **33** |

**ID usine typique : 1** (**FAIT** `DEFAULT_ID` dans `configure_motor.py`).  
Si ce n’est pas 1, scanner.

---

## 3. Valeurs cibles runtime (FAIT code)

`configure_motor.py` fait, dans cet ordre, après `set_lock(0)` :

| Paramètre | Valeur cible | Commentaire |
|-----------|--------------|-------------|
| Lock EEPROM | **0** avant écriture | Déverrouiller pour que ça tienne après coupure. |
| Mode | **0** | Position (pas roue / wheel). |
| Max acceleration | **0** | |
| Acceleration | **0** | |
| P | **32** | |
| I | **0** | |
| D | **0** | Usine souvent D=32 — **à ramener à 0**. |
| ID | ID de la table | `change_id` |
| Goal position | **0** (convention runtime) | Puis pose du horn. |

`configure_motor_plus.py` et `rustypot_position_hwi.py` : baud **1 000 000**.

`configure_motor.py` **ne change pas** le baud (**FAIT** : le script ne l’écrit pas). Un servo neuf est en général déjà à 1 Mbps. Si le scan ne trouve rien, essayer d’autres baud (section 8).

`configure_all_motors.py` **reverrouille** (`set_lock(1)`) après les PID. **DÉCISION** de cette séance : reverrouiller après écriture si le logiciel le permet.

**Offset (addr 31) : ne pas toucher aujourd’hui.** C’est `find_soft_offsets.py`.

---

## 4. Correspondance FT SCServo Debug (HYPOTHÈSE UI)

Noms de boutons **non vérifiés** dans v1.9.8.1. S’appuyer sur la **table mémoire** (adresses) plus que sur le libellé.

Table mémoire STS3215 (**FAIT** docs publiques Feetech / STS) :

| Adresse | Nom mémoire | Cible séance | Notes |
|---------|-------------|--------------|-------|
| 5 | ID | ID table (10–14, 20–24, 30–33) | Usine 1. Uniques sur le bus. |
| 6 | Baud Rate | **0** = 1 000 000 | 1=500k, 2=250k, 3=128k, 4=115200, 5=76800, 6=57600, 7=38400. |
| 9 / 11 | Min / Max angle | Laisser **0 / 4095** | Les deux à 0 = mode roue. **Ne pas** mettre les deux à 0. |
| 21 | P Coefficient | **32** | |
| 22 | D Coefficient | **0** | Souvent 32 usine. |
| 23 | I Coefficient | **0** | |
| 31 | Position Offset | **ne pas modifier** | Séance offsets plus tard. |
| 33 | Mode | **0** | 0=position, 1=vitesse, 2=PWM, 3=pas. |
| 40 | Torque Enable | 0 pour écrire EEPROM / poser le horn ; 1 pour aller au zéro | RAM. |
| 41 | Acceleration | **0** | RAM. |
| 42 | Goal Position | voir ci-dessous | |
| 55 | Lock | **0** pour écrire, **1** après | 0 = EEPROM enregistrée ; 1 = protégée. |

**HYPOTHÈSE UI** (logiciel Feetech FD / SCServo Debug) :

- En-tête : **COM**, **Baud Rate** du PC (mettre **1000000**), boutons **Open** puis **Search**.
- Onglet **Program** : table d’adresses. Modifier une ligne, **Save** / **Write**.
- Case **Unlock** / champ **Lock** à 0 avant d’écrire l’EEPROM.
- Debug / movement : **Torque**, consigne de position.

**Piège position 0 :**

- **FAIT** : le runtime envoie `set_goal_position(0)` (zéro **logiciel** pypot / radians).
- **HYPOTHÈSE** : dans SCServo Debug, la case « Position » est souvent **brute 0–4095**. La valeur **0** brute = un **bout de course**, pas le milieu. Le milieu usuel STS est **2048**.
- **DÉCISION** : pour poser le horn, viser le **milieu** (~2048) si l’UI est en 0–4095. Ne jamais envoyer 0 brut si ça envoie le servo en butée.

---

## 5. Ordre recommandé

**DÉCISION** (aligné sur le guide de calibration, une jambe puis l’autre) :

1. Jambe **droite** : 10 → 11 → 12 → 13 → 14  
2. Jambe **gauche** : 20 → 21 → 22 → 23 → 24  
3. **Tête** : 30 → 31 → 32 → 33  

Un servo à la fois. Marquer l’ID sur le boîtier. Ranger à part.

---

## 6. Procédure pour UN servo neuf (8–12 étapes)

Faire **une** étape, vérifier, passer à la suivante.

1. **Un seul** STS3215 sur le bus. Horn non vissé. Alim servos **on**. USB PC branché.
2. Lancer **FT SCServo Debug v1.9.8.1**.
3. **HYPOTHÈSE UI :** choisir le COM, baud PC **1000000**, **Open**.
4. **Search**. Attendu : **un** ID, souvent **1**. Si rien → section 8. Si plusieurs IDs → **débrancher**, un seul moteur.
5. Ouvrir la table (**Program**). Noter ID, baud (addr 6), mode (33), P/I/D, Lock.
6. **Lock = 0** (déverrouiller EEPROM). **Torque = 0** avant d’écrire l’EEPROM si le logiciel le demande (**FAIT** docs Feetech).
7. Écrire, dans l’ordre :
   - Mode **0**
   - Accélérations **0** (si visibles)
   - P **32**, I **0**, D **0**
   - Baud addr 6 = **0** si ce n’est pas déjà 1 Mbps (si tu changes le baud, **rouvrir** ensuite le COM à 1 000 000)
   - **ID** = ID cible de la table  
   Puis **Save**.
8. **Search** à nouveau : un seul servo, **nouvel ID**. Écrire l’ID au marqueur.
9. **DÉCISION :** Lock = **1**.
10. Articulation **libre**. Activer le couple, envoyer la **position milieu** (~2048 si UI 0–4095) — **pas** 0 brut. Attendre l’arrêt.
11. **Couple off**. Poser le horn **aligné au mieux**. Serrer. L’alignement n’a pas besoin d’être parfait.
12. Couper l’alim, débrancher, cocher la checklist. Servo suivant.

---

## 7. Checklist copiable — servo N

```
Servo : _______________   ID cible : ____
Date : _______________

[ ] Un seul moteur sur le bus, alim OK
[ ] COM ouvert, baud PC = 1 000 000
[ ] Search : 1 ID trouvé (usine souvent 1)  → ID lu : ____
[ ] Lock = 0 (EEPROM writable)
[ ] Mode = 0
[ ] Accélérations = 0
[ ] P=32  I=0  D=0
[ ] Baud registre = 0 (1 Mbps)  — ou déjà 1 000 000
[ ] ID écrit = ID cible
[ ] Search : seul l’ID cible apparaît
[ ] ID marqué sur le boîtier
[ ] Lock = 1
[ ] Position milieu (pas 0 brut) puis couple OFF
[ ] Horn posé (alignement approximatif OK)
[ ] Débranché, rangé

Écart / remarque : ________________________________
```

---

## 8. Si le scan ne trouve rien / mauvais baud / ID déjà pris

### Rien au Search

1. Un seul servo, connecteurs bien enfoncés.  
2. **Alim moteur** (USB PC seul ne suffit souvent pas). LED dongle : souvent **une** LED alim OK, **deux** = moteur non alimenté (**HYPOTHÈSE** tutos URT-1).  
3. Driver COM (CH340 / équivalent). Bon port.  
4. Baud PC : **1000000** d’abord, puis 115200, 500000, 250000… (**FAIT** table : codes 0–7).  
5. Autre câble / autre servo pour isoler.

### Search = ID 254 / 255 / fantôme

Souvent **pas d’alim moteur**. Vérifier la LED et le bloc d’alim.

### Plusieurs IDs

**Stop.** Deux servos (ou ID en double) sur le bus. Tout débrancher, un seul moteur.

### ID déjà pris (collision plus tard)

Ne jamais mettre deux servos au même ID. Si doute : reprogrammer **seul** sur le bus. L’ID 1 ne doit plus rester sur un moteur « fini » si un neuf arrive encore en ID 1.

### Écriture ID ignorée

Lock encore à 1, ou couple encore on. Lock 0, torque 0, Save, Search.

### Servo part en butée

Consigne brute **0** au lieu du milieu. Couple off, ne pas forcer. Reprendre à ~2048.

---

## 9. Lien avec `find_soft_offsets.py` (pas cette séance)

**FAIT** (`CALIBRATION_SERVOS_FEETECH_FR.md`) : après montage, le script envoie le zéro **logiciel**, on ajuste la mécanique **couple off**, on enregistre l’offset (`duck_config.json` ou EEPROM).

Aujourd’hui : ID + baud + PID + mode + horn approximatif.  
**Ne pas** écrire l’offset (addr 31) « à l’œil ».

**État BDXv2 (2026-08-30, PO) :** zéro mécanique posé dans FT SCServo Debug ; `joints_offsets` dans `~/duck_config.json` **tous à 0**. Pas de campagne d’offsets logiciels tant que ce zéro tient.

---

## 10. Sources

- `Open_Duck_Mini/docs/CONFIGURE_MOTORS_FR.md`
- `Open_Duck_Mini/docs/configure_motors.md`
- `Open_Duck_Mini/docs/CALIBRATION_SERVOS_FEETECH_FR.md`
- `Open_Duck_Mini_Runtime/scripts/configure_motor.py`
- `Open_Duck_Mini_Runtime/scripts/configure_motor_plus.py`
- `Open_Duck_Mini_Runtime/scripts/configure_all_motors.py`
- `Open_Duck_Mini_Runtime/mini_bdx_runtime/mini_bdx_runtime/rustypot_position_hwi.py`
- `Open_Duck_Mini_Runtime/mini_bdx_runtime/mini_bdx_runtime/antennas.py` (hors bus STS)
- Table mémoire STS3215 publique (adresses 5, 6, 9, 11, 21–23, 31, 33, 40, 41, 55)
