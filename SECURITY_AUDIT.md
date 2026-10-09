# Punto25 security audit and current repository posture

Current Android baseline: `0.3-alpha3-dev3.9` / versionCode `16` / applicationId `ar.com.mandados.app`. The canonical branch is `main`.

This document separates **historical pre-publication audit evidence** from the **current state of the already-public Android repository**. Historical scans are evidence about the refs/objects available at the time they were performed; they are not an immutable certification of later commits.

## A. Historical pre-publication audit

### Repository separation and cleanup

The historical `mandados-android` repository remains private because its history contains retired Alpha signing material and previously included backend source. The current backend repository `punto25-backend` also remains private.

`PREPUB-SCAN`, `PREPUB-CLEANUP` and `PREPUB-FINAL-SCAN` were completed before the repository became public. The all-ref/all-object scans covered branches, tags, accessible `refs/pull/*` refs and other Git objects exposed through fetch at the time of each scan. Gitleaks 8.30.1 plus complementary path/material and credential-pattern inventories found no active secret, signing material or backend source in that audited fetchable universe.

The retired Alpha Admin PIN was detected only as historical material. It was classified **HISTORICAL / MITIGATED** and is not a current authentication credential. The current Android build no longer defines or consumes `ALPHA_ADMIN_PIN`.

Older Alpha artifacts and the old secret-bearing source-repository Alpha workflow were removed during pre-publication cleanup. At that historical point, signed Alpha generation was moved away from the source repository while the public-source transition was being prepared.

The final all-object scan snapshot preceding later work covered 33 fetchable refs, 82 distinct commits and 494 distinct Git objects. `git fsck --full --no-reflogs` reported no unreachable or dangling objects in that fetched clone, and Gitleaks reported no new findings.

Technical limitation: those audits could only make claims about refs and objects GitHub exposed through accessible fetch paths. They could not certify hypothetical server-internal objects unavailable through any ref/fetch path, and they do not automatically certify commits created after the scan.

## B. Current public-repository posture

### Repository visibility and branch controls

`ernestoamaya/punto25-android` is currently **PUBLIC**. The historical `mandados-android` and current `punto25-backend` repositories remain private.

The active repository ruleset `Protect main` targets the default branch and currently enforces:

- non-fast-forward/force-push protection;
- branch-deletion protection;
- normal changes through Pull Requests;
- 0 mandatory external approvals for the current single-owner workflow;
- required review-thread resolution;
- required status check `test-and-build`.

The ruleset, rather than the classic branch-protection endpoint, is the authoritative current protection mechanism.

### Android stack and maps

The current client uses Kotlin / Jetpack Compose with compileSdk `37`, targetSdk `36`, minSdk `26`, Java `17`, Android Gradle Plugin `9.1.1` and Kotlin/Compose plugin `2.4.20`.

Internal maps use **MapLibre Compose** with the **OpenFreeMap Liberty** style. Google Maps Compose/SDK and `MAPS_API_KEY` are not part of the current app configuration. Google Play Services Location remains a dependency for Android location access and must not be confused with the removed Google Maps renderer/API-key integration.

### Authentication and authorization

Customer authentication uses Firebase Authentication / Google Identity on the default Firebase app.

Administration no longer uses a local Alpha PIN. Admin authentication is intentionally isolated in a secondary Firebase app/auth context (`punto25-admin-auth`) and uses a Firebase ID token to call the backend endpoint `/v1/admin/access`.

The client-side access policy is fail-closed:

- HTTP 200 with `authorized=true` → `AUTHORIZED`;
- 401/403 → `UNAUTHORIZED`;
- missing token, malformed/unexpected response, network/backend errors or other availability failures → no Admin authorization (`UNAUTHORIZED` or `UNAVAILABLE`).

Only `AUTHORIZED` enables the transient Admin session. Client-side checks are not a production security boundary; trusted backend authority remains required for production authorization and integrity.

### WhatsApp / Meta

Meta/WhatsApp integration remains intentionally paused and optional. The app still contains optional verification scaffolding. WhatsApp verification requires `WHATSAPP_VERIFICATION_ENABLED=true` plus the other required configuration; if not configured, the verification flow remains disabled and unrelated functionality must continue to work.

WhatsApp has therefore **not** been removed completely, but it is not a mandatory dependency of current Alpha operation.

### CI and CodeQL

`.github/workflows/ci.yml` currently runs Android CI for every Pull Request targeting `main`. Pushes to `main` also run CI except for the workflow's documented push-side path exclusions. The `test-and-build` job uses least-privilege `contents: read`, pinned Action SHAs, Java 17, Android SDK `platforms;android-37.0`, Build Tools `36.0.0` and Gradle `9.3.1`, then executes:

```text
:app:testDebugUnitTest
:app:assembleDebug
```

`.github/workflows/codeql.yml` currently provides **CodeQL Advanced** on pushes and Pull Requests to `main` plus a scheduled run. It analyzes GitHub Actions and Java/Kotlin. Java/Kotlin uses a manual Android tests/build step before CodeQL analysis.

CodeQL is therefore an active current control, not a feature deferred until publication.

### Current Alpha APK packaging

The current public source repository contains `.github/workflows/alpha-apk.yml`. Signed Alpha packaging is no longer accurately described as existing only in a separate private build channel.

The workflow is manually triggered with `workflow_dispatch` and a full 40-character `target_sha`. It verifies target existence, ancestry in trusted `main` and the configured safe release floor; checks out the exact target SHA detached; cleans the work tree; uses Java 17 / Android SDK 37.0 / Build Tools 36.0.0 / Gradle 9.3.1; validates required runtime/signing configuration by name; runs unit tests and `assembleDebug`; verifies the APK via `apksigner`; requires exactly one signer and the configured certificate identity; records SHA-256 and `BUILD_PROVENANCE.txt`; and uploads the APK/evidence bundle with 7-day retention.

Runtime/signing secrets are referenced through GitHub secrets and environment variables. This audit does not retrieve or reproduce secret values. The current configuration files inspected during this documentation reconciliation expose names/configuration only, not signing material or secret values.

### Current limitations and production gap

Punto25 remains Alpha software. Local on-device persistence and client-side business logic are not sufficient production authority. Production still requires appropriate trusted backend authority for critical data, identity/authorization, synchronization, financial integrity and operational hardening.

Play Store publication, production privacy/compliance review, final disclosures/permissions review and `DATA COMPATIBILITY FREEZE` remain future milestones.

## Controls requiring current settings verification

Some GitHub security settings are not established by repository files or the ruleset data inspected in this reconciliation. Their current enabled/disabled state must be verified separately at the applicable publication/production milestone rather than inferred from historical documentation:

- secret scanning;
- push protection;
- private vulnerability reporting;
- current Dependabot alert/security-update settings;
- current automatic dependency-submission settings.

Historical documentation records that several dependency/security settings had been enabled during pre-publication work, but this document does not convert that historical observation into an unverified present-tense guarantee.

## Scope statement

This document records engineering controls and findings. It is not a legal opinion, a penetration test, or a substitute for an independent professional security assessment. Any future release/publication decision must verify the exact target SHA and then-current repository/service configuration.
