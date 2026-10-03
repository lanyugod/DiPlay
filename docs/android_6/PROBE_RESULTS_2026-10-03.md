# H6 探针验证：2026-10-03

## 最新追加测试

已按设备所有者驻车授权建立可恢复临时窗口（整包停用落盘后重启）；探针与主体基础包安装/冷启动成功，并完成卸载。普通UID USB open及主动切换后CDC NCM描述符通过；配置切换仍受当前USB音频/HID占用阻塞，queue未进入。TI 720p样本循环10分钟、短测试音可听、一次探针桌面Surface恢复通过。普通VPN授权窗口缺失；额外批准ACTIVATE_VPN临时授权后无路由TUN建立/释放成功，授权已撤销。主体补齐VPN缺失错误处理；source-only缺CarPlay认证，完整会话未开始。测试包均卸载，安全包/组件恢复DEFAULT，最终重启核验完成：两服务PID1321、system_server PID532 hasBound=true，包/组件DEFAULT=0、/system只读、无测试包和TUN残留。详见[临时窗口与适配实测](SECURITY_PROBE_WINDOW_2026-10-03.md)。

以下为此前安全服务运行时的历史安装调查，APK哈希和“未执行”均对应该阶段。

## 首阶段结论

手机接入后的只读采集已完成。传统安装被厂商安全服务拒绝；用户随后授权的标准会话安装返回Success，但探针在任何启动尝试之前被安全服务自动删除。**G0稳定安装/冷启动未通过**，探针当前不存在，G1–G4未执行，不能据此认定USB、视频、声音或VPN不兼容。

已读取实际策略APK并在电脑核对：本机使用Yftech对应的signs_imx公钥白名单，探针debug公钥不在其中。PACKAGE_ADDED签名检查会自动删除不受信任的新包；启动另有同一服务的异步检查。尚无已验证的低影响放行方法。详情见[安装与启动策略](INSTALL_POLICY_OPTIONS.md)。未停用安全服务或修改系统。

## 用户授权与环境

- 用户授权数据采集和独立探针 APK 验证，明确禁止车机毁坏性测试。
- 用户确认已停稳、CarLife 未运行，iPhone 13 Pro / iOS 16.1 接入原车 CarLife 数据口。
- 用户确认以前未安装过第三方 APK。
- “未知来源安装”最初只读查询为 `0`；用户手动开启并确认，后续查询为 `1`。该变化不是自动化修改。
- 使用已有网络 ADB 端点 `192.168.43.1:5555`，未运行 `adb root`、`adb tcpip` 或改变 SELinux。
- 用户进一步授权标准会话安装及启动绕过调查；本轮没有执行服务停用、注入或系统回写。
- 适配代码提交：`0e77dbf`。本轮未修改应用实现、重构建 APK 或重新运行电脑端单元测试。

## 当前只读结果

| 项目 | 实测结果 | 边界 |
| --- | --- | --- |
| 车机 | IHU01 / j6headunit / API23 / M4B30Z dev-keys | 与原目标一致 |
| ABI | armeabi-v7a, armeabi | ARM32 |
| 显示 | real 1280×720，override app 1160×720，160dpi | 不是探针 Surface 实测 |
| iPhone USB | 05ac:12a8，480Mbps，活动配置2，配置数量4 | 内核和框架枚举一致 |
| 初始接口 | 配置2为音频/HID；配置3含USBMUX，配置4含USBMUX及厂商Apple Ethernet接口 | 当前快照无标准CDC NCM 02/0d接口；未发切换请求，不能据此判断切换后是否支持NCM |
| 应用USB授权 | Device permissions为空 | 未进行普通应用授权/open |
| /data存储 | 约2.9GB总量、2.2GB空闲 | 未出现空间不足错误 |
| 安装基线 | 探针包不在系统包列表中 | 没有同包覆盖或签名冲突 |
| 结束状态 | 探针仍未安装；iPhone仍在USB列表中；未知来源为1 | 未执行主动USB、视频、音频或VPN测试 |

另已采集当前前台应用、音频及连接基线，保存于电脑本地证据目录，不作为普通应用能力通过的依据。

## APK 核验

