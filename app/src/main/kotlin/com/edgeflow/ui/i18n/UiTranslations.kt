package com.edgeflow.ui.i18n

import android.content.Context

/**
 * Migration dictionary for legacy UI text; unmatched user-entered names are
 * returned verbatim. English remains the canonical fallback locale.
 */
object UiTranslations {
    /** For classic Android Views and overlay services outside Compose. */
    fun forContext(context: Context, english: String): String =
        if (AppLanguage.effectiveLanguage(context) == AppLanguage.CHINESE) chinese(english)
        else english
    private val zh = mapOf(
        "Home" to "首页", "Gestures" to "手势", "Keys" to "按键",
        "Panels" to "面板", "Actions" to "动作", "Settings" to "设置",
        "About" to "关于", "Back" to "返回", "Save" to "保存",
        "Cancel" to "取消", "Delete" to "删除", "Add" to "添加",
        "Remove" to "移除", "Clear" to "清除", "Close" to "关闭",
        "Search" to "搜索", "Done" to "完成", "Edit" to "编辑",
        "Name" to "名称", "OK" to "确定", "Reset" to "重置",
        "Enable" to "启用", "Enabled" to "已启用",
        "Disable" to "禁用", "Disabled" to "已禁用",
        "Choose" to "选择", "Select" to "选择",
        "Not set" to "未设置", "Not configured" to "尚未配置",
        "Unknown" to "未知", "Active" to "已激活",
        "Not active" to "未激活", "Action needed" to "需要处理",
        "System Hook active" to "系统 Hook 已激活",
        "System startup stage" to "系统模块初始化阶段",
        "Restart needed after APK update" to "更新安装包后需重启",
        "This APK was installed after device startup. EdgeFlow does not hot reload system_server hooks, so the running hook may still be the previous version." to
            "当前安装包是在本次开机之后更新的。EdgeFlow 不支持 system_server Hook 热重载，正在运行的 Hook 可能仍是旧版本。请重启设备后再测试。",
        "Gesture input runtime" to "手势输入运行状态",
        "System gesture configuration" to "系统手势配置",
        "Gesture diagnostics" to "手势诊断",
        "System Hook not confirmed" to "系统 Hook 未验证",
        "Framework connected · hook pending" to "框架已连接 · 系统 Hook 待确认",
        "Framework / hook unconfirmed" to "框架与 Hook 均待确认",
        "Verified" to "已验证",
        "Check scope" to "检查作用域",
        "No verified system_server response" to "尚未收到 system_server 的验证响应",
        "Awaiting XposedService connection" to "等待 XposedService 连接",
        "Awaiting framework service" to "等待框架服务连接",
        "Framework scope" to "框架实际作用域",
        "System Framework (system) selected" to "已授权系统框架（system）",
        "system is not in the framework's granted scope" to "框架授权作用域中没有 system",
        "No live response from system_server yet. If LSPosed has granted System Framework (system), restart to load the updated module and recheck. A hidden framework cannot be judged by installed package names." to
            "尚未收到 system_server 的实时回应。如果 LSPosed 已勾选「系统框架（system）」，请重启以加载新模块后重新检测。隐藏的框架不能通过已安装包名判断。",
        "NPatch detected" to "检测到 NPatch",
        "NPatch injects into individual apps, not system_server. This module needs a system-scoped LSPosed framework." to
            "NPatch 仅支持单个应用注入，不能 Hook system_server。此模块需要支持系统框架作用域的 LSPosed。",
        "System Hook not confirmed. NPatch was detected, but NPatch cannot inject into system_server. Use a system-level LSPosed-compatible framework; select System Framework (system) in its EdgeFlow scope." to
            "系统 Hook 尚未确认。已检测到 NPatch，但 NPatch 不能注入 system_server。请使用支持系统框架作用域的 LSPosed 兼容框架，并在 EdgeFlow 作用域选择「系统框架（system）」。",
        "Module APK is missing its modern Xposed entry or system scope declaration. Reinstall a complete build." to
            "模块安装包缺少 Xposed 入口或系统作用域声明，请重新安装完整版本。",
        "System Hook not confirmed. In LSPosed > EdgeFlow > Scope, select System Framework (system), not only the EdgeFlow app. Reboot once, then tap Recheck." to
            "系统 Hook 尚未确认。请在 LSPosed → EdgeFlow → 作用域中勾选「系统框架（system）」，而不是只勾选 EdgeFlow 应用。然后重启一次，点击重新检查。",
        "The system_server module responded to a live check. Individual gesture hooks still require testing." to
            "已收到 system_server 模块的实时确认。各项手势 Hook 仍需要单独测试。",
        "Target Android 9–17; not fully device-verified" to "目标支持 Android 9–17，尚未全部经过真机验证",
        "Checking…" to "检查中…", "Framework" to "框架",
        "API version" to "API 版本", "Version" to "版本",
        "Android version" to "Android 版本",
        "Module status" to "模块状态", "Configuration" to "配置",
        "How it works" to "工作原理",
        "Edge gestures" to "边缘手势", "Hardware keys" to "实体按键",
        "Left top" to "左上段", "Left center" to "左中段",
        "Left bottom" to "左下段", "Right top" to "右上段",
        "Right center" to "右中段", "Right bottom" to "右下段",
        "Top left" to "顶部左段", "Top center" to "顶部中段",
        "Top right" to "顶部右段",
        "Bottom left" to "底部左段", "Bottom center" to "底部中段",
        "Bottom right" to "底部右段",
        "System triggers" to "系统触发器",
        "Gesture zones" to "手势区域", "Enable this edge" to "启用此边缘",
        "Enable this key" to "启用此按键",
        "Gesture control" to "手势控制", "Key control" to "按键控制",
        "Edge width" to "边缘宽度", "Edge height" to "边缘高度",
        "Swipe threshold" to "滑动阈值",
        "Timing" to "时间设置", "Behaviour" to "行为设置",
        "Long press timeout" to "长按触发时间",
        "Double tap timeout" to "双击间隔",
        "Key double click timeout" to "按键双击间隔",
        "Haptic feedback" to "触觉反馈",
        "Show gesture pointer" to "显示手势指示点",
        "Allow broadcast triggers" to "允许广播触发",
        "Trigger on lock screen" to "锁屏时允许触发",
        "Trigger over the keyboard" to "键盘弹出时允许触发",
        "Gesture geometry" to "手势区域尺寸",
        "Floating overlays" to "悬浮窗",
        "Start overlay host" to "启动悬浮窗服务",
        "Grant overlay access" to "授予悬浮窗权限",
        "Stop overlay host" to "停止悬浮窗服务",
        "Quick Settings tiles" to "快捷设置磁贴",
        "Choose a saved action" to "选择已保存动作",
        "Clear selection" to "清除选择",
        "Wake & unlock diagnostics" to "唤醒与解锁诊断",
        "Test Android unlock flow" to "测试 Android 解锁流程",
        "Recorded gestures" to "已录制手势",
        "No recorded gestures" to "暂无录制的手势",
        "Game Mode · prevent accidental input" to "游戏模式 · 防误触",
        "Enable game mode" to "启用游戏模式",
        "In use · protected" to "使用中 · 已保护",
        "Tap to delete" to "点击删除",
        "Reset everything?" to "重置全部设置？",
        "Reset all settings to defaults" to "恢复所有默认设置",
        "Backup and restore" to "备份与恢复",
        "Export configuration" to "导出配置",
        "Restore configuration" to "恢复配置",
        "Restore previous local configuration" to "恢复上一份本地配置",
        "Configuration backup" to "配置备份",
        "Configuration backup exported." to "配置备份已导出。",
        "Configuration backup restored." to "配置备份已恢复。",
        "Current bindings have been replaced." to "当前绑定已被替换。",
        "Previous configuration restored. Current bindings have been replaced." to "上一份配置已恢复，当前绑定已被替换。",
        "Floating widget bindings" to "悬浮小组件绑定",
        "No hosted app widgets" to "暂无悬浮小组件",
        "Remove unused widget?" to "移除未使用的小组件？",
        "No saved actions" to "暂无已保存动作",
        "Saved actions" to "已保存动作", "New action" to "新建动作",
        "Steps" to "步骤", "Rename" to "重命名", "Rename action" to "重命名动作",
        "Choose an action" to "选择动作", "Choose action" to "选择动作",
        "Search actions" to "搜索动作", "Search conditions" to "搜索条件",
        "Choose condition" to "选择条件",
        "No action" to "无动作", "Premium" to "高级功能",
        "Set up" to "设置", "No items yet. Add entries to build a quick-action strip." to "暂无快捷项，请添加需要的动作。",
        "App freezer" to "应用冻结",
        "Manage frozen apps" to "管理已冻结应用",
        "Refreeze selected apps" to "重新冻结选中的应用",
        "Apps to freeze" to "待冻结的应用",
        "Notification access" to "通知读取权限",
        "Notification access granted" to "通知权限已授予",
        "Grant notification access" to "授予通知权限",
        "Universal Copy & region screenshots" to "通用复制和区域截图",
        "Choose a media playback command." to "选择媒体播放控制。",
        "Use manual tap" to "使用手动点击",
        "X coordinate" to "X 坐标", "Y coordinate" to "Y 坐标",
        "Package name" to "应用包名",
        "Launch through root shell" to "使用 Root 启动",
        "Activity class (optional)" to "Activity 类名（可选）",
        "Intent data URI (optional)" to "Intent 数据 URI（可选）",
        "Shell command" to "Shell 命令", "Run with root" to "以 Root 执行",
        "Milliseconds" to "毫秒", "Text to type" to "输入文字",
        "Android key code" to "Android 按键代码",
        "Meta state (optional, default 0)" to "修饰键状态（可选，默认 0）",
        "Target" to "目标", "Operation" to "操作", "Value" to "值",
        "Variable name" to "变量名称",
        "Notification title" to "通知标题",
        "Notification message" to "通知内容",
        "Text to speak" to "朗读内容",
        "Message" to "消息", "Audio stream" to "音频通道",
        "Increase" to "增大", "Decrease" to "减小",
        "Left" to "左", "Right" to "右", "Up" to "上", "Down" to "下",
        "Swipe left" to "向左滑", "Swipe right" to "向右滑",
        "Swipe up" to "向上滑", "Swipe down" to "向下滑",
        "Hold" to "长按", "Zone" to "区域", "Gesture" to "手势",
        "Key" to "按键", "Pie" to "饼形菜单", "Side bar" to "侧边栏",
        "Screen" to "屏幕", "Power" to "电源", "Network" to "网络",
        "Calls" to "通话", "Apps" to "应用", "Schedule" to "定时任务",
        "App started" to "应用启动", "App stopped" to "应用停止",
        "Gained focus" to "获取焦点", "Lost focus" to "失去焦点",
        "Screen turned on" to "屏幕点亮", "Screen turned off" to "屏幕熄灭",
        "Keyguard unlocked" to "锁屏已解除",
        "Screen rotated" to "屏幕旋转",
        "Rotated to portrait" to "旋转至竖屏",
        "Rotated to landscape" to "旋转至横屏",
        "Keyboard shown" to "键盘弹出", "Keyboard hidden" to "键盘隐藏",
        "Boot completed" to "开机完成",
        "Battery full" to "电池充满", "Battery low" to "低电量",
        "Battery okay" to "电量正常",
        "Power connected" to "连接电源", "Power disconnected" to "断开电源",
        "USB plugged" to "USB 已连接", "USB unplugged" to "USB 已断开",
        "Wi-Fi connected" to "Wi-Fi 已连接", "Wi-Fi disconnected" to "Wi-Fi 已断开",
        "Network connected" to "网络已连接", "Network disconnected" to "网络已断开",
        "Airplane mode on" to "飞行模式开启", "Airplane mode off" to "飞行模式关闭",
        "Headset plugged" to "耳机已连接", "Headset unplugged" to "耳机已断开",
        "Select an installed launchable app" to "选择已安装应用",
        "Choose an app" to "选择应用", "Search apps" to "搜索应用",
        "Remove app trigger" to "删除应用触发器",
        "Add app" to "添加应用", "Add schedule" to "添加定时任务",
        "Scheduled actions" to "定时动作", "Per-app triggers" to "应用触发器",
        "Delete schedule" to "删除定时任务",
        "Time" to "时间", "Action" to "动作", "Note" to "说明",
        "Status bar shown" to "状态栏显示",
        "Status bar hidden" to "状态栏隐藏",
        "Navigation bar shown" to "导航栏显示",
        "Navigation bar hidden" to "导航栏隐藏",
        "Session" to "会话",
        "Screen on" to "屏幕点亮", "Keyguard" to "锁屏",
        "Auto brightness" to "自动亮度",
        "Auto rotation" to "自动旋转",
        "Wi-Fi on" to "Wi-Fi 已开启",
        "Mobile data" to "移动数据",
        "Location" to "定位", "Bluetooth" to "蓝牙", "NFC" to "NFC",
        "Auto-sync" to "自动同步",
        "Hotspot" to "热点",
        "Airplane mode" to "飞行模式",
        "Gestures enabled" to "手势已启用",
        "Keys enabled" to "按键已启用",
        "Music active" to "音乐播放中",
        "Ringing" to "来电响铃中",
        "In call" to "通话中",
        "Keyboard showing" to "键盘正在显示",
        "Status bar showing" to "状态栏显示中",
        "Navigation bar showing" to "导航栏显示中",
        "Battery level" to "电池电量",
        "Time period" to "时间范围",
        "Days of week" to "星期",
        "Days of month" to "每月日期",
        "Months" to "月份",
        "App is focused" to "应用处于前台",
        "Orientation" to "屏幕方向",
        "Portrait" to "竖屏", "Landscape" to "横屏",
        "Variable comparison" to "变量比较",
        "All conditions (AND)" to "所有条件（且）",
        "Any condition (OR)" to "任一条件（或）",
        "Not condition" to "取反条件",
        "Add clause" to "添加条件",
        "Add alternative" to "添加候选条件",
        "Alternative" to "候选条件",
        "Clause" to "条件",
        "Minimum battery (%)" to "最低电量（%）",
        "Maximum battery (%)" to "最高电量（%）",
        "Start time (HH:mm)" to "开始时间（时:分）",
        "End time (HH:mm)" to "结束时间（时:分）",
        "Wi-Fi SSID" to "Wi-Fi 名称",
        "Compare with" to "比较值",
        "Mon" to "周一", "Tue" to "周二", "Wed" to "周三",
        "Thu" to "周四", "Fri" to "周五", "Sat" to "周六", "Sun" to "周日",
        "Jan" to "一月", "Feb" to "二月", "Mar" to "三月", "Apr" to "四月",
        "May" to "五月", "Jun" to "六月", "Jul" to "七月", "Aug" to "八月",
        "Sep" to "九月", "Oct" to "十月", "Nov" to "十一月", "Dec" to "十二月",
    )

