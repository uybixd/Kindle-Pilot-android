# KPilot Android

[English](README.md) | [简体中文](README.zh-CN.md)

KPilot is an Android companion app for jailbroken Kindle devices. It connects to Kindle over SSH and provides:

- Book transfer for EPUB, PDF, MOBI, AZW3, and TXT files
- Downloading and browsing `My Clippings.txt`
- Downloading and browsing Kindle vocabulary
- Remote page turning from the phone, hardware keys, or a Xiaomi wearable
- Local encrypted storage for Kindle SSH connection settings

The Kindle must have UsbNet or UsbNetLite installed, with SSH enabled, and must be reachable from the Android device.

## Build

Open the project in Android Studio and build the `app` module.

The Xiaomi wearable integration depends on Xiaomi's closed-source wearable interconnection SDK. The SDK is not included in this repository. Obtain it yourself and copy the following file into the project before building:

```text
app/libs/xms-wearable-lib_1.4_release.aar
```

The expected SHA-256 is:

```text
9c40fd1c5409bb948523d503af71e2978ae522c35636afe7d64f474c0f6bc195
```

Review Xiaomi's applicable SDK terms before distributing an APK containing the SDK.

## Local support images

The following personal QR-code images are intentionally not included in the repository:

```text
Donation_QR_Code/mmexport1786535949312.jpeg
Donation_QR_Code/mm_reward_qrcode_1786536180927.png
```

If you keep the support page, replace them with your own images. Otherwise, remove or modify the related support-page entries and asset references before distributing your build.

## License

KPilot source code is licensed under the [GNU General Public License v3.0](LICENSE) (`GPL-3.0-only`). Third-party software and assets remain subject to their own terms; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
