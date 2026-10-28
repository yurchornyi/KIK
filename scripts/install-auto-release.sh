#!/bin/bash
# Sets up scripts/auto-release.sh on the owner's Mac, from the main checkout (the one with decoded/, the
# original APK and work/signing): a worktree .worktrees/release on origin/main whose private inputs link to
# this checkout's, and a launchd agent that runs it every 5 minutes while the Mac is awake. Run again to
# update the agent; `launchctl bootout gui/$(id -u)/online.kik.auto-release` stops it.
set -euo pipefail
cd "$(dirname "$0")/.."
MAIN=$(pwd)
for input in decoded RezkaTV-1.2.0.apk work/tools/apktool.jar work/signing/localization.jks; do
  [ -e "$input" ] || { echo "Missing $input: run this from the checkout that builds KIK" >&2; exit 1; }
done
git fetch -q origin main
RELEASE="$MAIN/.worktrees/release"
[ -d "$RELEASE" ] || git worktree add -q --detach "$RELEASE" origin/main
# Linked, never copied: the key stays in one place. .git/info/exclude keeps them out of Git's status.
ln -sfn "$MAIN/decoded" "$RELEASE/decoded"
ln -sfn "$MAIN/RezkaTV-1.2.0.apk" "$RELEASE/RezkaTV-1.2.0.apk"
mkdir -p "$RELEASE/work" "$RELEASE/outputs"
for dir in tools signing media3; do ln -sfn "$MAIN/work/$dir" "$RELEASE/work/$dir"; done
for pattern in /decoded /RezkaTV-1.2.0.apk /work /outputs; do
  grep -qxF "$pattern" "$(git rev-parse --git-common-dir)/info/exclude" || echo "$pattern" >> "$(git rev-parse --git-common-dir)/info/exclude"
done

LABEL=online.kik.auto-release
PLIST="$HOME/Library/LaunchAgents/$LABEL.plist"
LOG="$HOME/Library/Logs/kik-auto-release.log"
mkdir -p "$(dirname "$PLIST")" "$(dirname "$LOG")"
cat > "$PLIST" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>Label</key><string>$LABEL</string>
  <key>ProgramArguments</key><array><string>/bin/bash</string><string>$RELEASE/scripts/auto-release.sh</string></array>
  <key>StartInterval</key><integer>300</integer>
  <key>RunAtLoad</key><true/>
  <key>StandardOutPath</key><string>$LOG</string>
  <key>StandardErrorPath</key><string>$LOG</string>
  <key>ProcessType</key><string>Background</string>
</dict>
</plist>
EOF
launchctl bootout "gui/$(id -u)/$LABEL" 2>/dev/null || true
launchctl bootstrap "gui/$(id -u)" "$PLIST"
echo "KIK auto-release runs every 5 minutes from $RELEASE; log: $LOG"
