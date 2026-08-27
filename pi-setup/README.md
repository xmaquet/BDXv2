# pi-setup — install post-OS (Raspberry Pi)

Source **BDXv2** de l’installation après flash OS. Pas un deuxième runtime Python.

Le package embarqué reste `Open_Duck_Mini_Runtime/` (`pip install -e .`).

## Lancer

Sur le Pi (déjà cloné) :

```bash
bash ~/BDXv2/pi-setup/install.sh
```

Machine neuve (après OS + SSH) :

```bash
curl -fsSL https://raw.githubusercontent.com/xmaquet/BDXv2/main/pi-setup/install.sh | bash
```

Idempotent : venv, overlay I2S, groupes, clone Git existants sont réutilisés. Pas d’écrasement de `~/duck_config.json`. Pas de reboot automatique. Extra **`[control]` / Xbox : non** (D-018). Extra **`[rl]` : non**.

Clone neuf : sparse checkout (`Open_Duck_Mini_Runtime` + `pi-setup` seulement) — pas de `Open_Duck_Mini/` (CAO) ni `docs/` (D-017). Un clone **déjà complet** n’est pas réduit.

## Après install

```bash
bash ~/BDXv2/Open_Duck_Mini_Runtime/scripts/run_bdx_lab.sh
```

I2S MAX98357 : appliqué si absent ; **reboot manuel** si le script signale un changement d’overlay.
