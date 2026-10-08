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

The prior pre-publication dependency review used GitHub's automatic Gradle dependency submission against canonical `main`. That resolved snapshot contained 432 coordinates across the project's Gradle configurations. For app-distribution review, the declared runtime roots and their transitive dependency edges were evaluated conservatively; that closure contained 175 resolved coordinates across 53 Maven group IDs. Those counts describe the reviewed pre-migration snapshot; MAPS-OSS-MIGRATION replaces the map runtime roots and is validated separately by Gradle/CI dependency analysis. Build-tooling and test-only dependencies are not treated as application runtime code.

The application runtime families include AndroidX/Jetpack/Compose, Kotlin and kotlinx libraries, MapLibre Compose/MapLibre Native, Google Play services, Firebase Authentication, Android Credentials/Google Identity, Play Integrity/reCAPTCHA transitives, Okio, Guava/ListenableFuture, Error Prone annotations, JSpecify and `javax.inject`.

Authoritative project/license metadata reviewed for these families establishes the following relevant categories:

- AndroidX/Jetpack/Compose libraries: Apache License 2.0.
- Kotlin and kotlinx libraries: Apache License 2.0 for the applicable runtime libraries.
- MapLibre Compose: BSD 3-Clause License. Project: https://github.com/maplibre/maplibre-compose
- MapLibre Native: BSD 2-Clause License. Project/license: https://github.com/maplibre/maplibre-native
- OpenFreeMap public tile/style service: free public service with no API key; the OpenFreeMap project is MIT-licensed. Service/project: https://openfreemap.org/ and https://github.com/hyperknot/openfreemap
- OpenFreeMap Liberty style: derived from OpenMapTiles/OSM Liberty; style code is BSD 3-Clause and visual design is CC BY 4.0 as documented by OpenFreeMap. License details: https://github.com/hyperknot/openfreemap/blob/main/LICENSE.md
- OpenStreetMap data: © OpenStreetMap contributors, licensed under the Open Data Commons Open Database License (ODbL). Copyright/license: https://www.openstreetmap.org/copyright
- OpenMapTiles attribution applies to the Liberty/OpenFreeMap map style. OpenFreeMap documents the interactive-map attribution as `OpenFreeMap © OpenMapTiles Data from OpenStreetMap`; MapLibre's attribution control reads the style/source attribution and must remain visible or accessible in the map UI.
- Okio, Guava/ListenableFuture, Error Prone annotations, JSpecify and `javax.inject`: permissive Apache License 2.0 family licensing for the resolved runtime components reviewed.
- Google-distributed Android SDK artifacts still used by Punto25, including applicable Google Play services Location, Identity, Firebase, reCAPTCHA and Play/Integrity components, remain subject to their published Android SDK / Google API / product-specific terms as applicable. These terms are separate from the Punto25 source license.

No resolved software runtime dependency was identified that requires reciprocal relicensing of original Punto25 source code. This conclusion does not remove the obligation to preserve third-party copyright/license notices and service-specific terms when distributing binaries.

The CC BY-SA photograph above is the material share-alike media item in the repository and is intentionally licensed separately from original Punto25 source and assets.

## Binary-distribution note

This repository does not vendor the Maven dependency binaries. A future distributed APK/AAB must continue to satisfy applicable third-party notice/license requirements for the libraries actually packaged in that release, including MapLibre/OpenFreeMap/OpenMapTiles/OpenStreetMap attribution and the terms for any enabled Google APIs that remain in use. That release-packaging review is distinct from making this source repository publicly viewable.

## Trademarks

Google, Android, Firebase, WhatsApp, Meta and other third-party names/trademarks belong to their respective owners. Their mention does not imply endorsement or ownership by Punto25.
