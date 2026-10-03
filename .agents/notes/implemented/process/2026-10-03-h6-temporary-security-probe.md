# Agent Note: H6 临时安全组件停用与探针窗口

Status: implemented

## Problem

签名策略阻止独立探针留存，平台能力尚未实测。设备所有者明确授权驻车时临时停止安全服务、安装/启动/卸载探针并恢复服务，以补齐 DiPlay API23 适配。

## Decision

优先单组件停用；实测system_server继续绑定，改为所有者授权的整包临时停用与重启，不修改系统APK内容、公钥资产或原车应用。先用 app_process 通过 IPackageManager 验证原始组件 DEFAULT=0 的读取与恢复；该工具已实测 before=0/after=0。保存两服务绑定和组件配置基线。停用后核验服务实际退出，测试窗口结束先卸载独立探针，再恢复 DEFAULT 并检查 BdPrivacyService、SecurityService 与 system_server 绑定。若停止或恢复不可证实，终止测试并执行恢复。

包管理器状态须等待异步落盘并回读package-restrictions.xml，再执行重启；立即重启的首次停用未保留，不能只看setter返回值。整包与组件均可通过IPackageManager精确恢复DEFAULT=0。用户按教程删除原包后，使用此前备份恢复原APK且核验哈希，重启验证两服务能加载；原oat无备份，不宣称目录字节级恢复。

用户另行明确授权仅探针ACTIVATE_VPN临时allow，原ignore基线已读取；无路由TUN实测建立并释放，授权已恢复ignore，tun0不存在。普通APK授权窗口缺失仍为正式App阻塞。

平台测试仅证明普通 UID 下 USB、MediaCodec、音频和 VPN 的能力；手机认证及完整协议会话仍需独立验收。永久放行调查以离线分析为主。

## Alternatives considered

- 厂商签名可以保持原始策略，但当前没有可用签名输入，无法完成本次平台验证。
- 仅会话安装已实测被自动删除，不能形成测试窗口。
- 停用整个包影响隐私和权限服务；单组件实测仍被重新绑定，故测试窗口采用整包临时停用，结束恢复并重启核验。
- pm enable 可启动组件，但状态值1不同于原始0，因此使用系统接口精确恢复，不回写包数据库。

## Consequences

已完成窗口内安装/冷启动、普通UID USB open与NCM枚举、10分钟TI720p样本循环、用户可听测试音、一次桌面Surface恢复。配置切换失败，queue未启动；普通VPN授权缺失，额外批准授权下无路由TUN建立/释放并恢复ignore。主体缺认证，完整会话未开始。

两测试包卸载均Success。恢复DEFAULT配置落盘后重启，最终两服务PID1321、system_server PID532 hasBound=true；原APK哈希一致，/system只读，无TUN/测试包残留，用户确认原车界面正常。原oat无备份，电话/倒车等功能未逐项验收。整包停用暂停全局隐私/安全策略，只作为驻车测试窗口，不是永久交付方案。详见[实测报告](../../../../docs/android_6/SECURITY_PROBE_WINDOW_2026-10-03.md)。

## Existing note audit

[既有安装路线](../../proposed/process/2026-10-03-h6-install-session-policy.md)部分重叠，保留其已失败的会话路线与公钥调查。本记录承接用户新增授权，替代其尚未选择停用的操作决定；[基础适配](../../implemented/feature/2026-10-01-h6-api23-wired-compatibility.md)仍独立保留。
