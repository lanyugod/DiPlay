# Android 6 车机兼容改造与适配计划

## 当前实施与实车验证入口（2026-10-03）

已完成用户批准的两批只读车机采集，并对齐一期范围：**普通 APK、有线画面/触摸/音乐/导航、原车功能共存；Siri/通话延期，无线二期**。

- [详细设计与代码改动位置](DETAILED_DESIGN.md)：实施方案、兼容契约、任务拆分、探针门槛与验收矩阵。
- [实施交接](IMPLEMENTATION_STATUS.md)：已提交的API23初步适配及电脑端验证。
- [2026-10-03探针结果](PROBE_RESULTS_2026-10-03.md)：手机接入后的采集已完成；标准会话提交成功后被自动删除，主动测试未开始。
- [安装策略与可恢复方案](INSTALL_POLICY_OPTIONS.md)：目标固件的安装/启动/卸载检查、标准会话实测、签名信任规则、停用影响及恢复限制；未修改策略。
- [本次验证结果](VALIDATION_RESULTS.md)：Android 6.0.1/API23、ARM32、1280×720、iPhone 枚举等实测事实及未验证项。
- [来源会话复核](CONVERSATION_REVIEW.md)：已读取原会话消息及用户ADB附件，补齐app1160×720、SoC标识及历史未插iPhone上下文。
- [下一次必要检查](NEXT_CHECKS.md)：用户恢复ADB环境后批量执行的最小只读命令和应用探针门槛。

API23初步适配已提交为`0e77dbf`，本轮另补VPN缺失保护并修复探针UI。已按设备所有者驻车授权建立可恢复临时窗口（整包停用落盘后重启）；探针与主体基础包安装/冷启动成功，并完成卸载。普通UID USB open及主动切换后CDC NCM描述符通过；配置切换仍受当前USB音频/HID占用阻塞，queue未进入。TI 720p样本循环10分钟、短测试音可听、一次探针桌面Surface恢复通过。普通VPN授权窗口缺失；额外批准ACTIVATE_VPN临时授权后无路由TUN建立/释放成功，授权已撤销。主体补齐VPN缺失错误处理；source-only缺CarPlay认证，完整会话未开始。测试包均卸载，安全包/组件恢复DEFAULT，最终重启核验完成：两服务PID1321、system_server PID532 hasBound=true，包/组件DEFAULT=0、/system只读、无测试包和TUN残留。详见[临时窗口与适配实测](SECURITY_PROBE_WINDOW_2026-10-03.md)。

下文是 2026-09-30 原计划背景，具体范围、代码方案和当前证据状态以以上当前文档为准。

## 执行前入口（2026-09-30 更新）

已补充共享对话及最后审计意见的复核、分步 ADB 采集流程。目标车型为用户提供的 **2021 第二代哈弗 H6 冠军版**；Android 6 和 ADB 可用仍需用本机输出复核，其他硬件参数不以车型资料推断代替实测。

1. 先执行 [ADB 分步验证清单](ADB_VALIDATION.md)，按暂停点分批回传。
2. 用 [验证结果模板](VALIDATION_RESULTS.md) 记录已实测、未确认、待探针和可选延期项。
3. 下文保留原始改造计划供参考；**在采集结果审查完成前，不开始正式改造**。后续如需 P0.5 USB 探针，应另行确认后制作和运行，不能认为只读 ADB 已验证应用 USB 授权、CarPlay 重枚举或硬解能力。

审计修正、探针门槛和具体执行顺序以 `ADB_VALIDATION.md` 为准；原有工作量估算尚未根据本机结果重新评估。

---

- 编制日期：2026-09-30
- 目标系统：Android 6.0 / 6.0.1（API 23）
- 项目：DiPlay（CarPlay 接收端）
- 当前状态：静态源码分析完成，尚未实施
- 验证边界：未执行构建、依赖解析、API 23 运行测试或实车测试；依赖最低 SDK、厂商硬件能力及实际工作量仍需确认

> 本计划中的代码行号对应分析时的源码快照，后续修改后可能变化。完成适配不代表所有 Android 6 车机通用兼容，最终支持范围应限定到实际验收过的车型、固件、连接方式和 iPhone/iOS 组合。

## 1. 结论与推荐路线

可以推进 Android 6 适配，但不能只把 `minSdk` 从 28 改为 23。已发现构建配置、USB 接口、前台服务、音频焦点、音频缓冲统计及 Java 标准库调用等兼容障碍。

推荐路线：

