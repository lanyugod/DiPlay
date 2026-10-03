# H6 安装、启动策略与恢复边界

日期：2026-10-03。

最新阶段：已按设备所有者驻车授权建立可恢复临时窗口（整包停用落盘后重启）；探针与主体基础包安装/冷启动成功，并完成卸载。普通UID USB open及主动切换后CDC NCM描述符通过；配置切换仍受当前USB音频/HID占用阻塞，queue未进入。TI 720p样本循环10分钟、短测试音可听、一次探针桌面Surface恢复通过。普通VPN授权窗口缺失；额外批准ACTIVATE_VPN临时授权后无路由TUN建立/释放成功，授权已撤销。主体补齐VPN缺失错误处理；source-only缺CarPlay认证，完整会话未开始。测试包均卸载，安全包/组件恢复DEFAULT，最终重启核验完成：两服务PID1321、system_server PID532 hasBound=true，包/组件DEFAULT=0、/system只读、无测试包和TUN残留。详见[临时窗口与适配实测](SECURITY_PROBE_WINDOW_2026-10-03.md)。

以下为临时停用授权前的历史策略调查，后续停用/恢复实测以追加记录为准。

标准会话安装已按用户授权执行；commit成功后探针被安全服务自动删除。启动未执行，策略修改、停用服务、系统回写均未执行。

## 实测结果

目标为IHU01/j6headunit/API23，ADB端点192.168.43.1:5555。使用原独立探针com.shihab.diplay.android6probe，1,585,287 bytes，SHA256为c0ca772a916d2e22a27e40f9fe48b9e0798ed6f4bf1fe5f544c70e061ac563c0，没有覆盖原车包或默认授予权限。

```text
pm install-create --user 0 -S 1585287
Success: created install session [182091378]
pm install-write -S 1585287 182091378 base.apk /data/local/tmp/diplay-api23-probe-review.apk
Success: streamed 1585287 bytes
pm install-commit 182091378
Success
```

随后日志显示：

```text
21:56:09.375 BdSecurityService: callback : {"cmd":"deletePackage","packageName":"com.shihab.diplay.android6probe"}
21:56:09.377 BdSecurityService: uninstall package : com.shihab.diplay.android6probe
21:56:09.531 BdSecurityService: deleted package : com.shihab.diplay.android6probe, returnCode : 1
```

returnCode=1在这里表示删除成功。安装后的pm path和dumpsys package均为空；后续复查仍无探针。删除发生在任何启动尝试之前，不是已实测的启动失败。普通卸载也未验证；自动删除不能证明用户发起的卸载会通过。G0稳定安装/冷启动仍未通过，G1–G4未执行。完整记录见[探针结果](PROBE_RESULTS_2026-10-03.md)。

## 安装与启动由谁检查

框架包装层BdSecurityService位于/system/framework/bdcarsec.jar，由system_server加载；实际Java策略位于/system/priv-app/BdPrivacy_bak/BdPrivacy.apk，即com.baidu.privacy内的com.baidu.bdcarsec.SecurityService。

| 阶段 | 目标固件中的行为 | 证据边界 |
| --- | --- | --- |
| 传统安装 | PMS.installPackageAsUser调用installPackageWithVerificationAndEncryption | 三次传统安装返回code:1，与代码一致 |
| 标准会话提交 | PackageInstallerSession.commitLocked转调PMS.installStage，没有上述传统入口钩子 | 本次create/write/commit实测成功；仍不能稳定留存 |
| 安装完成广播 | PackageEventReceiver收到PACKAGE_ADDED，调用PackageEventManager.onRun；签名不受信任则请求deletePackage | 代码与本次启动前自动删除日志相互印证 |
| 应用启动 | AMS→BdActivityManager.verifyProcess→BdSecurityService.onRunPackage→同一SecurityService | 仅静态核对；探针未运行。服务可先返回OK，再异步检查、终止进程或删除包 |
| 扫描/重连 | 扫描检查签名、可能的APK哈希及JNI；安全回调重连会扫描已有进程 | 绕过一次安装或一次启动不等于持续运行 |
| 用户卸载 | pm uninstall→PackageInstallerService.uninstall→PMS.deletePackage，仍有厂商钩子 | 自动删除调用者为system_server，有内部调用例外；不能当普通卸载的免检查证据 |

## 已确认的签名信任规则

PackageVerifier提取X.509证书的公钥编码（SPKI），要求DatabaseManager.getCertSignTag返回white。这是厂家公钥信任，不是Android APK签名格式校验。探针v1/v2签名有效，但不受该表信任；改minSdk、开启未知来源、普通root安装均不能替代信任。

白名单来自策略APK内的assets/signs_at或assets/signs_imx。JsonFile使用ARCFOUR及内置字符串baidu读取；本轮仅在电脑解码副本，未改车机文件。VendorDetector依据android框架APK签名选表：

- 本机framework-res.apk与BdPrivacy.apk均为Yftech证书；其SPKI SHA256为6a8fd516f9d18f557195abab90dfe8cc236691042575799496042cbc0ed37dac，精确匹配VendorDetector.YF_PK，选择signs_imx。
- signs_imx含10个受信任公钥。探针SPKI SHA256为a9202157b153d8c230689f12a38f65761af455a8ff600e689712a8ed44db36be，不在其中。
- 离线对比AOSP官方镜像的公开testkey/platform/shared/media证书：它们只匹配另一张signs_at表，均不匹配本机选择的signs_imx。不能据dev-keys标识宣称公开测试签名可用。
- 此版本PackageVerifier直接从APK资产加载信任表。当前没有发现从应用数据文件加载替代信任表或更新公钥的接口；Commander.updateConfig更新的是运行配置，不是这张公钥表。
- 当前/data/data/com.baidu.privacy/files只有appops.xml和records，未见config文件。PackageRecord.hasPermission用于权限确认；PACKAGE_ADDED仍独立做签名检查，改权限记录不足以放行新包。

