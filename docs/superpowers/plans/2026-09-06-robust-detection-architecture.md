# Robust Detection Architecture — Decision

Date: 2026-09-06
Status: **Accepted** (supersedes screenshot-OCR-on-grid for the picker path)

## 1. The problem you are hitting

Two distinct failures, both caused by **one root cause**.

### Symptom A — "sensitive info not detected when selecting image in WhatsApp"

`WhatsAppAdapter.inspect()` on the gallery-picker branch returns:

```kotlin
bounds = Rect(grid.left, effectiveTop, grid.right, effectiveBottom)   // the GRID
```

`ProtectionCoordinator.startScan()` then does:

```kotlin
val bitmap = capturer.captureWindowCrop(observation.windowId, observation.mediaBounds)
val result = inferenceEngine.scan(bitmap, ...)
```

So OCR runs on **screen pixels of a 3-column thumbnail grid**.

| Quantity | Value |
|---|---|
| Display width | ~1440 px |
| Grid columns | 3 |
| Per-cell width | **~470 px** |
| ID card occupies ~65% of cell | ~305 px |
| 12-digit Aadhaar UID row across that | **~16 px glyph, ~20 px cap height** |

ML Kit Text Recognition v2 degrades sharply below ~24 px cap height, and worse on JPEG-recompressed content. Realistic full-UID recall at this scale: **55–75%**.

Worse, and unfixable by any model: grid cells are **square center-crops**. A 4:3 landscape ID loses ~25% off each side. The UID row / address block / PAN string is often **not in the pixels at all**.

### Symptom B — "when multiple images I want to check them directly"

The grid crop is scanned as **one flat bitmap**. Consequences:

- No per-image attribution — you cannot say *which* of the 5 selected photos is sensitive.
- Unselected neighbours in the grid are scanned too → **false positives on photos the user never selected**.
- Selections scrolled off-screen are **invisible** → silent leak. Current code only bumps `selectedMediaCount`; it never learns *what* those items are.
- `sessionKey` includes `:count=N`, so every tap re-triggers a full grid screenshot + OCR. `AccessibilityService.takeScreenshot()` is throttled at `ACCESSIBILITY_TAKE_SCREENSHOT_REQUEST_INTERVAL_TIMES_MS = 333 ms`; a user multi-selecting 5 photos in ~1.2 s **will** be throttled and you drop items.

### Root cause

> You are asking the **same signal** to answer **two different questions**.
>
> - *"What did the user select?"* → only the screen can answer this.
> - *"Is it sensitive?"* → the screen is the **worst possible** source for this.

## 2. Decision

**Split the two questions. Screen resolves identity. Disk produces the verdict.**

```
screen pixels + a11y tree  ──►  media_id        (identity, ~50-bit problem, easy)
MediaStore full-res original ──►  verdict        (classification, needs real pixels)
```

Identity is a ~50-bit discrimination over a candidate set of ~300 recent images — trivially solvable. Classification is a fine-print OCR problem — impossible at 470 px. Solve each where it is cheap.

Once you hold `media_id`, you read the **full-resolution original** via `MediaRepository`. The 470 px ceiling evaporates. Recall goes 55–75% → **95%+**.

Name: **PVC-IR** — *Pre-computed Verdict Cache + Identity Resolution + Two-Stage Enforcement.*

## 3. Target architecture

```
┌───────────────── Indexer (off the critical path) ─────────────────┐
│ ContentObserver(MediaStore.Images) ─┐                            │
│ MediaStore.getGeneration() delta ───┼─► priority queue           │
│ WorkManager backfill (idle+charging)┘         │                  │
│                                               ▼                  │
│  T0  MediaStore row      bucket/mime/dims filter      <0.1 ms    │
│  T1  512 px decode       face + text-presence         15–30 ms   │
│  T2  2048 px / full-res  ML Kit Text Rec v2           60–140 ms  │
│  T3  extracted text      Verhoeff / Luhn / PAN / MRZ  5–20 ms    │
│                                               ▼                  │
│  VerdictCache: (media_id, date_modified, size)                   │
│      → { verdict, categories, confidence, regions[],             │
│          dhash64, colorGrid4x4 }                                 │
└───────────────────────────────────────────────────────────────────┘
              ▲ identity query              │ verdict
┌─────────────┴──────────────────────────────▼─────────────────────┐
│              PickerSessionController (a11y service)              │
│  WINDOW_STATE_CHANGED(com.whatsapp|.w4b) → open session          │
│  VIEW_CLICKED / CONTENT_CHANGED (coalesce 80 ms) → SelectionSet  │
│                                                                  │
│  IdentityResolver — ordered, first-hit-wins:                     │
│    L0  node contentDescription / text (date, "Photo, N of M")    │
│    L1  positional prior: gridIndex → MediaStore(DATE_MODIFIED ▼) │
│    L2  visual verify: dHash + 4×4 colorgrid vs L1 candidate ±k   │
│    L3  hot-set search (≤300), top-K re-verify at 64×64           │
│    L4  UNRESOLVED → escalate to Stage 2 strict                   │
│                                                                  │
│  armed ⟺ SelectionSet contains ≥1 SENSITIVE verdict              │
└──────────────────────────────────────────────────────────────────┘
              │                                    │
              ▼                                    ▼
    Stage 1: Picker FAB guard          Stage 2: Composer gate
    (already built)                    (already built)
```

