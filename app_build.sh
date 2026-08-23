#!/bin/bash

# ==============================================================================
# Antigravity Studio 构建与打包自动化脚本
# 适用平台：macOS / Windows / Linux (Compose Multiplatform Desktop)
# ==============================================================================

set -e  # 遇到错误时立即退出

# 颜色输出定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m' # No Color

# 全局变量
ACTION=""
BUILD_TYPE="release"     # debug | release
FORMATS=()              # dmg, pkg, app, msi, exe, all
SKIP_TEST=false
AUTO_OPEN=false

# 日志输出函数
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

log_header() {
    echo -e "\n${CYAN}${BOLD}=== $1 ===${NC}\n"
}

# 帮助信息说明
usage() {
    echo -e "${BOLD}Antigravity Studio 构建与打包工具${NC}"
    echo ""
    echo -e "${BOLD}用法:${NC}"
    echo "  $0 <action> [options]"
    echo ""
    echo -e "${BOLD}可用操作 (Action):${NC}"
    echo -e "  ${GREEN}clean${NC}                   - 清理所有模块构建产物与缓存 (gradlew clean)"
    echo -e "  ${GREEN}test${NC}                    - 运行全量单元测试 (jvmTest)"
    echo -e "  ${GREEN}run${NC}                     - 本地快速启动运行桌面端"
    echo -e "  ${GREEN}dist${NC}                    - 构建免安装独立 App 目录 (createDistributable)"
    echo -e "  ${GREEN}build | package${NC}         - 构建安装分发包 (DMG, PKG, MSI, EXE 等)"
    echo -e "  ${GREEN}assemble${NC}                - 完整发布流水线 (clean + test + package + summary)"
    echo -e "  ${GREEN}help${NC}                    - 显示本帮助信息"
    echo ""
    echo -e "${BOLD}选项说明 (Options):${NC}"
    echo -e "  ${YELLOW}--build-type <type>${NC}     - 指定构建环境类型: debug 或 release (默认: release)"
    echo -e "  ${YELLOW}--formats <f1,f2,...>${NC}   - 指定打包格式: dmg, pkg, app, msi, exe, all (默认: all)"
    echo -e "  ${YELLOW}--skip-test${NC}             - 在执行 assemble 流水线时跳过测试"
    echo -e "  ${YELLOW}--open${NC}                  - 构建成功后自动在文件管理器/访达中打开产物目录"
    echo ""
    echo -e "${BOLD}使用示例:${NC}"
    echo "  $0 run                                          # 以本地开发 Debug 模式启动应用"
    echo "  $0 run --build-type release                     # 以 Release 模式本地运行测试"
    echo "  $0 build --formats dmg                          # 仅打包 macOS DMG 镜像"
    echo "  $0 build --formats dmg,pkg                      # 同时打包 DMG 与 PKG"
    echo "  $0 build --build-type debug --formats dmg       # 打包带有调试入口的 Debug 版 DMG"
    echo "  $0 dist                                         # 生成免安装独立 App 目录 (速度极快)"
    echo "  $0 assemble                                     # 执行完整发布前流水线 (清理+测试+全量打包)"
    echo "  $0 assemble --skip-test --open                  # 跳过测试并自动打开产物目录"
    echo ""
}

# 检查项目根目录
check_project_root() {
    if [[ ! -f "settings.gradle.kts" || ! -f "build.gradle.kts" ]]; then
        log_error "请在 Antigravity Studio 项目根目录下执行此脚本！"
        exit 1
    fi
}

# 提取当前项目版本号
get_version_name() {
    local gradle_file="desktopApp/build.gradle.kts"
    if [[ -f "$gradle_file" ]]; then
        local version
        version=$(grep 'packageVersion\s*=' "$gradle_file" | head -n 1 | sed -E 's/.*"([^"]+)".*/\1/')
        if [[ -n "$version" ]]; then
            echo "$version"
            return 0
        fi
    fi
    echo "1.0.0"
}