1. 以 `mobile` 模块为基础，确认目标车机与认证配置具备运行前提。
2. 完成 API 23 安装、启动、权限、设置与会话服务的基础兼容。
3. 优先完成有线 CarPlay 的画面、触摸和音乐闭环，单独验收 Siri 和通话。
4. 增加基于车机自带热点的无线连接。
5. 完成 ACC 休眠唤醒、倒车切换、后台运行、方向盘按键及性能适配。
6. Wi-Fi Direct、HUD、仪表、电池联动作为后续独立工作，不纳入首期承诺。

普通 Android 车机不等于 Android Automotive OS。`automotive/src/main/AndroidManifest.xml:9` 声明了必需的 `android.hardware.type.automotive`，不建议因为设备是车机就选择 `automotive` 模块。系统类型需在 P0 确认，默认使用 `mobile`。

## 2. 目标范围

### 2.1 首期目标

- Android 6 能安装、启动和使用设置页面。
- 权限拒绝、再次授权和系统组件缺失时不崩溃。
- 有线 CarPlay 能建立连接，显示画面、接收触摸并播放音乐。
- 连接过程中后台服务和通知正常。
- USB 拔插后能够释放资源并重新连接。
- 明确记录 Siri、通话、导航播报的支持状态，不以音乐可用代替全部音频验收。

### 2.2 后续目标

- 车机自带热点无线连接及断线恢复。
- ACC 休眠唤醒、倒车抢占和前后台切换恢复。
- 标准媒体键与目标车机方向盘按键适配。
- 长时间运行稳定性和性能优化。

### 2.3 首期不承诺

- Android 6 上的现有 Wi-Fi Direct 实现直接可用。
- 所有车型及固件兼容。
- BYD HUD、仪表、副屏和电池联动通用兼容。
- 通过应用代码解决厂商不开放的 USB、蓝牙、麦克风或系统安装权限。
- 对未来 iOS 版本及实验性配件身份的接受情况作保证。

## 3. 当前差距与改造清单

### 3.1 构建配置、依赖和原生库

**已确认：**

| 位置 | 当前配置 | 改造方向 |
| --- | --- | --- |
| `mobile/build.gradle.kts:18` | `minSdk = 28` | 调整为 23 |
| `common/build.gradle.kts:13` | `minSdk = 28` | 调整为 23 |
| `shared/build.gradle:9` | `minSdk = 28` | 调整为 23 |
| `shared/build.gradle:18` | `APP_PLATFORM=android-28` | 调整为 API 23 并重编译 |
| `shared/src/main/jni/Application.mk:1` | `APP_PLATFORM := android-28` | 与 Gradle 原生构建配置保持一致 |
| `shared/build.gradle:13` | `arm64-v8a`、`armeabi-v7a`、`x86_64` | 已有 ARM32，仍需核对目标车机 ABI |

**实施事项：**

- 保留现代编译工具链；不因支持 Android 6 而将 `compileSdk`、`targetSdk` 一起降到 23。
- 解析 `gradle/libs.versions.toml` 中 AndroidX、Compose、Car App 等直接与传递依赖，核实真实最低 SDK。
- 如有不支持 API 23 的依赖，评估兼容版本或隔离非核心功能，不使用强制覆盖最低 SDK 作为运行兼容证明。
- 在 API 23 平台目标下重编译 JNI 库，检查动态链接符号并实测加载。
- 验证最终 APK 的最低 SDK、ABI、合并 Manifest 与签名。
- 先验证现有 UI 依赖及性能，不为 Android 6 直接重写全部界面。

**待验证：** 实际依赖解析结果、工具链可用性、车机 ABI 和原生库加载结果。

### 3.2 Java 标准库兼容

**已确认的调用：**

| API | 代码位置示例 | 处理方向 |
| --- | --- | --- |
| `java.util.Base64` | `shared/src/main/java/com/shilapi/xcertplay/transport/LockdownPlistChannel.kt:217` | core library desugaring 或兼容封装 |
| `java.time.Instant` | `shared/src/main/java/com/shilapi/xcertplay/transport/Iap2LocationClient.kt:118` | core library desugaring 或兼容实现 |
| `computeIfAbsent` | `shared/src/main/java/com/shilapi/xcertplay/media/AndroidMediaSink.kt:237` | 核实脱糖覆盖或采用保持并发语义的实现 |

当前构建文件设置了 Java 11 编译兼容，但没有看到 core library desugaring 配置。语言特性脱糖不等于标准库 API 脱糖。

**实施事项：**

