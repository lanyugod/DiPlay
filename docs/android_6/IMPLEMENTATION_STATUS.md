# Android 6 初步实施与明日验证交接

日期：2026-10-01；基线 Git HEAD：`7f432d0f425c3d9cd50e26d9162f044d751e9653`。本轮修改尚未提交，未连接/安装/运行车机。

## 阻塞判断与紧急程度

**当前没有要求立即进入 ADB 环境的开发阻塞。** NEXT_CHECKS 的身份、显示及USB只读快照可明天补采；实际运行门槛必须保留为未验证，不能以本机测试替代。

| 优先级 | 明天要验证的门槛 | 失败意味着什么 |
| --- | --- | --- |
| P0 / 首批实测，高优先级但今天不紧急 | G0 普通 APK 安装/冷启动 | ROM 安装限制可能阻断普通 APK 路线 |
| P0 / 首批实测 | G1a USB 权限/open；G1b 切换后真正 USBMUX+CDC NCM | 无NCM可能阻断现有有线网络路径；配置编号不固定，不放宽描述符规则 |
| P0 / 首批实测 | G1c 旧 queue 在空读、拔线、cancel/close 后退出 | 超过1秒记录退出超时；未退出时阻止继续开读泵，不能用更多线程掩盖 |
| P0 / 首批实测 | G4 VPN consent、建立和释放 | 无法建立将阻断现有 NCM/VPN 架构 |
| P1 / 同次测试窗口 | G2 实际TI H.264 Surface解码、G3音频与焦点 | 需按实测调分辨率/缓冲/路由；声明codec不等于可解码 |
| 完整会话的关键外部阻塞 | G5 可用运行认证输入 | 当前source-only APK没有认证；单纯ADB查询不能补齐材料，不能据连接失败否定Android6适配 |
| P1 / 认证后 | G1d/G6协议数据、画面/触摸/音乐/导航及稳定性 | 一期交付必须依照详细设计验收，当前仍未验收 |
| P2 / 延后 | 无线、语音上行、ACC、方向盘 | 不阻断本轮有线初步开发 |

## 已实施范围

| 设计任务 | 落地情况 |
| --- | --- |
| B1/B2 | mobile/common/shared minSdk23、NDK平台23，automotive28及ABI集合保持；ARM32自有库重新构建 |
| R1–R4 | 全部Base64入口统一封装并与Java输出比对；UTC Calendar；媒体创建/关闭同锁，关闭后不再创建资源；补掉NewApi发现的AtomicLong、newKeySet、removeIf和TLS参数调用 |
| S1/S2/C1 | 旧系统startService、NotificationCompat、channel版本保护；H6精确识别/显式选择；有线/AVC30/单屏/无麦克风、定位与BYD；保存偏好保持，Controller再次校验；不申请录音即可进入启动前提 |
| U1/U2/U3 | 旧requestWait独占泵、16KiB持续queue、256KiB上限、消费超时不取消、退出超时阻止继续开泵；USBMUX整条消息锁/总截止时间/正短写；NCM跨块重组和有界缓存 |
| U4/U5 | NCM保持完整NTB，旧系统超大块拒绝、正短写终止，运行阶段连续失败3秒升级；严格校验实时NTB；保持描述符识别，切换失败需GET_CONFIGURATION证实；claim(false)，不强抢原车接口；权限拒绝停止轮询 |
| U6/L1 | 旧异步open及VPN accept/session回调检查有效代次，释放桥接失败仍关闭TUN；既有后台/Surface恢复流程保持，需实车证明 |
| A1/A2/A3 | API29 attributes getter保护及实际fallback attributes；新旧焦点封装；H6单一MediaKeys焦点，永久失焦静音+PAUSE，瞬时失焦静音/duck、GAIN不发送PLAY；音频回调按sink token隔离；API23以进度/40ms窗口估算欠载并标为estimated |
| V1/V2 | 记录运行时codec候选与实际选择；已有Surface驱动协商/恢复保持，增加physical/app/window诊断；不扣120px，1160×720按0.8x为928×576，触摸仍按本地View归一化 |
| D1 | 无DocumentsUI时私有文件+FileProvider，分享失败仍保留文件，不增加存储权限 |
| W1/M1 | 统一MANUAL回退及配置错误；一期不宣告输入；实际无线/PCM录音/Opus能力协商留二期 |
| P0.5/T1 | 独立探针复用正式旧USB泵，提供USB/视频/音频/VPN手动操作；本机单元测试及API检查完成 |

