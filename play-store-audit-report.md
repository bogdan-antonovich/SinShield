# Google Play Compliance Audit — SinShield

**App:** SinShield (`applicationId app.sinshield`, namespace `com.example.sinshield`)
**Type:** On-device NSFW/adult-content shield — Accessibility screen monitoring + DNS-filtering VPN + full-screen overlay. On-device ML (TFLite/ONNX/OpenCV). **Not** a loan/financial app.
**Audit date:** 2026-09-18 · Policy cycle: 2025–2026 (effective March 4, 2026)

## Verdict

**Not submission-ready yet — but the blockers are declarations and store-console work, not code rewrites.** The code itself is in good shape and unusually clean on the things that normally sink apps (no data exfiltration, no SMS/contacts/location, minimal permission set, no dynamic code loading). The risk is concentrated in **three high-scrutiny runtime capabilities** — the Accessibility Service, the VpnService, and the Special-Use foreground service — each of which triggers a mandatory Play Console declaration and manual review. None of these can be resolved by me from static code; they need declaration evidence and (for Accessibility) a real disclosure. That is why every serious item below is **NEEDS_CONFIRMATION**, not BLOCKER.

**Summary:** 0 BLOCKER · 2 WARNING · 3 INFO · 5 NEEDS_CONFIRMATION

---

## Findings

### WARNING

```
[GP-ACC01] status: WARNING
  Category: 12 — Accessibility service description
  Finding: The accessibility_service_config declares android:description="@string/app_name". The description is required to explain, to the user, what the service does and why it needs to observe the screen. Using the bare app name provides no functional explanation and is a common trigger for manual-review rejection of accessibility apps.
  Evidence: app/src/main/res/xml/accessibility_service_config.xml — android:description="@string/app_name"; canRetrieveWindowContent="true", canTakeScreenshot="true", canPerformGestures="true"
  Reference: device-abuse.md (Accessibility API section)
  Policy Source: https://support.google.com/googleplay/android-developer/answer/16559646
  Declaration Channel: None (this is an in-APK content requirement, not a console toggle)
  Required by policy: The AccessibilityService must disclose what it does; the description is shown to the user before enabling. A description that does not describe the service's function does not meet the disclosure intent.
  Suggested implementation: non-policy, auditor's suggestion only — point android:description at a dedicated string, e.g. "SinShield watches the screen to detect and cover explicit images. It does not collect or transmit what you view."
  Deadline: Active
```

```
[GP-BAT01] status: WARNING
  Category: 3 — REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
  Finding: The app requests the battery-optimization exemption. This permission is an acceptable-use-restricted permission on Play; only a narrow set of use cases qualify, and requesting it draws review. A persistent shield may qualify, but the app must be able to justify why standard foreground-service behavior is insufficient.
  Evidence: AndroidManifest.xml — <uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
  Reference: permissions.md (restricted permissions)
  Policy Source: https://support.google.com/googleplay/android-developer/answer/16585319
  Declaration Channel: None (justified in listing/review, not a form)
  Required by policy: Apps may only request this permission for use cases Google lists as acceptable for continuous background operation.
  Suggested implementation: non-policy, auditor's suggestion only — confirm the shield genuinely needs it beyond the FGS; be ready to justify in the review. Consider gating the request behind a clear user-facing explanation.
  Deadline: Active
```

### INFO

```
[GP-R8] status: INFO
  Category: 1 — Release build optimization
  Finding: The release build type sets optimization { enable = false }; there is no R8/minify or resource shrinking. Not a policy requirement, but you ship a larger, un-obfuscated APK and lose the ProGuard mapping that helps triage Play Vitals crashes.
  Evidence: app/build.gradle.kts — release { optimization { enable = false } }; no proguard-rules.pro present
  Reference: build-and-signing.md
  Policy Source: N/A (best practice)
  Declaration Channel: None
  Required by policy: None.
  Suggested implementation: non-policy, auditor's suggestion only — enable R8 + resource shrinking for release and retain the mapping file.
  Deadline: N/A
```

