# 实现说明

本文记录 WeType Liquid Glass 当前版本的核心实现步骤、Hook 目标、真机验证方式和安全回退策略。

## 1. 模块入口与隔离

项目同时提供 Modern libxposed 与 Legacy Xposed 入口，但二者不共享任何框架 API 类型。

### Modern API 102

入口：

```text
com.wetype.liquid.hook.modern.WeTypeLibXposedEntry
```

流程：

1. `onPackageLoaded` 安装 `InputMethodService` 生命周期 Hook；
2. `onPackageReady` 使用目标 ClassLoader 安装微信输入法插件 Hook；
3. 使用 `XposedModule.hook(...).intercept(...)`；
4. HookHandle 安装成功后才记录 `INSTALLED`；
5. 回调首次命中后记录 `ATTACHED`。

Modern 调用链不引用 `de.robv.android.xposed.*`。

### Legacy API

入口：

```text
com.wetype.liquid.hook.legacy.WeTypeLegacyXposedEntry
```

仅 Legacy 目录引用 `XposedBridge` 与 `XC_MethodHook`。

## 2. InputMethodService 生命周期

安装以下系统类 Hook：

```text
InputMethodService.onWindowShown()
InputMethodService.onWindowHidden()
InputMethodService.onCreateInputView()
InputMethodService.setInputView(View)
InputMethodService.onConfigurationChanged(Configuration)
```

这些 Hook 用于：

- 捕获键盘根 View；
- 管理区域 Blur Surface 生命周期；
- 应用或恢复键盘面板背景；
- 响应深色/浅色模式；
- 在隐藏键盘时释放 Surface 和 RenderEffect。

## 3. 真实键盘区域定位

在已验证的 WeType 3.5.3 / OriginOS 15 环境中，IME Window 是近乎全屏的透明窗口：

```text
DecorView                      1216 × 2520
└── parentPanel               1216 × 2520
    ├── fullscreenArea        1216 × 1553
    └── inputArea             1216 × 967, y=1553
        └── ConstraintLayout  1216 × 967
            └── ImeRootView
```

因此不能对整个 Window 使用 `FLAG_BLUR_BEHIND`。

## 4. 区域 Surface 毛玻璃

核心类：

```text
RegionalSurfaceBlurController
```

实现流程：

1. 优先选择 InputView 的系统 `FrameLayout inputArea` 父容器；
2. 以 `1px` 高度在容器底部插入 `SurfaceView`，避免它参与首次测量并把 `inputArea` 撑成全屏；
3. 等 WeType InputView 完成布局后，将 SurfaceView 高度更新为真实键盘高度；
4. `SurfaceView` 始终使用 `Gravity.BOTTOM`，触控和可访问性均关闭；
5. 取得 `SurfaceView.surfaceControl`；
6. 通过受保护反射调用：

```text
SurfaceControl.Transaction.setBackgroundBlurRadius(surfaceControl, radiusPx)
SurfaceControl.Transaction.setCornerRadius(surfaceControl, cornerRadiusPx)
```

7. 在 Surface buffer 中绘制冷灰色透明 Tint；
8. 模糊 API 不存在、被 ROM 阻止或 Surface 无效时，立即移除该 SurfaceView。

真机 SurfaceFlinger 验证示例：

```text
SurfaceView[InputMethod]
bounds=(0,1673)-(1216,2640)
backgroundBlurRadius=77
```

### 为什么不用 FLAG_BLUR_BEHIND

`FLAG_BLUR_BEHIND` 会模糊 IME Window 后面的整个应用。当 WeType Window 接近全屏时，输入框和已输入内容也会被模糊。

`BlurController` 在每次应用视觉效果前都会主动清除：

```text
FLAG_BLUR_BEHIND
blurBehindRadius
Window backgroundBlurRadius
```

只有键盘区域的独立 Surface 可以启用 background blur。

## 5. WeType 类发现

已验证的关键类：

```text
com.tencent.wetype.plugin.hld.keyboard.selfdraw.n
    Keyboard View / Canvas 绘制宿主

com.tencent.wetype.plugin.hld.keyboard.selfdraw.j
    ImeButton 数据对象

com.tencent.wetype.plugin.hld.keyboard.selfdraw.drawmethod.c
    通用键帽背景绘制实现

com.tencent.wetype.plugin.hld.candidate.ImeCandidateView
    候选栏
```

### ImeButton 特征

`selfdraw.j` 不是 Android View。它通过方法签名识别：

- 至少两个无参 String 返回方法；
- 至少一个 `(String) -> void` 方法；
- 不继承 View。

### Keyboard View 评分

