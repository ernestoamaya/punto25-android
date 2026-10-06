# Punto25 publication checklist

Canonical public target: `ernestoamaya/punto25-android`.

Historical repository `ernestoamaya/mandados-android` must remain private. Backend repository `ernestoamaya/punto25-backend` must remain private. The dedicated Alpha build/distribution channel must remain private.

## PRE-PUBLIC GATES

### Source, history and security

- [x] New clean Android repository created privately.
- [x] Backend excluded from the Android import.
- [x] Historical tracked signing material excluded from the Android import.
- [x] Current Android baseline remains `0.3-alpha3-dev3.9` / versionCode `16` / applicationId `ar.com.mandados.app`.
- [x] All-ref/all-object secret scan completed over the GitHub-exposed/fetchable refs and objects available at scan time.
- [x] Findings classified; no active secret or new blocking Class A finding remained.
- [x] Retired historical Alpha PIN documented as **HISTORICAL / MITIGATED** without retaining it as a current credential.
- [x] Obsolete branches classified conservatively; only branches proven disposable were removed.
- [x] Post-cleanup final scan completed; no new active secrets or signing material found.
- [x] Older Alpha artifacts in the source repository explicitly removed.
- [x] Final audited history confirmed to contain no backend source, active secrets or signing material within the technically available GitHub ref/fetch coverage.
- [x] Source repository retains ordinary `ci.yml` only; secret-bearing Alpha generation is separated into the private Alpha channel.

### Ownership, documentation and third-party material

- [x] Copyright holder identified as Ernesto Enrique Amaya.
- [x] Restrictive source-available `LICENSE` added; no open-source license is granted for original Punto25 material by default.
- [x] `COPYRIGHT.md`, `SECURITY.md`, `CONTRIBUTING.md` and `CODEOWNERS` reviewed for public-repository consistency.
- [x] Third-party notices reviewed against the resolved Gradle dependency graph and the included Wikimedia Commons media asset.
- [x] Dependency/license review found no software license requiring reciprocal relicensing of original Punto25 source.
- [x] CC BY-SA media attribution identifies author, source, license and local modification and preserves the photo's separate license.
- [x] Pre-publication security audit reconciled with PREPUB-SCAN, PREPUB-CLEANUP, PREPUB-FINAL-SCAN and PREPUB-PROTECTION status.

### CI, supply chain and Alpha validation

- [x] Unit tests remain mandatory before debug build.
- [x] Pull-request CI receives no repository secrets.
- [x] GitHub Actions in `ci.yml` use immutable full commit SHAs.
- [x] Repository Actions settings require full-length commit SHAs.
- [x] GitHub token permissions are least-privilege for ordinary CI.
- [x] Superseded CI runs are cancelled through concurrency control.
- [x] Canonical source-repository CI passes tests/build.
- [x] PR #15 removed PR path filters so `test-and-build` executes for every pull request targeting `main`.
- [x] A documentation-only PR in this finalization batch is required to execute `test-and-build`, providing a regression check for required-status readiness.
- [x] Dependency graph enabled.
- [x] Dependabot alerts enabled.
- [x] Dependabot security updates enabled.
- [x] Automatic Gradle dependency submission enabled and successfully submitted a resolved dependency snapshot.
- [x] Existing Dependabot update PRs preserved; no dependency upgrade was merged as part of pre-publication hardening.
- [x] Private Alpha workflow validates source provenance before secret-bearing build steps.
- [x] Private signed APK verification includes signer/certificate, package/applicationId, version metadata and SHA-256 evidence.
- [x] Physical-device Alpha update validation completed successfully with the established Alpha signer.

### Remaining before the visibility change

- [ ] Owner performs final subjective review of README, copyright, trademark/product presentation and public-facing wording.
- [ ] Immediately before changing visibility, verify the exact `main` SHA, repo remains PRIVATE, CI is green, no unexpected concurrent changes exist, and the final documentation diff/tree contains no secret or sensitive value.
- [ ] Confirm the atomic post-public sequence below is ready to execute without billing, upgrade or paid-plan prompts.

## ATOMIC IMMEDIATE POST-PUBLIC GATES

These controls are intentionally not marked complete while the repository is PRIVATE because the required GitHub Free capabilities are not all available in the current private-repository state. A future PRIVATE → PUBLIC change is only complete when the following sequence is executed immediately after the visibility change.

- [ ] Change only `ernestoamaya/punto25-android` from PRIVATE to PUBLIC.
- [ ] Immediately create the intended `main` ruleset/protection.
- [ ] Require Pull Requests for normal changes to `main`.
- [ ] Require the real `test-and-build` status check with 0 mandatory external approvals for the single-owner workflow.
- [ ] Verify the required check executes for all pull requests targeting `main`.
- [ ] Block force pushes to `main`.
- [ ] Block deletion of `main`.
- [ ] Require conversation resolution if available without creating an unsatisfiable owner workflow.
- [ ] Enable and verify secret scanning.
- [ ] Enable and verify push protection.
- [ ] Enable and verify CodeQL/code scanning where available at no cost for the public repository.
- [ ] Enable private vulnerability reporting.
- [ ] Verify Dependency graph, Dependabot alerts, Dependabot security updates and automatic dependency submission remain enabled.
- [ ] Verify repository remains at the exact intended publication SHA after security configuration.
- [ ] Validate the private Alpha workflow using public source access without the temporary source-read bootstrap credential.
- [ ] Remove `SOURCE_REPO_READ_TOKEN` from the private Alpha repository only after that validation succeeds.
- [ ] Revoke the temporary fine-grained source-read PAT and verify it no longer works.
- [ ] Confirm `punto25-backend`, `mandados-android` and the dedicated private Alpha build/distribution repository remain PRIVATE.
- [ ] Confirm no billing, paid upgrade or charge was introduced.

## Publication completion

Publication is not complete merely because the repository becomes visible. It is complete only after every applicable item in **ATOMIC IMMEDIATE POST-PUBLIC GATES** has been verified.

No Play Store publication, production rollout or `DATA COMPATIBILITY FREEZE` is implied by making this source repository public.