- 包名：`com.shihab.diplay.android6probe`；versionCode 1；minSdk23 / targetSdk37。
- 文件：`tools/android6-probe/build/outputs/apk/debug/DiPlayAndroid6Probe-debug.apk`，1,585,287 bytes。
- SHA256：`c0ca772a916d2e22a27e40f9fe48b9e0798ed6f4bf1fe5f544c70e061ac563c0`。
- debug证书SHA256：`bcc18556561523b8b1122b559136024aecbd1dca0cd8ca3461080647834824b5`。
- `apksigner verify --verbose --min-sdk-version 23`通过，v1及v2签名均通过。
- 正常系统安装确认页显示正确探针名称及“此应用不需要任何特殊权限”。
- 从车机下载目录读回文件，字节数和SHA256与电脑原包一致。

## 安装尝试及证据

| 正常路径 | 开关状态 | 结果 |
| --- | --- | --- |
| `adb install` | 用户开启未知来源前 | `Failure [INSTALL_FAILED_INTERNAL_ERROR]` |
| 系统PackageInstaller，`/data/local/tmp/diplay-api23-probe-review.apk` | 1 | 确认后“应用未安装。” |
| 系统PackageInstaller，`/sdcard/Download/DiPlayAndroid6Probe-20261003-c0ca772a.apk` | 1 | 确认后“应用未安装。” |
| 标准PackageInstaller会话182091378 | 1 | create/write/commit均成功；随后安全服务自动删除，未启动 |

三次安装均有相应的厂商服务失败日志。例如下载目录的正常安装：

```text
21:34:03.267 PackageManager: SecurityService start to install, package is: /sdcard/Download/DiPlayAndroid6Probe-20261003-c0ca772a.apk
21:34:03.267 BdSecurityService: onInstallPackage ... callingUID : 10000
21:34:03.288 BdSecurityService: call result is {"code":1} time 21ms
21:34:03.288 BdSecurityService: oninstall... result is: {"code":1}
```

ADB路径同样返回`code:1`，调用UID为0。系统安装器路径调用UID为10000。上述三次传统尝试未使用`pm install -i`；后续标准会话实验单独记录如下。仅这些传统安装日志不能证明具体内部拒绝规则；后续实际策略APK分析补齐了公钥信任规则，仍没有普通应用启动结果。

## 标准会话追加结果

用户明确要求先执行标准会话安装。会话182091378写入1,585,287 bytes，commit返回Success。随后日志在21:56:09.375出现deletePackage回调，21:56:09.531报告删除成功（returnCode:1）；此期间未执行am start或探针按钮操作。pm path及dumpsys package均无探针，后续复查仍不存在。因此会话入口可提交，但未解决安装后的签名信任检查。

实际策略服务为com.baidu.privacy/com.baidu.bdcarsec.SecurityService，与BdPrivacyService同进程。安装后及后续复查PID均为1314，system_server绑定保持hasBound=true；未停止或重启服务。电脑只读复制并离线分析BdPrivacy.apk及framework-res.apk，没有回写。

## 测试门状态

| 门 | 本轮状态 |
| --- | --- |
| G0安装/冷启动 | 传统安装失败；会话commit成功但立即自动删除，冷启动未执行 |
| G1a USB授权/open | 未执行，被G0阻挡 |
| G1b主动切换/真正NCM | 未执行 |
| G1c旧queue与关闭 | 未执行 |
| G2视频/Surface | 未执行 |
| G3声音/焦点 | 未执行 |
| G4 VPN | 未执行 |
| G5认证/G6完整会话 | 仍未验证；主体source-only APK没有认证输入 |

## 保存与后续

本地证据保存在`evidence/2026-10-03-probe-local/`，已加入Git忽略；USB序列号已脱敏。电脑临时原始工作目录为`/private/tmp/diplay-h6-20261003-probe/`。追加会话证据来自`/private/tmp/diplay-h6-session-20261003/`，已复制到本地证据的`session-install/`；commit的原始文件缺失，该返回值单独标明来自工具记录，删除日志为原始文件。对外分享本摘要，不附全量车机快照。

失败安装提示已通过“完成”关闭。两个本轮放置的APK文件保留在车机临时目录和下载目录，便于后续通过受支持渠道审阅安装；没有删除既有文件。未改写系统分区、清数据、卸载/禁用/停止原车应用、重启或主动更改USB/VPN/音频。

下一步应取得本机信任表对应的合法签名授权或受支持放行入口；任何停用/修改策略的方案须明确影响及精确恢复路径后另行选择。若该固件不支持此安装范围，需要重新评估普通APK交付路线；不再反复提交相同包，也不以改minSdk、包名或随机签名猜测规则。安装获准后再恢复G0–G4验证；完整会话仍另需可用认证输入。
