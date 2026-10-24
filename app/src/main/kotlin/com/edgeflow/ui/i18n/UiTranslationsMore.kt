package com.edgeflow.ui.i18n

/**
 * Additional personal-Beta screen translations. Keep exact English labels as
 * keys so user-created names, shell commands, typed content and file paths are
 * never translated by an accidental substring replacement.
 */
internal object UiTranslationsMore {
    private val pages = mapOf(
        "Automations" to "自动化",
        "Floating" to "悬浮栏",
        "Floating shortcuts" to "悬浮快捷栏",
        "Input" to "输入",
        "Flow" to "流程",
        "Feedback" to "反馈",
        "Media" to "媒体",
        "Basic" to "基础",
        "System" to "系统",
        "Switches" to "开关",
        "Default" to "默认",
        "Off" to "关闭",
        "On" to "开启",
        "Ready" to "就绪",
        "Missing" to "缺失",
        "Copy" to "复制",
        "Stop" to "停止",
        "Restore" to "恢复",
        "Remove step" to "移除步骤",
        "New action" to "新建动作",
        "Recent automation steps" to "最近自动化步骤",
        "No run yet" to "尚无执行记录",
        "Not set" to "未设置",
        "No runs yet" to "暂无运行记录",
        "RUNNING" to "正在运行",
        "DISPATCHED" to "已派发",
        "FAILED" to "执行失败",
        "STOPPED" to "已停止",
        "IDLE" to "空闲",
        "Home" to "首页",
        "EdgeFlow · Unlock request" to "EdgeFlow · 解锁请求",
        "Use Android unlock" to "使用 Android 解锁",
        "Continue paused automation" to "继续暂停的自动化",
        "Automation history" to "自动化运行历史",
        "Stored on this device · newest 80 runs · no text input or shell commands" to "仅保存在本机 · 最多保留最近 80 次运行 · 不记录输入文字和 Shell 命令",
        "Refresh history" to "刷新历史记录",
        "View recent runs" to "查看最近运行",
        "Run this automation?" to "要运行这个自动化任务吗？",
        "Test run" to "试运行",
        "Run once" to "运行一次",
        "Run once · see latest execution diagnostics on Home" to "仅执行一次 · 可在首页查看最新执行诊断",
        "Automation request" to "自动化执行请求",
        "Recent automation runs" to "最近自动化运行记录",
        "Clear history" to "清空历史记录",
        "Delete execution history?" to "删除执行历史？",
        "Delete history" to "删除历史记录",
        "This removes saved run metadata only. Automations and their bindings are preserved." to
            "只会删除保存的运行记录，自动化任务及其绑定不会被删除。",
        "Create one to reuse a sequence of steps from several triggers." to
            "创建自动化任务后，可以在多个触发条件中复用同一组操作步骤。",
        "This can interact with other apps or change system settings. Review the steps and unlock your device before proceeding. Check Home → Last automation for dispatch and failure information." to
            "该操作可能控制其他应用或修改系统设置。请确认步骤并解锁设备后继续，可在首页的“最近自动化”中查看派发与失败信息。",
        "Execution requested. This is not confirmation that the task completed." to
            "已请求执行，但这并不表示任务已经成功完成。",
        "This task was deleted or is no longer configured." to
            "该任务已删除或不再可用。",
        "Configuration backup & recovery" to "配置备份与恢复",
        "Export a full backup before major updates. Existing bindings and action IDs are preserved during migration." to
            "重大升级前请导出完整备份。迁移期间会保留已有绑定和动作编号。",
        "Import configuration" to "导入配置",
        "Validate the file and back up the current document before replacing it" to
            "验证文件，并在替换前备份当前配置",
        "Restore previous edit" to "恢复上次修改前的配置",
        "Undo the last saved configuration change (not task execution)" to
            "撤销上一次配置保存操作（不影响已执行的任务）",
        "Restore original pre-upgrade settings" to "恢复升级前的原始配置",
        "Original JSON is preserved separately from ordinary edit history" to
            "升级前的原始 JSON 与日常修改记录分开保存",
        "Restore saved configuration?" to "确定恢复已保存的配置？",
        "The current settings and bindings will be replaced. Export a backup first if you need to keep them." to
            "当前设置和绑定将被替换。如需保留，请先导出备份。",
        "Configuration restored. Recheck permissions and bindings." to
            "配置已恢复。请重新检查权限和动作绑定。",
        "Configuration restored; verify all triggers and permissions." to
            "配置已恢复，请检查触发器和权限。",
        "Backup exported. Keep it in a safe location." to "备份已导出，请妥善保存。",
        "Export configuration" to "导出配置",
        "Restore configuration" to "恢复配置",
        "Restore previous local configuration" to "恢复上一版本地配置",
        "Restore original pre-upgrade configuration" to "恢复升级前原始配置",
        "Configuration" to "配置",
        "Export a backup before restoring." to "恢复前请先导出备份。",
        "The current configuration could not be loaded. Export it before restoring a backup." to
            "当前配置无法读取。恢复备份前，请先导出损坏的原始文件。",
        "Exports unreadable original JSON for recovery" to "导出无法读取的原始 JSON 以便恢复",
        "Save an editable JSON document using Android's file picker" to
            "通过 Android 文件选择器保存可编辑的 JSON 文件",
        "Restore the last successful settings snapshot (before your most recent edit/import)." to
            "恢复上一次成功保存的设置快照（最近一次编辑或导入前）。",
        "Export your configuration as a JSON file or restore a previous backup." to
            "将配置导出为 JSON 文件，或恢复之前的备份。",
        "Save a portable JSON backup." to "保存可携带的 JSON 备份。",
        "Replaces the current configuration after validation." to "验证后替换当前配置。",
        "Reset" to "重置",
        "Emergency stop" to "紧急停止",
        "Stop all automations" to "停止所有自动化",
        "Cancel running and queued automations without disabling normal Android touch or hardware keys." to
            "取消正在执行及排队中的自动化，不影响 Android 正常触摸和实体按键。",
        "Requests immediate cancellation. Long-running external shell commands may still need to exit." to
            "立即请求停止。长时间运行的外部 Shell 命令可能还需等待退出。",
        "Floating overlays" to "悬浮窗",
        "The app-side overlay host must remain enabled for Pie menus and panels to open over other apps." to
            "如需在其他应用上方显示圆盘菜单或面板，必须保持悬浮窗服务运行。",
        "Stops the foreground service and removes any open overlay." to
            "停止前台服务并移除当前打开的悬浮层。",
        "Optional Android Accessibility permission. Only reads text or takes screenshots when you trigger these actions. Region screenshot requires Android 11+." to
            "可选的 Android 无障碍权限。仅主动触发时读取文字或截图；区域截图需要 Android 11 及以上版本。",
        "Screen wake is not the same as unlocking. Android handles secure PIN, pattern, password or biometric authentication." to
            "唤醒屏幕不等于解锁。PIN、图案、密码和生物识别均由 Android 系统负责验证。",
        "Open the platform unlock request screen. This does not change your device credentials." to
            "打开系统解锁请求页面，不会更改设备凭据。",
        "Use the Gesture record action to capture a stroke. Bind one with Inject Gesture. Recordings currently used by any action are protected from deletion." to
            "使用“录制手势”捕获轨迹，再通过“注入手势”绑定。正在被动作引用的录制轨迹无法删除。",
        "Original Edge-style input guard. Edge choices pass through to the game; selected keys are intercepted. Leave Home and Back unselected unless needed." to
            "游戏模式下，所选边缘手势交由游戏处理，勾选的实体按键将被拦截。除非必要，请不要拦截主页和返回键。",
        "Ignore selected edge gestures and block selected hardware keys" to
            "忽略指定边缘手势并拦截选定的实体按键",
        "Let the foreground game handle touches on this edge" to
            "由前台游戏接管此边缘的触摸",
        "Suppress this key only while Game Mode is on" to
            "仅在游戏模式开启时拦截此按键",
        "Measured from the physical screen edge." to "从屏幕物理边缘开始计算。",
        "Thickness of the left and right strips" to "左右触发区域的宽度",
        "Thickness of the top and bottom strips" to "上下触发区域的高度",
        "How far the finger must travel to count as a swipe" to "手指需要移动多远才判定为滑动",
        "A single tap waits this long before firing" to "单击需等待此时长，以避免与双击冲突",
        "Master switch for all edge gestures" to "控制所有边缘手势的总开关",
        "Master switch for all key remapping" to "控制所有按键映射的总开关",
        "Allow gestures and keys to fire while the device is locked" to "允许在设备锁定时触发手势与按键动作",
        "Allow gestures when a soft keyboard is showing" to "软键盘显示时允许触发手势",
        "Let other apps fire actions with an intent. Off is safer." to
            "允许其他应用通过 Intent 触发动作。关闭更安全。",
        "Draw a dot that follows the finger during a gesture" to "手势过程中显示跟随手指移动的指示点",
        "Vibrate briefly when a gesture fires" to "触发手势时短暂振动",
        "Clears every gesture, key and trigger binding" to "清除全部手势、按键和触发器绑定",
        "Reset all settings to defaults" to "将全部设置恢复为默认值",
        "Floating automation shortcuts" to "悬浮自动化快捷栏",
        "Optional draggable edge handle, independent of Pie and Panel overlays. Tap to expand up to five saved tasks." to
            "可选的可拖动边缘手柄，与圆盘菜单和面板独立。点击可展开最多五个已保存任务。",
        "Enable floating shortcut bar" to "启用悬浮快捷栏",
        "Hide on lock screen" to "锁屏时隐藏",
        "Do not cover Android authentication or the keyguard" to
            "不遮挡 Android 解锁验证和锁屏界面",
        "Hide in Game Mode" to "游戏模式下隐藏",
        "Avoid blocking game touches while Game Mode is enabled" to
            "游戏模式开启时避免阻挡游戏触摸",
        "Reset handle position" to "重置手柄位置",
        "Move the handle to the right edge at the default height" to
            "将手柄移动至右侧边缘的默认高度",
        "Create a saved automation first, then select it below." to
            "请先创建并保存自动化任务，然后在下方选择。",
        "Active · drag to move, tap to expand" to
            "已启用 · 拖动可移动，点击可展开",
        "Off by default. Requires Android overlay permission." to
            "默认关闭，需要 Android 悬浮窗权限。",
        "Pinned to floating bar" to "已固定至悬浮快捷栏",
        "Maximum 5 shortcuts" to "最多设置 5 个快捷任务",
        "Tap to add" to "点击添加",
        "No tasks selected" to "尚未选择任务",
        "Collapse automation shortcuts" to "收起自动化快捷栏",
        "Expand automation shortcuts" to "展开自动化快捷栏",
    )

