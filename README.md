# 拾刻(ShiKe)

多平台无水印解析安卓 App:支持 **抖音 + 小红书**,不依赖任何服务器,安装即用。

## 功能

- 多平台:抖音 / 小红书(分享口令、短链、完整链接、分享文案自动识别)
- 无水印视频 / 音乐 / 图集图片 / 动图下载
- 动图 + 音乐合成(ffmpeg-kit)
- 图集原图下载(去 CDN 压缩)
- 内置下载管理器(进度 / 打开 / 分享 / 另存 / 删除)
- 收藏夹(独立 tab)
- 系统分享一键解析、剪贴板快捷解析卡片
- WiFi 传文件到电脑(局域网 HTTP,带 token 鉴权)
- 自动保存到下载目录、导出备份
- 解析历史(本地持久化,存原始链接,点击可重新解析)
- 匿名解析,无需登录/配置(解析频繁可能触发风控,属平台限制)

## 架构(多模块,依赖单向)

```
douyin-app/
├── gradle/libs.versions.toml      # 版本目录(单一依赖版本源)
├── app/                           # 壳:Koin DI 装配 + 导航(唯一知道实现的地方)
├── core/
│   ├── designsystem/              # 主题 / 颜色 / 通用 UI
│   └── common/                    # 通用工具
├── data/
│   ├── douyin/                    # DouyinRepository 接口(Python 引擎解析)
│   ├── xhs/                       # XhsRepository(小红书 SSR 提取 + 多源 API)
│   ├── download/                  # DownloadRepository(StateFlow 任务流)
│   ├── history/                   # HistoryRepository(解析历史)
│   ├── favorites/                 # FavoritesRepository(收藏)
│   ├── merger/                    # VideoMerger 接口(ffmpeg 合成)
│   ├── transfer/                  # WifiTransferServer(局域网传输)
│   └── settings/                  # SettingsRepository(配置)
└── feature/
    ├── home/                      # 首页(多平台路由 / 结果卡片 / 历史 / 剪贴板弹窗)
    ├── downloads/                 # 下载管理页
    ├── favorites/                 # 收藏页
    └── settings/                  # 设置页
```

- 依赖方向:`feature → data 接口 / core → app 装配`,无反向依赖
- 增删平台 = 增 data 模块 + 首页一行路由;换实现只改 app 装配
- 状态管理:ViewModel + StateFlow;图片:Coil(按域名 Referer);播放:Media3 ExoPlayer;DI:Koin

## 版本规则

- 测试版:`0.x.x`(功能 +0.1,修复 +0.0.1)
- 正式版:`1.0.0` 起步,之后永远 1.x(功能 1.1.0 / 修复 1.0.1)
- 改版本:`app/build.gradle.kts` 顶部三个数字

## 构建(命令行,无需 Android Studio)

```powershell
$env:JAVA_HOME = "C:\Users\Admin\AppData\Local\Programs\jdk-17\jdk-17.0.2"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat assembleDebug
```

产物:`app\build\outputs\apk\debug\app-debug.apk`(Python 路径可用环境变量 `PYTHON_EXE` 覆盖)

## 安装

- `adb install app-debug.apk`(小米需在开发者选项开启「USB 安装」)
- 或拷到手机点击安装(允许未知来源)

## 说明

- 抖音解析引擎:Chaquopy 内嵌 Python(a_bogus 签名),代码在 `app/src/main/python/`
- 小红书解析:直接抓取笔记页 SSR 数据(点赞/评论/收藏/时间齐全),第三方 API 作备源
- 小红书分享链接带时效(xsec_token 约 24h),过期后需重新复制分享链接
- 仅个人学习使用,注意版权合规
