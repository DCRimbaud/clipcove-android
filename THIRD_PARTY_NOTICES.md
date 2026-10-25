# 第三方代码与依赖

拾刻采用 GPL-3.0，并包含或参考以下第三方成果。各组件仍归原作者所有。

## TikTokDownloader / ABogus

- 项目：<https://github.com/JoeanAmier/TikTokDownloader>
- 文件：`app/src/main/python/douyin/abogus.py`
- 许可证：GNU General Public License v3.0
- 说明：原始声明保留在源文件头部。

## Douyin_TikTok_Download_API

- 项目：<https://github.com/Evil0ctal/Douyin_TikTok_Download_API>
- 关联：`abogus.py` 的后续修改和整理
- 许可证：Apache License 2.0
- 许可证副本：`third_party/Apache-2.0.txt`

## f2

- 项目：<https://github.com/Johnserf-Seed/f2>
- 关联：ABogus 算法实现参考
- 许可证：Apache License 2.0

## 主要运行时依赖

- AndroidX / Jetpack Compose / Media3：Apache License 2.0
- Kotlin / Kotlin Coroutines：Apache License 2.0
- Koin：Apache License 2.0
- OkHttp / Coil：Apache License 2.0
- Chaquopy：以其上游许可证为准
- ffmpeg-kit：以其上游及 FFmpeg 许可证为准
- gmssl、requests 等 Python 包：以各自 PyPI 项目许可证为准

分发二进制包前，请重新核对你所使用依赖版本的实际许可证与分发义务。
