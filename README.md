# Antigravity Studio

简体中文 · [English](README.en.md) · [更新日志](CHANGELOG.md)

Antigravity Studio 是一款桌面端多账号管理与本地模型代理工具，适用于 Antigravity App、Antigravity IDE、Antigravity CLI 和 VS Code 插件。把 Google 账号、额度和第三方模型放在一起管理，减少在不同客户端之间来回配置的麻烦。

账号、配置和用量记录保存在本地；模型请求仍会发送给 Google 或你配置的服务商，并非离线运行。

![运行概览](img/zh/overview.png)

## 主要功能

### 账号与额度

支持添加、导入和切换账号，提供精简账号视图，方便集中查看额度与状态。Argon 额度结合账号实际权限展示；使用独立额度池还是共享 Gemini 额度池，以账号返回的额度信息为准，不能仅凭模型名称或套餐判断是否可用。

账号池提供四种调度策略：

- **故障转移**：优先使用主号，不可用时尝试符合条件的备用账号。
- **重置优先**：优先使用额度重置时间更早的合格账号。
- **粘性会话**：尽量让同一会话及其子任务使用同一账号，减少不必要的切换。
- **负载均衡**：在可用账号之间按权重分配请求。

可设置额度阈值，也可为每个账号单独配置 HTTP / SOCKS5 出站代理。调度仍受模型权限、剩余额度和服务商限制约束。

![账号额度](img/zh/account_quota.png)
![账号池调度](img/zh/account_pool_relay.png)
![账号切换](img/zh/account_switch.png)

### 自带 API Key 接入模型

通过服务商预设接入 OpenAI、Anthropic、Gemini、DeepSeek、OpenRouter 等，也可配置本地 Ollama。思考输出、图片和工具调用是否可用，取决于模型及服务商支持。

支持连接测试和上下文设置，但调大设置值不能突破模型的真实上下文限制，也不保证长对话内容始终完整保留。

![模型管理](img/zh/model_management.png)
![服务商预设](img/zh/provider_presets.png)

### 客户端接管与运行概览

为四种受支持的客户端开启代理或恢复直连；CLI 可复制启动命令使用。概览展示请求路由、使用的账号与模型、在途状态及调度原因，方便排查请求去了哪里。

### 会话整理与 AI 标题

集中浏览不同客户端的历史会话，按项目或客户端筛选、搜索。需要生成 AI 标题时，工具会读取会话内容并发送给你选择的模型；不保证每次都读取或发送完整对话，使用前请确认内容适合交给该服务商处理。

![会话管理](img/zh/session_management.png)
![AI 标题](img/zh/session_rename_ai.png)

### 用量、模型速度对比与网络体检

查看调用次数、Token 用量、费用估算和请求日志；通过模型速度对比查看首字延迟、生成速度等指标。结果会受网络、负载和请求内容影响，费用估算不等于服务商账单。网络体检用于辅助排查连接问题。

![用量统计](img/zh/usage_statistics.png)
![调用日志](img/zh/activity_logs.png)

## 安装与快速上手

### 下载与安装

到 [GitHub Releases](https://github.com/yuzhiqiang1993/antigravity-studio/releases) 选择已发布版本。下表中的 `<version>` 请替换为该 Release 的版本号；系统版本要求和可用安装包以对应 Release 说明为准。

| 平台 | 安装包 |
| :--- | :--- |
| macOS · Apple Silicon | `Antigravity-Studio-<version>-macos-arm64.dmg` |
| macOS · Intel | `Antigravity-Studio-<version>-macos-x64.dmg` |
| Windows · x64 | `Antigravity-Studio-<version>-windows-x64.exe` |

macOS 打开 DMG 后将应用放入「应用程序」；Windows 运行安装程序，并确保已安装 Microsoft Edge WebView2 运行库。不要默认所有 Windows 10 环境都兼容，请先查看所选版本的要求。

升级前先退出旧版并备份应用数据，避免新旧版本同时访问同一份数据。关闭窗口可能只是隐藏到托盘，请确认已完全退出。

### 开始使用

1. 在「账号」页添加 Google 账号，按需开启账号池并选择调度策略；或在「模型」页添加服务商、填写 API Key 并启用所需模型。
2. 在「概览」页为目标客户端开启代理。使用 CLI 时，按界面提供的命令启动。
3. 在客户端选择模型发起请求，通过概览和日志确认连接状态。若需要重启客户端，请先保存工作。

## FAQ

### 默认端口 8321 被占用怎么办？

工具不会自动换端口。请在设置中改为可用端口，再按界面提示更新相关客户端配置；仍指向旧端口的客户端无法连接新地址。

### 自动调度和切换客户端登录账号有什么区别？

自动调度为模型请求选择账号，不等于更换客户端的登录账号。遇到限流或错误时可能尝试其他账号，但不能保证重试成功或会话不中断。

切换客户端登录账号前请保存工作：操作可能重启 Antigravity App、IDE 或整个 VS Code，而不只是当前窗口。CLI 需要手动重启。

### 已开启代理，为什么看不到模型或请求失败？

先检查模型是否启用、代理是否运行、客户端配置的端口是否一致，再检查账号实际模型权限、额度和服务商连接。可结合网络体检与日志排查；需要重新加载配置时，保存工作后退出并重启客户端。

### 数据存在哪里？哪些内容会发出去？

账号凭据、配置、用量和日志保存在本地。模型请求及其携带的内容会发送给 Google 或你配置的服务商；AI 标题功能也会读取会话内容并提交给所选模型。请求会遵循配置的出站代理或服务商地址，数据处理还取决于这些服务的政策。分享日志、截图或备份前，请移除凭据和敏感内容。

### macOS 提示“已损坏”或“无法打开”怎么办？

这类提示可能与 Gatekeeper、签名或公证状态有关，也可能是下载不完整、文件损坏或被篡改，不能一概归因于未签名。请先确认安装包来自可信的 Release，核对该版本提供的校验信息，必要时重新下载；无法确认来源或完整性时不要绕过警告。

只有在确认来源与完整性可信、且问题确由隔离标记引起后，才考虑对已安装的应用执行以下命令。它会移除该应用的隔离标记，不会修复文件损坏，也不要据此全局关闭系统安全检查。

```bash
sudo xattr -rd com.apple.quarantine "/Applications/Antigravity Studio.app"
```

### Studio 和 Cockpit 怎么选？

Studio 负责桌面端账号池、模型代理和跨客户端管理；[Antigravity IDE Cockpit](https://open-vsx.org/extension/yuzhiqiang/antigravity-ide-cockpit) 侧重 IDE 内的账号切换与上下文用量查看。按需选择，也可配合使用。

## 反馈与免责声明

- 问题与建议：[GitHub Issues](https://github.com/yuzhiqiang1993/antigravity-studio/issues)。请附应用版本、系统版本、复现步骤和脱敏后的错误信息，不要上传 Token、API Key 或私人会话。
- QQ 交流群：`613214996`，用于配置交流、答疑和版本通知。
- 许可条款见 [MIT License](LICENSE)。本工具为独立第三方项目，与 Google 或 Antigravity 官方无关联。

请遵守各服务商的使用条款。多账号调度和独立代理不代表可以绕过权限、配额或风控，也不能保证防封号、持续可用或请求不中断。服务商政策可能变化；涉及重要账号和数据时，请自行评估风险并做好备份。
