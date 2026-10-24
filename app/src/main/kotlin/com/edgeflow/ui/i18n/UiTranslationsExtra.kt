package com.edgeflow.ui.i18n

/** Legacy pages, unlock activity and longer help messages for simplified Chinese. */
internal object UiTranslationsExtra {
    private val labels = mapOf(
        "Target framework" to "目标框架",
        "Verified on" to "已验证系统",
        "This device" to "当前设备",
        "Xposed API" to "Xposed API",
        "Compatibility" to "兼容性",
        "Why system scope" to "为什么需要系统框架作用域",
        "Compatibility notes" to "兼容性说明",
        "Key interception" to "按键拦截",
        "Input filter" to "输入事件过滤",
        "Bar visibility" to "系统栏可见性",
        "Recheck status" to "重新检查状态",
        "Clear search" to "清除搜索",
        "running" to "运行中",
        "initializing" to "初始化中",
        "starting" to "启动中",
        "stopped" to "已停止",
        "failed" to "失败",
        "unknown" to "未知",
        "Keeps the action windows available while EdgeFlow is in the background." to
            "EdgeFlow 在后台运行时仍可显示动作悬浮窗口。",
        "Android requires special access to draw menus over other apps." to
            "需要授予悬浮窗权限，才能在其他应用上方显示菜单。",
        "Bindings still used by any gesture, panel, or saved action are protected. Unused widgets can be removed." to
            "正在被手势、面板或已保存动作引用的小组件会受到保护，未使用的小组件可移除。",
        "Used only to detect messages from the selected default SMS app; notification contents are not stored." to
            "仅用于检测所选默认短信应用的通知，不会储存通知内容。",
        "Android requires explicit special access for the SMS trigger." to
            "短信触发器需要单独授予 Android 通知读取权限。",
        "SMS trigger can observe eligible message notifications." to
            "短信触发器现可读取符合条件的新消息通知。",
        "Notification access granted" to "已授予通知读取权限",
        "General" to "设置分类",
        "Language & appearance" to "语言与显示",
        "Permissions & services" to "权限与系统服务",
        "Gestures & keys" to "手势与按键",
        "Game Mode" to "游戏模式",
        "Backup & advanced" to "备份与高级设置",
        "Choose a section to manage. Existing settings and bindings are preserved." to
            "选择需要管理的分类。现有设置和动作绑定均会保留。",
        "Choose your language and display preferences" to "切换界面语言与显示偏好",
        "Overlay access, notification listener, tiles and Android unlock" to
            "悬浮窗、通知权限、快捷磁贴及 Android 解锁",
        "Edge regions, timing, recorded gestures and input behavior" to
            "边缘区域、手势识别时间、录制手势及按键行为",
        "Game touch passthrough and hardware key interception" to
            "设置游戏触摸放行和实体按键拦截规则",
        "Import, export, recovery, emergency stop and reset" to
            "配置导入导出、恢复、紧急停止和重置",
        "Close floating widget" to "关闭悬浮小组件",
        "No installed launchable apps are available." to "没有可启动的应用。",
        "No launchable apps are available." to "没有可启动的应用。",
        "No custom panels yet." to "暂无自定义面板。",
        "No hosted app widgets" to "暂无已托管小组件",
        "Remove Android widget binding" to "移除 Android 小组件绑定",
        "Widget setup was canceled." to "小组件设置已取消。",
        "The selected widget could not be bound." to "无法绑定所选小组件。",
        "Widget binding was canceled." to "小组件绑定已取消。",
        "Clear selection" to "清除选择",
        "No items yet. Add entries to build a quick-action strip." to "暂无项目。添加条目后即可生成快捷操作栏。",
        "The inner condition must not match." to "内部条件不得成立。",
        "The conditions are evaluated when the action runs." to "将在动作执行时判断条件。",
        "Condition" to "条件",
        "Condition nesting is limited" to "条件嵌套层级已达上限",
        "Rotate screen" to "旋转屏幕",
        "Landscape" to "横屏",
        "Portrait" to "竖屏",
        "Background" to "背景",
        "Widget" to "小组件",
        "Width" to "宽度",
        "Height" to "高度",
        "Position" to "位置",
        "Transparency" to "透明度",
        "Duration" to "持续时间",
        "Test" to "测试",
        "Pause" to "暂停",
        "Resume" to "继续",
        "Next" to "下一步",
        "Previous" to "上一步",
        "None" to "无",
        "none" to "无",
        "Error" to "错误",
        "Warning" to "警告",
        "Success" to "成功",
        "Enable this edge" to "启用此边缘",
        "Delete panel?" to "删除面板？",
        "Create panel" to "创建面板",
        "No action" to "无动作",
        "No recent runs" to "暂无最近执行记录",
        "The latest task was not completed." to "最近任务尚未完成。",
        "Pending" to "等待中",
        "ready" to "就绪",
        "missing" to "缺失",
        "on" to "开启",
        "off" to "关闭",
        "not connected" to "未连接",
        "Android did not confirm unlocking. You can try again or cancel." to
            "Android 尚未确认解锁。可以重试或取消。",
        "Use Android unlock" to "使用 Android 解锁",
        "Continue paused automation" to "继续暂停的自动化",
        "Continuation requested. Check EdgeFlow's recent automation status to verify the result." to
            "已请求继续自动化。请在 EdgeFlow 的最近运行状态中确认执行结果。",
        "Android confirmed: device is unlocked. Tap Continue to resume only the unfinished steps of a paused task (if any)." to
            "Android 已确认设备解锁。点击“继续”即可恢复暂停任务中尚未执行的步骤。",
        "Screen is waking. Unlock remains unconfirmed." to "正在唤醒屏幕，尚未确认解锁。",
        "Android has not reported an interactive screen. Check the display and try again." to
            "Android 尚未报告屏幕已进入交互状态。请查看屏幕并重试。",
        "Non-secure keyguard detected. Android can dismiss it from this screen." to
            "检测到非安全锁屏，可通过本页面请求 Android 解除。",
        "The device is securely locked. Complete the normal Android PIN, password, pattern or biometric authentication. EdgeFlow will not bypass it." to
            "设备已安全锁定。请按正常流程进行 Android PIN、密码、图案或生物识别认证。EdgeFlow 不会绕过锁屏。",
        "Waiting for Android to confirm unlock or request authentication…" to
            "正在等待 Android 确认解锁或请求身份验证…",
        "Unlock cancelled by user or Android." to "用户或 Android 已取消解锁。",
        "Android rejected the unlock request." to "Android 拒绝了解锁请求。",
        "Dismissal callback received, but device is still locked." to
            "已收到解锁回调，但设备仍然锁定。",
        "Drag across the screenshot to choose a region" to "在截图上拖动以选择区域",
        "Save to Pictures" to "保存到图片目录",
        "Screenshot unavailable" to "截图不可用",
        "Screenshot saved" to "截图已保存",
        "Save failed" to "保存失败",
        "Clipboard history" to "剪贴板历史",
        "Text visible to Android Accessibility. Password fields are excluded. Select an entry to copy." to
            "Android 无障碍服务可读取的文字（不包含密码输入框）。点击条目即可复制。",
        "History saves only when you tap Save clipboard while this screen is in focus. It is encrypted and remains on this device." to
            "仅当本页面位于前台且你点击“保存剪贴板”时才会记录。历史经过加密并保存在本设备。",
        "No readable clipboard text. Bring EdgeFlow to the foreground and try again." to
            "没有可读取的剪贴板文字。请切回 EdgeFlow 前台后重试。",
        "Saved locally" to "已保存到本机",
        "Save clipboard" to "保存剪贴板",
        "History cleared" to "历史记录已清空",
        "No readable text found, or the EdgeFlow Accessibility service is disabled." to
            "未找到可读取文字，或 EdgeFlow 无障碍服务未开启。",
        "Accessibility settings" to "无障碍设置",
        "No saved clipboard entries" to "暂无已保存的剪贴板内容",
        "Copied" to "已复制",
        "Copy failed" to "复制失败",
        "Could not save clipboard text" to "无法保存剪贴板文字",
        "Could not remove entry" to "无法移除条目",
        "The selected region is empty." to "所选区域为空。",
        "Remove unused widget?" to "移除未使用的小组件？",
        "No hosted app widgets" to "没有已托管的小组件",
        "Edge gestures, key remapping and system triggers, delivered through the system server." to
            "通过系统进程实现边缘手势、按键重映射与系统触发器。",
        "LSPosed with modern libxposed API 102" to "需要支持 libxposed API 102 的 LSPosed",
        "EdgeFlow runs inside the system server through LSPosed. Touches are matched against the screen edges before any app sees them, so a bound gesture is consumed and an unbound one passes straight through." to
            "EdgeFlow 通过 LSPosed 在系统进程内工作。触摸事件先经过屏幕边缘手势识别，已绑定的手势会被拦截，未绑定的触摸则正常传递给应用。",
        "Edge gestures have to see a touch before any application does. The only place that happens is the input pipeline inside the system server, so the module declares the system scope rather than hooking individual apps. That also means a reboot is required after changing the scope, because the system server has already started." to
            "边缘手势必须在应用收到触摸之前识别事件，因此模块需要 Hook 系统进程的输入管线，而不是注入每个应用。系统进程启动后不会自动重新加载模块，所以修改作用域后需要重启设备。",
        "Android changes the classes this module hooks between releases. Where a hook target moved, the module detects the new shape at run time and falls back; where a capability was removed entirely, the related action is reported as unavailable rather than failing silently." to
            "不同 Android 版本中的 Hook 类和方法可能发生变化。模块会尝试在运行时适配；如果系统已移除某项能力，则报告动作不可用，而不是静默失败。",
        "Signature differs on Android 16 and 17; resolved automatically" to
            "Android 16 和 17 的方法签名不同，模块会尝试自动适配",
        "Unchanged from Android 9 through 17" to "Android 9–17 的该接口未变化",
        "Read from the inset source provider on Android 11+" to
            "Android 11 及以上版本通过系统 Insets 数据源读取",
        "These triggers fire from the system server, so they work even when the app isn't open." to
            "这些触发器由系统进程执行，即使 EdgeFlow App 没有打开也可触发。",
        "Names must be unique and no longer than 32 characters." to
            "名称不能重复，且不得超过 32 个字符。",
        "Select an installed launchable app" to "选择已安装且可启动的应用",
        "An action can be bound to each trigger." to "可以为每个触发条件绑定一个动作。",
    )

