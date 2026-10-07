# Agent Note: H6 用户触发的 USB API 接口接管

Status: rejected — force claim/release在该ROM立即回绑内核驱动，实车仍配置2；采用授权fd上的USBDEVFS_DISCONNECT

## Problem

H6固件先把iPhone放在配置2并绑定音频/HID；USB配置6又被cdc_ncm接管。退出CarLife及拔插无法解决。外部root局部解绑验证配置切换、手机认证及首帧可行，用户确认看到CarPlay，但随后NCM短包padding解析失败，尚未稳定。

## Proposal

H6有线资源失败页提供手动确认按钮，只有本次controller得到接管能力，正常启动和自动重连保持非强制claim。使用UsbManager授权后的所选Apple UsbDevice，读取实际活动配置，只force claim音频/HID和CDC NCM控制/数据接口，按接口ID去重并释放临时claims，再SET_CONFIGURATION；切换后再次释放自动绑定的NCM。USBMUX、PTP及Apple厂商Ethernet不强制接管。正常断开后先关闭所有读泵、确认worker退出，再接管当前配置并切回原配置以恢复驱动。设备地址/VID/PID不匹配则不操作；失败明确提示拔插恢复。API23原生连接关闭串行幂等，阻止worker与调用者重复native close。

## Alternatives considered

- [本机root ADB方案](../../rejected/feature/2026-10-04-h6-manual-usb-release.md)：复用外部已验证的sysfs操作，但APK客户端握手及对照实验未能可靠连接，故不将此依赖固化进最终包。
- 默认每次连接自动force claim：能省掉手动操作，但改变原车USB共存，且手机插入并不表示授权抢占；因此限定H6失败页用户确认的一次连接。
- 全局卸载音频/HID/NCM驱动：能避开绑定，但影响其他USB设备；选择单个已授权设备的标准USB接口操作。

## Acceptance criteria

普通UID实车验证从配置2手动切到6、进入手机认证和首帧；正常断开恢复2及音频/HID绑定；默认失败不授权接管，其他profile不显示入口；回归、lint、API23、ARM32和standalone门禁通过。持续音视频、NCM解析错误及拔插等后续验收独立记录。

## Risks

临时中断所选iPhone的原车USB音频与控制；只在驻车时操作。活动配置读取失败拒绝接管；原设备已拔出不操作替代设备。进程强杀不能跑恢复，需拔插重枚举。普通返回主页保留后台会话，不等于断开；需要主页的断开按钮。旧固件native堆崩溃可能有其他原因，关闭竞态修正不宣称已排除全部原生问题。

## Existing note audit

H6兼容与同步笔记部分重叠，默认不驱逐不变，明确用户授权的单次路径由本篇承接并互链。root本机ADB提案转rejected并保留其验证及失败理由。安全包停用/安装策略与本能力无关，没有吸收或归档。

后续方案见[授权fd直接解绑](../../implemented/feature/2026-10-04-h6-usb-fd-disconnect.md)。
