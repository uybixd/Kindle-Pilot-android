# KPilot Android

[English](README.md) | [简体中文](README.zh-CN.md)

KPilot is an Android companion app for jailbroken Kindle devices. It connects to Kindle over SSH and provides:

- Book transfer for EPUB, PDF, MOBI, AZW3, and TXT files
- Downloading and browsing `My Clippings.txt`
- A cached Kindle library with covers, reading progress, search, and per-book clippings
- Nine-card clipping review batches, favorites, exclusions, undo, and saved progress
- Offline note editing and writeback to existing AZW3, MOBI, and KFX highlights
- Downloading and browsing Kindle vocabulary
- Remote page turning from the phone, hardware keys, or a Xiaomi wearable
- Local encrypted storage for Kindle SSH connection settings
- Configurable bottom navigation tabs

The Kindle must have UsbNet or UsbNetLite installed, with SSH enabled, and must be reachable from the Android device.

## Using the new features

Refresh Library to cache the book list, available covers, and reading progress. Open a book to view its clippings; sync those from Clippings first. When Kindle's catalog database is unavailable, the library falls back to filenames. The Library send button opens the existing book-transfer queue.

Open Review from Clippings for batches of up to nine cards. Less-reviewed content comes first, with books interleaved. Swipe left to favorite or right to exclude, or use the buttons. History, daily counts, and progress are saved locally.

Open an existing highlight, edit its note, save locally, then send to Kindle. Return to the Kindle home screen before sending. Save and send an empty note to delete the annotation while keeping its highlight. A successful send retains the local copy; restoring the last synced note only discards that local copy. Creating new highlights is not supported.

Writeback verifies the target highlight, updates its reader sidecar and `My Clippings.txt`, and keeps `.kpilot-note-backup-*.bak` files beside the originals. Failed verification triggers recovery. Recovery refuses to overwrite changes made by another writer and reports the retained backups. Ambiguous matches, unsupported formats, and unknown reader states stop the write and retain the local draft.

Library, Clippings, Vocabulary, Remote, and Settings are visible by default. Choose up to five tabs in Settings; Clippings and Settings stay visible. Review, Send, and hidden features remain accessible from Settings. Library data, clippings, note drafts, and review history are scoped by host, port, and username. Changing the address selects a different local namespace.

## Build

Open the project in Android Studio and build the `app` module.

The Gradle toolchain uses Java 25. Run `./gradlew testDebugUnitTest assembleDebug lintDebug` for local checks. Unit tests cover offline persistence, review ordering, annotation matching, two-file writes, and failure recovery. Kindle firmware and SSH behavior still need device verification.

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
