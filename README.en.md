# Antigravity Studio

[简体中文](README.md) · English · [Changelog](CHANGELOG.md)

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Platform: macOS | Windows](https://img.shields.io/badge/Platform-macOS%20%7C%20Windows-lightgrey.svg)](#installation--download)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.x-7F52FF.svg?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Compose Multiplatform](https://img.shields.io/badge/Compose%20Multiplatform-1.11.x-4285F4.svg?logo=jetpackcompose&logoColor=white)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![Latest Release](https://img.shields.io/github/v/release/yuzhiqiang1993/antigravity-studio?color=green)](https://github.com/yuzhiqiang1993/antigravity-studio/releases/latest)

**Antigravity Studio** is a cross-platform desktop client for the Antigravity programming suite (IDE, App, CLI). It runs a local proxy that lets you bring your own API keys for third-party LLMs (such as OpenAI, Claude, DeepSeek, Gemini, Ollama, etc.), manage models, and toggle IDE integration with one click.

---

## Why Antigravity Studio?

Antigravity natively only provides a few built-in Gemini and Claude models. If you have your own subscriptions or API keys (e.g. Claude 3.7 Sonnet, DeepSeek R1, GPT-4o, Gemini 2.5 Pro, or local Ollama models), you cannot use them directly in Antigravity.

Antigravity Studio runs a lightweight local proxy (default port `8321`) that handles protocol translation on the fly:
- **Bring Your Own Key (BYOK)**: Use your own provider keys directly without third-party rate limits;
- **Full Capabilities**: Supports multimodal image inputs, tools calling, and reasoning / thinking levels;
- **Official Model Filtering**: Hide or disable built-in models you don't need to keep the model list clean;
- **Safe & Reversible**: Switch between proxy integration and official direct connection at any time without touching official binaries.

---

## Community & Support

- **QQ Group**: `613214996`
- **Telegram Group**: [Join Telegram Group](https://t.me/+IMj6SaNJAAhlNjM1)
- **Issues & Feedback**: Welcome to submit suggestions or bug reports on [GitHub Issues](https://github.com/yuzhiqiang1993/antigravity-studio/issues).

---

## Recommended Pairing: Antigravity IDE Cockpit

If you use Antigravity IDE frequently, check out the [**Antigravity IDE Cockpit**](https://open-vsx.org/extension/yuzhiqiang/antigravity-ide-cockpit) extension:
- Multi-account management and fast account switching;
- Real-time token usage tracking and quota monitoring;
- Session diagnostics and anonymized report export.

👉 [Install Cockpit on Open VSX](https://open-vsx.org/extension/yuzhiqiang/antigravity-ide-cockpit) · [Visit Website agycockpit.com](https://agycockpit.com)

---

## Core Features

### 1. Overview
- **Local Proxy Status**: Monitor proxy status and the active port;
- **Host Detection**: Automatically detects installed instances of Antigravity IDE, App, and CLI;
- **One-Click Integration**: Easily toggle between "Proxy Mode" and "Official Direct Connection".

### 2. Provider & Model Management
- **Provider Presets**: Built-in presets for OpenAI, Anthropic, Gemini, DeepSeek, xAI, Ollama, and API gateways, plus support for custom endpoints;
- **One-Click Model Fetching**: Fetch available models from upstream providers and add them to Antigravity;
- **Multimodal & Thinking Mapping**: Enable image inputs, tools, and map Low / Medium / High / Max reasoning levels;
- **Official Model Filtering**: Hide unused official models with a single click;
- **Latency Testing**: Test endpoint connectivity and response latency at any time.

### 3. Context Compression Settings
In long conversations, triggering context compression too early can lead to repeated summary calls and loss of earlier code details, while triggering too late might exceed the model's context limit.
- Capacity presets from 128K, 200K, 256K, 372K up to 1M;
- Customize compression trigger thresholds and output buffer sizes;
- Choose between using the current model or a lightweight model for compression.

### 4. Health Check (Doctor Engine)
Run a one-click diagnosis with guided fixes for common setup issues:
- Network connectivity to official services and GitHub Releases;
- Local config file integrity and permissions;
- Proxy port binding and loopback status;
- Host integration configuration validity;
- Upstream provider credentials and endpoint health.

### 5. Activity Logs
- Inspect local request routing records (timestamp, requested model, provider, HTTP status code, duration);
- **Privacy First**: Logs are stored strictly in memory and are cleared on exit. Prompts, code contents, responses, and API keys are never recorded or transmitted.

---

## How It Works

```text
Antigravity IDE / App / CLI
            │
            ▼ (Requests sent to local proxy)
   http://127.0.0.1:8321
            │
            ├─► Official Models ──► Direct to Google Cloud Code
            │
            └─► Custom Models ──► Protocol translation (OpenAI / Anthropic / Gemini / Ollama)
                                  │
                                  ▼
                         Upstream Providers / Local LLMs
```

---

## Installation & Download

### 1. Prebuilt Binaries (Recommended)

Download the prebuilt installer for your system from [GitHub Releases](https://github.com/yuzhiqiang1993/antigravity-studio/releases/latest):

| Platform | Package | Compatible With |
| :--- | :--- | :--- |
| **macOS (Apple Silicon)** | `Antigravity-Studio-1.0.0-macos-arm64.dmg` | M1 / M2 / M3 / M4 Macs |
| **macOS (Intel)** | `Antigravity-Studio-1.0.0-macos-x64.dmg` | Intel-based Macs |
| **Windows (x64)** | `Antigravity-Studio-1.0.0-windows-x64.exe` | 64-bit Windows 10 / 11 |

> 💡 **First Launch on macOS (Unsigned App Workaround)**
> If macOS Gatekeeper shows a damaged or unverified app warning, run the following command in Terminal:
> ```bash
> sudo xattr -rd com.apple.quarantine "/Applications/Antigravity Studio.app"
> ```

---

### 2. Quick Start

1. Launch **Antigravity Studio**;
2. Go to **Model Hub**, click **Add Provider**, select your provider (e.g. DeepSeek or OpenAI), enter your API Key, and save your desired models;
3. Go to **Overview**, verify the proxy is running, and click **Takeover** on the host card (e.g. Antigravity IDE);
4. Restart Antigravity IDE and select your custom models from the model selector.

---

### 3. Build from Source

Requirements: JDK 17 or JDK 21 (Temurin or Azul Zulu recommended).

```bash
# Run locally
./app_build.sh run

# Run tests
./app_build.sh test

# Package macOS DMG
./app_build.sh build --formats dmg
```

---

## FAQ

**Q: Custom models do not show up in IDE after enabling takeover?**
- Verify that models have been added and enabled in "Model Hub";
- Ensure the local proxy is running and the IDE card shows "Taken Over" in "Overview";
- Restart Antigravity IDE to reload settings;
- Check "Activity Logs" to verify requests reach the local proxy.

**Q: Does reverting to official direct connection delete my configurations?**
- No. Reverting only resets the IDE configuration. All added providers, models, and settings remain intact.

---

## License & Disclaimer

- **License**: Released under the [MIT License](LICENSE).
- **Disclaimer**: Antigravity Studio is an independent open-source tool and is not affiliated with Google or the official Antigravity team. Please comply with the terms of service of your respective LLM providers.
