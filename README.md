# Punto25 Android

Punto25 is an Android delivery/errand platform currently under active Alpha development.

Current validated Android baseline:

- Version: `0.3-alpha3-dev3.9`
- versionCode: `16`
- Canonical source branch: `main`; the exact publication SHA must be re-verified immediately before any visibility change.
- Android package/applicationId: `ar.com.mandados.app`

## Project status

This is Alpha software. It is intended for controlled development and physical-device validation, not for production use.

The current client includes, among other flows:

- Customer registration and local identity flows;
- orders/deliveries and status history;
- Rider invitation/login flows;
- Rider shifts and capacity management;
- Admin tools used during Alpha;
- service pricing and payment-method snapshots;
- Rider transfer confirmation workflow;
- local persistence used by the current Alpha;
- Google Maps/location integrations;
- Firebase/Google authentication integration scaffolding.

Some production-grade architecture remains intentionally pending, including server authority for core data, multi-device synchronization, production RBAC, production-grade payment/financial controls, FCM, private remote document storage and other hardening work.

## Architecture

Client stack:

- Kotlin
- Jetpack Compose
- Android SDK 36
- Java 17
- Google Maps Compose / Play Services Location
- Firebase Authentication integration

This repository contains client-side source only. Production backend code and server-side secrets are maintained separately in the private `punto25-backend` repository.

Client-side source code must never be treated as a security boundary. Production authorization and integrity guarantees belong on trusted server-side infrastructure.

## Build

Open the project with a current Android Studio/JDK 17 environment and allow Gradle to resolve dependencies.

Service/configuration values can be supplied through Gradle properties or environment variables where supported, including:

- `MAPS_API_KEY`
- `FIREBASE_API_KEY`
- `FIREBASE_APP_ID`
- `FIREBASE_PROJECT_ID`
- `GOOGLE_WEB_CLIENT_ID`
- `PUNTO25_API_BASE_URL`
- `WHATSAPP_VERIFY_NUMBER`

`ALPHA_ADMIN_PIN` defaults to an empty value when it is not supplied. Therefore ordinary builds from this repository keep local Alpha Administration disabled. The Alpha PIN is only a temporary client-side Alpha mechanism and is not a production authorization boundary.

Do not commit local credentials, private keys, keystores, service-account files or production secrets.

Public-source CI validates the project with:

```text
:app:testDebugUnitTest
:app:assembleDebug
```

This repository no longer generates or publishes signed/private Alpha APK artifacts. Owner-controlled Alpha builds are produced in a separate private build channel from an explicitly supplied 40-hex commit SHA that must resolve to this repository's canonical `main`. That private channel re-validates source provenance before accessing signing/configuration secrets, verifies the APK signature/certificate and package/version metadata, records SHA-256 and build metadata, and retains the artifact privately for a limited period.

## Development workflow

1. branch from the latest validated `main`;
2. implement one narrowly scoped approved change;
3. run local/unit checks where practical;
4. open a pull request;
5. let CI validate tests/build;
6. merge after checks pass;
7. generate any controlled Alpha artifact from the exact validated source SHA in the private Alpha build channel;
8. perform physical-device testing when required;
9. mark the corresponding task resolved only after validation.

GitHub Actions in this source repository are used for ordinary CI validation, not for distributing secret-bearing Alpha builds.

## Security

Read `SECURITY.md` and `SECURITY_AUDIT.md` before reporting vulnerabilities or handling sensitive configuration.

The repository must not contain production signing material or backend secrets. `PUBLICATION_CHECKLIST.md` separates pre-publication gates from controls that must be applied atomically immediately after a future visibility change.

## Copyright and usage

Copyright © 2026 Ernesto Enrique Amaya. All rights reserved.

This repository is intended to be publicly viewable, but it is not being released under an open-source license. See `LICENSE` and `COPYRIGHT.md`. Third-party components and media remain subject to their own terms; see `THIRD_PARTY_NOTICES.md`.

## Contributions

Punto25 is currently in an owner-controlled Alpha phase. Unsolicited external pull requests are not accepted at this stage. See `CONTRIBUTING.md`.
