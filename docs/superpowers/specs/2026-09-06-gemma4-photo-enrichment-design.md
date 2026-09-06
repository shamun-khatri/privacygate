# Gemma 4 Photo Enrichment Design

## Goal

Add an optional, fully local Gemma 4 E2B IT re-evaluation and enrichment layer to PrivacyGate without changing the availability of the existing ML Kit privacy pipeline. Re-evaluate one selected photo first, measure the physical iQOO 15 result, then permit background re-evaluation only after the model proves stable.

## Safety boundary

ML Kit OCR, face detection, image labels, deterministic PII validators, send interception, and redaction remain the authoritative path. Gemma output adds captions, search labels, scene/activity labels, and privacy cues. A missing model, initialization failure, inference error, timeout, invalid JSON, or process restart must return an unavailable result and leave every existing feature usable.

Gemma never downgrades an actionable ML Kit/checksum result, never independently declares an image safe, and never supplies masking geometry. It may confirm ML Kit labels, add missing labels, or upgrade a low-risk result to `NEEDS_REVIEW`. Exact Aadhaar, card, phone, and number-plate values are not persisted from Gemma output.

## Model and runtime

- Model: `litert-community/gemma-4-E2B-it-litert-lm`
- File: `gemma-4-E2B-it.litertlm`
- Runtime: `com.google.ai.edge.litertlm:litertlm-android:0.17.0`
- ABI: `arm64-v8a`
- Storage: external to the APK under the app-specific files directory
- Preferred backend: GPU for the language and vision executors; CPU retry if GPU initialization fails
- Maximum images per request: 1
- Inference: one fresh conversation per photo, hard timeout, bounded response

The model is not committed to Git and is not bundled into the APK. The hackathon device receives it through ADB so `android.permission.INTERNET` remains absent.

## Data model

Each indexed photo retains the immediate ML Kit fields and may gain a `GemmaEnrichment` record:

- status: `NOT_REQUESTED`, `QUEUED`, `RUNNING`, `READY`, `UNAVAILABLE`, or `FAILED`
- caption
- objects
- scene labels
- activities
- search labels
- privacy cues
- `vehiclePresent`
- `registrationPlateVisible`
- model version and enrichment timestamp
- ML Kit labels that Gemma confirmed
- labels added by Gemma
- labels where the two layers conflict
- combined review state

The cache stores this structured metadata but no generated transcription or raw sensitive values. Cache validity includes the MediaStore ID, modification date, and enrichment schema/model version.

The protection dashboard adds a persisted `VEHICLE_PLATE` category toggle. When disabled, plate metadata remains available for neutral gallery organization but cannot raise a plate warning. Existing toggles continue to gate their corresponding ML Kit detection categories. Gallery filter pills merge ML Kit segments and Gemma enrichment labels.

## Data flow

1. MediaStore returns a photo.
2. Existing ML Kit indexing publishes labels and the privacy verdict immediately.
3. A user opens the photo detail and requests deep analysis.
4. PrivacyGate checks the model file and initializes a process-wide engine off the UI thread.
5. A downscaled temporary JPEG and a redacted summary of first-layer categories/labels are passed with a strict JSON prompt. Raw detected values are excluded.
6. The response parser accepts only bounded JSON fields and normalizes/deduplicates labels.
7. A deterministic merger records confirmed, added, and conflicting labels. The combined policy is a conservative union: Gemma can request review but cannot remove an existing sensitive verdict.
8. The gallery saves the merged structured cache.
9. Temporary image files are deleted in `finally` blocks.

Background enrichment remains disabled until the one-photo benchmark passes on the physical device.

## Vehicle and number-plate behavior

The initial implementation adds `Vehicle`, `Number plate visible`, and `Vehicle with visible number plate` metadata from Gemma. This improves gallery categories and search. Gemma does not provide a trustworthy redaction rectangle; a dedicated plate detector and cropped ML Kit OCR are a separate follow-up before automatic plate masking is enabled.

## User interface

Photo detail gains a compact **Deep analyze** action. States are explicit: model missing, loading, analyzing, ready, failed, and retry. Existing photo details and actions remain visible in every state. Ready results show the caption and compact chips, including a highlighted number-plate privacy cue.

## Verification

- Unit tests cover availability, response parsing, failure isolation, preference toggles, metadata merging, cache migration, and vehicle/plate category matching.
- Existing unit tests and `assembleDebug` must pass after every integration stage.
- The merged manifest must still contain no `android.permission.INTERNET`.
- The pre-Gemma APK and SHA-256 remain in `artifacts/backups/`.
- Physical proof records model initialization time, one-photo latency, memory, response validity, airplane-mode behavior, and app recovery after forced model failure.