    private val actions = mapOf(
        "No action" to "无动作",
        "Recent apps" to "最近任务",
        "Menu" to "菜单",
        "Last app" to "上一个应用",
        "Previous app" to "前一个应用",
        "Next app" to "下一个应用",
        "Sleep / wake" to "休眠／唤醒",
        "Power menu" to "电源菜单",
        "Reboot" to "重启",
        "Soft reboot" to "软重启",
        "Shut down" to "关机",
        "Launch app" to "启动应用",
        "Launch activity" to "启动 Activity",
        "Launch activity (legacy)" to "启动 Activity（旧版）",
        "Launch activity (alternate)" to "启动 Activity（备用）",
        "Launch activity (alternate 2)" to "启动 Activity（备用 2）",
        "Kill app" to "结束应用进程",
        "Kill foreground app" to "结束前台应用进程",
        "Finish activity" to "结束 Activity",
        "Kill background apps" to "结束后台应用",
        "Freezer drawer" to "应用冻结列表",
        "Refreeze frozen apps" to "重新冻结应用",
        "Apps drawer" to "应用抽屉",
        "Running apps drawer" to "运行中的应用",
        "Screenshot" to "屏幕截图",
        "Screenshot region" to "区域截图",
        "Expand notifications" to "展开通知栏",
        "Expand quick settings" to "展开快捷设置",
        "Split screen" to "分屏",
        "Request Android unlock" to "请求 Android 解锁",
        "Rotate screen" to "旋转屏幕",
        "Fast scroll" to "快速滚动",
        "Universal copy" to "通用复制",
        "Game mode" to "游戏模式",
        "Location mode" to "定位模式",
        "Wi-Fi hotspot" to "Wi-Fi 热点",
        "Torch" to "手电筒",
        "Screen filter" to "屏幕滤镜",
        "Stay awake" to "保持屏幕常亮",
        "Hide status bar" to "隐藏状态栏",
        "Hide navigation bar" to "隐藏导航栏",
        "Gesture recording" to "录制手势",
        "Floating widget" to "悬浮小组件",
        "Shortcuts panel" to "快捷面板",
        "Custom panel" to "自定义面板",
        "Pen" to "画笔",
        "Gesture pointer" to "手势指示点",
        "Inject key event" to "注入按键事件",
        "Inject key event (alternate)" to "注入按键事件（备用）",
        "Inject gesture" to "注入手势",
        "Input text" to "输入文字",
        "Input text (alternate)" to "输入文字（备用）",
        "Soft keyboard" to "软键盘",
        "Read clipboard text" to "读取剪贴板文字",
        "Clipboard" to "剪贴板",
        "Edit text" to "编辑文字",
        "Adjust" to "调整数值",
        "Slide adjust" to "滑动调整",
        "Successive adjust" to "连续调整",
        "Multi-action" to "多步骤动作",
        "Saved multi-action" to "已保存多步骤动作",
        "Saved multi-action (alternate)" to "已保存多步骤动作（备用）",
        "Conditional" to "条件分支",
        "Repeat" to "重复执行",
        "Delay" to "延迟",
        "Abort" to "终止当前任务",
        "Stop all" to "全部停止",
        "Set variable" to "设置变量",
        "Sub gestures" to "二阶段手势",
        "Root shell command" to "Root Shell 命令",
        "Shell command" to "Shell 命令",
        "Select and edit" to "选择并编辑动作",
        "Play / pause" to "播放／暂停",
        "Previous track" to "上一曲",
        "Next track" to "下一曲",
        "Media control" to "媒体控制",
        "Adjust volume" to "调节音量",
        "Ringer mode" to "铃声模式",
        "Brightness up" to "增加亮度",
        "Brightness down" to "降低亮度",
        "Toast" to "短消息提示",
        "Notification" to "通知",
        "Speech" to "文字朗读",
        "Vibrate" to "振动",
        "Play sound" to "播放声音",
        "App option menu" to "应用选项菜单",
        "Fall back to the parent edge" to "未设置时使用父级边缘动作",
        "Cut, copy, paste, select all, undo, or redo" to
            "剪切、复制、粘贴、全选、撤销或重做",
        "Launch" to "启动",
        "Then" to "满足条件时",
        "Otherwise" to "否则",
        "Set up" to "配置",
        "Choose wrapped action" to "选择内部动作",
        "Add step" to "添加步骤",
        "Add repeated action" to "添加重复动作",
        "Custom label (optional)" to "自定义名称（可选）",
        "Choose an installed widget, then configure its movable, closable overlay." to
            "请选择已安装的小组件，然后配置可移动、可关闭的悬浮窗口。",
        "Remember position after dragging" to "拖动后记住位置",
        "Finish setup in Android to continue." to "请先在 Android 系统界面完成设置。",
        "No installed app widgets are available." to "没有可用的已安装小组件。",
        "Select a recorded stroke, or use manual coordinates for a single tap." to
            "选择已录制的手势轨迹，或使用手动坐标模拟一次点击。",
        "Choose the edge where the menu should appear." to "请选择菜单要显示在哪一侧边缘。",
        "Choose a media playback command." to "请选择媒体播放控制动作。",
        "Send the selected keyboard shortcut to the currently focused text field." to
            "向当前获得焦点的文字输入框发送选定快捷键。",
        "Continuous adjustment is intended for swipe gestures. Moving up or right increases the value; moving down or left decreases it." to
            "连续调整适合配合滑动手势使用。上滑或右滑增加数值，下滑或左滑减少数值。",
        "Choose an action to run." to "选择要执行的动作。",
        "Use this on an edge swipe or long press. After the first gesture, continue moving or hold to trigger the selected secondary action." to
            "适用于边缘滑动或长按。完成第一次手势后继续移动或按住，可触发相应的二阶段动作。",
        "Choose an action to run" to "选择要执行的动作",
        "Steps run in order. Configure parameters, replace, duplicate or reorder individual steps." to
            "步骤按顺序执行。可配置参数、修改、复制或调整每一步顺序。",
        "Select an installed launchable app" to "选择已安装且可启动的应用",
        "Choose apps and bind actions to their lifecycle and focus transitions." to
            "选择应用，并为启动、退出及焦点变化绑定动作。",
        "Keeps the app installed; only removes EdgeFlow bindings." to
            "不会卸载应用，仅移除 EdgeFlow 的相关绑定。",
        "Schedules use the device's local time. The system trigger service must be active." to
            "定时任务使用设备本地时间，需要保持系统触发器服务正常运行。",
        "Create a daily schedule, then choose its days and action." to
            "创建定时任务，再选择执行日期和动作。",
        "Removes this schedule and its action binding." to "移除此定时计划及其动作绑定。",
        "The inner condition must not match." to "内部条件不得成立。",
        "Select a condition before saving." to "保存前请选择条件。",
        "A single click waits for the double-click window before firing so it does not also trigger on a double click." to
            "单击会等待双击识别时间结束，以防双击时也触发单击。",
        "Inner ring is level 1, outer ring is level 2." to "内圈为第一级，外圈为第二级。",
        "Level 1" to "第一级",
        "Level 2" to "第二级",
        "Pie layout" to "圆盘菜单布局",
        "Shortcut panels" to "快捷面板",
        "Each edge has a four-row grid. Bind each cell to an action." to
            "每侧边缘都有四行网格，可为每个格子绑定动作。",
        "Custom panels" to "自定义面板",
        "Create a grid, then tap any cell to assign an action. Use the panel ID in a Custom panel action to open it." to
            "创建网格后，点击单元格分配动作。可通过自定义面板动作中指定的面板 ID 打开。",
        "No custom panels yet." to "暂无自定义面板。",
        "Create panel" to "创建面板",
        "Delete panel?" to "删除面板？",
        "Rows" to "行数",
        "Columns" to "列数",
        "Rows (1–8)" to "行数（1–8）",
        "Columns (1–8)" to "列数（1–8）",
        "Number of Quick Settings tiles" to "快捷设置磁贴数量",
        "Disable some system tiles?" to "禁用部分系统磁贴？",
        "Disable extra tiles" to "禁用多余磁贴",
        "Tile update failed" to "磁贴设置失败",
        "Android may remove disabled EdgeFlow tiles from Quick Settings. You may need to add them again later. Saved actions will be kept." to
            "Android 可能移除快捷设置中已禁用的 EdgeFlow 磁贴。重新启用后可能需要手动添加；保存的动作不会删除。",
        "Choose 0–12 actual Android tiles. Reducing the count hides components but preserves their saved action bindings." to
            "可设置 0–12 个 Android 快捷磁贴。减少数量只会隐藏磁贴组件，保留原来的动作绑定。",
        "Choose how many independent tiles Android exposes (0–12)" to
            "选择 Android 系统显示多少个独立磁贴（0–12）",
        "Current setting" to "当前设置",
        "Requires confirmation" to "需要确认",
        "Enable more tiles" to "启用更多磁贴",
    )

