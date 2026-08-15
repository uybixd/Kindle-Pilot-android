package com.hongxiang.kpilot.i18n

import org.junit.Assert.assertEquals
import org.junit.Test

class AppTextTest {
    @Test
    fun nonChineseSystemLanguagesUseEnglish() {
        assertEquals("Settings", localizeAppText("设置", "en"))
        assertEquals("Settings", localizeAppText("设置", "fr"))
        assertEquals("3 files ready to send", localizeAppText("3 个文件等待发送", "en"))
        assertEquals("Portrait Next page", localizeAppText("竖屏下一页", "en"))
    }

    @Test
    fun chineseSystemLanguageUsesChineseIncludingEnglishDiagnostics() {
        assertEquals("设置", localizeAppText("设置", "zh"))
        assertEquals(
            "SSH 身份验证失败，请检查设置中的 Kindle 用户名和密码。",
            localizeAppText(
                "SSH authentication failed. Check the Kindle username and password in Settings.",
                "zh"
            )
        )
        assertEquals("已连接到 192.168.1.2:22", localizeAppText("Connected to 192.168.1.2:22", "zh"))
    }

    @Test
    fun unknownUserContentIsNeverModified() {
        assertEquals("三体", localizeAppText("三体", "en"))
        assertEquals("The Left Hand of Darkness", localizeAppText("The Left Hand of Darkness", "zh"))
    }
}