```
[GP-BKP] status: INFO
  Category: 5 — Data handling
  Finding: android:allowBackup="true". Local preferences/state (e.g. shield settings) will be included in cloud/adb backups. Low risk here since no sensitive personal data is stored, but review whether backup of shield configuration is desirable (a user could restore around a bypass).
  Evidence: AndroidManifest.xml — android:allowBackup="true"
  Reference: data-privacy.md
  Policy Source: N/A
  Declaration Channel: None
  Required by policy: None.
  Suggested implementation: non-policy, auditor's suggestion only — consider allowBackup="false" or a backup exclusion rule for shield state.
  Deadline: N/A
```

```
[GP-SDK] status: INFO
  Category: 1 — Build target
  Finding: targetSdk = 37 / compileSdk release(37). This comfortably exceeds the current minimum (API 35) and the Aug 31, 2026 requirement. Just confirm you are building against a finalized (non-preview) SDK before uploading a production release.
  Evidence: app/build.gradle.kts — compileSdk release(37), targetSdk = 37, minSdk = 26
  Reference: build-and-signing.md
  Policy Source: https://support.google.com/googleplay/android-developer/answer/11926878
  Declaration Channel: None
  Required by policy: New apps and updates must target a recent API level (currently API 35+). Met.
  Suggested implementation: None.
  Deadline: Aug 31, 2026 (already satisfied)
```

---

## Pending Confirmation (NEEDS_CONFIRMATION)

These are the real gating items. Each has a legitimate Play declaration channel; I cannot confirm from code whether the developer has completed it, so none is escalated to BLOCKER. **Do not submit until all five are resolved in the Play Console.**

```
[GP-ACC02] status: NEEDS_CONFIRMATION
  Category: 12 — Accessibility API use + Prominent Disclosure
  Finding: The app's core function relies on AccessibilityService with canRetrieveWindowContent, canTakeScreenshot, and canPerformGestures. Content-filtering/parental-control is a permitted accessibility use, but Play requires a Prominent Disclosure + in-app consent explaining the accessibility use before enabling, and (for many apps) a Play Console declaration justifying it. Auditor cannot confirm the disclosure/declaration exists.
  Evidence: AndroidManifest.xml service .ShieldAccessibilityService (BIND_ACCESSIBILITY_SERVICE); accessibility_service_config.xml capabilities
  Reference: device-abuse.md; consent-flow.md
  Policy Source: https://support.google.com/googleplay/android-developer/answer/16559646
  Declaration Channel: Permissions Declaration / Prominent Disclosure
  Declaration Status: Unknown
  Required by policy: Use of the AccessibilityService API must be disclosed to the user and used only for the disclosed, user-facing purpose. Apps must provide prominent disclosure of accessibility use.
  Suggested implementation: non-policy, auditor's suggestion only — add an in-app disclosure screen shown before routing the user to Accessibility settings, and complete the Play Console accessibility use declaration.
  Deadline: Active
  Resolution requires: (a) evidence of an in-app prominent-disclosure screen; (b) Play Console declaration status.
```

```
[GP-ACC03] status: NEEDS_CONFIRMATION
  Category: 12 — Accessibility autonomous-action prohibition (Jan 28, 2026)
  Finding: canPerformGestures="true" is set and RecoveryController performs swipes/gestures. The Oct 2025 / Jan 28 2026 policy prohibits using the Accessibility API to autonomously plan/initiate/execute actions on the user's behalf. Whether SinShield's gesture use (recovery/navigation) falls inside the permitted "assist the disclosed shield function" scope or crosses into prohibited autonomous action cannot be determined statically.
  Evidence: accessibility_service_config.xml canPerformGestures="true"; RecoveryController.kt (centered-swipe recovery logic ~line 167)
  Reference: device-abuse.md (Oct 2025 autonomous-action update)
  Policy Source: https://support.google.com/googleplay/android-developer/answer/16559646
  Declaration Channel: None (absolute policy) — but scope-dependent
  Declaration Status: Not Applicable
  Required by policy: Apps may not use the Accessibility API to autonomously perform actions without user request outside the app's disclosed accessibility purpose.
  Suggested implementation: non-policy, auditor's suggestion only — confirm every gesture is a direct part of the disclosed shielding function (dismissing/covering explicit content) and not general automation; document the justification for review.
  Deadline: Jan 28, 2026 (enforced)
  Resolution requires: confirmation that gesture use is limited to the disclosed shield behavior.
```

