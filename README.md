# EdgeFlow

**EdgeFlow** 是面向个人 Root / LSPosed Android 设备的自动化工具与悬浮快捷栏。
项目代码以 **[MIT License](LICENSE)** 开源，目前仍处于 Beta 阶段。

EdgeFlow is an independent automation and floating-shortcut toolkit for
rooted Android devices using LSPosed, distributed under the
**[MIT License](LICENSE)**. It is currently in Beta development.

## 功能 / Features

- **自动化任务**：组合打开应用、延迟、点击、手势、按键、文字输入、条件分支与重复动作；支持执行历史及安全停止。
- **悬浮快捷栏**：拖动、吸边、展开与收起；可绑定最多五个保存任务，并设置透明度、尺寸及锁屏/游戏模式隐藏。
- **快速触发**：边缘手势、实体按键、定时/系统/应用事件及最多十二个快捷设置磁贴。
- **安全解锁**：遵循 Android 正常身份认证流程，不绕过 PIN、密码或生物识别；续行需用户确认。
- **安全与本地化**：JSON 备份/恢复/迁移、运行诊断、简体中文和英文界面。

功能仍需在不同 Android 设备上验收；构建通过不代表系统 Hook 或输入注入已在所有机型上有效。
The project is still under active development. Features and Android
compatibility may vary by device.

## Requirements

| | |
|---|---|
| Android | 9 (API 28) through 17 (intended; not all device-validated) |
| Framework | LSPosed supporting libxposed API 102 |
| Scope | **System Framework** (`system`) |
| Root | Required for LSPosed itself; not required for gestures once installed |

A reboot is required after enabling the module or changing its scope, because the system
server has already started by then. Android 9–17 is the intended compatibility range, not a
certified support matrix; hook and privileged-service behaviour still requires device tests.

## Implementation status

The app includes a task editor, execution diagnostics and history, scheduling,
configuration recovery and an opt-in persistent floating shortcut bar.
Some older actions remain available for backward compatibility but are hidden
from the new-action picker. Device-specific system hooks and injection still
require physical Android testing.

## Modules

```
core/   Pure Kotlin. Action and condition model, id catalogue, serialisation schema.
        No Android dependencies, so the schema can be unit-tested on the JVM.
app/    The module app and the injected code, in one APK.
        hook/    Runs inside system_server.
        data/    Configuration store and status detection.
        ui/      Compose UI.
        service/ Notification listener, boot receiver and overlay-host service.
```

The two are split so the persisted format — the contract between the app and the injected
code — lives in a module with no Android surface.

## How it works

### Input interception

Everything hinges on `InputManagerService.filterInputEvent(InputEvent, int)`. The native
input dispatcher calls it for every key and motion event, and its return value is inverted
from what the name suggests: the Java method returns *whether dispatch should continue*, so
`false` means the filter consumed the event.

The method body forwards to a Binder `IInputFilter` that a module cannot install, so the
hook answers directly and the original body never runs. Two further hooks make the
dispatcher call it at all:

- `NativeInputManagerService$NativeImpl.setInputFilterEnabled(boolean)` is forced to `true`.
- `InputManagerService.setInputFilter(IInputFilter)` is watched so clearing the filter
  re-opens the native gate.

These signatures are unchanged from Android 9 through 17.

### Why gestures only consume what they match

An edge strip that swallowed every touch would break the system back gesture and pull-down
shade. The gesture engine therefore passes every event through until it has positively
matched a binding, and only then consumes. The rule is enforced in one place,
`GestureEngine`, and the resolver answers "is anything bound here?" before the engine
commits to tracking a pointer.

### Cross-process configuration

The configuration is one JSON document in the module app's own preferences.
The app synchronizes it to a separate LSPosed remote preferences store through
the bound Xposed service, rather than sharing one Android preferences file.
The schema lives in
`core/model/EdgeConfig.kt`; `ConfigCodec` is the only serialiser.

Reads happen on the input thread, so the parsed model is cached and re-parsed only when the
stored string changes. A document that fails to parse is reported and the previous good
configuration is kept — a bad edit must not silently disable the user's gestures.

### Compatibility with changing framework internals

Android moves the classes this module hooks. Rather than branch on version numbers, each
hook resolves its target defensively and adapts:

| Target | Handling |
|---|---|
| `PhoneWindowManager.interceptKeyBeforeDispatching` | Return type changed from `long` (API 36, `-1` means consumed) to `boolean` (API 37, `true` means consumed). The sentinel is derived from the declared return type, so no version check is needed. |
| `InsetsSourceProvider.updateVisibility` | Resolved by name and arity, because its parameter list is not stable. |
| `InputMethodManagerService.setImeWindowStatusLocked` | Same. |
| `ActivityRecord.setState` | Resolved by name with an arity fallback. |

A hook that cannot be installed disables one feature and logs why; it never aborts startup
and never propagates an exception into the framework. An exception escaping a hook callback
can take down the system server.

## Action model

Actions and conditions are sealed hierarchies serialised polymorphically on their integer
id. That is what makes multi-actions, conditionals, repeats and sub-gestures nest
arbitrarily without special cases: a `Sequence` holds `List<Action>`, and an `Action` may be
another `Sequence`.

Ids are part of the on-disk format and must never be reassigned. The catalogue in
`ActionCatalog` carries the presentation metadata, kept out of the model so the injected
code does not depend on UI concerns.

## Building

```bash
./gradlew :app:assembleDebug
```

`local.properties` must point at an Android SDK with compile platform 37.
JDK 17 is required.

The Debug APK is signed with the development key. Release is **unsigned
unless you explicitly configure a private release keystore**; never distribute
an unsigned or debug-signed APK as a production release.

## Install

1. Install the APK.
2. Open LSPosed, enable EdgeFlow, and set its scope to **System Framework**.
3. Reboot.
4. Open EdgeFlow; the status card confirms the hooks are live.

## Layout of this repository

```
app/            Module app and injected code
core/           Shared model and schema
LICENSE         MIT license
```

## License / 开源协议

EdgeFlow 项目代码以 [MIT License](LICENSE) 发布。
EdgeFlow source code is licensed under the [MIT License](LICENSE).
