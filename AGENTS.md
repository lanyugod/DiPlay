# DiPlay 本地维护约束

本仓库是面向哈弗H6 Android6/API23的选择性官方功能移植分支。修改前阅读 [同步范围与步骤](docs/UPSTREAM_SYNC.md)、[提交清单](docs/upstream-sync.json) 和相关 `.agents/notes/implemented/` 记录。

- 同步官方时先核对目标标签/SHA和提交差异，保留现有未提交工作，按所需通用功能移植。BYD车辆联动、仪表/多屏地图、车辆档位依赖的停车视频不随官方版本自动纳入。
- mobile/common/shared、NDK保持API23及armeabi-v7a；automotive维持API28。新API和标准库调用须有版本保护或兼容封装。保留旧USB持续读泵/关闭边界、H6单一音频焦点与代次隔离。
- H6一期为有线/H.26430/单屏，麦克风与车辆联动关闭。不能把描述符、样本播放或电脑回归当作完整实车会话通过；车测缺口见docs/android_6。
- 官方基线、提交处置、本地版本和诊断中的版本需一致，更新docs/upstream-sync.json、UpstreamSyncInfo.kt、mobile版本及同步说明。已有认证资产构建门禁保持。

提交同步或兼容行为改动前运行：

```sh
python3 scripts/check_upstream_sync.py
python3 scripts/check_public_tree.py
./gradlew --no-configuration-cache :shared:testDebugUnitTest :common:testDebugUnitTest :mobile:lintDebug :mobile:assembleDebug
./gradlew --no-configuration-cache -I scripts/check-android6-newapi.gradle :mobile:lintDebug
```

非平凡取舍同步记录到相关Agent Note；本机JDK/SDK路径按环境设置，不修改全局配置。