# 解析命令行参数
parse_args() {
    if [[ $# -eq 0 ]]; then
        usage
        exit 0
    fi

    case "$1" in
        clean|test|run|dist|build|package|assemble|help)
            ACTION="$1"
            shift
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            log_error "未知操作: $1"
            usage
            exit 1
            ;;
    esac

    # 解析附加选项
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --build-type)
                shift
                if [[ -z "$1" || ("$1" != "debug" && "$1" != "release") ]]; then
                    log_error "--build-type 必须指定为 debug 或 release"
                    exit 1
                fi
                BUILD_TYPE="$1"
                ;;
            --formats)
                shift
                if [[ -z "$1" ]]; then
                    log_error "--formats 需要提供格式参数 (如: dmg, pkg, app, msi, exe, all)"
                    exit 1
                fi
                IFS=',' read -ra FORMATS <<< "$1"
                ;;
            --skip-test)
                SKIP_TEST=true
                ;;
            --open)
                AUTO_OPEN=true
                ;;
            -h|--help)
                usage
                exit 0
                ;;
            *)
                log_error "未知参数: $1"
                usage
                exit 1
                ;;
        esac
        shift
    done

    # 默认格式填充
    if [[ ${#FORMATS[@]} -eq 0 ]]; then
        FORMATS=("all")
    fi
}

# 1. 清理
action_clean() {
    log_header "执行构建清理"
    log_info "运行 ./gradlew clean..."
    ./gradlew clean
    log_success "项目构建缓存与产物清理完毕！"
}

# 2. 运行测试
action_test() {
    log_header "运行全量自动化测试"
    log_info "运行 ./gradlew :shared:jvmTest..."
    ./gradlew :shared:jvmTest
    log_success "所有单元测试通过！"
}

# 3. 本地运行
action_run() {
    log_header "启动桌面端应用 (环境: $BUILD_TYPE)"
    local gradle_args=("-PbuildType=$BUILD_TYPE")
    log_info "运行 ./gradlew :desktopApp:run ${gradle_args[*]}..."
    ./gradlew :desktopApp:run "${gradle_args[@]}"
}

# 4. 生成免安装分发包
action_dist() {
    log_header "生成免安装独立 App 目录 (环境: $BUILD_TYPE)"
    local gradle_args=("-PbuildType=$BUILD_TYPE")
    log_info "运行 ./gradlew :desktopApp:createDistributable ${gradle_args[*]}..."
    ./gradlew :desktopApp:createDistributable "${gradle_args[@]}"
    log_success "免安装 App 生成成功！"
    echo -e "产物目录: ${CYAN}desktopApp/build/compose/binaries/main/app/${NC}"
}

# 5. 构建分发安装包
action_build() {
    local version
    version=$(get_version_name)
    log_header "开始构建安装包 (版本: v$version, 环境: $BUILD_TYPE)"

    local tasks=()
    local os_name
    os_name=$(uname -s | tr '[:upper:]' '[:lower:]')

    for fmt in "${FORMATS[@]}"; do
        case "$fmt" in
            all)
                tasks+=(":desktopApp:packageDistributionForCurrentOS")
                ;;
            dmg)
                tasks+=(":desktopApp:packageDmg")
                ;;
            pkg)
                tasks+=(":desktopApp:packagePkg")
                ;;
            app)
                tasks+=(":desktopApp:createDistributable")
                ;;
            msi)
                tasks+=(":desktopApp:packageMsi")
                ;;
            exe)
                tasks+=(":desktopApp:packageExe")
                ;;
            *)
                log_error "不支持的打包格式: $fmt (支持: dmg, pkg, app, msi, exe, all)"
                exit 1
                ;;
        esac
    done

    # 去重
    local unique_tasks=($(echo "${tasks[@]}" | tr ' ' '\n' | sort -u | tr '\n' ' '))
    local gradle_args=("-PbuildType=$BUILD_TYPE")

    log_info "执行 Gradle 任务: ${unique_tasks[*]} (参数: ${gradle_args[*]})"
    ./gradlew "${unique_tasks[@]}" "${gradle_args[@]}"

    log_success "所有目标安装包构建成功！"
    show_artifacts_summary
}

# 获取当前系统标准化 CPU 架构
get_cpu_arch() {
    local raw_arch
    raw_arch=$(uname -m | tr '[:upper:]' '[:lower:]')
    case "$raw_arch" in
        arm64|aarch64)
            echo "arm64"
            ;;
        x86_64|amd64|x64)
            echo "x64"
            ;;
        *)
            echo "$raw_arch"
            ;;
    esac
}

