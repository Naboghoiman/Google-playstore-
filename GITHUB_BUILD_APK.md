# Build DJ IMAN 3.4 Native APK with GitHub Actions

This project includes `.github/workflows/build-apk.yml`.

## What the workflow does

- Uses JDK 17.
- Uses Gradle 8.11.1 for Android Gradle Plugin 8.9.2.
- Installs Android API 35 / Build Tools 35.0.0.
- Installs Android NDK 27.0.12077973 and CMake 3.22.1.
- Builds `:app:assembleDebug`.
- Renames the installable result to `DJ-IMAN-3.4-Native-debug.apk`.
- Uploads the APK and its SHA-256 checksum as the GitHub Actions artifact `DJ-IMAN-3.4-Native-APK`.

## After uploading the project to GitHub

1. Open the repository's **Actions** tab.
2. Open **Build DJ IMAN APK**.
3. Choose **Run workflow** if a build did not start automatically.
4. Open the completed workflow run.
5. Download **DJ-IMAN-3.4-Native-APK** from the Artifacts section.
6. Extract the downloaded ZIP and install `DJ-IMAN-3.4-Native-debug.apk` on the Android test device.

The debug APK is Android-debug-key signed so it is installable for testing. It is not the Play Store production-signed release. Keep the production upload keystore private; do not commit it to GitHub.
