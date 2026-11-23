# SoftTunes

Offline Android music player (Kotlin, Jetpack Compose, Media3/ExoPlayer, Room) with a
YouTube-link-to-MP3 tab powered by Python (Chaquopy + yt-dlp + PyAV).

## Build
1. Open this folder in Android Studio (Ladybug or newer). It will create the Gradle wrapper.
2. Chaquopy needs a **Python 3.11** on your computer for pip at build time
   (`python3.11` on PATH, or set `buildPython` in `app/build.gradle.kts`).
3. Run on a phone/emulator (Android 10+, arm64 or x86_64).

## Where things are
- `PlaybackService.kt`  – ExoPlayer + MediaSession (gapless, float/hi-res output, notification controls)
- `Scanner.kt`          – MediaStore scan + folder (SAF) scan
- `Data.kt`             – Room: songs, playlists, playlist entries
- `Ui.kt`               – soft-toned Library / Playlists / Convert / Now Playing screens
- `python/converter.py` – yt-dlp download + PyAV MP3 encode