# 6. 展示产物汇总与文件大小，并生成带架构标记的标准发布包
show_artifacts_summary() {
    local binaries_dir="desktopApp/build/compose/binaries/main"
    local version
    version=$(get_version_name)
    local arch
    arch=$(get_cpu_arch)
    local os_type
    os_type=$(uname -s | tr '[:upper:]' '[:lower:]')

    log_header "构建产物清单与校验 (平台: $os_type, 架构: $arch)"

    if [[ ! -d "$binaries_dir" ]]; then
        log_warning "未找到产物输出目录: $binaries_dir"
        return
    fi

    # 针对 macOS 自动生成标准架构命名的发布副本 (如 Antigravity-Studio-1.0.0-macos-arm64.dmg)
    if [[ "$os_type" == "darwin" ]]; then
        local dmg_dir="$binaries_dir/dmg"
        local pkg_dir="$binaries_dir/pkg"
        if [[ -f "$dmg_dir/Antigravity Studio-$version.dmg" ]]; then
            cp -f "$dmg_dir/Antigravity Studio-$version.dmg" "$dmg_dir/Antigravity-Studio-$version-macos-$arch.dmg"
        fi
        if [[ -f "$pkg_dir/Antigravity Studio-$version.pkg" ]]; then
            cp -f "$pkg_dir/Antigravity Studio-$version.pkg" "$pkg_dir/Antigravity-Studio-$version-macos-$arch.pkg"
        fi
    fi

    printf "${BOLD}%-10s %-10s %-10s %-52s %s${NC}\n" "类型" "架构" "大小" "文件路径" "SHA256 校验 (前8位)"
    echo "------------------------------------------------------------------------------------------------------"

    find "$binaries_dir" -maxdepth 3 \( -name "*.dmg" -o -name "*.pkg" -o -name "*.msi" -o -name "*.exe" -o -name "*.app" \) | sort | while read -r file; do
        if [[ -e "$file" ]]; then
            local file_type="${file##*.}"
            local file_size
            file_size=$(du -sh "$file" 2>/dev/null | cut -f1)
            local file_hash="N/A"
            if [[ -f "$file" ]] && command -v shasum &>/dev/null; then
                file_hash=$(shasum -a 256 "$file" | cut -c 1-8)
            elif [[ -f "$file" ]] && command -v sha256sum &>/dev/null; then
                file_hash=$(sha256sum "$file" | cut -c 1-8)
            fi
            printf "%-10s %-10s %-10s %-52s %s\n" "$file_type" "$arch" "$file_size" "$file" "$file_hash"
        fi
    done

    echo ""
    log_info "芯片架构提示: 当前构建机为 [${arch}] 架构。"
    if [[ "$arch" == "arm64" ]]; then
        log_info "原生支持: Apple Silicon (M1/M2/M3/M4 系列芯片)。"
    else
        log_info "原生支持: Intel x86_64 处理器芯片。"
    fi
    log_info "产物根目录: $(pwd)/$binaries_dir"

    if [[ "$AUTO_OPEN" == true ]]; then
        log_info "正在打开产物输出目录..."
        if command -v open &>/dev/null; then
            open "$binaries_dir"
        elif command -v xdg-open &>/dev/null; then
            xdg-open "$binaries_dir"
        fi
    fi
}

# 7. 完整发布流水线 (assemble)
action_assemble() {
    local version
    version=$(get_version_name)
    log_header "执行 Antigravity Studio 完整发布流水线 (v$version)"

    local start_time
    start_time=$(date +%s)

    action_clean

    if [[ "$SKIP_TEST" == false ]]; then
        action_test
    else
        log_warning "已跳过单元测试环节 (--skip-test)"
    fi

    action_build

    local end_time
    end_time=$(date +%s)
    local duration=$((end_time - start_time))

    log_success "完整发布流水线执行完毕！总耗时: ${duration}s"
}

# 主入口执行逻辑
main() {
    check_project_root
    parse_args "$@"

    case "$ACTION" in
        clean)
            action_clean
            ;;
        test)
            action_test
            ;;
        run)
            action_run
            ;;
        dist)
            action_dist
            ;;
        build|package)
            action_build
            ;;
        assemble)
            action_assemble
            ;;
        help)
            usage
            ;;
    esac
}

main "$@"
