# 新功能提交 — AI 提示词模板

本模板用于在 Media Optimizer 项目中提交新功能需求，供 AI 编程助手生成实现方案。

## 功能描述
> 请描述你要添加的功能，包括用户交互流程和预期结果。

## 涉及文件（预估）
> 列出可能需要修改的文件路径，例如：
> - `app/src/main/java/com/marvindi/mediaoptimizer/repository/ImageRepository.kt`
> - `app/src/main/java/com/marvindi/mediaoptimizer/ui/MainScreen.kt`
> - `app/src/main/res/values/strings.xml`

## 代码约定
- 遵循 `AGENTS.md` 中的 Kotlin 风格（4 空格缩进，行长 ≤ 120）
- 提交信息使用 `type(scope): subject` 格式
- 不修改 `HANDOFF.md`（保持未跟踪）
- 不提交任何密钥或敏感信息

## 关键产品决策（勿推翻）
1. 首版只做图片，不做视频转码
2. EXIF 保留默认开启，不申请 MANAGE_EXTERNAL_STORAGE
3. 原文件永不删除（自动替换源文件不进首版）

## 验收标准
- [ ] 功能逻辑正确实现
- [ ] `./gradlew detekt` 通过
- [ ] `./gradlew ktlint` 通过
- [ ] `./gradlew assembleDebug` 在 CI 中通过
- [ ] 如涉及 UI 变更，更新对应字符串资源（中文 + 英文）
