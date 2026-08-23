# Antigravity Studio 构建与打包指南

本项目提供标准化的跨平台自动化构建脚本 `app_build.sh`，封装了 Compose Multiplatform Desktop 的全套构建、测试、运行与安装包分发流水线，支持 Debug/Release 环境隔离与产物校验。

---

## 一、命令列表

### 基本操作命令

| 命令 | 说明 | 常用参数 |
| :--- | :--- | :--- |
| `./app_build.sh clean` | 清理项目构建缓存与所有模块产物 | 无 |
| `./app_build.sh test` | 运行项目全量单元测试与逻辑验证 | 无 |
| `./app_build.sh run` | 本地快速启动运行桌面端应用 | `--build-type <debug\|release>` |
| `./app_build.sh dist` | 构建免安装独立 App 目录（构建速度最快） | `--build-type <debug\|release>` |
| `./app_build.sh build` 或 `package` | 构建指定或全量平台安装分发包 | `--formats`, `--build-type` |
| `./app_build.sh assemble` | 执行完整发布流水线（clean + test + package + summary） | `--formats`, `--skip-test`, `--open` |
| `./app_build.sh help` | 显示脚本帮助说明与使用示例 | 无 |

---

## 二、参数与选项说明

### 1. 构建环境 (`--build-type`)

对标 Android 的 `buildTypes` 机制，通过 Gradle 原生 Task 在编译期自动注入 `BuildInfo` 常量：

| 构建类型 | 默认行为 | `BuildInfo.DEBUG` | 用途与说明 |
| :--- | :--- | :--- | :--- |
| **`debug`** | 本地 `run` 默认 | `true` | 开发调试使用：保留原始 JSON、修改后 JSON 协议审查等开发者入口，输出详尽调试日志 |
| **`release`** | 打包 `build` 默认 | `false` | 正式分发使用：隐藏内部调试入口，开启 ProGuard 与运行时性能优化 |

### 2. 打包格式 (`--formats`)

可指定构建单种或多种分发格式，多个格式使用半角逗号 `,` 分隔：

| 格式名称 | 适用操作系统 | 产物类型说明 |
| :--- | :--- | :--- |
| **`dmg`** | macOS | 苹果标准 DMG 磁盘挂载镜像（最常用的 macOS 分发格式） |
| **`pkg`** | macOS | macOS 标准安装器包 |
| **`app`** | macOS | 免安装的 `.app` 原生独立应用程序目录 |
| **`msi`** | Windows | Windows Installer 安装包 |
| **`exe`** | Windows | Windows 可执行独立安装器 |
| **`all`** | 当前系统 | 自动构建当前操作系统支持的全部安装包格式（默认值） |

### 3. 辅助控制参数

- **`--skip-test`**：在执行 `assemble` 完整发布流程时跳过单元测试（适用于紧急打包验证）。
- **`--open`**：构建完成后自动在文件管理器（macOS 访达 / Windows 资源管理器）中打开产物目录。

---

## 三、常用使用场景示例

### 1. 本地开发与联调
```bash
# 以开发调试模式直接启动应用窗口
./app_build.sh run

# 以正式环境行为本地运行（验证生产模式下的 UI 与功能）
./app_build.sh run --build-type release
```

### 2. 快速生成免安装 App 验证
```bash
# 免去制作 DMG/PKG 镜像的耗时，秒级生成独立可执行 App
./app_build.sh dist

# 直接打开运行生成的独立 App
open "desktopApp/build/compose/binaries/main/app/Antigravity Studio.app"
```

### 3. 打包指定安装包
```bash
# 仅打包 macOS DMG 镜像
./app_build.sh build --formats dmg

# 同时打包 DMG 与 PKG
./app_build.sh build --formats dmg,pkg

# 打包用于测试调试的 Debug 版 DMG
./app_build.sh build --build-type debug --formats dmg
```

### 4. 正式版本发布流水线 (Assemble)
```bash
# 完整发布前流程：clean -> test -> 全格式打包 -> 输出产物清单与 SHA256 校验和
./app_build.sh assemble

# 跳过测试并自动打开产物输出目录
./app_build.sh assemble --skip-test --open
```

---

## 四、macOS 芯片架构适配（Apple Silicon 与 Intel）

macOS 平台目前存在两种 CPU 架构体系，本项目在原生底层与更新分发上均已完成深度适配：

1. **Apple Silicon 芯片 (M1/M2/M3/M4 系列，`arm64` / `aarch64`)**：
   - 原生 ARM64 指令集编译，享受极致的启动速度与超低功耗。
   - 在 M 芯片 Mac 上执行打包时，自动生成 `Antigravity-Studio-1.0.0-macos-arm64.dmg`。
