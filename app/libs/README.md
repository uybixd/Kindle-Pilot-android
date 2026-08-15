# Xiaomi wearable SDK

`xms-wearable-lib_1.4_release.aar` is Xiaomi's wearable companion SDK v1.4.
It is required for the optional Xiaomi Band integration but is intentionally
excluded from this repository and is not covered by KPilot's MIT License.

Download Xiaomi's official
[`interconnect_dev_test_demo.zip`](https://cdn.cnbj3-fusion.fds.api.mi-img.com/quickapp-vela/interconnect_dev_test_demo.zip),
locate `xms-wearable-lib_1.4_release.aar` in the extracted demo, and copy it to:

```text
app/libs/xms-wearable-lib_1.4_release.aar
```

Expected SHA-256:

```text
9c40fd1c5409bb948523d503af71e2978ae522c35636afe7d64f474c0f6bc195
```

The AAR contributes Android package visibility queries for `com.xiaomi.wearable` and `com.mi.health` through manifest merging.

Before publishing an APK containing this SDK, review Xiaomi's applicable terms
and confirm that they permit binary redistribution. See the repository's
[`THIRD_PARTY_NOTICES.md`](../../THIRD_PARTY_NOTICES.md).
