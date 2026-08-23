# 更新日志 (CHANGELOG)

本项目遵循 [Semantic Versioning 2.0.0](https://semver.org/lang/zh-CN/) 语义化版本规范。

---

## [1.0.0] - 2026-08-23

**Antigravity Studio 首个正式版本发布 🎉**

### ✨ 核心特性

- **BYOK 统一模型接入中心**：支持 OpenAI Chat Completions / Responses、Anthropic Messages、Google Gemini 与 Ollama 本地模型全协议原生转换与路由透传
- **Thinking / Reasoning 推理等级映射**：原生聚类 Claude Thinking、DeepSeek R1、OpenAI o-series 推理档位（Low / Medium / High / Max / Auto），自动向宿主暴露推理子菜单
- **官方模型自由治理**：一键禁用或隐藏不需要的官方内置模型，保持模型选择器极致清爽
- **内置主流 Provider 预设**：OpenAI、Anthropic、Gemini、xAI、DeepSeek、OpenRouter、One API、Ollama 等，3 步快速接入

### 🧠 智能上下文压缩

- **模型级独立容量策略**：提供 128K、200K、256K、372K、1M 等多档物理容量预设，打破官方过早压缩限制
- **精细化阈值调节**：支持快捷百分比或精确 Token 微调压缩触发阈值、Checkpoint 上限与输出预留
- **独立压缩工作模型**：可将压缩任务指定给轻量模型（如 Gemini Flash），兼顾速度与成本

### 🩺 全链路体检与自愈诊断

- **6 大维度深度体检**：网络连通性、本地配置完整性、代理服务健康度、宿主环境感知、Provider 密钥有效性与延迟
- **智能一键自愈**：异常项目精准定位原因与一键修复建议

### 🌐 跨平台宿主无感接管

- **本地智能代理引擎**：默认监听 `127.0.0.1:8321`，端口冲突自动寻址；官方模型透传，自定义模型智能路由
- **macOS / Windows 双平台接管**：安全接管 Antigravity IDE、App 与 CLI，随时一键恢复官方直连

### 🎨 UI 与交互

- **Material 3 设计系统**：深度适配浅色与深色主题，桌面端紧凑视觉优化
- **中英多语言即时切换**：运行时一键无感切换简体中文与 English
