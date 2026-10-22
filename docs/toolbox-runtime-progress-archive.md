# Toolbox Runtime 阶段进度归档

**归档日期：** 2026-10-22  
**当前工作目录：** `C:\Users\10597\Desktop\2`  
**范围：** 自有 Toolbox Runtime 第一阶段（Bash + BusyBox）及“打开终端后闪退”排查记录。

## 结论摘要

- 已为 **arm64-v8a** 编译 Bash 5.3 与 BusyBox 1.37.0，并生成 ZIP bootstrap；该 ZIP 已加入 `app/src/main/assets/toolbox-runtime/arm64-v8a/bootstrap.zip`。
- 最近一次源码验证通过：`:app:testDebugUnitTest :app:assembleDebug :app:assembleRelease`，`BUILD SUCCESSFUL`。其中 Release 输出是 **unsigned APK**；Debug APK 使用 Android 默认 debug 证书。
- **没有把新构建安装到手机。** 手机仍运行其原有签名版本 `0.5.0003-bata`（versionCode 3，ABI arm64-v8a）。新 Debug APK 与手机安装证书不同，Android 拒绝覆盖。没有卸载原 App，也没有清除或修改它的数据。
- 用户报告“点击打开终端后会闪退”。通过 ADB 查询未取得对应的 `FATAL EXCEPTION`、native tombstone 或 `ANR in` 记录，因此根因**尚未在设备上确认**。源码中发现并修复了主线程同步启动 PTY 的高风险路径，但此修复尚未部署到手机验证。
- 此阶段没有完整包管理能力：不提供可用的 APT 仓库或完整 dpkg 安装流程。BusyBox 源码可能启用了部分同名 applet，但这不等于 APT/dpkg 包管理已交付。

## 已完成

### Runtime 构建产物

- 固定源码版本和 SHA-256：
  - Bash 5.3：`0d5cd86965f869a26cf64f4b71be7b96f90a3ba8b3d74e27e8e9d9d5550f31ba`
  - BusyBox 1.37.0：`3311dff32e746499f4df0d5df04d7eb396382d7e108bb9250e7b519b837043a4`
- 使用 Android NDK `28.2.13676358`、Android API 24，为 arm64-v8a 交叉编译。
- 输出文件：
  - `out/toolbox-runtime/arm64-v8a/bootstrap.zip`
  - `out/toolbox-runtime/arm64-v8a/bootstrap.zip.sha256`
  - APK asset：`app/src/main/assets/toolbox-runtime/arm64-v8a/bootstrap.zip`
- Bootstrap SHA-256：`c47e517b134a9c675a85517ce243c40e012482f255cc0ea76128ed7a21714f18`。
- ZIP 完整性测试通过；manifest 为版本 `0.1.0-foundation`、ABI `arm64-v8a`、129 个文件、总文件大小约 5,941,249 字节。
- Bash 与 BusyBox ELF 均为 ELF64/AArch64；动态依赖为 Android 系统库 `libc.so`、`libdl.so`、`libm.so`，未发现额外打包动态库需求。
- ZIP 包含 Bash/BusyBox、BusyBox applet symlink 元数据、SHA-256/size/mode manifest 和许可证文本。

### App 与 Runtime 代码

- Runtime 仍在本 App 的私有 `files/usr` 与 `files/home` 下运行，与 App 共用 UID；不是 Android root，也不是内核级文件沙箱。显式路径访问 App 私有文件的风险仍存在。
- Agent 终端命令仍按每条命令单次审批；当前 UI/提示词将基础阶段标为 Bash + BusyBox、无 APT/dpkg，并说明同 UID 边界。
- Runtime manifest 校验包括路径、ABI、SHA-256、文件大小和权限模式；安装器校验 archive 文件并应用 mode。
- 旧测试名已改为通用 Runtime 命名；相关 Runtime manifest 测试已纳入单测。

### 终端闪退缓解改动（尚未设备验证）

