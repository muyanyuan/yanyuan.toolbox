# 琰鸢工具箱插件：功能、安全与开发接入

本文介绍当前琰鸢工具箱的插件体系本身：插件能做什么、权限如何工作、需要注意哪些风险，以及如何制作并接入插件。接口和限制依据当前 App 源码整理。

> 文档更新标记：对应软件版本 0.4.2。自动化插件现使用通用 `accessibility_automation` 能力；初始规则随 ZIP 导入，匹配逻辑由插件包内脚本执行。API Key 在本机使用 Android Keystore 保护的 AES-GCM 加密存储，并排除在备份之外。

## 插件是什么

插件是由用户导入并安装在琰鸢工具箱中的小型扩展。它可以提供一个由 HTML、CSS、JavaScript 组成的功能页，也可以向宿主的自动化服务提供规则数据。普通插件页面运行在 Android WebView 中，通过 `window.Yanyuan` 调用宿主明确暴露的原生接口。

插件包不是 Android 安装包，不会自行安装原生代码，也不能通过 ZIP 修改宿主的 Android 权限清单。当前导入器读取 ZIP 中的 `manifest.json`，并把包内文件解压到 App 的私有插件目录。插件不会因此获得独立的 Android UID 或独立进程。

## 插件可以做什么

- 显示自定义工具页面，读取设备尺寸并适配手机和平板。
- 使用宿主提供的提示、振动、旋转方向、常亮、关闭页面等界面能力。
- 在按插件 ID 隔离的宿主存储中保存少量设置。
- 显示启动器中可见的应用列表。
- 在宿主接口允许的范围内读取已导入 AI 平台/模型信息，或请求平台账户状态。
- 使用 `accessibility_automation` 能力读取和保存规则；插件声明自动化引擎入口后，宿主无障碍服务会向插件脚本提供节点快照，并执行脚本选中的节点操作。

具体能力取决于宿主实际开放的 `Yanyuan` 方法以及插件声明的能力，不应把插件描述中的文字当作技术隔离或安全保证。

## 权限与授权

### 插件清单中的 permissions

插件可在 `manifest.json` 的 `permissions` 数组中列出首次使用前需要用户确认的权限。琰鸢工具箱会显示说明，并要求用户完成授权后再进入插件。当前支持两类：

- `runtime`：Android 运行时权限，例如通知权限。该权限必须已存在于宿主 App 的 Android 清单中；插件 ZIP 不能自行添加权限声明。
- `special`：跳转到 Android 系统设置的特殊访问项，当前枚举为 `ACCESSIBILITY`、`OVERLAY`、`BATTERY_OPTIMIZATION`、`USAGE_STATS`。

清单权限是宿主的授权引导与进入门槛，不会创建插件级权限沙箱。系统权限实际授予的是琰鸢工具箱，而非单独某个插件；若某插件需要无障碍权限，意味着宿主的无障碍服务能按其配置读取界面并执行动作。

示例：

```json
"permissions": [
  {
    "kind": "special",
    "special": "ACCESSIBILITY",
    "label": "无障碍服务",
    "description": "用于按规则识别并点击目标界面元素"
  }
]
```

### Android 宿主权限

当前宿主清单声明了网络访问、网络状态、振动、唤醒锁、开机广播、通知、悬浮窗、前台服务、电池优化豁免、使用情况访问和查询已安装应用等权限/特殊访问项。某项权限是否实际启用，仍取决于 Android 版本、系统设置和用户授权。

插件通过宿主原生接口间接使用能力，不能因为自己的 `manifest.json` 写了权限名就自动取得系统权限。制作插件时只申请必要权限，并在界面解释用途；不要为了“可能有用”要求无障碍、悬浮窗或应用使用情况访问。

### AI 接口授权

插件 AI 授权按插件 ID 单独保存。当前实现会在 `getAiCatalog()` 和 `getAiSnapshot()` 中检查授权状态，未授权插件无法通过这两个接口读取平台/模型目录或请求账户状态。API 密钥不会作为字符串返回给网页；获授权后，`getAiSnapshot()` 仍会由宿主代为向已配置的平台发请求，并把余额/状态结果返回插件。

## 风险说明

### 风险等级取决于插件来源与声明能力

- 普通信息展示插件：通常为低到中等风险，仍可能通过网络发送页面数据或滥用宿主桥接。
- 能使用网络、应用列表或 AI 账户查询接口的插件：中等风险；可访问的元数据可能暴露使用习惯或账户状态。
- 声明 `accessibility_automation` 并要求无障碍服务的插件：高风险。无障碍服务可读取其他应用的可访问界面内容，并执行插件脚本选择的操作。错误规则或恶意规则可能误触按钮、确认操作或关闭页面。
- 请求悬浮窗、使用情况访问、电池优化豁免等特殊访问的插件：需逐项审查其必要性，避免授予与功能无关的访问。

