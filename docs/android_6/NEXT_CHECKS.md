# 返回 ADB 环境后的最小检查清单

2026-10-04晚间更新：车机已安装并验证code30手动接口释放，手机认证/真实首帧通过；断线与线缆触碰高度同步，原厂线对照及车机音频输出未完成。code31诊断包仅保存，尚未安装。当前包、触发方式、恢复与下一步见[USB释放与音频记录](USB_RELEASE_TEST_2026-10-04.md)。以下保留此前构建/探针的历史记录，不代表今天最终状态。

- 最新状态日期：2026-10-04；以下只读命令原编于2026-10-01，保留供后续复查。主体standalone认证输入已离线补齐，包与明早测试步骤见[实车交接](CAR_TEST_2026-10-04.md)，不需要为准备认证再次采ADB数据。
- 既有车测：已按设备所有者驻车授权建立可恢复临时窗口（整包停用落盘后重启）；探针与主体基础包安装/冷启动成功，并完成卸载。普通UID USB open及主动切换后CDC NCM描述符通过；探针配置3切换失败，活动配置2有USB音频/HID占用，queue未进入。主体选择快照中的配置6，实际选择与数据通道待测。TI 720p样本循环10分钟、短测试音可听、一次探针桌面Surface恢复通过。普通VPN授权窗口缺失；额外批准探针ACTIVATE_VPN临时授权后无路由TUN建立/释放成功，授权已撤销且不转移给主体。当时source-only缺认证，完整会话未开始。测试包均卸载，安全包/组件恢复DEFAULT，最终重启核验完成：两服务PID1321、system_server PID532 hasBound=true，包/组件DEFAULT=0、/system只读、无测试包和TUN残留。详见[临时窗口与适配实测](SECURITY_PROBE_WINDOW_2026-10-03.md)。
- 依据：[原会话复核](CONVERSATION_REVIEW.md)、[现有实测结果](VALIDATION_RESULTS.md)、[详细设计](DETAILED_DESIGN.md)。

## 1. 必要的下一轮只读快照

已有Android/ABI/内存/codec/音频/TUN资料足够支持设计，不重复全量采集。下次进入探针阶段前，只需确认目标仍一致、当前应用显示区域和iPhone USB初始状态。

以下在电脑Mac终端执行，不在交互式adb shell中执行。使用原来已经开启的ADB端点；不执行adb tcpip，不改变网络/显示/USB设置。若get-state失败，停止采集并恢复原有连接方式，不猜测其他设备地址。

```bash
H6_NEXT_TARGET='192.168.43.1:5555'
if ! adb -s "$H6_NEXT_TARGET" get-state; then
  echo '目标车机未在线，请恢复原有连接后再运行。'
else
  H6_NEXT_OUT="$(mktemp -d "${TMPDIR:-/tmp}/diplay-h6-next.XXXXXX")"
  adb -s "$H6_NEXT_TARGET" shell \
    'getprop ro.product.model; getprop ro.product.device; getprop ro.build.version.sdk; getprop ro.build.display.id' \
    > "$H6_NEXT_OUT/01_identity.txt" 2>&1
  adb -s "$H6_NEXT_TARGET" shell wm size \
    > "$H6_NEXT_OUT/02_wm_size.txt" 2>&1
  adb -s "$H6_NEXT_TARGET" shell wm density \
    > "$H6_NEXT_OUT/02_wm_density.txt" 2>&1
  adb -s "$H6_NEXT_TARGET" shell dumpsys display \
    > "$H6_NEXT_OUT/02_display.txt" 2>&1
  adb -s "$H6_NEXT_TARGET" shell dumpsys usb \
    > "$H6_NEXT_OUT/04_usb_current.txt" 2>&1
  echo "输出已保存在：$H6_NEXT_OUT"
fi
```

预期身份为IHU01 / j6headunit / API23。若不同，先停止套用本设计。请记录采集时iPhone是否插入、所在USB口及原车CarLife是否运行；本清单不要求启动或停止CarLife。命令卡住约30秒时人工中止并保留已有输出，超时不视为通过。

重点回传：

- `mBaseDisplayInfo`、`mOverrideDisplayInfo` 的app/real尺寸，屏幕模式/density。历史值为real1280×720、app1160×720；实际DiPlay Surface仍要由探针/应用测量。
- USB Host中Apple VID1452/05ac、PID、配置和接口。已知历史本地快照为05ac:12a8、活动配置2、配置列表1～4；变化本身不代表失败。
- 明确错误，例如服务不存在或权限拒绝。USB快照可能含手机序列号，回传前替换成`<redacted>`，不需要提供serial。

这组只读ADB不能证明应用USB授权、CarPlay切换、真实视频播放或VPN可用，不需要为了补齐结果清日志。

## 2. 真正必要但不能只用ADB查询代替的测试

| 测试 | 要回答的问题 | 执行前提 |
| --- | --- | --- |
| 普通APK安装/启动 | 是否被ROM安装策略限制 | 先提供源码、包名、权限、签名与哈希，经用户批准安装 |
| USB权限/open | 普通应用能否访问当前iPhone | 用户在系统弹窗授权 |
| vendor request/重枚举 | 是否暴露真正USBMUX+NCM，能否再次授权 | 先批准主动USB切换；不要在只读采集脚本发送请求 |
| 旧USB queue/close | 16KiB持续接收、超时不取消、关闭线程能否退出 | 普通应用探针，实际驱动测试 |
| 实际Surface/视频/触摸 | Surface是否1160×720，硬解能否工作，边角命中是否正确 | 探针样本与后续DiPlay真实会话 |
| 普通应用音频/VPN | 是否能播放并正确失焦；VpnService是否能建立和释放 | 用户触发测试音/VPN授权 |
| 运行认证/完整会话 | iPhone13Pro/iOS16.1能否接受身份并完成会话 | 使用2026-10-04已显式补齐认证的standalone包；先完成主体自身VPN授权 |

APK包名、源码、签名、哈希和按钮动作见[实施交接](IMPLEMENTATION_STATUS.md)及[探针审阅说明](../../tools/android6-probe/README.md)。2026-10-03最初安装被自动删除，随后临时窗口内主动测试已执行，结果见开头链接；此处命令仍仅提供只读采集，实际主体测试按新的实车交接执行。

## 3. 当前无需补查的项目

- 精确DRA742/DRA752 SKU：历史标识差异保留，对ARM32/API23实现无当前分支影响。
- 重复codec XML、media.codec服务：XML已采集，历史media.codec不存在；实际运行测试更有价值。
- MCU、方向盘、ACC、热点/蓝牙无线、麦克风：记录为后续交付说明/二期信息；不阻断一期代码设计。
- 原车APK逆向、完整日志、系统提权、改USB MUX：没有当前证据要求进入这些路线，且不属于已确认的普通APK方案。

## 4. 本轮开发后的优先级

只读快照已采集，G0临时窗口内安装/启动通过，安全策略恢复后的持续留存仍是独立条件。主体standalone已准备，下一轮重点是主体包级VPN授权、真正USB配置/接口与持续数据通道，以及手机认证后的画面/触摸/音频。无需先重复探针采集。详细状态、测试结果与审阅包见[IMPLEMENTATION_STATUS.md](IMPLEMENTATION_STATUS.md)。
