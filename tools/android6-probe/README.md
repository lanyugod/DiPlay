# Android 6 能力探针审阅说明

用于详细设计G0–G4。已制作和构建；2026-10-03用户授权验证后，ADB及正常系统安装器均安装失败，探针未安装/运行。实测证据见[本轮结果](../../docs/android_6/PROBE_RESULTS_2026-10-03.md)。与DiPlay主体、原车CarLife包名独立，不携带任何认证材料。

## 2026-10-03 追加实车状态

用户已授权驻车临时停用与重启，窗口内安装/冷启动/卸载通过；720p样本循环10分钟、音频可听、一次桌面Surface恢复通过。普通VPN窗口缺失，额外授权的无路由TUN测试通过并已撤销；USB配置切换失败，queue未运行。布局改为独立操作区与有界日志区。详见[追加实测](../../docs/android_6/SECURITY_PROBE_WINDOW_2026-10-03.md)。下文原始APK哈希对应修改UI前，当前APK为1560601 bytes，SHA256 b347f1a47842fc510b28a46c5ab83a42e3a9e903ca5412a84a6ceb1526aa321b。

## 身份与权限

- 包名：`com.shihab.diplay.android6probe`，入口：`.ProbeActivity`；minSdk23、targetSdk37。
- APK：`build/outputs/apk/debug/DiPlayAndroid6Probe-debug.apk`，1585287 bytes。
- APK SHA256：`c0ca772a916d2e22a27e40f9fe48b9e0798ed6f4bf1fe5f544c70e061ac563c0`。
- debug证书SHA256：`bcc18556561523b8b1122b559136024aecbd1dca0cd8ca3461080647834824b5`。
- Manifest没有应用级uses-permission；USB Host feature可选；VPN服务私有并受`BIND_VPN_SERVICE`保护。USB与VPN均由系统授权UI确认。无存储、录音、网络权限，无自动配置切换、无认证或原车服务控制。
- UI显示普通应用uid；日志不读取USB serial，不保存媒体、手机数据或配对记录。完成payload只计字节数；Android logcat保留诊断标签`DiPlay-API23-Probe`。

## 每个按钮的作用

| 按钮 | 作用与边界 |
| --- | --- |
| USB只读快照 | 仅列枚举配置、接口、端点及是否已有授权；不open/claim/switch |
| G1a权限/open | 为唯一Apple设备请求USB系统授权；授权后open并立即close；拒绝时不循环请求 |
| G1b主动切换 | 再次确认后发送vendor IN 0xc0/0x52、value0/index4、1byte；可能重枚举；关闭连接后等待用户再次快照和授权，不自动claim |
| G1c旧queue | 再次确认后选择含USBMUX的配置、claim(false)、旧16KiBqueue和无参requestWait；消费者每250ms超时但不cancel；不发送协议数据。复用正式`LegacyUsbReadPump`源码，不是另一套模拟实现 |
| 关闭USB | 终止消费、cancel+connection.close、泵有界join并报告耗时及reconnectAllowed；卡住时禁止开更多pump |
| G2视频 | 用户启动后实际MediaExtractor/MediaCodec进行720p/30fps Surface解码，输出实际codec/首帧/Surface代次；每5秒重启样本，最多10分钟；样本重启不等于连续CarPlay负载，需后续真实会话验证 |
| 停止视频 | 结束解码循环；Surface销毁/恢复会重建解码，不能代表车机倒车必然恢复 |
| G3短测试音 | 旧音频焦点+48kHz单声道低幅度440Hz、2秒；失焦静音，结束释放焦点/AudioTrack；实际是否可听需用户记录 |
| G4授权/建立 | 用户接受VPN系统弹窗后bind私有服务、建立fd00:23::1/128、MTU1500、无路由TUN；不拦截原车流量，不模拟完整NCM网络 |
| G4释放 | 关闭TUN并unbind；Activity销毁同样释放 |

进入桌面/倒车时记录Surface回调，恢复后查看画面。USB重枚举后必须重新授权；G1c只测驱动生命周期，不能证明CarPlay有真正NCM或协议数据完整。禁止凭此探针宣称认证/CarPlay成功。

## 后续顺序与记录

当前先确认厂商支持的安装渠道或授权策略；不通过禁用安全服务或修改系统绕过G0。以下步骤须等正常安装获准后继续。

1. 先执行`docs/android_6/NEXT_CHECKS.md`只读快照，确认IHU01/j6headunit/API23及采集上下文。
2. 审阅上述源码/签名/按钮动作并明确批准安装及需要执行的测试后，才在目标车机安装。若已有同包且签名不同，停止覆盖；不默认卸载。当前没有自动安装脚本。
3. G0冷启动 → G1a → G4 → G1c关闭/拔插（目标waiter退出1秒以内） → 已批准的G1b主动切换及描述符快照 → G2/G3。G1b影响USB，需先退出原车投屏。
4. 回传按钮日志和实际可见/可听结果；USB卡住/无法再次open、VPN拒绝/无法建立、没有真正CDC NCM应优先报告。不要清日志。日志仅含本探针信息时可在明确目标后过滤导出，序列号无需回传。
5. 应用主体实际Surface、触摸、音频路由及USBMUX/NCM协议必须在认证到位后再次验证；G1d/G5/G6不能用本探针代替。

构建（从仓库根目录，选择JDK25及Android SDK）：

```sh
./gradlew -p tools/android6-probe --no-configuration-cache assembleDebug lintDebug
```

AGP生成任务仅从shared拷贝`UsbReadTransport.kt`和`LegacyUsbReadPump.kt`到build目录，探针不依赖shared模块或其原生库。样本为ffmpeg testsrc2生成，无外部视频来源：

```sh
ffmpeg -f lavfi -i testsrc2=size=1280x720:rate=30 -t 5 -c:v libx264 -profile:v baseline -level 3.1 -pix_fmt yuv420p -b:v 1M -g 30 -an src/main/res/raw/avc720.mp4
```