候选类根据以下特征评分：

- View / ViewGroup 子类；
- `onDraw(Canvas)` / `dispatchDraw(Canvas)`；
- Paint / Drawable / Rect / RectF 字段；
- MotionEvent 处理；
- ImeButton 数组或集合；
- 目标包名前缀。

只有达到阈值且存在 Canvas 绘制方法的类才安装 Hook。

## 6. 逐键背景替换

当前版本直接 Hook：

```text
drawmethod.c.e(Canvas, ImeButton)
```

WeType 的通用 DrawMethod 调用顺序为：

```text
a(Canvas, ImeButton)
├── e(Canvas, ImeButton)   # 背景、边框、阴影、按压遮罩
├── b(Canvas, ImeButton)   # 文字或图标
├── f(Canvas, ImeButton)   # 其他按压/装饰逻辑
└── debug overlay
```

模块只替换 `e(...)` 的背景绘制，并继续执行原始文字、图标和输入行为。

每个键的视觉边界来自：

```text
ImeButton.t() -> Rect
```

视觉 Rect 会向内部缩进，但不会修改 ImeButton 的原始 Rect，所以 hitbox 保持不变。

### 离屏 Canvas 密度问题

部分 DrawMethod 先将键帽画入 Bitmap Canvas，再把 Bitmap 合成到 Keyboard View。Bitmap Canvas 的 `density` 可能为 0。

错误实现会得到：

```text
density = 0
radiusDp * density = 0
```

表现为所有圆角、边框和阴影退化为 0。

当前实现优先读取 Canvas density；值无效时回退到 `InputMethodService.resources.displayMetrics.density`。

## 7. 键帽分类与材质

按键分类同时考虑文字和尺寸：

- `空格` / `space` -> SPACE；
- `搜索`、`换行`、`完成`、`发送` 等 -> ACTION；
- `重输`、`删除`、`123`、`符号` 等 -> FUNCTIONAL；
- T9 的 `ABC`、`DEF` 等宽字母组仍为 NORMAL；
- 无文字时才使用宽高比回退判断。

材质层级：

```text
NORMAL      高不透明白色圆角 Surface
SPACE       白色宽 Surface
FUNCTIONAL  中性浅灰 Surface
ACTION      更深的中性灰 Surface
```

绘制顺序：

```text
轻微底部阴影
圆角填充
顶部弱高光
细内边框
原始文字 / 图标
```

## 8. 候选栏、工具栏和标点列

- 候选栏与父容器背景透明化；
- 工具栏图标递归设置统一透明度；
- 键盘面板在候选区底部绘制 0.5dp 分隔线；
- T9 左侧标点 RecyclerView 在子项完成布局后分两次补充独立圆角键帽背景；
- 所有视觉背景使用 `HookStateRegistry` 备份，停用时可恢复。

## 9. 性能策略

- 热路径不执行 Binder、ContentResolver 或 JSON 解析；
- Paint、Path、RectF、Shader 均缓存；
- Hook Method、Field 和键类型结果缓存；
- ImeButton 类型使用 WeakHashMap，避免阻止对象回收；
- Surface blur 只在创建、尺寸变化或配置变化时提交 Transaction；
- DEX 扫描结果按进程缓存。

## 10. 构建与测试

完整验证命令：

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug --rerun-tasks
```

当前覆盖包括：

- ClassScorer；
- ModuleConfig 序列化与预设；
- ColorResolver；
- BlurSafetyPolicy；
- KeycapRenderer 分类与 hit detection；
- HookStateRegistry；
- Hook 状态机；
- Provider 安全策略；
- Remote diagnostics JSON。

## 11. 真机诊断

查看模块日志：

```bash
adb logcat -d -v time -s 'WeTypeLiquidGlass:*'
```

检查是否命中逐键绘制：

```text
First hook hit: Modern_DrawMethod_e_c
```

检查 IME Window 是否错误启用了全屏 Blur：

```bash
adb shell dumpsys window windows | grep -A 12 InputMethod
```

不应出现：

```text
BLUR_BEHIND
blurBehindRadius > 0
```

检查局部 Surface blur：

```bash
adb shell su -c 'dumpsys SurfaceFlinger' | grep -C 3 backgroundBlurRadius
```

## 12. 后续工作

- 使用 libxposed RemotePreferences 替代目标进程不可见的 ContentProvider 配置通道；
- 将诊断数据迁移到 libxposed Service 或可靠的模块远程文件；
- 为更多微信输入法版本维护签名与特征适配；
- 对 QWERTY、手写、符号和横屏布局进行独立真机验收；
- 增加区域 blur backend 的设备兼容列表。
