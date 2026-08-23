# 更新日志 (CHANGELOG)

本项目遵循 [Semantic Versioning 2.0.0](https://semver.org/lang/zh-CN/) 语义化版本规范。

---

## [1.0.0] - 2026-08-23

Antigravity Studio 首个正式版本发布 🎉

### ✨ 核心功能与特性

- **BYOK 多模型智能接入**：支持 OpenAI、Anthropic Claude、Google Gemini、DeepSeek、Ollama 等主流大模型统一接入，提供强大的双向协议自动转换能力
- **Thinking 推理分级映射**：自动聚合 Claude Thinking、DeepSeek R1、OpenAI o-series 推理档位，向宿主端优雅暴露多级推理子模型
- **官方模型治理**：可在 UI 上自由禁用或隐藏官方内置模型，保持模型选择列表极简清爽
- **智能上下文自动压缩**：按模型独立配置容量上限（128K~1M），自定义触发阈值与专用摘要压缩模型
- **全链路健康体检 (Doctor Engine)**：一键检测网络、配置、代理服务、宿主环境与 Provider 连通性，异常项支持一键自愈修复
- **跨平台宿主一键接管**：高性能本地代理引擎自动监听，安全接管 Antigravity IDE / App / CLI，支持随时一键恢复官方直连
- **原生应用内更新升级**：支持版本更新日志 Markdown 富文本优雅渲染、应用内流式下载（实时速率与进度）及跨平台一键安装
- **多端原生支持与双语体验**：原生适配 macOS（Apple Silicon / Intel）与 Windows 64位桌面环境，支持中英双语无缝切换

> ⚠️ **macOS 用户注意**：首次安装后如果提示"已损坏"或"无法打开"，请在终端执行以下命令解除系统的 Gatekeeper 隔离限制：
> ```bash
> sudo xattr -rd com.apple.quarantine "/Applications/Antigravity Studio.app"
> ```
