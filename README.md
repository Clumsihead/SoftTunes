# SoftTunes

Offline Android music player (Kotlin, Jetpack Compose, Media3/ExoPlayer, Room) with a
YouTube-link-to-MP3 tab powered by yt-dlp (Python) + FFmpeg via the youtubedl-android library.

## Build
1. Open this folder in Android Studio (Ladybug or newer). It will create the Gradle wrapper.
3. Run on a phone/emulator (Android 10+, arm64 or x86_64).

## Where things are
- `PlaybackService.kt`  – ExoPlayer + MediaSession (gapless, float/hi-res output, notification controls)
- `Scanner.kt`          – MediaStore scan + folder (SAF) scan
- `Data.kt`             – Room: songs, playlists, playlist entries
- `Ui.kt`               – soft-toned Library / Playlists / Convert / Now Playing screens

## Releasing with GitHub (no Android Studio needed)
Repo layout: the repo root is this folder (`settings.gradle.kts`, `app/`, `.github/`, `release.bat`).

1. Double-click `release.bat`. First run it asks for your GitHub repo URL; every run asks for a version
   (e.g. `1.0.1`), commits everything, pushes, and pushes the tag `v1.0.1`.
2. The tag triggers `.github/workflows/release.yml`, which builds the APK on GitHub (a few minutes).
3. Open **Releases** on GitHub: a **draft** release with `SoftTunes-v1.0.1.apk` attached is waiting.
   Review it, press **Publish release** (or just download the APK from the draft).
   You can also run the workflow by hand from the **Actions** tab.

### Signing (optional but recommended)
Without a key, each build is signed with a throwaway key, so a new APK will NOT install over an older one
(uninstall first, which also wipes playlists). To fix that, create one keystore and add four repo secrets
(Settings > Secrets and variables > Actions): `KEYSTORE_BASE64` (the .jks file, base64), `KEYSTORE_PASSWORD`,
`KEY_ALIAS`, `KEY_PASSWORD`. Keep a backup of the keystore; never commit it (`.gitignore` blocks it).
