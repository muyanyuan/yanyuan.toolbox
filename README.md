沐琰鸢的个人ai辅助项目
面向 Android 的多功能工具箱：内置Agent、可用的终端环境（Bash + BusyBox）、以及基于WebView的插件体系，并借助Shizuku与无障碍服务完成跨应用自动化。旨在辅助android端的agent与自动化。
尚未完成请勿打包。

以下为ai辅助
## 功能
- **AI 对话与 Agent**：支持导入多个 AI 平台的模型配置，提供普通对话与 Agent 两种模式；Agent 可调用本机工具、读写工作区文件、在终端执行命令，并回传执行结果。API Key 使用 Android Keystore 保护的 AES-GCM 加密后仅存本机。
- **内置终端**：自带 Toolbox Runtime（Bash 5.3 + BusyBox 1.37.0），通过 JNI 伪终端（`pty_bridge.cpp`）运行，支持原生 PTY 会话；也可经 Shizuku 以更高权限执行。
- **插件系统**：插件是携带 `manifest.json` 的 ZIP 包，页面以 HTML/CSS/JS 运行在 WebView 中，通过宿主注入的 `window.Yanyuan` 调用受控原生能力；支持声明式权限、按插件 ID 隔离的存储、以及无障碍自动化入口。
- **跨应用自动化**：插件声明 `accessibility_automation` 能力后，宿主无障碍服务可向其提供界面节点快照并执行节点点击（详见 [插件开发指南](app/src/main/assets/plugin_guide.md)）。
- **分享接入**：响应 `ACTION_SEND` / `SEND_MULTIPLE`，可从其他应用直接接收文件并在应用内处理。
- **外观定制**：主题色、玻璃不透明度、窗口玻璃效果（backdrop）、莫奈取色（从背景图提取主色）、图标主题切换。
## 技术栈
| 项          | 版本 / 说明                                                              |
|-------------|--------------------------------------------------------------------------|
| 语言 / UI   | Kotlin 2.2.10、Jetpack Compose（Material 3）                              |
| 构建        | AGP 9.4.1、Gradle 9.6.0（wrapper 自带）、JDK 17                            |
| SDK         | compileSdk 37、minSdk 24、targetSdk 34                                    |
| NDK / CMake | NDK **28.2.13676358**、CMake 3.22.1+                                      |
| 关键依赖     | Shizuku 13.1.5、OkHttp 4.12.0、Gson 2.10.1、Palette 1.0.0、backdrop 1.0.6 |
| ABI         | arm64-v8a、armeabi-v7a、x86_64                                            |
## 目录结构
```
.
├── app/
│   └── src/
│       ├── main/
│       │   ├── java/com/yanyuan/toolbox/    # 源码：UI、数据层、运行时、Shizuku、终端、插件
│       │   ├── cpp/                         # pty_bridge.cpp —— JNI 伪终端桥
│       │   ├── aidl/                        # Shizuku 文件 / 终端服务接口
│       │   ├── assets/toolbox-runtime/      # 内置运行时 bootstrap（按 ABI 分目录）
│       │   └── res/                         # 图标、主题、无障碍配置
│       ├── test/                            # 单元测试
│       └── androidTest/                     # 仪器测试
├── docs/                                    # 运行时构建记录与审计文档
├── gradle/                                  # 版本目录（libs.versions.toml）与 wrapper
└── scripts/toolbox-runtime/                 # 运行时交叉编译脚本 + 源码 / 工具链锁定
```
## 构建
### 环境要求
- JDK 17
- Android SDK（Platform 37、Build-Tools）
- Android NDK `28.2.13676358`（编译 `pty_bridge.cpp` 所需）
- CMake 3.22.1 以上
### 步骤
```bash
git clone https://github.com/muyanyuan/yanyuan.toolbox.git
cd yanyuan.toolbox
# 方式一：用 Android Studio 直接打开，自动生成 local.properties
# 方式二：手动指定 SDK 路径
echo "sdk.dir=/path/to/Android/Sdk" > local.properties
./gradlew assembleDebug
```
产物位于 `app/build/outputs/apk/debug/app-debug.apk`。
> 仓库已内置 `app/src/main/assets/toolbox-runtime/arm64-v8a/bootstrap.zip`（约 2 MB，含 Bash 5.3 + BusyBox 1.37.0），因此**无需重新编译运行时**即可构建出终端可用的 APK。
### 重新编译运行时（可选）
仅当需要升级 Bash / BusyBox 或新增 ABI 时才需要：
```bash
export ANDROID_NDK_HOME=/path/to/android-ndk-r28c   # 必须匹配 28.2.13676358
bash scripts/toolbox-runtime/build-runtime.sh out/toolbox-runtime/arm64-v8a arm64-v8a
cp out/toolbox-runtime/arm64-v8a/bootstrap.zip* app/src/main/assets/toolbox-runtime/arm64-v8a/
```
- 必须在 **Linux / WSL** 下执行；Windows Git Bash 会被脚本直接拒绝。
- 依赖 `curl`、`make`、`tar`、`zip`、`sha256sum`。
- 源码版本与 SHA-256 锁定在 [`sources.lock`](scripts/toolbox-runtime/sources.lock)，NDK 与 API 等级锁定在 [`toolchain.lock`](scripts/toolbox-runtime/toolchain.lock)；版本不匹配时脚本会报错退出，不会静默换用其他工具链。
### 签名
`release` 变体未配置 `signingConfig`，`assembleRelease` 产出的是**未签名** APK，需要自行签名：
```kotlin
// app/build.gradle.kts
android {
    signingConfigs {
        create("release") {
            val props = java.util.Properties().apply {
                rootProject.file("keystore.properties").inputStream().use { load(it) }
            }
            storeFile = rootProject.file(props.getProperty("storeFile"))
            storePassword = props.getProperty("storePassword")
            keyAlias = props.getProperty("keyAlias")
            keyPassword = props.getProperty("keyPassword")
        }
    }
    buildTypes {
        release { signingConfig = signingConfigs.getByName("release") }
    }
}
```
**密钥文件绝对不要提交到仓库。** 请在 `.gitignore` 中确认包含 `*.jks`、`*.keystore`、`keystore.properties`（当前 `.gitignore` 已覆盖前两者，`keystore.properties` 需自行追加）。
## 插件开发
插件是一个 ZIP 包，最小结构：
```
my-plugin/
├── manifest.json     # 必需：id、name、version、type、entry 等
├── index.html        # 由 manifest 的 entry 指定
└── ...               # JS / CSS / 资源
```
关键点：
- `id` 需稳定唯一（版本升级沿用同一 `id`），`type` 目前为 `web`。
- 通过 `window.Yanyuan` 调用宿主暴露的能力，页面无法直接访问任意原生接口。
- `permissions` 只能声明宿主清单中已存在的权限（`runtime`）或跳转系统设置的特殊访问项（`special`：`ACCESSIBILITY`、`OVERLAY`、`BATTERY_OPTIMIZATION`、`USAGE_STATS`）。
- 声明 `accessibility_automation` 后，宿主会调用 `window.runAutomationEvent(snapshotJson)`；匹配与决策逻辑由插件自身实现。
完整字段说明、权限语义与接入步骤见 [插件开发指南](app/src/main/assets/plugin_guide.md)（应用内同样可查看）。
## 敏感权限说明
| 权限                                                    | 用途                               |
|---------------------------------------------------------|------------------------------------|
| `INTERNET` / `ACCESS_NETWORK_STATE`                     | 请求用户配置的 AI 服务               |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE` | 维持插件运行时与长任务               |
| `POST_NOTIFICATIONS`                                    | 任务状态与运行时通知                 |
| `SYSTEM_ALERT_WINDOW`                                   | 悬浮窗 / 覆盖显示                   |
| `RECEIVE_BOOT_COMPLETED`                                | 开机后恢复必要服务                   |
| `PACKAGE_USAGE_STATS`                                   | 插件读取应用使用情况                 |
| `QUERY_ALL_PACKAGES`                                    | 插件列出已安装应用                   |
| `BIND_ACCESSIBILITY_SERVICE`                            | 无障碍自动化插件读取界面节点并执行操作 |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`                  | 保证长时间任务不被系统回收            |
部分权限（`QUERY_ALL_PACKAGES`、`PACKAGE_USAGE_STATS` 等）在 Google Play 上需要专项申报，本项目的定位偏向侧载 / 自用分发。
## 安全说明
- **API Key**：以 Android Keystore 中不可导出的 AES 密钥做 AES-GCM 加密后保存于本机，且排除在云备份与设备迁移之外；换机后需重新填写。运行时密钥会短暂解密到进程内存，该方案只能降低本地文件被直接读取的风险，无法抵御已控制设备或内存检查。
- **插件不是安全边界**：插件与宿主共享同一 Android UID 和进程权限，ZIP 无法自行提升权限，也无法绕过宿主清单。请只安装可信来源的插件。
- **无障碍自动化**：启用后宿主可将其他应用的界面节点交给插件脚本处理，错误或恶意规则可能误触按钮、确认操作或关闭页面。请仅为可信插件开启。
- **Shizuku**：仅在用户显式授权后才会以更高权限执行受控操作。
## 已知限制
- 处于 beta，接口可能变动；`0.5.0003-bata` 未经完整设备回归。
- 内置运行时目前只提供 **arm64-v8a** 的 bootstrap；其他 ABI 虽在构建配置中，但终端功能不可用。
- 不提供 `apt` / `dpkg` 包管理，也没有可用的软件源，仅包含 Bash + BusyBox 基础环境。
- 运行时暂未覆盖 Android 10+ 专用 variant，详见 [bootstrap 审计记录](docs/termux-bootstrap-aarch64-audit.md)。
## 相关文档
| 文档                                                          | 内容                                |
|---------------------------------------------------------------|-------------------------------------|
| [插件开发指南](app/src/main/assets/plugin_guide.md)            | 插件清单字段、权限模型、无障碍接入     |
| [插件介绍与开发接入（根目录版）](目录App插件介绍与开发接入文档.md) | 同上文档的另一修订版                  |
| [运行时阶段进度归档](docs/toolbox-runtime-progress-archive.md)  | Bash / BusyBox 交叉编译与闪退排查记录 |
| [bootstrap 审计记录](docs/termux-bootstrap-aarch64-audit.md)   | Termux bootstrap 构建溯源与许可核对   |
## 许可证
本仓库**尚未声明开源许可证**，默认保留所有权利。如需以开源方式分发，请补充 `LICENSE` 文件（并注意内置运行时组件 Bash 为 GPL-3.0-or-later、BusyBox 为 GPL-2.0-only，其许可证文本随 `bootstrap.zip` 一同分发）。
