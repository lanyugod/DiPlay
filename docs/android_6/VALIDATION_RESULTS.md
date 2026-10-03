# H6 Android 6：验证结果与后续模板

## 2026-10-03 实车探针进度

已按设备所有者驻车授权建立可恢复临时窗口（整包停用落盘后重启）；探针与主体基础包安装/冷启动成功，并完成卸载。普通UID USB open及主动切换后CDC NCM描述符通过；配置切换仍受当前USB音频/HID占用阻塞，queue未进入。TI 720p样本循环10分钟、短测试音可听、一次探针桌面Surface恢复通过。普通VPN授权窗口缺失；额外批准ACTIVATE_VPN临时授权后无路由TUN建立/释放成功，授权已撤销。主体补齐VPN缺失错误处理；source-only缺CarPlay认证，完整会话未开始。测试包均卸载，安全包/组件恢复DEFAULT，最终重启核验完成：两服务PID1321、system_server PID532 hasBound=true，包/组件DEFAULT=0、/system只读、无测试包和TUN残留。详见[临时窗口与适配实测](SECURITY_PROBE_WINDOW_2026-10-03.md)。下文保留2026-10-01历史采集与模板。

## 2026-10-01 实际采集结论

本节是本轮有效结果；后面的空模板保留供后续补采，不代表本次尚未采集。配套 [详细设计](DETAILED_DESIGN.md) 与 [原分步清单](ADB_VALIDATION.md)。

### 授权和来源

用户先批准系统/ABI/内存/屏幕/USB/codec 只读采集，再批准音频/TUN/应用候选只读采集。固定目标为 `192.168.43.1:5555`，两批设备查询均完成，没有执行 root、安装、重启、权限授予、日志清理、USB 切换或原车应用启停。

首次访问分享页面失败，当时经用户确认采用保存的摘要。用户后续提供可访问的“判断DiPlay安装兼容性”会话；现已通过read_thread复核5轮消息及两份用户附件，详见[会话复核](CONVERSATION_REVIEW.md)。此次追加复核不连接ADB；用户当前离开调试环境，下一轮必要命令见[NEXT_CHECKS.md](NEXT_CHECKS.md)。

### 已确认事实

| 项目 | 本次结果 | 来源与边界 |
| --- | --- | --- |
| 车型/手机 | 2021 第二代哈弗 H6 冠军版；iPhone 13 Pro / iOS 16.1 | 用户确认 |
| 连接拓扑 | 网络 ADB；iPhone 插原车 CarLife 数据口；CarLife 当前未运行 | 用户确认；未主动改变车机应用状态 |
| Android/API | 6.0.1 / 23 | batch1 01_properties |
| 设备/厂商 | IHU01 / j6headunit；GuangDong_YF_Technology_Co_Ltd | batch1 01_properties |
| OS构建 | M4B30Z dev-keys；full_j6headunit-user 6.0.1 M4B30Z 228 dev-keys | 不是MCU版本；未采集完整fingerprint |
| ABI | armeabi-v7a,armeabi；无64位应用ABI | batch1 01_properties |
| CPU/内核 | 两个可见ARMv7 Cortex-A15核，Generic DRA74X，Linux4.4.45/armv7l | batch1 01_cpu/01_identity；不推断精确SKU |
| 内存 | MemTotal 1,926,428 KiB；当时MemAvailable 1,105,232 KiB | batch1 02_memory，单次快照 |
| 物理屏 | 1280×720，160dpi；wm没有Override行，历史display模式约60Hz | 本地batch1 + 历史用户ADB附件；不等于应用拥有完整面板 |
| 应用区域 | 历史mOverrideDisplayInfo为app1160×720、real1280×720 | 已读取原始用户附件；当前未重采，实际DiPlay Surface待测 |
| SoC/CPU历史补充 | machine=DRA752、family=DRA7、ES2.0；DT model=TI DRA742；possible/present/online=0-1 | 已读历史用户消息，保留SKU差异，不作为一期实现阻塞 |
| USB Host/普通系统 | 声明Host/麦克风/Wi-Fi/蓝牙；没有automotive特征行 | batch1 01_features；采用mobile |
| ADB权限 | 现有uid0；SELinux Disabled | batch1 01_identity；未提权，不等于应用权限 |
| iPhone | 内核与框架均可见05ac:12a8，480Mbps | batch1 04_usb/04_usb_sysfs；只是枚举 |
| 活动USB配置 | 2，iPod USB Interface | 未触发CarPlay请求 |
| 其他配置 | 1～4；3含USBMUX，4含USBMUX+Apple Ethernet；当前无CDC NCM 02/0d描述符 | 重枚举后须重新采集，不固定config6 |
| USB应用授权 | 当前列表为空 | 未测试requestPermission/openDevice |
| H.264 | OMX.TI.DUCATI1.VIDEO.DECODER，AVC声明最大1920×1088 | batch1 03_codecs；未实际解码 |
| Opus | 发现decoder，配置未发现encoder | batch1 03_codecs；不等于穷尽运行时能力 |
| 音频 | primary speaker 48kHz；快照有BUILTIN_MIC 16kHz输入线程 | batch2 06_audio/06_audio_flinger/06_audio_policy；未验证普通应用 |
| TUN | /dev/tun存在，system:vpn；/dev/net/tun不存在 | batch2 06_tun；后者缺失不判VPN失败 |
| 原车包候选 | com.baidu.carlifevehicle | batch2 05_package_candidates；不推断实现/特权 |
| 安装/导出 | PackageInstaller可见；第三方包列表为空；候选摘要无DocumentsUI | 不证明安装限制或chooser运行结果 |
| 运行认证 | 当前没有可用配置或已验证DiPlay测试包 | 用户确认；完整会话前置阻塞 |