未执行：任何 connectedAndroidTest/模拟器/车机运行、APK安装、USB主动切换、真实USB协议回放或手机认证、2小时稳定性、倒车/ACC实车验证。USB线程目标1秒、欠包40ms、NCM失败3秒仍是待实测初值。第三方JAR内部全部标准库调用尚未获得API23真机覆盖，专项NewApi检查通过并不替代其实际运行。

## 构建及验证结果

- `:shared:testDebugUnitTest`：284项通过；`:common:testDebugUnitTest`：76项通过；合计360项，无失败。
- API23/26焦点listener申请与释放、API23/28/29 PCM实际创建/写入/启动、API23/26通知动作、API23私有导出FileProvider均执行；它们证明模拟Android API路径，不能证明厂商HAL/驱动。
- USB测试覆盖迟到完成、close/queue竞争、取消失败、意外完成、溢出及卡住waiter；USBMUX覆盖0/1/16384/16385/65536与短写/整条消息deadline；NCM覆盖60KiB跨chunk、粘包、512边界pad、坏datagram和队列上限。
- 标准 `:mobile:lintDebug` 通过；跨mobile/common/shared的NewApi专项检查通过；`:mobile:assembleDebug` 通过。
- 探针 `assembleDebug lintDebug` 通过，内置H.264 Constrained Baseline、1280×720/30fps、5秒循环样本；未据此声称TI已成功解码。
- AAPT确认两个APK minSdk23/targetSdk37；应用包含armeabi-v7a。ARM32两个自有SO及AndroidX graphics.path SO的ELF machine均ARM，动态依赖仅libc/libm/libdl；强制导入符号全部存在于NDK API23 stub。真实ROM加载仍待测。
- APK签名验证通过；debug证书SHA256：`bcc18556561523b8b1122b559136024aecbd1dca0cd8ca3461080647834824b5`。目标若已有同包需核对签名；不要卸载配对/配置来绕过覆盖失败。
- Agent Note目录/格式检查、`git diff --check`通过。

可复现命令（Mac终端；显式使用本机JDK25，不更改全局Java设置）：

```sh
export JAVA_HOME=/Users/lanyu/.gradle/jdks/eclipse_adoptium-25-aarch64-os_x.2/jdk-25.0.3+9/Contents/Home
export ANDROID_HOME=/Users/lanyu/Library/Android/sdk
./gradlew --no-configuration-cache :shared:testDebugUnitTest :common:testDebugUnitTest :mobile:lintDebug :mobile:assembleDebug
./gradlew --no-configuration-cache -I scripts/check-android6-newapi.gradle :mobile:lintDebug
./gradlew -p tools/android6-probe --no-configuration-cache assembleDebug lintDebug
```

## 审阅包

| 包 | 文件 / 用途 | SHA256 |
| --- | --- | --- |
| `com.shihab.diplay.hudtest` | `mobile/build/outputs/apk/debug/mobile-debug.apk`（20927026 bytes）；**source-only**，可用于基础UI/设置检查，没有运行认证 | `000fa90be156d7da29625079762be25ae00a081117f741e3325fed6a1d44ea03` |
| `com.shihab.diplay.android6probe` | `tools/android6-probe/build/outputs/apk/debug/DiPlayAndroid6Probe-debug.apk`（1585287 bytes）；独立平台门槛探针，不携带认证材料 | `c0ca772a916d2e22a27e40f9fe48b9e0798ed6f4bf1fe5f544c70e061ac563c0` |

探针源码、权限、按钮动作和构建方式见[探针审阅说明](../../tools/android6-probe/README.md)。安装/主动测试尚未获得执行授权，本轮不执行。明天先运行[NEXT_CHECKS](NEXT_CHECKS.md)只读快照，再在审阅并明确授权后进行探针测试；只读信息不能替代平台门槛实测。

`assembleStandaloneDebug` 的认证门禁保持不变；认证材料到位后用该任务构建正式车测包，不把source-only APK重命名成可验收包。