- 选择与工具链匹配、覆盖所需 API 的 core library desugaring 配置。
- 未覆盖的 API 使用兼容封装。
- Base64 替换保留普通编码与 MIME 编码的换行、填充及解析语义。
- 在 API 23 实际执行配对、TLS、定位和媒体相关路径，而不只检查编译结果。

### 3.3 USB 传输兼容

**已确认：** 源码直接使用 API 26 起提供的 `UsbRequest.queue(ByteBuffer)` 和 `UsbDeviceConnection.requestWait(timeout)`。

关键位置：

- `shared/src/main/java/com/shilapi/xcertplay/transport/IphoneUsbHost.kt:370`
- `shared/src/main/java/com/shilapi/xcertplay/transport/IphoneUsbHost.kt:377`
- `shared/src/main/java/com/shilapi/xcertplay/transport/NcmUsbBridge.kt:235`
- `shared/src/main/java/com/shilapi/xcertplay/transport/NcmUsbBridge.kt:245`

当前 USBMUX 接收缓冲区为 64 KiB，NCM 为 32 KiB，需要处理旧版 USB API 的长度限制和不同调用的行为差异。

**实施事项：**

- 提取 USB 兼容层，新系统保留现有实现。
- API 23 使用旧版队列接口与专用接收线程。
- 在应用层实现超时语义，并正确处理取消、拔线、关闭和线程退出。
- 根据旧系统限制进行传输分块，处理短读、短写和协议重组。
- 同时审查读与写，不能只修改接收缓冲区。
- 保留 NCM 持续接收的设计意图，避免为实现超时而频繁取消请求导致丢包。
- 验证 USBMUX、NCM 接口选择与切换，以及 VPN/TUN 链路。

**禁止的简单替换：** 不能仅将 `requestWait(timeout)` 改为 `requestWait()`；无超时等待需要配套可退出的线程与资源管理。

**实车前提：** USB 数据口、Host 模式、驱动及接口切换能力必须可用。

### 3.4 前台服务和通知

**已确认：**

- `common/src/main/java/com/shilapi/xcertplay/CarPlayHostActivity.kt:3180` 直接调用 `startForegroundService()`。
- `common/src/main/java/com/shilapi/xcertplay/DiPlaySessionService.kt:26` 直接创建 `NotificationChannel`。
- `common/src/main/java/com/shilapi/xcertplay/DiPlaySessionService.kt:29` 使用带渠道参数的 `Notification.Builder`。

上述调用需要 API 26 兼容分支。

**实施事项：**

- API 26 以下使用 `startService()`，服务启动后调用 `startForeground()`。
- 使用兼容的通知构建方式，例如 `NotificationCompat`。
- 仅在 API 26 及以上创建通知渠道。
- 保留较新 Android 系统的前台服务类型和权限处理。
- 验证切回桌面、其他导航应用及倒车界面后会话的存续与恢复。

### 3.5 音频焦点与缓冲

**已确认的问题 A：直接依赖 API 26 的 `AudioFocusRequest`。**

- `common/src/main/java/com/shilapi/xcertplay/CarPlayMediaKeys.kt:89`
- `shared/src/main/java/com/shilapi/xcertplay/media/AndroidMediaSink.kt:81`

**改造：** 统一音频焦点兼容封装；Android 6 使用旧版 `requestAudioFocus(listener, streamType, gain)` 和对应释放方法，保留音乐、导航、电话、Siri 的差异化需求。

**已确认的问题 B：缓冲逻辑使用 API 24 的 `AudioTrack.underrunCount`。**

- `shared/src/main/java/com/shilapi/xcertplay/media/AndroidMediaSink.kt:1117`
- `shared/src/main/java/com/shilapi/xcertplay/media/AndroidMediaSink.kt:1125`
- `shared/src/main/java/com/shilapi/xcertplay/media/AndroidMediaSink.kt:1145`

**改造：** 在 API 23 使用播放头位置、已写数据量、队列状态和时间阈值判断缓冲状态。不能只将欠载统计固定为零，因为它参与重新缓冲逻辑。

**实车验证：** 原车收音机、蓝牙电话、导航播报与 CarPlay 的焦点切换、压低音量、恢复和停止行为。现有针对特定车机的策略不能直接视为通用策略。

### 3.6 麦克风、Siri 和电话

**已确认：**

