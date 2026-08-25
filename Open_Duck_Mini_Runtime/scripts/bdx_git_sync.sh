#!/usr/bin/env bash
# À lancer EN TANT QUE bdxv2 (pas root), après : sudo apt-get install -y git
# Remplace l'arbre scp par un clone GitHub en gardant le venv.
set -euo pipefail

REPO_URL="https://github.com/xmaquet/BDXv2.git"
DEST="${HOME}/BDXv2"
VENV_REL="Open_Duck_Mini_Runtime/.venv"
BACKUP="${HOME}/BDXv2.pre-git"

if ! command -v git >/dev/null 2>&1; then
  echo "git introuvable. D'abord :"
  echo "  sudo apt-get update && sudo apt-get install -y git"
  exit 1
fi

if [[ -d "${DEST}/.git" ]]; then
  echo "Dépôt déjà présent — git pull --ff-only"
  git -C "${DEST}" pull --ff-only
  git -C "${DEST}" log -1 --oneline
  exit 0
fi

if [[ -d "${DEST}" ]]; then
  echo "Sauvegarde de l'arbre scp → ${BACKUP}"
  rm -rf "${BACKUP}"
  mv "${DEST}" "${BACKUP}"
fi

echo "Clone ${REPO_URL} → ${DEST}"
git clone --depth 1 "${REPO_URL}" "${DEST}"

if [[ -d "${BACKUP}/${VENV_REL}" ]]; then
  echo "Restauration du venv"
  mkdir -p "${DEST}/Open_Duck_Mini_Runtime"
  mv "${BACKUP}/${VENV_REL}" "${DEST}/${VENV_REL}"
fi

echo "HEAD :"
git -C "${DEST}" log -1 --oneline
echo "OK. Tu peux supprimer ${BACKUP} plus tard si tout va bien."
