# Punto25 publication checklist

Canonical public target: `ernestoamaya/punto25-android`.

Historical repository `ernestoamaya/mandados-android` must remain private. Backend repository `ernestoamaya/punto25-backend` must remain private. The dedicated Alpha build/distribution channel must remain private.

## Source and history

- [x] New clean Android repository created privately.
- [x] Backend excluded from the Android import.
- [x] Historical tracked signing material excluded from the Android import.
- [x] Current canonical Android baseline is `0.3-alpha3-dev3.9` / versionCode `16` / applicationId `ar.com.mandados.app`.
- [ ] Run the planned all-ref/all-object secret scan and classify findings before any publication decision.
- [ ] Prune obsolete branches only after history-scan findings are classified.
- [ ] Re-scan the final publication tree/history after cleanup.

## Ownership and repository documentation

- [x] Copyright holder identified as Ernesto Enrique Amaya.
- [x] Restrictive source-available `LICENSE` added; no open-source license is granted by default.
- [x] `COPYRIGHT.md` added.
- [x] `SECURITY.md` added.
- [x] `CONTRIBUTING.md` added; unsolicited external contributions are not accepted during Alpha.
- [x] `CODEOWNERS` added.
- [x] Third-party notices added.
- [x] Pre-publication security audit added and updated for the private Alpha channel.

## Source-repository CI and supply-chain controls

- [x] Unit tests remain mandatory before debug build.
- [x] Pull-request CI does not receive repository secrets.
- [x] GitHub Actions use immutable commit SHAs.
- [x] GitHub token permissions are least-privilege.
- [x] Superseded CI runs are cancelled through concurrency control.
- [x] Canonical source-repository CI passes tests/build for the current baseline.
- [x] Secret-bearing Alpha artifact generation has been removed from this source repository.
- [x] The source repository retains ordinary `ci.yml` only for Android test/build validation.

## Private Alpha signing and Admin access

- [x] Ordinary/source-repository builds receive an empty `ALPHA_ADMIN_PIN` when no value is supplied.
- [x] Canonical Alpha signing material is stored only through private build-channel secrets, not in Git.
- [x] Private Alpha Admin configuration is supplied only through the private build channel.
- [x] Private Alpha workflow requires an explicit 40-hex source SHA and verifies canonical-main ancestry before accessing Alpha secrets.
- [x] Validation and signed-build jobs are separated so source provenance/tests run before signing/PIN/service secrets are used.
- [x] Private signed APK verification includes signer/certificate, package/applicationId, version metadata and SHA-256 evidence.
- [x] First controlled private Alpha artifact from source SHA `47a762914b24a10f7d678a3ad2b59b3888ec7c05` completed successfully in run `37397037436` with artifact ID `11384111656`.
- [ ] Complete the required physical-device install/reinstall validation for the current canonical Alpha signature/build.

## Remaining security blockers before public visibility

- [ ] Complete the all-ref/all-object history scanner and resolve any blocking findings.
- [ ] Ensure older Alpha artifacts created in this source repository have expired or are explicitly removed before publication.
- [ ] Confirm final public history contains no backend source, active secrets or signing material.
- [ ] Configure the intended protected-main ruleset / required CI checks.
- [ ] Enable secret scanning and push protection where available.
- [ ] Enable code scanning / CodeQL where appropriate.
- [ ] Enable private vulnerability reporting where available.
- [ ] Review Dependabot/security-alert settings.
- [ ] Review dependency/license notices against resolved dependencies.

## Bootstrap credential after source publication

- [ ] After this source repository is public, switch the private Alpha checkout to public-source access without the temporary read credential.
- [ ] Validate the private Alpha workflow again without that credential.
- [ ] Remove the temporary source-read repository secret from the private Alpha channel.
- [ ] Revoke the temporary fine-grained source-read token and verify it is unusable.

## Publication

- [ ] Owner performs final review of README, copyright and trademark presentation.
- [x] Keep `punto25-backend` private.
- [x] Keep `mandados-android` private as historical archive.
- [x] Keep the dedicated Alpha build/distribution channel private.
- [ ] Change only `punto25-android` from Private to Public after every blocker above is closed.
