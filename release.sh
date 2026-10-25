#!/bin/sh
# Builds the release APK and publishes it to the frontend for sideloaded updates:
# copies it to frontend/public/downloads/sway.apk and writes sway.json from the
# versionCode/versionName in app/build.gradle.kts. Deploy the frontend afterwards.
#
#   ./release.sh                     # keeps the notes already in sway.json
#   ./release.sh "What's new"        # sets the notes shown in the update dialog
#   ./release.sh ""                  # clears the notes
set -e

cd "$(dirname "$0")"
DOWNLOADS=../frontend/public/downloads

if [ -z "$JAVA_HOME" ] && [ -d /Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home ]; then
    export JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home
fi

VERSION_CODE=$(sed -n 's/^ *versionCode *= *\([0-9][0-9]*\).*/\1/p' app/build.gradle.kts)
VERSION_NAME=$(sed -n 's/^ *versionName *= *"\([^"]*\)".*/\1/p' app/build.gradle.kts)
if [ -z "$VERSION_CODE" ] || [ -z "$VERSION_NAME" ]; then
    echo "could not read versionCode/versionName from app/build.gradle.kts" >&2
    exit 1
fi

PUBLISHED_CODE=$(sed -n 's/.*"versionCode" *: *\([0-9][0-9]*\).*/\1/p' "$DOWNLOADS/sway.json" 2>/dev/null)
if [ -n "$PUBLISHED_CODE" ] && [ "$VERSION_CODE" -le "$PUBLISHED_CODE" ]; then
    echo "\033[33mwarning: versionCode $VERSION_CODE is not higher than the published $PUBLISHED_CODE, installed apps won't see an update\033[0m"
fi

echo "\033[34mbuilding sway $VERSION_NAME ($VERSION_CODE)\033[0m"
./gradlew assembleRelease

cp app/build/outputs/apk/release/app-release.apk "$DOWNLOADS/sway.apk"

if [ $# -gt 0 ]; then
    NOTES_MODE=set
else
    NOTES_MODE=keep
fi
python3 - "$DOWNLOADS/sway.json" "$VERSION_CODE" "$VERSION_NAME" "$NOTES_MODE" "$1" <<'PY'
import json, sys
path, code, name, mode, notes = sys.argv[1:6]
try:
    with open(path) as f:
        old = json.load(f)
except (OSError, ValueError):
    old = {}
data = {"versionCode": int(code), "versionName": name, "url": "sway.apk"}
if mode == "set":
    if notes:
        data["notes"] = notes
elif old.get("notes"):
    data["notes"] = old["notes"]
with open(path, "w") as f:
    json.dump(data, f, indent=4, ensure_ascii=False)
    f.write("\n")
PY

echo "\033[34mpublished to frontend/public/downloads\033[0m"
cat "$DOWNLOADS/sway.json"
printf '\a'
