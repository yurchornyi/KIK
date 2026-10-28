#!/bin/bash
# On-device regression suite. Builds an isolated instrumentation APK using the local KIK key.
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${KIK_JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"
SDK="${KIK_ANDROID_SDK:-/opt/homebrew/share/android-commandlinetools}"
BT="$SDK/build-tools/34.0.0"
DEVICE="${KIK_DEVICE:-192.168.1.127:5555}"
mkdir -p work/audit-classes work/audit-dex outputs/design-audit
"$JAVA_HOME/bin/javac" -encoding UTF-8 -source 8 -target 8 -cp "$SDK/platforms/android-34/android.jar:work/helper-classes:$(scripts/media3.sh)" -d work/audit-classes tests/device/*.java
"$BT/d8" --min-api 26 --output work/audit-dex work/audit-classes/kik/qa/*.class
"$BT/aapt2" link -I "$SDK/platforms/android-34/android.jar" --manifest tests/device/AndroidManifest.xml -o work/audit-base.apk
python3 - <<'PY'
import zipfile
with zipfile.ZipFile('work/audit-base.apk') as src,zipfile.ZipFile('work/audit-unsigned.apk','w') as dst:
 for n in src.namelist():dst.writestr(n,src.read(n))
 dst.write('work/audit-dex/classes.dex','classes.dex')
PY
"$BT/zipalign" -f -p 4 work/audit-unsigned.apk work/audit-aligned.apk
"$BT/apksigner" sign --ks work/signing/localization.jks --ks-key-alias localization --ks-pass file:work/signing/password --out work/KIK-audit.apk work/audit-aligned.apk
if [ "${KIK_AUDIT_BUILD_ONLY:-0}" = 1 ]; then exit 0; fi
trap 'adb -s "$DEVICE" uninstall kik.audit >/dev/null 2>&1 || true' EXIT
adb -s "$DEVICE" install -r work/KIK-audit.apk
adb -s "$DEVICE" shell input keyevent 224
if [ "${KIK_AUDIT_MODE:-full}" = onboarding ]; then
 adb -s "$DEVICE" shell am instrument -w -r kik.audit/kik.qa.OnboardingAudit | tee outputs/design-audit/onboarding-instrumentation.txt
 adb -s "$DEVICE" pull /sdcard/Android/data/kik.app/files/design-audit/onboarding.json outputs/design-audit/onboarding.json
 adb -s "$DEVICE" pull /sdcard/Android/data/kik.app/files/design-audit/onboarding-selected.png outputs/design-audit/onboarding-selected.png
 python3 -c 'import json; assert json.load(open("outputs/design-audit/onboarding.json"))["passed"]'
 adb -s "$DEVICE" shell am start -n kik.app/kik.i18n.HomeActivity
 exit 0
fi
if [ "${KIK_AUDIT_MODE:-full}" = loading ]; then
 adb -s "$DEVICE" shell am instrument -w -r -e label measured kik.audit/kik.qa.LoadingAudit | tee outputs/design-audit/loading-instrumentation.txt
 adb -s "$DEVICE" pull /sdcard/Android/data/kik.app/files/design-audit/loading-measured.json outputs/design-audit/loading-measured.json
 python3 -c 'import json; assert json.load(open("outputs/design-audit/loading-measured.json"))["passed"]'
 adb -s "$DEVICE" shell am start -n kik.app/kik.i18n.HomeActivity
 exit 0
fi
if [ "${KIK_AUDIT_MODE:-full}" = recommendations ]; then
 adb -s "$DEVICE" shell am instrument -w -r kik.audit/kik.qa.RecommendationAudit | tee outputs/design-audit/recommendation-instrumentation.txt
 adb -s "$DEVICE" pull /sdcard/Android/data/kik.app/files/design-audit/recommendations.json outputs/design-audit/recommendations.json
 python3 -c 'import json; assert json.load(open("outputs/design-audit/recommendations.json"))["passed"]'
 exit 0
fi
if [ "${KIK_AUDIT_MODE:-full}" = performance ]; then
 adb -s "$DEVICE" shell am instrument -w -r kik.audit/kik.qa.UiPerformanceAudit | tee outputs/design-audit/ui-performance-instrumentation.txt
 adb -s "$DEVICE" pull /sdcard/Android/data/kik.app/files/design-audit/ui-performance.json outputs/design-audit/ui-performance.json
 python3 -c 'import json; r=json.load(open("outputs/design-audit/ui-performance.json")); print({k:r[k] for k in ("frames","frame_p50_ms","frame_p95_ms","frames_over_32ms") if k in r}); assert r["passed"]'
 adb -s "$DEVICE" shell am start -n kik.app/kik.i18n.HomeActivity
 exit 0
fi
if [ "${KIK_AUDIT_MODE:-full}" = oops ]; then
 adb -s "$DEVICE" shell am instrument -w -r kik.audit/kik.qa.OopsAudit | tee outputs/design-audit/oops-instrumentation.txt
 mkdir -p outputs/design-audit/oops;adb -s "$DEVICE" pull /sdcard/Android/data/kik.app/files/oops-audit/. outputs/design-audit/oops/ >/dev/null
 python3 -c 'import json; r=json.load(open("outputs/design-audit/oops/oops.json")); print({k:r[k] for k in r if k.startswith("frames_")}); assert r["passed"]'
 adb -s "$DEVICE" shell am start -n kik.app/kik.i18n.HomeActivity
 exit 0
fi
if [ "${KIK_AUDIT_MODE:-full}" = accounts ]; then
 adb -s "$DEVICE" shell am instrument -w -r kik.audit/kik.qa.AccountsAudit | tee outputs/design-audit/accounts-instrumentation.txt
 for f in accounts.json accounts-picker.png accounts-settings.png; do adb -s "$DEVICE" pull /sdcard/Android/data/kik.app/files/design-audit/$f outputs/design-audit/$f >/dev/null; done
 python3 -c 'import json; assert json.load(open("outputs/design-audit/accounts.json"))["passed"]'
 exit 0
fi
AUDIT_SCREEN_ARGS=(-e noScreenshots false)
if [ "${KIK_AUDIT_NO_SCREENSHOTS:-0}" = 1 ]; then AUDIT_SCREEN_ARGS=(-e noScreenshots true); fi
adb -s "$DEVICE" shell am instrument -w -r "${AUDIT_SCREEN_ARGS[@]}" kik.audit/kik.qa.AuditRunner | tee outputs/design-audit/instrumentation.txt
adb -s "$DEVICE" shell am instrument -w -r "${AUDIT_SCREEN_ARGS[@]}" -e geometry true kik.audit/kik.qa.AuditRunner | tee outputs/design-audit/geometry.txt
python3 - "$DEVICE" <<'PY'
import subprocess,re,sys,json
from pathlib import Path
adb=['adb','-s',sys.argv[1]];remote='/sdcard/Android/data/kik.app/files/design-audit/'
for name in subprocess.check_output(adb+['shell','ls',remote],text=True).splitlines():
 if re.fullmatch(r'\d+-[a-z-]+\.png|report\.json|geometry\.json',name):subprocess.run(adb+['pull',remote+name,str(Path('outputs/design-audit')/name)],check=True,capture_output=True)
for name in ['report.json','geometry.json']:
 report=json.loads((Path('outputs/design-audit')/name).read_text());assert report['passed'],name
print('Functional and focus geometry audits passed.')
PY
adb -s "$DEVICE" shell am start -n kik.app/kik.i18n.HomeActivity
