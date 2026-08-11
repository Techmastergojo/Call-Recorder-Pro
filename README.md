# RecordPro 🎙️

A premium Android call recorder companion app — records **both SIM calls and WhatsApp calls** with full two-sided audio capture. No root required.

[![Build APK](https://github.com/Techmastergojo/Call-Recorder-Pro/actions/workflows/build.yml/badge.svg)](https://github.com/Techmastergojo/Call-Recorder-Pro/actions/workflows/build.yml)

## Features

- 📞 **SIM Calls** — reads recordings from Samsung's built-in call recorder
- 💬 **WhatsApp Calls** — records both voices (yours + other side) via MediaProjection API
- 🗂️ **Time Filters** — Today / 7 Days / 30 Days / All
- 🔍 **Search** — by caller name or phone number
- 🃏 **Rich Cards** — caller name, number, direction (in/out), date, duration
- ▶️ **Built-in Player** — animated waveform, seekbar, skip ±10s
- 🗑️ **Share & Delete** recordings
- 🌙 **Dark theme** — premium navy + electric blue design

## How It Works

| Call Type | Recording Method |
|-----------|-----------------|
| SIM Calls | Samsung's built-in recorder saves to `/sdcard/Call/Recordings/` — we read those files |
| WhatsApp | `AudioPlaybackCaptureConfiguration` (other person's voice) + `AudioRecord MIC` (your voice) → mixed into WAV |

## Setup on Phone

### SIM Calls
1. Open **Phone app** → ⋮ → **Settings** → **Record calls** → **ON (All calls)**

### WhatsApp Calls
1. Install RecordPro APK
2. Grant all permissions (storage, call log, contacts, microphone)
3. Go to **Settings → Accessibility → RecordPro** → Enable
4. On first WhatsApp call → tap **Allow** on the capture permission dialog

## Download APK

👉 Go to [**Actions**](../../actions) → Latest build → **Artifacts** → Download `RecordPro-APK`

## Building Locally

Requirements: JDK 17, Android SDK 34

```bash
git clone https://github.com/Techmastergojo/Call-Recorder-Pro.git
cd Call-Recorder-Pro

# Download gradle wrapper (one time)
curl -o gradle/wrapper/gradle-wrapper.jar \
  https://github.com/gradle/gradle/raw/v8.9.0/gradle/wrapper/gradle-wrapper.jar

./gradlew assembleDebug
# APK → app/build/outputs/apk/debug/app-debug.apk
```

## Permissions

| Permission | Purpose |
|-----------|---------|
| `READ_MEDIA_AUDIO` | Read Samsung call recordings |
| `READ_CALL_LOG` | Get caller names & directions |
| `READ_CONTACTS` | Resolve contact names |
| `RECORD_AUDIO` | Capture your voice in WhatsApp calls |
| `FOREGROUND_SERVICE_MEDIA_PROJECTION` | Background WhatsApp recording |
| `BIND_ACCESSIBILITY_SERVICE` | Detect WhatsApp call state |

## Tech Stack

- **Kotlin** + **Jetpack Compose** (Material 3)
- **ExoPlayer** (Media3) for audio playback
- **AudioPlaybackCaptureConfiguration** (Android 10+) for WhatsApp audio
- **AccessibilityService** for WhatsApp call detection
- **Kotlin Coroutines + Flow** for async state

## License

MIT — Free to use, modify, and distribute.
