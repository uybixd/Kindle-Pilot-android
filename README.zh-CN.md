# KPilot Android

[English](README.md) | [简体中文](README.zh-CN.md)

KPilot 是一款用于已越狱 Kindle 的 Android 辅助应用，通过 SSH 连接 Kindle，主要功能包括：

- 传输 EPUB、PDF、MOBI、AZW3 和 TXT 文件
- 下载并查看 `My Clippings.txt` 摘抄
- 浏览 Kindle 书库、封面和阅读进度，离线搜索并查看单本书摘抄
- 每组 9 条笔记复习，支持收藏、不再复习、撤销及进度续接
- 离线编辑批注，并反向写入 Kindle 的 AZW3、MOBI、KFX 已有标注
- 下载并查看 Kindle 生词本
- 通过手机、硬件按键或小米穿戴设备遥控翻页
- 在 Android 本地加密保存 Kindle SSH 连接设置
- 在设置中自选底部导航入口

Kindle 需要安装 UsbNet 或 UsbNetLite、开启 SSH，并且能够被 Android 设备访问。

## 新功能使用

书库页刷新后会缓存书籍列表、可读取的封面和阅读进度。点按书籍查看本书摘抄；摘抄需要先在“摘抄”页同步。Kindle 目录数据库不可用时，书库会使用文件名显示书籍。书库页的发送按钮可进入原有传书队列。

在“摘抄”页点按“笔记复习”。每组最多 9 条，优先展示复习较少的内容并交错书籍。左滑收藏，右滑不再复习，也可使用按钮；复习记录、今日计数和当前进度会本地保存。

在标注详情中“编辑批注”，先“保存到本机”，再“发送到 Kindle”。发送前请退出 Kindle 阅读器并回到主页。清空批注后保存并发送，可删除批注且保留划线。本地副本在发送后继续保留；“恢复同步时的 Kindle 批注”仅移除本地副本。当前支持给已有标注添加、编辑或清空批注，不支持创建新的划线。

反向写入会校验目标标注、修改书籍 sidecar 和 `My Clippings.txt`，将原文件备份为同目录的 `.kpilot-note-backup-*.bak`。校验失败时尝试恢复；如文件已被其他程序修改，会保留外部修改和备份，并报告恢复失败。匹配不明确、格式不支持或阅读器状态不可确认时停止写入，保留本地草稿。

底部默认显示书库、摘抄、生词、遥控和设置。在“设置 → 底部导航栏”可替换入口，最多 5 个；摘抄和设置固定保留。复习、传书及隐藏入口仍可从设置打开。书库、摘抄缓存、批注草稿和复习记录按主机、端口、用户名隔离；更换地址会使用新的本地数据空间。

## 构建

使用 Android Studio 打开项目并构建 `app` 模块。

项目使用 Java 25（由 Gradle toolchain 配置）。本地检查：`./gradlew testDebugUnitTest assembleDebug lintDebug`。单元测试覆盖离线持久化、复习规则、写入匹配、双文件事务及失败恢复；真实 Kindle 的固件与 SSH 行为仍需实机验证。

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

KPilot 源代码采用 [GNU General Public License v3.0](LICENSE)（`GPL-3.0-only`）。第三方软件和素材仍适用各自的条款，详情请参阅 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