```
[GP-VPN01] status: NEEDS_CONFIRMATION
  Category: 5/10 — VpnService use
  Finding: AdultContentVpnService uses VpnService for on-device DNS filtering of adult domains. VpnService is permitted only when it is core functionality and the app does not collect user data or redirect traffic to third parties for non-app purposes. The tunnel here appears local DNS-only (protect() on sockets, own traffic excluded, blocklist from StevenBlack/hosts). Play still gates VPN apps behind declaration/review; auditor cannot confirm the console declaration.
  Evidence: AdultContentVpnService.kt (DatagramSocket DNS forwarding, protect(socket), local filtering); AdultDomainListRepository.kt downloads public hosts list over HTTPS
  Reference: data-privacy.md; deceptive-behavior.md
  Policy Source: https://support.google.com/googleplay/android-developer/answer/10144311
  Declaration Channel: Data Safety + VPN core-functionality declaration
  Declaration Status: Unknown
  Required by policy: Apps using VpnService must use it for core functionality, must not collect personal/sensitive data without disclosure, and must not redirect user traffic to third parties.
  Suggested implementation: non-policy, auditor's suggestion only — declare the VPN as core functionality; confirm no browsing/DNS data leaves the device; state the blocklist source in the privacy policy.
  Deadline: Active
  Resolution requires: (a) confirmation no user traffic/DNS is transmitted off-device; (b) console declaration.
```

```
[GP-FGS01] status: NEEDS_CONFIRMATION
  Category: 3 — FOREGROUND_SERVICE_SPECIAL_USE
  Finding: Both the accessibility service and the VPN service declare foregroundServiceType="specialUse" with subtypes "nsfw_screen_monitoring" / "on_device_adult_domain_dns_filtering". Special Use FGS requires a Play Console declaration justifying why no standard FGS type applies; Google may reject if a standard type (or none) fits. Auditor cannot confirm the declaration.
  Evidence: AndroidManifest.xml — two services with android:foregroundServiceType="specialUse" + PROPERTY_SPECIAL_USE_FGS_SUBTYPE
  Reference: permissions.md (FGS types)
  Policy Source: https://support.google.com/googleplay/android-developer/answer/13392821
  Declaration Channel: Play Console FGS declaration
  Declaration Status: Unknown
  Required by policy: Every foreground service must declare a type; Special Use requires justification that no other type is appropriate.
  Suggested implementation: non-policy, auditor's suggestion only — complete the Special Use FGS declaration for both services with the justification text.
  Deadline: Active
  Resolution requires: Play Console FGS declaration status.
```

```
[GP-DS01] status: NEEDS_CONFIRMATION
  Category: 5 — Data Safety form + privacy policy
  Finding: App holds INTERNET and reads screen content on-device. Static review shows no user data exfiltration — the only network egress is (1) local DNS forwarding in the VPN and (2) an HTTPS GET of a public hosts blocklist (StevenBlack/hosts). This implies a minimal/"no data collected" Data Safety posture, but the form and an active privacy-policy URL must still be completed and must match runtime behavior.
  Evidence: AdultDomainListRepository.kt:30,164 (blocklist download); no HttpURLConnection/OkHttp/Retrofit upload of user data found in app/src/main/java; on-device TFLite/ONNX/OpenCV classification
  Reference: data-privacy.md; store-listing.md
  Policy Source: https://support.google.com/googleplay/android-developer/answer/10787469
  Declaration Channel: Data Safety
  Declaration Status: Unknown
  Required by policy: The Data Safety section must accurately declare all data collection/sharing; a linked privacy policy is required.
  Suggested implementation: non-policy, auditor's suggestion only — declare "no data collected/shared" if accurate; publish a privacy policy stating screen analysis is on-device and no viewed content is transmitted.
  Deadline: Active
  Resolution requires: (a) completed Data Safety form; (b) live privacy-policy URL.
```