- `shared/src/main/java/com/shilapi/xcertplay/media/OpusEncoder.kt:22` 使用系统 Opus 编码器。
- `shared/src/main/java/com/shilapi/xcertplay/media/MicrophoneUplink.kt:57` 在协商为 Opus、但编码器不可用时无法启动上行。
- `shared/src/main/java/com/shilapi/xcertplay/airplay/AirPlayInfoPlist.kt:131` 宣告的无线输入能力包含 Opus。

**实施事项：**

- 探测实际音频编码能力，而不是仅按 Android 版本判断。
- 根据能力宣告可用格式，优先验证双方接受的 PCM 路径。
- 如果实际协商必须使用 Opus，再评估软件编码、ABI 打包及 CPU 开销。
- 验证麦克风对普通应用是否开放，是否被原车蓝牙电话独占。
- 分别验收 Siri、通话上行、下行和回声情况。

### 3.7 无线连接与能力降级

首期无线方案采用车机自带热点，复用 `ManualHotspotManager`。

**实施事项：**

- Android 6 隐藏当前 Wi-Fi Direct 入口。
- 由用户在车机系统设置中开启热点，应用保存并校验 SSID、密码。
- 验证蓝牙握手、热点网卡发现、IPv6 地址及 Bonjour/mDNS 发现。
- 支持 2.4 GHz 的可行路径，但按带宽和干扰情况降低视频负载。
- 热点不可用、密码错误、接口信息不可读时提供明确提示。

**边界说明：** Android 6 并非完全没有 Wi-Fi Direct，但本项目当前实现依赖较新的配置能力。为旧系统重新实现应作为独立任务。

**需要统一的底层回退：**

- `shared/src/main/java/com/shilapi/xcertplay/orchestration/CarPlayController.kt:1603` 在 Android 10 以下把 Wi-Fi Direct 回退至 `LocalOnlyHotspotManager`，而 Local-only hotspot 公共 API 从 API 26 才提供。
- `common/src/main/java/com/shilapi/xcertplay/AirPlayPersistence.kt:237` 已将旧模式迁移为手动热点。

当前不是所有旧系统连接都会触发错误回退，但需统一设置层和控制层的能力判断，避免历史配置或其他入口走入不支持的路径。

### 3.8 视频、界面与性能

**推荐初始配置：** H.264、30 fps、按屏幕与解码能力选择较低分辨率，关闭 HEVC、60 fps 及额外仪表视频流。

**实施事项：**

- 探测并实际验证 H.264 硬件解码，不仅依赖能力列表。
- 测试黑屏、绿屏、帧积压、延迟和长时间解码稳定性。
- 验证倒车抢占、屏幕旋转和休眠唤醒后的 Surface 与解码器恢复。
- 检查低分辨率屏幕的布局、字体、点击区域和设置可操作性。
- 根据测量结果优化内存分配、帧队列和后台负载，不预先重写整个 UI。

### 3.9 权限、文件和诊断

**实施事项：**

- 检查 Android 6 的运行时录音、定位等权限流程，以及旧版蓝牙权限声明。
- 对拒绝、永久拒绝、权限撤回和缺失系统服务给出可恢复提示。
- 保留旧系统基于文档选择器的诊断导出路径；核实目标 ROM 是否提供可用的 DocumentsUI。
- 如系统组件被裁剪，提供适当的应用私有目录保存或用户主动分享方案，不直接扩大全盘存储权限。
- 诊断信息记录系统/API/ABI、连接方式、编解码器及异常阶段，不记录热点密码、认证私钥或协议敏感数据。

### 3.10 方向盘、后台和 ACC 生命周期

项目已有媒体会话与开机接收器，`common/src/main/java/com/shilapi/xcertplay/BootReceiver.kt:9` 处理开机广播。

**实施事项：**

- 验证方向盘按钮是否以标准 Android 媒体键发送。
- 测试播放、暂停、上下曲和长按语音。
- 区分 ACC 熄火后的真正关机与休眠。
- 唤醒时检查 USB、网络、解码器、音频及旧连接状态，必要时重新建立会话。
- 检查厂商自启动和后台运行设置。
- 明确进程被杀后的行为，不以单一开机广播覆盖全部恢复场景。

### 3.11 HUD、仪表和电池联动

这些功能依赖 BYD 固件、服务和私有接口，不是降低 Android 最低版本后自然可用。

- 首期默认关闭。
- 确认车型和固件能力后，按功能单独启用与验收。
- 即使目标车机也是 BYD，也不能假设新 DiLink 接口适用于旧 Android 6 系统。

## 4. CarPlay 认证与测试包前提

`docs/BUILD.md:11` 说明普通源码构建不含运行时 accessory identity；`docs/BUILD.md:29` 说明可实际连接 iPhone 的 standalone 测试包构建要求。