### 3.1 IdentityResolver is the load-bearing piece

Perceptual hashing "fails" only when framed as *search the whole 20k library*. Collapse the search space instead:

1. **L1 positional prior.** WhatsApp's picker is `DATE_MODIFIED DESC`. Run the equivalent projection-only query once per session (~8 ms / 500 rows). Grid index *i* → row *i* with high probability. You now have **one candidate**, not 20 000.
2. **L2 verify, do not search.** Accept only if dHash Hamming ≤ 12 **and** 4×4 colorgrid L1 ≤ threshold. Two weak-but-independent signals against a *single hypothesis* has a false-accept rate orders of magnitude below nearest-neighbour over 20k.
3. **Align the transforms.** Generate the cached signature through the same pipeline shape Glide uses: center-square-crop → 32×32 grayscale (`RGB_565`-quantized) → dHash. Do **not** hash the full frame. Store **two** signatures (one from `loadThumbnail`, one from a re-encoded q80 pass) and accept either — this absorbs recompression drift for free.
4. **L3 only on prior miss** (date header, album switch, "select all"). Bounded to the 300-item hot set → 300 × 8-byte Hamming = **<1 ms**.
5. **L0 first, never depended on.** Many picker builds emit a content description carrying the capture date. If present, L1 becomes exact.

Then **throw the thumbnail away and keep `media_id`.**

### 3.2 The count invariant — this is what makes it production-grade

```
observedBadgeCount = numeric text in send_media_counter
resolvedSet.size   = items positively identified

if (observedBadgeCount != resolvedSet.size) {
    session.integrity = DEGRADED
    // do NOT arm Stage 1  (a bad state model causes false positives)
    // DO force strict Stage 2  (fail-closed at the composer)
}
```

Every possible tracking failure — missed tap, throttle, "select all", album switch, WhatsApp redesign — collapses into *"the second gate does the work"* rather than into a silent leak **or** a spurious block. This is the single highest-value invariant in the design and it directly answers Symptom B.

### 3.3 Tier 3 is your false-positive killer

Do **not** ship "12 consecutive digits ⇒ Aadhaar". That fires on invoices, IMEIs, order numbers, boarding passes.

| Entity | Validator | Effect |
|---|---|---|
| Aadhaar | **Verhoeff** check digit | kills ~90% of random 12-digit hits |
| PAN | 5 alpha + 4 numeric + 1 alpha, 4th char ∈ `P C H F A T B L J G` | highly discriminative |
| Card | **Luhn** | standard |
| Passport / DL | MRZ line shape, format patterns | structural |

Require **checksum-valid entity + supporting context token** (`Government of India`, `आधार`, `UIDAI`, `Income Tax Department`, `DOB`, MRZ shape) before emitting `SENSITIVE_ID`.

`PatternDetector` / `SmartFieldDetector` already exist — audit them against this table; that is a cheap, high-leverage win independent of everything else.

### 3.4 Face policy — deliberately conservative

Default `SENSITIVE_FACE` **off** for the Stage 1 picker block, or gate on `faceCount == 1 && faceArea > 15% && no text` (i.e. a plausible KYC portrait). Otherwise every group photo trips the guard and the app gets disabled within a week. Keep it user-configurable, default permissive.

### 3.5 Zero-latency by construction

> **The overlay is not installed unless the SelectionSet already holds a positive SENSITIVE verdict.**

On an all-clean selection there is no overlay window, no touch interception, no FAB hook — the first tap hits WhatsApp's real view with **0 ms added latency, structurally**, not statistically. `RiskOverlayController.armSendGuard()` already has exactly this shape; it just needs to be driven by the SelectionSet instead of by a grid screenshot.

