# Agent Note: H6 安装检查与会话路线评估

Status: proposed

## Problem

独立API23探针经ADB及正常系统安装器安装失败，厂商BdSecurityService返回code:1。用户要求解释可恢复的绕过方法及其后果，由用户选择是否执行，不接受破坏车机系统或以恢复出厂设置回滚。

## Proposal

用户随后明确授权标准PackageInstaller会话安装及启动绕过调查。已执行会话182091378的create/write/commit，全部成功，但PACKAGE_ADDED检查在任何启动前自动删除探针。故本笔记仍为proposed：实验已执行，稳定安装/运行路线尚未获得，不能标记目标完成。

目标固件传统installPackageAsUser存在百度钩子；会话commit转调installStage，没有同一钩子，已获实测印证。但com.baidu.privacy内SecurityService监听安装完成广播并做独立签名检查，框架AMS启动也调用该服务；仅切换安装入口不足以持续放行。

离线解析真实BdPrivacy.apk后确认：PackageVerifier按证书公钥SPKI匹配white；本机framework-res与VendorDetector.YF_PK精确一致，选择APK资产signs_imx，探针debug公钥不在其中。常见AOSP testkey/platform/shared/media只在另一张表中，本机不接受。当前未找到只对探针放行的受支持数据配置/接口；不因updateConfig名字而假定可改信任表。

继续优先评估合法的厂家签名授权或明确的局部放行入口。该阶段未选择停用。所有者后续明确授权的临时停用、重启、探针与恢复由[临时测试窗口](../../implemented/process/2026-10-03-h6-temporary-security-probe.md)承接；本提案仍保存会话单独放行失败和永久签名信任未解决的结论，不把临时窗口当永久解法。修改信任资产与进程注入未执行。

此提案与[H6 API23兼容边界](../../implemented/feature/2026-10-01-h6-api23-wired-compatibility.md)部分重叠，新增安装门槛分析，保留原应用兼容与共存约束；不改变系统提权和原车应用控制边界。细节及命令见[安装策略评估](../../../../docs/android_6/INSTALL_POLICY_OPTIONS.md)。

## Alternatives considered

- 厂家受信任签名可满足已确认的公钥判断，无需关闭全局服务；局部放行接口尚未找到，二者都不能宣称已运行或可卸载。
- 单独继续会话重试没有新增依据：本次已证实commit后自动删除，排除将其当稳定交付路线。
- 用AOSP公开测试签名重签不需要改车机，但本机选表不包含其公钥，离线对比后排除。
- install -i可只改变安装来源记录，改动范围小，但仍进入原检查且真实UID/签名不改变；未知规则下不作为有依据的首选。
- 临时停用com.baidu.privacy可能使策略调用不可用，不改原始二进制；但它是特权常驻隐私/权限组件，影响超出安装，重新enable不能保证原DEFAULT状态及运行连接恢复，因此不优先采用。
- bdcarsec调试属性开关代码已存在，操作简单；但此user固件只在eng/userdebug分支读取这些值，不能作为此设备的有效方案。
- system_server内存Hook可定点修改目标包判断且不回写系统文件，但存在系统核心进程故障和撤销缺口；框架文件补丁还引入开机恢复依赖，均不符合当前低影响要求。

## Acceptance criteria

- 操作前后核对IHU01/j6headunit/API23、APK哈希、目标包不存在及活动会话基线。
- 本次会话实验记录commit成功与自动删除，不以Success单独判定G0通过；已复查探针不存在、两服务及绑定正常。
- 正式commit只针对独立包，不覆盖、降级、默认授予全部权限或改变安装位置。
- 后续可行路线必须先验证持续留存/冷启动与普通卸载，再进入能力探针；本次启动及用户卸载均未执行，自动删除不是普通卸载通过的证据。
- 记录安装、启动、卸载各自结果；不能用安装成功宣称DiPlay会话可用。

## Risks

会话提交成功后立即自动删除是实际结果，已否定它单独解决安装限制的预期。停用SecurityService影响全局安全策略，且与BdPrivacyService同进程；停用整个包还影响权限/AppOps和隐私监控。重新enable写1不等于原DEFAULT=0，本机pm无default-state命令，精确状态与运行重连的撤销未验证。修改信任表需处理原厂系统APK签名/shared UID/ART依赖，并非已证实的/data单文件变更。日志和历史元数据不等于字节级恢复；不采用恢复出厂设置、删包数据库或刷机回滚。

## Existing note audit

已检索活跃proposed/implemented/rejected中H6、安装、BdSecurity、普通APK及root关键词。仅命中既有H6 API23实现笔记，归类为部分重叠并互链；没有完全吸收或过时提案，不删除或归档旧决定。
