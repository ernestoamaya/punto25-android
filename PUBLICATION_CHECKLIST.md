# Punto25 publication and release checklist

Canonical Android repository: `ernestoamaya/punto25-android`.

**Current repository state:** PUBLIC. The historical `ernestoamaya/mandados-android` repository and the current `ernestoamaya/punto25-backend` repository remain private.

Making the Android source repository public did **not** imply Play Store publication, production rollout or `DATA COMPATIBILITY FREEZE`.

## Historical pre-publication work — completed

These items describe controls performed before the repository became public. They are retained for traceability and must not be read as future PRIVATE → PUBLIC tasks.

### Source, history and security

- [x] Clean Android repository created and backend source kept separate.
- [x] Historical tracked signing material excluded from the clean Android import.
- [x] All-ref/all-object scans performed over the GitHub-exposed/fetchable refs and objects available at scan time.
- [x] Findings classified; no active secret or blocking Class A finding remained in the audited fetchable universe.
- [x] Retired Alpha Admin PIN classified **HISTORICAL / MITIGATED**; it is not a current authentication mechanism.
- [x] Obsolete branches/artifacts handled conservatively during pre-publication cleanup.
- [x] Final pre-publication scan completed with the documented technical limits of GitHub ref/fetch visibility.
- [x] Backend and historical repository separation preserved.

### Ownership, documentation and third-party material

- [x] Copyright holder identified as Ernesto Enrique Amaya.
- [x] Restrictive source-available `LICENSE` added; original Punto25 material is not automatically open-sourced.
- [x] `COPYRIGHT.md`, `SECURITY.md`, `CONTRIBUTING.md` and `CODEOWNERS` reviewed during pre-publication work.
- [x] Third-party notices/licensing reviewed for the then-resolved dependency/media set.
- [x] Pre-publication security audit and cleanup history documented.

### Historical transition to public source

- [x] `ernestoamaya/punto25-android` changed from private to public.
- [x] The obsolete “PRIVATE → PUBLIC” action is complete and is no longer a future publication gate.

Historical scans and reviews remain evidence for their time; later commits require their own delta/release review where appropriate.

## Current public-repository controls — verified

The following items were verified against the current repository/rules/workflows during P25-PEND-08:

- [x] Repository visibility is PUBLIC.
- [x] Default branch is `main`.
- [x] Active ruleset `Protect main` targets the default branch.
- [x] Normal changes to `main` require a Pull Request.
- [x] Mandatory external approvals are `0` for the current single-owner workflow.
- [x] Review-thread resolution is required.
- [x] Required status check is `test-and-build`.
- [x] Non-fast-forward/force-push updates are blocked by the active ruleset.
- [x] Deletion of the protected default branch is blocked by the active ruleset.
- [x] `.github/workflows/ci.yml` runs `test-and-build` for every Pull Request targeting `main`.
- [x] CI runs `:app:testDebugUnitTest` and `:app:assembleDebug` using Java 17, Android SDK 37.0 and Gradle 9.3.1.
- [x] `.github/workflows/codeql.yml` is present and configured for push/PR to `main` plus a schedule.
- [x] CodeQL Advanced analyzes GitHub Actions and Java/Kotlin; Java/Kotlin uses a manual Android build.
- [x] `.github/workflows/alpha-apk.yml` exists in this public repository and provides owner-triggered Alpha packaging from a trusted full target SHA.
- [x] Alpha packaging validates trusted-main ancestry/safe floor, exact detached checkout and clean working tree.
- [x] Alpha packaging validates runtime/signing configuration by name without printing secret values.
- [x] Alpha packaging runs unit tests + debug build, verifies APK signature/single signer/certificate identity, produces SHA-256 and `BUILD_PROVENANCE.txt`, and uploads an evidence bundle with 7-day retention.
- [x] Current Android baseline is `0.3-alpha3-dev3.9` / versionCode `16` / applicationId `ar.com.mandados.app`.
- [x] Current maps are MapLibre Compose + OpenFreeMap; `MAPS_API_KEY` is not current configuration.
- [x] Current Admin access uses isolated Firebase/Google authentication plus backend `/v1/admin/access`; no current local `ALPHA_ADMIN_PIN` mechanism is documented as active.

## Current GitHub security settings — verify before relying on them

The following settings are not proven by the repository files/ruleset evidence inspected in this documentation reconciliation. Do not mark them complete from historical statements alone:

- [ ] Verify current Secret scanning status.
- [ ] Verify current Push protection status.
- [ ] Verify current Private vulnerability reporting status.
- [ ] Verify current Dependency graph status.
- [ ] Verify current Dependabot alerts status.
- [ ] Verify current Dependabot security updates status.
- [ ] Verify current automatic Gradle dependency-submission status.

Do not enable billing, a paid plan or a trial merely to satisfy these checks. Cost authorization remains USD 0 unless explicitly changed.

## Future Play Store / production gates

These remain real future milestones and are not satisfied by the repository being public:

### Release source and build

- [ ] Select and verify the exact intended release/Alpha source SHA on trusted `main`.
- [ ] Confirm required CI and CodeQL checks are green for the applicable release state.
- [ ] Generate the controlled Alpha/release artifact through the approved packaging path and retain its signer/hash/provenance evidence.
- [ ] Perform physical-device installation/update/signature/auth/permission validation only where automation cannot establish the required evidence.

### Production architecture and data

- [ ] Establish/verify trusted backend authority for production-critical identity, authorization, core data and financial integrity.
- [ ] Complete multi-device/synchronization and other production hardening required by the release scope.
- [ ] Declare `DATA COMPATIBILITY FREEZE` before the first non-discardable real-user dataset; from that point, incompatible schema changes require explicit migration/recovery planning.

### Privacy, compliance and store readiness

- [ ] Review current Android permissions and Play Store disclosures against the exact release build.
- [ ] Complete privacy policy/data-handling review for the production architecture and connected services.
- [ ] Recheck branding, copyright, licenses, third-party notices and attribution for the release state.
- [ ] Verify current Google Play requirements and other changing Google/Firebase/Cloudflare/Meta requirements from official sources at the publication milestone.
- [ ] Complete Play Console listing, testing-track and production-release gates when separately authorized.
- [ ] Confirm no secret/configuration/private-governance material is included in the release or public documentation.

## Completion criteria

The Android source repository is already public. A future **Play Store/production release** is complete only after the applicable future gates above are verified for the exact release state and separately authorized.
