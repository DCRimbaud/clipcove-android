# 贡献指南

感谢参与拾刻。提交代码前请先搜索现有 Issue，避免重复工作。

## 开发流程

1. Fork 仓库并从 `main` 创建分支。
2. 保持改动聚焦，不要在同一个 PR 中混入无关重构。
3. 平台解析修复应附带最小化、已脱敏的回归样本或单元测试。
4. 提交前运行：

```powershell
.\gradlew.bat lintDebug testDebugUnitTest assembleDebug
```

5. 涉及真实平台的验证，请运行：

```powershell
python tools\smoke_network.py
```

## 代码约定

- 平台 JSON 只能出现在 data 映射层，UI 使用 `MediaInfo` / `ParseResult`。
- 新平台通过独立 data 模块和端口接入，不直接在 Compose 页面写平台分支。
- 异常需要保留明确错误类型，避免无日志的空 `catch`。
- 不提交 Cookie、Token、签名私钥、真实用户链接或版权不明媒体。
- 不提交 `local.properties`、构建产物、日志或 APK。

## Issue 内容

- 说明 App 版本、Android 版本和设备型号。
- 描述链接类型：抖音、小红书、短链或完整链接。
- 日志与截图必须移除 `xsec_token`、Cookie、手机号和 IP 地址。
- 不要上传整段版权媒体，只提供最小复现信息。

## Pull Request

- 说明问题、修复思路和验证证据。
- UI 改动请附脱敏截图。
- 提交即表示你同意按 GPL-3.0 许可证贡献代码。
