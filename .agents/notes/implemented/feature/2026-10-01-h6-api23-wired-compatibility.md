# Agent Note: H6 API 23 有线一期兼容边界

Status: implemented

## Problem

目标 IHU01 / j6headunit 使用 Android 6.0.1 和 ARM32。只降低 minSdk 会在 USB 26、焦点 26、欠载计数 24、音轨属性 29 和 Java Base64 26 路径失败；厂商驱动与普通应用权限仍缺实测证据。用户授权先开发，翌日补 ADB 与应用运行验证。

## Decision

mobile/common/shared 的最低平台与 NDK APP_PLATFORM 使用 23，automotive 保持 28。保持现有 ABI 和 targetSdk。H6 profile 仅匹配 API23 + IHU01 + j6headunit 或用户显式选择；有效设置为有线、AVC30、单屏、无上行/定位/BYD，原用户偏好保持存储且诊断记录 requested/effective。Controller 校验同一边界。

API23–25 USB 每个连接只有一个旧 requestWait 等待线程和持续 16KiB queue；应用超时不取消，完成队列上限256KiB。关闭先终止消费、取消并关闭连接，1秒有界等待；只有 waiter 退出后释放 request，驱动未退出则阻止继续创建读泵。USBMUX 分段写共享整条消息截止时间，正短写推进；NCM 保持完整 NTB 写和有界流重组，实时NTB采用严格长度、NDP表与datagram校验，离线宽松parser仍保留，运行阶段失败窗口3秒。保留现代读取后端和 CDC NCM 描述符规则，claim 不强制驱逐原车驱动。

标准库使用定点 Base64/UTC Calendar 封装；媒体创建与关闭受同一锁保护。旧音轨保存实际最终 attributes，API24+读取欠载计数，API23用播放头、实际写入和40ms窗口估计；进度按 track generation 归属，不能将归零当回绕。H6焦点由 MediaKeys 持有，失焦本地静音并在永久失焦发送PAUSE，GAIN不向手机发送PLAY；导航不随媒体静音，PCM继续有界消费。MediaKeys回调按sink token隔离，来自sink的媒体状态先post到主线程再取焦点锁，避免sink→focus与focus→sink锁序形成死锁。

诊断缺文档选择器时写私有 files/diagnostic-exports，仅此目录经FileProvider供用户主动分享。无分享目标时文件保留。Surface决定协商尺寸，不硬编码扣除120px，保留等比例分辨率缩放。VPN授权用VpnConsentRequest区分Ready/Requested/Unavailable；缺失系统授权窗口或运行时拒绝时清理awaitingVpnConsent，保持vpnReady=false并显示固件限制，不伪造成功或自动提权。

## Alternatives considered

- 只降 minSdk：改动最小，但不能避免已确定的新 API 调用，因此拒绝作为兼容方案。
- 旧 USB 同步 bulk 读或每次超时取消请求：消费者超时实现简单，但现有 NCM 已记录 JNI/GC 停顿以及取消间隙丢流风险，因此使用持续请求和独占等待线程。
- 全量 desugar_jdk_libs：可统一 Java 标准库，然而当前没有更广泛依赖覆盖证据；本轮保留定点实现和与 Java 原输出比对的测试，若第三方 JAR/DEX 或真机出现新标准库缺口再重访。

## Consequences

安装门槛的后续分析见[会话安装路线待评审](../../proposed/process/2026-10-03-h6-install-session-policy.md)，保留这里的应用兼容与原车共存约束；已执行会话安装实验，commit后被策略自动删除；永久放行路线仍待评估。设备所有者随后明确授权驻车临时停用、重启和恢复，新增窗口实验见[临时测试窗口](../process/2026-10-03-h6-temporary-security-probe.md)；这不改变正式普通应用对强制USB驱逐和自动提权的边界。

兼容路径有独立测试和明确流完整性边界，较新系统保持现有策略；代价是维护两种 USB/焦点后端以及旧音轨欠载估算。256KiB队列、1秒关闭、40ms缺包和3秒NCM失败窗都是待实测调参的设计值，不是性能保证。

临时窗口的普通UID USB open、主动切换后的CDC NCM描述符、TI720p样本循环10分钟、短测试音和一次探针桌面恢复通过；外部临时授权下TUN可建立/释放。实际USB配置仍受音频/HID占用，queue未进入，普通VPN窗口缺失。认证材料缺失单独阻塞完整会话；source-only APK不用于CarPlay成功或失败结论。原车应用共存，不进行系统提权或kill原车服务。

官方0.2.10选择性移植及后续同步门禁见[同步契约](../process/2026-10-03-upstream-0-2-10-h6-sync.md)。它部分重叠并维护本篇API23运行时边界；探针配置/接口不可用转为可识别资源失败，主体停止自动重连并提供手动重试。

## Testing

运行 shared/common 单元测试、mobile lint/assemble，检查APK最低版本及ARM32 ELF。用例覆盖旧USB迟到完成、关闭竞争、坏完成/queue失败、溢出/退出超时、USBMUX边界及部分写/总截止时间、Base64/PEM与Java字节一致、播放头归零与回绕、1160×720→928×576等比例缩放。测试包括API23真实执行的模拟音轨/通知/私有导出路径及NCM跨16KiB重组；初步实施与VPN回归364项通过（shared284/common80），包含API23 VPN已有授权、待授权、缺失窗口及拒绝准备场景。0.2.10选择性同步后为501项、500通过/1项跳过，详情见同步契约。独立tools/android6-probe复用正式旧读泵，提供用户明确触发的USB、720p样本解码、短测试音和无路由VPN测试。实车门槛和测试结果见 docs/android_6/IMPLEMENTATION_STATUS.md。

## Existing note audit

仓库没有现存 .agents/notes 决策；未找到同主题的 proposed/implemented/rejected 记录。docs/android_6/DETAILED_DESIGN.md 是实施依据，保持设计与实际结果分别记录。
