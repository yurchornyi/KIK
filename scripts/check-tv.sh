#!/bin/bash
# The TV app's Java policy checks. They need a JDK, which the Cloudflare Workers
# Builds image lacks, so they run on the owner's Mac: scripts/build.sh calls
# this before every APK build, and it can be run alone at any time.
set -euo pipefail
cd "$(dirname "$0")/.."
JAVA_HOME="${KIK_JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"
J=localization/java/kik/i18n
out=$(mktemp -d)
trap 'rm -rf "$out"' EXIT
"$JAVA_HOME/bin/javac" -encoding UTF-8 -d "$out" $J/UpdatePolicy.java scripts/UpdatePolicyCheck.java $J/QrCode.java scripts/QrCheck.java $J/RecoveryPolicy.java scripts/RecoveryPolicyCheck.java $J/DohPolicy.java scripts/DohCheck.java $J/ImageDecodePolicy.java scripts/ImageDecodeCheck.java $J/FocusScrollPolicy.java scripts/FocusScrollCheck.java $J/OopsPolicy.java scripts/OopsCheck.java
for check in UpdatePolicyCheck QrCheck RecoveryPolicyCheck DohCheck kik.i18n.ImageDecodeCheck kik.i18n.FocusScrollCheck OopsCheck; do
  "$JAVA_HOME/bin/java" -cp "$out" "$check"
done
