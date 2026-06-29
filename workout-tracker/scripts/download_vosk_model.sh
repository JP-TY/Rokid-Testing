#!/usr/bin/env bash
set -euo pipefail

ASSET_DIR="app/src/main/assets"
MODEL_DIR="$ASSET_DIR/model-en-us"
MODEL_NAME="vosk-model-small-en-us-0.15"
MODEL_ZIP="/tmp/${MODEL_NAME}.zip"
MODEL_URL="https://alphacephei.com/vosk/models/${MODEL_NAME}.zip"

if [ -f "$MODEL_DIR/uuid" ]; then
    existing=$(cat "$MODEL_DIR/uuid")
    if [ "$existing" = "en-us-small-0.15-v2" ]; then
        echo "Model already exists at $MODEL_DIR"
        exit 0
    fi
    echo "Replacing existing model ($existing -> en-us-small-0.15-v2)..."
else
    echo "Downloading Vosk model ($MODEL_NAME)..."
fi

curl -# -L -o "$MODEL_ZIP" "$MODEL_URL"

echo "Extracting..."
unzip -q -o "$MODEL_ZIP" -d /tmp/vosk-model-tmp
mkdir -p "$ASSET_DIR"
rm -rf "$MODEL_DIR"
mv "/tmp/vosk-model-tmp/$MODEL_NAME" "$MODEL_DIR"
rm -rf /tmp/vosk-model-tmp

printf 'en-us-small-0.15-v2\n' > "$MODEL_DIR/uuid"
echo "Model ready at $MODEL_DIR"