### 当前实现中的安全限制

- 插件脚本运行于启用了 JavaScript 的 WebView，并可访问 `Yanyuan` 原生桥接。当前 WebView 还启用了文件访问及跨文件 URL 访问；因此不可信插件包中的脚本应按可执行代码对待，而不是普通文档。
- 普通桥接方法（例如 `getInstalledApps()`、设备信息、振动和存储）没有统一的 capability 检查；AI 目录/快照接口额外检查插件 AI 授权，自动化规则读写接口检查 `accessibility_automation` capability。清单中列出权限不等于所有接口都会按该清单拦截。
- 插件 ID 用于隔离偏好存储，但不是安全身份验证。导入同 ID 的包会替换原有插件目录；不要从不明来源安装或更新插件。
- 卸载会删除该插件目录及按插件 ID 保存的配置。插件联网行为仍由其网页代码发起，导入前应检查外链、请求地址和第三方脚本。

建议只安装可信来源的 ZIP；审阅 HTML/JavaScript、清单能力和联网地址；仅在确有需要时授予 Android 特殊访问；发现异常时关闭插件开关并撤销系统授权后再卸载。

## 插件包格式

建议 ZIP 根目录直接放置 `manifest.json`。当前导入器也能在子目录中找到清单，但入口路径和资源引用应保持清晰、相对路径一致。

```text
my-plugin.zip
  manifest.json
  index.html
  js/app.js
  css/style.css
  assets/icon.png
```

清单关键字段：

| 字段 | 用途 |
| --- | --- |
| `id` | 必填且稳定的插件 ID；更新包沿用同一 ID |
| `name` | 必填，功能页显示名称 |
| `description` | 插件用途简介 |
| `author` | 作者，可选 |
| `version` | 版本号 |
| `type` | 插件类别，通常为 `web` 或 `automation` |
| `entry` | 网页入口，默认 `index.html` |
| `automationEngine` | 可选；声明 `accessibility_automation` 时指定自动化引擎网页入口 |
| `rules` | 可选；初始规则 JSON 文件，首次导入时写入插件规则存储，升级时保留用户现有规则 |
| `orientation` | `auto`、`portrait`、`landscape`、`reverseLandscape`、`sensorPortrait`、`sensorLandscape` 或 `autoTablet` 等方向模式 |
| `fullscreen` | 是否隐藏系统栏 |
| `keepScreenOn` | 是否在页面运行期间保持屏幕常亮 |
| `capabilities` | 功能能力声明；自动化规则接口需要 `accessibility_automation` |
| `hasSwitch` | 功能卡片是否显示插件开关 |
| `icon` | 插件包内图标相对路径 |
| `permissions` | 首次使用的权限说明/授权门槛 |

声明 `automationEngine` 后，宿主会在后台加载该网页入口，并在其他应用界面变化时调用 `window.runAutomationEvent(snapshotJson)`。快照含 `token`、`packageName`、屏幕宽高和 `nodes`；每个节点含 `id`、`parentId`、文字/描述/提示、可见/启用/可点击状态及 `left`、`top`、`right`、`bottom` 边界。引擎可通过 `PluginAutomationHost.getPluginRules()` 读取规则，并通过 `PluginAutomationHost.clickNode(token, id)` 请求宿主执行节点点击。规则匹配与决策由插件实现，宿主只负责节点快照和操作接口。

示例：

```json
{
  "id": "example-tool",
  "name": "示例工具",
  "description": "一个琰鸢工具箱网页插件",
  "author": "作者名",
  "version": "1.0.0",
  "type": "web",
  "entry": "index.html",
  "orientation": "autoTablet",
  "fullscreen": false,
  "keepScreenOn": false,
  "capabilities": [],
  "hasSwitch": false,
  "icon": "icon.png",
  "permissions": []
}
```

导入器限制：单个解压文件最大 10 MiB，整个 ZIP 解压总量最大 30 MiB；必须含有效 JSON 清单和非空 `id`、`name`。更新同 ID 插件会覆盖其安装目录。避免 ZIP 中放置敏感密钥或不必要的大体积文件。

## JavaScript 接口接入

页面在宿主 WebView 中通过 `window.Yanyuan` 调用接口。桥接方法是同步 JavaScript 接口；JSON 数据以字符串返回，需自行 `JSON.parse()`。