    fun exact(value: String): String? = pages[value] ?: actions[value]

    private fun status(value: String): String = when (value) {
        "RUNNING" -> "正在运行"
        "DISPATCHED" -> "已派发"
        "FAILED" -> "执行失败"
        "STOPPED" -> "已停止"
        "IDLE" -> "空闲"
        "ready" -> "就绪"
        "missing" -> "缺失"
        "not connected" -> "未连接"
        "on" -> "开启"
        "off" -> "关闭"
        "true" -> "是"
        "false" -> "否"
        else -> value
    }

    /**
     * Numeric/status templates must be handled explicitly. Do not blindly
     * replace English words in arbitrary task names, notifications or typed
     * strings: only these known full UI formats are translated.
     */
    fun dynamic(value: String): String? {
        Regex("^Hook: (ready|missing) · Native: (ready|not connected) · Filter: (on|off)$")
            .matchEntire(value)?.let {
                return "Hook：${status(it.groupValues[1])} · 原生输入：${status(it.groupValues[2])} · 输入过滤：${status(it.groupValues[3])}"
            }
        Regex("^Schema (\\d+) · (\\d+)/12 zones · (\\d+) executable bindings$")
            .matchEntire(value)?.let {
                return "配置版本 ${it.groupValues[1]} · 已启用 ${it.groupValues[2]}/12 个区域 · ${it.groupValues[3]} 项可执行绑定"
            }
        Regex("^(\\d+) input events · (\\d+) matched gestures · tap Recheck to refresh$")
            .matchEntire(value)?.let {
                return "${it.groupValues[1]} 次输入事件 · ${it.groupValues[2]} 次识别手势 · 点击重新检查以刷新"
            }
        Regex("^(\\d+) attempts · (\\d+) accepted · (\\d+) rejected(?: · last code (\\d+))?\\. Accepted only means Android received the event, not that the target acted\\.$")
            .matchEntire(value)?.let {
                val code = it.groupValues[4]
                return "${it.groupValues[1]} 次尝试 · 已接受 ${it.groupValues[2]} 次 · 已拒绝 ${it.groupValues[3]} 次" +
                    (if (code.isNotEmpty()) " · 最后返回码 $code" else "") +
                    "。接受仅表示 Android 收到输入事件，不代表目标应用已执行动作。"
            }
        Regex("^(.*?) · (\\d+) dispatched · (\\d+) failed\\. Dispatch is not proof of an app-side effect\\.$")
            .matchEntire(value)?.let {
                return "${UiTranslations.chinese(it.groupValues[1])} · 已派发 ${it.groupValues[2]} 步 · 失败 ${it.groupValues[3]} 步。派发不代表目标应用执行成功。"
            }
        Regex("^([^\n]+) · (\\d+)ms · (\\d+) dispatched · (\\d+) failed$")
            .matchEntire(value)?.let {
                return "${it.groupValues[1]} · ${it.groupValues[2]} 毫秒 · 已派发 ${it.groupValues[3]} 步 · 失败 ${it.groupValues[4]} 步"
            }
        Regex("^Last automation: (RUNNING|DISPATCHED|FAILED|STOPPED|IDLE)$").matchEntire(value)?.let {
            return "最近自动化：${status(it.groupValues[1])}"
        }
        Regex("^Active tiles: (\\d+)$").matchEntire(value)?.let {
            return "已启用磁贴：${it.groupValues[1]} 个"
        }
        Regex("^(\\d+) tiles$").matchEntire(value)?.let {
            return "${it.groupValues[1]} 个磁贴"
        }
        Regex("^Tile (\\d+) · (.+)$").matchEntire(value)?.let {
            return "磁贴 ${it.groupValues[1]} · ${UiTranslations.chinese(it.groupValues[2])}"
        }
        Regex("^Handle size: (\\d+)%$").matchEntire(value)?.let {
            return "手柄大小：${it.groupValues[1]}%"
        }
        Regex("^Opacity: (\\d+)%$").matchEntire(value)?.let {
            return "透明度：${it.groupValues[1]}%"
        }
        Regex("^Action (\\d+)$").matchEntire(value)?.let {
            return "动作 ${it.groupValues[1]}"
        }
        Regex("^Saved actions: (\\d+)$").matchEntire(value)?.let {
            return "已保存动作：${it.groupValues[1]} 个"
        }
        Regex("^Available panels: (.*)$").matchEntire(value)?.let {
            return "可用面板：${if (it.groupValues[1] == "none") "无" else it.groupValues[1]}"
        }
        Regex("^Set up (.+)$").matchEntire(value)?.let {
            return "设置${UiTranslations.chinese(it.groupValues[1])}"
        }
        Regex("^Use (.+)$").matchEntire(value)?.let {
            return "使用${UiTranslations.chinese(it.groupValues[1])}"
        }
        Regex("^Selected: (.+)$").matchEntire(value)?.let {
            return "已选择：${it.groupValues[1]}"
        }
        Regex("^Action: (.+)$").matchEntire(value)?.let {
            return "动作：${UiTranslations.chinese(it.groupValues[1])}"
        }
        Regex("^Then: (.+)$").matchEntire(value)?.let {
            return "条件成立时：${UiTranslations.chinese(it.groupValues[1])}"
        }
        Regex("^Otherwise: (.+)$").matchEntire(value)?.let {
            return "否则：${UiTranslations.chinese(it.groupValues[1])}"
        }
        Regex("^(Hold|Swipe left|Swipe right|Swipe up|Swipe down): (.+)$").matchEntire(value)?.let {
            return "${UiTranslations.chinese(it.groupValues[1])}：${UiTranslations.chinese(it.groupValues[2])}"
        }
        Regex("^(\\d+)\\. (.+)$").matchEntire(value)?.let {
            return "${it.groupValues[1]}. ${UiTranslations.chinese(it.groupValues[2])}"
        }
        Regex("^Delay (\\d+) ms$").matchEntire(value)?.let {
            return "延迟 ${it.groupValues[1]} 毫秒"
        }
        Regex("^Tap \\((\\d+), (\\d+)\\)$").matchEntire(value)?.let {
            return "点击坐标（${it.groupValues[1]}，${it.groupValues[2]}）"
        }
        Regex("^Key (\\d+)$").matchEntire(value)?.let {
            return "按键代码 ${it.groupValues[1]}"
        }
        Regex("^Replay gesture (.+)$").matchEntire(value)?.let {
            return "回放手势 ${it.groupValues[1]}"
        }
        Regex("^Launch (.+)$").matchEntire(value)?.let {
            return "启动应用 ${it.groupValues[1]}"
        }
        Regex("""^Input "(.*)"$""").matchEntire(value)?.let {
            return "输入文字「${it.groupValues[1]}」"
        }
        Regex("^Shell: (.*)$").matchEntire(value)?.let {
            return "Shell 命令：${it.groupValues[1]}"
        }
        Regex("^Root shell: (.*)$").matchEntire(value)?.let {
            return "Root Shell 命令：${it.groupValues[1]}"
        }
        Regex("^Latest: (.*)$").matchEntire(value)?.let {
            return "最新状态：${status(it.groupValues[1]).let(UiTranslations::chinese)}"
        }
        Regex("^Last automation: (.+)$").matchEntire(value)?.let {
            return "最近自动化：${status(it.groupValues[1])}"
        }
        Regex("^(\\d+) entries · dispatched is not confirmed success$").matchEntire(value)?.let {
            return "${it.groupValues[1]} 条记录 · 已派发不代表目标应用执行成功"
        }
        Regex("^(\\d+) attempts · (\\d+) accepted · (\\d+) rejected(.*)$").matchEntire(value)?.let {
            return "${it.groupValues[1]} 次尝试 · ${it.groupValues[2]} 次已接受 · ${it.groupValues[3]} 次被拒绝${it.groupValues[4]}"
        }
        Regex("^(\\d+) input events · (\\d+) matched gestures(.*)$").matchEntire(value)?.let {
            return "${it.groupValues[1]} 次输入事件 · ${it.groupValues[2]} 次识别手势${it.groupValues[3]}"
        }
        Regex("^Schema (\\d+) · (\\d+)/12 zones · (.*)$").matchEntire(value)?.let {
            return "配置版本 ${it.groupValues[1]} · 已启用 ${it.groupValues[2]}/12 个区域 · ${it.groupValues[3]}"
        }
        Regex("^Hook: (ready|missing) · (.*)$").matchEntire(value)?.let {
            return "Hook：${status(it.groupValues[1])} · ${it.groupValues[2]}"
        }
        Regex("^(\\d+) dispatched · (\\d+) failed(.*)$").matchEntire(value)?.let {
            return "已派发 ${it.groupValues[1]} 步 · 失败 ${it.groupValues[2]} 步${it.groupValues[3]}"
        }
        Regex("^Run (.+)$").matchEntire(value)?.let {
            return "运行${it.groupValues[1]}"
        }
        Regex("^No .* yet[.]?$").matchEntire(value)?.let {
            // Only exact known labels above should be translated. Unknown
            // messages (possibly supplied by a user) remain unmodified.
            return exact(value)
        }
        return null
    }
}
