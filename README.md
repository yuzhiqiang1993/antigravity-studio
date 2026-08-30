# Antigravity Studio

简体中文 · [English](README.en.md) · [更新日志](CHANGELOG.md)

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Platform: macOS | Windows](https://img.shields.io/badge/Platform-macOS%20%7C%20Windows-lightgrey.svg)](#安装与下载)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.x-7F52FF.svg?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Compose Multiplatform](https://img.shields.io/badge/Compose%20Multiplatform-1.11.x-4285F4.svg?logo=jetpackcompose&logoColor=white)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![Latest Release](https://img.shields.io/github/v/release/yuzhiqiang1993/antigravity-studio?color=green)](https://github.com/yuzhiqiang1993/antigravity-studio/releases/latest)

**Antigravity Studio** 是一款为 Antigravity（包含 IDE 插件/编辑器、独立 App、终端 CLI）量身打造的**全能桌面客户端**。

一句话概括：**用自己的 Key 畅享任意第三方大模型，集中管理多个 Google 账号与额度余量，随心定制长记忆压缩与模型列表。**

---

## 为什么需要 Antigravity Studio？

平时用 Antigravity 时，你是否遇到过这些痛点：
1. **想用其他大模型？** 官方自带的模型有限，自己手里的 Claude、DeepSeek、OpenAI、Gemini 或本地私有模型没法直接塞进 Antigravity 里用；
2. **多账号换号太折腾？** 手里有多个 Google 账号，查额度要来回切换，换号还要重新退登登录；
3. **长对话代码细节老被吞？** 官方默认的上下文压缩太激进，聊着聊着就把之前的关键代码细节给总结掉了；
4. **模型列表太乱？** 一大堆不常用的官方模型占着下拉列表，找模型费劲。

**Antigravity Studio 就是为了解决这些问题而生的。**

---

## 核心功能一览

### 1. 自带 Key 接入任意大模型 (BYOK)
- **支持全主流服务商**：内置 OpenAI、Anthropic、Gemini、DeepSeek、xAI、本地 Ollama 以及各种中转/聚合网关，也可填入任意自定义端点；
- **一键拉取模型**：填好 Key 后直接点拉取，勾选就能把模型无缝注入 Antigravity；
- **能力完全不缩水**：完美支持多模态图片输入、代码工具调用 (Tools) 与思维链 (Thinking) 推理档位；
- **无额度限制**：直接连你的服务商，不经过任何第三方服务器中转。

### 2. 多账号管理与一键换号
- **多账号集中托管**：支持 Google 浏览器一键登录或直接粘贴 Refresh Token 录入多个账号；
- **实时监控配额**：各账号各模型的额度余量一目了然；
- **一键生效到 IDE**：点击「设为当前账号」，即可秒级切换当前 IDE 正在使用的账号，不用反复重新登录。

### 3. 多端一键接管，随时切回官方直连
- **多端自动感知**：自动识别本机 Antigravity IDE、Antigravity App 和 Antigravity CLI 的运行状态；
- **安全可逆**：点一下「接入代理」即可开启接管；不想用了点一下「恢复官方直连」秒切回官方，**绝不修改任何官方安装包或二进制文件**。

### 4. 隐藏不常用模型 & 定制长记忆
- **模型列表瘦身**：在界面上一键勾选隐藏不常用的官方模型，让 IDE 里的模型下拉框清爽利落；
- **放宽上下文压缩阈值**：提供 128K、200K、256K、372K、1M 等多档容量设定，自定义压缩触发时机，防止长对话过早遗忘关键代码细节。

### 5. 一键体检与故障自愈 (Doctor)
- 连接不上或配置异常时，点一下「健康诊断」即可一键体检（网络连通性、代理端口占用、配置文件完整性、宿主接入状态等），并支持一键自动修复常见问题。

### 6. 调用日志透明审计
- 本地实时查看请求明细（模型名称、调用耗时、上游状态码）；
- **隐私保护**：所有日志仅存放在本地内存中，退出即清空，**绝不记录用户代码、Prompt 提示词、模型回答或 API Key**。

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
            └─► 自定义模型 ──► 自动转换协议发送到你的服务商
                                  │
                                  ▼
                         OpenAI / Claude / DeepSeek / Ollama 等
```

---

## 强强联合：桌面端与 IDE 插件分工与搭配

| 产品 | 定位与核心能力 |
| :--- | :--- |
| **Antigravity Studio（桌面端）** | **专注自定义模型注入与代理接管**<br>核心能力在于将第三方大模型（云端/本地）无缝注入 Antigravity、长记忆策略控制、官方模型过滤，以及对 IDE / App / CLI 的多端接管。 |
| **[Antigravity IDE Cockpit（IDE 插件）](https://open-vsx.org/extension/yuzhiqiang/antigravity-ide-cockpit)** | **专注 IDE 内无感切号与上下文深度展示**<br>主要面向 Antigravity IDE，具备更强大的**无感切号**能力、实时 Token 用量统计、上下文会话详情分析与脱敏诊断。 |

> 💡 **推荐结合使用**：由 **Studio 桌面端** 负责自定义模型的代理与注入，由 **Cockpit 插件** 在 IDE 内负责无感切号与上下文用量追踪，两者无缝结合，体验最佳！

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
> 这是 macOS Gatekeeper 对开源未签名应用的限制。打开系统的「终端」，执行以下命令回车即可：
> ```bash
> sudo xattr -rd com.apple.quarantine "/Applications/Antigravity Studio.app"
> ```

---

### 2. 快速上手 3 步走

1. **添加模型服务**：打开 Studio，进入「**模型管理**」，选择你的服务商（如 DeepSeek、OpenAI 或 Claude），填入 API Key 并勾选想要使用的模型；
2. **开启接管**：进入「**运行概览**」，在对应的宿主卡片（如 Antigravity IDE）上点击「**接入代理**」；
3. **开始使用**：重新打开 Antigravity IDE，在模型选择下拉列表中就可以直接选用刚刚添加的模型了！

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
- 完全不会。恢复直连只是把 IDE / App 的网络指向还原回官方，你添加的服务商、Key、模型和各项设置都会完好保存在本地。

**Q: 我的 API Key 和代码安全吗？**
- 绝对安全。Antigravity Studio 是 100% 纯本地运行的开源软件，没有外部云端服务器，所有配置存放在本地电脑，发包直接与对应模型服务商建立连接。

---

## 交流与反馈

- **QQ 交流群**：`613214996`
- **Telegram 群组**：[点击加入 Telegram 群组](https://t.me/+IMj6SaNJAAhlNjM1)
- **问题反馈**：欢迎提交 [GitHub Issues](https://github.com/yuzhiqiang1993/antigravity-studio/issues)

---

## 开源协议与免责声明

- **开源协议**：本项目采用 [MIT License](LICENSE) 开源许可证。
- **免责声明**：Antigravity Studio 为独立的第三方开源工具，与 Google 或 Antigravity 官方团队无关。请在使用时遵守各模型服务商的使用规范。