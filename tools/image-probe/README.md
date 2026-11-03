# HY300 Pro: isolated geometry permission probe

This optional Android application tests `android.permission.HARDWARE_TEST` and the HY300 Pro firmware's SurfaceFlinger transaction 1050. It is not included in ordinary launcher builds and does not change the launcher's application ID or signing configuration.

- Package: `io.jostx.hy300launcher.imageprobe`
- Minimum SDK: 30; target SDK: 32, matching the launcher
- No shared system UID, HOME activity, exported component, internet permission, ADB client, root invocation, or OEM application dependency
- Only requested permission: `android.permission.HARDWARE_TEST`

## Build and signing

```sh
./gradlew -PimageProbe=true :imageProbe:assembleRelease
```

The unsigned APK is generated under `tools/image-probe/build/outputs/apk/release/`. Sign outside Gradle with the appropriate platform certificate for the verified firmware. Keep signing material outside the repository; do not commit signing keys or generated APKs. Verify the signed artifact's public certificate before installation. This probe is specific to the inspected firmware, not a general Android application.

The release lint configuration exempts only `ExpiredTargetSdkVersion` because this private device diagnostic preserves the launcher's target SDK and is not distributed on Google Play. Other release lint checks remain enabled.

## Run

After installing the separate signed package, launch it through an authorized root host connection; its activity is deliberately not exported. The activity itself runs as an independent app UID.

Read-only permission check:

```sh
adb shell am start -S -W -n io.jostx.hy300launcher.imageprobe/.ProbeActivity
```

Explicit reversible geometry probe:

```sh
adb shell am start -S -W -n io.jostx.hy300launcher.imageprobe/.ProbeActivity --ez writes true
```

Use a cold start (`-S`) to ensure `onCreate` receives the operation; merely bringing an existing activity forward will not rerun the probe. Do not interrupt a running write probe. Before a write run, save the current image properties externally and ensure automatic correction is inactive.

The write probe requires the saved matrix to agree with all eight corner properties. It persists a recovery record before any transaction, submits the unchanged matrix first, then adjusts the top-left X coordinate by one unit and restores the initial matrix and properties in `finally`. An unfinished recovery record prevents another write run. Recovery information and incremental results are under the probe's private `files/` directory (`pending-restore.txt`, `result.txt`). Retrieve them using authorized host access; they are not exposed through a provider.

`PASS_RESTORED` means the Binder transactions were accepted and saved properties matched after restoration. It does not measure the optical result. A host-side property comparison is performed independently during verification. This package contains no production service and does not replace the launcher's current backend.

See [verification results](../../docs/signed-image-probe-verification.md).