## 4. What you already have (≈70% built)

| Piece needed | Exists as | Gap |
|---|---|---|
| MediaStore enumeration | `gallery/data/MediaRepository.kt` | sort by `DATE_MODIFIED`, add `_ID`+`SIZE` to projection |
| Full-res decode | `MediaRepository.loadDownscaledBitmap()` | raise cap to 2048 for Tier 2 |
| Persistent verdict cache | `gallery/data/PhotoIndexCache.kt` | add `dhash64`, `colorGrid`, `date_modified`, `size`; encrypt |
| Tiered classifier | `ai/PrivacyInferenceEngine.kt` | already tiered-ish; add T0/T1 early exits |
| Entity validators | `ai/PatternDetector.kt`, `ai/SmartFieldDetector.kt` | audit vs §3.3 table |
| Background indexer | `gallery/ai/LocalVisionIndexer.kt` | add `ContentObserver` + WorkManager |
| Stage 1 guard | `protection/overlay/RiskOverlayController.kt` | drive from SelectionSet |
| Stage 2 composer gate | `WhatsAppAdapter` composer branch | keep as-is — it is already correct |

### Missing, must be built

1. `identity/ImageSignature.kt` — dHash64 + 4×4 colorgrid, Glide-aligned transform.
2. `identity/IdentityResolver.kt` — L0→L4 ladder.
3. `protection/SelectionSet.kt` + `PickerSessionController.kt` — event ledger + count invariant.
4. `index/MediaIndexObserver.kt` — `ContentObserver` + `getGeneration()` delta + WorkManager backfill.

Dependency to add: `androidx.work:work-runtime-ktx`. Everything else is already in `libs.versions.toml`.

## 5. Handling the new-photo hole

- `ContentObserver` on `MediaStore.Images` external URI → enqueue at **priority 0**. Camera capture → verdict in **200–700 ms**, well before the user reaches Attach → Gallery.
- Poll `MediaStore.getGeneration(volume)` on session open; delta-scan new rows before the picker's first frame paints.
- Keep the **hot set** at 100% coverage always: 300 most recent, plus everything in `Screenshots/`, `Camera/`, `Download/`, `WhatsApp Images/`, and any `*scan*` / `*document*` bucket. >95% of shares originate there. Long-tail backfill under `requiresDeviceIdle + requiresCharging`.
- Cache key `(media_id, date_modified, size)` so re-crops and edits invalidate correctly.
- Still unindexed at tap time: **do not block at Stage 1**, escalate to Stage 2 strict. If unresolved even at composer send, bounded "Checking… ≤400 ms" spinner, hard timeout, default fail-open.

## 6. Resilience to WhatsApp UI changes

`WhatsAppAdapter` currently leans hard on `viewIdResourceName` (`gallery_picker`, `send_media_btn`, `send_media_counter`, …). Those are obfuscated and **rotate per release**. Harden:

- Geometric + structural signatures: picker grid = largest `RecyclerView`/`AbsListView`-class scrollable node covering >50% of the window; send FAB = smallest `clickable` near-square node (44–72 dp) in the bottom 20% / right 30%, ideally co-located with a 1–2 char numeric text node.
- Never match localized strings as a primary key (`Send`, `भेजें`) — confirming signal only.
- **Self-test on session open**: if grid or FAB is not located within 150 ms → `integrity = DEGRADED`, silently disable Stage 1, keep Stage 2, bump a local breakage counter surfaced as *"Protection reduced — update available"*. **Never fake protection.**
- Handle the **system photo picker** (`ACTION_PICK_IMAGES`, `com.google.android.providers.media.module`) — different process, much more stable tree, and it hands WhatsApp URIs you cannot observe. Switch rulesets on package; there, Stage 1 is optional and **Stage 2 is mandatory**.
- Keep both `com.whatsapp` and `com.whatsapp.w4b` registered (already done).

## 7. Budgets

| Path | Added latency |
|---|---|
| Clean selection → first tap on Send | **0 ms** (no overlay exists) |
| Per-tap identity resolution (L0/L1/L2) | 3–10 ms, off main thread |
| Guard arm on sensitive detect | 40–90 ms — during selection, not at send |
| Sensitive tap → pill visible | 0 ms (pre-armed) |
| Unresolved → Stage 2 full-res verdict | 60–180 ms, absorbed by screen transition |
| Worst case (unindexed, instant send) | ≤400 ms bounded spinner |

