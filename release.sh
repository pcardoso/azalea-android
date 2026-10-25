#!/bin/sh
# Bumps the version in app/build.gradle.kts, builds the release APK and publishes it
# to the frontend for sideloaded updates: copies it to frontend/public/downloads/sway.apk
# and writes sway.json to match. Deploy the frontend afterwards.
#
# versionCode always goes up by one; versionName bumps its patch number unless told otherwise.
#
#   ./release.sh                     # 1.1.0 -> 1.1.1, keeps the notes already in sway.json
#   ./release.sh --minor             # 1.1.0 -> 1.2.0
#   ./release.sh --major             # 1.1.0 -> 2.0.0
#   ./release.sh "What's new"        # sets the notes shown in the update dialog
#   ./release.sh ""                  # clears the notes
set -e

BUMP=patch
case "$1" in
    --major|--minor|--patch) BUMP=${1#--}; shift ;;
esac

cd "$(dirname "$0")"
DOWNLOADS=../frontend/public/downloads

if [ -z "$JAVA_HOME" ] && [ -d /Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home ]; then
    export JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home
fi

GRADLE=app/build.gradle.kts
OLD_CODE=$(sed -n 's/^ *versionCode *= *\([0-9][0-9]*\).*/\1/p' $GRADLE)
OLD_NAME=$(sed -n 's/^ *versionName *= *"\([0-9][0-9]*\.[0-9][0-9]*\.[0-9][0-9]*\)".*/\1/p' $GRADLE)
if [ -z "$OLD_CODE" ] || [ -z "$OLD_NAME" ]; then
    echo "could not read versionCode/versionName (x.y.z) from $GRADLE" >&2
    exit 1
fi

# Never go below what's already published, in case the gradle file fell behind.
PUBLISHED_CODE=$(sed -n 's/.*"versionCode" *: *\([0-9][0-9]*\).*/\1/p' "$DOWNLOADS/sway.json" 2>/dev/null)
[ -n "$PUBLISHED_CODE" ] && [ "$PUBLISHED_CODE" -gt "$OLD_CODE" ] && OLD_CODE=$PUBLISHED_CODE
VERSION_CODE=$((OLD_CODE + 1))

MAJOR=${OLD_NAME%%.*}; REST=${OLD_NAME#*.}; MINOR=${REST%%.*}; PATCH=${REST#*.}
case $BUMP in
    major) VERSION_NAME="$((MAJOR + 1)).0.0" ;;
    minor) VERSION_NAME="$MAJOR.$((MINOR + 1)).0" ;;
    patch) VERSION_NAME="$MAJOR.$MINOR.$((PATCH + 1))" ;;
esac

# Put the gradle file back if the build fails, so a retry doesn't skip a version.
cp $GRADLE $GRADLE.bak
trap 'mv $GRADLE.bak $GRADLE; echo "\033[31mbuild failed, version left at $OLD_NAME\033[0m" >&2' EXIT
sed -i '' -e "s/^\( *versionCode *= *\)[0-9][0-9]*/\1$VERSION_CODE/" \
    -e "s/^\( *versionName *= *\)\"[^\"]*\"/\1\"$VERSION_NAME\"/" $GRADLE

echo "\033[34mbuilding sway $VERSION_NAME ($VERSION_CODE)\033[0m"
./gradlew assembleRelease
trap - EXIT
rm $GRADLE.bak

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
