# Punto25 pre-publication security audit

Audit target: canonical `punto25-android` source repository at Android baseline `0.3-alpha3-dev3.9` / versionCode `16` / applicationId `ar.com.mandados.app`. The canonical branch is `main`; the exact publish-state SHA must be verified again immediately before any visibility change.

## Repository separation

The historical `mandados-android` repository remains private because its history contains retired Alpha signing material and previously included backend source.

Server source remains separate in the private `punto25-backend` repository.

Secret-bearing Alpha APK generation has been separated from this source repository. A dedicated private Alpha build channel performs owner-controlled signed builds from an explicitly supplied source commit; this source repository retains ordinary CI only.

This source repository must not contain signing keystores, signing passwords, private Alpha PIN values, backend secrets or service-account credentials.

## Completed pre-publication scans and cleanup

`PREPUB-SCAN`, `PREPUB-CLEANUP` and `PREPUB-FINAL-SCAN` were completed before this documentation-finalization change.

The all-ref/all-object scans covered all branches, tags, accessible `refs/pull/*` refs and other Git objects that GitHub exposed through fetch at the time of each scan. Gitleaks 8.30.1 plus complementary path/material and credential-pattern inventories found no active secret, signing material or backend source in the audited fetchable universe.

The known retired Alpha Admin PIN was detected only as historical material. It was safely compared against the current private Alpha configuration without displaying either value or a PIN-derived fingerprint; it is different from the current value and remains classified as **HISTORICAL / MITIGATED**. It is not a current authentication credential.

Older Alpha artifacts created by the source repository were explicitly removed. The source repository's old secret-bearing Alpha workflow was removed; current source CI is separated from the private Alpha signing/configuration channel.

The final all-object scan snapshot preceding later documentation-only work covered 33 fetchable refs, 82 distinct commits and 494 distinct Git objects. `git fsck --full --no-reflogs` reported no unreachable or dangling objects in that fetched clone, and Gitleaks reported no new findings. Subsequent changes must therefore be checked as deltas, and the future publication gate must verify the exact final SHA rather than treating that earlier scan as an immutable certification of later commits.

Technical limitation: these audits can only make claims about refs and objects that GitHub exposes through accessible refs/fetch and that physically reach the audit clone. They cannot certify hypothetical server-internal Git objects that GitHub does not expose through any ref or fetch path.

## Current source and Alpha security posture

Ordinary builds receive an empty `ALPHA_ADMIN_PIN`, which keeps local Alpha Administration disabled. A PIN compiled into an Alpha client is only a temporary Alpha convenience and must never be treated as a production authorization boundary; production Admin access requires authenticated server-side authorization/RBAC.

Rider password material is stored as a salted PBKDF2-derived hash in the current local Alpha model; production authorization and account security still require trusted backend authority.

The first controlled private Alpha build was successfully validated, including package/version/source metadata, signer/certificate verification and APK hash evidence. Physical-device update validation was also completed: the validated Alpha build updated the installed app without uninstalling it or deleting app data and opened normally.

While the source repository remains private, the Alpha channel uses a temporary least-privilege read credential for source checkout only, with checkout credentials not persisted. After this source repository becomes public, that bootstrap credential must be removed from the workflow, deleted from repository secrets and revoked only after the private Alpha workflow has been validated without it.

## CI and repository hardening

The source repository retains only ordinary Android CI for build/test validation. CI uses least-privilege `contents: read`, immutable full commit SHAs for third-party Actions, cancellation of superseded runs, and no Alpha signing/PIN secrets.

The CI command remains:

```text
:app:testDebugUnitTest
:app:assembleDebug
```

PR #15 removed `paths-ignore` from the `pull_request` trigger while preserving the push-side path exclusions. Therefore the real `test-and-build` job now runs for every pull request targeting `main` and is ready to become a required status check after the repository becomes public.

GitHub Actions repository settings were hardened to read-only default workflow-token permissions, disabled Actions-created/approved pull requests, and require Actions references to use full-length commit SHAs.

Dependency graph, Dependabot alerts, Dependabot security updates and automatic Gradle dependency submission are enabled. The first automatic Gradle dependency-submission run completed successfully against canonical `main`.

Protected-main rulesets, secret scanning/push protection, CodeQL/code scanning and private vulnerability reporting remain deferred only where GitHub Free does not make the required capability available while this personal repository remains private. No paid plan, billing or trial was enabled.

## Remaining publication gates

Before changing visibility, the owner must complete the final presentation review and an exact-SHA preflight confirming repository state, documentation, CI and absence of unexpected concurrent changes.

If publication is authorized, the visibility change is valid only as an atomic operation followed immediately by the controls listed in `PUBLICATION_CHECKLIST.md`: protect `main`, require `test-and-build`, block force-push/delete, enable and verify the public-repository security features available at no cost, validate the private Alpha channel without its temporary source-read credential, then remove that repository secret and revoke the temporary token.

This document records engineering controls and findings; it is not a legal opinion or a substitute for a professional security assessment.
