# 更新日志 (CHANGELOG)

本项目遵循 [Semantic Versioning 2.0.0](https://semver.org/lang/zh-CN/) 语义化版本规范。

---

## [1.1.0] - 2026-08-27

### 🇨🇳 中文

#### ✨ 新增功能
- **多账号与配额中心**：新增账号配额管理中心，支持多账号集中管理、宿主环境活跃账号自动探测，以及账号凭据复制与 JSON 导出备份
- **官方配额直查与快照缓存**：支持官方配额直连查询与代理模式切换，配合磁盘快照缓存提升配额刷新速度
- **Material 3 主题调色板**：升级 Material Design 3 色彩体系，支持多套个性化主题配色无缝切换

#### 🎨 体验优化
- **桌面交互与动效体系**：统一桌面按钮规范、悬停动效与下拉菜单交互，优化账号卡片与配额数据动态展示
- **概览页布局优化**：重构运行概览页面，清晰展示各宿主（IDE / App / CLI）的活跃账号与状态看板
- **多语言与文案规范**：全面优化中英文多语言词条，统一各功能模块的术语与操作反馈

#### 🐛 问题修复
- **Windows 兼容性修复**：修复 Windows 环境下宿主凭据注入、Shim 管理与跨平台权限兼容问题
- **宿主路径检测隔离**：严格隔离 IDE、App 与 CLI 的路径扫描与候选发现逻辑，避免环境误判

---

### 🌐 English

#### ✨ New Features
- **Multi-Account & Quota Center**: Added an account quota management center with multi-account centralized management, active host account auto-detection, credential copying, and JSON export/backup.
- **Direct Quota Probe & Snapshot Cache**: Supported direct official quota querying and proxy mode toggling, combined with disk snapshot caching for instant quota display.
- **Material 3 Theme Palettes**: Upgraded the Material Design 3 color system with seamless switching across multiple custom theme palettes.

#### 🎨 Improvements
- **Desktop UI & Motion System**: Unified desktop button styling, hover animations, and dropdown menus, while enhancing dynamic motion for account cards and quota metrics.
- **Overview Layout Enhancement**: Refactored the overview screen layout to clearly showcase active accounts and health status across IDE, App, and CLI hosts.
- **i18n & Terminology Polish**: Polished English and Chinese localization strings, standardizing host application terms and interactive feedback.

#### 🐛 Bug Fixes
- **Windows Compatibility**: Fixed host credential injection, Shim management, and cross-platform permission compatibility on Windows.
- **Host Path Detection Isolation**: Strictly isolated path scanning and candidate discovery across IDE, App, and CLI to prevent environment misjudgment.

---

## [1.0.2] - 2026-08-24

### ✨ 核心功能与特性

- **耗时三级色彩分级**：对首字延迟（TTFT）与请求总耗时引入主流的三级健康色彩体系（正常极速、轻微等待、超长耗时），并在列表行与详情弹窗中动态渲染
- **上下文缓存命中率统计**：顶部指标区新增全局「缓存命中率」看板，并在日志行与调用详情中实时计算呈现单次请求的缓存命中百分比与效益色彩
- **Antigravity App 宿主拦截增强**：支持 Antigravity App 的 Language Server 包装器二进制拦截、状态感知与干净一键还原，解决与 IDE 宿主的冲突与生命周期问题
- **Checkpointer 压缩策略数学边界约束**：策略配置阶段强制保障数学约束，增加公式提示与实时输入防错校验，杜绝宿主秒报错
- **OpenAI Responses 思考与缓存解析优化**：增强 OpenAI / DeepSeek 等服务商的 `reasoning_tokens` 与 `cached_tokens` 深度提取，修复多轮会话中 Token 消耗统计
- **流式会话保活与 Fallback 自愈强化**：实现流式空闲心跳守护（`: ping`），增强网络抖动与异常中断自愈，优化 Fallback 备用路由无感切换

---

## [1.0.1] - 2026-08-24

### ✨ 核心功能与特性

- **模型目录多厂商动态解析**：支持主流大模型服务商（OpenAI、Gemini、Anthropic、DeepSeek 等）模型目录智能解析、调试详情与原始响应查看
- **连通性测试与失败排查**：新增模型连通性探测失败详情（HTTP 状态码、错误原因与原始 Body）弹窗展示与快捷重试
- **模型跳过探测与手动添加**：支持自定义模型时跳过网络探测直接添加与配置模型，保障离线或内网服务商灵活接入
- **服务商与模型卡片重构**：基于 Material 3 全面重构服务商导航与模型管理卡片体系，支持分组基础模型统计与清晰的档位标签展示
- **概览页 Hero 控制台升级**：重构本地代理服务 Hero 状态看板，支持 IDE / App / CLI 宿主环境版本动态检测与层次化健康展示
- **全局 UI 视觉体系优化**：引入全新的横向 StudioTabLayout，极简化页面顶栏并将操作按钮就近收敛至卡片内部
- **系统托盘高清矢量 (SVG) 体验升级**：内置 macOS 单色模板与 Windows 彩色矢量 SVG 图标资产，支持自适应像素密度渲染，解决系统菜单栏图标模糊问题
- **macOS 原生桌面调度优化**：优化托盘菜单与窗口调度机制，修复托盘点击激活、最小化恢复与系统级前台唤醒

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