    fun exact(value: String): String? = labels[value]

    fun dynamic(value: String): String? {
        Regex("^Ignore (left|right|top|bottom) edge$", RegexOption.IGNORE_CASE)
            .matchEntire(value)?.let {
                val edge = when (it.groupValues[1].lowercase()) {
                    "left" -> "左侧"; "right" -> "右侧"; "top" -> "顶部"; else -> "底部"
                }
                return "忽略${edge}手势"
            }
        Regex("^Intercept (.+)$").matchEntire(value)?.let {
            return "拦截${UiTranslations.chinese(it.groupValues[1])}键"
        }
        Regex("^(\\d+) points · (\\d+) ms · (In use · protected|Tap to delete)$")
            .matchEntire(value)?.let {
                return "${it.groupValues[1]} 个轨迹点 · ${it.groupValues[2]} 毫秒 · ${UiTranslations.chinese(it.groupValues[3])}"
            }
        Regex("^Delete (.+)$").matchEntire(value)?.let {
            return "删除：${it.groupValues[1]}"
        }
        Regex("^Drag to move (.+)$").matchEntire(value)?.let {
            return "拖动：${it.groupValues[1]}"
        }
        Regex("^Unable to request continuation: (.*)$").matchEntire(value)?.let {
            return "无法继续自动化：${it.groupValues[1]}"
        }
        Regex("^Cannot request Android unlock: (.*)$").matchEntire(value)?.let {
            return "无法请求 Android 解锁：${it.groupValues[1]}"
        }
        Regex("^Import rejected: (.*)$").matchEntire(value)?.let {
            return "导入被拒绝：${it.groupValues[1]}"
        }
        Regex("^Restore failed: (.*)$").matchEntire(value)?.let {
            return "恢复失败：${it.groupValues[1]}"
        }
        Regex("^Export failed: (.*)$").matchEntire(value)?.let {
            return "导出失败：${it.groupValues[1]}"
        }
        Regex("^Could not request execution: (.*)$").matchEntire(value)?.let {
            return "无法请求执行：${it.groupValues[1]}"
        }
        Regex("^Condition: (.*)$").matchEntire(value)?.let {
            return "条件：${UiTranslations.chinese(it.groupValues[1])}"
        }
        Regex("^Condition nesting is limited to (\\d+) levels\\.$").matchEntire(value)?.let {
            return "条件最多嵌套 ${it.groupValues[1]} 层。"
        }
        Regex("^Full (left|right|top|bottom) edge$").matchEntire(value)?.let {
            return "整条${when(it.groupValues[1]) {
                "left" -> "左侧"; "right" -> "右侧"; "top" -> "顶部"; else -> "底部"
            }}边缘"
        }
        Regex("^Unset gestures fall back to the full-length (left|right|top|bottom)\\.$").matchEntire(value)?.let {
            return "未绑定的分段手势将使用${when(it.groupValues[1]) {
                "left" -> "左侧"; "right" -> "右侧"; "top" -> "顶部"; else -> "底部"
            }}整条边缘的动作。"
        }
        Regex("^Level (\\d+)$").matchEntire(value)?.let {
            return "第 ${it.groupValues[1]} 级"
        }
        Regex("^Item (\\d+)$").matchEntire(value)?.let {
            return "项目 ${it.groupValues[1]}"
        }
        Regex("^Cell (\\d+) · row (\\d+), column (\\d+)$").matchEntire(value)?.let {
            return "单元格 ${it.groupValues[1]} · 第 ${it.groupValues[2]} 行、第 ${it.groupValues[3]} 列"
        }
        return null
    }
}
