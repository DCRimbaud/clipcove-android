# 拾刻 ShiKe

[![Android CI](https://github.com/DCRimbaud/shike-android/actions/workflows/android-ci.yml/badge.svg)](https://github.com/DCRimbaud/shike-android/actions/workflows/android-ci.yml)
[![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue.svg)](LICENSE)
[![Latest release](https://img.shields.io/github/v/release/DCRimbaud/shike-android?include_prereleases)](https://github.com/DCRimbaud/shike-android/releases)

拾刻是一款本地运行的 Android 媒体工具，支持解析抖音和小红书分享链接，并下载公开内容中的视频、图集、原声和动图。解析过程不依赖自建服务器，收藏、历史、下载任务均保存在本机。

> 本项目与抖音、字节跳动、小红书无任何隶属关系。请仅处理你有权保存的内容，并遵守所在地区法律及平台规则。

## 功能

- 抖音、小红书分享口令、短链、完整链接和分享文案识别
- 视频、图集原图、原声和 Live Photo / 动图下载
- 断点续传、暂停、继续、失败重试和前台下载通知
- 动图与原声合成
- 本地收藏、解析历史和 JSON 备份
- 剪贴板快捷解析和系统分享入口
- WiFi 局域网传文件
- 深色 Compose UI，支持 Android 7.0+（minSdk 24）

## 技术栈

- Kotlin、Jetpack Compose、Material 3
- ViewModel、StateFlow、Koin
- OkHttp、Coil、Media3
- Chaquopy + Python 3.12
- ffmpeg-kit
- 多模块 Android Gradle 项目

## 架构

```text
app/                   应用壳、导航、依赖注入、平台适配器
core/
  model/               媒体模型与解析结果
  designsystem/        主题与通用 Compose 组件
  common/              通用工具
data/
  douyin/              抖音解析端口与 JSON 映射
  xhs/                 小红书解析端口与 SSR 映射
  download/            下载任务状态机
  history/             历史记录
  favorites/           收藏
  merger/              FFmpeg 合成端口
  transfer/            WiFi 传输
  settings/            设置
feature/
  home/                首页、解析结果、历史
  downloads/           下载管理
  favorites/           收藏
  settings/            设置
```

依赖方向为 `feature → core:model + data 接口 → app 装配`。平台原始 JSON 只允许存在于 data 映射层，页面只接收强类型 `MediaInfo` 与 `ParseFailure`。

## 构建

### 环境要求

- JDK 17
- Android SDK 34（compileSdk 34，targetSdk 34）
- Python 3.12 与 `pip`
- Windows、macOS 或 Linux

Chaquopy 需要找到本机 Python 可执行文件。优先读取环境变量 `PYTHON_EXE`；未设置时默认调用 PATH 中的 `python`。

### Windows

```powershell
$env:JAVA_HOME = "C:\path\to\jdk-17"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$env:PYTHON_EXE = "C:\path\to\python.exe"

# 首次构建前创建 local.properties，并替换为自己的 SDK 路径
Set-Content local.properties 'sdk.dir=C\:\\Android'

.\gradlew.bat lintDebug testDebugUnitTest assembleDebug
```

### macOS / Linux

```bash
export JAVA_HOME=/path/to/jdk-17
export PATH="$JAVA_HOME/bin:$PATH"
export PYTHON_EXE=python3
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew lintDebug testDebugUnitTest assembleDebug
```

Debug APK 输出到 `app/build/outputs/apk/debug/app-debug.apk`。

## 测试

```powershell
.\gradlew.bat lintDebug testDebugUnitTest assembleDebug
python tools\smoke_network.py
```

`tools/smoke_network.py` 会访问真实平台，用于手动冒烟测试。平台风控、网络环境或作品时效都可能导致失败，因此不进入普通 CI。

## 已知限制

- 抖音和小红书接口、风控与页面结构会变化，解析器需要持续维护。
- 小红书的 `xsec_token` 具有时效性，旧链接失效后必须重新复制分享链接。
- 匿名解析可能触发频率限制或验证码。
- 当前发布流程使用 Debug 签名；正式分发前应配置自己的 Release keystore。
- 不提供账号登录、批量爬取、商业采集或代理池能力。

## 安全与合规

- 不要在 Issue、日志或截图中公开 Cookie、`xsec_token`、手机号、局域网地址等敏感信息。
- 安全问题的报告方式见 [SECURITY.md](SECURITY.md)。
- 本项目仅用于个人学习和研究。使用者应自行承担内容版权、平台条款和当地法律风险。

## 参与贡献

欢迎提交 Bug、兼容性修复和测试样本。提交前请阅读 [CONTRIBUTING.md](CONTRIBUTING.md)。

## 许可证

本项目以 **GNU General Public License v3.0** 发布，详见 [LICENSE](LICENSE)。第三方代码与依赖归属见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
