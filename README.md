# VYRO MUSIC 1.1.2 — Android source project

**Developed by Mohamed Tarek**

## Implemented in this source revision
- Kotlin + Jetpack Compose dark/purple interface.
- Local music discovery through MediaStore after the real Android audio permission is granted.
- Search, song list, album list and basic playback controls.
- One authoritative ExoPlayer inside `MediaSessionService`.
- Activity connects to that service through `MediaController`.
- Audio focus handling and noisy-route handling are configured on the service player.
- Android media session provides system media controls and its media notification.
- In-app sleep timer pauses playback after 5, 10, 15, 30, 45 or 60 minutes while the app process remains alive; the user can cancel it.
- Runtime request for audio-library access and Android 13+ notification permission.
- Legacy `READ_EXTERNAL_STORAGE` permission is declared only through API 32.
- Adaptive launcher icon.
- GitHub Actions workflow builds a debug APK and uploads it as an artifact.
- Target/compile SDK set to API 36.

## Important permission clarification
Android does **not** provide a general runtime dialog called “allow background music playback”. The supported pattern is a declared `mediaPlayback` foreground service with `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, plus a media session. Android/Media3 displays the required media notification while the service is active. `POST_NOTIFICATIONS` is requested on Android 13+ for normal notification visibility. Do not request battery-optimization exemption for ordinary music playback; users retain control over device battery settings.

## Build APK using GitHub
1. Upload the complete project folder contents to a GitHub repository, keeping `.github/workflows/android-apk.yml`.
2. Open the repository's **Actions** tab.
3. Run **Build VYRO MUSIC APK** with **Run workflow**, or push a commit to `main`.
4. Wait for the workflow to finish successfully.
5. Open the completed run and download the `vyro-music-debug-apk` artifact.
6. Extract the artifact to obtain `app-debug.apk`.

The workflow builds a **debug APK**, suitable for installation/testing. It is not a signed production release. A release APK/AAB needs a protected signing key and release build configuration.

## Not yet implemented / not verified
This revision does not claim these features are complete: real multi-band Equalizer/Bass Boost, service-owned Sleep Timer that survives process death, persistent user-created playlists, lyrics lookup/provider and timed lyrics, Crossfade, backup/restore, playback resumption after process/device restart, artwork fetching, Android Auto, and extensive device testing. These require implementation and validation before calling the app feature-complete.

## Validation status
No successful Android Gradle build, emulator run, physical-device test, APK install, or Play Console submission is asserted by this source package. Use the GitHub Actions build first, then test on physical Android devices and fix any build/runtime errors before release.


## Upload ZIP directly through GitHub Actions

For a brand-new empty repository, GitHub needs the ZIP-unpack workflow to exist on the default branch before it can react to an uploaded ZIP. Create `.github/workflows/unpack-zip.yml` once using the bootstrap file supplied alongside this archive. Then upload the project ZIP to the repository root and run **Unpack uploaded project ZIP** from the Actions tab. The workflow safely extracts the archive, commits the project files, and the APK build workflow runs afterward.

The generated APK is a **debug build for testing**, not a signed Play Store release.
