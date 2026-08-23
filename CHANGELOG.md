# 更新日志 (CHANGELOG)

本项目遵循 [Semantic Versioning 2.0.0](https://semver.org/lang/zh-CN/) 语义化版本规范。

---

## [1.0.0] - 2026-08-23

Antigravity Studio 首个正式版本发布 🎉

### ✨ 新功能

- BYOK 模型接入：支持 OpenAI、Anthropic、Google Gemini、DeepSeek、Ollama 等主流模型一键接入与多协议自动转换
- Thinking 推理等级映射：自动聚合 Claude Thinking / DeepSeek R1 / OpenAI o-series 推理档位，向宿主暴露推理子菜单
- 官方模型治理：可在 UI 上自由禁用或隐藏官方内置模型，保持模型列表清爽
- 智能上下文压缩：按模型独立配置容量上限（128K~1M），自定义压缩阈值与独立压缩工作模型
- 全链路体检诊断：一键检测网络、配置、代理服务、宿主环境与 Provider 连通性，异常项支持一键自愈
- 跨平台宿主接管：本地代理引擎自动监听，安全接管 Antigravity IDE / App / CLI，随时一键恢复官方直连
- 双平台支持：macOS（Apple Silicon + Intel）与 Windows 原生桌面应用
- 中英双语即时切换

> ⚠️ **macOS 用户注意**：首次安装如提示"已损坏"，请执行：
> ```bash
> sudo xattr -rd com.apple.quarantine "/Applications/Antigravity Studio.app"
> ```
