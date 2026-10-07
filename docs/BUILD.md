# Building DiPlay

Requirements: JDK 25, Android SDK 37, NDK 28.2.13676358 and the included Gradle wrapper.

## Source and CI builds

```sh
./gradlew :shared:testDebugUnitTest :common:testDebugUnitTest :mobile:lintDebug :mobile:assembleDebug
```

The resulting source-only APK contains no accessory identity. Standalone CarPlay requires runtime authentication provisioning. Tests generate synthetic identities at runtime; no test private-key files are tracked.

## Local release packaging

Provide an external asset directory using `DIPLAY_AUTH_ASSETS_DIR`. The directory must contain exactly the intended runtime files under `offline-mfi/identity.pk8` and `offline-mfi/certificate.p7b`. Neither file belongs in Git. The build permits those two files only when this explicit input is set and rejects unexpected credential containers elsewhere in APK assets.

Set `ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD` locally for your Android signing key. Never commit these values or the keystore. Different signing keys cannot update an existing project-signed installation.

```sh
./gradlew :shared:testDebugUnitTest :common:testDebugUnitTest :mobile:lintRelease :mobile:assembleRelease
```

Output: `mobile/build/outputs/apk/release/mobile-release.apk`. The release APK deliberately contains the experimental identity described in the notices; it is extractable by recipients. The separate Android signing key is not included. The retired build-beta.py helper is not used; this Gradle workflow uses explicit environment inputs.

The public release source archive corresponds to the tagged source and excludes runtime identities, signing keys, local configuration and build output.

## Standalone car-test APK

Use `:mobile:assembleStandaloneDebug` for a test APK that must connect to an iPhone:

```sh
DIPLAY_AUTH_ASSETS_DIR=/absolute/path/to/runtime-assets ./gradlew :mobile:assembleStandaloneDebug
```

This task refuses missing or empty runtime inputs. `assembleDebug` remains an identity-free
source/CI build when the explicit asset input is absent; do not install that output as a
standalone car-test package. Before delivery, verify both `assets/offline-mfi/identity.pk8`
and `assets/offline-mfi/certificate.p7b` in the APK against the selected local inputs.
Update the existing test app without uninstalling it to preserve its settings.

For the selected official 0.2.10 release, the local preparation helper verifies the
entire APK before writing only the two runtime assets to the ignored private tree:

```sh
python3 scripts/prepare_h6_auth.py \
  --apk /absolute/path/to/DiPlay-0.2.10.apk \
  --sha256 8c555ce179f30a30b659914f70355245a594f82957424bf44fc532fb1409441e \
  --output .private/h6-car-test/2026-10-04/runtime-assets
DIPLAY_AUTH_ASSETS_DIR="$PWD/.private/h6-car-test/2026-10-04/runtime-assets" \
  ./gradlew --no-configuration-cache :mobile:assembleStandaloneDebug
```

The helper requires an explicitly downloaded and selected release APK; it does not
fetch identities during source builds. Its receipt stays beside the asset root and
is not packaged. Local key/certificate verification does not prove iPhone acceptance.
The completed H6 package and device-side VPN/test steps are recorded in
[the 2026-10-04 car-test handoff](android_6/CAR_TEST_2026-10-04.md).