    private val replacements = listOf(
        Regex("^Tile (\\d+) · (.*)$") to { match: MatchResult ->
            "磁贴 ${match.groupValues[1]} · ${chinese(match.groupValues[2])}"
        },
        Regex("^Choose action for tile (\\d+)$") to { match: MatchResult ->
            "选择磁贴 ${match.groupValues[1]} 的动作"
        },
        Regex("^Saved action (\\d+)$") to { match: MatchResult ->
            "已保存动作 ${match.groupValues[1]}"
        },
        Regex("^(\\d+) of (\\d+) bindings set$") to { match: MatchResult ->
            "已设置 ${match.groupValues[1]} / ${match.groupValues[2]} 项绑定"
        },
        Regex("^(\\d+) of (\\d+) triggers bound$") to { match: MatchResult ->
            "已绑定 ${match.groupValues[1]} / ${match.groupValues[2]} 个触发器"
        },
        Regex("^Ignore (Left|Right|Top|Bottom) edge$") to { match: MatchResult ->
            "忽略${when (match.groupValues[1]) { "Left" -> "左"; "Right" -> "右"; "Top" -> "上"; else -> "下" }}侧边缘"
        },
        Regex("^Intercept (Back|Home|Recents|Menu|Volume \\+|Volume -)$") to { match: MatchResult ->
            "拦截${when (match.groupValues[1]) { "Back" -> "返回"; "Home" -> "主页"; "Recents" -> "最近任务"; "Menu" -> "菜单"; "Volume +" -> "音量加"; else -> "音量减" }}键"
        },
        Regex("^(\\d+) points · (\\d+) ms · (.*)$") to { match: MatchResult ->
            "${match.groupValues[1]} 个点 · ${match.groupValues[2]} 毫秒 · ${chinese(match.groupValues[3])}"
        },
    )

    fun chinese(value: String): String {
        zh[value]?.let { return it }
        UiTranslationsMore.exact(value)?.let { return it }
        UiTranslationsExtra.exact(value)?.let { return it }
        for ((pattern, replacement) in replacements) {
            val match = pattern.matchEntire(value) ?: continue
            return replacement(match)
        }
        UiTranslationsMore.dynamic(value)?.let { return it }
        UiTranslationsExtra.dynamic(value)?.let { return it }
        return value
    }
}