### 电脑端核验

- 默认Java8；使用已有缓存JDK25.0.3执行离线Gradle。
- mobile debugRuntimeClasspath解析通过。实际选中Core1.19.0、Compose UI1.10.4、Material3 1.4.0、Activity Compose1.8.2、Lifecycle2.9.4、Car App/Projected1.4.0。
- 检查63个解析到的AndroidX AAR Manifest，没有minSdk>23；不代表所有JAR运行API通过。
- 当前minSdk28源码的checkDebugAarMetadata与processDebugMainManifest通过；没有执行API23 APK构建/安装、单元测试或connected测试。
- SDK37 API版本表确认AudioTrack.getAudioAttributes为API29，加入详细设计的新增修正。
- 当前目录无.git，未生成提交或修改应用实现。

### 已确认范围和仍需验证的门

用户确认一期普通APK有线画面/触摸/音乐/导航，保留原车共存；Siri/通话延期，无线二期。不采用系统提权路线。需求确认仅授权本轮详细设计，不授权探针安装运行。

| 门 | 状态 | 下一步 |
| --- | --- | --- |
| G0普通APK安装 | 待探针 | 审阅并批准安装独立小包 |
| G1a应用USB授权/open | 待探针 | 普通UID真实授权 |
| G1bvendor request与NCM配置 | 未执行 | 单独批准主动切换，记录重枚举及完整描述符 |
| G1c旧queue/关闭退出 | 待探针 | 验证取消/close能退出native等待 |
| G1d真实USBMUX/NCM收发 | 待兼容实现+实车 | 16KiB分段与协议完整性、MTU1500 |
| G2真实TI硬解 | 待探针 | 720p30样本、Surface恢复 |
| G2显示与触摸 | 待普通应用实测 | 不硬编码1280；验证1160Surface候选、928×576缩放和触摸坐标 |
| G3普通应用声音/焦点 | 待探针 | 用户触发播放、原车音源切换 |
| G4VPN授权/establish | 待探针 | 用户接受系统弹窗、建立并释放 |
| G5运行认证 | 缺失 | 补齐现有本地资产入口要求的可用认证 |
| G6完整有线会话 | 未验证 | 前置门通过后验收 |
| Siri/电话/无线/方向盘/ACC | 可选延期或二期 | 单独记录支持范围 |
| MCU/主机零件号 | 未取得 | 不阻碍源码设计；交付支持说明前补充可见版本 |

本地原始输出：`evidence/2026-10-01-batch1-local/`、`evidence/2026-10-01-batch2-local/`。存在设备标识，不随设计文档分发；本层.gitignore排除这些目录。

**当前结论：具备推进API23兼容和应用探针的已知平台线索；完整有线CarPlay尚无实测闭环，认证为已知缺口。**

---

以下保留原模板供后续按批填写。空字段属于下一轮记录占位，本轮状态以上节为准。

## 1. 原始分步采集模板（后续探针/补采用）

| 项目 | 填写 |
| --- | --- |
| 采集日期与本地时间 | 待填写 |
| 车机时间是否正确、与电脑差值 | 待填写 |
| 车辆是否停稳、供电是否稳定 | 待填写 |
| 本次结果目录（电脑本地路径） | 待填写 |
| 电脑系统与 ADB 版本 | 待填写 |
| ADB 连接方式 | USB / 已有网络 ADB / 其他 |
| 是否存在多个 ADB 设备，是否已选定车机 | 待填写；不必公开实际序列号 |
| `id`、SELinux 查询结果 | 待填写；本次不进行提权或模式修改 |
| 本轮完成批次 | A / B / C / D（可选） |

