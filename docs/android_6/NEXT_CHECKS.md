# 返回 ADB 环境后的最小检查清单

- 最新状态日期：2026-10-03；以下只读命令原编于2026-10-01，保留供后续复查。
- 当前状态：已按设备所有者驻车授权建立可恢复临时窗口（整包停用落盘后重启）；探针与主体基础包安装/冷启动成功，并完成卸载。普通UID USB open及主动切换后CDC NCM描述符通过；配置切换仍受当前USB音频/HID占用阻塞，queue未进入。TI 720p样本循环10分钟、短测试音可听、一次探针桌面Surface恢复通过。普通VPN授权窗口缺失；额外批准ACTIVATE_VPN临时授权后无路由TUN建立/释放成功，授权已撤销。主体补齐VPN缺失错误处理；source-only缺CarPlay认证，完整会话未开始。测试包均卸载，安全包/组件恢复DEFAULT，最终重启核验完成：两服务PID1321、system_server PID532 hasBound=true，包/组件DEFAULT=0、/system只读、无测试包和TUN残留。详见[临时窗口与适配实测](SECURITY_PROBE_WINDOW_2026-10-03.md)。
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
| 运行认证/完整会话 | iPhone13Pro/iOS16.1能否接受身份并完成会话 | 当前缺少可用认证输入，先补齐 |

APK已完成初步构建，包名、源码、签名、哈希和各按钮动作见[实施交接](IMPLEMENTATION_STATUS.md)及[探针审阅说明](../../tools/android6-probe/README.md)。2026-10-03已授权并尝试安装，但会话安装后被自动删除，主动测试未执行；此处命令仍仅提供只读采集，不混入adb install、vendor request或VPN建立命令。

## 3. 当前无需补查的项目

- 精确DRA742/DRA752 SKU：历史标识差异保留，对ARM32/API23实现无当前分支影响。
- 重复codec XML、media.codec服务：XML已采集，历史media.codec不存在；实际运行测试更有价值。
- MCU、方向盘、ACC、热点/蓝牙无线、麦克风：记录为后续交付说明/二期信息；不阻断一期代码设计。
- 原车APK逆向、完整日志、系统提权、改USB MUX：没有当前证据要求进入这些路线，且不属于已确认的普通APK方案。

## 4. 本轮开发后的优先级

只读快照已采集，当前优先解决**G0安装后持续留存/冷启动未通过**。安装获准后再验证应用USB权限/真正NCM、旧queue关闭与VPN，随后验证H.264和音频。认证材料是完整会话的另一外部阻塞，ADB快照不能补齐。详细状态、测试结果与审阅包见[IMPLEMENTATION_STATUS.md](IMPLEMENTATION_STATUS.md)。
