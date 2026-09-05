# PrivacyGate Preflight

Share what they need. Nothing more.

Native Android prototype for event-driven, local inspection of WhatsApp image previews.
Development is currently at the foundation milestone. Protection is not yet implemented or device-verified.

## Build

Pinned baseline: AGP 8.13.2, Gradle 8.13, Kotlin 2.2.21, Compose BOM 2025.10.01.
Android compile/target SDK 36, minimum SDK 30. Java 17+ is required.

With the project-local toolchain:

```powershell
.\scripts\build.ps1
```

With an existing Android installation, configure `JAVA_HOME`, `ANDROID_HOME` (or `local.properties`) and run:

```powershell
.\gradlew.bat assembleDebug
```

Tools and caches in `.tools/` are ignored by Git. The app has no INTERNET permission and disables Android backup.

See [the delivery plan](docs/superpowers/plans/2026-09-05-privacygate-hackathon.md) for scope and physical-device gates.
