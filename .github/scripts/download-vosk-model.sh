#!/usr/bin/env bash
set -euo pipefail

MODEL="vosk-model-small-ru-0.22"
URL="https://alphacephei.com/vosk/models/${MODEL}.zip"
ASSETS_DIR="app/src/main/assets"
TARGET="${ASSETS_DIR}/${MODEL}"

if [ -d "$TARGET" ]; then echo "✓ Vosk уже в assets"; exit 0; fi

mkdir -p "$ASSETS_DIR"
cd /tmp
curl -fL --retry 3 -o "${MODEL}.zip" "$URL"
unzip -q "${MODEL}.zip"

[ -f "${MODEL}/uuid" ] || echo "1234" > "${MODEL}/uuid"

mv "${MODEL}" "$OLDPWD/$ASSETS_DIR/"
echo "✓ Vosk установлен: $TARGET"
