# Antigravity Studio (AGY Studio)

简体中文 · [English](README.en.md) · [更新日志](CHANGELOG.md)

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Platform: macOS | Windows](https://img.shields.io/badge/Platform-macOS%20%7C%20Windows-lightgrey.svg)](#安装指南)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.x-7F52FF.svg?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Compose Multiplatform](https://img.shields.io/badge/Compose%20Multiplatform-1.11.x-4285F4.svg?logo=jetpackcompose&logoColor=white)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![Latest Release](https://img.shields.io/github/v/release/yuzhiqiang1993/antigravity-studio?color=green)](https://github.com/yuzhiqiang1993/antigravity-studio/releases/latest)

一款**面向 Antigravity 系列 AI 工具的旗舰级全能桌面中枢与生产力套件**。基于 **Kotlin + Compose Multiplatform Desktop** 现代跨平台技术构建，支持 **Antigravity IDE**、**Antigravity App** 和 **Antigravity CLI** 通过本地智能代理接入任意第三方模型，实现模型深度定制、上下文智能压缩、全链路健康诊断与多端无感接管。

---

### 为什么做 Antigravity Studio？

Antigravity 只提供了官方预设的 Gemini 系列模型以及部分旧版 Claude 模型。对于习惯 Antigravity 优秀开发体验、同时又拥有 OpenAI、Claude 3.7 Sonnet、DeepSeek R1、Ollama 或各类聚合网关订阅的开发者来说，官方缺少原生 BYOK（Bring Your Own Key）能力、多账号频繁重登繁琐、长会话上下文压缩过于保守等痛点十分明显。

**Antigravity Studio** 就是为了彻底解决这些限制而生。通过本地高性能代理与协议转换，你可以将任意自定义模型丝滑注入到 Antigravity IDE、App 或 CLI 中，并享受完整的视觉能力、工具调用 (Tools) 与推理思考档位映射。

---

## ⭐ 推荐搭配：Antigravity IDE Cockpit

如果你经常使用 Antigravity IDE，强烈建议同时安装 [**Antigravity IDE Cockpit 插件**](https://open-vsx.org/extension/yuzhiqiang/antigravity-ide-cockpit)。它是你的专属账号驾驶舱：

- **多账号集中管理**：统一查看多账号状态、配额与 Token 用量；
- **智能热切号**：优先尝试不重启切号，按模型额度状态智能调度；
- **额度与用量监控**：实时掌握 Token 消耗与费用走势；
- **会话与模型诊断**：管理会话坏块并导出脱敏诊断报告。

> **💡 黄金组合**：**Antigravity Studio** 负责把你想用的任意模型与网络接入 Antigravity，**Antigravity IDE Cockpit** 负责让你的账号、额度和会话更好管理。两者结合，让 AI 辅助编程体验达到极致。
> 
> 👉 [立即在 Open VSX 安装 Cockpit 插件](https://open-vsx.org/extension/yuzhiqiang/antigravity-ide-cockpit) · [访问官网 agycockpit.com](https://agycockpit.com)

---

## 💬 交流群

欢迎加入 Antigravity 交流群探讨技术与反馈体验：

- **QQ 交流群**：`613214996`
- **Telegram 交流群**：[点击加入 Telegram 群组](https://t.me/+IMj6SaNJAAhlNjM1)

---

## 🧭 功能概览

```text
                                 Antigravity Studio
  ┌─────────────────────────────────────┼─────────────────────────────────────┐
  │                                     │                                     │
  ▼                                     ▼                                     ▼
🛠️ 模型中心 (BYOK Model Hub)        🌐 宿主与代理 (Host & Proxy)          🧠 智能压缩 (Smart Checkpointer)
• OpenAI / Claude / Gemini / Ollama   • IDE / App / CLI 一键接管与恢复      • 模型级物理容量预设 (128K ~ 1M)
• 官方模型自由隐藏/启用               • Loopback 端口自适应与防冲突         • 触发阈值与安全缓冲区精细调节
• 图像视觉 / 工具调用 / 思考档位聚合  • macOS 会话级隔离 & Windows 注册表   • 压缩任务独立轻量工作模型隔离
  │                                     │                                     │
  ├─────────────────────────────────────┼─────────────────────────────────────┤
  │                                     │                                     │
  ▼                                     ▼                                     ▼
🩺 全链路体检 (Doctor Diagnostics)    📊 调用日志 (Activity Logs)           ⚙️ 偏好与更新 (Preferences & Update)
• 网络连通性、本地配置与代理端口检测  • 请求路由、上游 Provider 与耗时解析  • 中英双语一键切换与主题适配
• 宿主接管状态与路径智能诊断          • 官方透传与自定义路由明细            • GitHub Releases 自动增量更新检查
• 智能修复建议与错误一键定位          • 纯内存安全留存，不记录敏感数据      • 开发者调试模式与原生环境隔离
```

### 1. 运行概览 (Overview)

运行概览是 Antigravity Studio 的主控大盘，为你提供一站式的代理运行与宿主状态监控：

- **代理服务状态**：实时查看本地代理运行状态、实际监听端口与官方模型数据同步状态；
- **配置与就绪指标**：查看已接入的服务商数量、自定义模型总数与当前就绪度；
- **宿主环境感知**：自动探测本机 **Antigravity IDE**、**Antigravity App** 和 **Antigravity CLI** 的安装状态与运行状态；
- **一键接管与安全恢复**：为指定宿主一键开启代理接管模式，或随时一键无损恢复官方直连模式（不影响已保存的 Provider 与模型配置）。

### 2. Provider 与模型管理 (Model Hub)

- **服务商丰富预设**：内置海量 Provider 预设，涵盖官方大厂（OpenAI、Anthropic、Google Gemini、xAI、DeepSeek 等）、聚合网关（OpenRouter、CLIProxyAPI、One API 等）及本地大模型（Ollama）；
- **动态模型发现**：一键从上游服务拉取可用模型列表，按需勾选并配置暴露给 Antigravity 的模型；
- **官方内置模型治理**：支持在界面上一键禁用或隐藏不需要的官方原生模型（如 Gemini 3.5 Flash 等），保持 IDE 模型选择器极致清爽；
- **多模态与能力配置**：细粒度配置图像视觉输入、工具调用 (Tools) 与 Thinking / Reasoning 思考等级映射；
- **分级连通性测试**：支持对整个 Provider、单个模型或官方模型同步链路进行即时延迟测试与健康检查。

#### 2.1 添加上游服务三步曲
1. **选择服务**：选择内置预设（官方厂商/聚合网关/本地服务）或自定义服务；
2. **连接配置**：填写 API Base URL 与 API Key，支持 OpenAI、Anthropic、Gemini 协议，展开高级设置可定制模型端点；
3. **选择与定制模型**：从上游一键抓取模型，勾选所需模型并配置图像、工具调用与推理档位后保存。

#### 2.2 模型级上下文压缩策略 (Smart Checkpointer)

Antigravity 官方默认的上下文压缩策略通常非常**保守**（在上下文用量很低时就会早早触发压缩），导致长会话中频繁触发 Checkpointer 摘要压缩。这不仅会增加额外的交互等待延迟和 Token 开销，还容易造成早期代码细节与对话记忆的过度精简。

通过 Antigravity Studio，你可以针对每个官方或自定义模型独立定制上下文压缩策略，按需降低压缩频率：

- **多档容量预设与微调**：提供从“深度压缩”到“极限保真”的多档容量预设（如 128K、200K、256K、372K、1M），支持通过快捷百分比或精确 Token 手动调整**压缩触发阈值**、**Checkpoint 上限**以及**输出预留 Token**；
- **压缩工作模型选择**：压缩任务可以跟随当前模型执行，也可以固定指定轻量快速的 Gemini Flash，在上下文一致性、速度和成本之间取得平衡；
- **原生/上游模式兼容**：选择“官方默认”或“上游默认”时，不写入模型级覆盖，保留模型原有的 Checkpointer 与上下文限制。

> [!TIP]
> **💡 上下文压缩参数设置建议与权衡：**
> - **⚠️ 阈值设置过小（触发过于频繁）**：对话刚进行几轮就会频繁触发压缩；每次压缩都需要额外的 LLM 摘要调用，增加等待延迟并消耗额外 Token；历史上下文被多次过度压缩摘要，容易丢失早期关键需求和代码细节。
> - **⚠️ 阈值设置过大（过于接近模型物理上限）**：极易超出上游模型的最大上下文窗口导致请求直接报错（`Context Window Exceeded` / `400 Bad Request`）。
> - **✅ 最佳实践建议**：根据上游模型的实际最大 Context 窗口选择匹配的预设档位，建议为模型输出和系统工具预留 **20% ~ 30%** 的安全缓冲区（例如 200K 上下文的模型，推荐设置在 148K 左右触发压缩），既能有效减少压缩频率、完整保留长上下文，又能避免溢出报错。

### 3. 本地代理与宿主接入 (Host Integration)

Antigravity Studio 使用一个只监听本机 Loopback 地址（`127.0.0.1`）的高性能本地代理完成协议转换和请求转发：

- 默认端口为 `8321`，被占用时自动选用空闲端口；
- 官方原生模型直接透传至官方服务，自定义模型智能路由至对应的 Provider Adapter；
- 跨平台接管机制如下：

| 平台与宿主入口 | 接入与接管方式 | 生效与恢复方式 |
| :--- | :--- | :--- |
| **macOS / Windows · Antigravity IDE** | 自动管理用户 `settings.json` 中的 `jetski.cloudCodeUrl`，记录可逆 ownership | 运行时切换按需安全重启 IDE |
| **macOS · Antigravity App** | 管理用户登录会话级 `CLOUD_CODE_URL`，绝不篡改官方应用签名包 | 运行时切换按需安全重启，Studio 启动自动同步 |
| **Windows · Antigravity App** | 管理用户级 `CLOUD_CODE_URL` 环境变量与注册表 | 运行时切换按需安全重启 |
| **macOS · Antigravity CLI** | 管理用户会话级 `CLOUD_CODE_URL` | 重新打开终端窗口即可生效 |
| **Windows · Antigravity CLI** | 管理用户级 `CLOUD_CODE_URL` 环境变量 | 重新打开终端窗口即可生效 |

### 4. 🩺 Doctor 全链路健康体检

内置强大的全链路诊断引擎，一键体检 6 大核心维度：
- **网络与核心连通性**：检测 Google 官方服务、GitHub Release 接口与本地回环连通性；
- **本地存储与配置**：校验 `config.json` 完整性、读写权限与存储健康度；
- **本地代理服务**：检测代理端口监听状态与 HTTP/SSE 转发通道；
- **宿主安装与接管**：全盘扫描 IDE、App、CLI 的安装路径、代理接管状态与版本匹配度；
- **自定义 Provider**：逐一诊断已配置上游服务的密钥有效性与接口响应；
- **一键智能修复**：提供明确的故障定位原因与一键修复指引。

### 5. 调用日志 (Activity Logs)

- 结构化展示代理转发的每一次 HTTP 请求与响应元数据；
- 包含时间戳、路由分类（官方透传 / 自定义模型）、上游 Provider、HTTP 状态码与耗时；
- 支持实时刷新、失败请求快速筛选与内存日志一键清空；
- **隐私保护原则**：日志仅保存在本地内存中，绝不记录用户的 Prompt 提示词、代码内容、模型回答、Header 或 API Key。

### 6. 应用设置与版本升级 (Settings & Update)

- **多语言本地化**：支持 **简体中文** 与 **English** 运行时一键无感切换；
- **外观主题**：支持浅色、深色与跟随系统主题；
- **网络端口管理**：自定义本地代理服务端口与配置目录快速打开；
- **自动检查更新**：内置语义化版本比对引擎（`SemVer`），启动时静默检测 GitHub Releases 最新版本，并根据当前 Mac（Apple Silicon / Intel）或 Windows 芯片架构智能匹配下载最优安装包；
- **开发者调试模式**：可通过设置开关或关于页面点击版本号快捷激活，动态展示原始/修改后 JSON 等协议调试工具。

---

## ⚙️ 工作原理 (How It Works)

```text
Antigravity IDE / App / CLI
            │
            │ 选择由 Antigravity Studio 注入的模型
            ▼
   http://127.0.0.1:8321 (本地 Loopback 代理)
            │
            ├─► 官方模型 ──► Google Cloud Code 原生直连
            │
            └─► 自定义模型 ──► 协议转换 (OpenAI / Anthropic / Gemini / Ollama)
                                 │
                                 ▼
                    上游 Provider / 企业网关 / 本地 LLM
```

---

## 📦 安装指南 (Installation)

### 1. 下载预编译安装包 (推荐)

前往 [GitHub Releases](https://github.com/yuzhiqiang1993/antigravity-studio/releases/latest) 下载对应平台的最新安装包：

| 操作系统与平台 | 推荐安装包格式 | 说明 |
| :--- | :--- | :--- |
| **macOS (Apple Silicon)** | `Antigravity-Studio-1.0.0-macos-arm64.dmg` | 原生适配 M1/M2/M3/M4 系列芯片 Mac |
| **macOS (Intel)** | `Antigravity-Studio-1.0.0-macos-x64.dmg` | 原生适配 Intel x86_64 架构 Mac |
| **Windows (x64)** | `Antigravity-Studio-1.0.0-windows-x64.exe` | 适配 64 位 Windows 10/11 |

#### macOS 首次打开提示未签名或损坏？

由于 macOS Gatekeeper 安全机制，首次打开未签名的开源 App 时，在系统终端执行以下命令即可正常打开：

```bash
sudo xattr -rd com.apple.quarantine "/Applications/Antigravity Studio.app"
```

---

### 2. 从源码构建与运行

#### 环境要求
- **macOS** (Apple Silicon / Intel) 或 **Windows 10/11**
- **JDK 17** 或 **JDK 21**（推荐 Temurin 或 Azul Zulu）

#### 本地启动与构建命令
本项目提供了全功能自动化构建脚本 [`app_build.sh`](app_build.sh)：

```bash
# 1. 本地开发快速运行 (Debug 模式)
./app_build.sh run

# 2. 运行全量自动化单元测试
./app_build.sh test

# 3. 秒级生成免安装独立 App 目录
./app_build.sh dist

# 4. 构建 macOS DMG 磁盘镜像包
./app_build.sh build --formats dmg

# 5. 完整发布前流水线 (clean + test + 全量打包 + SHA256校验)
./app_build.sh assemble
```

详细构建参数与 CI/CD 矩阵配置请参阅 [构建与打包指南 (app_build.md)](app_build.md)。

---

## ❓ 常见问题排查 (Troubleshooting)

### Q1: 代理启动后，IDE 或 App 中没有显示自定义模型？
1. 在“模型管理”中确认已成功添加 Provider 并勾选保存了模型；
2. 在“运行概览”中确认本地代理服务处于“运行中”状态；
3. 确认对应宿主卡片已开启“已接管”状态；
4. 若宿主正在运行，等待其安全重启或手动重新打开；
5. CLI 宿主需要完全退出并重新打开终端窗口；
6. 打开“调用日志”查看请求是否已成功到达本地代理。

### Q2: Provider 连接测试失败？
- 检查 API Base URL 是否为正确的根地址（如 OpenAI 协议一般为 `https://api.openai.com` 或三方中转根路径，无需重复附带 `/v1/chat/completions`）；
- 检查 API Key 是否有效且账户余额充足；
- 本地无鉴权服务（如 Ollama）API Key 可直接留空。

### Q3: 恢复官方直连模式会清空我的配置吗？
- **不会**。恢复操作仅撤销对宿主配置文件的接管修改，不会删除任何已保存的 Provider、自定义模型或应用设置，本地代理也可继续按需运行。

---

## 🛡️ 非官方声明与免责声明

- **非官方声明**：Antigravity Studio 是独立开发的开源兼容工具，与 Google 或 Antigravity 官方团队无任何官方隶属、授权或背书关系。本项目不分发 Antigravity 专有二进制或闭源源码。
- **免责声明**：本项目仅供个人学习、技术研究与开发效率提升使用。使用本项目即表示您同意遵守各上游服务商的服务条款与相关法律法规，自行承担配置第三方 API Key 与网络代理的相关风险。

---

## 📄 许可证

本项目采用 [MIT License](LICENSE) 开源许可证。