- `NativeTerminalPanel.kt`：将 APP_SANDBOX/Toolbox Runtime 的同步 JNI PTY open 调用移入 `Dispatchers.IO`；新增打开中状态，阻止重复点击创建多个 PTY；捕获 open 异常并显示在终端区域。
- 关闭按钮和 Composable dispose 时，将 session close 放入 IO coroutine，避免 native `waitpid` 在主线程阻塞。
- 变更后的 Debug/Release 编译及单测通过。

## 未完成 / 未验证

1. **未在手机安装或运行新 APK。** 设备只验证了已连接、ABI 与现有包版本；新 APK 被拒绝覆盖的原因是证书不同。
2. **未完成设备端 Bash/BusyBox 自检。** 因新 APK 未安装，不能声称终端已在手机上修复或可用。
3. **闪退根因尚未确认。** ADB 日志曾遇到多个 adb 客户端版本冲突，切换到独立端口后能连接，但查询时没有保留的 FATAL/ANR 栈；日志只见包/launcher 活动信息。主线程同步 JNI 调用是源码确认的风险并已缓解，但不能据此断言它就是该次闪退根因。
4. **Native/JNI 生命周期风险仍需单独审查与设备验证。** 包括 fork 后调用非 async-signal-safe libc 操作、nativeClose 与 read/write 的并发竞态、退出后 PTY read 轮询，以及 JNI 参数防御。当前修改没有完全重构这些路径。
5. **ABI 仅完成 arm64-v8a Runtime。** App 自身 JNI 构建覆盖 arm64-v8a、armeabi-v7a、x86_64；但 BusyBox/Bash bootstrap 没有生成另外两个 ABI 工件，安装器不会跨 ABI 回退。
6. **不含 APT/dpkg 包管理器、签名仓库、Release/Packages 索引、GPG keyring 或软件包仓库。** 不应把它描述为完整 Linux 发行版或可安装任意软件包的环境。
7. **许可证/源码再分发审核与设备兼容矩阵未完成。** 归档中含上游许可证文本和版本说明，但这不等于完整法律审计、补丁源代码履约审计或发布批准。
8. **Release 安装包未签名。** `app/build/outputs/apk/release/app-release-unsigned.apk` 是当前 Gradle Release 输出。仓库另有 `app/release/app-release.apk`，其证书 SHA-256 与设备已安装证书相同（`4eae10d0876b91d52bf3590fa2d40617a9e85178d2c0cd993d2951b123bf79c9`），但它不是本轮新生成的 Release 产物，不能假定包含此次 PTY 修复与 Runtime asset。未使用它覆盖设备。

## 关键文件

- 构建脚本：`scripts/toolbox-runtime/build-runtime.sh`
- 源码锁：`scripts/toolbox-runtime/sources.lock`
- NDK/API 锁：`scripts/toolbox-runtime/toolchain.lock`
- Runtime 安装器/状态：`app/src/main/java/com/yanyuan/toolbox/runtime/ToolboxRuntime.kt`
- Runtime 命令执行器：`app/src/main/java/com/yanyuan/toolbox/runtime/ToolboxRuntimeExecutor.kt`
- 交互终端 UI：`app/src/main/java/com/yanyuan/toolbox/ui/NativeTerminalPanel.kt`
- Native PTY bridge：`app/src/main/cpp/pty_bridge.cpp`
- Kotlin PTY wrapper：`app/src/main/java/com/yanyuan/toolbox/terminal/NativePty.kt`
- 已集成的 arm64 asset：`app/src/main/assets/toolbox-runtime/arm64-v8a/bootstrap.zip`
- 本轮 APK：`app/build/outputs/apk/debug/app-debug.apk`、`app/build/outputs/apk/release/app-release-unsigned.apk`

## 复核命令与结果

- `:app:testDebugUnitTest :app:assembleDebug :app:assembleRelease`：通过。
- `unzip -t out/toolbox-runtime/arm64-v8a/bootstrap.zip`：通过。
- `sha256sum -c out/toolbox-runtime/arm64-v8a/bootstrap.zip.sha256`：通过。
- `llvm-readelf -h`：Bash/BusyBox 均为 AArch64 ELF64。
- 设备上仍安装 `com.yanyuan.toolbox` versionCode 3 / `0.5.0003-bata`；没有卸载、清数据或更新。