## 2. 已知信息与本次复核

| 项目 | 共享对话信息 | 本次结果 | 状态/证据文件 |
| --- | --- | --- | --- |
| 车型 | 2021 第二代哈弗 H6 冠军版 | 待复核 | 用户提供 |
| Android 版本 | 用户最终纠正为 Android 6 | 待填写 | 待采集：01_system_properties.txt |
| API Level | 预期 23 | 待填写 | 待采集 |
| ADB | 用户确认可用 | 待填写 | 待采集 |
| 原车投屏 | 有线百度 CarLife | 是否实际正常使用、使用哪种手机 | 用户提供/待补充 |
| 车机系统类型 | 普通定制 Android 候选 | 待综合判断 | 待采集：01_features.txt |
| 系统版本 / Build / fingerprint | 未实测 | 待填写，注意脱敏 | 待采集 |
| MCU 版本 | 未实测 | 待填写 | 系统页面/无法取得 |
| 主机型号或零件号 | 未实测 | 待填写；不要求拆机 | 系统页面/无法取得 |

## 3. 硬件与显示

| 项目 | 结果 | 状态/证据 |
| --- | --- | --- |
| `ro.hardware` / `ro.board.platform` | 待填写 | 01_system_properties.txt |
| CPU 信息与核数 | 待填写 | 01_cpuinfo.txt、01_cpu_online.txt |
| 精确 SoC 型号是否确定 | 待填写；只推断时注明 | 不把平台线索直接当作芯片精确型号 |
| 应用 ABI / abilist | 待填写 | 01_system_properties.txt |
| 32 位 / 64 位应用支持 | 待填写 | 以 ABI 属性为依据，不只看内核 |
| 内核版本 | 待填写 | 01_kernel.txt |
| MemTotal / MemAvailable | 待填写 | 02_meminfo.txt；缺字段时说明 |
| 可用存储 | 待填写 | 01_storage.txt |
| Physical size / Override size | 待填写 | 02_wm_size.txt |
| Physical density / Override density | 待填写 | 02_wm_density.txt |
| 显示方向及可用画面区域 | 待填写 | 02_display.txt / 人工观察 |

## 4. 编解码证据

| 项目 | 结果 | 状态/证据 |
| --- | --- | --- |
| media.codec 服务输出 | 正常 / 无服务 / 无活动实例 / 其他 | 03_media_codec.txt |
| media.player 服务输出 | 待填写 | 03_media_player.txt |
| 已取得 codec XML 清单 | 待填写 | 03_codec_paths.txt、codecs/ |
| 被引用但尚未取得的 XML | 待填写 | 记录 include 路径，后续按需补采 |
| H.264 / video/avc decoder 名称 | 待填写 | 区分声明与实际枚举 |
| 厂商实现或软件实现线索 | 待填写 | 不据名称直接宣布硬解通过 |
| 声明的尺寸、profile/level、帧率限制 | 待填写 | 无法取得时注明 |
| Opus decoder | 有线索 / 未发现 / 待确认 | 待填写 |
| Opus encoder | 有线索 / 未发现 / 待确认 | 待填写 |
| 实际 H.264 Surface 解码 | 尚未验证 | 待探针 |
| 实际 PCM / Opus 麦克风协商 | 尚未验证 | 待探针/完整会话 |

## 5. iPhone 与连接拓扑

| 项目 | 结果 |
| --- | --- |
| iPhone 型号 | 待填写 |
| iOS 完整版本 | 待填写 |
| Lightning / USB-C | 待填写 |
| CarPlay 设置或管理限制 | 待填写；无需账号信息 |
| 数据线与转接头 | 待填写 |
| 待测车机 USB 口的位置 | 待填写 |
| 是否为已验证的 CarLife 数据口 | 待填写 |
| 是否通过 HUB / 延长线 | 待填写 |
| ADB 与 iPhone 是否可以同时连接 | 待填写 |
| 若不能同时连接，当前阻塞原因 | 待填写，不自行改变 USB 角色 |

### 插拔事件记录

| 事件 | 电脑/车机时间 | 手动观察 | 对应文件 |
| --- | --- | --- | --- |
| 未插 iPhone | 待填写 | 车机处于哪个页面、CarLife 是否运行 | 04_*_before.txt |
| 插入 iPhone | 待填写 | 是否充电、弹窗、原车应用自动启动 | 04_*_after.txt |
| 用户是否选择“信任”或其他授权 | 待填写 | 未操作/接受/拒绝，说明选择 | 人工记录 |
| 拔掉 iPhone | 待填写 | 车机是否恢复，是否出现错误 | 04_*_removed.txt |

