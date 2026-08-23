# 更新日志 (CHANGELOG)

本项目遵循 [Semantic Versioning 2.0.0](https://semver.org/lang/zh-CN/) 语义化版本规范。

---

## [1.0.0] - 2026-08-23

**Antigravity Studio 首个正式里程碑版本发布！**  
采用纯血 **Kotlin + Compose Multiplatform Desktop** 现代架构从零构建，为 Antigravity 开发者提供全方位、高度自由、稳定可控的旗舰级桌面控制中心与 BYOK 接入套件。

### ✨ 核心特性 (Features)

- **BYOK 模型接入中心 (Model Hub)**：
  - 支持 OpenAI Chat Completions、OpenAI Responses、Anthropic Messages、Google Gemini 及 Ollama 本地模型全协议原生转换与路由透传；
  - 内置海量主流 Provider 预设（OpenAI、Anthropic、Gemini、xAI、DeepSeek、OpenRouter、CLIProxyAPI、One API、Ollama 等）；
  - 支持 3 步向上游快速接入服务并一键获取/定制模型清单；
  - 深度支持内联图像（Vision）、工具调用（Tools）与流式 SSE 响应。
- **Thinking / Reasoning 思考等级聚合映射**：
  - 原生聚类 Claude 3.7 Thinking、DeepSeek R1、OpenAI o-series 的推理档位（Low / Medium / High / Max / Auto）；
  - 自动向 Antigravity 宿主暴露推理档位子菜单，并在协议转换时完成精确映射。
- **官方原生模型自由治理**：
  - 支持在 UI 界面上一键禁用或隐藏不需要的官方内置模型，保持 IDE 与 App 模型选择器极致清爽。

---

### 🧠 智能上下文压缩 (Smart Checkpointer)

- **模型级独立容量策略**：
  - 打破官方过早触发摘要压缩的保守限制，提供 128K、200K、256K、372K、1M 等多档物理容量预设；
  - 支持通过快捷百分比或精确 Token 自由微调**压缩触发阈值**、**Checkpoint 上限**以及**输出预留 Token**；
  - 支持将压缩任务指定给独立的轻量工作模型（如 Gemini Flash），在上下文一致性、速度和成本间取得最佳平衡。

---

### 🩺 全链路体检与自愈诊断 (Doctor Diagnostics)

- **6 大核心维度深度体检**：
  - **网络连通性**：Google 官方服务、GitHub Releases API 与本地回环；
  - **本地存储**：`config.json` 完整性、读写权限与配置校验；
  - **本地代理服务**：Loopback 监听端口状态与 HTTP/SSE 通道健康度；
  - **宿主环境感知**：IDE、App、CLI 安装路径扫描、运行状态与代理接管对齐；
  - **自定义 Provider**：上游服务密钥有效性与接口响应延迟；
- **智能一键自愈**：针对异常项目提供精准定位原因与一键修复建议。

---

### 🌐 跨平台宿主无感接管 (Host & Proxy)

- **本地智能代理引擎**：
  - 默认监听 `127.0.0.1:8321`，端口冲突时自动寻找空闲 Loopback 端口；
  - 官方原生模型直接透传 Cloud Code，自定义模型智能分发至对应 Provider Adapter。
- **macOS / Windows 双平台无损接管**：
  - **Antigravity IDE**：自动管理用户 `settings.json` 中的 `jetski.cloudCodeUrl`，记录可逆 ownership；
  - **Antigravity App**：macOS 管理用户登录会话级 `CLOUD_CODE_URL`（不破坏官方签名包），Windows 管理用户环境变量与注册表；
  - **Antigravity CLI**：安全管理会话/用户环境变量，重新打开终端即可生效；
  - **安全恢复**：随时一键恢复官方直连模式，不影响任何已保存的配置。

---

### 🎨 UI 视觉与交互重构 (UI Refinements)

- **现代科技质感设计系统**：基于 Material Design 3 与微质感令牌（AppTokens）构建，深度适配浅色与深色主题；
- **分段切换器（Segmented Toggle）精致化**：标准化为 `32.dp` 桌面端黄金高度，选中项采用高对比度品牌主色与纯白文字聚焦；
- **高对比度胶囊按钮**：宿主路径按钮与未选中项增加清晰 `1.dp` 描边与高对比度文字，大幅提升视觉可读性；
- **多语言即时切换**：运行时一键无感切换 **简体中文** 与 **English**。

---

### 🚀 自动化构建与 CI/CD 矩阵 (Build & Release)

- **Gradle 原生环境隔离**：编译期自动注入 `BuildInfo` 常量，生产包默认隐藏调试入口；
- **自动化构建脚本 (`app_build.sh`)**：支持 `run`, `test`, `dist`, `build`, `assemble` 全流程自动化；
- **GitHub Actions 极速多平台矩阵**：
  - 🍏 **macOS Apple Silicon (arm64)**：原生构建 `Antigravity-Studio-1.0.0-macos-arm64.dmg`
  - 🍏 **macOS Intel (x86_64)**：在 macOS-14 上通过 x64 JDK 极速交叉编译 `Antigravity-Studio-1.0.0-macos-x64.dmg`
  - 🪟 **Windows (x64)**：原生构建 `Antigravity-Studio-1.0.0-windows-x64.exe`
  - 📋 **统一校验**：自动生成 `CHECKSUMS.txt` 并挂载发布到 GitHub Releases。
