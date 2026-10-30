# Media Optimizer

[English](README.md) | [中文](README.zh-CN.md)

Android app that batch-converts and compresses photos to modern formats (HEIF/HEIC, WebP, JPEG), keeps EXIF metadata (shooting time, GPS location, camera info), and gathers the results into one single gallery folder.

- **Open source** (Apache-2.0), free, no ads, no in-app purchases
- **100% local processing** - photos never leave your device
- **Zero permissions** - uses the system PhotoPicker, no storage access needed
- **Originals are never deleted** - verify results first, then delete originals yourself
- 中文 / English (follows system language)

## Why

Phones fill up with mixed photos: camera shots, camera SD-card imports, web downloads - all in different formats and scattered across many folders. Re-encoding to HEIF/WebP at high quality typically shrinks files 30-50% with no visible quality loss.

> Note: re-encoding is always lossy. The goal is *visually imperceptible* quality loss, not mathematically lossless compression.

## Features

- Batch select photos via the system PhotoPicker (no permissions required)
- Convert to **HEIF** (uses `HeifWriter`, falls back to JPEG if the device can't encode HEIF), **JPEG**, or **WebP**
- Adjustable quality slider (60-100)
- **EXIF preservation**: shooting time, GPS location, camera/lens model are copied to the converted file, so the photo keeps its place in the Gallery timeline and map
  - JPEG / WebP output: full EXIF copied via `ExifInterface`
  - HEIF output: the raw EXIF block is embedded during encoding for JPEG sources (covers camera shots, SD-card imports, most downloads); for HEIC/PNG/WebP sources, file-level EXIF cannot be rewritten into HEIF, so the Gallery timeline still matches via MediaStore timestamps, but file-level GPS may not be carried over
- Converted photos are written to a single aggregated folder: `Pictures/MediaOptimizer`
- MediaStore timestamps are aligned with the original shooting time
- Bilingual UI (English / 简体中文)
- Dark mode follows the system

## Build

### Requirements

- Android Studio (latest stable)
- JDK 17
- Android SDK 35

### Steps

```bash
git clone https://github.com/MarvinDi/media-optimizer.git
cd media-optimizer
```

Open the project in Android Studio, wait for Gradle sync, then:

**Build > Build Bundle(s) / APK(s) > Build APK(s)**

APK output: `app/build/outputs/apk/debug/app-debug.apk`

A debug APK is also built automatically by GitHub Actions on every push to `main` and attached to the workflow run artifacts.

## Usage

1. Tap **Select photos** and pick one or more photos (the system picker also lets you select from multiple albums/folders)
2. Choose the output format (HEIF recommended) and quality
3. Keep **Keep shooting time & location (EXIF)** enabled (default) so timeline/map views stay correct
4. Tap **Convert** and wait for progress to finish
5. Open your Gallery, check `Pictures/MediaOptimizer` and compare with the originals
6. When you are satisfied, delete the originals manually

## Roadmap

- [ ] Video transcoding (H.265/HEVC batch compression)
- [ ] Optional "replace original" flow with a backup/undo window
- [ ] Scan-and-aggregate from multiple source folders in one pass
- [ ] JPEG XL / AVIF output (once device support is widespread enough)

## Privacy

- No ads, no analytics, no tracking, no network calls of any kind
- No storage permission is requested; file access happens only through the system PhotoPicker and for files the app creates itself via MediaStore

## License

[Apache-2.0](LICENSE)
