# 官方版本同步与 H6 Android 6 兼容

本地版本为 **0.2.10-h6**（versionCode 35），选择性同步官方 **v0.2.10**，标签提交为 [`3e43e25c55921bdf5149f5f92851acf202ed353a`](https://github.com/shihabal3amri/DiPlay/commit/3e43e25c55921bdf5149f5f92851acf202ed353a)。官方不存在2.0.10标签。检查日期为2026-10-04；不纳入标签之后的main提交。

前次官方源码基线为885dffb。本轮逐项审阅到标签之间的97个提交，移植16个通用源码提交，其余处置见机器可读的[同步清单](upstream-sync.json)。这是所需功能的同步基线，不表示与官方全部功能或支持范围一致。官方最低Android 9，本分支mobile/common/shared及NDK保持API23、保留ARM32；automotive仍为API28。

## 纳入的功能

| 功能 | 官方提交/PR | 本地边界 |
| --- | --- | --- |
| 音频卡顿诊断、硬件解码失败后重试最小格式 | b946745 / #74、260a5a0 / #76 | 保留API23音轨属性、欠载估算、媒体创建/关闭锁；TI720p探针通过，不擅自降画质 |
| 拒绝旧Lockdown Host ID后重新配对 | 842b647 / #75 | 保留配对记录存储与Android6标准库封装 |
| 音频输出0–20及旧导航设置迁移 | 56d5651 / #88 | 通用设置同步；H6有效设置仍固定mobile音频路由 |
| 窗口尺寸变化保持会话、触摸比例映射和小窗口启动恢复 | 9d90d06、7eb4a3f / #89 | 属于通用画面恢复，独立于BYD车辆回调；在API23/29运行回归，倒车仍需实车验收 |
| 媒体标题、作者、专辑、进度和封面 | 18ce25e、f8f46b2 / #82 | 有界封面队列与过期会话隔离；保留H6单一音频焦点和sink token锁序 |
| USBMUX识别到控制回复后的四字节padding | 9f9bc1b、4e97b53 / #114 | 使用发布版修正，普通分片/合并帧不丢弃；保留旧USB持续读泵及整条消息写截止时间；3d0411d是等价修正 |
| USB启动不校验未用的手动热点设置 | 732b101 / #130 | 无线仍校验；H6有效传输保持有线 |
| AirPlay端口7000被占用后的回退与监听器清理 | 2cfd801、650e83b / #143 | 实际端口由Bonjour及有线/无线iAP2通知；不改变原车USB占用 |
| Wi-Fi Direct未知安全类型、忙碌重试和频段回退 | f1c8d3a / #121 | 仅维护现代设备路径；不会让H6启用Wi-Fi Direct |
| 连接、USB I/O、关闭和开机诊断；有界异步写入 | 9b12f0d、f805044 | 不记录音频/协议内容；导出保留API23私有文件回退，并带官方标签/SHA |

## 跳过的内容

BYD昼夜回调、CAN/CANFD电量、轮速/档位、仪表歌曲、独立转向叠层、仪表/浮动/启动器地图及样例启动器均不新增同步。停车视频依赖BYD档位与网络ADB，不纳入。H6一期关闭位置上报和麦克风，GPS上报、回声消除/语音诊断及新增语言暂不纳入。原有BYD代码保留，H6 profile继续禁止其车辆联动。

## 探针结果落到主体

- VPN授权窗口缺失时，`VpnConsentRequest`返回Unavailable，清理等待状态、保持`vpnReady=false`并提供重新检查。系统弹窗返回成功也再次prepare核验；主体获外部授权后可直接继续。探针的临时外部授权不移植为App自动提权，也不等于正式App授权。
- USB配置切换失败且GET_CONFIGURATION不能证明目标配置有效时，或USBMUX/NCM接口无法占用时，报告可识别的`ResourceUnavailable`。主体停止自动重连，提示退出原车投屏、拔插线缆并提供手动重试；默认不强抢内核音频/HID接口；H6用户明确确认的单次释放与恢复见[车测记录](android_6/USB_RELEASE_TEST_2026-10-04.md)。现有USB配置仍按USBMUX+CDC NCM描述符选择，不使用探针的“首个USBMUX配置”规则。探针请求配置3失败，主体对相同快照选6，不能直接外推主体配置5/6失败。
- TI720p样本循环、短测试音和一次探针Surface恢复只证明平台能力。正式持续视频、触摸、音乐/导航混音、拔插、倒车、两小时稳定性仍待认证车测包验收。应用改动不能解决固件USB所有权或补出缺失的系统VPN授权界面。

## 本轮电脑验证

2026-10-04：shared355/common154，共509项，508通过、1项既有macOS通配绑定跳过、无失败/错误。窗口、触摸和媒体metadata回归在API23/29运行；新增API23 VPN授权核验/重新检查及USB描述符选择回归。普通mobile lint/assemble、standalone构建与跨模块NewApi通过；清单、公有树和笔记校验通过。

standalone实车包已独立保存于`.private/h6-car-test/2026-10-04/DiPlay-0.2.10-h6-car-test.apk`，包名`com.shihab.diplay.hudtest`，versionName`0.2.10-h6-hud-test`/code29，minSdk23/target37，包含armeabi-v7a，v1/v2签名通过，19,274,221 bytes。SHA256：`65abca1e237616e3700adc5bbee36fe847119197876b6efee618f52cb9ffdb20`。显式运行输入来自校验过的官方公开0.2.10发布APK，源码不包含身份；本地签名校验6次通过，手机接受程度仍待测。详细来源、主体VPN授权及测试步骤见[实车交接](android_6/CAR_TEST_2026-10-04.md)。本轮未安装到车机或进行新的实车验证。

2026-10-09：本地code35增加H6主页/裁剪页左侧导航栏窗口回退，上游标签/SHA及97项处置不变。code33实车已建立音乐/导航音轨；code34在root临时提高UDP接收上限后，用户确认音乐/导航流畅。系统包级策略隐藏DiPlay及高德原车左栏，CarPlay保持1280×720且应用切换正常，高德左侧搜索按钮响应。527项回归中526通过、1项既有跳过，构建/lint/API23专项通过。UDP调优、USB驱动所有权及各版本验收见[root调试记录](android_6/DEBUG_2026-10-09.md)，重启后的音频调优及长期稳定性仍待处理。

## 下一次同步步骤

1. 用独立临时clone获取官方标签/分支，确认目标tag及SHA，比较清单的`upstream_commit`到新目标。`origin`仍指向本地fork，不直接用官方分支覆盖当前工作树。
2. 按通用功能和H6范围逐项评审。新增未用功能写明skipped理由；混合提交拆分移植。检查合并提交是否存在额外修正，并保留上游署名/提交链接。同步范围扩展时更新本文件和决策笔记。
3. 对涉及共享Controller、USB、媒体和服务的改动，以本地工作树为基础合并，保留已有未提交工作。检查API26 requestWait/queue、新音频焦点/通知、API24欠载计数、API29属性、Base64和Calendar封装以及代次隔离。
4. 更新`upstream-sync.json`、`UpstreamSyncInfo.kt`和mobile版本，增加新范围的提交审阅记录。不要删除旧审阅记录；`previous_upstream_commit`表示本次审阅范围起点，`upstream_commit`表示最新已审阅终点。
5. 运行下列检查；CI同样运行清单一致性和跨模块NewApi检查。测试成功后才推进同步基线，实车缺口继续单独保留。

```sh
python3 scripts/check_upstream_sync.py
python3 scripts/check_public_tree.py
./gradlew --no-configuration-cache :shared:testDebugUnitTest :common:testDebugUnitTest :mobile:lintDebug :mobile:assembleDebug
./gradlew --no-configuration-cache -I scripts/check-android6-newapi.gradle :mobile:lintDebug
```

有认证输入时使用既有`assembleStandaloneDebug`门禁；未设置显式输入的普通`assembleDebug`产出source-only包。不要用官方版本号、电脑回归或探针样本结果宣告完整H6 CarPlay已通过。

2026-10-04晚间：当前源码code31；车机保留已验证接口释放的code30。shared359/common156共515项，514通过/1项既有跳过，lint/普通与standalone/API23通过。code31仅补有限音频资源和NCM边界诊断，未安装实车；用户今天无法换线，稳定性和车机音频待下一轮验证，详见[车测记录](android_6/USB_RELEASE_TEST_2026-10-04.md)。

2026-10-08：以 `/Users/lanyu/IdeaProjects/DiPlay` 为主项目同步此前隔离实现：code32 的 NCM 可选短包填充及回归，code33 的 H6 PCM 静音输入候选及回归。上游标签/SHA和97项提交处置不变。真实麦克风保持关闭，静音输入候选未完成实车验收。迁移和本地主项目验证见[音频同步交接](android_6/AUDIO_2026-10-08.md)。
