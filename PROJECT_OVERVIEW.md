# SinSheld

**On-device NSFW content shield for Android.** SinSheld watches supported social apps and
browsers, and when explicit imagery appears on screen it covers it with a blocking overlay — all
image analysis runs locally on the device, and screenshots never leave the phone. A second,
independent layer blocks known adult-content websites at the DNS level through a local VPN.

- **Package:** `com.example.sinshield`
- **Language / UI:** Kotlin, Jetpack Compose (Material 3)
- **minSdk 26 · targetSdk / compileSdk 37**
- **~8,600 lines** of production Kotlin across 29 files, plus **~2,000 lines** of unit tests.

---

## Table of contents

1. [What it does](#1-what-it-does)
2. [Permissions and how they map to features](#2-permissions-and-how-they-map-to-features)
3. [High-level architecture](#3-high-level-architecture)
4. [The detection pipeline](#4-the-detection-pipeline)
5. [The two machine-learning models](#5-the-two-machine-learning-models)
6. [Verdict policy and thresholds](#6-verdict-policy-and-thresholds)
7. [Per-app analysis flows](#7-per-app-analysis-flows)
8. [Region detection (localization)](#8-region-detection-localization)
9. [Overlays and recovery](#9-overlays-and-recovery)
10. [Scan scheduling, freshness, and dedup](#10-scan-scheduling-freshness-and-dedup)
11. [Website protection (DNS VPN)](#11-website-protection-dns-vpn)
12. [User feedback and false positives](#12-user-feedback-and-false-positives)
13. [The app UI](#13-the-app-ui)
14. [Debug instrumentation and timing](#14-debug-instrumentation-and-timing)
15. [Build, run, and test](#15-build-run-and-test)
16. [File / module reference](#16-file--module-reference)
17. [Tech stack and dependencies](#17-tech-stack-and-dependencies)
18. [Privacy notes](#18-privacy-notes)

---

## 1. What it does

SinSheld is a "content shield" built around two independent protections:

### a) Screen protection (Accessibility Service)
For apps whose media cannot be inspected through the accessibility node tree (feed images are not
plain `ImageView`s), SinSheld takes **on-device screenshots** when the screen changes, classifies
what is visible with local ML models, and — only on a confirmed unsafe verdict — draws an opaque
blocking window over the content.

Normal content is **never covered**: accessibility events merely request a coalesced screenshot,
and only a *current, final* unsafe verdict ever creates a blocking window.

Supported apps get one of two block styles:
- **Full-screen block** with recovery actions (Instagram, X, Facebook, Facebook Lite, Reddit,
  Telegram) — used where individual feed posts cannot be localized reliably enough to cover
  post-by-post.
- **Per-region covers** — used where localization is reliable, covering individual images in place.

### b) Website protection (Local VPN + DNS filtering)
A local, **split-tunnel VPN** captures classic DNS only. Queries for known adult-content domains
get an `NXDOMAIN` answer and never leave the device; every other query is forwarded to the
network's real resolver unchanged. Web traffic itself is never decrypted or inspected.

---

## 2. Permissions and how they map to features

| Permission | Why it's needed |
|---|---|
| **Accessibility Service** (`BIND_ACCESSIBILITY_SERVICE`) | Observe screen-change events and take screenshots of monitored apps. Core of screen protection. |
| **Display over other apps** (`SYSTEM_ALERT_WINDOW`) | Draw the blocking overlays. Detection can run without it, but nothing can be covered. |
| **VPN** (`BIND_VPN_SERVICE`) | The local DNS-filtering tunnel for website protection. |
| **Foreground service** (`FOREGROUND_SERVICE` + `..._SPECIAL_USE`) | Keep both services alive against aggressive OEM power management (MIUI/HyperOS etc.). Declared subtypes: `nsfw_screen_monitoring` and `on_device_adult_domain_dns_filtering`. |
| **Post notifications** | The persistent foreground-service notifications. |
| **Ignore battery optimizations** | Optional; improves background reliability. |
| **Internet / network state** | Forward allowed DNS queries and refresh the domain blocklist. |

The two services are declared in [`AndroidManifest.xml`](app/src/main/AndroidManifest.xml):
`ShieldAccessibilityService` and `AdultContentVpnService`. Android requires the user to grant
Accessibility, overlay, and VPN permissions manually — the app can only deep-link to the relevant
settings screens.

---

## 3. High-level architecture

The accessibility service was originally a ~2,300-line "God object" and has been decomposed into
focused, single-responsibility collaborators. The service itself is now a thin coordinator.

```
ShieldAccessibilityService  (event loop, foreground identity, node reads)
│
├── FrameScanner            screenshot → classify → verdict → overlay pipeline
│   ├── ScanScheduler       single-flight state machine, generations, adaptive interval
│   ├── AppAnalysisFlowRegistry
│   │   ├── FullScreenAnalyzer      whole-screen classifier (MobileNetV2)
│   │   ├── AsyncOpenCvLocalizedAnalyzer   OpenCV detect + parallel per-region classify
│   │   ├── AnalysisFinalizer      combine + verifier policy → FrameAnalysis
│   │   │   └── CandidateVerifier  lazy Marqo ViT verifier (ONNX)
│   │   └── {X, Instagram, Reddit, FullScreenOnly}AnalysisFlow   per-app sequencing
│   ├── ContentPolicy / VerifierPolicy    verdict math (pure, unit-tested)
│   └── FeedbackRepository  remembered false positives
│
├── OverlayManager          every WindowManager window (localized/app/site blocks)
└── RecoveryController      Return-to-feed / Scroll-past / Close-app navigation & gestures

AdultContentVpnService      independent DNS-filtering VPN
├── Ipv4UdpPacketCodec / DnsMessageCodec   packet parse/build (DnsPacketCodec.kt)
├── AdultDomainMatcher      immutable suffix matcher
└── AdultDomainListRepository   bundled seed + maintained blocklist refresh

MainActivity                Compose settings / onboarding UI
```

**Design seam:** classes that *must* touch Android (screenshotting, WindowManager, gesture
dispatch) receive their Android surfaces through injected `Host` interfaces and lambdas, so the
dependency always points *into* the service, never back. Pure decision logic
(`ContentPolicy`, `VerifierPolicy`, `ScanScheduler`, `ScanFreshnessPolicy`,
`NavigationRecoveryPolicy`, the region planners, the DNS codecs) is Android-free and directly
unit-tested.

---

## 4. The detection pipeline

Driven by [`FrameScanner.kt`](app/src/main/java/com/example/sinshield/FrameScanner.kt). Every
member runs on the main thread except `analyzeFrame` and model workers, which run off-main and hop
back via a `Handler`.

1. **Event → schedule.** A relevant accessibility event (`SCROLLED`,
   `WINDOW_CONTENT_CHANGED`, `WINDOW_STATE_CHANGED`, `WINDOWS_CHANGED`) for a monitored,
   foreground app requests a scan after an **80 ms debounce**. If a scan is already in flight, a
   single "frame pending" bit is set instead of starting a second one.
2. **Capture.** `ScanScheduler` enforces single-flight + Android's screenshot cooldown, then
   `FrameScanner` calls `takeScreenshotOfWindow` (API 34+, excludes SinSheld's own covers so a
   covered-but-now-safe frame can be re-evaluated) or `takeScreenshot` on older APIs. The hardware
   buffer is copied to an `ARGB_8888` bitmap.
3. **Hash + short-circuit.** The frame is average/difference-hashed (`FrameHasher`). If it matches
   the last known-safe hash, or a remembered false positive, it's treated as SAFE without running
   the models. Overlay-release checks deliberately bypass this shortcut so each qualifying result
   is a complete model analysis of a newly captured screenshot.
4. **Analyze.** Otherwise the correct `AppAnalysisFlow` runs (see §7): whole-screen classify,
   optional localized per-region classify, then combine and (if needed) verify.
5. **Verdict → overlay.** Back on the main thread, `completeScan` checks freshness, then acts on
   the verdict: SAFE clears covers; SUSPICIOUS shows a provisional shield and schedules one quick
   confirmation; EXPLICIT / SEMI_NUDE shows the final block.
6. **Reschedule.** The adaptive interval decides when the next scan runs (fast while active,
   backing off while a feed stays safe).

### Clock domains (important)
Three clocks are kept deliberately separate so a busy feed can't make every capture look stale:
- **Scheduling** uses `elapsedRealtime` (includes deep sleep).
- **Accessibility events and screenshots** are stamped in `uptimeMillis`.
- **Inference duration** is measured with `elapsedRealtime`.

The release-safe `BAN` timing log (see §14) is built entirely from `uptimeMillis` stamps so its
parts sum cleanly: `eventToCapture + captureToBlock = total`.

---

## 5. The two machine-learning models

SinSheld uses a **fast primary classifier** plus a **slower independent verifier**, both CPU-only
and bundled uncompressed in `assets/` (`noCompress` for `tflite`/`onnx`).

### Primary: GantMan NSFW MobileNetV2 (TFLite / LiteRT)
- File: `nsfw_mobilenet_v2.tflite` (~24 MB), wrapper
  [`NsfwClassifier.kt`](app/src/main/java/com/example/sinshield/NsfwClassifier.kt).
- Input: `FLOAT32 [batch, 224, 224, 3]`, RGB interleaved (HWC), normalized to `[0, 1]`.
- Output: 5 softmax scores — **[0] Drawings, [1] Hentai, [2] Neutral, [3] Porn, [4] Sexy**.
- Supports **batched inference** (many crops in one interpreter call) with automatic fall-back to
  one-call-per-crop if the model rejects a batched tensor. Runs via Google's **LiteRT** artifacts
  (`com.google.ai.edge.litert`), the renamed successor to `org.tensorflow:tensorflow-lite` — the
  legacy artifacts fail AGP's unique-namespace merge check.

### Verifier: Marqo NSFW ViT-tiny 384 (ONNX Runtime)
- File: `nsfw_marqo_vit_tiny_384.onnx` (~22 MB), wrapper
  [`NsfwVerifier.kt`](app/src/main/java/com/example/sinshield/NsfwVerifier.kt).
- Input: planar **NCHW** `[1, 3, 384, 384]`, normalized to `[-1, 1]` (note: different layout *and*
  normalization from the classifier).
- Output: 2 logits → softmax → **NSFW probability** (index 0 = NSFW, 1 = SFW; boundary 0.50).
- It's a **binary** porn-vs-SFW model, so it's used as a *confirming* second opinion for borderline
  cases, and it can promote a "Sexy" candidate to the stricter explicit policy — but it must never
  veto a confident detection down to SAFE (a low binary score is consistent with suggestive-but-
  not-explicit imagery, not evidence against it). It crops to the flagged box (padded by
  `CROP_MARGIN_RATIO`) so it judges the region, not the whole screen. It is **lazy-loaded** and
  **fails open**: if it can't load, detection continues on the primary model alone.

---

## 6. Verdict policy and thresholds

All verdict math is pure and lives in
[`ContentPolicy.kt`](app/src/main/java/com/example/sinshield/ContentPolicy.kt).

**Verdicts:** `SAFE`, `SUSPICIOUS`, `SEMI_NUDE`, `EXPLICIT`.

- `ContentPolicy.evaluate(scores, thresholds)` maps the 5 classifier scores to a
  `StageOneResult(verdict, suspectedFinalVerdict, causes)`.
- `ContentPolicy.combine(...)` merges the whole-screen result with localized region scores, without
  letting either stage *weaken* the other. Full-screen social feeds dilute the Sexy score with
  surrounding UI, which is exactly why lower "suspicious" bands feed a multi-frame confirmation
  path instead of blocking instantly.
- `VerifierPolicy.finalVerdict(...)` applies the second model:
  - A firm **SEMI_NUDE** (Sexy) is trusted on the primary alone; a high verifier score promotes it
    to EXPLICIT.
  - A decisive **EXPLICIT** (Porn/Hentai) requires verifier approval under the recommended
    defaults; users can disable *"Verifier must approve decisive hits"* to favor recall.
  - Merely **SUSPICIOUS** candidates require the verifier to agree before they block.

### Protection levels ([`ProtectionPreferences.kt`](app/src/main/java/com/example/sinshield/ProtectionPreferences.kt))

| Level | Explicit | Suggestive (Sexy) | Verifier | Notes |
|---|---|---|---|---|
| Relaxed | 0.95 | 0.96 | 0.90 | Only very confident detections |
| **Recommended** (default) | 0.93 | 0.92 | 0.86 | Verifier approval required |
| Balanced | 0.70 | 0.80 | 0.80 | Fewer false alarms |
| High | 0.55 | 0.65 | 0.65 | Catches more, may err |
| Maximum | 0.40 | 0.50 | 0.50 | Most sensitive |

Each level also carries lower `suspiciousExplicit` / `suspiciousSemiNude` "watch the next frames"
bands. Users can override any threshold in **Advanced detection tuning**; overrides are normalized
(kept finite and internally ordered) before use.

**"Block suggestive content"** (a.k.a. strict mode, `blockSuggestive`) decides whether the Sexy
category is covered at all — with it off, a SEMI_NUDE verdict is allowed through. Per the project's
design intent, over-blocking is acceptable for a shield.

**Confirmation:** a SUSPICIOUS frame gets a provisional cover *immediately* and one fast
confirmation scan; a matching follow-up promotes it to a real incident. This rejects one-frame
glitches without leaving disturbing pixels visible while confirming.

---

## 7. Per-app analysis flows

There are **two independent axes** here, and it helps to keep them apart:

- **Analysis flow** — *how a captured frame is scored*. Chosen by the pure
  `AppAnalysisFlowSelector` on package name; each flow is assembled independently in
  `AppAnalysisFlowRegistry`
  ([`AppAnalysisFlow.kt`](app/src/main/java/com/example/sinshield/AppAnalysisFlow.kt)) so adding one
  app can't alter another's sequence.
- **Block presentation** — *what the user sees and how they get out*. Decided in `FrameScanner`
  purely by whether the package has a `ShieldedApp` profile
  ([`ShieldedApp.forPackage`](app/src/main/java/com/example/sinshield/ShieldedApp.kt)): a profile →
  **full-screen block with recovery actions**; no profile → **per-region covers**.

These don't line up one-to-one. Facebook, for example, uses the generic *full-screen-only analysis*
but still gets a *full-screen block* because it has a `ShieldedApp` profile. Telegram uses the same
analysis but gets *per-region covers* because it doesn't.

| App / package | Analysis flow | Block presentation |
|---|---|---|
| **X** (`com.twitter.android`) | `XAnalysisFlow` | Full-screen block + recovery |
| **Instagram** (`com.instagram.android`) | `InstagramAnalysisFlow` | Full-screen block + recovery |
| **Reddit** (`com.reddit.frontpage`) | `RedditAnalysisFlow` | Full-screen block + recovery |
| **Facebook / Facebook Lite** | `FullScreenOnlyAnalysisFlow` | Full-screen block + recovery |
| **Telegram** (`org.telegram.messenger`) | `FullScreenOnlyAnalysisFlow` | Per-region covers |
| **Browsers** (Chrome, Firefox, Edge, Brave, Samsung, Opera) | `FullScreenOnlyAnalysisFlow` | Per-region covers **+** DNS site-block overlay (§11) |

### 7.1 End-to-end trace (X, the canonical case)

Walking one frame all the way through, so the moving parts are concrete:

1. **You scroll the X feed.** Android fires a `TYPE_VIEW_SCROLLED` accessibility event.
2. **The service reacts** (`onAccessibilityEvent`): it resolves the real foreground app from the
   active window root, and — if the app/window changed — clears stale covers and resets the adaptive
   interval. For a monitored app it calls `scanner.onMonitoredEvent()`.
3. **A scan is scheduled** after an **80 ms debounce**. If one is already in flight, only a
   "frame pending" bit is set (no second flight). `ScanScheduler` also enforces Android's screenshot
   cooldown and hands back a **generation** number for this flight.
4. **Capture.** `FrameScanner` snapshots context (package, window id, event timestamp, the
   accessibility media regions, the resolved `ShieldedScreenMode`, screen signals, the last
   known-safe hash, protection level + thresholds), then takes a **window screenshot** (API 34+,
   which excludes SinSheld's own covers). The hardware buffer is copied to a bitmap.
5. **Off-main analysis** (`analyzeFrame` on the inference executor):
   - Difference-hash the frame. If it equals the last known-safe hash or a remembered false
     positive → **SAFE**, models skipped.
   - Otherwise `XAnalysisFlow.analyze` runs: **whole-screen classify** (MobileNetV2, 224×224).
     Because localized is a *fallback for content diluted in an otherwise-safe screen*, the OpenCV
     localized pass runs **only if the whole screen came back SAFE**
     (`LocalizedStageGate.shouldRun`). If the whole screen already flagged something, the finalizer
     handles it without localized.
   - `AnalysisFinalizer` combines whole-screen + localized scores (`ContentPolicy.combine`), and if
     the candidate is borderline, asks the **Marqo verifier** to confirm (`VerifierPolicy`). The
     result is a `FrameAnalysis` exposing a final `verdict`.
6. **Back on the main thread** (`completeScan`): a **freshness** check (generation not superseded,
   same package + window, `ScanFreshnessPolicy`) decides whether the result still applies. Then:
   - **SAFE** → clear any covers, record the safe hash, maybe back off the interval.
   - **SUSPICIOUS** → show a provisional block **immediately** and schedule **one fast confirmation
     scan**; a matching follow-up promotes it.
   - **EXPLICIT / SEMI_NUDE** → show the final block.
   - A one-line `BAN` timing log is emitted (§14).
7. **Reschedule** the next scan on the adaptive interval (fast while active, backing off while safe).

Instagram, Reddit, and the others differ **only in step 5** (and in step 6's presentation).

### 7.2 How each analysis flow differs (step 5)

- **X** ([`XAnalysisFlow.kt`](app/src/main/java/com/example/sinshield/XAnalysisFlow.kt)) —
  whole-screen classify; run OpenCV-localized inference **only if the whole screen is SAFE**.
  Sequential.
- **Reddit** ([`RedditAnalysisFlow.kt`](app/src/main/java/com/example/sinshield/RedditAnalysisFlow.kt)) —
  currently **identical to X's sequence**, but kept as its own class so it can diverge without
  touching X. (Reddit's one special rule lives in `FrameScanner`: a strong-explicit candidate needs
  one confirmation before it blocks.)
- **Instagram** ([`InstagramAnalysisFlow.kt`](app/src/main/java/com/example/sinshield/InstagramAnalysisFlow.kt)) —
  the whole-screen classify runs on a **separate executor, in parallel** with the ~1 s OpenCV
  grid/post detection (`planRegions`), so the slow CV pass overlaps the fast classifier instead of
  following it. Then it decides from the whole-screen verdict:
  - whole-screen **EXPLICIT** is terminal → skip scoring the grid entirely (localized can only
    agree);
  - **anything softer** (including SUSPICIOUS/SEMI_NUDE, unlike X) → score the detected crops,
    because Instagram's grid dilutes the whole-screen score and per-region is what catches it. It
    **stops after the first actionable region** (`stopAfterFirstActionable`) — one hit is enough
    since Instagram draws a single full-screen block.
- **Full-screen only** (Facebook, Facebook Lite, Telegram, browsers) — whole-screen classify only,
  no localization. The conservative default.

The `LocalizedAnalyzer` interface is split into `planRegions()` (OpenCV detect + planner, the ~1 s
CPU pass) and `classifyRegions()` (crop + parallel classify with early-exit) *specifically* so the
Instagram flow can overlap region-finding with the classifier. `AsyncOpenCvLocalizedAnalyzer` runs
crops on a fixed pool of independent `NsfwClassifier` interpreters (2 workers × 2 threads) via an
`ExecutorCompletionService`, aborting the rest as soon as one region is actionable.

### 7.3 What the block looks like and how you leave it (step 6)

**Full-screen block** (X, Instagram, Reddit, Facebook, Facebook Lite) —
[`OverlayManager`](app/src/main/java/com/example/sinshield/OverlayManager.kt) inflates an opaque
window with an explanation and three buttons; the *middle* button's behavior adapts to the resolved
`ShieldedScreenMode` (via `RecoveryController.performPrimaryRecoveryAction`):

| Button | Behavior |
|---|---|
| **Return to feed** | `navigateToVisibleHomeFeed`: click the app's Home tab; else press Back up to 4×; else (Facebook/Reddit only) restart the app's task at its launcher. Shield stays up through the transition. |
| **Primary action** (adapts) | **Feed / Profile / Reels / Explore / Unknown →** scroll past (a vertical scroll node action, or a shielded swipe gesture behind the opaque cover). **Story →** protected tap to skip one story. **Direct message →** Back (return to chat). **Live →** Back (leave live). |
| **Close app** | Reset to Home feed, then `HOME` → `RECENTS` → dismiss the app's card (`ACTION_DISMISS` or a swipe-away gesture). |

Recovery gestures are dispatched *behind* the still-visible shield (it's briefly made
non-touchable), so the disturbing pixels never reappear while navigating. After an action the
scanner re-scans to confirm the new screen is safe before the shield is removed.

Two important edge cases, both in `FrameScanner`:
- **CREATION mode** (you're composing a post/story/reel — strong evidence required) **suppresses**
  even a confirmed unsafe verdict, so SinSheld never blocks your *own* draft. It also deliberately
  does **not** bless those pixels as globally safe, in case they're later posted into a feed.
- The `FLAG_RETRIEVE_INTERACTIVE_WINDOWS` / node reads let the service tell a transient System-UI or
  keyboard window apart from the real foreground app, so a stray event doesn't stop the scanner.

**Per-region covers** (Telegram, browsers) — `showLocalizedBlockingOverlays` covers each detected
box in place. These **track scroll** (moved on `TYPE_VIEW_SCROLLED`), **reconcile** against media
nodes on content changes, and auto-clear when the content scrolls away (scroll-peek). There are no
recovery buttons — the covers simply hide the images. In browsers this is layered *on top of* the
DNS site-block path (§11), which is a separate overlay with **Open safe page** / **Go back**.

---

## 8. Region detection (localization)

Two independent sources of candidate regions are combined:

1. **Accessibility node scan** — `collectVisibleMediaRegions()` in
   [`ShieldAccessibilityService.kt`](app/src/main/java/com/example/sinshield/ShieldAccessibilityService.kt)
   walks up to 400 nodes, keeping likely-media nodes (ImageView/TextureView/SurfaceView, media
   hints in id/description) within size bounds, deduped by IoU.
2. **OpenCV visual detection** —
   [`OpenCvMediaRegionDetector.kt`](app/src/main/java/com/example/sinshield/OpenCvMediaRegionDetector.kt)
   finds probable photo bounds directly from the screenshot (down-scaled to `ANALYSIS_MAX_WIDTH`
   720), with Instagram grid/post-aware configs. Supporting pure helpers:
   [`LineRectangleAssembler.kt`](app/src/main/java/com/example/sinshield/LineRectangleAssembler.kt)
   and [`TextEdgePattern.kt`](app/src/main/java/com/example/sinshield/TextEdgePattern.kt)
   (distinguishes UI text rows from real image edge detail).

Planners turn raw detections into the final crop list:
- [`LocalizedRegionPlanner.kt`](app/src/main/java/com/example/sinshield/LocalizedRegionPlanner.kt) —
  keeps localized inference in 1:1 correspondence with displayed OpenCV rectangles.
- [`InstagramMediaRegionPlanner.kt`](app/src/main/java/com/example/sinshield/InstagramMediaRegionPlanner.kt) —
  combines Instagram's visual crops with accessibility media bounds and infers grid/post surfaces.

> **Verified:** CV localization works well — a region can score sexy≈0.99 where the whole screen
> scores ≈0.01. "Photos not covered" is usually the strict-mode-off policy gate, not a detection
> failure.

---

## 9. Overlays and recovery

**[`OverlayManager.kt`](app/src/main/java/com/example/sinshield/OverlayManager.kt)** owns *every*
window SinSheld draws — nothing else in the app talks to `WindowManager`:
- **Localized covers** — per-post opaque covers, moved during scroll and reconciled against media
  nodes on content changes (scroll-peek auto-clear).
- **App block** — the full-screen shield with recovery buttons.
- **Site block** — the browser overlay shown when a blocked domain is detected.

**[`RecoveryController.kt`](app/src/main/java/com/example/sinshield/RecoveryController.kt)** owns
what happens once a full-screen block is up: **Return-to-feed**, **Scroll-past**, and
**Close-app** actions, Home-feed navigation, Recents dismissal, shielded gesture dispatch, and
browser exits. It exposes `navigationActionInProgress` / `closingAppInProgress` so the scanner
keeps the shield above a deliberate task-recreating transition.

Recovery choices appear after a five-second pause. While a full-screen shield is present, ordinary
accessibility events do not trigger screenshots: scanning resumes only after the user chooses a
recovery action. The shield is then removed only after three consecutive fresh screenshots each
pass the complete analysis pipeline. Any suspicious or unsafe result stops that verification,
resets the clean streak, and restarts the five-second pause before choices return.

The right recovery actions depend on which app surface is visible.
[`ShieldedApp.kt`](app/src/main/java/com/example/sinshield/ShieldedApp.kt) resolves a
`ShieldedScreenMode` (FEED, STORY, PROFILE_OR_POST, REELS, EXPLORE, DIRECT_MESSAGE, LIVE,
CREATION, UNKNOWN) per app from text/resource-id evidence, using deliberately loose phrase matching
with quorums. **CREATION** mode is special: it's the only mode that can suppress a confirmed unsafe
verdict (so the app never covers the user's *own* draft), and it therefore requires stronger
evidence than ordinary navigation. Pure success criteria for navigation live in
[`NavigationRecoveryPolicy.kt`](app/src/main/java/com/example/sinshield/NavigationRecoveryPolicy.kt).

---

## 10. Scan scheduling, freshness, and dedup

**[`ScanScheduler.kt`](app/src/main/java/com/example/sinshield/ScanScheduler.kt)** is the
Android-free state machine: at most one capture/inference flight at a time, a **generation counter**
so a late result can be discarded, a trailing-scan debounce, and an **adaptive interval**
(active 250 ms → stable 1150 ms, backing off after 3 stable safe frames).

- **Freshness** ([`ScanFreshnessPolicy.kt`](app/src/main/java/com/example/sinshield/ScanFreshnessPolicy.kt)):
  Instagram emits content-change events even when media is visually unchanged, and inference takes
  >1 s, so rejecting every result after any such event would starve the blocker. A late **SAFE**
  result is retried; a late **unsafe** result is conservatively accepted while package + window
  identity remain current.
- **Model warm-up:** when the foreground app is *not* monitored, the models are warmed while idle
  so the first real scan isn't cold.
- **Dedup / hashing:** `FrameHasher` uses a difference hash (retains post edges even when most of
  the screen is white UI). A Hamming distance ≥ 14 counts as a significant content change.

---

## 11. Website protection (DNS VPN)

**[`AdultContentVpnService.kt`](app/src/main/java/com/example/sinshield/AdultContentVpnService.kt)**
is a self-contained local VPN, independent of the accessibility service:

- **Split tunnel:** routes only traffic to a virtual DNS address (`10.77.0.2/32`) into the tunnel;
  everything else keeps its normal path. It captures **classic DNS only** and never touches web
  traffic.
- **Filtering:** for each UDP DNS query on port 53 it parses the question
  ([`DnsPacketCodec.kt`](app/src/main/java/com/example/sinshield/DnsPacketCodec.kt) —
  `Ipv4UdpPacketCodec` + `DnsMessageCodec`). Blocked hosts get an `NXDOMAIN` response built locally;
  allowed hosts are forwarded to the network's real resolvers (with `1.1.1.1` / `8.8.8.8` as
  fallback) over a `protect()`ed socket so the query doesn't loop back in.
- **Blocklist:**
  [`AdultDomainMatcher.kt`](app/src/main/java/com/example/sinshield/AdultDomainMatcher.kt) is an
  immutable suffix matcher; `AdultDomainListRepository` ships a bundled seed
  (`assets/adult_domains_seed.txt`) and can atomically replace it with a validated maintained list.
- **Reporting:** a blocked domain broadcasts to the accessibility service (which shows the browser
  site-block overlay if a browser is foreground) and posts a notification, coalesced per-domain to
  one report per cooldown.

> **Limitation (surfaced in the UI):** apps using encrypted DNS (DoH/DoT) can bypass classic DNS
> filtering, and Android allows only one VPN at a time.

---

## 12. User feedback and false positives

When a block is shown, the frame's JPEG is prepared as evidence. From the block the user can:
- **Dismiss** the incident (hidden while the same frame is visible), or
- **Report a false positive** — handled by
  [`FeedbackRepository.kt`](app/src/main/java/com/example/sinshield/FeedbackRepository.kt), which
  remembers the `(package, frameHash)` pair so future scans of that frame short-circuit to SAFE, and
  stores the evidence privately (outside Android's automatic backup). The correction is applied
  in-memory *before* the async write finishes, so a re-scan can't recreate the block in the meantime.

---

## 13. The app UI

[`MainActivity.kt`](app/src/main/java/com/example/sinshield/MainActivity.kt) is a single Compose
screen (theme in `ui/theme/`). It's essentially an **onboarding + settings console**:

- A first card containing only the required Accessibility and blocking-overlay permissions.
- Optional reliability and protection controls follow it: battery/background startup, website
  protection, Always-on VPN, and failure notifications.
- **Domain block list** status, persistent user-added domains with removal controls, and a separate
  manual refresh for the maintained built-in list. Changes reload the running VPN immediately.
- **Block suggestive content** switch.
- **Advanced detection tuning**: described per-threshold sliders + "Verifier must approve decisive
  hits" + a return-to-recommended-settings action. Changes apply to the running scanner
  automatically.

Each setting has a tap-for-details dialog explaining exactly what it does and its privacy posture.

---

## 14. Debug instrumentation and timing

Developer instrumentation is kept in the codebase but off by default:

- **Global debug mode** — `GlobalDebugMode.ENABLED` (in
  [`DebugSettings.kt`](app/src/main/java/com/example/sinshield/DebugSettings.kt)) is the code-only
  gate for every developer feature. When enabled, the main screen shows a **Debug** card with
  independent controls for photo dumps, overlay feedback, the overlay dismiss button, and last
  shutdown diagnostics. No gesture or user-facing setting can reveal the card.
- **Image dumps** — the Debug card's **Photo dumps** switch gates writing a JPEG per classifier
  crop, verifier crop, and OpenCV overlay into the app's Pictures debug folders. These are
  **synchronous encodes on the inference thread** that add ~1.5 s per frame.
- **`BAN` timing log** — one release-safe `Log.i` line per shown block, measuring the visible→blocked
  latency without the debuggable-only image dumps. All stamps are `uptimeMillis`, so:
  `eventToCaptureMs` (debounce + cooldown + capture queueing) + `captureToBlockMs` (screenshot
  delivery + inference + main-thread hop + overlay inflation) = `totalMs`; `inferenceMs` is the
  model-only slice. `provisional=true` marks a cover shown before its confirmation; the matching
  `provisional=false` line follows when finalized.

There's also a rich per-scan diagnostic `Log.i` (tag `SinSheld`) in `completeScan` dumping every
score, threshold, region, and freshness flag.

> **Latency note (from log analysis):** typical event→block was ~4–5 s (up to ~6.5 s when backed
> up), with inference dominating (~80%); ~1.5 s of that was the synchronous debug JPEG I/O now
> disabled by default. Recent work (parallelized Instagram whole-screen + region passes, early-exit
> after the first actionable region, batched classifier) reduced this.

---

## 15. Build, run, and test

The build works locally using **Android Studio's bundled JBR** (OpenJDK 25) at
`/opt/android-studio/jbr` — `JAVA_HOME`/`PATH` are otherwise empty in this environment.

```bash
# Compile
JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:compileDebugKotlin

# Unit tests
JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:testDebugUnitTest
```

Notes:
- `release` build type has R8/optimization **disabled** (`optimization { enable = false }`).
- Java source/target compatibility is **11**.
- Model assets are kept uncompressed (`noCompress += ["tflite", "onnx"]`) so they can be memory-
  mapped straight from `assets`.
- **Kotlin gotcha:** block comments *nest* in Kotlin — a `/*` inside a KDoc (e.g. a path like
  `Pictures/*-debug`) opens a nested comment and swallows the rest of the file. This previously
  broke the build.

### Tests
16 unit-test files (~2,000 lines) cover the pure logic: `ContentPolicyTest`, `VerifierPolicyTest`,
`ScanSchedulerTest`, `ScanFreshnessPolicyTest`, `LocalizedRegionPlannerTest`,
`InstagramMediaRegionPlannerTest`, `LineRectangleAssemblerTest`, `LocalizedBoxSelectorTest`,
`NavigationRecoveryPolicyTest`, `ShieldedAppTest`, `TextEdgePatternTest`, `AdultDomainMatcherTest`,
`DnsPacketCodecTest`, `AppAnalysisFlowSelectorTest`, plus the defaults.

---

## 16. File / module reference

### Screen protection
| File | Lines | Responsibility |
|---|---|---|
| `ShieldAccessibilityService.kt` | 579 | Event loop, foreground identity, node reads, wiring |
| `FrameScanner.kt` | 974 | Screenshot→classify→verdict→overlay pipeline, confirmation, timing |
| `ScanScheduler.kt` | 160 | Single-flight state machine, generations, adaptive interval |
| `ScanFreshnessPolicy.kt` | 11 | Discard-stale-result decision |
| `AppAnalysisFlow.kt` | 554 | Registry + shared analyzers, finalizer, verifier, localized analyzer |
| `XAnalysisFlow.kt` / `RedditAnalysisFlow.kt` / `InstagramAnalysisFlow.kt` | 22 / 22 / 48 | Per-app sequencing |
| `ContentPolicy.kt` | 171 | Verdicts, thresholds, `combine`, `VerifierPolicy` |
| `NsfwClassifier.kt` | 136 | MobileNetV2 TFLite wrapper (batched) |
| `NsfwVerifier.kt` | 137 | Marqo ViT ONNX wrapper |
| `OverlayManager.kt` | 505 | All WindowManager windows |
| `RecoveryController.kt` | 773 | Recovery actions, navigation, gestures |
| `ShieldedApp.kt` | 610 | Per-app surface/screen-mode evidence |
| `NavigationRecoveryPolicy.kt` | 44 | Pure navigation success criteria |
| `OpenCvMediaRegionDetector.kt` | 567 | Visual region detection |
| `LineRectangleAssembler.kt` / `TextEdgePattern.kt` | 358 / 43 | OpenCV support helpers |
| `LocalizedRegionPlanner.kt` / `InstagramMediaRegionPlanner.kt` | 49 / 711 | Crop planning |
| `FeedbackRepository.kt` | 111 | Remembered false positives |
| `ProtectionPreferences.kt` | 149 | Settings, protection levels, thresholds |
| `ModelAnalysisDebugWriter.kt` / `OpenCvDetectionDebugWriter.kt` | 219 / 232 | Debug image dumps (off by default) |

### Website protection
| File | Lines | Responsibility |
|---|---|---|
| `AdultContentVpnService.kt` | 307 | Split-tunnel DNS VPN |
| `DnsPacketCodec.kt` | 218 | IPv4/UDP + DNS message parse/build |
| `AdultDomainMatcher.kt` | 78 | Immutable suffix matcher |
| `AdultDomainListRepository.kt` | 203 | Seed + maintained blocklist |

### UI
| File | Lines | Responsibility |
|---|---|---|
| `MainActivity.kt` | 599 | Compose settings/onboarding |
| `ui/theme/*` | — | Material 3 theme |

---

## 17. Tech stack and dependencies

- **Kotlin** + **Jetpack Compose** (BOM, Material 3, Activity Compose)
- **AndroidX** core-ktx, lifecycle-runtime-ktx
- **LiteRT** `com.google.ai.edge.litert:litert:1.4.2` (TensorFlow Lite successor) — primary classifier
- **ONNX Runtime** `com.microsoft.onnxruntime:onnxruntime-android:1.29.0` — verifier
- **OpenCV** `org.opencv:opencv:5.0.0.1` — visual region detection
- **JUnit** — unit tests; Espresso / Compose test for instrumented tests
- Build: **AGP** (via version catalog `libs.versions.toml`), Gradle with Kotlin DSL

---

## 18. Privacy notes

- **All image analysis is on-device.** Screenshots are captured, classified, and discarded locally;
  they are not uploaded. Feedback evidence is stored privately and excluded from Android backup.
- **The VPN does not decrypt or inspect web traffic** — it only answers DNS questions, returning
  `NXDOMAIN` for blocklisted hosts and forwarding the rest unchanged.
- The only network use is forwarding allowed DNS queries and (optionally) refreshing the domain
  blocklist.
- SinSheld can only request Android's Accessibility, overlay, and VPN permissions; the user grants
  and revokes them through system settings.

---

*Generated as a project overview from source. For the authoritative behavior, the code is the
source of truth — the `SinSheld` logcat tag and the per-scan diagnostic line are the fastest way to
see the pipeline's live decisions.*
