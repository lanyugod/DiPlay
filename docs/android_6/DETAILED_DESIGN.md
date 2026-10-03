# 哈弗 H6 Android 6 适配详细设计

- 日期：2026-10-01（Asia/Shanghai）
- 状态：需求边界已确认；2026-10-01初步代码已实施并完成本机验证，实车门槛待验收。详见[实施交接](IMPLEMENTATION_STATUS.md)。
- 目标：2021 第二代哈弗 H6 冠军版，IHU01 / j6headunit，Android 6.0.1 / API 23。
- 手机基线：iPhone 13 Pro / iOS 16.1，原车 CarLife 数据口；采集时 CarLife 未运行。
- 设计采集轮交付：设计、实测记录和实施任务；该轮未修改应用。后续开发轮已修改源码并构建基础APK/探针，未安装或运行车机。

## 1. 可行性结论与支持边界

**现有证据支持推进普通 APK 的 API 23 兼容和应用级探针验证；尚不支持宣布完整 CarPlay 已能在本机落地。** Android/API、ARM32、屏幕、iPhone 枚举已经实测，依赖解析和当前 Manifest 基线已经核验。剩余核心门槛是第三方 APK 安装、应用 USB 权限与配置切换、旧 USB 请求收发及关闭、VPN 建立、实际 H.264 解码，以及运行认证。

当前没有可用的 DiPlay 运行认证配置或已验证测试包。基础兼容、USB/音视频能力探针可以独立推进；配对认证后的完整会话验收必须等待可用认证输入。不得用 source-only APK 的连接失败判断本机不能适配，也不得把源码构建成功当作 CarPlay 成功。

用户已确认的范围：

| 阶段 | 交付要求 | 边界 |
| --- | --- | --- |
| 一期 | 有线画面、触摸、音乐、导航播报；手动重连；切回桌面及倒车后可恢复 | 普通 APK，不依赖系统提权；保留原车应用共存 |
| 一期分别记录 | Siri、电话、方向盘键、ACC 行为 | Siri/电话可延期；未验收的功能不标为支持 |
| 二期 | 原车热点无线连接及恢复 | 一期闭环后推进，不承诺现有 Wi-Fi Direct 代码直接适用于 API 23 |
| 排除项 | 系统刷写、改厂商服务、强制关闭原车应用、BYD HUD/仪表/电池联动 | 受权限或驱动限制时降级或停止该路径 |

“导航”指 iPhone CarPlay 导航画面及播报；一期不需要车机给 iPhone 上报 GPS，也不需要 BYD 仪表显示。原车 CarLife 与 DiPlay 保留安装共存，不意味着两个应用可同时独占同一 iPhone USB 接口。

## 2. 证据、来源与本次审计修正

### 2.1 来源范围

1. [原计划](README.md)、[ADB 清单及已保存的审计摘要](ADB_VALIDATION.md)、当前本地源码。
2. 用户批准的两批只读 ADB 查询，目标始终为 `192.168.43.1:5555`。
3. 本机 Gradle 离线依赖解析、AAR Manifest 检查、当前源码的 AAR 元数据与 Manifest 合并。
4. 本机 SDK 37 的 `data/api-versions.xml`，用于复核 Android API 引入版本。
5. 后续经 `read_thread` 读取用户提供的 **判断DiPlay安装兼容性** 会话（ID `6abcd4f7-e0f8-83e9-80c8-3dc35c1b702c`）及两份用户附件，详见 [原会话复核](CONVERSATION_REVIEW.md)。

