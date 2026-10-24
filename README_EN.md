# EdgeFlow

**English** | [简体中文](README.md)

> An independent, local-first Android automation toolkit and floating shortcut interface for personal devices using Root and LSPosed.
>
> **Status: Beta development.** Building an APK is not evidence that all system hooks, injected gestures or OEM-specific behavior work on every device.

EdgeFlow lets you assemble repeatable actions, bind them to gestures or quick triggers, and access saved tasks from a movable floating shortcut bar. The app is licensed under the [MIT License](LICENSE).

## Contents

- [Features](#features)
- [Requirements and compatibility](#requirements-and-compatibility)
- [Installation and activation](#installation-and-activation)
- [Quick start](#quick-start)
- [Floating ball and concentric ring menu](#floating-ball-and-concentric-ring-menu)
- [Safety, permissions and privacy](#safety-permissions-and-privacy)
- [Architecture](#architecture)
- [Build and tests](#build-and-tests)
- [Troubleshooting](#troubleshooting)
- [Status and roadmap](#status-and-roadmap)
- [License](#license)

## Features

| Area | Capabilities | Notes |
| --- | --- | --- |
| Automation | Saved action sequences, application launches, waits, taps, swipes, text input, conditional steps, bounded repetition, playback and stop controls | Some actions rely on LSPosed and device-specific input hooks |
| Floating shortcuts | Persistent movable edge handle, optional list mode (up to 5 pinned tasks), optional concentric ring mode | Disabled by default; requires overlay permission |
| Concentric ring menu | **2 rings × 6 sectors by default** (12 slots), optional 3 rings (18 slots), spring opening, sector highlighting and haptic feedback | Each sector can bind a saved task; one-finger slide-and-release or tap to select |
| Triggers | Edge gestures, selected hardware keys, schedules, screen/app events and Android Quick Settings tiles | Trigger availability depends on system support, configured scope and permissions |
| Game Mode | Optional prevention of accidental edge/key actions | Floating shortcuts can automatically hide |
| Config and diagnostics | JSON backup/recovery, import/export, status checks, log and execution diagnostics | Existing bindings are retained during compatible upgrades |
| Language | Simplified Chinese and English app UI | See known translation limitations as features evolve |

EdgeFlow focuses on personal use. It is **not** an enterprise MDM product and does not attempt to bypass Android authentication.

## Requirements and compatibility

| Requirement | Detail |
| --- | --- |
| Android | Android 9 / API 28 minimum; Android 9–17 are intended targets, **not a fully certified compatibility matrix** |
| Root / framework | A working LSPosed installation that supports the required module API |
| LSPosed scope | **System Framework** (`system`) for system-level hook features |
| Floating UI | Android **Display over other apps** permission |
| Build tools | **JDK 17**, Android SDK with **compile platform 37**, compatible Android Gradle toolchain |

The app and the system-level hook may initialize independently. A green app indicator alone must not be interpreted as proof that a specific input hook is operational. Run a real gesture/action test.

## Installation and activation

1. Build or obtain a trusted EdgeFlow APK. Avoid installing APKs from unknown distributors.
2. Install the APK and launch EdgeFlow once.
3. In LSPosed, enable the EdgeFlow module and select **System Framework** in its scope.
4. Restart the device so the system process can load the module.
5. Return to EdgeFlow, check the module diagnostics and run a harmless test action (for example, an explicitly configured navigation action).
6. Grant the Android overlay permission **only if** you want the floating shortcuts or another overlay feature.
7. Enable the particular feature in EdgeFlow; gesture regions and the floating bar should not become active without your choice.

If the app reports that system hooks are pending, recheck LSPosed enablement, scope and its logs. Reboot again after any scope change, and do not assume that reinstalling the APK will fix a framework incompatibility.

## Quick start

### Create an automation

1. Open **Automation** and create a saved task.
2. Name it and add steps, for example: **Open app → Wait → Tap → Back**.
3. Configure each step, including a timeout or failure handling where available.
4. Use the explicit test command before binding it to a global trigger.
5. Stop a task immediately if it interacts with the wrong app. Use the emergency-stop control when necessary.

### Bind a quick trigger

Choose a supported edge gesture, selected key, event, schedule or Quick Settings tile. Make sure the trigger does not conflict with Android system navigation. Game Mode and security restrictions take precedence.

## Floating ball and concentric ring menu

The floating shortcut bar is a single feature with two display modes. Existing installs keep the **list layout** unless the user explicitly switches to the ring layout.

### Configure

1. Open **Floating shortcuts**.
2. Enable the floating shortcut bar and grant **Display over other apps** if Android asks.
3. Select **Radial ring layout** (the original list layout remains available).
4. Keep **Two rings** for 12 slots, or enable **Three rings** for 18 slots.
5. Tap a ring sector in the configuration page to assign one saved task. You can also clear a sector.
6. Adjust the existing **handle size** (default **100%**), opacity and position; haptic sector feedback is enabled by default.
7. Check the on-page sector preview before interacting with the system overlay.

### Operate

| Gesture | Result |
| --- | --- |
| Tap the collapsed ball | Expand the ring menu with a spring animation |
| Long-press the ball and slide | Enter continuous sector selection |
| Slide across ring sectors | Highlight the sector under your finger; optionally feel a subtle vibration |
| Release over an assigned sector | Trigger the selected saved task and close the menu |
| Release in the center, in a gap or outside the menu | Cancel without executing |
| Touch outside / cancel a gesture | Dismiss the menu |
| Drag the collapsed handle | Move it along the screen; its side and vertical position are remembered |

The menu normally hides on the lock screen or while Game Mode is active, according to the existing switches. Layout may be constrained by very small displays, rotation or manufacturer-specific overlay rules. **Physical-device gesture accuracy and window edge behavior still require validation.**

**Backward compatibility:** Ring assignments have their own stable task-ID mapping. Any unchanged slot can fall back to the first existing pinned list shortcuts; clearing a slot explicitly prevents this fallback. Switching modes does not delete the old five-item list, saved tasks or the older Pie configuration.

## Safety, permissions and privacy

- **Local-first:** Task configuration is maintained locally and can be backed up or restored using the app's tools. Avoid putting sensitive credentials into automated text-input steps or shared backups.
- **Root / LSPosed:** Module hooks can interact with Android framework internals. Misconfiguration or OEM changes may affect navigation or input, so keep a reliable recovery path.
- **Unlocking:** Waking the display does not mean the device is unlocked. Secure PIN/password/pattern/biometric authentication is handled by Android; EdgeFlow must not silently bypass it.
- **Overlays:** An overlay needs the Android overlay permission. Disable the floating UI if it obscures authentication, another sensitive app, or system navigation.
- **System events:** Input interception is designed to pass through events that do not match enabled user bindings. Actual behavior must still be checked on the specific device.
- **Emergency recovery:** Disable the feature, stop running tasks, restore a configuration backup or temporarily disable the LSPosed module when needed.

## Architecture

```text
EdgeFlow/
├── app/
│   └── src/main/kotlin/com/edgeflow/
│       ├── hook/          # Android framework hooks and action dispatch
│       ├── service/       # OverlayHostService, floating bar, ring menu
│       ├── data/          # Configuration repository
│       └── ui/            # Compose UI and translated screens
├── core/
│   └── src/main/kotlin/com/edgeflow/core/
│       ├── model/         # Serialized actions and settings
│       └── ...            # Geometry, policies and shared logic
├── docs/                  # Scope, roadmap and quality checklist
├── README.md              # Default Chinese guide
├── README_EN.md           # This English guide
└── LICENSE                # MIT
```

The `core` module owns serialization models and pure logic, so geometry/policy tests can run as JVM unit tests. The `app` module contains both the UI and injected behavior.

The overlay host runs in the application process and forwards selected task IDs through the existing action-dispatch mechanism. The ring menu does **not** introduce a second action engine.

Old action identifiers and saved configurations must not be silently reassigned during migrations.

## Build and tests

Configure `local.properties` with a working Android SDK path (do not commit your local file). Use JDK 17:

```bash
# Linux / macOS
./gradlew :core:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug
```

```powershell
# Windows
.\gradlew.bat :core:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug
```

The resulting APK is usually:

```text
app/build/outputs/apk/debug/app-debug.apk
```

An authorized Android device can be used for installation:

```bash
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

**Signing:** The Debug APK uses a debug signing key. Release builds require your own signing configuration; without it, the release output can be **unsigned**. Never publish your release keystore or passwords in Git. A release signed with a different key cannot update an already installed copy without matching signing lineage.

## Troubleshooting

| Symptom | Check |
| --- | --- |
| Module inactive / system hook pending | LSPosed enabled? **System Framework** scope? Restart after changing scope; inspect framework logs |
| Ball does not appear | Enable the floating bar, allow Android overlay permission, and check lock-screen/Game Mode hiding |
| Ring opens but a sector does nothing | Assign an existing saved task; inspect whether it was deleted, disabled or rejected by the action engine |
| Gestures block system navigation | Turn off the problem gesture region, use emergency stop, then verify conflicts and logs |
| Wrong ring location after rotation | Reset handle position, collapse/reopen and retest; report device/OS details |
| Build fails with Java 8 | Use JDK 17 and set `JAVA_HOME` appropriately |
| Build warns about compile SDK 37 | Android Gradle Plugin compatibility warning; review toolchain compatibility separately from compilation success |

When reporting bugs, include Android version, model, LSPosed/module versions, steps to reproduce, expected behavior and sanitized logs. Do not attach passwords, tokens or personal content.

## Status and roadmap

- The ring menu implementation includes two-ring defaults, optional three-ring rendering, slot configuration, spring opening, haptic feedback and pure geometry tests.
- Debug compilation and JVM tests have passed in the development workspace. **This does not constitute a complete physical-device acceptance test** of every ring gesture, rotation, lock screen or system hook.
- Remaining work includes sustained device QA, OEM compatibility, accessibility improvements, performance profiling and beta release validation.

See [Product scope](docs/PRODUCT_SCOPE.md), [Development plan](docs/NEXT_PHASE_DEVELOPMENT_PLAN.md) and [Beta release checklist](docs/BETA_RELEASE_CHECKLIST.md). Older feature-alignment notes are historical references, not mandatory parity targets.

## License

EdgeFlow is released under the [MIT License](LICENSE).
