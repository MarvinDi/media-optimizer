# Media Optimizer — AI 代理指令文件 (AGENTS.md)

本文件供 Cursor、Cline、Trae 等 AI 编程助手阅读，用于了解项目结构、规范与约定。
请在生成或修改代码前先阅读本文件。

---

## 1. 项目概述

**Media Optimizer** —— 开源 Android 应用，Apache-2.0 许可，无广告、无内购、完全免费。

核心功能：批量将照片转码为 HEIF/WebP/JPEG，保留 EXIF 拍摄时间与 GPS 地点，输出聚合到 `Pictures/MediaOptimizer`，原文件不动。

仓库：https://github.com/MarvinDi/media-optimizer  
包名：`com.marvindi.mediaoptimizer`  
minSdk：29（以 `app/build.gradle.kts` 为准），targetSdk：35

---

## 2. 技术栈

- 语言：Kotlin 1.8 (JVM target 1.8)
- UI：Jetpack Compose 1.6.3 + Material3 1.2.0
- 架构：Activity → Compose UI → ViewModel → Repository → Util
- 依赖注入：Hilt（声明中提及，实际当前未在代码中广泛使用）
- 图片加载：Coil 2.6.0
- EXIF 读写：AndroidX ExifInterface 1.3.7
- HEIF 编码：AndroidX HeifWriter 1.0.0
- 测试：JUnit 4.13.2（声明中，当前无测试用例）

---

## 3. 构建命令（纯 JVM，无需 Android SDK）

| 命令 | 说明 |
|------|------|
| `./gradlew assembleDebug` | 构建 Debug APK |
| `./gradlew detekt` | 运行 detekt 静态检查 |
| `./gradlew ktlint` | 运行 ktlint 代码风格检查 |
| `./gradlew testDebugUnitTest` | 运行单元测试 |
| `./gradlew lintDebug` | 运行 Android Lint |

> 当前沙箱无 Android SDK，构建与测试依赖 GitHub Actions CI。

---

## 4. 代码约定

### 提交信息
遵循 `type(scope): subject` 格式，例如：
- `feat(repo): add detekt config`
- `fix(ui): correct theme fallback`
- `docs: update AGENTS.md`

原则：原子提交（一次只修一个问题）、信息带 Why、**不提交任何密钥/secrets**。

### Kotlin 风格
- 缩进：4 个空格
- 行长：最多 120 字符
- 文件末尾换行
- 编码：UTF-8，LF 换行符

### 关键产品决策（勿推翻）
1. 开源 + 免费无广告
2. 首版不做“自动替换源文件”（V2 才做，且可选+确认）
3. 首版只做图片，不做视频转码
4. EXIF 保留必须默认开启：DATETIME_ORIGINAL、GPS_*、MAKE/MODEL、ORIENTATION
5. 不申请 `MANAGE_EXTERNAL_STORAGE`；选图用 PhotoPicker，输出目录用 `ACTION_OPEN_DOCUMENT_TREE`
6. 承诺用语：“肉眼不可见差别”，不能写“无损压缩”

---

## 5. 架构概览

```
MainActivity (入口，Compose 设置)
  └── MainScreen (UI：选图/格式/质量/EXIF/进度/开始)
        └── CompressViewModel (StateFlow 状态 + 协程批量处理)
              └── ImageRepository (解码→编码→ExifHelper→MediaStore 发布)
                    └── ExifHelper (EXIF 标签复制、JPEG EXIF block 提取、Orientation 重置)
```

### 各文件职责

| 文件 | 职责 |
|------|------|
| `MainActivity.kt` | Compose 入口，创建 ViewModel |
| `ui/MainScreen.kt` | PhotoPicker（`PickVisualMedia`）、格式下拉、质量滑块、EXIF 开关、转换按钮、进度条 |
| `viewmodel/CompressViewModel.kt` | 状态持有 + 批量转换协程 + 进度 + 结果汇总 |
| `repository/ImageRepository.kt` | 核心：`ImageDecoder` 解码 → HEIF/JPEG/WebP 编码 → `MediaStore` 发布到 `Pictures/MediaOptimizer` |
| `util/ExifHelper.kt` | 复制关键 EXIF 标签；HEIF 用原始 EXIF block 嵌入；JPEG/WebP 用 `ExifInterface` 复制 |
| `model/CompressResult.kt` | 转换结果数据类（源 URI、输出 URI、原大小、新大小、节省大小） |
| `ui/theme/` | 颜色、主题、字体 |

---

## 6. 常见任务

### 添加新输出格式
1. 在 `OutputFormat` enum（`model/CompressResult.kt`）中添加新条目（displayName, mimeType）
2. 在 `ImageRepository.encode()`（`repository/ImageRepository.kt`）中添加 `when` 分支
3. 在 `ImageRepository.buildOutputName()` 中添加扩展名映射
4. 在 `MainScreen.kt` DropdownMenu 中会自动显示（遍历 `OutputFormat.entries`）

### 修改 EXIF 标签集
编辑 `ExifHelper.COPY_TAGS`（`util/ExifHelper.kt`），添加或移除 `ExifInterface.TAG_*` 条目。

### 调整输出文件夹
修改 `ImageRepository.OUTPUT_FOLDER` 常量（当前值 `"MediaOptimizer"`），输出至 `Pictures/<OUTPUT_FOLDER>`。

---

## 7. CI 流程

- `push` 到 `origin/main` 触发 `.github/workflows/android.yml`
- CI 步骤：JDK17 → `assembleDebug` → `detekt` → `ktlint` → `lintDebug`
- APK 产物：GitHub Actions Artifacts `app-debug-apk`（约 7.9 MB）
- CI 红了：查看 Actions 日志 → 修复 → 新提交（不改写历史）

---

## 8. 发布流程

1. 在本地用 `keytool` 生成签名密钥（**密钥绝不入库**）
2. `app/build.gradle.kts` 的 `release` 构建类型从环境变量读取密钥库路径与密码
3. 构建 Release APK，创建 GitHub Release v1.0.0，附 APK
4. 参照 HANDOFF.md 待办事项 #3

---

## 9. 禁忌 / 限制

- ❌ 不申请 `MANAGE_EXTERNAL_STORAGE` 高危权限
- ❌ 首版不做视频转码
- ❌ 不写“无损压缩”，使用“肉眼不可见差别”
- ❌ 不提交 `HANDOFF.md`（保持未跟踪）
- ❌ 不提交任何密钥、keystore、`local.properties`、`secrets.properties`
