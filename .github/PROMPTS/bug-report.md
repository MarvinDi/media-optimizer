# Bug 修复 — AI 提示词模板

本模板用于在 Media Optimizer 项目中报告和修复 Bug，供 AI 编程助手辅助调试。

## Bug 描述
> 请描述异常现象、触发条件、预期行为与实际行为。

## 复现步骤
1.
2.
3.

## 涉及文件（预估）
> 列出可能需要修改的文件路径。

## 调试建议
- 优先检查 `ExifHelper.kt` 中的 EXIF 读写逻辑
- 检查 `ImageRepository.kt` 中的编码回退路径（HEIF → JPEG）
- 检查 `CompressViewModel.kt` 中的异常捕获与进度更新
- 如涉及 MediaStore，检查 `publish()` 方法的 `IS_PENDING` 流程

## 代码约定
- 遵循 `AGENTS.md` 中的 Kotlin 风格
- 提交信息使用 `type(scope): subject` 格式，带 Why
- 不提交任何密钥或敏感信息

## 验收标准
- [ ] Bug 复现后已修复，不再出现
- [ ] 未引入新的静态检查警告（detekt/ktlint）
- [ ] `./gradlew assembleDebug` 在 CI 中通过
- [ ] 如有单元测试覆盖，测试通过
