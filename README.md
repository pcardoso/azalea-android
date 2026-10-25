# Sway for Android

Native Jetpack Compose port of the SwiftUI app in `../mobile-ios`. Kotlin, Material 3, Android 8.0+ (minSdk 26).

## Build

Needs JDK 17 and the Android SDK (`local.properties` points at `~/Library/Android/sdk`).

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home
./gradlew assembleDebug     # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease   # app/build/outputs/apk/release/app-release.apk
```

Or open the folder in Android Studio.

## API

- Debug: `http://10.0.2.2:3100` (the host machine as seen from the emulator; cleartext allowed in `src/debug`)
- Release: `https://api.gowithsway.com`

Set in `app/build.gradle.kts` via `BuildConfig.API_BASE_URL`.

## Signing

Release builds are signed with the debug key for now so the APK installs directly. Before
publishing on Google Play (or to keep sideloaded updates installable across machines), create a
release keystore and point `signingConfigs` at it.

## Updates

Until the app is on Google Play it is sideloaded from `frontend/public/downloads/sway.apk`. On launch
(and when you tap Version in Settings) the app fetches `downloads/sway.json` from the web app and, if
its `versionCode` is higher than the installed one, offers to open the new APK in the browser, which
downloads it and hands it to the system installer.

To publish a release:

1. Bump `versionCode` (and `versionName`) in `app/build.gradle.kts`.
2. Run `./release.sh` (or `./release.sh "What's new"` to set the `notes` shown in the dialog). It
   builds the release APK, copies it to `frontend/public/downloads/sway.apk` and writes
   `sway.json` there with the same `versionCode` and `versionName`.
3. Deploy the frontend.

Android only installs an update signed with the same key as the installed app, so keep building
releases from the machine whose debug keystore signed the first APK (or move to a release keystore,
which needs one uninstall).

## Layout

- `data/` – API client, session, diary and exercise-run stores (JSON in app storage, synced to the backend), reminders (AlarmManager), tone synthesis
- `ui/` – Today, Diary, Exercises (with the guided session and confetti) and Settings screens
- `res/values*/strings.xml` – English and Portuguese
