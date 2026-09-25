# TimbreMini

A small, focused Android app for trimming audio and video files. Pick a file, set an
in/out point on a range slider, preview the selection with looping playback, and export
a clean cut straight to your device's Music/Movies library.

<p align="center">
  <img src="demo.gif" width="300" alt="TimbreMini Demo" />
</p>

## Features

- Trim **audio and video** — MP3, M4A, WAV, MP4, MKV, WebM, and more.
- **Range slider** with 0.5s nudge steppers for precise in/out points.
- **Looping preview** of just the selection, so you hear/see exactly what you'll export.
- **Fast export** — a lossless stream copy first, with an automatic precise re-encode fallback.
- Saves via **MediaStore / Scoped Storage** to `Music/TimbreMini` or `Movies/TimbreMini`.
- Share or open the result directly from the success screen.

## Screenshots

| Home (Dark Mode) | Light Mode | Video Trimming |
| :---: | :---: | :---: |
| <img src="screenshots/home_dark.jpeg" width="240" alt="Home Dark Mode" /> | <img src="screenshots/home_light.jpeg" width="240" alt="Home Light Mode" /> | <img src="screenshots/video_trim.jpeg" width="240" alt="Video Trimming Screen" /> |

| Video Saved Result | Audio Trimming | Audio Saved Result |
| :---: | :---: | :---: |
| <img src="screenshots/video_result.jpeg" width="240" alt="Video Saved Result" /> | <img src="screenshots/audio_trim.jpeg" width="240" alt="Audio Trimming Screen" /> | <img src="screenshots/audio_result.jpeg" width="240" alt="Audio Saved Result" /> |

## Tech

- **Kotlin + Jetpack Compose** (Material 3), single-activity.
- **MVVM** — `TrimViewModel` exposes `StateFlow` state; UI is stateless and observes it.
- **Media3 ExoPlayer** for in-app preview and result playback.
- **FFmpegKit** for the actual trimming.
- `minSdk 24`, `targetSdk 35`, `compileSdk 36`, JDK 17.

## Architecture

```
MainActivity / Compose UI      ← observes state, emits events
        │
   TrimViewModel               ← playback + trim state, ExoPlayer lifecycle
    ├── MediaStorageManager     ← SAF read → cache, MediaStore export
    └── FFmpegTrimmer           ← FFmpeg session, progress, cancellation
```

### Why copy the input to cache first?

FFmpegKit's native layer needs a real, seekable file path — it can't reliably read a
`content://` URI. `MediaStorageManager.prepareMediaItem` copies the picked file into the
app cache once, and everything downstream (metadata, preview, trimming) uses that path.

### Trimming strategy

`FFmpegTrimmer.trim` first attempts a **stream copy** (`-c copy`) — near-instant and
lossless, but it can only cut on keyframes so the start may snap to the nearest one. If
that fails or produces an empty file, it falls back to a **precise re-encode**
(`libx264`/`aac` for video, `aac` for audio). User cancellation is detected via the
session return code and aborts without falling through to the re-encode.

## Permissions

- Reading uses the Storage Access Framework (`GetContent` / `OpenDocument`), which needs
  no runtime permission.
- `WRITE_EXTERNAL_STORAGE` is requested at runtime **only on API ≤ 28**, where inserting
  into MediaStore requires it. On API 29+ scoped storage handles the export with no prompt.

## Build & run

```bash
./gradlew assembleDebug          # build the debug APK
./gradlew installDebug           # install on a connected device/emulator
./gradlew testDebugUnitTest      # run JVM unit tests
```

The APK lands in `app/build/outputs/apk/debug/`.

## Tests

`app/src/test/` holds JVM unit tests for the pure logic (time formatting, trim-bounds
math). UI and media I/O are exercised manually / via instrumented tests.