### USB 分层结果

| 层级 | 结果 | 证据状态 |
| --- | --- | --- |
| 系统声明 USB Host | 待填写 | 01_features.txt |
| sysfs 发现 Apple VID `05ac` | 是 / 否 / 权限不足 | 04_usb_sysfs_after.txt |
| 实际 PID、速度、当前配置、接口 | 待填写 | sysfs 快照；不公开 serial |
| dumpsys Host 列表发现 Apple | 是 / 否 / 输出受限 | 04_usb_after.txt |
| 拔出后移除 | 是 / 否 / 未观察到 | 04_usb_sysfs_removed.txt |
| 应用 `requestPermission()` | 尚未验证 | 待探针 |
| 应用 `openDevice()` | 尚未验证 | 待探针 |
| CarPlay vendor request | 未执行 | 待单独批准探针 |
| 请求后的重枚举与再次授权 | 尚未验证 | 待探针 |
| 按 USBMUX/NCM 描述符选择配置 | 尚未验证 | 待探针；不固定 config ID 6 |
| claim 与实际 USBMUX/NCM 收发 | 尚未验证 | 待探针 |

## 6. CarLife、安装和系统服务

| 项目 | 结果 | 证据 |
| --- | --- | --- |
| CarLife 候选包名 | 待填写 | 05_carlife_candidates.txt |
| 确认的包名与前台 Activity | 待填写 | 05_carlife_activity.txt |
| 系统/特权应用标记、UID、权限 | 待填写 | 05_carlife_package.txt |
| 是否存在厂商相关服务线索 | 待填写，不推断调用实现 | 01_services.txt |
| 以往第三方 APK 安装记录 | 成功/失败/无记录 | 人工说明，保留错误码 |
| API 23 最小测试包安装 | 未执行 | 待单独批准 |
| 运行认证前提 | 已具备/未具备/待确认 | 不回传任何私钥、证书容器 |

## 7. 音频、麦克风与 VPN

| 项目 | 结果 | 证据状态 |
| --- | --- | --- |
| 音频输出与 primary route | 待填写 | 06_audio*.txt、audio/ |
| 麦克风输入设备与路由 | 待填写 | 系统配置线索，不代表应用录音成功 |
| 当前原车音源及焦点状态 | 待填写 | 06_audio.txt |
| `/dev/tun` 或 `/dev/net/tun` | 存在/未发现/无权限 | 06_tun_nodes.txt |
| 当前 IPv6 地址 | 待填写，必要时脱敏 | 06_ipv6_interfaces.txt |
| 实际普通应用播放 | 尚未验证 | 待探针 |
| 实际普通应用录音 | 尚未验证 | 待探针 |
| VpnService 授权与 establish | 未执行 | 待探针 |

## 8. 可选项与优先级

| 项目 | 结果或延期原因 |
| --- | --- |
| 输入设备与 getevent 静态信息 | 待填写/可选延期 |
| 方向盘按键采集 | 待填写/可选延期；无事件不等于不支持 |
| Wi-Fi / 蓝牙基线 | 待填写/可选延期 |
| 当前电源状态 | 待填写/可选延期 |
| 日常 ACC 熄火/唤醒观察 | 待填写；本轮不主动测试 |
| 首期是否仅有线 | 待确认 |
| Siri 是否首期必须 | 待确认 |
| 通话是否首期必须 | 待确认 |
| 原车功能保留与应用共存要求 | 待填写 |

## 9. 失败、缺失和补采清单

| 步骤/命令 | 原始错误或缺失字段 | 已尝试的只读替代方式 | 后续处理 |
| --- | --- | --- | --- |
| 待填写 | 待填写 | 未尝试/具体方式 | 补采/待探针/可选延期 |

不要用“无输出”替代原始错误，也不要为了补齐本表 root、修改权限或删除原车软件。

## 10. 审查结论（结果到齐后填写）

- **必需 ADB 与人工信息：** 未完成 / 已完成 / 有明确阻塞。
- **可选延期项：** 待填写。
- **已证实的能力：** 待填写。
- **只有声明或配置线索的能力：** 待填写。
- **仍需普通应用探针的关键项：** 待填写。
- **当前最大阻塞：** 待填写。
- **下一步建议：** 补采 / 经用户确认后制作最小探针 / 暂停评估。
- **是否获得开始探针的确认：** 否。
- **是否获得开始正式兼容改造的确认：** 否。

**初始结论：等待逐步采集和审查，不开始修改 DiPlay。**
