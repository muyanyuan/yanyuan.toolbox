# 内置 Termux bootstrap 审计记录（aarch64）

> 状态：**构建成功，但尚未获准打包进 APK**。本报告不是法律意见；在逐包许可证、源码提供义务和真实设备安装验证完成前，不得发布该 runtime。

## 固定供应链

- termux-packages commit: `c208d019879c2d790ca50fe30d144b9a5979dfbf`
- 应用包名: `com.yanyuan.toolbox`
- 目标 Android ABI: `aarch64` / `arm64-v8a`
- Docker builder image: `ghcr.io/termux/package-builder@sha256:44999857244619e25bc8e3427904a16d36cc0c0e087704af039e5bdf94814ac9`
- 构建参数: `build-bootstraps.sh --architectures aarch64`
- 重要：该工件是 Termux 默认 Android 7–9 兼容 variant，不是 Android 10+ variant；由于 App 目标为 Android 10+，该 ZIP 不能直接打包发布。
- Android 10+ 三 ABI 重建已验证 PCRE2 归档可从旧构建缓存复用并通过 SHA-256；Android SELinux 已切换到固定的 Gitiles `android-14.0.0_r11` 归档，并补充了 Android 14 与 Termux Makefile 的兼容层。当前构建继续被容器内 Android/Gradle 依赖下载的 TLS/网络失败阻塞，尚未生成 Android 10+ ZIP。没有使用已生成的 `.deb` 绕过源码/身份校验。
- 依赖策略: 所有依赖使用源码构建（`-F`）；未绕过包身份保护、未复用 `com.termux` 预编译 `.deb`。
- 隔离 worktree 的网络修复：固定源码 hash 与 recipe 一致时才替换为可达的上游官方/作者 tag 归档；这些修改不在 App 源码树中。

## 工件校验

- 文件：`bootstrap-aarch64.zip`
- SHA-256: `34f96cc4e1888562145dddae896fd72890226e36c4425acbf45571c850af2963`
- 压缩大小：`128,940,870` bytes（约 123 MiB）
- ZIP 展开大小：`377,822,058` bytes（约 360 MiB）
- manifest ABI: `arm64-v8a`
- manifest version: `termux-packages-c208d019879c2d790ca50fe30d144b9a5979dfbf`
- manifest 文件数：17,480
- 逐文件 SHA-256 校验失败数：0
- 包含 `bin/bash`：是
- 包含 `SYMLINKS.txt`：是；安装器会在 staging 中恢复，并拒绝越界目标。

## dpkg / 许可证初步统计

- dpkg status 包数：159
- 明确的 `share/doc` 版权/许可证文件：63
- 能找到同名 `share/doc/<package>/` 目录的包：118/159
- dpkg `License` 字段：不能作为完整包级许可证清单使用（当前状态文件没有可用的统一 License 字段）。

该统计**不是逐包法律审计**。仍需逐一核对：

1. 每个二进制包的版权/许可证文本和上游来源；
2. GPL/LGPL/Apache/MIT/BSD/公共领域等许可证的通知、保留版权、修改说明和源码提供义务；
3. 生成配置、补丁和 Termux recipe 的许可证；
4. `termux-licenses` 与各包实际安装文档是否一致；
5. 发布 APK/AAB 时的 notices、对应源码归档和可获取期限。

## 安装约束

App 侧安装器只接受包含 manifest 的 ABI 匹配 ZIP，并执行：

- staging 解压；
- 绝对路径、`..`、重复条目、manifest 不一致和越界 symlink 拒绝；
- 每个普通文件的 SHA-256 和大小校验；
- `files/usr` 的原子切换和失败恢复；
- 独立 `files/home`；
- 不将 API key、授权存储或 Agent 工作区复制到 Home。

## 当前决定

该 aarch64 工件暂不复制到 `app/src/main/assets`，也不进入 APK。原因是逐包许可证审计、arm/arm64-v8a/x86_64 全 ABI 构建、APT 签名仓库策略和真机安装验证尚未完成。