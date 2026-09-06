# PrivacyGate

PrivacyGate is a native Android privacy assistant that inspects outgoing image shares locally and helps mask sensitive information before it leaves the device.

The current prototype is built and physically verified on an iQOO 15 running Android 16. It uses bundled on-device ML Kit models and deterministic validators; the app explicitly removes `android.permission.INTERNET` and disables Android backup.

## Current features

- PrivacyGate-first protection dashboard with a modern OLED Compose interface
- WhatsApp and WhatsApp Business image-preview detection through an opt-in Accessibility service
- Protected final-send flow for sensitive previews
- Offline OCR and local detection for Aadhaar, PAN, payment cards, CVV, OTPs, phone numbers, email addresses, invoices, and medical documents
- Aadhaar/card-aware smart masking with last-four preservation
- Native **Share with PrivacyGate** preflight target with per-field mask controls
- Local Prism Photos gallery with People, Docs & IDs, Vehicles, Food, Nature, and Screenshot categories
- On-device photo detail inspection and Magic Studio prototype
- Persistent gallery index for faster warm launches

## Privacy model

- No `INTERNET` permission
- No cloud inference or analytics
- Image scanning and masking happen on the device
- Accessibility access is optional and must be enabled by the user in Android Settings
- Android application backup is disabled

## Stack

- Kotlin 2.2.21
- Jetpack Compose Material 3
- Android Gradle Plugin 8.13.2 / Gradle 8.13
- Compile and target SDK 36; minimum SDK 30
- Bundled ML Kit OCR, image labeling, face detection, and selfie segmentation
- Kotlin coroutines and Coil

## Build

The repository includes scripts that use the ignored project-local JDK and Android SDK under `.tools/`:

```powershell
& '.\scripts\android-env.ps1'
.\gradlew.bat testDebugUnitTest assembleDebug --console=plain
```

Install on a connected Android device:

```powershell
adb -s <device-serial> install -r app\build\outputs\apk\debug\app-debug.apk
```

## Architecture notes

See [Robust Detection Architecture](docs/superpowers/plans/2026-09-06-robust-detection-architecture.md) for the planned full-resolution verdict-cache and picker identity-resolution design.
