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
mkdir -p work/helper-classes work/helper-dex
"$JAVA_HOME/bin/javac" -encoding UTF-8 -source 8 -target 8 -cp "$SDK/platforms/android-34/android.jar" -d work/helper-classes localization/java/kik/i18n/*.java
"$BT/d8" --min-api 24 --output work/helper-dex work/helper-classes/kik/i18n/*.class
python3 - <<'PY'
import zipfile
with zipfile.ZipFile('RezkaTV-1.2.0.apk') as src, zipfile.ZipFile('work/helper.apk', 'w') as dst:
    dst.writestr('AndroidManifest.xml', src.read('AndroidManifest.xml'))
    dst.write('work/helper-dex/classes.dex', 'classes.dex')
PY
"$JAVA_HOME/bin/java" -jar work/tools/apktool.jar d -f -r work/helper.apk -o work/helper-decoded
cp work/helper-decoded/smali/kik/i18n/*.smali localized/smali/kik/i18n/
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