实施前确认：

- 有可合法使用、且被目标 iPhone 接受的运行认证配置。
- 测试包按项目规定提供所需运行认证输入。
- 私钥、证书输入和 Android 签名密钥不提交到源码或本计划目录。
- 明确自签名测试包不能覆盖不同签名原安装包的情况，避免误卸载导致配置和配对记录丢失。

Android APK 签名证书与 CarPlay 配件认证不是同一件事。缺失认证材料导致的连接失败应单独归类，不能误判为 Android 6 问题。

项目使用实验性配件身份的限制仍然存在；Android 6 适配不消除其未来 iOS 接受情况及分发适用性的不确定性。

## 5. 分阶段执行计划

工作量为单人熟悉 Android、可持续获得目标车机和 iPhone 测试反馈时的粗估，不构成固定交付承诺。

### P0：可行性确认与基线（1～2 天）

- [ ] 获取车型、年份、车机型号、Android/API、系统与 MCU 版本。
- [ ] 确认普通 Android 或 AAOS，选择正确应用模块。
- [ ] 获取 CPU ABI、内存、屏幕分辨率和密度。
- [ ] 确认 APK 安装能力及可用调试方式。
- [ ] 检查 USB Host、数据口、蓝牙、热点和麦克风开放情况。
- [ ] 探测 H.264 解码及音频编码能力。
- [ ] 确认 iPhone 型号、iOS 版本和运行认证前提。
- [ ] 验证原项目构建基线，记录与适配无关的既有问题。

**验收门槛：** 明确硬件和测试包是否具备连接前提。若核心能力不开放或认证条件不满足，先解决前提或调整范围，不盲目进入全面改造。

### P1：API 23 基础兼容（2～4 天）

- [ ] 调整 `mobile`、`common`、`shared` 最低 SDK。
- [ ] 调整原生构建 API 目标并验证 ABI。
- [ ] 核查依赖最低 SDK，处理不兼容依赖。
- [ ] 配置并验证 Java 标准库兼容。
- [ ] 修复前台服务和通知兼容。
- [ ] 修复音频焦点与欠载统计兼容。
- [ ] 全面检查未保护的高版本 API，处理基础权限和诊断导出。
- [ ] 在 API 23 验证安装、冷启动、设置、权限请求及会话服务启动。

**验收门槛：** 基础操作不发生已知 API 不兼容崩溃。此阶段不等于 CarPlay 已可连接。

### P2：有线连接闭环（4～7 天）

- [ ] 实现 API 23 USB 接收、超时、取消和退出机制。
- [ ] 处理 USB 传输分块、短读短写和协议重组。
- [ ] 验证 USBMUX、NCM、VPN/TUN、配对和 TLS 链路。
- [ ] 验证 H.264 画面、触摸及音乐播放。
- [ ] 验证拔插、连接失败后重试和资源释放。
- [ ] 分别记录导航播报、Siri、电话的支持状态和问题。

**验收门槛：** 有线画面、触摸、音乐可用，拔插后可恢复；明确未完成能力，不将部分成功描述为完整兼容。

### P3：自带热点无线闭环（3～6 天）

- [ ] 统一无线能力判断及历史配置迁移。
- [ ] 隐藏或禁用 API 23 不支持的当前连接方式。
- [ ] 验证手动热点、蓝牙握手、网卡和地址发现。
- [ ] 验证无线投屏、音频及目标格式协商。
- [ ] 验证热点开关、错误密码、断线与重连。
- [ ] 对不支持的蓝牙或网络能力提供明确提示。

**验收门槛：** 目标车机自带热点可完成无线会话，异常能够定位并具备明确恢复路径。

### P4：车机稳定性与交付（3～5 天）

- [ ] 验证 ACC 休眠唤醒及真关机重启。
- [ ] 验证倒车界面抢占和 Surface 恢复。
- [ ] 验证前后台、原车音源与音频焦点切换。
- [ ] 验证标准媒体键和方向盘按键。
- [ ] 完成至少 2 小时连续会话及多轮恢复测试。
- [ ] 优化测量确认的性能、内存和延迟问题。
- [ ] 回归较新 Android 系统，避免破坏原功能。
- [ ] 形成目标设备兼容说明、推荐设置和已知限制。

**验收门槛：** 交付经过目标车机验证的版本，而非仅在模拟器中通过的 APK。

### 工作量汇总

