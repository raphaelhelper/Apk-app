# Word Popup Overlay

Tiny native Android app for periodic vocabulary popups over other apps.

## What it does
- Requests Android's "Display over other apps" permission.
- Runs a foreground service after the user taps Start.
- Shows one English word + Vietnamese meaning periodically.
- Tap the popup to close it.
- Default interval: 10 minutes (1–1440 minutes allowed).
- Works offline after installation.

## Build APK on GitHub (phone only)
1. Create a new GitHub repository.
2. Upload the entire contents of this folder, including `.github/workflows/build.yml`.
3. Open the repo's **Actions** tab.
4. Open **Build Android APK**.
5. Tap **Run workflow**.
6. Wait for the workflow to finish.
7. Open the completed workflow run, then open **Artifacts** and download `word-popup-debug-apk`.
8. Extract the ZIP and install the `.apk` on the Samsung A12.

The GitHub runner supplies Android SDK components and Gradle; nothing needs to be installed on the phone for the cloud build.

## First launch
1. Open Word Popup.
2. Tap **Cấp quyền popup nổi** and allow it.
3. Set the interval.
4. Tap **Bắt đầu**.
5. A foreground-service notification will remain while popup mode is running.

## Notes
- Android may show a warning about installing APKs downloaded outside Play Store. This is normal for a debug APK.
- Samsung/Android battery optimization can stop long-running background services. If popups stop later, set the app battery mode to **Unrestricted** in Android settings.
- Android 13+ may ask for notification permission. The overlay itself uses the special "Display over other apps" permission.
- The default words are embedded in `PopupService.kt`; replace the `words` list later with your own vocabulary file.
