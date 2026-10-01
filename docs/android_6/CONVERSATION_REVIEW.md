# 原会话审计意见复核与后续信息查询

- 日期：2026-10-01。
- 来源会话：**判断DiPlay安装兼容性**，ID `6abcd4f7-e0f8-83e9-80c8-3dc35c1b702c`。
- 读取方式：通过 `read_thread` 读取工具可返回的 5 轮记录，以及用户附件 `粘贴的文本 (1).txt` 和原始 `README.md`。返回无后续游标。
- 本次没有连接 ADB；用户当前离开调试环境，以下判断依据已有记录。

这次已读到会话正文，不再仅依赖之前保存的审计摘要。会话助手的建议与推测仍需要独立核对；被引用但没有原始内容的外部文献或下载交接文件，不视为已读取。两份用户附件原文已保存在本地 `evidence/2026-10-01-conversation-import-local/`，有导入哈希，不随方案分发。

## 1. 新增信息及其证据强度

| 信息 | 原始证据 | 本轮结论 |
| --- | --- | --- |
| 物理屏 | 用户附件 DisplayDeviceInfo：1280×720，约60Hz，density160 | 已有历史输出，可作为面板基线；面板60Hz不代表CarPlay必须60fps |
| 应用区域 | 同附件 mOverrideDisplayInfo：`app 1160 x 720, real 1280 x 720` | 必须纳入显示设计；不是固定保证所有Activity都得到1160×720 |
| 尺寸差 | app宽少120px | 系统保留区域线索；不能只据此断定在左侧、永久占用或应用可隐藏 |
| SoC | 用户消息：soc0 machine=DRA752、family=DRA7、revision=ES2.0；DT model=TI DRA742、compatible含ti,dra742/dra74/dra7 | 支持TI DRA7/Jacinto6家族判断；保留标识差异，不强定唯一SKU；本轮未重采 |
| CPU | 用户消息possible/present/online均0-1；附件CPU part=0xc0f、两个processor | 历史记录支持两个Cortex-A15 CPU；与本地采集一致 |
| media.codec服务 | 用户消息：Can't find service: media.codec | 历史ROM没有该服务输出；不能据此判断没有codec，不反复要求同一查询 |
| TI AVC | 历史助手建议检查父节点；本地采集XML已找到OMX.TI.DUCATI1.VIDEO.DECODER下的video/avc | 配置线索已补齐；实际configure/start/Surface解码仍需测试 |
| Opus | 用户消息与本地XML均找到decoder，未发现encoder声明 | 一期不因此停止；语音延期，后续先PCM协商，不预先引入libopus |
| 历史未发现iPhone | 用户随后明确“我一直没有插入苹果设备” | 该快照不能证明枚举失败，也不支持据此推断必须逆向USB MUX |
| 当前已有iPhone枚举 | 本地已批准采集05ac:12a8，内核与USB Host框架均可见；用户确认当时CarLife未运行 | 枚举门已通过；应用授权、重枚举、claim、USBMUX/NCM仍未通过 |
| Wi-Fi Direct | feature存在；历史display快照mWifiP2pEnabled=false/mWfdEnabled=false | feature是声明，false是当时状态；都不能证明当前DiPlay的API23无线链路可用 |

`wm size` 没有 Override 行和 `dumpsys display` 存在 mOverrideDisplayInfo 不矛盾：前者不是所有窗口逻辑应用区域的完整报告。前版设计把1280×720作为先测的协商尺寸不够精确，已修正为以实际Surface为准。

## 2. 审计意见的采纳和保留