以上是本固件的离线代码和公钥对比，不是所有固件的通用结论。没有解出厂家私钥；复制证书、公钥或改证书名称不能生成受信任的有效签名。厂商签名授权与CarPlay认证材料是两个独立问题。

## 停止服务的影响

com.baidu.privacy为system UID1000、PERSISTENT/PRIVILEGED包，同一进程运行BdPrivacyService与SecurityService；system_server以AUTO_CREATE绑定后者。本次安装后及后续复查，两服务仍在PID1314，system_server为PID619，绑定hasBound=true，未停止或重启它们。

- 只停止SecurityService，理论影响范围主要是厂商安装/启动/卸载策略、安装后删除及扫描。它是被绑定的服务，am stopservice不保证真正断开；重连还会检查已有进程。
- 停用整个com.baidu.privacy会同时影响隐私服务、权限/AppOps管理、隐私监控和依赖它的客户端。两个服务在同一进程，组件状态变化也可能引起进程重启，不能保证另外一个服务完全不受影响。
- 框架onRunPackage在策略连接为空时返回“不阻止”的分支，提示停用可能使启动检查失效；这只是静态候选，不是停用后安装/运行/其他原车功能已验证的保证。
- 当前没有证据表明该包直接控制MCU/CAN；同样没有验证停止它后电话、导航、倒车界面及其他依赖功能完全正常。
- 服务停止期间其他应用的行为可能改变。重新启用会恢复检查，仍不受信任的探针可能再次被删除，因此临时关闭不是永久安装解法。

## 方案比较与可恢复性

| 方法 | 明确操作对象与影响 | 恢复判断 |
| --- | --- | --- |
| 标准会话安装 | 只提交独立探针，未停安全服务 | 已执行，commit成功但自动删除；不再反复重试 |
| 厂家受信任签名/授权 | 厂家使用signs_imx中对应的合法签名密钥签署独立包，或提供受支持放行入口 | 无需改全局安全策略；仍需验证普通卸载、冷启动和持续留存。当前没有授权密钥或已验证入口 |
| 改安装来源、调试属性、改权限记录 | -i仅改来源；bdcarsec调试开关仅在eng/userdebug读取；权限记录不取代签名判断 | 当前user固件没有有效依据，不作为可用方案 |
| 停用安全组件 | 候选对象com.baidu.privacy/com.baidu.bdcarsec.SecurityService；包管理命令形态为pm disable --user 0 包/组件 | 全局安全策略暂停，可能牵动同进程隐私服务。恢复enabled开关后还须验证重连/扫描；未执行，也不能保证探针恢复后留存 |
| 停用整个包 | 候选命令形态pm disable-user --user 0 com.baidu.privacy | 比单组件影响更广。pm enable写ENABLED=1，不等于原DEFAULT=0；本机pm未提供default-state命令。精确回滚需额外确认API恢复渠道，当前不作为可执行的低风险方案 |
| 改信任资产或策略APK | 将自有公钥加入signs_imx，或改签名判断，再部署修改后的BdPrivacy.apk | 必须改系统APK资产/签名及可能的ART缓存；会破坏其原厂平台签名和shared UID关系。不是仅编辑一个/data白名单文件，当前不执行 |
| 定点内存Hook | 改SecurityService或system_server中针对探针的签名/运行判断 | 需要进程注入，可能崩溃或牵动系统服务；未验证移除与恢复，不在实车执行 |

“开关能重新打开”只能表示配置可改回，不能保证运行状态、权限记录及所有原车功能恢复。本轮不把factory reset、删除/data/app、改packages.xml或刷机当回滚方式。

在用户选择任何策略变更之前，应先准备并验证：原包/组件enabled精确状态、两服务和绑定基线、仅相关配置文件的备份及哈希/属主/权限/SELinux标签、离线原APK、撤销工具与重连检查。备份这些文件本身不等于已有完整恢复能力。当前无已验证的低影响策略变更方案，因此没有执行停用或系统修改。

## 对DiPlay的含义

当前探针与DiPlay自编译包使用同一debug证书，均为普通第三方包；按本机公钥规则，DiPlay也会遇到相同信任问题，不能仅靠Android6代码适配解决。不过本轮没有安装DiPlay，不能把规则推断写成其实际安装结果。签名/策略问题解决后才恢复G0–G4；source-only主体APK缺CarPlay认证输入，仍另阻塞完整会话。

## 证据

本地脱敏证据位于evidence/2026-10-03-probe-local/session-install及policy-analysis，均已Git忽略。电脑原始只读分析目录为/private/tmp/diplay-h6-policy-readonly；未回写系统二进制，不随仓库分发厂家APK或全量资产。

| 原文件 | SHA256 |
| --- | --- |
| /system/framework/bdcarsec.jar | 9cd9a70433a5e492ff6d856ae7a57db9ed61f627acff8c237d3d50d6896e5e76 |
| /system/framework/oat/arm/services.odex | 920f5f450e30d6c2bd1067c88620da8ec2ca45433e14e89dd5728e2d00fd8414 |
| /system/priv-app/BdPrivacy_bak/BdPrivacy.apk | 3fbf47aa7f97e6497cf94613c853535baa8fcac14a41889039445d7a5c9bc232 |

ART优化DEX使用dexdump -i读取，有优化后校验和提示；关键调用与原始策略APK、运行日志互相印证。公开证书来源：https://github.com/aosp-mirror/platform_build/tree/master/target/product/security 。
