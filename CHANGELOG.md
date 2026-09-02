# 更新日志 (CHANGELOG)

本项目遵循 [Semantic Versioning 2.0.0](https://semver.org/lang/zh-CN/) 语义化版本规范。

## [1.3.0] - 2026-09-02

### 🇨🇳 中文

#### ✨ 新增功能
- **支持官方 Gemini 3.8 Flash**：完整接入最新的 Gemini 3.8 Flash（包含 High / Medium / Low 三档推理变体）。调整了官方接口的探测优先级，并在 IDE 使用中自动同步最新可用模型，不再需要反复手动刷新
- **用量统计页面改版**：重新设计了用量看板，支持按模型筛选、定时刷新和自定义日期范围；优化了走势图的刻度排版，文字不再互相重叠；同时接入了最新的模型价格数据，费用算得更准
- **软件更新安全性增强**：客户端安装包下载加入了 Ed25519 签名验证和哈希校验，防止更新文件在传输过程中被篡改

#### ⚡ 体验与性能优化
- **移除容易误导的“卡顿”提示**：大模型深度思考和调用工具时的自然停顿容易被机械算法误判为网络卡顿。现已彻底去掉速度统计中的“卡顿 X 次”红标，页面更清爽，重点保留首字延迟（TTFT）、输出速度（TPS）和会话总耗时等客观指标
- **大幅缩短启动等待时间**：后台账号探测、用量扫描和检查更新改为并行处理，在弱网环境下打开软件的速度提升明显
- **降低系统资源占用**：优化了 macOS 系统代理检测的缓存机制，不再频繁调用系统底层命令；退出软件时自动清理后台任务，避免残留僵尸进程
- **长会话稳定性提升**：为长思考会话增加了租约锁保护，模型正在深度推理时不会因为后台并发切号而意外中断

#### 🐛 问题修复
- **修复潜在闪退隐患**：排查并消除了界面数据解析中残留的强制空断言，极端空数据场景下运行更稳定
- **过滤探活日志干扰**：自动忽略代理根路径的心跳探测请求，调用日志列表不再被无用探针刷屏

---

### 🌐 English

#### ✨ New Features
- **Gemini 3.8 Flash Support**: Added full support for the latest Gemini 3.8 Flash models (High, Medium, and Low). Optimized official API endpoint prioritization and added real-time background catalog sync with the IDE.
- **Redesigned Usage Dashboard**: Refreshed usage page with model filtering, auto-refresh options, and custom date ranges. Fixed overlapping labels on charts and updated real-time pricing benchmarks.
- **Secure App Updates**: Added Ed25519 signature and SHA-256 integrity verification to release downloads to prevent package tampering.

#### ⚡ Improvements & Performance
- **Removed Misleading "Stall" Badges**: Removed the confusing "stalls" counter in latency dialogs. Reasoning pauses and tool calls are normal for LLMs; metrics now focus strictly on TTFT, generation speed (TPS), and total duration.
- **Faster Startup**: Decoupled background initialization tasks to run in parallel, significantly cutting down launch wait times on slower networks.
- **Lower Resource Usage on macOS**: Added caching for system proxy detection to avoid redundant `scutil` calls, and added clean shutdown hooks to prevent zombie processes.
- **Interruption Protection**: Added workflow lease locks to ensure long-thinking generation streams aren't interrupted by background account switching.

#### 🐛 Bug Fixes
- **Stability**: Fixed unsafe unwraps across UI data paths to prevent crashes on edge-case empty data.
- **Cleaner Logs**: Silenced root health check probes so they no longer clutter activity logs.

---

## [1.2.3] - 2026-08-31

### 🇨🇳 中文

#### ✨ 新增功能
- **流式响应时序追踪与输出速率度量**：调用日志全链路支持流式响应时序追踪（TTFT 首字耗时、会话总耗时、输出 Token 计数），实时计算输出生成速率（Tokens/sec）与双层时序胶囊
- **活动日志智能筛选与接口语义化**：重构筛选面板（加宽至 900dp，界面更通透），支持“仅看会话生成 (Chat / Stream)”一键切换，并自动在指标统计中聚焦核心会话；日志最大缓存容量扩容至 2000 条
- **Cockpit 插件生态联动推广与智能检测**：在运行概览新增 Antigravity Cockpit 插件联动推荐横幅，内置 `CockpitPluginDetector` 智能跨平台检测用户是否已安装插件，已安装自动静默隐藏

#### 🐛 问题修复
- **Release 独立安装包 SQL/Naming 模块缺失修复**：桌面端打包配置补充 `java.sql` 与 `java.naming` 运行时模块，彻底解决独立 App 在生产环境下 SQLite 驱动缺失导致的账号探测失效问题

---

### 🌐 English

#### ✨ New Features
- **Streaming Timing Metrics & Output Speed Tracker**: Added full-link streaming response lifecycle metrics (TTFT, Total Duration, Output Tokens) with real-time output throughput (Tokens/sec) calculation and dual-layer visual badges.
- **Enhanced Activity Filtering & Endpoint Semantics**: Redesigned filter modal (expanded to 900dp) with a 1-click "Chat / Stream Only" filter tab, semantic endpoint labels, and dynamic activity KPI recalculations; expanded log buffer to 2000 entries.
- **Cockpit Extension Ecosystem Integration & Auto-Detection**: Integrated Antigravity Cockpit extension promotion banner in overview with smart local installation prober (`CockpitPluginDetector`) that automatically hides the banner for existing users.

#### 🐛 Bug Fixes
- **Standalone App Distribution SQL/Naming Module Fix**: Added missing `java.sql` and `java.naming` modules to native distribution package to resolve SQLite driver runtime initialization failures in Release builds.

---

## [1.2.2] - 2026-08-30

### 🇨🇳 中文

#### ✨ 新增功能
- **首字延迟与总耗时双指标统计体系**：活动分析与模型耗时面板全面升级为“首字耗时 (TTFT)”与“会话总耗时”双指标并列视图，搭配动态分级色标与双层进度条，直观反映流式响应首字速度与整体会话处理时长
- **模型耗时明细弹窗 (Model Latency Detail)**：点击活动指标卡片中的“模型首字耗时”即可快速唤起全局模型耗时明细弹窗，支持模型关键字过滤、极值区间（Min ~ Max）透视与请求完成量统计

#### ⚡ 体验与性能优化
- **活动指标卡片聚焦首字响应**：概览与调用日志顶栏的主耗时指标统一切换为首字延迟（TTFT），更精准度量大模型实时交互响应体感

#### 🐛 问题修复
- **宿主账号探测前置阻断修复**：修复 IDE 与 App/CLI 宿主在未配置凭据或检测环境时的前置拦截与异常判断，优化宿主活跃账号的自动探测与识别规则

---

### 🌐 English

#### ✨ New Features
- **Dual TTFT & Total Duration Metric System**: Upgraded activity latency analytics to display both "Time-to-First-Token (TTFT)" and "Total Session Duration" side-by-side with dynamic tiered color coding and dual-layer visual progress bars.
- **Model Latency Breakdown Dialog**: Clicking on the TTFT activity metric opens an interactive model latency modal, supporting keyword filtering, min-max range inspection, and completed request tracking.

#### ⚡ Improvements & Performance
- **TTFT-Focused Activity KPI**: Aligned primary latency indicators across overview and activity views with Time-to-First-Token for a more accurate reflection of interactive streaming responsiveness.

#### 🐛 Bug Fixes
- **Host Account Probe Unblocking**: Fixed pre-check blockers during account probing across IDE, App, and CLI hosts, refining multi-host credential detection and identification rules.

---

## [1.2.1] - 2026-08-30

### 🇨🇳 中文

#### ✨ 新增功能
- **全景镂空聚光灯新手指引 (Spotlight Tour)**：内置两阶段 12 步超详细深度漫游引导，涵盖左侧主导航骨架漫游与各功能页核心聚焦（本地代理中转、客户端零配置一键接管、账号管理与防窥、第三方服务直接接入宿主列表与自由压缩策略控制、请求流审计、端口与主题定制、关于页随时重温向导）
- **高阶动效与双区域联合高亮**：采用 Spring 弹性阻尼镜头插值与文本淡入滑移动画，右侧详情页聚焦时左侧对应 Tab 同步镂空呼吸高亮；支持视口智能防溢出与自适应上下翻转

#### 🐛 问题修复
- **App 代理接入权限与环境变量重构**：修复 App 代理注入权限死锁问题，重构跨平台环境变量注入与安全自愈机制

---

### 🌐 English

#### ✨ New Features
- **Comprehensive Spotlight Tour System**: Introduced a two-phase, 12-step onboarding tour covering sidebar skeleton navigation and in-depth page feature walkthroughs (local proxy hub, zero-config one-click integration, account switching & privacy masking, custom model integration directly into host lists with flexible compression strategies, activity stream audit, network & theme settings, and reopening anytime).
- **Smooth Physics Animation & Dual Cutout Highlighting**: Powered by Spring-damped transitions, animated text fades, dual-area union cutouts (highlighting target content and associated sidebar tab simultaneously), and smart viewport clamping with adaptive top/bottom flipping.

#### 🐛 Bug Fixes
- **App Proxy Permissions & Environment Refactoring**: Fixed permission deadlocks during App proxy injection, refactoring cross-platform environment variable propagation and self-healing mechanisms.

---

## [1.2.0] - 2026-08-30

### 🇨🇳 中文

#### ✨ 新增功能
- **多账号无缝切换与事务安全**：支持全宿主环境（IDE / App / CLI）的活跃账号秒级一键切换，引入事务机制与自动回滚保障凭据一致性
- **8 大主题微浸润卡片体系 (Tinted Tonal Surfaces)**：全面重塑视觉设计，新增「晨曦粉紫」、「清冽海蓝」、白曜纯白蓝调等 8 套主题，结合微浸润卡片与 Logo 动态主题变色
- **macOS 沉浸式透明标题栏**：落地 macOS 原生全屏透明标题栏与侧边栏红绿灯安全避让，与系统外观浑然一体
- **出站代理与网络健康体检**：支持配置 HTTP/Socks5 出站网络代理，自动感知 macOS 系统代理，并将代理连通性纳入健康体检 (Doctor)
- **调用日志多维筛选与报文详情**：支持按客户端来源、状态码与耗时进行多维组合过滤，调试模式下支持查看请求与响应完整报文

#### ⚡ 体验与性能优化
- **宿主探查与重启亚秒级加速**：优化 Windows 注册表内存缓存与守护线程异步广播，消除子进程开销；强杀采用树状查杀 (`taskkill /F /T`) 消除等待延迟
- **流式代理首字与异常防卡死**：优化代理流式响应首字延迟（TTFT），支持多层瞬态重试与流式首块缓冲重试，有效防止上游异常时 IDE 挂起
- **结构化客户端识别**：智能识别官方 IDE 结构化 User-Agent、插件端及跨端请求来源并在日志与概览中精确标记
- **国内构建与镜像加速**：配置阿里云 Maven 镜像源，提升国内开发与 CI 依赖解析效率

#### 🐛 问题修复
- **Windows 平台 App 代理注入与 Shim 修复**：内置原生 PE Shim 二进制程序，彻底解决 Windows 端 Antigravity App 代理注入、端口探测与进程拉起异常
- **Shim 状态自愈与残留清理**：修复 App 代理半成品接入时的状态误报，支持检测 `.original` 备份残留并在重置时安全原子还原
- **CI 与单测时序稳定性**：放宽流式尝试单测空闲超时，收敛 CI 矩阵至 macOS 与 Windows 双目标平台，消除时序竞态偶发报错

---

### 🌐 English

#### ✨ New Features
- **Seamless Multi-Account Switching & Transaction Safety**: Supported 1-click account switching across IDE, App, and CLI hosts with transactional rollback for credential consistency.
- **8-Theme Tinted Tonal Surfaces**: Overhauled UI design with 8 theme palettes (including Dawn Lilac, Glacial Blue, Pure White/Azure), featuring tinted tonal surface cards and dynamic logo color adaptability.
- **macOS Immersive Transparent Title Bar**: Integrated native macOS transparent title bar styling with traffic light button avoidance for a seamless OS appearance.
- **Outbound Proxy & Network Doctor**: Added support for HTTP/Socks5 outbound network proxies, auto-detection for macOS system proxies, and proxy connectivity health checks.
- **Activity Multi-Dimensional Filtering & Full Payload Inspection**: Enabled multi-criteria log filtering by client source, status code, and duration, with full request/response payload viewing in Debug mode.

#### ⚡ Improvements & Performance
- **Sub-Second Host Discovery & Restart**: Introduced registry memory caching and async daemon broadcasting to eliminate Windows child process overhead; used tree kill (`taskkill /F /T`) to eliminate termination delays.
- **Stream TTFT & Hanging Prevention**: Optimized proxy stream Time-To-First-Token (TTFT), supporting multi-tier transient retries and first-chunk buffer retries to prevent IDE hangs on upstream glitches.
- **Structured Client Identification**: Accurately detected and highlighted official IDE structured User-Agents, plugin clients, and cross-platform request origins.
- **Accelerated Build Resolution**: Added Aliyun Maven mirrors for faster domestic Gradle builds and CI dependency resolution.

#### 🐛 Bug Fixes
- **Windows App Proxy Injection & Native Shim**: Embedded native PE shim binaries, completely fixing Antigravity App proxy injection, port probing, and process launch issues on Windows.
- **Shim Self-Healing & Residue Cleanup**: Resolved false "installation failed" errors during half-baked injections, supporting atomic restoration and residue cleanup.
- **CI & Test Timing Stability**: Extended idle timeouts in streaming attempt unit tests and scoped CI quality matrix to macOS and Windows targets.

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
