# 返回 ADB 环境后的最小检查清单

- 日期：2026-10-01。
- 当前状态：用户已离开 ADB 调试环境；本轮没有连接设备。以下命令供用户稍后一起执行。
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

APK尚未准备和审阅，因此这里不提供占位的adb install或am start命令，防止把状态变更混入只读采集。

## 3. 当前无需补查的项目

- 精确DRA742/DRA752 SKU：历史标识差异保留，对ARM32/API23实现无当前分支影响。
- 重复codec XML、media.codec服务：XML已采集，历史media.codec不存在；实际运行测试更有价值。
- MCU、方向盘、ACC、热点/蓝牙无线、麦克风：记录为后续交付说明/二期信息；不阻断一期代码设计。
- 原车APK逆向、完整日志、系统提权、改USB MUX：没有当前证据要求进入这些路线，且不属于已确认的普通APK方案。
