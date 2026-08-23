# 更新日志 (CHANGELOG)

本项目遵循 [Semantic Versioning 2.0.0](https://semver.org/lang/zh-CN/) 语义化版本规范。

---

## [1.0.1] - 2026-08-23

### 🚀 优化与修复

- 修复 Antigravity IDE / App 宿主进程误判问题，精确收敛 macOS 下 App Bundle 匹配特征并规范运行状态徽章逻辑
- 统一宿主未运行状态文案为「已安装」
- 优化「停止代理」按钮为醒目的语义警告色
- 将模型「视觉」能力文案与标签统一规范更名为「多模态」（Multimodal）
- 重构「添加上游服务」模型选择卡片视觉样式，去除表面色污染，带来更优雅的选中高光与微底效果

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
