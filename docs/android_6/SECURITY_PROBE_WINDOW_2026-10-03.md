# H6 临时安全服务测试与原 APK 恢复

设备所有者授权驻车测试，目标是完成 DiPlay Android6 适配；卸载为原消息“洗澡”的正确含义。

## 已验证的恢复事实

用户按网络教程 remount 后删除 `/system/priv-app/BdPrivacy_bak`，目录确实消失；两服务仍在 PID1314 中运行，删除不等于立即停止检查。本地此前只读拉取的原始 APK 为114694 bytes，SHA256 `3fbf47aa7f97e6497cf94613c853535baa8fcac14a41889039445d7a5c9bc232`。

已将该 APK 恢复到原路径，目录root:root/0755、APK root:root/0644；设备 SELinux Disabled，同级与恢复文件标签均unlabeled。回读 APK 哈希一致。原目录内 oat 文件没有备份，不宣称完整目录字节级恢复。实际进程使用的 `/data/dalvik-cache/arm/system@priv-app@BdPrivacy_bak@BdPrivacy.apk@classes.dex` 仍存在，319920 bytes，已备份。

原 APK 恢复后应用进程成功重建；经用户明确授权重启车机，API23系统 boot_completed=1，BdPrivacyService 与 SecurityService 在PID1330运行，system_server PID739 hasBound=true。这证明原包可在本轮重启加载，但不能代替全部原车功能验收。

## 临时停用实验

通过电脑编译的专用 ComponentState 工具，在app_process中反射 IPackageManager，仅对目标包/组件读取和修改enabled状态。原包与组件均为DEFAULT=0；恢复接口均实测0→0成功。工具与原包副本不提交仓库。

- 单组件0→2后，原绑定继续存在。重启应用进程后仍重新绑定，因此不能作为可靠测试窗口。
- 整包0→3后，am force-stop未停止原服务。
- 第一次设置整包3后立即重启，重启后状态为0，配置文件无目标包项；尚不能区分异步落盘未完成与固件重置，不判定持久停用可用。

第二次设置包3/组件2后等待包管理器异步落盘，并读回 package-restrictions.xml 确认目标项，再重启。重启后包状态3保持，dumpsys activity services com.baidu.privacy为(nothing)，临时窗口成立；不需要删除原APK。

## 已取得的平台证据

| 项目 | 结果 | 边界 |
| --- | --- | --- |
| G0窗口内安装/冷启动 | adb非流式安装Success，启动403ms，普通UID10025 | 恢复厂商安全策略后仍无公钥信任，不能永久留存 |
| G1a | iPhone open=true，主动切换后再次open=true | 系统返回已有permission=true，本轮未显示USB授权弹窗 |
| G1b | vendor request后重新枚举，配置5/6出现02/0d CDC NCM控制和0a数据接口 | 暴露描述符不等于配置已选中或协议数据已通过 |
| G1c | 探针请求USBMUX配置3失败，GET_CONFIGURATION未证实目标；queue未启动 | sysfs实际配置2，snd-usb-audio和usbhid占用；closeMs=0不能当waiter退出通过 |
| G2 | OMX.TI.DUCATI1.VIDEO.DECODER实际解码1280×720样本，画面可见 | 第一版日志挤出按钮已修复；22:52:24.447至23:02:25.710共601.263秒、117次样本解码，首帧119–220ms/中位152ms；5秒样本循环不是持续CarPlay流 |
| G3 | 48kHz/96000采样写入，用户明确反馈听到测试音 | 原车音源抢焦点/导航混音未验收 |
| G4普通应用 | VpnService.prepare返回缺失的com.android.vpndialogs/.ConfirmDialog，启动失败 | 当前普通APK授权路径阻塞，普通授权路径未建立TUN；随后另行批准的辅助测试见下文 |

## 追加 VPN、生命周期及主体验证

用户明确批准仅探针ACTIVATE_VPN临时授权。原始appops为ignore，临时设allow后，普通UID10025返回bind=true/establish=true，系统出现tun0、fd00:23::1/128，无应用添加的转发路由。点击释放后授权恢复ignore，/sys/class/net已无tun0。这说明VPN内核/框架存在，但不能写成普通授权窗口或正式App已获授权。

十分钟样本循环结束后启动DiPlay主页，冷启动765ms。H6 profile显示有线/H.26430/单屏/关闭麦克风及车辆联动。进入CarPlayHostActivity时source-only缺认证，返回主页并显示认证缺失提示；完整会话未开始，未实车触发主体的VPN错误处理路径，其回归由API23测试和探针实际错误共同验证。

随后探针短视频桌面切换，Surface generation5→销毁6→创建7/size8，generation8重新解码首帧172ms，截图画面可见；这里只验证一次探针桌面恢复，不等于主体/倒车/ACC验收。

## 代码补齐与电脑检查

探针改为独立按钮区、视频区与有界日志区，实车截图确认所有操作按钮保持可见。主体增加VpnConsentRequest：已授权才Ready，正常打开窗口为Requested，缺失Activity/运行时拒绝为Unavailable；错误清理等待状态、保持vpnReady=false并提示固件有线连接不可用。没有伪造授权或自动提权。

重新执行shared284 + common76项共360项通过；补上4项API23 VPN授权回归后common80项通过，合计364项。主体及探针lint/assemble通过，v1/v2签名验证通过。主体source-only APK为19221100 bytes、SHA256 7da30c6f0c0472ab32325c6bb992ff62bb69c191ca89ac20aa3104647bcca4f3；修复界面探针1560601 bytes、SHA256 b347f1a47842fc510b28a46c5ab83a42e3a9e903ca5412a84a6ceb1526aa321b。

## 本地证据

原始状态、回读APK、运行缓存和进程映射位于 `/private/tmp/diplay-h6-service-test`。原 APK 来源为 `/private/tmp/diplay-h6-policy-readonly`，不向仓库分发原厂二进制。

## 最终恢复核验

两测试包普通卸载均Success，重启后pm path均为空。安全包与组件已精确恢复DEFAULT=0，配置文件与原基线保持同样目标状态；等待落盘确认后重启。最终boot_completed=1，BdPrivacyService与SecurityService同PID1321，system_server PID532 hasBound=true；原APK回读SHA256一致，权限root:root/0644，/system为ro。无tun0。用户确认原车界面正常，电话/倒车/导航等完整功能未逐项验收。

最后一次重启后ADB的普通shell通道返回closed，但exec-out可用；用exec-out完成状态与服务核验，未把连接问题判为服务未恢复。长期恢复备份位于Git忽略的`.private/h6-recovery/2026-10-03/`，包含原APK、实际运行缓存和恢复核验记录。

## 完整适配仍缺的条件

- 安全服务恢复后debug公钥仍不受信任；已验证的整包停用+等待落盘+重启仅是临时测试窗口，不是服务开启情况下的永久白名单放行。原包删除不再需要，也未采用永久修改资产或Hook。
- VPN框架可用，但正常授权组件缺失。正式App需由设备所有者/厂商另行决定提供受支持授权入口或明确接受外部授权流程，App不会自动执行root。
- iPhone描述符含CDC NCM，但实际仍是配置2的音频/HID占用；本轮未强制驱逐内核驱动，queue与协议流完整性未验收。
- source-only没有合法运行认证输入。补齐后才可生成standalone车测包并验收完整画面/触摸/音乐/导航、拔插、倒车及两小时稳定性。