| 原意见 | 本轮处理 |
| --- | --- |
| 有线优先、先USB Probe、别重写UI | 采纳；与已经对齐的一期范围一致 |
| 旧USB请求≤16KiB、上层重组 | 采纳；同时区分USBMUX流写与完整NCM NTB写 |
| 寻找Configuration6 | 修正为按描述符；本机当前只有1～4，不将编号作为成功条件 |
| read超时、取消与退出要重构 | 采纳，设计已有单一waiter、持续挂起请求和有界关闭 |
| 先coreLibraryDesugaring | 保留为实际备选。当前详细设计选择定点兼容，不因会话建议自动切换路线；若更广泛库依赖或运行验证证明需要，再核验并锁定desugar库 |
| native优先级可下降/legacy包裁剪 | 不阻塞独立探针，但正式APK仍先按API23重编；没有加载证据不直接删.so或创建功能裁剪flavor |
| 独立legacy Manifest | 保留为排障备选；未知属性不是已证明安装失败原因，先用G0探针与真正API23 APK核验 |
| AudioTrack进度估算、回绕/reset | 采纳；timestamp可作为后续辅助，但不能把厂商timestamp默认视为可靠；播放头回退必须可用 |
| PCM麦克风先行 | 对二期采纳；本地用户已经决定Siri/通话延期，因此一期关闭输入能力，不自行恢复为PCM microphone首期要求 |
| H6初始1160×720 | 采纳实际Surface原则，不硬编码1160也不硬编码1280；源码已有基础路径，应保护和补测 |
| ARMv7-only包 | ARM32必须支持；一期设计保留已有多ABI，避免无依据影响其他设备，后续可单独做瘦包 |
| 看到TI XML就判720p硬解通过 | 收窄为强配置线索；不能替代真实解码或长时测试 |
| 软件Opus性能不是问题/内存一定够 | 不采纳保证性表述；现有数据不足以替代CPU/PSS/队列测量 |
| ADB查询都是只读 | 原文包括logcat -c、dmesg -c，实际会清日志；不执行、不收入后续只读清单 |
| CarLife包权限能说明内部USB实现 | 权限/路径只能提供线索；不推断具体API，也不自动进入APK逆向或厂商系统方案 |

原会话中的操作要求是历史建议，不自动构成本地执行授权。当前用户确认的普通APK、原车共存、有线一期、语音延期和无线二期边界保持。

## 3. 显示设计必须保留的现有代码路径

当前源码已经具备：

```text
CarPlayHostActivity.textureListener（实际Surface尺寸）
  → scheduleDisplaySize / applyDisplaySize（去抖、尺寸变化）
  → startCarPlay / createAirPlayConfig（以传入尺寸建立baseDisplay）
  → CarPlayDisplayScale.apply（协商分辨率缩放、偶数对齐）
  → CarPlayTouchMapper.contacts（按View局部坐标归一化）
```

因此任务是验证和保护现有行为，不是重写显示系统。

- 首次协商等待非零稳定Surface；历史1160×720是测试基线，真正DiPlay运行时尺寸优先。
- 若实测Surface是1160×720，1.0x为1160×720，0.8x通过现有缩放函数得到928×576；低负载测试保留宽高比，不能直接套用960×540。
- 1280×720可用作独立decoder样本；正式协商仅在实际Surface允许时选用。两个目标不同。
- 不把120px一律写成safeArea左边距，也不在MotionEvent.getX后再减120。触摸已是View局部坐标，重复修正会制造偏移。
- 如果之后新增letterbox，触摸须按视频viewport重新归一化并拒绝黑边事件；不能沿用整个View范围而不修正。
- 稳定尺寸变化复用现有restartCarPlay/debounce，避免每帧layout触发重连；倒车/系统栏变化后分别验证。
- 诊断同时记录physical/app/window/Surface/negotiated尺寸以及缩放和Insets来源；不能只输出“屏幕720p”。

相关代码位置、测试及验收已纳入 [详细设计](DETAILED_DESIGN.md) 的C1/V2任务。

## 4. 后续关键查询与执行顺序

后续只读命令见 [下一次检查清单](NEXT_CHECKS.md)。当前不需要为了设计再次查询精确SoC型号、完整包清单、全量日志或重复已完成的codec XML。

1. 返回ADB环境后，确认固定目标/API/型号；复核display逻辑应用区域，并保存iPhone当前USB框架快照。
2. 准备独立普通应用探针供审阅；安装、USB授权、vendor request、claim和VPN授权分别属于运行测试，不能由只读ADB替代。
3. 先完成USB授权/open/重枚举/实际NCM描述符及驱动关闭门槛，再全面移植USB数据路径。
4. 真机解码、普通应用播放及VPN establish；已缺失的运行认证并行准备。
5. 满足认证与数据门槛后验收一期完整会话。没有认证时保留平台探针结论，不承诺手机已能连接。

新增信息改变了显示参数和证据完整性，没有改变一期功能边界，也没有消除普通应用USB/VPN与认证风险。
