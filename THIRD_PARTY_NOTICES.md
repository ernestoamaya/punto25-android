# Third-party notices

Punto25 uses third-party software, services and media assets. Those components remain subject to their own licenses, copyright notices and terms; the Punto25 source-available notice does not replace them.

## Media assets

### Iglesia Nuestra Señora del Rosario — Buenos Aires

The application includes a resized copy of the photograph **“Iglesia Nuestra Señora del Rosario- Buenos Aires”** by **Jrivell**, originally published on Wikimedia Commons.

- Source: https://commons.wikimedia.org/wiki/File:Iglesia_Nuestra_Se%C3%B1ora_del_Rosario-_Buenos_Aires.JPG
- Author: Jrivell
- License: Creative Commons Attribution-ShareAlike 3.0 Unported (CC BY-SA 3.0)
- License text: https://creativecommons.org/licenses/by-sa/3.0/
- Local asset: `app/src/main/res/drawable-nodpi/punto25_login_church.jpg`
- Modification: resized copy used for the Punto25 Android interface.

The local copy of this photograph remains distributed under CC BY-SA 3.0. The attribution above identifies the author and source, links the license and identifies the local modification. Any adaptation of the photograph remains subject to the applicable CC BY-SA share-alike terms. Punto25's repository `LICENSE` does not replace or restrict the rights granted for this photograph by CC BY-SA 3.0.

## Software dependency review

The pre-publication dependency review used GitHub's automatic Gradle dependency submission against canonical `main`. The resolved snapshot contained 432 coordinates across the project's Gradle configurations. For app-distribution review, the declared runtime roots and their transitive dependency edges were evaluated conservatively; that closure contained 175 resolved coordinates across 53 Maven group IDs. Build-tooling and test-only dependencies were not treated as application runtime code.

The resolved runtime families include AndroidX/Jetpack/Compose, Kotlin and kotlinx libraries, Google Maps Compose/Maps KTX, Google Play services, Firebase Authentication, Android Credentials/Google Identity, Play Integrity/reCAPTCHA transitives, Okio, Guava/ListenableFuture, Error Prone annotations, JSpecify and `javax.inject`.

Authoritative project/license metadata reviewed for these families establishes the following relevant categories:

- AndroidX/Jetpack/Compose libraries: Apache License 2.0.
- Kotlin and kotlinx libraries: Apache License 2.0 for the applicable runtime libraries.
- Google Maps Compose / Maps KTX: Apache License 2.0 for the open-source libraries; use of Google Maps Platform services remains subject to the applicable Google Maps Platform terms.
- Okio, Guava/ListenableFuture, Error Prone annotations, JSpecify and `javax.inject`: permissive Apache License 2.0 family licensing for the resolved runtime components reviewed.
- Google-distributed Android SDK artifacts, including applicable Google Play services, Identity, Firebase, reCAPTCHA and Play/Integrity components, remain subject to their published Android SDK / Google API / product-specific terms as applicable. These terms are separate from the Punto25 source license.

No resolved software runtime dependency was identified that requires reciprocal relicensing of original Punto25 source code. This conclusion does not remove the obligation to preserve third-party copyright/license notices and any product-specific Google terms when distributing binaries.

The CC BY-SA photograph above is the material share-alike item in the repository and is intentionally licensed separately from original Punto25 source and assets.

## Binary-distribution note

This repository does not vendor the Maven dependency binaries. A future distributed APK/AAB must continue to satisfy applicable third-party notice/license requirements for the libraries actually packaged in that release and the service terms for any enabled Google APIs. That release-packaging review is distinct from making this source repository publicly viewable.

## Trademarks

Google, Android, Firebase, WhatsApp, Meta and other third-party names/trademarks belong to their respective owners. Their mention does not imply endorsement or ownership by Punto25.
