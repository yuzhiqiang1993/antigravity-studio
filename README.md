# Antigravity Studio

简体中文 · [English](README.en.md) · [更新日志](CHANGELOG.md)

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Platform: macOS | Windows](https://img.shields.io/badge/Platform-macOS%20%7C%20Windows-lightgrey.svg)](#安装与下载)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.x-7F52FF.svg?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Compose Multiplatform](https://img.shields.io/badge/Compose%20Multiplatform-1.11.x-4285F4.svg?logo=jetpackcompose&logoColor=white)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![Latest Release](https://img.shields.io/github/v/release/yuzhiqiang1993/antigravity-studio?color=green)](https://github.com/yuzhiqiang1993/antigravity-studio/releases/latest)

**Antigravity Studio** 是为 Antigravity 系列（IDE 编辑器、独立 App、终端 CLI）打造的桌面辅助工具。

它可以帮你用自己的 API Key 接入第三方大模型，统一管理多个 Google 账号与额度余量，并支持调整长对话压缩阈值与隐藏不常用模型。

<p align="center">
  <img src="img/zh/overview.png" alt="Antigravity Studio 运行概览" width="100%" />
</p>

---

## 它能解决什么问题？

日常使用 Antigravity 时，经常会遇到几个影响体验的地方：

1. **想用第三方模型**：官方自带模型种类有限，手头的 Claude 3.7、DeepSeek R1、GPT-4o、Gemini 2.5 或本地 Ollama 模型没法直接在 Antigravity 中调用。
2. **多账号切换繁琐**：有多个 Google 账号时，查看各账号额度需要反复切换，换号还要重新登录。
3. **长对话代码被过早总结**：官方默认的上下文压缩阈值偏低，多聊几轮就容易把前面的关键代码细节给总结掉。
4. **模型下拉列表过长**：很多不常用的官方模型占着下拉菜单，找想用的模型比较费劲。

---

## 功能特性

### 1. 自带 Key 接入任意模型 (BYOK)
- **支持主流服务商与自定义端点**：内置 OpenAI、Anthropic、Gemini、DeepSeek、xAI、本地 Ollama，以及各类兼容 OpenAI 格式的中转网关。
- **一键拉取与注入**：填入 Key 后直接拉取模型列表，勾选即可无缝加入 Antigravity 的模型下拉菜单。
- **完整能力支持**：正常支持多模态图片输入、代码工具调用 (Tools) 与 Thinking 思考推理档位。
- **直连无中转**：请求直接从本机发送至你的服务商，不经过任何第三方服务器。

![模型管理](img/zh/model_management.png)
![服务商预设](img/zh/provider_presets.png)
![模型选择与能力配置](img/zh/provider_models_select.png)

### 2. 多账号配额监控与一键切号
- **集中管理多账号**：支持浏览器一键登录或直接粘贴 Refresh Token 录入多个 Google 账号。
- **额度余量实时查看**：直观展示每个账号 Gemini 与 Claude 的 5 小时/周额度余量与重置倒计时。
- **秒级换号生效**：点击即可切换 IDE、App 或 CLI 当前使用的账号，无需重新登录。

![账号配额管理](img/zh/account_quota.png)
![一键切换账号](img/zh/account_switch.png)

### 3. 一键代理接管与直连恢复
- **多端状态感知**：自动识别本机 Antigravity IDE、App 与 CLI 的运行状态。
- **安全可逆**：点击「接入代理」开启接管，点击「恢复官方直连」即可还原，不修改任何官方安装包与二进制文件。

### 4. 隐藏不常用模型 & 放宽长记忆压缩
- **精简模型列表**：在界面上一键勾选隐藏不常用的官方模型，让 IDE 下拉菜单只留常用项。
- **提高上下文压缩阈值**：提供 128K、200K、256K、372K、1M 等多档设定，推迟总结触发时机，避免长对话丢代码细节。

![定制长记忆上下文策略](img/zh/context_strategy.png)

### 5. 一键体检与故障自愈 (Doctor)
- 连接异常或配置失效时，点击「健康诊断」即可一键体检（网络连通性、本地代理端口、配置文件完整性、宿主接入状态等），并支持一键自动修复。

### 6. Token 用量统计与消耗大盘
- **多维度用量分析**：支持按今天、1天、7天、14天、30天及自定义日期范围，统计总 Token、输入/输出用量、Prompt 缓存命中率及预估节省费用。
- **每日消耗趋势**：直观展示每日 Token 消耗曲线。
- **热门模型排行**：统计各模型的调用次数与 Token 占比，清晰掌握使用成本。

![用量统计](img/zh/usage_statistics.png)

### 7. 调用日志与响应耗时统计
- **请求明细审计**：本地查看请求路径、模型名称、调用耗时、上游状态码与输出速率 (TPS)。
- **响应速度分析**：汇总各模型的首字延迟 (TTFT)、生成速度与会话总耗时。
- **本地隐私安全**：日志仅保存在本地内存，退出即清空，不记录代码、提示词、模型回复或 API Key。

![调用日志明细](img/zh/activity_logs.png)
![模型速度与耗时统计](img/zh/logs_model_speed_stats.png)

### 8. 偏好设置与个性化
- **外观模式**：支持浅色、深色模式与多款 Material 3 主题配色。
- **常规设置**：支持中英文界面切换、切号默认目标应用配置与版本更新检测。

![应用偏好与配置](img/zh/settings_general.png)
![关于 Antigravity Studio](img/zh/settings_about.png)

---

## 工作原理

```text
Antigravity IDE / App / CLI
            │
            ▼ (请求发送到本地代理)
   http://127.0.0.1:8321
            │
            ├─► 官方内置模型 ──► 直接透传至 Google 官方服务器
            │
            └─► 自定义模型 ──► 本地协议转换并发送至对应服务商
                                  │
                                  ▼
                         OpenAI / Claude / DeepSeek / Ollama 等
```

---

## 桌面端与 IDE 插件配合推荐

| 产品 | 定位与核心场景 |
| :--- | :--- |
| **Antigravity Studio（桌面端）** | **第三方模型接入与代理接管**<br>适合用于自带 Key 接入第三方大模型（云端/本地）、调整长上下文压缩阈值、隐藏冗余官方模型，以及统一接管 IDE / App / CLI。 |
| **[Antigravity IDE Cockpit（IDE 插件）](https://open-vsx.org/extension/yuzhiqiang/antigravity-ide-cockpit)** | **IDE 内无感切号与会话上下文洞察**<br>运行在 Antigravity IDE 内部，支持更细致的无感切号、实时会话 Token 消耗分析与脱敏诊断。 |

> 💡 **使用建议**：推荐由 **Studio 桌面端** 负责自定义模型的代理与注入，由 **Cockpit 插件** 在 IDE 内负责无感切号与上下文用量追踪，两者配合使用体验更佳。

👉 [在 Open VSX 安装 Cockpit 插件](https://open-vsx.org/extension/yuzhiqiang/antigravity-ide-cockpit) · [访问官网 agycockpit.com](https://agycockpit.com)

---

## 安装与下载

### 1. 下载预编译安装包 (推荐)

前往 [GitHub Releases](https://github.com/yuzhiqiang1993/antigravity-studio/releases/latest) 下载对应平台的安装包：

| 操作系统与平台 | 推荐安装包 | 说明 |
| :--- | :--- | :--- |
| **macOS (Apple Silicon)** | `Antigravity-Studio-x.x.x-macos-arm64.dmg` | 适用于 M1 / M2 / M3 / M4 芯片 Mac |
| **macOS (Intel)** | `Antigravity-Studio-x.x.x-macos-x64.dmg` | 适用于 Intel 处理器 Mac |
| **Windows (x64)** | `Antigravity-Studio-x.x.x-windows-x64.exe` | 适用于 64 位 Windows 10 / 11 |

> 💡 **macOS 首次打开提示“已损坏”或“无法打开”？**
> 这是 macOS Gatekeeper 对未签名开源应用的拦截。打开系统「终端」，执行以下命令即可：
> ```bash
> sudo xattr -rd com.apple.quarantine "/Applications/Antigravity Studio.app"
> ```

---

### 2. 快速上手 3 步走

1. **添加模型服务**：打开 Studio，进入「**模型管理**」，选择你的服务商（如 DeepSeek、OpenAI 或 Claude），填入 API Key 并勾选想要使用的模型；
2. **开启接管**：进入「**运行概览**」，在对应的宿主卡片（如 Antigravity IDE）上点击「**接入代理**」；
3. **开始使用**：重新打开 Antigravity IDE，在模型选择下拉菜单中即可直接选用新添加的模型。

---

### 3. 从源码构建

开发环境要求：JDK 17 或 JDK 21（推荐 Azul Zulu 或 Eclipse Temurin）。

```bash
# 本地运行
./app_build.sh run

# 运行测试
./app_build.sh test

# 打包 macOS DMG
./app_build.sh build --formats dmg
```

---

## 常见问题 FAQ

**Q: 开启接管后，IDE 里的模型列表没出现新模型？**
- 确认「模型管理」中已成功添加服务商并勾选了模型；
- 确认「运行概览」中代理服务正在运行，且对应 IDE 状态显示为“已接入”；
- 重启一次 Antigravity IDE 使其重新读取配置。

**Q: 恢复官方直连会清空我添加的配置吗？**
- 不会。恢复直连只是把 IDE / App 的网络指向还原回官方，你添加的服务商、Key、模型和各项设置都会保存在本地。

**Q: 我的 API Key 和代码安全吗？**
- 安全。Antigravity Studio 是 100% 纯本地运行的开源软件，没有外部云端服务器，所有配置保存在本地电脑，网络请求直接发送至对应模型服务商。

---

## 交流与反馈

- **QQ 交流群**：`613214996`
- **Telegram 群组**：[点击加入 Telegram 群组](https://t.me/+IMj6SaNJAAhlNjM1)
- **问题反馈**：欢迎提交 [GitHub Issues](https://github.com/yuzhiqiang1993/antigravity-studio/issues)

---

## 开源协议与免责声明

- **开源协议**：本项目采用 [MIT License](LICENSE) 开源许可证。
- **免责声明**：Antigravity Studio 为独立的第三方开源工具，与 Google 或 Antigravity 官方团队无关。请在使用时遵守各模型服务商的使用规范。