第一次访问[分享链接](https://chatgpt.com/share/6abd044b-5220-83e9-abc8-ae8df98c4f09)失败，当时经用户确认先以保存的审计摘要为依据。用户随后提供了可访问的来源会话，现已复核工具返回的5轮记录及原始ADB附件，补齐显示区域、SoC标识和“旧USB快照未插iPhone”的上下文。没有原始内容的外部引用与下载交接文件不视为已读取。用户当前离开ADB环境，本次追加复核没有查询车机。

设计采集时工作副本没有 `.git`；本轮实施工作区已有Git，基线与未提交状态见实施交接。下文设计锚点仍以采集时文件路径和类/函数为准，行号只是定位辅助。

### 2.2 实测设备基线

| 项目 | 结果 | 判断范围 |
| --- | --- | --- |
| 系统 | Android 6.0.1，API 23，`M4B30Z dev-keys`，build description 尾部版本 228 | OS 构建标识；不是 MCU 版本 |
| 厂商 / 型号 | `GuangDong_YF_Technology_Co_Ltd` / IHU01 | 与用户确认的车型共同定义支持对象 |
| 应用 ABI | `armeabi-v7a,armeabi`，64 位 ABI 列表为空 | 必须提供 ARM32 原生库；不能选 ARM64-only APK |
| CPU / 内核 | 两个可见 ARMv7 / Cortex-A15 核；`Generic DRA74X`；Linux 4.4.45 / armv7l | TI DRA74X 平台线索，不推断精确 SoC SKU 或全部物理核心 |
| 历史SoC/CPU补充 | 用户原消息：machine=DRA752、family=DRA7、ES2.0；DT model=TI DRA742；CPU possible/present/online均0-1 | 历史用户输出；当前未重采；保留DRA742/DRA752差异，不阻断ARM32设计 |
| 内存 | MemTotal 1,926,428 KiB，约 1.84 GiB；当时 MemAvailable 1,105,232 KiB | 单次快照，不能作长期内存预算或峰值保证 |
| 物理屏幕 | 1280×720，160 dpi；本地wm输出没有Override行；历史display模式约60Hz | wm结果不是所有窗口应用区域的完整报告；视频协商仍为30fps |
| 历史应用区域 | 原附件mOverrideDisplayInfo：app1160×720、real1280×720 | 真实历史快照；差120px的具体位置及DiPlay实际Surface需运行时确认 |
| 系统声明 | USB Host、触摸、麦克风、Wi-Fi、Wi-Fi Direct、蓝牙及 BLE；无 automotive 特征行 | 采用 mobile；声明不替代普通应用调用验证 |
| ADB 身份 | 已有 shell 为 uid 0；SELinux 输出 Disabled | 本轮没有执行 root；此权限不继承给普通 APK |
| iPhone 枚举 | VID/PID `05ac:12a8`，480 Mbps；内核与 USB Host 框架均可见 | 证明枚举，不证明第三方权限或实际吞吐 |
| 当前活动配置 | 配置 2（iPod USB Interface），sysfs 显示音频和 HID 接口 | 不是已完成 CarPlay 切换 |
| 框架配置列表 | 配置 1～4；配置 3 有 USBMUX，配置 4 有 USBMUX 和 Apple Ethernet | 当前快照没有 CDC NCM 的 `02/0d` 描述符；不能把 Ethernet 等同于可用 CarPlay NCM |
| USB 应用授权 | dumpsys 当前 Device permissions 列表为空 | 本轮未申请应用 USB 授权 |
| H.264 | XML 有 `OMX.TI.DUCATI1.VIDEO.DECODER`，AVC 最大尺寸声明 1920×1088 | 硬件实现线索；需要实际 Surface 解码 |
| Opus | XML 有 `OMX.google.opus.decoder`；本次配置未发现 Opus encoder | 后续先验证 PCM；不声称系统绝无编码器 |
| 音频输出 | primary speaker / 48 kHz，AudioFlinger 存在输出线程 | 不证明 DiPlay 已能发声或取得焦点 |
| 麦克风 | policy 声明 BUILTIN_MIC；快照有 16 kHz 输入线程 | 不证明普通应用能够录音；一期可不请求录音权限 |
| VPN 节点 | `/dev/tun` 存在，system:vpn；`/dev/net/tun` 不存在 | 不需要应用直接打开字符设备；必须验证 VpnService consent/establish |
| 原车应用 | 候选主包 `com.baidu.carlifevehicle`，位于 `/system/app/CarLifeVehicle/` | 不据路径推断内部 USB API、权限或进程状态 |
| 安装 / 导出组件 | PackageInstaller 有包记录；第三方包列表为空；候选摘要未发现 DocumentsUI | 新 APK 安装和文档选择器可用性均未证明 |

完整状态和未验证项见 [验证结果](VALIDATION_RESULTS.md)。原始输出留在 `evidence/*-local/`，可能含设备标识，不用于分发。

### 2.3 审计修正结论

| 建议 | 最终处理 |
| --- | --- |
| 有线优先、USB 为重点 | 采纳，符合用户已确认范围 |
| API 23 单次 USB 请求限制 | 旧队列和旧 bulk 路径按不超过 16 KiB 设计，协议数据在上层重组；读写分别处理 |
| 使用配置 6 判断成功 | 不采纳。本机当前仅有配置 1～4；沿用描述符匹配，重枚举后重新检查 |
| `05ac`、CarLife 可见就说明普通应用可用 | 不采纳，拆成枚举、授权、open、切换、claim、收发各门槛 |
| 直接替换 `requestWait(timeout)` | 不采纳，API 23 必须有唯一等待线程、持续挂起请求和可验证关闭 |
| 所有 bulk 写都做相同分块 | 修正。USBMUX 为流，可受控分块；NCM 保持完整 NTB 的传输边界 |
| AudioTrack/AudioRecord Builder 不兼容 | 两者从 API 23 提供，可保留。**新增发现：AudioTrack.getAudioAttributes 从 API 29 提供** |
| 欠载计数置零 | 不采纳，API 23 用播放进度、实际写入量和时间窗口判断重新缓冲 |
| 无 Opus encoder 即失败 | 不采纳，一期关闭上行能力，二期先测 PCM |
| 全部依赖降级、重写 UI | 暂不采纳，解析的 63 个 AndroidX AAR 未发现 minSdk >23；运行性能仍需测 |
| Native 首期删除 | 不采纳，保留原生构建结构，降平台并重编 ARM32；后续按真实加载路径优化 |
| 只读采集包含清日志 | 不采纳；本轮未执行清日志，也未读取完整 logcat |
| 历史没看到iPhone，可能需要USB MUX逆向 | 用户当时未插iPhone；本地后来已观测枚举且CarLife未运行，不继续沿用失败推断 |
| H6以实际Surface尺寸协商 | 采纳；源码已有此路径，补测1160×720及缩放，避免硬编码物理1280×720 |

## 3. 构建与发布设计

### 3.1 模块和版本

- `mobile` 为车机应用入口；`common`、`shared` 共同支持 API 23。
- `automotive` 保留自己的 API 28 下限，不为这台普通 Android 车机改为 AAOS 应用。
- 现有 compileSdk/targetSdk 37、AGP 9.3.0、Gradle 9.5.0、Kotlin 2.2.10 保留。
- `mobile/build.gradle.kts`、`common/build.gradle.kts`、`shared/build.gradle` 的 minSdk 改为 23。
- `shared/build.gradle` 的 `APP_PLATFORM=android-28` 与 `shared/src/main/jni/Application.mk` 的平台一起改为 android-23；NDK 28.2.13676358 保留。
- 保留目前 ABI 集合，确保 APK 含 `lib/armeabi-v7a/`，不为了本机删掉其他设备所需 ABI。
- 不用 `tools:overrideLibrary` 绕过最低 SDK，不为了安装方便降低 targetSdk，不预建没有实测依据的 legacy Manifest/flavor。

实际离线选中版本：Core/Core-KTX 1.19.0；Compose UI/Runtime 1.10.4；Material3 1.4.0；Activity Compose 1.8.2；Lifecycle 2.9.4；Car App/Projected 1.4.0。版本目录中的声明值不总是最终选中值。63 个已检查 AndroidX AAR 的 Manifest 下限均不高于 23；这不覆盖 JAR 内所有运行 API 或证明厂商 ROM 行为。

当前源码已通过 `:mobile:dependencies --configuration debugRuntimeClasspath`、`:mobile:checkDebugAarMetadata`、`:mobile:processDebugMainManifest`，均为离线执行。**这些任务检查的是尚为 minSdk 28 的现有源码，没有构建或验证 API 23 APK。**

本机默认 shell 的 Java 是 8，不满足构建要求；已有 Gradle 缓存中的 JDK 25.0.3 可用。实施时显式选择 JDK 25，不修改用户的全局 Java 配置。

### 3.2 原生库与签名

`xcertplay_i2c` 与 `local_hotspot_radio` 均按 API 23 重编。检查 ELF machine、依赖 SONAME 和引入的系统符号版本；在 API 23 进行实际加载。仅设置 APP_PLATFORM 不能替代加载验证。

测试包优先沿用现有 debug applicationId `com.shihab.diplay.hudtest` 和其签名；探针使用独立包名。先核实目标是否已有同包，签名不同就停止覆盖，不能为了通过安装直接卸载。车机目前未查到 DiPlay 候选包。

完整车测包继续使用 `:mobile:assembleStandaloneDebug` 的认证检查；基础 UI 检查可用 source-only APK，但标签必须说明不可据此验收 CarPlay。认证输入仅由现有本地资产入口提供，文档不添加来源不明的身份、不包含私钥。

## 4. 运行能力与设置收敛

新增 `shared/.../compat/PlatformCapabilities.kt`，返回基于 API 的能力；新增 `common/.../H6CompatibilityProfile.kt`，组合 API、当前设备标识与用户选择形成有效设置。Profile 只应用于明确识别的本目标或显式选中的兼容配置，不把所有 API 23 设备视为同一固件。

有效设置在 UI 和 Controller 开始连接前共同校验，不能只隐藏按钮。新默认值仅在键不存在或首次明确应用兼容配置时保存；保留用户的原配置，诊断同时记录 requested/effective，避免把较新系统的全局默认值改成 H6 默认值。

| 一期 H6 有效设置 | 初始方案 |
| --- | --- |
| 连接 | 有线；无线入口说明“二期验证” |
| 视频 | H.264、30fps、单主屏；以稳定非零Surface尺寸为协商基准，历史候选1160×720；默认1.0x，降负载复用现有0.8x等同比例缩放 |
| HEVC / 软件 HEVC / 副屏 | 关闭，不把软件解码作为静默兜底 |
| 麦克风 | 关闭，AirPlay 不宣告输入能力；不把录音权限作为一期启动前提 |
| 音频 | mobile 路由；媒体 48 kHz 为起点；导航保持独立音轨，实测焦点叠加 |
| BYD 能力 | 关闭 HUD、仪表、车辆电池与厂商输出 |
| 定位上报 | 关闭；手机导航保持可用 |
| 自启动 | 保留用户明确开启的行为，不把 BOOT_COMPLETED 等同于 ACC 恢复 |

屏幕物理毫米数不能由 160 dpi 推断为车机面板实测尺寸；复用现有 UI 缩放配置，触摸仍使用当前 View 到协商视频坐标的映射。

物理屏1280×720与历史app1160×720应分别记录。H6不硬编码减120px、不把差值一律当成左safeArea；只有DiPlay实际Surface测得1280×720时才协商完整面板尺寸。`uiScalePercent`先保持默认100%，防止已有图标缩放设置反向扩大解码canvas；分辨率缩放与图标大小分别处理。

## 5. USB 兼容层详细设计

### 5.1 分层与契约

在 `shared/.../transport/` 新增：

- `UsbReadTransport.kt`：完成数据、超时、终止三类结果的接口；创建工厂根据 API 选择后端。
- `LegacyUsbReadPump.kt`：仅用旧 `queue(ByteBuffer,length)` 与无参 `requestWait()`；持有唯一等待线程。
- `UsbMuxWriteCompat.kt`：USBMUX 写分块与总截止时间。

API ≥26 保留当前 USBMUX/NCM 接收行为；API 23～25 接入 Legacy pump。旧实现可抽为现代后端，但不在同次兼容任务重写现代协议路径。

不变量：同一 `UsbDeviceConnection` 最多一个 `requestWait` 消费者。USBMUX 与 NCM 沿用各自连接及所有权，不把两个读泵放到同一连接。NCM interrupt/status 当前为同步 bulk 读取；不得另加抢同一完成队列的等待者。

`read(deadline)` / `recv(deadline)` 超时只表示本次调用未拿到数据，不等于拔线、不取消挂起请求、不丢弃已经读到的协议残片。关闭与失败是持久终止状态，后续调用不能继续返回“普通超时”。

### 5.2 API 23 接收线程

1. 在工作线程初始化请求与直接缓冲区；单次容量固定为 16,384 bytes，position 从 0 开始，调用旧 queue 重载时显式传长度。
2. 发布请求与 close 状态用同一锁协调，避免 detach 恰好漏掉新请求；不得持有该锁进入 `requestWait()`。
3. 完成后校验请求身份和 position 范围，复制有效数据；旧 buffer 尚被内核持有时不可读取、清空或关闭。
4. 及时重新 queue，再交付完成数据；应用消费者使用基于单调时钟的条件等待，不在读取超时后 cancel/requeue。
5. 完成队列设字节数上限，初始 256 KiB，记录高水位。溢出时终止当前会话并明确报错，不静默丢失 USBMUX 字节或半个 NTB。
6. 0-byte 完成仅为无负载事件，不是 detach；连续 0-byte 导致忙循环时用有界节流并记录，不能无上限空转。
7. 请求完成异常、queue 失败、非本请求完成均进入 FAILED 并唤醒全部调用方。

应用超时由消费者等待实现，USB 请求继续存在。线程数量是每个打开连接一个，不是每次 read 新建线程。

### 5.3 关闭与重连

```text
OPEN → CLOSING → CLOSED
  └──── FAILED ───┘

标记终止/唤醒调用方
    → cancel 挂起请求
    → 连接所有者 close(connection)，使驱动退出等待
    → 在非 UI 线程有界 join（初始目标 1 秒）
    → 等待线程退出后关闭 UsbRequest 与剩余资源
```

`Thread.interrupt()` 不能被视为 native `requestWait()` 已退出。若车机驱动在 cancel/close 后仍不退出，记录退出超时，不对仍被驱动持有的对象并发 free；停止自动重连，列为 G1 阻塞。不得把 daemon 线程当作泄漏处理方式，也不能无限新建读泵。

每次重新 open 生成新的 session generation；权限回调、attach、完成数据和 Controller 回调都检查 generation。拔线或进程结束后的旧消息不得影响下一次连接。用户拒绝授权时停止申请，显示重试入口。

### 5.4 写入策略：USBMUX 与 NCM 分开

**USBMUX：** `Iap2UsbSession.write` 在 API 23 单次 bulkTransfer ≤16 KiB，使用已有 API 18 的 offset 重载；持有完整 writeLock 保证消息片段不交错。正数短写按实际偏移推进，使用整个 write 的总截止时间，不能每个片段重置 timeout。0/负数、截止时间耗尽或 detach 使本次会话失败；已写出部分字节的消息不能从头静默重发。

**NCM：** `NcmUsbBridge.send` 当前每个 Ethernet frame 一个 NTB16；保持这个传输单位。当前 VPN MTU 为 1500，正常出站 NTB 明显小于 16 KiB。保留该 MTU；先检查构建后的完整 block 不超过旧 API 限制，再一次发送；不把超大 NTB 任意切成多个独立短 USB transfer。若未来增大 MTU/聚合，应先设计并验证协议传输边界。

NCM 正数短写不能盲目续写或从头重发，报传输不完整并终止会话。现有 `transferred<=0` 将启动前 NAK 视为暂时未就绪；保留该协议意图，但必须区分“准备阶段、允许上层协议重试”与“会话运行阶段持续失败”。进入运行阶段后超过有界失败窗口（初始 3 秒）升级为可诊断错误，不长期吞掉所有失败。

### 5.5 重组、配置和 iPhone 切换

- USBMUX 继续交给既有帧层重组；16 KiB USB chunk 不等于 iAP2 消息大小，不能截断 64 KiB 协议帧。
- NCM 保留 `drainFrames` / `Ntb16Codec` 的 NTB 重组、多个 datagram、粘连 block 与 512-byte 边界 pad 处理。增加最大 block/buffer 边界；16 位 block 最大 65,535 bytes，含可能 pad 的 wire buffer 要多容纳一个字节及后继 chunk。旧路径重组缓冲初始上界为 81,920 bytes（65,536 + 16,384）；每次追加后尽快 drain，超过上界或非法长度终止会话。
- 限制与格式校验是流完整性要求；格式损坏不能靠扫描任意位置伪恢复成合法帧。
- 保留 `IphoneCarPlayConfiguration.find` 按 USBMUX + CDC NCM 描述符选配置的规则。
- 本机当前配置 4 的 Apple Ethernet 缺少现规则必需的 CDC NCM，不能为“识别成功”直接放宽规则。先在批准后的 vendor request 重枚举后采集全部配置，再判断本手机真正暴露的接口。
- vendor request、setConfiguration、claim(force) 会影响原车 USB 使用，必须由用户明确触发；不作为只读扫描副作用。
- 原车自动启动或占用时提示用户退出当前投屏并重试，不 kill/disable 原车包、不自动强抢接口。
- `setConfiguration` 返回 false 时，现代码继续 claim 的策略应由探针确认是否真的生效；没有数据证据时不报配置切换成功。

## 6. 服务、权限和标准库

### 6.1 前台服务

`CarPlayHostActivity.startCarPlay` 内的 `startForegroundService` 按 API ≥26 分支；旧系统调用 `startService`，在 `DiPlaySessionService.onStartCommand` 建立常驻通知并 `startForeground`。

服务仅 API ≥26 创建 NotificationChannel；统一用 NotificationCompat 构建通知与断开操作。现有 API ≥29 服务类型及 API ≥30 麦克风类型分支保留。启动/失败/用户停止均释放 Controller、媒体与 USB；没有会话时不保留空服务。

保持 `START_NOT_STICKY`，不把进程被杀后无条件自动重启当作恢复。Activity/Surface 重建可以连接当前存活会话；进程丢失后以新 generation 重建，不复用旧 USB/TUN。现有默认退出逻辑与 BYD 专用分支需确保 H6 不触发厂商路径。

### 6.2 权限与导出

- 一期不宣告麦克风输入，不因 RECORD_AUDIO 拒绝而阻塞有线启动；用户进入后续语音功能时才请求录音。`CarPlayHostActivity.onCreate`（429 附近）当前会主动请求录音，`maybeStartCarPlay` 又检查 `microphonePermissionResolved`：H6 输入关闭时设置 `microphoneAvailable=false`、`microphonePermissionResolved=true`，跳过录音弹窗并直接进入 `requestStartupPrerequisites`；不能只关 `/info` 能力而留下启动等待。
- USB 系统弹窗与 VPN consent 分别处理拒绝、取消和重试，不用 ADB `pm grant` 代替真实用户授权。
- 保留旧 BLUETOOTH/BLUETOOTH_ADMIN 声明；新 BLUETOOTH_CONNECT、通知、nearby 权限只在对应版本请求。
- 旧系统诊断导出优先现有 ACTION_CREATE_DOCUMENT，捕获 ActivityNotFoundException。没有文档选择器时写应用私有目录，并通过 FileProvider + ACTION_SEND 提供用户主动分享；没有分享目标时保留文件并显示无法导出的状态。
- 不为诊断导出增加全盘存储权限；PackageInstaller 存在或 ADB uid 0 均不证明安装无策略限制。

### 6.3 Java API 兼容路线

本轮静态发现的标准库范围较小，初期选择定点兼容封装，避免先引入未经核验覆盖面的 core-library desugaring 版本。Java 11 编译级别与 D8 语言脱糖保持，**不把它视为标准库兼容证明**。

新增 `shared/.../compat/CompatBase64.kt`；将所有 java.util.Base64 调用迁移到封装。Android 后端可使用 `android.util.Base64`，但封装必须保留原语义：

- 普通 encoder 不换行，保留 padding；普通 decoder 接受合法无 padding 输入，拒绝非法 alphabet/长度/padding。
- MIME decoder 按原 Java MIME 语义忽略非 alphabet 字符，不能仅以 Android 默认忽略空白声称等价。
- PEM encoder 64 字符一行、LF 分隔、无额外末尾换行；与既有 PairRecord 输出逐字节比较。
- 异常仍进入既有配对/TLS/解析错误类别，不把损坏数据悄悄解码为别的证书。

`Iap2LocationClient.Timestamp` 使用 UTC GregorianCalendar/明确字段读取替换 Instant/ZoneOffset，保持 Locale.US、月份从 1 起和原 NMEA 时间格式；不启用新的位置权限。

`AndroidMediaSink` 两处 computeIfAbsent 改为明确的受锁 get/create/publish，关闭与删除使用同一生命周期锁/状态。MicrophoneUplink 和 VideoDecoder 的构造可能持有资源，不能用会创建两个对象却不释放失败者的简单 putIfAbsent 替代。

仍需检查 BouncyCastle、JmDNS 和其他 JAR 的实际 API 使用。若后续解析/DEX 扫描发现更广泛标准库依赖，才引入并锁定匹配工具链的 `desugar_jdk_libs`，由 API 23 路径执行证明覆盖范围；不得两套方案无规则混用。

## 7. 音频与视频

### 7.1 音轨与焦点

`AndroidMediaSink.AudioRenderer.createTrack` 的 `built.audioAttributes` 仅 API ≥29 使用；旧版保存**实际创建该音轨时的最终 attributes**，包含 legacy stream 失败后的 fallback attributes。不能保留最初打算使用、实际未使用的属性。

API 23 保留 AudioTrack.Builder、bufferSizeInFrames、routedDevice、带 writeMode 的 write，SDK 版本表确认这些调用可用。`USAGE_ASSISTANT` 在 API <26 使用兼容 speech usage，避免把新 usage 值交给旧 HAL。

新增 `shared/.../media/AudioFocusCompat.kt`，API ≥26 的 AudioFocusRequest 类型仅放在对应实现中；API 23 使用旧 requestAudioFocus(listener,stream,gain) / abandonAudioFocus(listener)，每次申请与释放复用同一 listener。

先让 `AudioFocusCoordinator` 和 `CarPlayMediaKeys` 都经兼容封装，维持现有职责以减少现代系统回归。H6 profile 中一期媒体焦点由 MediaKeys 的单一会话持有，sink 的额外焦点请求关闭；导航用独立轨叠加。若实测原车路由必须启用 sink 焦点，则在该 profile 中协调成一个 owner，不能同时启用两套互相争抢焦点的申请。

原实现“永久失焦仍继续播放”是已有特定车机策略，不当作 H6 结论。H6 一期永久失焦立即将媒体本地静音，并通过现有 media button 通道向手机发送 PAUSE；瞬时失焦暂时静音，CAN_DUCK 压低音量。新增 sink 的 `setMediaFocusState` 接口，按 generation 保存静音/duck 状态，对新建媒体轨也应用，导航轨不随之静音；GAIN 只解除本次焦点引起的静音，不自动向手机发 PLAY。保留 PCM 消费及有界队列，避免暂停 AudioTrack 后把阻塞 write 永久卡住。用户主动选了原车收音机时不得周期性抢回焦点；新播放动作可按既有明确事件重新请求。此行为按设备 profile 隔离，较新设备保留既有策略。

### 7.2 API 23 欠载与重缓冲

`startPlayback`、`maintainPlaybackBuffer`、`logStatsIfDue` 三处 underrunCount 均需要版本保护。API 23 以如下条件判定媒体缓冲耗尽：

```text
成功写入的完整 PCM frame 数 − 本代 AudioTrack 累计播放 frame 数 ≤ 容差
且 compressed/RTP 待处理队列为空
且缺少新 PCM 持续超过短窗口（初始 40 ms）
且当前属于已启动的媒体音轨
```

时间窗口使用单调时钟；容差和 40 ms 为初始参数，须由真机短缺包/长缺包测试调整，不称为性能实测。

扩展 `AudioBufferProgress`：每次采样先更新 unsigned 32-bit head，即使本次不满足 rebuffer 条件；维护 track generation。新建/stop/flush 重置基线，pause 后继续使用本轨基线。发生倒退且不能解释为合理回绕时标记进度暂不可用并重建基线，不能把一次归零当成播放了 2^32 帧。

只统计实际成功 write 的字节，负值和部分写入不计入未播放数据。先 pause 等待预缓冲，避免 flush 丢掉尚未播出的导航/媒体 PCM；短尾包沿用有界尾等待。API ≥24 保留系统欠载信号结合进度判断。

诊断分别输出 `underrunSource=system|estimated`、队列时长、rebuffer 次数及 generation；API 23 的系统欠载计数写为 unavailable，不伪报 0。估算结果不使用“硬件 underrunCount”的字段名。

### 7.3 H.264 与 Surface 恢复

首选经运行时 MediaCodecList 与实际 configure/start 验证的 TI AVC decoder，不直接因为 XML 中出现名称就硬编码成功。API <29 不使用 isHardwareAccelerated 判断；记录候选名称与选择原因，并实际解码。

独立decoder探针可先测试1280×720/30fps样本，验证硬解余量；**实际CarPlay协商以TextureView/Surface的稳定尺寸为准**。若Surface为1160×720，先测1160×720/30fps；失败时通过现有`CarPlayDisplayScale.apply`的0.8x生成928×576，再重启会话协商，保持相同宽高比。不能把这种非16:9窗口强制改成960×540而忽略视频viewport。若Surface实测为1280×720，同样按其实际比例缩放。Android显示wm设置或仅图标/字体缩放不代表视频协商已降级。

复用`textureListener → scheduleDisplaySize/applyDisplaySize → startCarPlay/createAirPlayConfig`现有路径；在非零稳定Surface到达前不启动显示协商，保持已有尺寸变化去抖。通过`DisplayDiagnosticSnapshot`记录physical/app/window/Surface/negotiated尺寸、实际缩放和safeArea来源。只读`dumpsys display`用于系统基线，不能替代普通应用中的Surface测量。

`CarPlayTouchMapper.contacts`已经按MotionEvent局部坐标除以View尺寸归一化；不要再次减系统保留区域的120px。边角、中心、拖动和多点触摸按真实内容矩形验收；若引入letterbox，增加viewport坐标转换并拒绝黑边触摸。

保留 `VideoDecodeQueue` 的帧龄和恢复机制；Surface 丢失时停止向无效 Surface 输出，不继续积压无限帧。恢复时调用版本可用的 setOutputSurface 路径或重建 codec，并请求关键帧；更换 Surface/codec 的 callback 需检查 generation。

倒车和桌面切换不默认销毁整个有线会话；若 firmware 重置 USB，则由 detach 路径结束会话并提供重新连接。ACC 真关机与休眠单独记录，不能在未实测前承诺无感恢复。

### 7.4 Siri/电话延期接口

一期 `AirPlayConfig.microphone=false`，`AirPlayInfoPlist` 不宣告任何输入格式，UI 说明语音上行待验证。二期增加 `AudioInputCapabilities`，PCM 与 Opus 分开宣告；未成功创建 Opus encoder 时不再宣告 Opus input。

后续先普通应用 PCM 录音，再测试手机是否接受协商；上行、下行、AEC/回声、原车蓝牙电话占用分别验收。需要软件 Opus 时另行评估原生 ABI、CPU、许可和打包，不把它偷偷加入一期。电话功能延期不是承诺原车蓝牙与有线 CarPlay 电话能无条件同时使用。

## 8. P0.5 普通应用探针与落地门槛

设计采集轮未制作探针；开发轮现已完成[探针审阅包](../../tools/android6-probe/README.md)，未授权或执行安装运行。源码、包名、权限、签名摘要、APK哈希和各按钮动作供用户审阅，设备执行仍须具体授权。

建议独立 `tools/android6-probe/` Gradle 项目，避免当前 minSdk 28 的 shared/UI 依赖阻塞探针；native Activity、小体积、API 23、独立包名 `com.shihab.diplay.android6probe`。不嵌入认证、存储权限或自动设备扫描写操作。能力探针只证明平台前提；正式兼容层完成后，还必须在 DiPlay APK 上再测同一链路。

| 门槛 | 验证动作 | 通过标准 / 失败处置 |
| --- | --- | --- |
| G0 安装与基础 | 经批准安装探针；冷启动、通知、权限拒绝 | 普通 APK 可安装运行；INSTALL 错误保持原码，不能提权绕过 |
| G1a USB 授权 | deviceList、用户点 USB 授权、openDevice | 普通 UID 成功授权/open；拒绝有重试入口 |
| G1b 切换 | 用户点 vendor request，等待重枚举，再授权，输出全部配置/接口 | 找到 USBMUX+实际 NCM 描述符；若仍没有，不把 config 4 伪当成功 |
| G1c 驱动生命周期 | 用户点 claim/旧 queue，空闲 read、取消/close、拔插 | read 超时不丢请求；close 后工作线程在目标 1 秒内退出，无持续累积 |
| G1d 协议数据 | 正式 USB 兼容层完成后执行 USBMUX 及 NCM 数据验证 | 分段/粘连数据完整，写入边界正确；需要协议级流量，不能由空读测试替代 |
| G2 视频 | 内置已知 H.264 样本，Surface 解码，记录 codec/首帧/掉帧 | 真机至少连续 10 分钟，画面可见；倒车/桌面后可恢复 |
| G3 音频 | 用户点短测试音；旧音频焦点及原车音源切换 | 可听、停止干净；不持续覆盖原车声音；实际播放不由 XML 替代 |
| G4 VPN | 用户明确接受 VPN 系统弹窗，establish 并关闭 | 普通 UID 可建立及释放 TUN；无 consent UI/服务限制则阻塞现有有线路线 |
| G5 认证 | 可用身份配置；目标手机接受，记录失败阶段 | 补齐认证后才开始完整 CarPlay 验收 |
| G6 一期闭环 | 目标手机/固件上视频、触摸、音乐、导航和重连 | 满足第 11 节矩阵；不把 Siri/电话标为已支持 |

停止条件：G0、G1、G4 或 G5 缺失，不能交付当前架构下的完整有线 CarPlay。可以交付基础兼容 APK/诊断结果，但需按实际能力命名；用户已经确定不走系统提权路线。

## 9. 二期无线与生命周期设计

API 23 仅选择手动原车热点。统一 `PlatformCapabilities` 对设置加载、保存和 `CarPlayController.startWirelessHotspot` 的判断：API <26 的 LOCAL_ONLY_HOTSPOT 或 API <29 的当前 WIFI_P2P 实现都归为 MANUAL，缺少 SSID/密码时返回可操作配置错误，不能落入不支持的管理器。

原设置迁移已把旧模式变为 MANUAL，但 Controller 仍把旧系统 P2P 回退到 LocalOnlyHotspotManager；两处必须共享有效模式决策。控制层失败要保留 requested/effective/backend 信息。

无线实测包括原车热点接口与 IPv6、蓝牙 iAP2/RFCOMM、地址变化、Bonjour/mDNS，以及热点和 ADB 同网络时的观测连续性。蓝牙特征存在和当前网络 ADB 可达不能证明这些链路已通。不根据 API 版本猜 5 GHz 支持，也不自动更改车机热点设置。

生命周期二期补全：方向盘标准媒体键、ACC 日常休眠/唤醒、进程回收及 USB 断电。厂商私有 ACC/CAN 广播在没有记录和授权时不接入，不凭 `BOOT_COMPLETED` 替代。

## 10. 代码改动位置与任务拆分

表中 `…` 表示 `src/main/java/com/shilapi/xcertplay/` 包根；列出的新增文件是计划位置，当前尚不存在。入口行号以 2026-10-01 源码为准。

| ID / 优先级 | 现有文件与锚点 | 计划改动 | 核验 |
| --- | --- | --- | --- |
| B1 / P0 | `mobile/build.gradle.kts:18`、`common/build.gradle.kts:13`、`shared/build.gradle:9` | minSdk 23，其他编译/目标版本保留 | 合并 Manifest / APK badging / NewApi lint |
| B2 / P0 | `shared/build.gradle` externalNativeBuild；`shared/src/main/jni/Application.mk:1` | API 23 平台，ARM32 保留 | ELF 符号、API 23 加载 |
| R1 / P1 | shared `…/transport/LockdownPlistChannel.kt` encode/decode；`LockdownPairRecord.kt` PEM；`LockdownTlsEngineFactory.kt` decode | 接入新增 `…/compat/CompatBase64.kt` | 配对记录/证书字节一致、MIME 与非法 padding |
| R2 / P1 | shared `…/mfi/RemoteMfiAuthenticationClient.kt`；`…/adb/AdbKeys.kt` | 同一 Base64 封装，避免非默认路径漏改 | 普通 Base64 语义、错误分类 |
| R3 / P1 | shared `…/transport/Iap2LocationClient.kt:118` Timestamp | UTC Calendar 替代 Instant | 既有 NMEA 测试、日/月/年边界 |
| R4 / P1 | shared `…/media/AndroidMediaSink.kt:237,265` | 替代两个 computeIfAbsent；同步生命周期 | 并发 start/close 不重复创建/泄漏 |
| S1 / P1 | common `…/CarPlayHostActivity.kt:3180` | service 启动版本分支 | API 23/26/现代系统通知与关闭 |
| S2 / P1 | common `…/DiPlaySessionService.kt:26` onStartCommand | API 26 channel guard、NotificationCompat | 旧系统可用断开 action，空会话不保留服务 |
| C1 / P1 | common `…/AirPlayPersistence.kt` loadSettings；`…/CarPlayHostActivity.kt` loadPersistedSettings/createMediaSink/onCreate/maybeStartCarPlay；`…/DiPlayActivity.kt` 设置 UI | 新增 common `…/H6CompatibilityProfile.kt`，shared `…/compat/PlatformCapabilities.kt`；有效设置统一；录音关闭时启动前提已解决 | 历史高性能/无线设置不能绕过能力判断；无录音授权仍能进入有线启动 |
| U1 / P0 核心 | shared `…/transport/IphoneUsbHost.kt` Iap2UsbSession.read/close（354/401 附近） | 接入新增 UsbReadTransport/LegacyUsbReadPump | 超时不取消；唯一 waiter；关闭可退出 |
| U2 / P0 核心 | 同文件 Iap2UsbSession.write（341） | 新增 UsbMuxWriteCompat：16 KiB、offset、总 deadline | 16 KiB 边界、短写、半写失败不重发 |
| U3 / P0 核心 | shared `…/transport/NcmUsbBridge.kt` readChunk/recv/close（220/93/114 附近） | 旧 pump、保留流重组、buffer 上界 | 多 chunk、粘包、pad、退出和 generation |
| U4 / P1 | 同文件 send（60）；`…/transport/Ntb16Codec.kt` | NTB 完整写/上界；运行阶段失败升级 | MTU1500；短写和 NAK 分阶段 |
| U5 / P1 | shared `…/transport/IphoneCarPlayConfiguration.kt:30`；`…/transport/NcmFunctionDiscovery.kt`；`…/orchestration/CarPlayController.kt` wired bring-up | 保留描述符规则；增强切换失败/重枚举诊断 | 配置不固定 6，不能把 config4 无 CDC NCM 判为成功 |
| U6 / P1 | shared `…/network/Ipv6NcmBridge.kt` close；`…/network/CarPlayVpnService.kt` attach/release；Controller close | 统一关闭所有权、清理、generation 检查 | VPN拒绝、拔线、旧回调、失败后重连 |
| A1 / P0 | shared `…/media/AndroidMediaSink.kt:804` createTrack | API29 getter guard、保存最终 attributes；旧 assistant usage | 真正启动 PCM 音轨，非仅构造 sink |
| A2 / P1 | 同文件 AudioFocusCoordinator（35）；common `…/CarPlayMediaKeys.kt:89`；Activity createMediaSink | 新增 shared `…/media/AudioFocusCompat.kt`、sink setMediaFocusState；MediaKeys attach 注入焦点回调；H6 profile | listener 身份、释放、原车抢焦点、新轨状态、无卡死 write |
| A3 / P0 | shared `…/media/AndroidMediaSink.kt:1117,1125,1145`；`…/media/AudioBufferProgress.kt` | 系统计数 guard、进度/时间估算、track reset | 饥饿/恢复、回绕、flush、新轨 |
| V1 / P1 | shared `…/media/AndroidMediaSink.kt` VideoDecoder；`…/media/MediaCodecSupport.kt`；common Activity Surface 回调 | 候选 decoder 诊断、单屏 AVC30、Surface 恢复 | TI 真解码、低分辨率回退、关键帧恢复 |
| V2 / P1 | common `…/CarPlayHostActivity.kt` textureListener（350）、createAirPlayConfig（2729）、currentActivitySize（2844）、applyDisplaySize（3219）、onHostTouch（3420）；`…/DisplayDiagnosticSnapshot.kt`；shared `…/airplay/CarPlayDisplayScale.kt`、`…/media/CarPlayTouchMapper.kt` | 保护现有Surface驱动协商；补充系统保留区域、比例缩放、触摸和尺寸诊断；必要时加viewport映射 | 历史1160×720、0.8x=928×576；不重复减120；尺寸变化不反复重连 |
| D1 / P1 | common `…/DiPlayActivity.kt` exportDiagnostics；`…/DiagnosticExportStore.kt`；`common/src/main/AndroidManifest.xml`、`common/src/main/res/xml/` | chooser 缺失回退、FileProvider、私有诊断文件 | 无 DocumentsUI/无分享目标都不崩溃 |
| L1 / P1 | common `…/CarPlayHostActivity.kt` CarPlayBackgroundSession、shutdown；`…/BootReceiver.kt` | 检查 H6 会话保留与停止路径 | 桌面/倒车 Surface、进程结束、手动断开 |
| M1 / P2 | shared `…/airplay/AirPlayConfig.kt`、`AirPlayInfoPlist.kt:131`；`…/media/MicrophoneUplink.kt:57`、`OpusEncoder.kt` | 一期关输入；二期新增 AudioInputCapabilities 与按能力宣告 | /info 不误报，PCM 协商、编码器失败 |
| W1 / P2 | common `…/AirPlayPersistence.kt:237`；shared `…/orchestration/CarPlayController.kt:1603`；`…/network/ManualHotspotManager.kt` | 统一 MANUAL 回退，验证接口/IPv6/热点 | 历史配置、无热点、错密码、蓝牙断线 |
| T1 / 跨阶段 | shared/common `src/test`、新增 API23 `src/androidTest` | 第 11 节必要测试，现有用例扩展 | API23/26/28/29及现代系统分支 |

### 推荐实施批次

1. **P0 / 证据与探针**：先完成 G0/G1a/G1b/G2/G4 平台门槛；G1c 验证旧队列驱动能关闭。认证配置并行作为外部阻塞跟踪。没有平台门槛不投入完整协议迁移。
2. **P1a / 基础兼容**：B1/B2、R1～R4、S1/S2、C1、A1/A2/A3、D1；API 23 可安装冷启动、设置、通知及实际音频测试。
3. **P1b / 有线兼容**：U1～U6；先协议回放及 API 23 硬件测试，再结合已补齐认证执行 G1d/G5。
4. **P1c / 一期交付**：V1/V2/L1，G6 完整验收，稳定性和较新系统回归；交付已验证范围及缺口。
5. **P2 / 二期**：W1/M1、ACC/方向盘。根据一期测量和用户后续优先级重新评估，不并入一期成功定义。

建议熟悉项目的一人按一期 **约 10～18 个有效工作日**预留：探针与门槛 2～3、基础兼容 3～4、USB 与协议 3～6、实车闭环/稳定性/回归 2～5。该区间不是实测工期；认证等待、驱动门槛失败、缺少测试窗口另计。二期须独立评估，旧计划的“3～5 周完整适配”不直接沿用为一期承诺。

## 11. 验证与验收计划

### 11.1 自动化测试

| 对象 | 复用 / 新增位置 | 关键场景 |
| --- | --- | --- |
| USB pump | 新 `shared/src/test/.../transport/LegacyUsbReadPumpTest.kt`，可注入 USB 调用边界 | 超时后迟到完成、close/queue 竞争、取消失败、意外请求、队列溢出、旧 generation |
| USBMUX write | 新 `…/transport/UsbMuxWriteCompatTest.kt` | 0/1/16384/16385/65536 bytes、正短写、负值、总 deadline、并发消息不交错 |
| NCM | 现 `…/transport/Ntb16CodecTest.kt`，新增桥接重组测试 | NTB 跨 16 KiB、两个 block 粘连、多个 datagram、精确 512 边界 pad、非法长度、正短写 |
| 播放进度 | 现 `…/media/AudioBufferProgressTest.kt`、`MediaAudioBufferTest.kt` | 32 位回绕、head reset、部分 write、短缺包不反复暂停、长缺包重新缓冲、尾包 |
| 媒体 API | 现 `…/media/AndroidMediaSinkStateTest.kt` + 真机/模拟器 smoke | 创建并播放音轨，禁止遗漏 API29 getter；无系统 underrunCount 的实际路径 |
| 标准库 | 现 NMEA/配对相关用例 + 新 CompatBase64Test | UTC 边界、PEM 精确换行、未 padding、MIME 杂字符、坏证书、并发资源创建关闭 |
| 显示与触摸 | 现`CarPlayDisplayScaleTest`、`SafeAreaTest`、`DisplayDiagnosticSnapshotTest`；新增media `CarPlayTouchMapperTest`及Activity实测 | 1160×720→928×576、1280实际Surface、Insets差异、无重复120px扣减、边角/拖动、多点、稳定尺寸去抖 |
| 无线迁移 | 现 `common/src/test/.../HotspotModeMigrationTest.kt` 等 | UI 与 Controller 对 API23/26/29 的一致模式判定 |
| 服务/诊断 | 新 common API23 Robolectric 或 instrumentation 场景 | channel 不被调用、通知 action、无 DocumentsUI、拒绝权限、失败后重试 |

API 23 Robolectric 用例必须实际执行相关路径，不只实例化入口。USB 驱动、TI decoder、音频 HAL、VPN 及原车生命周期用实车测试；单元模拟不能证明这些功能。

实施后的基础命令沿用项目方式：

```sh
./gradlew :shared:testDebugUnitTest :common:testDebugUnitTest :mobile:lintDebug :mobile:assembleDebug
```

新增 API 23 instrumentation 后再执行连接测试设备上的对应任务。`assembleStandaloneDebug` 仅在认证材料到位后用于完整车测。运行前明确测试设备目标；本轮没有执行任何 connectedAndroidTest。

### 11.2 实车一期最低验收

| 场景 | 通过条件 |
| --- | --- |
| 基础 | API23 冷启动/设置/通知/拒绝 USB 与 VPN 后恢复，无 API 不兼容崩溃 |
| 完整连接 | 指定 iPhone/iOS 与固件组合通过认证及会话，明确 codec、连接方式、视频协商参数 |
| 画面/触摸 | 持续H.264输出；physical/app/Surface/negotiated尺寸可追踪，验证1160×720候选及同比例缩放；边角/拖动映射正确，无持续绿屏/黑屏 |
| 音乐/导航 | 分别有声音，播报结束音乐恢复；原车音源切换行为符合单一焦点策略 |
| 拔插 | 至少 10 轮 USB 拔插与手动重连；无累积等待线程/TUN/USB FD，无需重启车机恢复 |
| 桌面/倒车 | 各至少 5 轮；Surface 恢复并能得到关键帧；若固件重置 USB，提示并重新连接 |
| 稳定性 | 至少 2 小时会话；记录 PSS/线程/FD/队列高水位，无持续单调增长或不可恢复卡死 |
| 回归 | API26 现代 USB 切点、API28旧音轨属性分支、API29新属性分支及一个当前支持系统实测/自动化覆盖 |
| Siri/电话 | 标记“未验收/二期待验证”，不要用音乐通过代替语音和电话通过 |

初始恢复性能目标：USB 关闭线程 1 秒内退出、可再次手动连接、Surface 恢复 5 秒内可见画面。数值属于设计目标；若 ROM 实测不满足，记录分阶段耗时并决定调参或降级，不能用放宽无限等待达到通过。

ACC、方向盘与无线为独立记录；一期文档不得宣传通用 Android 6 支持或未实测的休眠无感恢复。

## 12. 风险、替代路线与回滚

| 风险 | 处置 / 回滚 |
| --- | --- |
| 安装策略不允许普通 APK | G0 停止；不使用 ADB root 身份把应用变成系统应用 |
| vendor request 后仍无真正 NCM | 保留完整接口诊断，判断手机/固件组合门槛；不凭 config 编号改规则 |
| cancel/close 无法退出驱动等待 | G1 停止自动重连，保留探针证据；不泄漏新线程掩盖问题 |
| VPN 无法建立 | 现有 wired NCM/VPN 架构阻塞；用户未授权系统方案，不私自改网络栈 |
| TI decoder 运行失败 | 降级视频协商并重测；软件解码不能未经评估成为交付默认 |
| 运行认证缺失/不被手机接受 | 单独阻塞 G5；基础 APK、USB probe 结果保留，不误归为 Android 版本故障 |
| 音频/原车占用冲突 | 保留原车优先和明确停止入口；不抢改原车服务 |
| 新系统回归 | 兼容实现按 API 切分，设备策略按 profile 切分；未通过回归不发布 |

真实考虑过的替代方案：

1. **只降 minSdk**：改动最少，但 USB26、焦点26、underrun24、音轨属性29及 Base6426 已有明确运行障碍，无法作为可交付方案。
2. **旧系统统一同步 bulk 读**：超时编程容易，但本项目 NCM 注释明确指出同步 byte[] 读取的 GC/JNI 与取消间隙问题，优先持续 async request + 独占等待线程。
3. **API23 请求定时 cancel 实现 read 超时**：容易复用当前 USBMUX 结构，但会破坏 NCM 持续挂起并增加完成队列歧义，应用层超时替代。
4. **整体降低 AndroidX/重写 UI**：可缩小旧设备负担，但当前 AAR 下限支持23且尚无 UI 性能失败证据，先保留并测量。
5. **全量 core library desugaring**：统一标准库兼容、未来维护省力；目前未验证所需 Base64/并发 API 覆盖与新依赖版本，先定点封装，发现更广泛库依赖时重访。
6. **系统提权适配**：可能绕过厂商访问限制，但与用户确认的普通 APK / 原车共存边界冲突，不进入本设计。

实施前保存当前可运行 APK、签名摘要、有效配置和诊断，记录本地代码快照。设计采集副本当时没有Git；当前实施工作区已有Git基线（见实施交接），未提交改动应先审阅保留，再按需记录版本。兼容改动失败时回到已记录的源码/APK，保留原车应用。更新/回退不得通过默认卸载丢掉配对和配置。

## 13. 交付物与当前完成状态

- 已完成：两批批准的只读证据、设备基线、需求确认、当前依赖与Manifest基线、来源会话正文/附件复核、显示区域修正、文件/函数级实施计划。
- 当前已进入：用户授权初步API23代码改造，本机测试/构建已完成并提供探针审阅包；尚未授权或执行车机安装运行。
- 未完成的落地门槛：普通 APK 安装、应用 USB/重枚举/数据、实际硬解/播放/VPN、认证、完整会话与稳定性。
- 后续交付：探针审阅包 → 基础兼容 APK → 含可用认证的车测包 → 指定车型/固件/手机组合的验收记录。

所有“待验证”是显式执行门，不是未经证实的功能承诺。需求边界可以定稿；完整落地可行性须按 G0～G6 逐门转为实测结论。

用户稍后返回ADB环境时只补必要状态快照，命令见[NEXT_CHECKS.md](NEXT_CHECKS.md)；精确SoC SKU和二期功能查询不再作为一期设计前置项。
