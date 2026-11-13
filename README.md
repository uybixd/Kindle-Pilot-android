# KPilot Android

[English](README.md) | [简体中文](README.zh-CN.md)

KPilot is an Android companion app for jailbroken Kindle devices. It connects to Kindle over SSH and provides:

- Book transfer for EPUB, PDF, MOBI, AZW3, and TXT files
- Downloading and browsing `My Clippings.txt`
- A cached Kindle library with covers, reading progress, search, and per-book clippings
- Random review of past clippings with favorites, hiding, and a daily review count
- Vocabulary review with iOS-matched learning steps and SM-2-style intervals, plus local definitions
- A terminal-style daily favorite home screen widget using the Terminus console font
- Offline note editing and writeback to existing AZW3, MOBI, and KFX highlights
- Downloading and browsing Kindle vocabulary
- Remote page turning from the phone, hardware keys, or a Xiaomi wearable
- Local encrypted storage for Kindle SSH connection settings
- Icon-only navigation with optional Vocabulary and Remote tabs

The Kindle must have UsbNet or UsbNetLite installed, with SSH enabled, and must be reachable from the Android device.

## Using the new features

Pull down to refresh Books, Clippings, Vocabulary, and Remote. The bookshelf uses three columns, with book import and transfer status in its toolbar. Open a book to browse its clippings. Transfers are written to `/mnt/us/documents/Downloads/Items01` (created automatically when needed).

Clippings and Vocabulary each have a “Review” entry. Clipping review favors less-reviewed entries and interleaves different books, with favorite, hide, next, a three-second undo, and a daily count. Vocabulary review uses the same learning steps and SM-2-style intervals as iOS; rating a card advances the queue, and learning cards reappear when due. Definitions can be added from the word details, are searchable, and appear on the review answer side.

Settings → Home screen widget previews the daily favorite card in Default, Terminal green, or Terminal amber. The terminal styles use Terminus for Latin text. Both styles and the default card use Zhuque Fangsong for Chinese text. Long-press the home screen to add the KPilot “Daily favorite” widget. It switches to another favorite clipping at local midnight. Tap the arrow to switch immediately. Favorites are mirrored into an app-private file so the widget can work offline.

Review data can be shared between phones through the Kindle itself, with no account or cloud service. On connection and pull-to-refresh the app merges its local data with `/mnt/us/kpilot/learning/state.json` under a Kindle-side lock, then atomically replaces the file and verifies the read-back. Favorites and hidden flags merge field by field (latest update wins); vocabulary cards and definitions use per-record latest-write wins with the phone ID as a tiebreaker; review counts keep the larger value. Daily review counts remain local to each phone. The file does not contain SSH credentials. Cross-phone sharing currently applies to the Android app; the iOS app does not yet read or write this file.

Settings groups Kindle Connection, Bottom Navigation, Local Data, About, and Support the Author. Connection supports multiple Kindle profiles. Local Data provides file import, library backup export, cache information, and local-data clearing. Passwords are encrypted with Android Keystore.

Clippings, Books, and Settings stay visible. Only Vocabulary and Remote can be hidden. Remote keeps page-turn buttons, volume-key control, and the Xiaomi band option.

Edit an existing highlight's note directly; changes are saved locally. Send the saved note to the current Kindle after returning to its home screen. Sending a cleared note deletes the annotation and keeps the highlight.

Writeback verifies the target highlight, updates its reader sidecar and `My Clippings.txt`, and keeps `.kpilot-note-backup-*.bak` files beside the originals. Failed verification triggers recovery. Recovery refuses to overwrite changes made by another writer and reports the retained backups. Ambiguous matches, unsupported formats, and unknown reader states stop the write and retain the local draft.

Library data, clippings, vocabulary, and note drafts are scoped by the stable Kindle identity. On the first connection the app creates or reads `/mnt/us/.kpilot/device-id`; changing the Kindle's IP or connecting from another phone still resolves to the same data namespace. Caches created under the old host/port scope are migrated automatically on first identification. Each phone keeps its own local replica under that scope, and the Kindle-hosted review file is the shared copy used to reconcile phones. A sync failure keeps the local data unchanged and reports the error.

Long-press an AZW / AZW3 / MOBI / PRC book on the bookshelf and choose “Repair Kindle cover”. The app extracts the embedded cover, generates a Kindle shelf thumbnail, replaces the previous image directly, and verifies the write (no backup of the previous image is kept); the book text, notes, reading progress, `cc.db`, and `.sdr` files are not changed. Uploading AZW3 / MOBI also adds the cover automatically; EPUB / PDF are not handled automatically. Kindle may overwrite the cover after connecting to the network, in which case the repair can be repeated.

Long-pressing any book also offers “Delete from Kindle”. Deleting only the book keeps its notes and reading progress; deleting the book and reading data also removes its `.sdr` directory. A second confirmation is required, and only supported book paths under `/mnt/us/documents` are accepted.

## Build

Open the project in Android Studio and build the `app` module.

The Gradle toolchain uses Java 25. Run `./gradlew testDebugUnitTest assembleDebug lintDebug` for local checks. Unit tests cover offline persistence, navigation rules, annotation matching, two-file writes, failure recovery, review-data merging, and the daily favorite rotation. Kindle firmware and SSH behavior still need device verification. `connectedDebugAndroidTest` installs on a device and normally uninstalls the APKs afterwards; `gradle.properties` keeps them installed so running widget tests does not erase local app data.

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
