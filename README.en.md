# Antigravity Studio

[简体中文](README.md) · English · [Changelog](CHANGELOG.md)

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Platform: macOS | Windows](https://img.shields.io/badge/Platform-macOS%20%7C%20Windows-lightgrey.svg)](#installation--download)
[![Latest Release](https://img.shields.io/github/v/release/yuzhiqiang1993/antigravity-studio?color=green)](https://github.com/yuzhiqiang1993/antigravity-studio/releases/latest)
[![QQ Group: 613214996](https://img.shields.io/badge/QQ%20Group-613214996-12B7F5.svg?logo=tencentqq&logoColor=white)](#community--feedback)

Antigravity Studio is a desktop companion and local proxy tool built for Antigravity (including the IDE, standalone desktop app, terminal CLI, and VS Code extension).

When coding with Antigravity daily, managing multiple accounts is tedious, frequent requests easily hit 429 rate limits, and you can't directly use Claude, DeepSeek, or local models. Antigravity Studio solves these exact frictions: pool multiple Google accounts with automatic dispatching, prioritize quotas that are about to expire, seamlessly hand off to backup accounts when rate-limited, and plug in your own API keys for third-party models. Everything runs locally on your machine without intermediate servers.

QQ Community: `613214996` (troubleshooting, config sharing, and release updates)

<p align="center">
  <img src="img/zh/overview.png" alt="Antigravity Studio Overview" width="100%" />
</p>

---

## What Problems Does It Solve?

If you use Antigravity regularly, you've probably run into these issues:

1. **Wasted quotas vs. annoying 429 rate limits**: Official quotas roll every 5 hours and reset weekly. Unused quota expires without carrying over. When you're in the flow, a single account frequently hits rate limits while your idle backup accounts sit unused. Switching accounts manually across multiple tools is repetitive and breaks focus.
2. **Multi-window collisions and sluggish responses**: Opening several projects at once crowds all requests onto a single account, triggering concurrency bottlenecks. Inconsistent switching also breaks the provider's server-side Prompt Cache, forcing slow, full-context recalculations on every turn.
3. **Can't use third-party models in Antigravity**: Official model options are limited. You can't directly call Claude, DeepSeek (with full streaming reasoning thoughts), GPT, or local Ollama models in your workflow.
4. **Important code gets summarized away too early**: The default context compression threshold is relatively low. In long sessions, early code and architectural context often get lost in summary.
5. **Scattered history across different clients**: Working across IDE, desktop app, and CLI leaves conversations fragmented in different places. Default titles look identical, making it hard to find what you worked on.
6. **Port collisions breaking proxy startup**: Default port 8321 is sometimes taken by another program, preventing the tool from launching smoothly.

---

## Key Features

### 1. Smart Multi-Account Pooling & Auto-Relay
Add your Google accounts to a shared pool, and the local proxy routes traffic automatically according to your strategy:

- **Failover (Recommended Default)**: Stick to your primary login account during normal use. When its quota runs low (e.g. below 5%) or hits a 429 rate limit, the tool automatically hands off to the backup account with the earliest weekly reset. Once the primary recovers, traffic switches back. If no accounts are available, it halts cleanly instead of hammering errors.
- **Reset First**: All accounts compete together. Whichever account's weekly quota expires soonest gets picked first, squeezing out free quotas before they reset.
- **Sticky Session**: Locks each conversation window to a dedicated account. As long as the session continues, it sticks to that account to maximize server-side Prompt Cache hits and cut latency. Subagent tasks inherit the same account. It only migrates if the account hits a rate limit or runs completely dry.
- **Round Robin**: Distributes requests evenly across all healthy accounts to balance high-frequency spikes.
- **Thresholds & Independent Outbound Proxies**: Set your own quota alert line (e.g. "switch when quota drops below 5%"). You can also attach distinct HTTP / SOCKS5 proxies to each account so they don't share the same exit IP.

![Account Quotas](img/zh/account_quota.png)
![Dispatch Settings](img/zh/account_pool_relay.png)
![Switch Account](img/zh/account_switch.png)

---

### 2. Bring Your Own Key (BYOK) for Any Model
Connect your own models directly into Antigravity:

- **34+ Provider Presets**: OpenAI, Anthropic, Gemini, DeepSeek, Ollama, OpenRouter, SiliconFlow, DashScope, Kimi, Zhipu, and more. Just plug in your API key.
- **Reasoning Chains & Tool Calling**: Full streaming support for DeepSeek's thinking process, image inputs, and code tool calls.
- **Custom Context Windows**: Expand model context thresholds (e.g. to 512K or 1M) to delay automatic summarization and preserve your code.
- **Strictly Local & Direct**: Test connections with one click. Requests travel directly from your computer to the model provider, never through any third-party middleman.

![Model Management](img/zh/model_management.png)
![Provider Presets](img/zh/provider_presets.png)

---

### 3. Clear Routing Insights & Live Dashboard
Never guess which account handled your request or why:

- **Plain-English Causal Banner**: The bottom bar clearly displays the reason for each routing decision, such as "Session from Antigravity IDE · Routed to backup account: Primary quota below 5%", along with latency, token usage, and cache hit rates.
- **Active Card Glow**: The account currently answering illuminates with a neat streamer border, color-coded by model family (Gemini blue/purple, Claude orange/red), and fades out when finished.
- **Auto Port Shifting**: If port 8321 is busy, the tool automatically tries the next open port (like 8322). A banner pops up on the dashboard so you can update your editor settings in one click.

---

### 4. One-Click Host Integration & Safe Account Switching
- **Auto-Detects Clients**: Recognizes local installations of Antigravity 2.0 Desktop, Antigravity IDE, VS Code Extension, and terminal CLI.
- **One-Click Proxy Toggle**: Enable or disable proxy routing per client directly from the UI, or copy terminal launch commands for CLI. Restore direct official connections at any time without touching official installation files.
- **Safe Transactional Switching**: Switching active login accounts uses file locking and automated backups in the background to prevent corrupting editor config files.

---

### 5. Unified Session Manager & AI Renaming
- **Find Past Conversations Anywhere**: Browse sessions across IDE, Desktop, CLI, and VS Code in one place. Filter and search by project or client.
- **AI Reads & Names Sessions**: When generic titles get confusing, ask AI to read the full conversation and summarize a concise, accurate title, with multiple candidates to choose from.
- **Privacy First**: Conversation details open locally in their original editors. The tool only indexes metadata.

![Session Management](img/zh/session_management.png)
![AI Renaming](img/zh/session_rename_ai.png)

---

### 6. Local Usage Analytics & Request Logs
- **Track Tokens & Costs**: Review daily and weekly token consumption, request counts, input/output/reasoning breakdowns, and estimated costs.
- **Real Speeds & Latency**: Measures genuine Time to First Token (TTFT), generation speeds, and queue durations using monotonic local clocks. Copy cURL commands with one click to reproduce in terminal.
- **Everything Stored Locally**: Logs live in a local SQLite database with customizable retention periods and one-click cleanup.

![Usage Statistics](img/zh/usage_statistics.png)
![Health Doctor](img/zh/health_doctor.png)

---

## How It Works

```text
Antigravity Clients (IDE / Standalone App / CLI / VS Code)
                     │
                     ▼ (Local requests sent to proxy port)
            Local Proxy Server (Default 8321, auto-shifts if busy)
                     │
         ┌───────────┴───────────┐
         ▼                       ▼
   Official Models (Gemini / Claude)  Third-Party Models (BYOK)
         │                       │
         ▼                       ▼ (Local protocol adapter & reasoning parser)
  Smart Account Pool Engine       DeepSeek / OpenAI / Claude / Ollama
  (Failover / Reset First / Sticky)
         │
         ▼ (Optional dedicated egress proxy per account)
   Google Official Endpoints
```

---

## Desktop App vs. IDE Extension

| Tool | Best For |
| :--- | :--- |
| **Antigravity Studio (Desktop)** | **Account pooling, automatic dispatching, BYOK model integration, local proxy**<br>Best for managing multiple Google account quotas, configuring outbound proxies, plugging in DeepSeek/Claude, and toggling proxy modes across all clients. |
| **[Antigravity IDE Cockpit (Extension)](https://open-vsx.org/extension/yuzhiqiang/antigravity-ide-cockpit)** | **In-editor account switching & real-time context token insights**<br>Runs inside Antigravity IDE for switching accounts while coding and monitoring session token usage. |

> Tip: Run **Studio Desktop** in the background for account pooling and model routing, and keep **Cockpit Extension** inside your IDE for instant in-editor checks.

👉 [Cockpit on Open VSX](https://open-vsx.org/extension/yuzhiqiang/antigravity-ide-cockpit) · [Official Site agycockpit.com](https://agycockpit.com)

---

## Installation & Download

### 1. Download Binaries

Get the installer for your system from [GitHub Releases](https://github.com/yuzhiqiang1993/antigravity-studio/releases/latest):

| Platform | Installer | Target Machine |
| :--- | :--- | :--- |
| **macOS (Apple Silicon)** | `Antigravity-Studio-x.x.x-macos-arm64.dmg` | M1 / M2 / M3 / M4 Macs |
| **macOS (Intel)** | `Antigravity-Studio-x.x.x-macos-x64.dmg` | Intel-based Macs |
| **Windows (x64)** | `Antigravity-Studio-x.x.x-windows-x64.exe` | 64-bit Windows 10 / 11 |

> **macOS says "App is damaged" or "Cannot be opened"?**
> macOS Gatekeeper blocks unsigned open-source binaries by default. Open Terminal and run:
> ```bash
> sudo xattr -rd com.apple.quarantine "/Applications/Antigravity Studio.app"
> ```

---

### 2. Getting Started in 3 Steps

1. **Add Accounts or Models**:
   - Official models: Add or import Google accounts in **Accounts**, turn on the pool switch, and select a strategy (Failover recommended);
   - Third-party models: Click **Add Provider** in **Models**, choose a platform, enter your API key, and check the models you want.
2. **Turn On Proxy**: Go to **Overview**, locate your editor (e.g. Antigravity IDE), and toggle the switch on. For CLI, copy the one-line launch command.
3. **Start Coding**: Open your editor. Your new models will appear in the model list. When calling official models, the proxy will automatically select the best account in the background.

---

## FAQ

**Q: Will switching accounts interrupt what I'm writing?**
- No. If you use Sticky Session, your active chat stays locked to the same account to protect the context cache. If you use Failover, it won't switch while your primary account is healthy—it only hands off when the primary runs low or gets throttled. If every account in the pool is empty, it pauses cleanly with a notice instead of throwing broken errors.

**Q: Why does "Reset First" help save quota?**
- Unused Google quotas expire when their reset cycle hits. Reset First watches each account's countdown and uses whichever account clears earliest, helping you burn expiring free quota before it vanishes.

**Q: What does the threshold mean?**
- It means "switch accounts when quota drops below X%" (5% by default). When an account's 5-hour quota falls below 5%, the system marks it as running low and finds a healthy account to take over.

**Q: Can each account use a different proxy?**
- Yes. You can set individual HTTP or SOCKS5 proxies on each account card. Token refreshes, quota lookups, and requests will all travel through that dedicated proxy, preventing multiple accounts from sharing the same exit IP.

**Q: What if port 8321 is already in use?**
- The tool automatically looks for the next open port (like 8322). A yellow banner will appear on the dashboard where you can sync the new port to your editor settings with one click.

**Q: I turned on proxy, but don't see new models in my editor?**
- Check that the model is checked in **Models**;
- Check that the proxy shows running in green in **Overview** and your editor card says "Connected";
- Quit and restart your editor so it reloads its configuration.

**Q: Will turning off the proxy erase my settings?**
- No. Turning off the proxy simply points your editor back to official servers. All your saved accounts, API keys, models, and usage logs stay safely on your computer.

**Q: Are my API keys, accounts, and code safe?**
- Yes. Antigravity Studio is completely open-source and runs entirely on your local machine. There are no tracking backends or relay servers. All credentials stay local, and requests go straight from your machine to Google or your configured providers.

---

## Community & Feedback

- **QQ Community**: `613214996` (Daily Q&A, configuration sharing, and updates)
- **Bug Reports & Requests**: Submit issues on [GitHub Issues](https://github.com/yuzhiqiang1993/antigravity-studio/issues)

---

## License & Disclaimer

- **License**: Released under the [MIT License](LICENSE).
- **Disclaimer**:
  - This is an independent open-source tool and is not affiliated with Google or Antigravity. Please use it within each provider's acceptable use policies.
  - Official quota and risk policies can change at any time. Any account limitations, flags, or restrictions resulting from using this tool are your own responsibility. If you have concerns about impacts to personal or work accounts, please use discretion.
