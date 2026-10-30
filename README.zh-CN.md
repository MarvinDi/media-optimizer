# Media Optimizer

Android 应用，批量将照片转码并压缩为现代格式（HEIF/HEIC、WebP、JPEG），保留 EXIF 元数据（拍摄时间、GPS 位置、相机信息），并将结果汇总到同一个相册文件夹。

- **开源**（Apache-2.0），免费，无广告，无内购
- **100% 本地处理** - 照片永不离开你的设备
- **零权限** - 使用系统照片选择器，无需存储访问权限
- **原文件永不删除** - 先核对结果，再手动删除原文件
- 中文 / English（跟随系统语言）

## 为什么需要它

手机里充斥着格式各异的照片：相机拍摄、SD 卡导入、网页下载……分布在不同文件夹，占用大量空间。以高质量重新编码为 HEIF/WebP 通常可缩小 30-50% 的文件体积，且肉眼不可见质量损失。

> 注意：重新编码始终是有损的。目标是**肉眼不可见的质量损失**，而非数学意义上的无损压缩。

## 功能

- 通过系统照片选择器批量选择照片（无需任何权限）
- 转码为 **HEIF**（使用 `HeifWriter`，设备不支持时自动回退 JPEG）、**JPEG** 或 **WebP**
- 可调节质量滑块（60-100）
- **EXIF 保留**：拍摄时间、GPS 位置、相机/镜头型号会被复制到转码后的文件，照片在相册时间线和地图上的位置保持不变
  - JPEG / WebP 输出：通过 `ExifInterface` 完整复制 EXIF
  - HEIF 输出：JPEG 来源的原始 EXIF 块在编码时嵌入（覆盖相机拍摄、SD 卡导入、大多数下载）；对于 HEIC/PNG/WebP 来源，文件级 EXIF 无法重新写入 HEIF，相册时间线仍通过 MediaStore 时间戳匹配，但文件级 GPS 可能不会被携带
- 转码后的照片写入单个聚合文件夹：`Pictures/MediaOptimizer`
- MediaStore 时间戳与原始拍摄时间对齐
- 双语界面（英文 / 简体中文）
- 深色模式跟随系统

## 构建

### 要求

- Android Studio（最新稳定版）
- JDK 17
- Android SDK 35

### 步骤

```bash
git clone https://github.com/MarvinDi/media-optimizer.git
cd media-optimizer
```

在 Android Studio 中打开项目，等待 Gradle 同步完成，然后：

**Build > Build Bundle(s) / APK(s) > Build APK(s)**

APK 输出：`app/build/outputs/apk/debug/app-debug.apk`

每次推送到 `main` 分支时，GitHub Actions 也会自动构建 debug APK，并附加到工作流运行的工件中。

## 使用方法

1. 点击 **选择照片**，挑选一张或多张照片（系统选择器也允许从多个相册/文件夹选择）
2. 选择输出格式（推荐 HEIF）和质量
3. 保持 **保留拍摄时间与位置（EXIF）** 开启（默认开启），以便时间线/地图视图正确
4. 点击 **转换**，等待进度完成
5. 打开相册，查看 `Pictures/MediaOptimizer` 并与原图对比
6. 满意后，手动删除原文件

## 路线图

- [ ] 视频转码（H.265/HEVC 批量压缩）
- [ ] 带备份/撤销窗口的可选“替换原文件”流程
- [ ] 一次扫描并聚合来自多个源文件夹
- [ ] JPEG XL / AVIF 输出（等设备支持足够广泛时）

## 隐私

- 无广告、无分析、无追踪、无任何网络调用
- 不请求存储权限；文件访问仅通过系统照片选择器，以及应用通过 MediaStore 创建的文件

## 许可证

[Apache-2.0](LICENSE)
