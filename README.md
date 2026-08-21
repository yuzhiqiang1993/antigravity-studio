# Antigravity Studio (AGY Studio)

简体中文 · [English](README.en.md)

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Platform: macOS | Windows](https://img.shields.io/badge/Platform-macOS%20%7C%20Windows-lightgrey.svg)](#系统环境)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.x-7F52FF.svg?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Compose Multiplatform](https://img.shields.io/badge/Compose%20Multiplatform-1.11.x-4285F4.svg?logo=jetpackcompose&logoColor=white)](https://www.jetbrains.com/lp/compose-multiplatform/)

> 🚀 **面向 Antigravity 系列 AI 工具的全能桌面中枢与生产力套件。**  
> 一站式集成 **本地 BYOK 模型代理**、**多账号集中管理与热切号**、**多 Key 轮询容灾**、**Token 费用实时大盘**、**上下文智能压缩** 与 **会话深度自愈**。

---

## 🌟 为什么做 Antigravity Studio？

Antigravity 是一款极具生产力与创新性的 AI 开发辅助工具，但由于官方缺乏原生的 BYOK（Bring Your Own Key）支持、内置模型选择有限、多账号频繁重登切换繁琐、长会话上下文压缩过于保守以及缺少精确的 API 账单监控，开发者常常面临诸多限制与痛点。

**Antigravity Studio** 由经验丰富的 Android 架构师与忠实重度用户联合打造，采用 **纯血 Kotlin + Compose Multiplatform Desktop** 现代架构从零重构，旨在为 Antigravity 开发者提供全方位、高度自由、稳定可控的旗舰级桌面控制中心。

---

## 🧭 核心能力全景 (Features)

```text
                                Antigravity Studio
  ┌─────────────────────────────────────┼─────────────────────────────────────┐
  │                                     │                                     │
  ▼                                     ▼                                     ▼
🛠️ 模型中心 (BYOK Model Hub)        👤 账号中心 (Account & Key Pool)      📊 用量大盘 (Token & Cost)
• OpenAI / Claude / Gemini / Ollama   • Google 多账号一键无感热切号         • 实时 Token 消耗走势 (Input/Output)
• 本地 Loopback 智能代理与流式 SSE    • 多 API Key 轮询与 429 自动 Failover • Prompt Caching 命中统计与省流折算
• FLUX / Midjourney / DALL-E 生图     • 智能配额守卫与账号健康度监控        • 自定义模型费率与多币种账单导出
  │                                     │                                     │
  ├─────────────────────────────────────┼─────────────────────────────────────┤
  │                                     │                                     │
  ▼                                     ▼                                     ▼
🩺 会话自愈 (Session Doctor)          🧠 智能压缩 (Smart Checkpointer)      🌐 独立网络 (Network & Host)
• 损坏会话与 bad checkpoint 快速修复  • 模型级物理容量预设 (128K ~ 1M)      • 独立 HTTP / SOCKS5 代理分流
• 历史工具调用坏块与断裂状态剥离      • 触发阈值与安全预留缓冲区精细调节    • macOS launchctl / Windows 注册表无损接管
• 一键生成脱敏诊断报告                • 压缩任务专用轻量工作模型隔离        • 系统托盘 (System Tray) 常驻与开机自启
```

### 1. 🛠️ 模型中心 (BYOK Model Hub)
- **多协议原生转换**：内置 OpenAI Chat/Responses、Anthropic Messages、Gemini 原生协议转换，零损耗转接 Antigravity Cloud Code 请求；
- **Reasoning 档位聚合**：原生聚类 Claude 3.7 Thinking、DeepSeek R1、OpenAI o-series 的推理档位（Low / Medium / High / Max）；
- **本地大模型深度赋能**：针对 Ollama / vLLM 提供自定义 `num_ctx`、`keep_alive` 等高阶参数透传，长文本不溢出；
- **多模态与生图路由**：支持内联图片、PDF 视觉理解，并拦截生图请求路由至 FLUX、Midjourney、DALL-E、SDXL 等。

### 2. 👤 账号与多 Key 轮询 (Account & Key Pool)
- **多账号集中管理**：统一管理多个 Google / Antigravity 官方账号，支持不重启 IDE 一键热切号；
- **多 Key 轮询与容灾 (Failover)**：同一个 Provider 支持配置多个 API Key 轮询池，遇到 `429 Too Many Requests` 或欠费时后台毫秒级无感重试并切换到下一个可用 Key。

### 3. 📊 用量与费用大盘 (Token & Cost Analytics)
- **全链路 Token 精准统计**：结构化解析上游返回的 `prompt_tokens`、`completion_tokens`、`reasoning_tokens`；
- **Prompt Caching 效益洞察**：精确统计 Anthropic / DeepSeek 的缓存命中 Token 与节省的金额；
- **可视化仪表盘**：日 / 周 / 月用量走势图、模型消耗占比饼图、历史会话明细导出。

### 4. 🩺 会话诊断与自愈 (Session Doctor)
- **会话坏块修复**：智能识别由于工具调用 ID 丢失、异常断流引发的 `bad checkpoint state`，一键重置自愈；
- **脱敏诊断报告**：一键导出脱敏的环境与连通性诊断报告，方便问题排查。

### 5. 🧠 模型级上下文压缩 (Checkpointer)
- **告别保守截断**：打破官方过早触发摘要压缩的限制，根据模型真实物理上限（128K、200K、256K、372K、1M）自定义安全触发阈值，最大化保留长上下文代码细节。

### 6. 🌐 独立网络代理与系统常驻
- **独立 SOCKS5 / HTTP 代理**：支持为特定 Provider 配置独立代理出口，告别国内科学上网环境下的 TUN 环路与 DNS 冲突；
- **系统托盘 (System Tray)**：常驻 macOS 菜单栏与 Windows 任务栏，关闭主窗口不中断代理服务，支持托盘快捷菜单。

---

## 🏗️ 架构设计与技术栈 (Architecture)

Antigravity Studio 采用现代 Android Clean Architecture + MVI 架构模式构建：

```text
antigravity-studio/
├── desktopApp/                    # 桌面主工程入口 (窗口管理, System Tray 托盘, 打包配置)
└── shared/                        # 跨平台核心共享模块
    ├── commonMain/
    │   └── kotlin/com/yuzhiqiang/antigravity/studio/
    │       ├── core/              # 核心代理引擎 (Ktor Server, OkHttp Proxy, SSE 流式处理)
    │       ├── data/              # 数据持久化 (Room KMP / SQLite, KeyStore, Settings)
    │       ├── domain/            # 领域模型与 UseCases (协议转换, 路由分发, 费率折算)
    │       └── ui/                # Compose Multiplatform UI (M3 组件库, 状态流, 图表)
    └── jvmMain/                   # 桌面特有实现 (macOS launchctl, Windows 注册表集成)
```

- **UI 渲染**：Compose Multiplatform 1.11+ (基于 Skiko 直通 Metal / DirectX 硬件加速)
- **代理服务**：Ktor Server 3.1+ (CIO 纯协程高并发引擎)
- **网络客户端**：OkHttp + Ktor Client (原生 HTTP / SOCKS5 代理支持)
- **异步与流式**：Kotlin Coroutines & Flow
- **依赖注入**：Koin 4.0+
- **数据存储**：Room KMP / SQLite + Multiplatform Settings

---

## 🚀 快速开始与构建 (Getting Started)

### 环境要求
- **macOS** (Apple Silicon / Intel) 或 **Windows 10/11**
- **JDK 17** 或 **JDK 21**（推荐 Temurin 或 Azul Zulu）

### 运行与开发

```bash
# 运行桌面端应用
./gradlew :desktopApp:run

# 运行测试
./gradlew check
```

### 独立原生安装包打包 (Distribution)

```bash
# macOS 打包 (.dmg)
./gradlew :desktopApp:packageDmg

# Windows 打包 (.msi / .exe)
./gradlew :desktopApp:packageMsi
```
产物将输出在 `desktopApp/build/compose/binaries/main/` 目录下。

---

## 🤝 参与贡献与社区交流

欢迎提交 Issue 与 Pull Request 共同完善 Antigravity Studio！

- **开源协议**：[MIT License](LICENSE)
- **作者**：[yuzhiqiang1993](https://github.com/yuzhiqiang1993)