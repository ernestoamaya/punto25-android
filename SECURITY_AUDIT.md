# Punto25 pre-publication security audit

Audit target: clean `punto25-android` repository derived from the physically validated Android baseline `0.3-alpha3-dev3.6` / versionCode `13` / historical source commit `112e77223e740f3b399b9a2ad6d0a481ba736c3d`.

## Repository separation

The historical `mandados-android` repository remains private because its history contains the retired Alpha debug signing key and previously included backend source.

The publication repository is a new repository with a clean history. Its imported Android tree excludes `backend/` and excludes signing material such as `.jks`, `.keystore`, `.p12` and `.pfx` files.

Server source is maintained separately in the private `punto25-backend` repository.

## Current source scan

Targeted scans of the imported Android baseline did not find obvious hardcoded values matching common private-key or API-token patterns. Build/service configuration is designed to be injected through Gradle properties, environment variables or GitHub Secrets.

The legacy hardcoded Alpha Admin PIN (`2468`) has been removed from public client source. Public/ordinary builds receive an empty `ALPHA_ADMIN_PIN` and therefore keep local Administration disabled. The owner-controlled signed Alpha workflow injects a private 6–8 digit `ALPHA_ADMIN_PIN` from GitHub Secrets. This remains an Alpha-only control: a value compiled into a client APK must never be treated as a production authorization boundary. Production Admin access still requires authenticated server-side authorization/RBAC.

Rider password material is stored as a salted PBKDF2-derived hash in the current local Alpha model; production authorization and account security still require trusted backend authority.

## Signing

The historical tracked `debug.keystore` is not present in this clean repository. A new private Alpha signing key must be generated outside the repository and supplied only through GitHub Secrets. The retired historical key must never be reused as a trusted public-build signing key.

## CI

CI uses least-privilege `contents: read`, immutable full commit SHAs for third-party Actions, cancellation of superseded runs, unit tests before build, and no secrets for pull-request CI.

The signed Alpha APK workflow is manual and main-only. It refuses to proceed when the required signing secrets or private Alpha Admin PIN are absent, verifies the APK signature and emits a SHA-256 checksum with the artifact.

## Required before public visibility

- generate and configure the new private Alpha signing key;
- configure a private 6–8 digit `ALPHA_ADMIN_PIN` GitHub Secret for controlled Alpha builds;
- validate signed-APK installation after the signing-key rotation;
- validate the clean repository CI;
- confirm no backend/signing material exists in the final publication tree/history;
- review dependency/license notices against resolved dependencies;
- configure GitHub repository security features and a protected-main ruleset;
- enable secret scanning / push protection / code scanning where available;
- perform final owner review before changing repository visibility.

This document records engineering controls and findings; it is not a legal opinion or a substitute for a professional security assessment.
