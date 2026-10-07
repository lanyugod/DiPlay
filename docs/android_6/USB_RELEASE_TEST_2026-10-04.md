# H6 手动 USB 释放与音频车测（2026-10-04）

## 当前结果

用户确认接口释放后真实出现CarPlay画面；APK普通UID10025也完成局部接口解绑、配置2到6、手机接受MFi认证与首帧。用户触发能力已固化到code30，当前安装在车机。完整稳定性和车机音乐输出未通过。

用户反馈每次断线与触碰/震动数据线时间同步，固定线后连接明显更久；原厂线替换是下一轮首要对照。日志多次报NCM short-packet pad，不据此排除接触问题，也不直接放宽解析。22:32:07首帧到22:36:03断线约3分56秒，不是两小时稳定性验收。今天无法换线，按用户要求结束并保存。

## 安装包

两包都是com.shihab.diplay.hudtest / 0.2.10-h6-hud-test，minSdk23 / target37，含ARM32，v1/v2签名通过，认证资产与显式私有输入逐字节一致。

| 包 | 状态 | 大小 / SHA256 |
| --- | --- | --- |
| `.private/h6-usb-release/2026-10-04/DiPlay-0.2.10-h6-usb-release-code30.apk` | 已安装，手动释放实车通过 | 19293203 bytes / `9dc1a3e6dbd577ac17934efe4ea60ece2e33076e14d65d163dce40915002f06e` |
| `.private/h6-usb-release/2026-10-04/DiPlay-0.2.10-h6-audio-diagnostic-code31.apk` | 当前源码构建；已回归，尚未安装/实车验证 | 18494315 bytes / `c30ed807cac279eb56e2b696c599e5d07c025f23667d718795ff69983be40c3f` |

不要使用此前force claim/release或本机ADB试验构建；这些方案已被否定。code29旧包仍作为历史快照保存。

## 如何触发与恢复

驻车退出原车投屏并解锁手机，打开CarPlay。若出现USB配置/接口不可用，点“释放iPhone接口并重试”，确认本次接管。正常启动、普通重试和自动重连不继承此授权。只操作已获USB授权的Apple设备音频/HID及CDC NCM接口，不动其他USB设备；APK无需root或本机ADB。

返回DiPlay保留后台会话；主页“断开连接”或通知Disconnect才结束会话。worker/旧读泵退出后恢复原配置。最终调用应用同一DISCONNECT动作，回读配置2，snd-usb-audio两接口和usbhid已重新绑定。强杀进程无法保证恢复，需要拔插重枚举。原设备已拔出时不操作替代设备。

## 关键证据

- 基线05ac:12a8、配置2、snd-usb-audio/usbhid；CarLife未运行。退出界面和拔插仍受驱动占用。
- 外部root定向sysfs解绑先证明可行；APK本机ADB握手不可靠。Android force claim/release实测立即回绑，仍配置2。
- 最终JNI用UsbManager授权fd执行USBDEVFS_IOCTL/USBDEVFS_DISCONNECT，避免临时claim/release回绑。
- code30 22:23:07释放active2及6；22:23:09 authentication accepted；22:23:16 Video first frame rendered；22:23:30清理恢复configuration2。后续多次会话亦记录恢复。
- 22:43:41正常DISCONNECT后最终sysfs配置2和音频/HID绑定；未强杀原车服务、未全局卸载驱动。
- 早期外部实验出现一次native SIGSEGV，已补旧USB连接串行幂等close及并发回归；不宣称排除所有native问题。

## 音频尚未完成

用户在CarPlay播放音乐时声音从手机响起，车机无声，进度条不能拖动；手机控制中心没有DiPlay/CarPlay输出选项。日志声明audioFormats/audioLatencies，但实际SETUP仅type110画面流，未见媒体音频stream或主体AudioTrack，系统焦点栈为空。故当前证据指向音频流/输出路由未建立，不能用增大车机音量作为修复，也不能据此认定具体格式或资源所有权错误。

code31仅增加有界数字资源诊断（resourceID/owner/entity/borrower/transferType）和NCM边界最多4字节标识；不记录完整协议/音频内容，不启用麦克风，不改变严格解析。换原厂线后安装它，记录播放时资源状态和SETUP，再决定音频协商修正。进度条问题需在稳定连接下独立验证。

## 保留的设备状态

百度com.baidu.privacy包DISABLED_USER=3、SecurityService在disabled-components中，已落盘并在前序重启后保持。最终两安全/隐私服务均(nothing)；原APK保留。主体ACTIVATE_VPN allow保留，当前安装code30。测试结束恢复iPhone配置2，未回滚本轮用户要求保留的百度停用及VPN授权。

## 验证与保存位置

shared359/common156，共515项，514通过、1项既有macOS测试跳过，0失败/错误。普通/standalone构建、lint、跨模块NewApi、同步清单、公有树、签名及认证输入检查通过；笔记目录/格式与diff检查通过。编译验证不等于完整会话验收。

日志、sysfs状态、屏幕截图、native tombstone及构建日志保存在本机Git忽略目录 `/Users/lanyu/IdeaProjects/DiPlay-main/.private/h6-host-test/2026-10-04/`。重点文件：`diplay-usb-native-app.log`、`diplay-audio-current.log`、`diplay-audio-live.log`、`audio-baseline.txt`、`audio-flinger.txt`、`audio-policy.txt`、`final-device-state.txt`、`final-package-restrictions.xml`及`final-installed-package.txt`。原始日志含设备/手机信息，不提交Git。

## 下次换线后的顺序

1. 换原厂数据线，固定车机口和手机口，先不触碰观察连接稳定性；单独记录任何拔出/震动时刻。
2. 先用车机现有code30手动释放进入CarPlay；若稳定，覆盖安装code31诊断包并保留设置。
3. 播放音乐，核对手机是否出现CarPlay输出，日志是否建立音频stream和AudioTrack；记录资源所有权数值。
4. 稳定连接下测触摸/进度控制、音乐/导航混音；最终断开确认恢复配置2。
5. 上述通过后再做原车音源共存、倒车/ACC及两小时稳定性；不提前宣告通过。
