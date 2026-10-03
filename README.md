# Punto25 Android

Punto25 is an Android delivery/errand platform currently under active Alpha development.

Current validated Android baseline:

- Version: `0.3-alpha3-dev3.6`
- versionCode: `13`
- Validated source baseline: `112e77223e740f3b399b9a2ad6d0a481ba736c3d` from the private historical repository.
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

Configuration values can be supplied through Gradle properties or environment variables where supported, including:

- `MAPS_API_KEY`
- `FIREBASE_API_KEY`
- `FIREBASE_APP_ID`
- `FIREBASE_PROJECT_ID`
- `GOOGLE_WEB_CLIENT_ID`
- `PUNTO25_API_BASE_URL`
- `WHATSAPP_VERIFY_NUMBER`

Do not commit local credentials, private keys, keystores, service-account files or production secrets.

CI validates the project with:

```text
:app:testDebugUnitTest
:app:assembleDebug
```

## Development workflow

1. branch from the latest validated `main`;
2. implement one narrowly scoped approved change;
3. run local/unit checks where practical;
4. open a pull request;
5. let CI validate tests/build;
6. merge after checks pass;
7. generate/use the APK from the validated commit;
8. perform physical-device testing when required;
9. mark the corresponding task resolved only after validation.

GitHub Actions is used as CI validation, not as an iterative patch-application mechanism.

## Security

Read `SECURITY.md` before reporting vulnerabilities or handling sensitive configuration.

The repository must not contain production signing material or backend secrets.

## Copyright and usage

Copyright © 2026 Ernesto Enrique Amaya. All rights reserved.

This repository is intended to be publicly viewable, but it is not being released under an open-source license. See `LICENSE` and `COPYRIGHT.md`. Third-party components remain subject to their own terms; see `THIRD_PARTY_NOTICES.md`.

## Contributions

Punto25 is currently in an owner-controlled Alpha phase. Unsolicited external pull requests are not accepted at this stage. See `CONTRIBUTING.md`.
