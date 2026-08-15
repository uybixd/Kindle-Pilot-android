# Third-party notices

The MIT License in [`LICENSE`](LICENSE) covers KPilot source code authored for
this repository. It does not replace or modify the licenses of third-party
software, tools, or assets.

## Xiaomi wearable companion SDK

KPilot's optional Xiaomi Band integration requires Xiaomi's
`xms-wearable-lib_1.4_release.aar`. The SDK is not stored in this repository and
is not licensed under KPilot's MIT License.

Obtain the SDK from Xiaomi's official
[interconnect development and testing demo](https://cdn.cnbj3-fusion.fds.api.mi-img.com/quickapp-vela/interconnect_dev_test_demo.zip)
and review Xiaomi's applicable terms before using or redistributing it. KPilot
does not grant any rights to Xiaomi's SDK.

Expected SDK file and checksum:

```text
app/libs/xms-wearable-lib_1.4_release.aar
SHA-256 9c40fd1c5409bb948523d503af71e2978ae522c35636afe7d64f474c0f6bc195
```

In particular, do not assume that KPilot's MIT License authorizes publishing
the SDK in a source repository or distributing it inside an APK. Confirm those
rights under Xiaomi's terms before publishing a binary release.

## Android and JVM dependencies

The Android build uses AndroidX, Jetpack Compose, Kotlin, kotlinx.coroutines,
and Android desugaring libraries under their respective licenses. JSch is
distributed under the Revised BSD license, with bundled JZlib and jBCrypt
components retaining their Revised BSD and ISC notices. Dependency license
files and notices remain the property of their respective copyright holders.

## Xiaomi Vela development dependencies

The `xiaomi_band` project uses npm development dependencies under a mixture of
third-party licenses. Their package versions and declared licenses are recorded
in `xiaomi_band/package-lock.json`. Those packages are not relicensed under
KPilot's MIT License.

## Support assets

Personal support/payment QR-code images are not stored in this repository.
Local builds may provide their own images under `Donation_QR_Code`; those
images are not software and are not covered by KPilot's MIT License.
