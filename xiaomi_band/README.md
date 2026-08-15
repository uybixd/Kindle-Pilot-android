# KPilot for Xiaomi Band

This is the smallest useful Xiaomi Vela companion for KPilot. It sends semantic `previous` and `next` commands to the paired Android app through `system.interconnect`; it does not emulate phone volume keys.

The remote screen presents an 18-bead loop: one gold bead and 17 brown beads. A vertical swipe immediately sends the page command and optional haptic feedback, while native compositor animations are queued independently so rapid gestures are never discarded. Separate forward and reverse strip layers keep both directions smooth without runtime image decoding. The wearable intentionally does not wait for Kindle execution confirmation because the phone cannot observe the Kindle screen.

The remote keeps the display awake while the page is visible by default. Swipe left to open settings if you want to disable screen wake-lock or page-turn vibration. The wake-lock is released whenever the page is hidden or destroyed.

## Develop

1. Open this directory as a Vela project in AIoT-IDE.
2. Install dependencies with `npm install` or the IDE development guide.
3. Create a watch simulator and run `npm start`, or use the IDE **Debug** action.
4. Use a real wearable for interconnect testing. The simulator-to-phone path is not representative of the signed production channel.

The message protocol is documented in [`PROTOCOL.md`](PROTOCOL.md).

## Signing and package identity

The Vela manifest package is `com.hongxiang.kpilot`, matching the Android `applicationId`. Keep both values synchronized because Xiaomi interconnect uses this identity to pair the wearable and Android apps.

Xiaomi interconnect also requires the Vela RPK and Android APK to use the same signing identity. Do not commit private keys. Export the correct Android key locally:

```sh
./scripts/export-signing-key.sh debug /path/to/debug.keystore androiddebugkey
./scripts/export-signing-key.sh release /path/to/release.jks your-key-alias
```

The script writes `private.pem` and `certificate.pem` beneath `sign/debug` or `sign/release`; those files are ignored by Git. Package the matching Android and Vela variants together.

For Android release builds, copy `../keystore.properties.example` to `../keystore.properties` and point it at the same JKS passed to the export script. The Android Gradle build reads this ignored local file and signs release APKs with that key.

## Build

```sh
npm install
npm run generate:beads
npm run build
npm run release
```

`npm run release` requires matching PEM files in `sign/release`.
