# Punto25 pre-publication security audit

Audit target: canonical `punto25-android` source repository at Android baseline `0.3-alpha3-dev3.9` / versionCode `16` / source commit `47a762914b24a10f7d678a3ad2b59b3888ec7c05`.

## Repository separation

The historical `mandados-android` repository remains private because its history contains retired Alpha signing material and previously included backend source.

Server source remains separate in the private `punto25-backend` repository.

Secret-bearing Alpha APK generation has been separated from this source repository. A dedicated private Alpha build channel performs owner-controlled signed builds from an explicitly supplied source commit; this source repository retains ordinary CI only.

This source repository must not contain signing keystores, signing passwords, private Alpha PIN values, backend secrets or service-account credentials.

## Current source and history status

Targeted inspection of the current tree did not identify active signing material or obvious hardcoded private credentials. Build/service configuration is injected through Gradle properties or environment variables where supported.

The retired historical Alpha Admin PIN is not part of the current source configuration. Ordinary builds receive an empty `ALPHA_ADMIN_PIN`, which keeps local Alpha Administration disabled. A PIN compiled into an Alpha client is only a temporary Alpha convenience and must never be treated as a production authorization boundary; production Admin access requires authenticated server-side authorization/RBAC.

Rider password material is stored as a salted PBKDF2-derived hash in the current local Alpha model; production authorization and account security still require trusted backend authority.

A complete all-object/all-ref history scan is still a pre-publication gate. Targeted/current-tree inspection must not be treated as certification of every historical Git object.

## Private Alpha build channel

The private Alpha workflow is manual-only and requires an exact 40-hex source SHA. The canonical source repository is fixed to `ernestoamaya/punto25-android`; the workflow checks out that exact commit, verifies that it is reachable from canonical `main`, and refuses ambiguous or non-main source.

Validation is split into two jobs. The first job receives no signing/PIN/service configuration secrets and runs unit tests plus an ordinary debug build. The second job re-checks the same source provenance before accessing Alpha secrets, reconstructs the keystore only in runner temporary storage, builds the signed APK, verifies the APK signature and expected signing certificate, validates application/package/version metadata, computes SHA-256, records build metadata and removes temporary signing material.

The first controlled private build from source SHA `47a762914b24a10f7d678a3ad2b59b3888ec7c05` completed successfully in workflow run `37397037436`. Its private artifact ID is `11384111656`, retained for 7 days. Downloaded evidence was independently checked: the artifact contains the APK, `SHA256SUMS.txt` and `BUILD_METADATA.txt`; the recorded APK SHA-256 matches the downloaded APK; package/version/source metadata match the canonical baseline; signer count is one and the expected Alpha certificate was verified by the workflow.

While the source repository remains private, the Alpha channel uses a temporary least-privilege read credential for source checkout only, with checkout credentials not persisted. After this source repository becomes public, that bootstrap credential must be removed from the workflow, deleted from repository secrets and revoked.

## CI in this source repository

The source repository retains only ordinary Android CI for build/test validation. CI uses least-privilege `contents: read`, immutable full commit SHAs for third-party Actions, cancellation of superseded runs, and no Alpha signing/PIN secrets.

The CI command remains:

```text
:app:testDebugUnitTest
:app:assembleDebug
```

No GitHub Actions workflow in this source repository should generate or publish a secret-bearing Alpha artifact.

## Remaining gates before public visibility

- run the planned all-ref/all-object secret scanner and classify any findings;
- remove or allow expiry of older Alpha artifacts from the source repository before publication;
- prune obsolete branches only after the history scan and classification step;
- re-scan the final tree/history after cleanup;
- review dependency/license notices against resolved dependencies;
- configure the intended protected-main ruleset / required CI checks;
- enable applicable repository security features before publication;
- perform final owner review before changing repository visibility.

This document records engineering controls and findings; it is not a legal opinion or a substitute for a professional security assessment.
