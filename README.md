# Punto25 Android

Punto25 is an Android delivery/errand platform under active Alpha development. This repository is currently public; the product is not yet a production release.

## Current Android baseline

- Version: `0.3-alpha3-dev3.9`
- versionCode: `16`
- applicationId / namespace: `ar.com.mandados.app`
- minSdk: `26`
- targetSdk: `36`
- compileSdk: `37`
- Java: `17`
- Android Gradle Plugin: `9.1.1`
- Kotlin / Compose plugin: `2.4.20`
- CI Gradle: `9.3.1`
- Canonical branch: `main`

## Project status

The current Alpha client includes Customer, Rider and Administration flows; orders and status history; Rider shifts/capacity; service pricing; payment/transfer flows; local Alpha persistence; structured order locations; and Admin order-zone overrides.

The current map stack is **MapLibre Compose + OpenFreeMap**. Google Maps Compose/SDK and `MAPS_API_KEY` are not part of the current application configuration. Google Play Services Location remains in use only for Android location access (for example, obtaining a recent device location); it is not the map renderer.

Authentication uses Firebase Authentication and Google Identity. Customer authentication uses the default Firebase app. Administration uses a separate Firebase app/auth context and then calls the backend endpoint `/v1/admin/access`; only an `AUTHORIZED` result grants the transient Admin session. `UNAUTHORIZED`, missing credentials and unavailable/error states fail closed. There is no current local `ALPHA_ADMIN_PIN` authorization mechanism.

Alpha data is currently persisted locally on-device. Production still requires trusted server authority for critical identity/authorization, core data integrity, multi-device synchronization and other production-grade controls; client-side code must not be treated as a security boundary.

Backend source remains separate in the private `punto25-backend` repository. The historical `mandados-android` repository also remains private.

## Runtime configuration

Supported runtime/build configuration names currently include:

- `FIREBASE_API_KEY`
- `FIREBASE_APP_ID`
- `FIREBASE_PROJECT_ID`
- `GOOGLE_WEB_CLIENT_ID`
- `PUNTO25_API_BASE_URL`
- `WHATSAPP_VERIFY_NUMBER`
- `WHATSAPP_VERIFICATION_ENABLED`

WhatsApp/Meta integration is intentionally paused and optional. WhatsApp verification is enabled only when `WHATSAPP_VERIFICATION_ENABLED=true` and the remaining required configuration is present; otherwise that verification path stays disabled and must not block unrelated functionality.

Do not commit credentials, private keys, keystores, service-account files or secret values.

## CI and code scanning

`.github/workflows/ci.yml` runs for every pull request targeting `main`. Pushes to `main` also run CI except for the documented push-side path exclusions. The `test-and-build` job uses Java 17, Android SDK `platforms;android-37.0`, Build Tools `36.0.0` and Gradle `9.3.1`, then executes:

```text
:app:testDebugUnitTest
:app:assembleDebug
```

`.github/workflows/codeql.yml` runs CodeQL Advanced for pushes and pull requests to `main`, plus its scheduled run. It analyzes GitHub Actions and Java/Kotlin; Java/Kotlin uses a manual Android tests/build step before analysis.

The active repository ruleset `Protect main` applies to the default branch. It requires normal changes through Pull Requests, requires the `test-and-build` status check and review-thread resolution, uses 0 mandatory external approvals for the current single-owner workflow, and blocks branch deletion and non-fast-forward/force-push updates.

## Alpha APK packaging

This repository currently contains `.github/workflows/alpha-apk.yml`. It is an owner-triggered `workflow_dispatch` flow that accepts a full 40-character target commit SHA and validates that the target exists in the trusted `main` history and is at or above the configured safe release floor.

The workflow checks out the exact target SHA in detached state, cleans the working tree, uses Java 17 / Android SDK 37.0 / Build Tools 36.0.0 / Gradle 9.3.1, validates required runtime/signing configuration by name without printing secret values, runs unit tests plus the debug build, verifies the APK with `apksigner`, requires exactly one signer and the configured certificate identity, records the APK SHA-256 and `BUILD_PROVENANCE.txt`, and uploads the evidence bundle with 7-day retention.

Signing material and runtime secret values are supplied through GitHub secrets; they are not stored in this README or expected to be committed to source.

## Development workflow

1. branch from the latest validated `main`;
2. implement one narrowly scoped approved change;
3. run automated checks;
4. open a Pull Request;
5. let Android CI and CodeQL validate the exact PR state;
6. merge only after the applicable review/authorization sequence;
7. package an Alpha APK only from an explicitly selected trusted `main` SHA when required;
8. reserve physical-device testing for behavior that automation cannot establish reliably.

## Security

Read `SECURITY.md`, `SECURITY_AUDIT.md` and `PUBLICATION_CHECKLIST.md` before handling sensitive configuration or release/publication work.

This repository is public, but production backend code, secrets and private governance remain separate. Public source visibility does not imply production readiness, Play Store publication or a `DATA COMPATIBILITY FREEZE`.

## Copyright and usage

Copyright © 2026 Ernesto Enrique Amaya. All rights reserved.

This repository is publicly viewable, but it is not released under an open-source license. See `LICENSE` and `COPYRIGHT.md`. Third-party components and media remain subject to their own terms; see `THIRD_PARTY_NOTICES.md`.

## Contributions

Punto25 is currently in an owner-controlled Alpha phase. Unsolicited external pull requests are not accepted at this stage. See `CONTRIBUTING.md`.
