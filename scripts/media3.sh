#!/bin/bash
# Fetches the pinned ExoPlayer (Media3) jars into work/media3 and prints the javac classpath.
set -euo pipefail
cd "$(dirname "$0")/.."
DIR=work/media3;mkdir -p "$DIR/x"
while IFS=$'\t' read -r file url sum; do
 case "$file" in ''|'#'*) continue;; esac
 if [ ! -f "$DIR/$file" ] || [ "$(shasum -a 256 "$DIR/$file" | cut -d' ' -f1)" != "$sum" ]; then curl -sfL -o "$DIR/$file.part" "$url";mv "$DIR/$file.part" "$DIR/$file";fi
 [ "$(shasum -a 256 "$DIR/$file" | cut -d' ' -f1)" = "$sum" ] || { echo "Checksum mismatch: $file" >&2;exit 1; }
 case "$file" in *.aar) mkdir -p "$DIR/x/${file%.aar}";unzip -oq "$DIR/$file" classes.jar -d "$DIR/x/${file%.aar}";echo "$DIR/x/${file%.aar}/classes.jar";; *) echo "$DIR/$file";; esac
done < localization/media3.tsv | paste -sd: -
