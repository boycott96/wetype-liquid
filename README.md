# WeType Liquid Glass

为官方微信输入法（`com.tencent.wetype`）提供局部毛玻璃底板与浮动圆角键帽的 Vector / Xposed 模块。

项目不会修改、重签名或重新打包微信输入法 APK。所有视觉变化均在目标进程内通过运行时 Hook 完成，停用模块并重启输入法后即可恢复原始界面。

## 当前状态

已在以下环境完成真机验证：

- Android 15 / OriginOS 15
- 微信输入法 3.5.3（versionCode `56201`）
- Vector Framework，Modern libxposed API 102
- 设备 SurfaceFlinger 支持 background blur

真机验证结果：

- Modern API 102 模块入口正常加载；
- `drawmethod.c.e(Canvas, ImeButton)` 逐键背景 Hook 正常命中；
- 局部 Surface 模糊层仅覆盖键盘区域；
- SurfaceFlinger 报告 `backgroundBlurRadius=77px`；
- 输入法 Window 不再使用会模糊整个宿主应用的 `FLAG_BLUR_BEHIND`；
- 27 个单元测试通过，Android Lint 为 0 errors。

## 主要功能

### 键盘区域局部毛玻璃

微信输入法在部分 ROM 上使用近乎全屏的透明 IME Window。直接设置 Window Blur 会把输入框和已输入内容一起模糊。

本模块改为：

1. 找到系统 `inputArea` 的 `FrameLayout`；
2. 在该容器底层插入与真实键盘区域等大的 `SurfaceView`；
3. 对其 `SurfaceControl` 设置 background blur；
4. 在模糊层上叠加冷灰色透明 Tint、圆角、高光和分隔线；
5. 模糊不可用或隐藏 API 调用失败时，自动移除 Surface 并回退到安全的半透明底板。

该实现不会对全屏 IME Window 设置 `FLAG_BLUR_BEHIND`。

### 浮动玻璃键帽

- 普通键：高不透明白色圆角 Surface；
- 功能键：中性浅灰 Surface；
- Action 键（搜索、换行、发送等）：更深一级的灰色层级；
- 键帽视觉边界向 hitbox 内缩，但不改变真实触控范围；
- 细白边、顶部高光、轻微环境阴影；
- 轻量按压缩放和透明度反馈；
- T9 字母组、空格、标点列和功能键按文字及尺寸分类。

### 候选栏与工具区

- 移除候选栏和工具栏不透明底板；
- 候选区、工具区和键盘共用同一块玻璃面板；
- 工具图标统一透明度；
- 候选区与键区之间使用极细分隔线；
- 保留微信输入法原始候选内容、输入逻辑和布局。

### 稳定性保护

- Hook 作用域严格限制为 `com.tencent.wetype`；
- Modern libxposed 与 Legacy Xposed 实现完全隔离；
- Hook 安装和回调使用 protective exception mode；
- 所有被修改的 View / Window 状态均有备份和恢复路径；
- 不支持区域模糊的 ROM 自动使用半透明 Surface；
- 不修改按键 hitbox、字符映射、候选词或拼音逻辑。

## 架构

```text
app/src/main/java/com/wetype/liquid
├── hook
│   ├── modern/                 # 纯 libxposed API 102 实现
│   └── legacy/                 # 传统 XposedBridge 兼容实现
├── core
│   ├── HookCallbackDispatcher  # API 无关的 Hook 回调与视觉调度
│   └── HookStateRegistry       # View / Window 原始状态备份与恢复
├── glass
│   ├── RegionalSurfaceBlurController
│   ├── BlurController
│   ├── GlassDrawable
│   ├── GlassKeyDrawable
│   ├── GlassRenderer
│   ├── KeycapRenderer
│   └── ColorResolver
├── discovery
│   ├── ClassFinder
│   ├── ClassScorer
│   ├── MethodFinder
│   ├── HookDiagnostics
│   └── ViewTreeScanner
├── config
│   ├── ModuleConfig
│   ├── ConfigBridge
│   └── WeTypeConfigProvider
└── ui                         # Compose Material 3 设置界面
```

现代模块入口：

```text
META-INF/xposed/java_init.list
com.wetype.liquid.hook.modern.WeTypeLibXposedEntry
```

Legacy 兼容入口：

```text
assets/xposed_init
com.wetype.liquid.hook.legacy.WeTypeLegacyXposedEntry
```

作用域：

```text
com.tencent.wetype
```

## 构建

环境要求：

- JDK 17+
- Android SDK 35
- 网络可访问 Google Maven、Maven Central 和 Xposed Maven

执行完整质量门禁：

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Debug APK 输出路径：

```text
app/build/outputs/apk/debug/app-debug.apk
```

只构建 APK：

```bash
./gradlew assembleDebug
```

## 安装与启用

1. 安装生成的 APK；
2. 在 Vector Framework 中启用模块；
3. 作用域只选择微信输入法 `com.tencent.wetype`；
4. 强制停止微信输入法，或重启其进程；
5. 呼出键盘验证效果。

ADB 示例：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am force-stop com.tencent.wetype
adb shell ime set com.tencent.wetype/.plugin.hld.WxHldService
```

部分 OriginOS 版本会显示“继续安装”确认页。Root 设备也可使用：

```bash
adb push app/build/outputs/apk/debug/app-debug.apk /data/local/tmp/wetype-liquid.apk
adb shell su -c 'pm install -r /data/local/tmp/wetype-liquid.apk'
```

## 实现步骤

详细的 Hook 点、Surface 层级、键帽绘制流程、兼容策略和诊断方法见：

- [实现说明](docs/IMPLEMENTATION.md)

## 已知限制

1. 当前逐键适配以微信输入法 3.5.3 的 `selfdraw` 架构为主要验证目标；新版微信输入法可能需要更新特征发现规则。
2. 区域 Surface blur 使用 Android 隐藏 API，并依赖 ROM 的 SurfaceFlinger 实现；失败时会自动降级。
3. Android `adjustResize` 会让宿主应用内容停在键盘上方，因此在纯白背景应用中，真实背景模糊的视觉变化可能较弱。
4. 目标进程目前可能因 Android 包可见性限制无法解析模块 ContentProvider，设置实时同步及远程诊断仍需迁移到 libxposed RemotePreferences / Service。
5. 模块保留微信输入法原始 T9 / QWERTY 布局和 hitbox，不复制 iOS 键盘的按键排列。

## 安全提示

这是需要 Root 与 Xposed/Vector 环境的实验性模块。安装前请确认：

- 已准备可恢复的 Root / Zygisk 环境；
- Vector Framework 工作正常；
- 只为微信输入法启用作用域；
- 遇到异常时可在框架中停用模块并重启目标进程。

## License

[Apache License 2.0](LICENSE)
