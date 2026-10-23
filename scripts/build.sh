#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${KIK_JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"
SDK="${KIK_ANDROID_SDK:-/opt/homebrew/share/android-commandlinetools}"
BT="$SDK/build-tools/34.0.0"
VERSION="$(python3 -c 'import json; print(json.load(open("localization/release.json"))["version_name"])')"
APK="outputs/KIK-$VERSION.apk"
python3 scripts/localize.py
python3 scripts/simplify.py
MEDIA3="$(scripts/media3.sh)"
rm -rf work/helper-classes work/helper-dex;mkdir -p work/helper-classes work/helper-dex
"$JAVA_HOME/bin/javac" -encoding UTF-8 -source 8 -target 8 -nowarn -cp "$SDK/platforms/android-34/android.jar:$MEDIA3" -d work/helper-classes localization/java/kik/i18n/*.java
(cd work/helper-classes && "$JAVA_HOME/bin/jar" cf ../helper-classes.jar kik)
# R8 keeps KIK's helpers by name and moves ExoPlayer into kik.m3, apart from the app's own obfuscated copy.
"$JAVA_HOME/bin/java" -cp "$BT/lib/d8.jar" com.android.tools.r8.R8 --release --min-api 24 --output work/helper-dex --lib "$SDK/platforms/android-34/android.jar" --pg-conf localization/media3.pro work/helper-classes.jar ${MEDIA3//:/ } 2>&1 | grep -v "MANIFEST.MF" || true
test -f work/helper-dex/classes.dex
python3 - <<'PY'
import zipfile
with zipfile.ZipFile('RezkaTV-1.2.0.apk') as src, zipfile.ZipFile('work/helper.apk', 'w') as dst:
    dst.writestr('AndroidManifest.xml', src.read('AndroidManifest.xml'))
    dst.write('work/helper-dex/classes.dex', 'classes.dex')
PY
"$JAVA_HOME/bin/java" -jar work/tools/apktool.jar d -f -r work/helper.apk -o work/helper-decoded
# Helpers and the player live in their own dex: the app's classes.dex has no room for them.
rm -rf localized/smali_classes2;cp -R work/helper-decoded/smali localized/smali_classes2
# The player's KIK ident, the same clip the web uses.
mkdir -p localized/assets/kik;cp web/kik-intro.mp4 localized/assets/kik/intro.mp4
cp localization/kik-wait.png localized/assets/kik/wait.png;cp localization/kik-wait-logo.png localized/assets/kik/wait-logo.png;cp localization/kik-wait-emblem.png localized/assets/kik/wait-emblem.png
"$JAVA_HOME/bin/java" -jar work/tools/apktool.jar b localized -o work/RezkaTV-UA-unsigned.apk
"$BT/zipalign" -f -p 4 work/RezkaTV-UA-unsigned.apk work/RezkaTV-UA-aligned.apk
mkdir -p work/signing outputs
if [ ! -f work/signing/localization.jks ]; then
    python3 - <<'PY'
import os,secrets
fd=os.open('work/signing/password',os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
with os.fdopen(fd,'w') as f:f.write(secrets.token_urlsafe(32))
PY
    "$JAVA_HOME/bin/keytool" -genkeypair -keystore work/signing/localization.jks -storepass:file work/signing/password -keypass:file work/signing/password -alias localization -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=KIK Local Ukrainian Build'
    chmod 600 work/signing/localization.jks
fi
"$BT/apksigner" sign --ks work/signing/localization.jks --ks-key-alias localization --ks-pass file:work/signing/password --out "$APK" work/RezkaTV-UA-aligned.apk
"$BT/apksigner" verify --verbose "$APK"
"$BT/zipalign" -c -p 4 "$APK"
shasum -a 256 "$APK" > "$APK".sha256
