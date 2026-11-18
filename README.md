# Para Po!
A mobile application for proximity-based stop alerts with sensor and cellular fallback positioning (CS 312, Group 13).

## Setup (VS Code, no Android Studio)
1. JDK 17, Android command-line tools, `ANDROID_HOME` set, and `sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0"`.
2. Copy `local.properties.example` to `local.properties`; set `sdk.dir` and `ORS_API_KEY`. Never commit this file.
3. Phone: enable Developer options + USB debugging, then check `adb devices`.
4. Build and install: `./gradlew installDebug` (Windows: `gradlew.bat installDebug`).

## Layout
Package structure follows build guide 7.2 under `app/src/main/java/com/parapo/`.
Tunable values live in `util/Constants.kt`; data models in `storage/Models.kt`; design tokens in `ui/theme/`.
