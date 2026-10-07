# Agent Note: H6 授权 USB fd 直接解绑

Status: implemented

## Problem

外部sysfs定向解绑已让手机接受认证并显示CarPlay；普通USB force claim/release在H6立即回绑，仍无法切配置6。本机ADB握手也不可靠，需要应用普通UID完成用户触发的接口释放。

## Decision

保留H6失败页单次手动确认能力。UsbManager确认所选Apple设备授权后打开fd，读取实际配置，仅对音频/HID及CDC NCM接口执行USBDEVFS_IOCTL/USBDEVFS_DISCONNECT；不claim/release临时接口，避免Android的回绑副作用。JNI只接受有效fd与0–255接口ID，EINTR重试，只有成功或无驱动ENODATA可继续，其他errno明确失败。切换配置前后各调用一次。正常断开等worker和读泵全部关闭再切回原配置，地址/VID/PID不符则不碰替代设备。进程强杀需拔插恢复。原生连接关闭保持串行幂等。

## Alternatives considered

- [标准force claim/release](../../rejected/feature/2026-10-04-h6-usb-api-interface-recovery.md)：无需JNI，但实际回绑导致配置切换仍失败，保留失败理由避免重试此路。
- [本机root ADB](../../rejected/feature/2026-10-04-h6-manual-usb-release.md)：sysfs外部验证有效，但APK握手失败，不固化该依赖。
- 全局卸载驱动：避免所有绑定，但影响其他设备和原车功能，限定授权设备接口。

## Testing

实车UID10025/code30：默认连接停在配置2；22:23:07手动确认后释放活动2与6的接口，22:23:09手机接受认证，22:23:16渲染首帧。22:23:30及后续多个会话清理恢复2；最终通过应用DISCONNECT动作结束后台会话，sysfs回读配置2及snd-usb-audio/usbhid绑定。非H6入口和单次授权门禁有回归。shared359/common156共515项，514通过/1项既有跳过；lint、API23、ARM32、普通/standalone构建、认证资产与v1/v2签名通过。详细包哈希及证据见[车测记录](../../../../docs/android_6/USB_RELEASE_TEST_2026-10-04.md)。

## Consequences

临时中断所选iPhone原车USB功能。native ioctl依赖该固件usbfs支持；拒绝时停止并提示恢复。手机拔出后不操作替代设备。正常返回主页保留会话，恢复使用主页断开按钮。关闭竞态修正不能证明排除全部native崩溃。

## Existing note audit

两个失败提案转rejected互链；H6兼容及同步笔记部分重叠，默认非强制占用保持，明确授权单次路径由本篇承接；安全服务和安装策略无关。

音频与线缆后续诊断见[边界与音频资源](../bug-fix/2026-10-04-h6-carplay-audio-boundary-diagnostics.md)。用户确认触碰/震动线缆与断线时间一致；换原厂线前保持严格解析，不宣告协议修复。
