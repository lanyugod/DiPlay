# Agent Note: H6 手动释放 iPhone USB 接口

Status: rejected — 电脑sysfs验证有效，但APK本机ADB握手实测失败；采用Android USB API手动接管

## Problem

2026-10-04主体选择配置6但实际配置2；iPhone音频/HID接口分别由snd-usb-audio/usbhid绑定。退出CarLife并拔插仍失败。局部root解绑后切换到6，随后cdc_ncm绑定又阻挡NCM claim；释放它后冷启动完成手机协议交换并收到137个视频包，之后NCM短包padding解析失败。接口释放有效不等于稳定CarPlay验收。

## Proposal

H6资源失败界面增加用户手动确认的“释放iPhone接口并重试”。仅本次controller获得授权，正常启动和自动重连没有释放能力。复用本机LocalAdb，以exec通道运行固定、受限的sysfs操作；要求已获授权的root adbd，不自动提权或开启ADB。按UsbDevice的bus/dev、Apple VID及PID精确匹配，并只允许snd-usb-audio/usbhid/cdc_ncm。USB配置切换前及切换后分别释放；结束后先关读泵、确认executor退出，再恢复原配置/驱动。找不到原设备时不碰新插入设备。失败显示具体原因与手动恢复提示。旧native连接关闭须幂等，避免waiter与调用者并发double free。

## Alternatives considered

- 普通UsbManager claimInterface(force=true)：无需root，但本轮验证的是sysfs解绑，主动配置的音频/HID与目标NCM分别有生命周期；尚未验证该ROM的force/rebind语义，因此不把未测试路线作为已验证修复。
- 全局卸载驱动或永久改变默认配置：可避免每次绑定，但会影响其他USB设备和原车功能；选择单设备单次释放。
- 只退出CarLife并拔插：无需接口接管，但两次实测仍绑定配置2，不足以解决此固件问题。

## Acceptance criteria

默认路径不执行ADB释放；H6手动确认才启用；非Apple/错误bus-dev/非root/未知驱动均拒绝；部分失败回绑；正常退出恢复原配置；API23/ARM32及认证门禁保持；编译standalone并在普通应用UID验证按钮路径。NCM解析错误和完整音视频稳定性单独记录，不能据接口成功宣告通过。

## Risks

释放会暂时中断所选iPhone的原车USB音频及控制。需要已授权root adbd，普通ADB不能执行。设备拔出后旧目标不恢复到新设备；进程被强杀无法跑恢复回调，需拔插重枚举。试验暴露一次native堆崩溃和NCM流解析失败，后者不在接口释放成功判据内。

## Existing note audit

H6兼容笔记与同步笔记部分重叠：保留默认不驱逐与普通应用边界，手动授权路径由本篇部分接管并互链。临时安全服务笔记与会话安装策略无关，不改变。没有完全吸收或过时提案。

后续普通USB API方案见[手动接口接管](../../rejected/feature/2026-10-04-h6-usb-api-interface-recovery.md)。此记录保存已验证的sysfs机制及放弃本机ADB依赖的理由。
