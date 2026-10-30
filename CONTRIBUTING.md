# Media Optimizer 贡献者指南

感谢你对 Media Optimizer 的关注！这是一款开源 Android 应用，Apache-2.0 许可，无广告、无内购、完全免费。

## 项目简介

Media Optimizer 解决手机里图片格式混杂、占用空间大的问题。核心功能：批量将照片转码为 HEIF/WebP/JPEG，保留 EXIF 拍摄时间与 GPS 地点，输出聚合到 `Pictures/MediaOptimizer`，原文件不动。

仓库：https://github.com/MarvinDi/media-optimizer

## 开发环境

- JDK 17
- Android SDK（当前沙箱无本地 SDK，构建与测试依赖 GitHub Actions CI）
- 任意支持 Kotlin 的 IDE（Android Studio、IntelliJ IDEA、VS Code 等）

## 构建命令

| 命令 | 说明 |
|------|------|
| `./gradlew assembleDebug` | 构建 Debug APK |
| `./gradlew detekt` | 运行 detekt 静态检查 |
| `./gradlew ktlintCheck` | 运行 ktlint 代码风格检查 |
| `./gradlew testDebugUnitTest` | 运行单元测试 |
| `./gradlew lintDebug` | 运行 Android Lint |

## 提交规范

- 使用 `type(scope): subject` 格式，例如 `feat(repo): add detekt config`
- 原子提交：一次只修一个问题
- 提交信息带 Why
- **不提交任何密钥/secrets**
- 不修改 `HANDOFF.md`（保持未跟踪）

## 代码约定

- 缩进：4 个空格
- 行长：最多 120 字符
- 编码：UTF-8，LF 换行符
- 遵循 `AGENTS.md` 中的所有约定

## 关键产品决策（勿推翻）

1. 开源 + 免费无广告
2. 首版不做“自动替换源文件”（V2 才做，且可选+确认）
3. 首版只做图片，不做视频转码
4. EXIF 保留必须默认开启：DATETIME_ORIGINAL、GPS_*、MAKE/MODEL、ORIENTATION
5. 不申请 `MANAGE_EXTERNAL_STORAGE`；选图用 PhotoPicker，输出目录用 `ACTION_OPEN_DOCUMENT_TREE`
6. 承诺用语：“肉眼不可见差别”，不能写“无损压缩”

## 提交 PR 流程

1. Fork 仓库并创建特性分支
2. 在分支上进行开发，确保 `./gradlew detekt`、`./gradlew ktlintCheck`、`./gradlew assembleDebug` 均通过
3. 提交变更（遵循提交规范）
4. 发起 Pull Request，填写 PR 模板
5. 等待维护者审查与合并

## 发布

签名密钥用本地 `keytool` 生成，**密钥绝不入库**。Release 由维护者在 GitHub 发布，附 APK。

## 行为准则

请保持友善、建设性的讨论氛围。所有贡献均遵循 Apache-2.0 许可证。
