# Punto25 publication checklist

Canonical public target: `ernestoamaya/punto25-android`.

Historical repository `ernestoamaya/mandados-android` must remain private. Backend repository `ernestoamaya/punto25-backend` must remain private.

## Source and history

- [x] New clean Android repository created privately.
- [x] Physically validated `0.3-alpha3-dev3.6` Android baseline exported through a sanitized Android-only snapshot.
- [x] Backend excluded from the Android import.
- [x] Historical `debug.keystore` and other signing-file extensions excluded from the Android import.
- [ ] Final canonical `main` history rebuilt from clean repository objects so temporary migration workflows/URLs are not part of the public branch history.
- [ ] Final tree re-scanned for secrets and signing material immediately before publication.

## Ownership and repository documentation

- [x] Copyright holder identified as Ernesto Enrique Amaya.
- [x] Restrictive source-available `LICENSE` added; no open-source license is granted by default.
- [x] `COPYRIGHT.md` added.
- [x] `SECURITY.md` added.
- [x] `CONTRIBUTING.md` added; unsolicited external contributions are not accepted during Alpha.
- [x] `CODEOWNERS` added.
- [x] Third-party notices added.
- [x] Pre-publication security audit added.

## CI and supply-chain controls

- [x] Unit tests remain mandatory before debug build.
- [x] Pull-request CI does not receive repository secrets.
- [x] GitHub Actions use immutable commit SHAs.
- [x] GitHub token permissions are least-privilege.
- [x] Superseded CI runs are cancelled through concurrency control.
- [x] APK names are derived from Android `versionName`.
- [x] Signed Alpha artifact workflow is manual/main-only and emits SHA-256.
- [ ] Clean-repository CI passes on the imported baseline.

## Alpha signing and Admin access

- [x] Retired tracked debug signing key excluded from the clean repository.
- [x] Legacy hardcoded Admin PIN removed from public Android source.
- [x] Ordinary/public builds disable local Admin access when `ALPHA_ADMIN_PIN` is absent.
- [ ] Generate a new private Punto25 Alpha keystore outside Git.
- [ ] Configure `ALPHA_KEYSTORE_B64`, `ALPHA_KEYSTORE_PASSWORD`, `ALPHA_KEY_ALIAS`, and `ALPHA_KEY_PASSWORD` as GitHub Secrets.
- [ ] Configure a private 6–8 digit `ALPHA_ADMIN_PIN` GitHub Secret for owner-controlled Alpha builds.
- [ ] Generate the first APK using the rotated Alpha key.
- [ ] Physically install/reinstall and validate the rotated-signature APK and Alpha Admin access.

## Security blockers before public visibility

- [ ] Confirm final public history contains no backend source and no signing key.
- [ ] Configure a protected-main ruleset / required CI checks.
- [ ] Enable secret scanning and push protection where available.
- [ ] Enable code scanning / CodeQL where appropriate.
- [ ] Enable private vulnerability reporting where available.
- [ ] Review Dependabot/security-alert settings.

## Publication

- [ ] Owner performs final review of README, copyright and trademark presentation.
- [ ] Keep `punto25-backend` private.
- [ ] Keep `mandados-android` private as historical archive.
- [ ] Change only `punto25-android` from Private to Public after every blocker above is closed.