**Battery.** Steady state: `ContentObserver` + a few delta rows/day ≈ negligible. Cold start on 20k images with tiering ≈ 75% exit at T1 (~25 ms) + 25% at T2 (~100 ms) ≈ **~14 min** of chunked NPU/GPU work under idle+charging → **<1.5% battery**. Naive full-res-OCR-everything is 8–10× that. **The tiering is not optional.**

**Storage.** ~80 bytes/image → 20k images ≈ **1.6 MB**. Keystore-backed encrypted DB. **Never persist OCR text, thumbnails, or crops** — hash and discard in the same coroutine scope. Note: `PhotoIndexCache` currently writes `extractedText` to plaintext JSON in `filesDir` — **that is a privacy bug, fix it.** Zero network (no `INTERNET` permission — good).

## 8. Failure matrix

| Scenario | Behaviour |
|---|---|
| Clean set | No overlay, instant send. Zero FP by construction. |
| Sensitive set, indexed | Guard pre-armed, pill on tap. |
| Selected then scrolled off-screen | Tracked by **event**, not pixels. Guarded. |
| Rapid 6-tap multi-select | L0/L1/L2 need no screenshots → 333 ms throttle never hit. |
| Brand-new photo | Priority-indexed <1 s; else Stage 2 strict. |
| Count invariant mismatch | Stage 1 disarmed, Stage 2 forced strict. |
| WhatsApp redesigns picker | `DEGRADED`, Stage 2 holds, honest UI, config push fixes. |
| System photo picker | Stage 2 only, mandatory. |
| Screenshot returns null (secure window / OEM) | L0/L1 still work (metadata-only); Stage 2 uses **disk** pixels. |
| Burst shots / near-duplicates | Positional prior disambiguates; if still tied and verdicts differ → **sensitive-union** at Stage 2. |

## 9. Honest weak spot — the Redact action

Nothing in the accessibility API lets you substitute a file inside WhatsApp's already-materialized selection. `RedactionEngine.saveAndShareSmartMasked()` correctly sidesteps this by sharing a new copy. Best achievable in-place flow:

1. Generate the redacted copy locally; **re-encode, strip EXIF/GPS, drop ICC and the embedded thumbnail segment.** A naive JPEG overlay leaves the EXIF thumbnail unredacted — classic leak.
2. Insert into MediaStore with a fresh `date_modified` so it sorts **first** in the picker.
3. `ACTION_CLICK` to deselect the offending item, scroll to top, select the redacted copy.
4. Low automation confidence → fall back to cancel + `ACTION_SEND`.

Primary CTA should be **"Remove from selection"** (100% reliable). "Redact & replace" is secondary, and must re-resolve the SelectionSet and confirm the sensitive `media_id` is gone before disarming. Never ship a Redact button that silently fails to change what is sent.

## 10. Build order

1. **`ImageSignature` + verdict cache schema.** Pure Kotlin, unit-testable, no a11y.
2. **Indexer** (`ContentObserver` + WorkManager + hot-set priority) and audit Tier-3 validators. Build a golden corpus: ≥500 real IDs (Aadhaar front/back, PAN, DL, passport, voter — varied angle/lighting/partial frame) and ≥5 000 hard negatives (invoices, IMEI screenshots, boarding passes, receipts, memes with 12-digit strings, group photos). **Gate: FP rate <0.3% before any UI work.**
3. **Repoint Stage 2** (composer) at the full-res original via `media_id` instead of the screen crop. Ship this alone — simpler, more stable, delivers most of the protection.
4. **`IdentityResolver` + `SelectionSet` in shadow mode.** Enforcement disabled, log resolution accuracy locally. **Gate: ≥99% correct identity attribution on the hot set** before arming Stage 1.
5. **Stage 1 FAB guard** behind a flag, count invariant wired from day one.
6. **Redact & replace**, last, with verification.

## 11. Immediate next change

In `WhatsAppAdapter.inspect()`, the gallery-picker branch must stop returning a grid `Rect` for OCR. Replace with per-cell node bounds + grid index, feeding `IdentityResolver`. The grid screenshot becomes a **verification** input (L2) only — never a classification input.

**Bottom line:** stop classifying on-screen thumbnails. Screen for identity, disk for truth, pre-computed verdicts for zero latency, checksum-validated entities for zero false positives, opt-in overlay so clean sends are untouched, composer gate as the invariant-preserving backstop.
