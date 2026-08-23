# Antigravity Studio

简体中文 · [English](README.en.md) · [更新日志](CHANGELOG.md)

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Platform: macOS | Windows](https://img.shields.io/badge/Platform-macOS%20%7C%20Windows-lightgrey.svg)](#安装与下载)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.x-7F52FF.svg?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Compose Multiplatform](https://img.shields.io/badge/Compose%20Multiplatform-1.11.x-4285F4.svg?logo=jetpackcompose&logoColor=white)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![Latest Release](https://img.shields.io/github/v/release/yuzhiqiang1993/antigravity-studio?color=green)](https://github.com/yuzhiqiang1993/antigravity-studio/releases/latest)

**Antigravity Studio** 是为 Antigravity 编程助手（IDE、App、CLI）开发的桌面客户端。通过本地代理，你可以方便地使用自己的 API 密钥接入第三方大模型（如 OpenAI、Claude、DeepSeek、Gemini、Ollama 等），管理模型配置，并一键开启或关闭 IDE 接管。

---

## 为什么需要 Antigravity Studio？

Antigravity 官方只提供了少量预设模型。如果你有自己的 API 密钥或订阅（如 Claude 3.7 Sonnet、DeepSeek R1、GPT-4o、Gemini 2.5 Pro 或本地 Ollama 模型），无法直接在 Antigravity 中使用。

Antigravity Studio 在本地运行一个轻量代理（默认端口 `8321`），自动完成协议转换：
- **自带密钥 (BYOK)**：直接使用你自己的服务商 Key，没有额度中转限制；
- **能力完整**：支持多模态图片输入、工具调用 (Tools) 与思维链 (Thinking) 推理等级；
- **官方模型管理**：可按需隐藏或禁用不常用的官方内置模型，保持模型列表清爽；
- **安全可逆**：随时一键开启接管，随时一键恢复官方直连，不修改任何官方程序文件。

---

## 交流与反馈

- **QQ 交流群**：`613214996`
- **Telegram 群组**：[点击加入 Telegram 群组](https://t.me/+IMj6SaNJAAhlNjM1)
- **问题反馈**：欢迎提交 [GitHub Issues](https://github.com/yuzhiqiang1993/antigravity-studio/issues)。

---

## 搭配推荐：Antigravity IDE Cockpit

如果你经常在 Antigravity IDE 中编码，推荐配合使用 [**Antigravity IDE Cockpit**](https://open-vsx.org/extension/yuzhiqiang/antigravity-ide-cockpit) 插件：
- 多账号集中管理与快速切号；
- 实时统计 Token 用量与配额走势；
- 会话诊断与脱敏报告导出。

👉 [在 Open VSX 安装 Cockpit 插件](https://open-vsx.org/extension/yuzhiqiang/antigravity-ide-cockpit) · [访问官网 agycockpit.com](https://agycockpit.com)

---

## 核心功能

### 1. 运行概览
- **本地代理状态**：查看代理运行状态与监听端口；
- **宿主环境感知**：自动识别本机 Antigravity IDE、Antigravity App 和 Antigravity CLI 的安装与运行状态；
- **一键接管与恢复**：点击即可切换为“代理接管”或“官方直连”。

### 2. 模型与服务商管理
- **主流服务商预设**：内置 OpenAI、Anthropic、Gemini、DeepSeek、xAI、Ollama 及各类聚合网关预设，也可添加任意自定义端点；
- **一键拉取模型**：连接上游服务后，可直接拉取可用模型列表，勾选即可保存到 Antigravity；
- **多模态与推理档位**：支持为模型开启图片输入、工具调用，并映射 Low / Medium / High / Max 推理档位；
- **官方模型过滤**：可在界面上一键隐藏不需要的官方模型；
- **延迟测试**：随时对服务商或单个模型发起延迟与连通性测试。

### 3. 上下文压缩设置
长对话中，如果上下文压缩触发过早，容易频繁调用总结模型并丢失早期代码细节；触发过晚则可能超出模型物理上限导致报错。
- 提供 128K、200K、256K、372K、1M 等多档容量预设；
- 支持自定义压缩触发阈值与输出预留大小；
- 可选择使用当前模型或轻量模型执行摘要压缩。

### 4. 健康检查 (Doctor)
遇到连接或配置问题时，可一键体检并支持一键修复：
- 官方网络与 GitHub Release 连通性
- 本地配置文件读写与格式完整性
- 代理端口占用与回环监听状态
- 宿主接管配置有效性
- 已添加的上游服务有效性

### 5. 调用日志
- 本地记录代理转发的请求明细（时间、请求模型、上游服务、HTTP 状态码、耗时）；
- **隐私保护**：所有日志仅存放在本地内存中，绝不记录用户代码、Prompt 提示词、模型回答或 API Key。

---

## 工作原理

```text
Antigravity IDE / App / CLI
            │
            ▼ (请求发送到本地代理)
   http://127.0.0.1:8321
            │
            ├─► 官方内置模型 ──► 直接透传至官方服务器
            │
            └─► 自定义模型 ──► 转换为 OpenAI / Anthropic / Gemini / Ollama 协议
                                  │
                                  ▼
                         上游服务商 / 本地大模型
```

---

## 安装与下载

### 1. 下载预编译安装包 (推荐)

前往 [GitHub Releases](https://github.com/yuzhiqiang1993/antigravity-studio/releases/latest) 下载对应平台的安装包：

| 操作系统与平台 | 推荐安装包 | 说明 |
| :--- | :--- | :--- |
| **macOS (Apple Silicon)** | `Antigravity-Studio-1.0.0-macos-arm64.dmg` | 适用于 M1 / M2 / M3 / M4 芯片 Mac |
| **macOS (Intel)** | `Antigravity-Studio-1.0.0-macos-x64.dmg` | 适用于 Intel 处理器 Mac |
| **Windows (x64)** | `Antigravity-Studio-1.0.0-windows-x64.exe` | 适用于 64 位 Windows 10 / 11 |

> 💡 **macOS 首次打开提示“已损坏”或“无法打开”？**
> 这是 macOS Gatekeeper 对未签名开源应用的限制。打开“终端”，执行以下命令即可正常打开：
> ```bash
> sudo xattr -rd com.apple.quarantine "/Applications/Antigravity Studio.app"
> ```

---

### 2. 快速使用步骤

1. 打开 **Antigravity Studio**；
2. 进入「**模型管理**」页面，点击「**添加服务**」，选择你的服务商（如 DeepSeek 或 OpenAI），填入 API Key 并勾选想要使用的模型；
3. 进入「**运行概览**」页面，确认代理处于运行状态，在对应的宿主卡片（如 Antigravity IDE）上点击「**接管**」；
4. 重新打开 Antigravity IDE，在模型选择下拉菜单中即可直接使用刚添加的模型。

---

### 3. 从源码构建

需要环境：JDK 17 或 JDK 21（推荐 Azul Zulu 或 Temurin）。

```bash
# 本地运行
./app_build.sh run

# 运行单测
./app_build.sh test

# 打包 macOS DMG
./app_build.sh build --formats dmg
```

---

## 常见问题

**Q: 开启接管后，IDE 中没有显示添加的模型？**
- 检查「模型管理」中是否已成功添加并勾选了模型；
- 检查「运行概览」中代理服务是否正在运行，且对应 IDE 卡片显示为“已接管”；
- 重启一次 Antigravity IDE 使其重新加载配置；
- 打开「调用日志」查看请求是否到达本地代理。

**Q: 恢复官方直连会清空我的模型配置吗？**
- 不会。恢复直连仅还原宿主配置文件的指向，所有添加的服务商、模型和设置都会完整保留。

---

## 开源协议与声明

- **开源协议**：本项目采用 [MIT License](LICENSE) 开源许可证。
- **免责声明**：Antigravity Studio 为独立的第三方开源工具，与 Google 或 Antigravity 官方团队无关。请在使用时遵守各模型服务商的使用规范。