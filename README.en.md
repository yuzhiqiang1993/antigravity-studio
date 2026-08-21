# Antigravity Studio (AGY Studio)

[简体中文](README.md) · English

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Platform: macOS | Windows](https://img.shields.io/badge/Platform-macOS%20%7C%20Windows-lightgrey.svg)](#requirements)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.x-7F52FF.svg?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Compose Multiplatform](https://img.shields.io/badge/Compose%20Multiplatform-1.11.x-4285F4.svg?logo=jetpackcompose&logoColor=white)](https://www.jetbrains.com/lp/compose-multiplatform/)

> 🚀 **The all-in-one desktop hub and productivity studio for Antigravity AI tools.**  
> Seamlessly integrating **Local BYOK Model Proxy**, **Multi-Account Hot-Swapping**, **Key Pool Failover**, **Real-time Token & Cost Analytics**, **Smart Context Compression**, and **Session Deep Recovery**.

---

## 🌟 Why Antigravity Studio?

Antigravity is a cutting-edge AI coding assistant, but developers often face pain points: lack of native BYOK (Bring Your Own Key) support, limited built-in model choices, cumbersome account re-authentications, overly conservative context summaries, and blind spots in API token billing.

**Antigravity Studio** is built from the ground up by senior Android architects and active daily users using **Pure Kotlin & Compose Multiplatform Desktop**. It provides developers with a full-fledged, stable, and highly customizable desktop control cockpit.

---

## 🧭 Core Pillars & Features

```text
                                Antigravity Studio
  ┌─────────────────────────────────────┼─────────────────────────────────────┐
  │                                     │                                     │
  ▼                                     ▼                                     ▼
🛠️ Model Studio (BYOK Hub)           👤 Account Studio (Key Pool)          📊 Analytics Studio (Token & Cost)
• OpenAI / Claude / Gemini / Ollama   • One-click Google Hot-swapping       • Real-time Input/Output Token charts
• Local Loopback Proxy & SSE Streams  • Multi-key failover upon 429 errors  • Prompt Caching efficiency tracking
• FLUX / Midjourney / DALL-E routing  • Quota guard & health monitoring     • Custom pricing & billing export
  │                                     │                                     │
  ├─────────────────────────────────────┼─────────────────────────────────────┤
  │                                     │                                     │
  ▼                                     ▼                                     ▼
🩺 Session Doctor (Diagnostics)       🧠 Smart Checkpointer                 🌐 Network & Host Integration
• Bad checkpoint & corrupt session fix• Model-level capacity presets (128K~1M)• Dedicated SOCKS5 / HTTP proxies
• Broken tool-call block stripping    • Precision buffer & threshold tuning • Lossless macOS & Windows management
• Redacted diagnostic report generator• Isolated lightweight summary worker • System Tray persistence
```

### 1. 🛠️ BYOK Model Hub
- **Native Protocol Conversion**: High-performance transformations across OpenAI Chat/Responses, Anthropic Messages, and Gemini APIs;
- **Reasoning Aggregation**: Out-of-the-box clustering for Claude 3.7 Thinking, DeepSeek R1, and OpenAI o-series;
- **Local Model Optimization**: Pass `num_ctx`, `keep_alive`, and custom parameters to Ollama/vLLM for long context preservation;
- **Image & Multimodal Routing**: Support inline images/PDFs and route image generation requests to FLUX, Midjourney, DALL-E, SDXL.

### 2. 👤 Account & Key Pool Failover
- **Centralized Account Management**: Manage multiple Google/Antigravity accounts with zero-restart hot-swapping;
- **Key Rotation & Failover**: Pool multiple API keys per provider; automatically retry and failover within milliseconds upon `429 Too Many Requests`.

### 3. 📊 Token & Cost Analytics
- **Structured Usage Tracking**: Parse `prompt_tokens`, `completion_tokens`, and `reasoning_tokens` from streaming events;
- **Prompt Caching ROI**: Measure exact cached tokens and financial savings on Anthropic / DeepSeek;
- **Interactive Dashboards**: Daily/weekly trends, provider breakdown pie charts, and session logs export.

### 4. 🩺 Session Doctor & Deep Recovery
- **Session Auto-Healing**: Clean corrupted tool calls and resolve `bad checkpoint state` without losing workspace history;
- **Diagnostics Export**: Generate sanitized logs to troubleshoot connectivity and latency bottlenecks.

### 5. 🧠 Smart Context Checkpointer
- **Tune Compression Thresholds**: Bypass aggressive official summaries; customize trigger limits based on true physical capacities (128K to 1M).

### 6. 🌐 Dedicated Network & Host Integration
- **Isolated SOCKS5/HTTP Proxy**: Configure distinct proxy endpoints per provider to prevent TUN loopbacks and DNS pollution;
- **System Tray**: Background persistence in macOS Menu Bar and Windows Taskbar.

---

## 🏗️ Architecture & Tech Stack

Built on modern Android Clean Architecture + MVI:

- **UI Rendering**: Compose Multiplatform 1.11+ (Skiko directly via Metal / DirectX)
- **Local Proxy Engine**: Ktor Server 3.1+ (CIO non-blocking coroutines)
- **HTTP Client**: OkHttp & Ktor Client (Native HTTP / SOCKS5 proxy support)
- **Concurrency**: Kotlin Coroutines & Flow
- **Dependency Injection**: Koin 4.0+
- **Persistence**: Room KMP / SQLite + Multiplatform Settings

---

## 🚀 Getting Started

### Requirements
- **macOS** (Apple Silicon / Intel) or **Windows 10/11**
- **JDK 17** or **JDK 21** (Temurin / Azul Zulu recommended)

### Run & Build

```bash
# Run Desktop Application
./gradlew :desktopApp:run

# Run Unit & Architecture Tests
./gradlew check
```

### Packaging Installers

```bash
# macOS (.dmg)
./gradlew :desktopApp:packageDmg

# Windows (.msi / .exe)
./gradlew :desktopApp:packageMsi
```

Installers are generated under `desktopApp/build/compose/binaries/main/`.

---

## 🤝 Community & Contributing

Contributions, issues, and feature requests are welcome!

- **License**: [MIT License](LICENSE)
- **Author**: [yuzhiqiang1993](https://github.com/yuzhiqiang1993)