2. **Intel 处理器芯片 (`x86_64` / `x64`)**：
   - 原生 x86_64 编译，适配各类 Intel 架构 Mac。
   - 在 Intel Mac 上执行打包时，自动生成 `Antigravity-Studio-1.0.0-macos-x64.dmg`。
3. **客户端智能匹配下载**：
   - 客户端升级检测引擎 `ReleaseInfo.kt` 会在后台读取当前机器的 `System.getProperty("os.arch")`。
   - 当用户点击“立即下载更新”时，**自动精准下载匹配其 CPU 芯片的原生安装包**，无需用户手动辨别。

---

## 五、产物输出目录结构

构建完成后，所有二进制产物统一输出至 `desktopApp/build/compose/binaries/main/` 目录：

```text
desktopApp/build/compose/binaries/main/
├── app/
│   └── Antigravity Studio.app                         # 免安装原生独立 App 目录
├── dmg/
│   ├── Antigravity Studio-1.0.0.dmg                   # 默认镜像
│   └── Antigravity-Studio-1.0.0-macos-arm64.dmg       # 标准发布归档 (Apple Silicon)
├── pkg/
│   ├── Antigravity Studio-1.0.0.pkg                   # 默认安装器
│   └── Antigravity-Studio-1.0.0-macos-arm64.pkg       # 标准发布归档 (Apple Silicon)
├── msi/
│   └── Antigravity Studio-1.0.0.msi                   # Windows MSI 安装包
└── exe/
    └── Antigravity Studio-1.0.0.exe                   # Windows EXE 安装器
```

---

## 六、代码中消费构建环境 (`BuildInfo`)

项目基于 Gradle 原生能力自动生成 `com.yuzhiqiang.antigravity.BuildInfo`，在所有 Kotlin 代码中均可安全引用：

```kotlin
package com.yuzhiqiang.antigravity

import com.yuzhiqiang.antigravity.BuildInfo

// 判断当前是否为 Debug 环境
if (BuildInfo.DEBUG) {
    // 开发者专属入口或调试协议逻辑
}

// 获取构建元数据
val version = BuildInfo.VERSION_NAME   // "1.0.0"
val buildType = BuildInfo.BUILD_TYPE   // "debug" 或 "release"
val isRelease = BuildInfo.IS_RELEASE   // true / false
```

---

## 七、GitHub Actions 云端矩阵自动化打包与发布

桌面端原生安装包受操作系统环境限制（macOS DMG 必须在 Mac 环境打包，Windows MSI/EXE 必须在 Windows 环境打包）。因此在生产发布时，**推荐直接利用 GitHub Actions 矩阵并发打包并自动创建 GitHub Releases**。

### 1. 自动化流水线配置

项目已内置完整的 CI/CD 工作流：
- **`.github/workflows/ci.yml`**：PR 和 Push 触发的快速多系统质量把关（测试 + 编译）。
- **`.github/workflows/release.yml`**：推送版本 Tag 或手动触发的全平台矩阵并发打包流水线。

### 2. 矩阵构建机与产物对照表

| 目标平台与芯片 | Runner 与 JDK 架构 | 输出核心推荐发布安装包 |
| :--- | :--- | :--- |
| **macOS (Apple Silicon M系列)** | `macos-14` (ARM64 JDK) | `Antigravity-Studio-1.0.0-macos-arm64.dmg` |
| **macOS (Intel 芯片)** | `macos-14` (x64 JDK 原生交叉构建) | `Antigravity-Studio-1.0.0-macos-x64.dmg` |
| **Windows (x64)** | `windows-latest` (x64 JDK) | `Antigravity-Studio-1.0.0-windows-x64.exe` |

### 3. 一键发布新版本步骤

1. **更新版本号并提交**（如调整为 `1.0.0`）。
2. **为当前提交打上版本 Tag 并推送至 GitHub**：
   ```bash
   git tag v1.0.0
   git push origin v1.0.0
   ```
3. **GitHub Actions 自动执行**：
   - 自动并发拉起 3 台虚拟机构建各平台原生安装包并计算 SHA256 校验和。
   - 自动在 GitHub Releases 中创建 `Antigravity Studio v1.0.0` 正式发布页面。
   - 自动上传各平台安装包与校验文件，并根据 Git 提交记录自动生成更新日志（Release Notes）。
4. **客户端无缝感知**：用户端启动应用或在“关于”页检查更新时，即可自动获取最新版本并根据其当前操作系统和芯片架构智能匹配下载！

