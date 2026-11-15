# 解码与任务恢复的设备验证

连接测试设备后，在项目根目录执行小型回归：

```bash
JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.example.video2gif.media.MediaPipelineTest,com.example.video2gif.media.PublicationRecoveryTest
```

`MediaPipelineTest` 检查顺序取帧、回退定位、旋转、编码中取消后的临时文件清理，以及 RunStore 重建。它使用测试 APK 私有目录和任务存储，不覆盖用户任务。

`PublicationRecoveryTest` 需要 API 29+，在 `Pictures/Video2Gif` 创建测试条目并在 finally 删除，检查保存失败清理、重试发布、完整/未完整复制检查点恢复与恢复幂等性。执行时应用不能正在转换。报告在 `app/build/reports/androidTests/connected/`。

合成素材 `app/src/androidTest/assets/codec-sdr.mp4` 用于轻量回归，可按以下命令重建：

```bash
ffmpeg -f lavfi -i 'testsrc2=size=96x64:rate=12:duration=1' \
  -c:v libx264 -pix_fmt yuv420p -g 12 -bf 2 \
  app/src/androidTest/assets/codec-sdr.mp4
```

## 系统级故障需要单独验证

自动化模拟了故障和快照恢复，下面这些条件需要在测试设备上触发。使用可丢弃的短视频和专用测试批次，记录设备、API、触发阶段及结果；不要通过填满用户设备磁盘制造故障。

| 条件 | 触发与观察 |
|---|---|
| 编码中取消 | 批次转换过程中取消当前/全部；确认当前临时文件消失、成功项保留、队列按选择继续或停止 |
| 发布中取消 | 用较大成品观察 Publishing 时取消；确认 pending 清理、没有损坏的可见 GIF |
| FGS 超时 | 在支持测试超时命令的 Android 15+ 测试设备上按平台说明缩短 mediaProcessing 超时，记录原设置并在测试后恢复；确认回调后状态保持“已中断”、服务及时退出、完整临时文件可重试保存 |
| 进程退出 | 分别在 Encoding、Publishing、刚出现相册文件时终止测试应用进程并重启；记录成功项、临时文件与目标 URI，确认不会重复发布 |
| 存储/提供方失败 | 本地 JVM 已注入分块 ENOSPC 和 IO 失败；设备侧通过测试提供方或独立受限测试卷验证，不修改真实用户存储 |
| 硬件与颜色 | 用已有独立集验证 VFR、各方向、HDR/SDR；比较相同目标时刻的预览与 GIF 合成帧，单列预览缩小的情况 |
| 内存与帧耗时 | 先运行一段 4K 输入及紧裁剪，记录进程峰值、解码/转换耗时；据此决定是否下调 2560px 上限，不把对象预算当成实测峰值 |

当前状态：以上设备测试尚未执行。需要用户连接可用设备，并确认系统级注入所用的测试设备和批次；不自动扩大为大规模素材实验。
