# KPilot Android

[English](README.md) | [简体中文](README.zh-CN.md)

KPilot 是一款用于已越狱 Kindle 的 Android 辅助应用，通过 SSH 连接 Kindle，主要功能包括：

- 传输 EPUB、PDF、MOBI、AZW3 和 TXT 文件
- 下载并查看 `My Clippings.txt` 摘抄
- 下载并查看 Kindle 生词本
- 通过手机、硬件按键或小米穿戴设备遥控翻页
- 在 Android 本地加密保存 Kindle SSH 连接设置

Kindle 需要安装 UsbNet 或 UsbNetLite、开启 SSH，并且能够被 Android 设备访问。

## 构建

使用 Android Studio 打开项目并构建 `app` 模块。

小米穿戴设备互联功能依赖小米闭源的穿戴设备互联 SDK。该 SDK 不包含在本仓库中，构建前需要自行获取，并将以下文件放入项目：

```text
app/libs/xms-wearable-lib_1.4_release.aar
```

文件的预期 SHA-256 为：

```text
9c40fd1c5409bb948523d503af71e2978ae522c35636afe7d64f474c0f6bc195
```

如果需要分发包含该 SDK 的 APK，请先确认并遵守小米适用的 SDK 条款。

## 本地支持页面图片

以下两张个人二维码图片不会包含在本仓库中：

```text
Donation_QR_Code/mmexport1786535949312.jpeg
Donation_QR_Code/mm_reward_qrcode_1786536180927.png
```

如果保留应用内的支持页面，请自行替换为自己的图片；如果不需要该页面，请在分发前删除或修改相应入口和资源引用。

## 许可证

KPilot 源代码采用 [MIT License](LICENSE)。第三方软件和素材仍适用各自的条款，详情请参阅 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
