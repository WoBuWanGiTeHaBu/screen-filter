# 项目长期记忆 · screen-filter（屏幕滤镜）

## 这是什么

Android 全屏色彩滤镜 App。用户在页面里调颜色 / 灰度 / 不透明度 / 亮度，点应用后
整块屏幕（除状态栏）蒙上该颜色，触摸穿透不挡操作。

## 渲染模型（核心，改错就白干）

**两层叠加**，`out = (1 − d) · [ (1 − t) · content + t · color ]`
- 着色层：颜色 = `HSV(hue, 1−gray/100, 1−0.5·gray/100)`，透明度 `t = strength/100`。
  **纯黑被抬到 `t × 颜色亮度`，这是黑位抬升的唯一来源**。
- 压暗层：纯黑，透明度 `d = 1 − brightness/100`。纯黑叠加 = 整幅乘系数，不伤对比度。

单层彩色蒙层数学上必然抬黑（`content=0` 时 `out = t·color`），调参数救不了 —— 别再退回单层。
悬浮窗是**单窗口两层**：一个 View + `TwoLayerOverlay : Drawable`，`onDraw` 里两次
`canvas.drawColor`。不要改成挂两个全屏窗口。

## 固定约定（改代码前先读）

- **包名**：`com.shen.screenfilter`；debug 变体加 `.debug` 后缀，可与正式包共存。
- **架构**：MVVM。`FilterRepository`（object）是 App 与悬浮窗服务之间**唯一**的状态源，
  用 StateFlow 暴露 `config` / `customPresets` / `livePreview`。
  想改悬浮窗参数就走仓库，**不要**用 Intent 传。
- **两套状态**：`FilterViewModel.draft`（界面正在拧的值）vs `FilterRepository.config`
  （已下发的值）。「实时预览」开关决定两者是否同步。改按钮语义时务必同时看这两者。
- **参数四件套**：`hue / gray / strength / brightness`（旧 `alpha` 已废弃，改名 strength）。
  HSV 计算全部收在 `FilterConfig.saturation` / `.value` / `tintColor` 派生属性里，
  别在 UI 层散落。
- **minSdk 26**：可以直接用 `TYPE_APPLICATION_OVERLAY`、`NotificationChannel`，
  不用写兼容分支。

## 已知边界（不要试图绕过，会被写进 README）

- 状态栏 / 导航栏不会被染色——`TYPE_APPLICATION_OVERLAY` 层级低于系统关键窗口。
- 「灰度」是叠中性色洗淡画面，**不是**真去饱和。真灰度需 root 或 `WRITE_SECURE_SETTINGS`。
- 「亮度」只能往下压，不能往上提（蒙层是叠加，不是相乘提亮）。
- 划掉最近任务时滤镜保留（设计选择，不是 bug）。

## 构建

见 `.workbuddy/memory/2026-09-21.md`。一句话：Git Bash 跑缓存里的 POSIX `gradle` 脚本，
`run_in_background` + 日志重定向，首次约 17 分钟、增量约 2 分钟。改完 `app/build.gradle.kts`
**必须注意**：`java.util.Properties()` 在该文件里编译不过，要先在顶部 `import java.util.Properties`。

## 签名与产物

- **正式 keystore**（不可再生，已备份提醒）：`C:\Users\shengqi\android-keys\screen-filter-release.keystore`，
  PKCS12 / RSA 2048 / alias `screenfilter`；密码在工程根 `keystore.properties`（已 gitignore）。
- `app/build.gradle.kts` 用顶部 `cred()` 读取凭据（环境变量优先），**缺凭据只降级不报错**。
- 产物命名：debug → `screen-filter-debug.apk`（包名带 `.debug`）；正式 → `screen-filter-<version>-release.apk`。
- 验证工具：`aapt2.exe` 直接跑；**`apksigner` 只有 `.bat`（本机 cmd.exe 被禁），必须
  `java -jar $SDK/build-tools/36.0.0/lib/apksigner.jar verify --print-certs`**。

## 宣传物料

`poster/xiaohongshu-1242x1656.html`（源）+ `.png`（成品）。手机 mockup 是按真实布局和
`previewColor` 真实计算值画的 —— **改参数/预设后海报要跟着改**，别让两者对不上。
