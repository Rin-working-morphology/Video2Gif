# Video2Gif

换成安卓机失去iPhone的预设指令后就老是要去找在线转Gif的网站才能做表情包，下的apk又总是要插入广告或者要注册才能用。怒而自己开发之。

做都做了索性就做得好看一点，所以这是一个还算好看的视频转Gif应用。界面为 Jetpack Compose + Material 3。为什么用Kotlin因为我上次写Kotlin还是若干年前突然想复健一下（突然老气横秋！）

## 下载

正式版从 [GitHub Releases](https://github.com/Rin-working-morphology/Video2Gif/releases/latest) 下载 `Video2Gif-<版本>.apk`。

## 已实现

- **导入**：系统 Photo Picker，单批 1–10 个视频；读取真实时长、显示尺寸、方向与缩略图；Photo Picker 不可用时由系统文件选择器回退。
- **无编码效果预览**：同一份 `ConversionRecipe`、同一 Wu 调色板与有序抖动驱动预览和导出；支持原视频/效果对照、按目标帧率播放、暂停、拖动定位；预览阶段不创建 GIF、不调用编码器。
- **参数**：画质（省空间/均衡/更细腻）、颜色数、抖动、帧率、长边尺寸、截取区间、裁剪取景框、旋转、速度、循环次数。常用项直接可见，专业项在高级设置中。
- **体积估算**：两阶段估算；分析阶段在实际输出分辨率上抽取连续帧，统计索引色熵、相邻像素相等比例等特征，按同一调色板/抖动推断 LZW 码率，只输出区间，不试编码。批量合计只在全部就绪后显示。
- **正式转换**：纯 Kotlin GIF89a 编码器（LZW、全局调色板、NETSCAPE 循环扩展、逐帧延迟误差补偿、重复帧合并和差分矩形），输出到 `Pictures/Video2Gif`（Android 10+ 通过 MediaStore pending 发布；旧版保存到应用图片目录并支持分享）。
- **批次**：逐项覆盖、统一参数、单独设置标记、错误项阻止整批开始、串行前台服务、通知进度、取消当前/全部、失败项保留原因并重试。
- **设置与任务恢复**：DataStore 保存设置，SharedPreferences 同步保存带版本的转换任务与发布检查点；进程中断后未完成项标记为“已中断”，已完成文件保留。首页始终选择新视频，不保存或恢复上次编辑。
- **更新检查**：设置页手动查询 GitHub Release 的 `latest.json`，只有远端 `versionCode` 更高时才提示，确认后打开 Release 页面下载最新 APK。
- **主题与适配**：跟随系统/浅色/深色；Android 12+ 可选的壁纸配色；平板或宽窗口切为左侧预览、右侧参数；手机横屏仍可滚动使用；触控目标、数值输入与主要 TalkBack 描述已加入。

## 代码结构

```text
app/src/main/java/com/example/video2gif/
├── domain/       参数、批次、输出规格、GIF 计时、任务状态
├── media/        解码、Wu 量化、抖动、LZW/GIF 编码、体积估算、发布
├── data/         DataStore 设置、任务快照 JSON
├── service/      串行转换引擎、前台服务、通知
├── ui/           Compose 页面、组件、ViewModel、主题
└── Video2GifApp.kt
```

`media/` 中预览、估算与导出共用 `PaletteBuilder`、`ColorPalette`、`mapToIndices` 与采样时间表；编码器只由转换引擎持有。没有原生 `.so` 依赖，便于审查和构建。