```js
const Y = window.Yanyuan;
if (!Y) throw new Error("请在琰鸢工具箱插件容器中运行");

const device = JSON.parse(Y.getDeviceInfo());
Y.toast(`宽度：${device.screenWidthDp} dp`);
Y.storageSet("last-open", new Date().toISOString());
```

### 当前开放接口

| 接口 | 用途与返回 |
| --- | --- |
| `getPluginId()` | 当前插件 ID |
| `toast(message)` | 显示短提示 |
| `vibrate(milliseconds)` | 请求振动 |
| `setKeepScreenOn(boolean)` | 设置页面期间是否常亮 |
| `setOrientation(mode)` | 设置屏幕方向 |
| `getDeviceInfo()` | 返回设备/屏幕 JSON 字符串，含 `isTablet`、dp/px 尺寸、方向和密度 |
| `applyDeviceOrientation()` | 手机竖屏、平板跟随传感器 |
| `close()` | 关闭插件页面 |
| `openAppSettings()` | 打开琰鸢工具箱的系统应用详情设置 |
| `storageGet(key)` | 读取当前插件的字符串存储，不存在时返回空值 |
| `storageSet(key, value)` | 保存当前插件的字符串值 |
| `storageRemove(key)` | 删除当前插件的字符串值 |
| `getSwitch()` / `setSwitch(boolean)` | 读取或修改当前插件开关 |
| `getInstalledApps()` | 返回启动器可见应用 JSON 数组，含包名、名称和系统应用标记 |
| `getAiCatalog()` | 返回已配置/已导入模型的平台与模型信息，不返回 API Key |
| `getAiSnapshot(platformId)` | 宿主请求账户/接口状态，返回 JSON 字符串；依平台而异 |
| `getPluginRules()` / `setPluginRules(json)` | 读取/保存当前插件规则；需要 `accessibility_automation` capability |
| `isAccessibilityServiceEnabled()` | 查询宿主无障碍服务是否已启用；需要 `accessibility_automation` capability |
| `openAccessibilitySettings()` | 打开系统无障碍设置；需要 `accessibility_automation` capability |
| `openBatteryOptimizationSettings()` | 打开电池优化设置；需要 `accessibility_automation` capability |
| `setBackHandler(functionName)` | 注册系统返回处理函数名；传空值取消注册 |
| `onBackResult(boolean)` | 返回处理结果回传接口，通常由注册流程使用 |

`setBackHandler` 注册的函数应同步返回布尔值：`true` 表示插件已经处理返回（例如关闭菜单/关于页），`false` 表示交给宿主继续 WebView 返回或退出。

```js
let aboutOpen = false;
function handlePluginBack() {
  if (aboutOpen) {
    closeAbout();
    return true;
  }
  return false;
}
Y.setBackHandler("handlePluginBack");
```

宿主在系统返回时会先调用处理器；若返回 `false` 或未注册，则尝试 WebView 历史，最后才关闭插件。处理器等待有超时兜底，页面销毁时宿主会销毁 WebView。

## 制作与接入流程

1. 明确插件用途，只申请所需的清单权限和能力；确定稳定唯一的 `id`。
2. 创建 `manifest.json` 与 `entry` 指定的网页入口，制作页面资源，并通过 `window.Yanyuan` 接入必要的宿主能力。
3. 若使用自动化接口，在 `capabilities` 中声明 `accessibility_automation`，通过 `automationEngine` 指向自动化入口，可通过 `rules` 随包提供初始规则；理解其读取界面和执行操作的风险。
4. 将清单、入口页、自动化引擎、初始规则、脚本、样式和资源放入 ZIP。检查大小限制、相对路径、清单 JSON 和资源大小写。
5. 在琰鸢工具箱的插件导入入口选择 ZIP；安装成功后插件会出现在功能页。首次使用时按清单完成所需授权。
6. 在手机和平板上实际测试布局、旋转、系统返回、开关、权限拒绝后的状态、资源离线加载和卸载清理。
7. 发布前审阅全部 JavaScript 和网络请求；版本更新沿用插件 ID、增加版本号，并说明权限/网络行为的变化。

## 发布前安全自检

- 插件是否真的需要无障碍、悬浮窗、应用列表或 AI 账户查询？
- 是否存在远程脚本、不可解释的网络请求或明文密钥？
- 是否明确向用户解释会读取什么、会自动执行什么？
- 规则是否限制目标应用、目标控件和触发频率，并排除系统设置页面？
- 拒绝授权、网络失败、宿主桥接不存在时，插件是否安全降级？

接口会随 App 版本变化；本文记录的是当前工作区源码中的接口，不构成 Android 系统权限说明或对第三方插件安全性的保证。发布新版 App 后应重新核验桥接方法、权限列表和 ZIP 导入限制。