| 阶段 | 粗估 |
| --- | --- |
| P0 | 1～2 天 |
| P1 | 2～4 天 |
| P2 | 4～7 天 |
| P3 | 3～6 天 |
| P4 | 3～5 天 |

完整的有线、自带热点无线和基本车机稳定性适配，可先按约 3～5 周预留。只需要有线核心功能时，可先完成 P0～P2，再决定后续投入。

遇到厂商驱动缺陷、硬件能力不开放、认证不可用或必须新增软件编码器等情况，需要重新估算。

## 6. 测试与验收矩阵

| 维度 | 必测场景 | 验收关注点 |
| --- | --- | --- |
| 构建与安装 | APK 元数据、ABI、签名、全新安装与可行的覆盖安装 | 最低 SDK 正确、原生库可加载、配置保留策略明确 |
| 基础运行 | API 23 冷启动、设置、语言、旋转、诊断导出 | 无高版本 API 崩溃，缺失组件有提示 |
| 权限 | 首次授权、拒绝、再次授权、撤回 | 可恢复，不进入无提示失败状态 |
| USB | 首次连接、反复插拔、超时、接口切换、失败后重试 | 不死锁、不遗留接收线程、不持续泄漏资源 |
| 无线 | 热点开关、错误密码、蓝牙断开、地址变化 | 阶段诊断明确，可重新连接 |
| 视频 | H.264、30 fps、不同分辨率、长时运行 | 无持续黑屏、绿屏或不可恢复积压 |
| 音频 | 音乐、导航、Siri、电话分别测试 | 上下行、焦点、音量和恢复状态清晰 |
| 车机切换 | 倒车、桌面、原车收音机、蓝牙电话 | Surface、音频和会话可恢复 |
| 生命周期 | ACC 休眠唤醒、开机、进程回收 | 不复用失效连接，恢复策略明确 |
| 按键 | 播放、暂停、上下曲、长按语音 | 标准键与厂商键支持范围明确 |
| 稳定性 | 至少 2 小时连续会话、多轮拔插和唤醒 | 无持续资源增长、卡死或必须重启车机才能恢复的问题 |
| 回归 | API 23 与较新 Android 版本 | 兼容分支不破坏原有功能 |

补充 API 23 自动化测试。现有明确指定系统版本的测试主要覆盖 API 28 及以上，不能作为 Android 6 已兼容的证明。

单元测试和模拟器不能替代 USB、车载蓝牙、麦克风、硬件解码和 ACC 场景的实车验收。

## 7. 风险与应对

| 风险 | 应对 |
| --- | --- |
| 依赖最低 SDK 高于 23 | 以实际解析结果为准，调整版本或隔离非核心依赖 |
| 旧 USB API 与驱动行为不同 | 独立兼容层、分块传输、明确线程退出机制、真机反复测试 |
| 车机没有可用的 Opus 编码器 | 能力宣告与实际能力一致，验证 PCM，必要时单独评估软件编码 |
| 蓝牙或麦克风不向普通应用开放 | P0 优先探测，必要时缩小范围，不承诺纯应用解决 |
| 热点接口与 IPv6 发现存在厂商差异 | 优先有线，热点无线单独验收并保留诊断 |
| ACC 与倒车行为不标准 | 以目标固件实测形成设备策略，避免全局硬编码 |
| 运行认证配置缺失或不被 iPhone 接受 | 前置确认，将认证故障与系统兼容故障分开 |
| 为旧系统适配破坏新系统 | 保留新系统路径，增加版本分支和回归测试 |
| HUD/仪表私有接口不兼容 | 首期关闭，按车型和固件单独立项 |

## 8. 待用户补充的信息

1. 车型、年份、车机品牌和型号。
2. “关于设备”页面照片，包含 Android 版本、系统版本和 MCU 版本。
3. 有线与无线的优先级；Siri、电话是否必须首期支持。
4. 是否允许安装 APK，是否能连接 ADB。
5. iPhone 型号和 iOS 版本。
6. 如已知：CPU ABI、内存、屏幕分辨率、USB 数据口、5 GHz 热点支持情况。

以上信息到位后，优先执行 P0，再将粗略计划收敛为目标车机的具体改造任务和验收标准。

## 9. 相关文档

- [构建说明](../BUILD.md)
- [兼容范围](../COMPATIBILITY.md)
- [安装与连接](../INSTALL.md)
- [连接设置](../CONNECTION_SETUP.md)
- [BYD 导航与仪表能力](../BYD_NAVIGATION.md)
- [已有验证记录](../VALIDATION.md)