---

## Reference Coverage Report

| # | Reference | Loaded | Findings | Notes |
|---|-----------|--------|----------|-------|
| 1 | build-and-signing.md | ✅ | 2 | targetSdk OK; R8 disabled |
| 2 | permissions.md | ✅ | 3 | FGS special-use, battery, overlay |
| 3 | financial-declaration.md | ⏭ Not Applicable | 0 | Not a financial/loan app; no lending, payments, or credit features |
| 4 | data-privacy.md | ✅ | 2 | Data Safety + VPN egress |
| 5 | store-listing.md | ⏭ Not Applicable | 0 | No store-listing assets present in repo to audit; content-rating/IARC noted in checklist |
| 6 | deceptive-behavior.md | ✅ | 0 | No system-UI mimicry, no hidden/remote code loading found |
| 7 | spyware-policy.md | ✅ | 0 | No SMS/contacts/call-log; no user-data exfiltration found |
| 8 | device-abuse.md | ✅ | 2 | Accessibility disclosure + autonomous-action gesture scope |
| 9 | consent-flow.md | ✅ | (folded into GP-ACC02) | Prominent-disclosure ordering must be verified in-app |
| 10 | loan-harassment.md | ⏭ Not Applicable | 0 | Not a loan app |
| 11 | deployment.md | ✅ | 0 | Developer verification / testing tracks are console-side; nothing in code blocks it |
| 12 | code-audit.md | ✅ | — | Used as grep pattern source |
| 13 | intellectual-property.md | ✅ | 0 | No third-party bank/brand logos; blocks Instagram/Twitter/Reddit surfaces but does not impersonate them — confirm store icon/screenshots use owned assets |
| 14 | restricted-content.md | ✅ | 0 | App blocks adult content; ensure listing/screenshots show no explicit content and rating questionnaire reflects the subject matter |

Coverage: 14/14 loaded, 3 marked Not Applicable
Total findings: 5 (0 BLOCKER, 2 WARNING, 3 INFO)
Pending confirmation: 5 NEEDS_CONFIRMATION (not part of the verdict; require developer/console evidence to resolve)

---

## What to do before submitting (ordered)

1. **Accessibility disclosure (GP-ACC02, GP-ACC01):** add an in-app prominent-disclosure screen before sending users to Accessibility settings, and set a real `android:description` string. Complete the Play Console accessibility declaration. *This is the single most likely rejection point for this app.*
2. **Special-Use FGS declaration (GP-FGS01):** justify both services in the console.
3. **VPN declaration + privacy posture (GP-VPN01):** declare VpnService core functionality; confirm no DNS/browsing data leaves the device.
4. **Data Safety + privacy policy (GP-DS01):** complete the form (likely "no data collected") and publish/link the policy URL.
5. **Content rating questionnaire:** answer honestly for a tool that operates on adult content; keep the store listing and screenshots free of explicit imagery.
6. **Confirm gesture scope (GP-ACC03):** ensure recovery gestures stay within the disclosed shield function.
7. Optional polish: enable R8 + resource shrinking, retain the mapping, reconsider `allowBackup`.

**Not applicable to this app:** SMS/Call-Log permissions, READ_CONTACTS/Contact Picker, location, financial/loan declarations, account deletion (no account system found — confirm there is genuinely no login/account before relying on this).
