# Gemma 4 Photo Enrichment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Integrate an optional Gemma 4 E2B IT one-photo enrichment flow while preserving PrivacyGate's existing ML Kit protection behavior.

**Architecture:** A small `GemmaPhotoEnricher` boundary owns LiteRT-LM and returns a sealed result instead of throwing into gallery code. The gallery invokes it explicitly for one photo, merges validated structured metadata into its existing cache, and treats the model as optional. ML Kit remains the immediate and authoritative privacy layer.

**Tech Stack:** Kotlin, Jetpack Compose, LiteRT-LM 0.17.0, Gemma 4 E2B IT `.litertlm`, bundled ML Kit, JUnit 4, Android API 36.

---

### Task 1: Preserve and isolate the working baseline

**Files:**
- Modify: `.gitignore`

- [ ] Build and test the unmodified app with `./gradlew testDebugUnitTest assembleDebug`.
- [ ] Copy the APK to `artifacts/backups/privacygate-pre-gemma4-<commit>-<timestamp>.apk` and record its SHA-256.
- [ ] Ignore `*.litertlm`, model directories, and benchmark output.
- [ ] Create the `codex/gemma4-enrichment` branch.

### Task 2: Define enrichment data and parsing with TDD

**Files:**
- Create: `app/src/main/java/com/privacygate/app/ai/gemma/GemmaEnrichment.kt`
- Create: `app/src/main/java/com/privacygate/app/ai/gemma/GemmaResponseParser.kt`
- Test: `app/src/test/java/com/privacygate/app/ai/gemma/GemmaResponseParserTest.kt`

- [ ] Write tests proving valid fenced/unfenced JSON is normalized and bounded.
- [ ] Run the targeted test and confirm it fails because the parser is absent.
- [ ] Implement the sealed status/result model and strict parser.
- [ ] Run the targeted test and all unit tests.

### Task 3: Add optional runtime boundary with TDD

**Files:**
- Create: `app/src/main/java/com/privacygate/app/ai/gemma/GemmaModelLocator.kt`
- Create: `app/src/main/java/com/privacygate/app/ai/gemma/GemmaPhotoEnricher.kt`
- Test: `app/src/test/java/com/privacygate/app/ai/gemma/GemmaModelLocatorTest.kt`
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] Write tests for present/missing/undersized model files and confirm RED.
- [ ] Implement the pure locator and confirm GREEN.
- [ ] Pin LiteRT-LM 0.17.0 and package only `arm64-v8a`.
- [ ] Add optional OpenCL/VNDK native-library declarations without adding network permission.
- [ ] Implement lazy GPU engine initialization, CPU initialization retry, `maxNumImages = 1`, one-shot multimodal inference, hard timeout, and exception-to-result isolation.
- [ ] Build the app and inspect the merged manifest and APK size.

### Task 4: Extend gallery metadata and cache safely with TDD

**Files:**
- Modify: `app/src/main/java/com/privacygate/app/gallery/model/GalleryPhoto.kt`
- Modify: `app/src/main/java/com/privacygate/app/gallery/data/PhotoIndexCache.kt`
- Modify: `app/src/test/java/com/privacygate/app/gallery/GalleryTest.kt`
- Create: `app/src/test/java/com/privacygate/app/gallery/PhotoEnrichmentMergeTest.kt`

- [ ] Write failing tests for Gemma metadata merging and `Vehicle with visible number plate` matching.
- [ ] Add nullable enrichment metadata and normalized query/category matching.
- [ ] Migrate the cache schema without rejecting older version-1 entries.
- [ ] Stop persisting existing raw OCR text and preserve only safe search metadata.
- [ ] Run gallery and full unit tests.

### Task 5: Connect protection category toggles with TDD

**Files:**
- Modify: `app/src/main/java/com/privacygate/app/settings/PrivacyPreferences.kt`
- Modify: `app/src/main/java/com/privacygate/app/sentinel/ui/SentinelScreen.kt`
- Test: `app/src/test/java/com/privacygate/app/settings/PrivacyCategoryPolicyTest.kt`

- [ ] Write failing tests proving `VEHICLE_PLATE` defaults on, persists through the existing category set, and gates plate-warning eligibility.
- [ ] Add the vehicle-plate category using the existing enum-driven toggle UI.
- [ ] Map Gemma's normalized plate cue through the category policy without allowing it to mark unrelated photos safe.
- [ ] Run targeted and full unit tests.

### Task 6: Add one-photo deep analysis UI

**Files:**
- Modify: `app/src/main/java/com/privacygate/app/gallery/ui/GalleryScreen.kt`
- Modify: `app/src/main/java/com/privacygate/app/MainActivity.kt`

- [ ] Add callback and UI state tests where practical through pure state reducers.
- [ ] Add a **Deep analyze** action to photo details with missing/loading/ready/failed states.
- [ ] Load a bounded image, write an app-private temporary JPEG, invoke the enricher, merge successful metadata, persist the cache, and delete the temporary file.
- [ ] Keep current detail, redaction, and Magic Studio actions available during every Gemma state.
- [ ] Run all tests and assemble the debug APK.

### Task 7: Install model and benchmark on iQOO 15

**Files:**
- Create ignored artifact: `artifacts/benchmarks/gemma4-e2b-iqoo15.json`

- [ ] Download the pinned model outside Git and verify its published size/hash metadata.
- [ ] Push it to the app-specific external files model directory with ADB.
- [ ] Install the new APK without clearing app data.
- [ ] Run one clean photo, one vehicle with visible plate, one document, and one failure-control case.
- [ ] Record initialization, inference latency, peak memory, output validity, and thermal state.
- [ ] Disable connectivity and repeat one inference.
- [ ] Remove/rename the model, relaunch PrivacyGate, and verify ML Kit gallery, protection, redaction, and send interception still work.

### Task 8: Final verification and source control

**Files:**
- Modify: `README.md`

- [ ] Document optional model installation, storage cost, and failure behavior.
- [ ] Run `./gradlew testDebugUnitTest assembleDebug --console=plain`.
- [ ] Verify the merged manifest has no `android.permission.INTERNET`.
- [ ] Run `git diff --check` and a credential/large-file scan.
- [ ] Commit source without the model or benchmark media and push the feature branch.
