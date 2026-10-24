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

## Layout

- `data/` – API client, session, diary and exercise-run stores (JSON in app storage, synced to the backend), reminders (AlarmManager), tone synthesis
- `ui/` – Today, Diary, Exercises (with the guided session and confetti) and Settings screens
- `res/values*/strings.xml` – English and Portuguese
