package com.edgeflow.ui.i18n

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UiTranslationsTest {
    @Test
    fun `actual Android 16 device audit strings are localized`() {
        assertEquals("运行中", UiTranslations.chinese("running"))
        assertEquals("重新检查状态", UiTranslations.chinese("Recheck status"))
        assertEquals(
            "EdgeFlow 在后台运行时仍可显示动作悬浮窗口。",
            UiTranslations.chinese("Keeps the action windows available while EdgeFlow is in the background."),
        )
        assertEquals(
            "正在被手势、面板或已保存动作引用的小组件会受到保护，未使用的小组件可移除。",
            UiTranslations.chinese("Bindings still used by any gesture, panel, or saved action are protected. Unused widgets can be removed."),
        )
        assertEquals(
            "短信触发器需要单独授予 Android 通知读取权限。",
            UiTranslations.chinese("Android requires explicit special access for the SMS trigger."),
        )
        assertEquals("手势与按键", UiTranslations.chinese("Gestures & keys"))
        assertEquals("游戏模式", UiTranslations.chinese("Game Mode"))
        assertEquals("备份与高级设置", UiTranslations.chinese("Backup & advanced"))
        assertEquals("忽略左侧手势", UiTranslations.chinese("Ignore left edge"))
        assertEquals("3 个点 · 123 毫秒 · 使用中 · 已保护",
            UiTranslations.chinese("3 points · 123 ms · In use · protected"))
    }
    @Test
    fun `all primary beta navigation items have Chinese names`() {
        val entries = listOf("Home", "Automations", "Floating", "Settings")
        assertEquals(listOf("首页", "自动化", "悬浮栏", "设置"), entries.map(UiTranslations::chinese))
    }

    @Test
    fun `automation and quick bar labels are localized`() {
        assertEquals("自动化运行历史", UiTranslations.chinese("Automation history"))
        assertEquals("试运行", UiTranslations.chinese("Test run"))
        assertEquals("悬浮自动化快捷栏", UiTranslations.chinese("Floating automation shortcuts"))
        assertEquals("手柄大小：120%", UiTranslations.chinese("Handle size: 120%"))
        assertEquals("透明度：80%", UiTranslations.chinese("Opacity: 80%"))
        assertEquals("已启用磁贴：2 个", UiTranslations.chinese("Active tiles: 2"))
        assertEquals("磁贴 1 · 尚未配置", UiTranslations.chinese("Tile 1 · Not configured"))
    }

    @Test
    fun `technical status shows Chinese instead of only translating the heading`() {
        val input = "Hook: ready · Native: not connected · Filter: off"
        assertEquals("Hook：就绪 · 原生输入：未连接 · 输入过滤：关闭", UiTranslations.chinese(input))
        assertEquals("最近自动化：执行失败", UiTranslations.chinese("Last automation: FAILED"))
        assertEquals(
            "12 次输入事件 · 3 次识别手势 · 点击重新检查以刷新",
            UiTranslations.chinese("12 input events · 3 matched gestures · tap Recheck to refresh"),
        )
        assertTrue(UiTranslations.chinese("3 attempts · 2 accepted · 1 rejected. Accepted only means Android received the event, not that the target acted.").contains("已接受 2 次"))
    }

    @Test
    fun `action catalog names and parametric descriptions are localized`() {
        assertEquals("注入手势", UiTranslations.chinese("Inject gesture"))
        assertEquals("重复执行", UiTranslations.chinese("Repeat"))
        assertEquals("条件分支", UiTranslations.chinese("Conditional"))
        assertEquals("延迟 500 毫秒", UiTranslations.chinese("Delay 500 ms"))
        assertEquals("点击坐标（120，380）", UiTranslations.chinese("Tap (120, 380)"))
        assertEquals("条件成立时：返回", UiTranslations.chinese("Then: Back"))
    }

    @Test
    fun `user generated text must stay unchanged`() {
        val userTaskName = "My client Home automation"
        val userText = "客户报价不是 UI 翻译内容"
        assertEquals(userTaskName, UiTranslations.chinese(userTaskName))
        assertEquals(userText, UiTranslations.chinese(userText))
        assertEquals("hello world", UiTranslations.chinese("hello world"))
    }
}
