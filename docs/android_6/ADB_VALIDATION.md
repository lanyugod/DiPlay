# H6 Android 6：改造前 ADB 分步验证清单

- 编制日期：2026-09-30
- 目标：2021 第二代哈弗 H6 冠军版，Android 6 车机
- 状态：**仅准备验证步骤；本文命令尚未在你的车机执行，尚未开始 DiPlay 改造。**
- 依据：[共享对话及最后审计意见](https://chatgpt.com/share/6abd044b-5220-83e9-abc8-ae8df98c4f09)、[原改造计划](README.md)、当前本地源码。
- 结果填写：[验证结果模板](VALIDATION_RESULTS.md)。

> 2026-10-01状态更新：两批批准的本地只读采集已完成；用户提供的来源会话正文及附件也已复核。当前结果以[VALIDATION_RESULTS.md](VALIDATION_RESULTS.md)为准。原会话新增app1160×720等信息见[CONVERSATION_REVIEW.md](CONVERSATION_REVIEW.md)，稍后只补必要查询见[NEXT_CHECKS.md](NEXT_CHECKS.md)。下文保留原分步清单，其“尚未采集”描述是编制时状态，不应覆盖最新结果。

> 执行原则：分组采集、分组回传，先确认硬件与系统事实，再决定是否进入探针验证和正式改造。不要把所有代码块一次性粘贴执行。需要你手动操作的步骤、可选步骤和暂停点均已标注。

## 1. 已知信息及证据边界

| 信息 | 当前结论 | 证据状态 |
| --- | --- | --- |
| 车型 | 2021 第二代哈弗 H6 冠军版 | 共享对话中用户提供 |
| Android | Android 6，预期 API 23 | 用户曾写 Android 9，随后纠正为 Android 6；以本次 `getprop` 复核 |
| ADB | 已可用 | 用户提供；连接方式、权限身份待采集 |
| 原车投屏 | 有线百度 CarLife | 用户提供 |
| 第三方 APK 安装 | 不能只因 ADB 可用就认定任意 APK 可安装 | 待安装记录或后续经批准的测试包验证 |
| USB Host 与 iPhone | 有线 CarLife 提供相关线索 | 尚未证明 iPhone 枚举、应用 USB 授权和 CarPlay 切换 |
| 屏幕、CPU、SoC、RAM、MCU | 待实测 | 不把共享对话里的车型资料推断作为本机实测 |
| iPhone 型号与 iOS | 待补充 | 在 iPhone 设置中手动查看 |

已读取共享页面提供的可见用户消息、回答和最后审计意见。页面中部分历史插件输出显示为已删节，不能把这些不可见的工具证据视为已独立核实。

## 2. 对最后审计意见的采纳与修正

| 审计建议或说法 | 本次处理 |
| --- | --- |
| 有线优先、USB 兼容层最高优先级 | 采纳；无线、BYD 功能不作为本次前置投入 |
| 增加 P0.5 USB Probe | 采纳为后续独立决策门槛；**本轮不开发、不安装、不运行探针** |
| API 23 的旧 USB 请求按 16 KiB 上限处理 | 作为后续兼容实现约束；读写都要验证，不能直接把 64 KiB 缓冲区传给旧接口 |
| 核查 Configuration 6 | **修正：不能以配置编号 6 作为通用判据**。本地 `IphoneCarPlayConfiguration.kt:30` 按 USBMUX、NCM 等接口描述符选择配置，编号会因设备而异 |
| `05ac` 表示普通应用已能使用 iPhone | **修正：sysfs 只证明内核枚举；框架可见、应用授权、打开和接口读写需分别验证** |
| `dumpsys media.codec` / XML 可以证明硬解 | **修正：只能提供清单或配置线索，服务输出可能只含活动实例；最终需要 MediaCodec 枚举及真实解码测试** |
| 没有 Opus encoder 就研究软件 Opus | 先验证 PCM；不把缺少 Opus encoder 直接判为项目失败 |
| Native JNI 首期不必优先改 | 有条件采纳；仅限确认有线＋本地认证路径不加载相关库后，才可考虑隔离，不直接删除 `.so` |
| legacy Manifest/sourceSet | 保留为设计选项，不是已确认必须改造项；旧系统出现未知属性不等于一定安装失败，要看合并 Manifest 和安装证据 |
| 音频缓冲使用播放头估算 | 后续需处理无符号 32 位回绕、flush/stop/rebuild 后的计数重置，以及 timestamp 不可用的回退 |
| 原审计称命令全部只读，但包含 `adb logcat -c` | **不采纳清日志步骤**。本清单保留原日志，以时间戳和插拔前后快照关联 |
| 读完硬件信息即可正式改造 | 先审查信息；应用级权限、USB 切换、硬解、麦克风等仍可能需要经单独批准的探针 |

其他不应直接照搬的判断：

- 原对话较早回答把 `AudioTrack.Builder` 写为 API 21，实际为 API 23；`AudioRecord.Builder` 也是 API 23。均满足目标 API 23，但不能据此保证厂商音频 HAL 正常。
- CarLife 的包权限与进程信息不能证明它内部一定调用了哪套 USB API，也不代表第三方应用具有同等权限。
- `/proc/cpuinfo`、平台属性可能只能缩小 SoC 范围，不能保证直接得到精确芯片型号或主机零件号。
- 不把“USB 通过等于项目已成功 70%”等经验判断当作量化验收结果。

## 3. 安全、隐私和执行环境

### 3.1 本轮允许与不执行的操作

本清单的设备侧命令以读取属性、系统服务快照、公开配置和日志为主。`adb pull` 读取车机文件并在电脑写入副本；保存输出和创建文件夹发生在电脑。

**本轮不执行：** root、remount、修改 SELinux、刷机、重启、写系统属性、修改 USB 模式、清日志、删除或禁用原车应用、授予应用权限、安装 APK、开启新的 ADB 网络端口、调用未知厂商 service 命令。

- 只在车辆停稳、驻车状态、供电稳定时操作。不要边驾驶边验证，不在通风不良处怠速。
- `Permission denied`、`Can't find service`、文件不存在或命令不支持都保留原始输出，**不要提权补救**。
- 设备命令超过约 30 秒没有结束时，可以按 `Ctrl+C`，记录超时并先停下；不要无限等待或反复触发。
- 只有读命令不代表完全无负载；日志和 `dumpsys` 可能短暂增加负载。出现原车功能异常时停止采集。
- 插拔 iPhone、点击信任、打开 CarLife、按方向盘按钮属于手动状态变化，不与只读查询混为一谈。
- 日志可能包含 VIN、序列号、蓝牙地址、SSID、手机名称、定位及账号信息。回传前审阅脱敏；不要上传配对文件、认证私钥、热点密码或整个 bugreport。

### 3.2 电脑与车机命令的区别

以下代码块在 **Mac 的终端（zsh/bash）执行，不是在交互式 `adb shell` 中执行**。

- `>` 位于电脑 shell，文件保存在电脑。
- `hu` 是下面定义的快捷函数，等价于 `adb -s "$HU_SERIAL"`，始终指定目标车机。
- 车机上的 `cat`、`getprop`、`dumpsys` 等命令通过 `hu shell` 执行。
- 过滤默认放在电脑做，避免依赖老 Android 的 `grep -E`、`tail` 等工具版本。
- 不使用 `dumpsys -l` 作为必需条件；部分老系统不支持，改用 `service list`。
- 以下采集流程不适用于直接粘贴到 Windows CMD/PowerShell；若改用 Windows，先转换脚本。

## 4. 第 0 步：确认 ADB 目标，建立独立结果目录

### 4.1 查看已连接设备

```bash
adb version
adb devices -l
```

**检查：** 车机对应行必须是 `device`，不是 `offline` 或 `unauthorized`。如果没有连接，先使用你已经验证过的连接方式。

仅当你原本就有已开启、已知地址的网络 ADB，可使用下面的连接命令；先替换地址和端口。它只连接现有端点，不负责开启网络调试：

```bash
adb connect '替换为已知车机IP:已知端口'
```

不要猜测端口、扫描车内网络或为本次检查执行 `adb tcpip`。不要在公用网络暴露老系统 ADB。

### 4.2 固定设备并保存输出

将第一行替换为 `adb devices -l` 中车机那一行的序列号，或网络连接的 `IP:端口`。**不要选手机或模拟器。**

```bash
export HU_SERIAL='替换为车机序列号或IP:端口'

hu() {
  command adb -s "$HU_SERIAL" "$@"
}

hu get-state
hu shell getprop ro.product.model
hu shell getprop ro.build.version.sdk
```

确认目标正确后，在同一个终端建立采集目录和保存函数：

```bash
export OUT="$(mktemp -d "$HOME/Desktop/diplay-android6-check.XXXXXX")"
mkdir -p "$OUT/codecs" "$OUT/audio"
printf '本次结果目录：%s\n' "$OUT"

cap() {
  local filename="$1"
  shift
  {
    printf 'HOST_TIME: '
    date '+%Y-%m-%d %H:%M:%S %z'
    printf 'COMMAND:'
    printf ' %q' "$@"
    printf '\n\n'
    "$@"
    local rc=$?
    printf '\nHOST_COMMAND_EXIT_CODE: %s\n' "$rc"
  } > "$OUT/$filename" 2>&1
  printf '已保存：%s/%s\n' "$OUT" "$filename"
}

cap 00_adb_version.txt adb version
cap 00_device_identity.txt hu shell 'id; getenforce; date; getprop ro.product.model; getprop ro.build.version.sdk'
```

**注意：**

- `cap` 显示“已保存”只表示保存了输出，不表示检查通过；文件中的错误也属于有效诊断资料。
- 老版 ADB 或同一 shell 中的多条命令可能不可靠地传播每一步退出码，判断以输出正文为准。
- 所有后续步骤使用同一终端中的 `HU_SERIAL`、`OUT`、`hu`、`cap`。如果关闭终端，重新执行本节并开始一轮新采集，不要误写旧目录。
- 同一轮重复执行同一个文件名会覆盖该文件；需保留两轮结果时重新创建采集目录。

**暂停点 0：** 如果设备身份不明、连接不稳定，先回传上述结果，不继续。

## 5. 第 1 步：系统、ABI、SoC、安装环境

```bash
cap 01_system_properties.txt hu shell '
for p in \
  ro.build.version.release ro.build.version.sdk ro.build.version.security_patch \
  ro.build.fingerprint ro.build.description ro.build.display.id \
  ro.build.type ro.build.tags ro.build.characteristics \
  ro.product.manufacturer ro.product.brand ro.product.model \
  ro.product.name ro.product.device ro.product.board \
  ro.hardware ro.board.platform \
  ro.product.cpu.abi ro.product.cpu.abi2 ro.product.cpu.abilist \
  ro.product.cpu.abilist32 ro.product.cpu.abilist64 \
  ro.config.low_ram
 do
  printf "%s=" "$p"
  getprop "$p"
 done'

cap 01_kernel.txt hu shell uname -a
cap 01_cpuinfo.txt hu shell cat /proc/cpuinfo
cap 01_cpu_online.txt hu shell 'cat /sys/devices/system/cpu/possible; cat /sys/devices/system/cpu/online'
cap 01_features.txt hu shell pm list features
cap 01_services.txt hu shell service list
cap 01_storage.txt hu shell df
cap 01_third_party_packages.txt hu shell pm list packages -3
```

**检查与判读：**

- Android 6 应对应 API 23；如果不一致，停止套用既定版本假设。
- 以 `abilist` / `abi` 判断 APK 所需 ABI；64 位 CPU 或内核不等于应用环境一定支持 `arm64-v8a`。
- 属性为空不代表硬件不存在；平台、内核、CPU 信息需综合判断。
- `android.hardware.type.automotive` 只能作系统声明线索，不能仅凭有无该特征证明 AAOS 身份。
- `android.hardware.usb.host` 是声明线索，不是 iPhone 应用访问的实测证据。
- 现有第三方应用列表不能证明本次新 APK 一定允许安装；列表也可能包含个人信息。

**手动补充：** “关于设备”页面的系统版本、MCU 版本、主机型号/零件号（如果可见）。拍照前遮挡 VIN、账号和序列号；无需拆机，也不必为了查信息进入未知工程菜单。

## 6. 第 2 步：内存、屏幕与默认视频档位依据

```bash
cap 02_meminfo.txt hu shell cat /proc/meminfo
cap 02_wm_size.txt hu shell wm size
cap 02_wm_density.txt hu shell wm density
cap 02_display.txt hu shell dumpsys display
```

**检查与判读：**

- 记录 `MemTotal`、`MemAvailable`；旧内核没有 `MemAvailable` 时保留其他字段，不将 `MemFree` 直接当作可用内存。
- 同时记录 `Physical size/density` 和 `Override size/density`；应用布局可能受 override 影响。
- 如果 `wm` 不支持，保留报错，并使用 `dumpsys display` 与系统界面信息辅助判断。
- 不执行 `wm size 数值`、`wm density 数值` 或 reset，这些会改变显示配置。
- 不依据公开资料预填本机分辨率为 1280×720。

**暂停点 A：** 建议第一次只执行到这里，先回传 `00_*`、`01_*`、`02_*` 以及系统页面信息。确认基础身份与连接方式后，再执行 USB 相关步骤。

## 7. 第 3 步：H.264 / Opus 能力线索

### 7.1 保存媒体服务状态和配置文件清单

```bash
cap 03_media_codec.txt hu shell dumpsys media.codec
cap 03_media_player.txt hu shell dumpsys media.player
cap 03_codec_file_listing.txt hu shell 'ls -l /system/etc/media_codecs*.xml /vendor/etc/media_codecs*.xml /odm/etc/media_codecs*.xml'
```

有些目录不存在属正常现象。`media.codec` 服务不存在或内容为空，也不能直接判定没有解码器。

### 7.2 拉取存在的编解码配置到电脑

下面只拉取可列出的 XML，不修改车机文件。路径中的 `/` 会转换为 `__`，避免不同目录同名文件互相覆盖。

```bash
hu shell '
for f in /system/etc/media_codecs*.xml /vendor/etc/media_codecs*.xml /odm/etc/media_codecs*.xml; do
  if [ -f "$f" ]; then
    printf "%s\n" "$f"
  fi
done' > "$OUT/03_codec_paths.txt" 2> "$OUT/03_codec_paths_error.txt"

while IFS= read -r remote; do
  remote="${remote%$'\r'}"
  case "$remote" in
    /system/etc/media_codecs*.xml|/vendor/etc/media_codecs*.xml|/odm/etc/media_codecs*.xml)
      name="${remote#/}"
      name="${name//\//__}"
      hu pull "$remote" "$OUT/codecs/$name"
      ;;
  esac
done < "$OUT/03_codec_paths.txt" > "$OUT/03_codec_pull.txt" 2>&1
```

可选：在电脑过滤便于查看的摘要，不替代原始文件：

```bash
grep -inE 'avc|h264|opus|encoder|decoder|include' "$OUT/03_media_codec.txt" "$OUT/03_media_player.txt" > "$OUT/03_codec_summary.txt"
```

**检查与判读：**

- 看 `video/avc` 的 decoder 名称、支持尺寸、profile/level、帧率和限制；配置引用其他 XML 时记录被引用文件名，下一轮按需补采。
- 厂商 OMX 名称是硬件实现的线索，不是硬解已成功的证据；`OMX.google.*` 通常是软件实现。
- 区分 Opus decoder 和 encoder：麦克风 Opus 上行关心 encoder。
- 没有发现 Opus encoder 不作为停止条件，PCM 上行仍值得后续验证。
- `dumpsys` 可能只显示活动实例，XML 可能不完整。最终能力需要应用调用 `MediaCodecList` 并进行实际编解码测试。

## 8. 第 4 步：iPhone 插拔前后的 USB 证据（重点）

### 8.1 先确认连接拓扑

手动记录：

- 电脑通过 USB ADB 还是已有网络 ADB 连接车机？
- iPhone 准备连接哪个车机物理 USB 口？是否就是正常使用 CarLife 的口？
- 是否经过 HUB、转接头或延长线？
- 数据线类型及是否已在其他场景验证能传数据？

**如果电脑 ADB 占用唯一的待测 USB 口，且插入 iPhone 会断开 ADB，先停下。** 不要用 Y 线把两个 Host 硬连，也不要为本轮临时改变 USB 角色或开启新网络调试；先确认可同时观测的连接方案。

### 8.2 定义只读 USB 快照函数

```bash
usb_snapshot() {
  hu shell '
for d in /sys/bus/usb/devices/*; do
  if [ -f "$d/idVendor" ]; then
    printf "\n=== DEVICE %s ===\n" "$d"
    for f in idVendor idProduct bcdDevice product manufacturer bNumConfigurations bConfigurationValue speed busnum devnum; do
      if [ -f "$d/$f" ]; then
        printf "%s=" "$f"
        cat "$d/$f"
      fi
    done
  fi
  if [ -f "$d/bInterfaceClass" ]; then
    printf "\n=== INTERFACE %s ===\n" "$d"
    for f in bInterfaceNumber bAlternateSetting bInterfaceClass bInterfaceSubClass bInterfaceProtocol; do
      if [ -f "$d/$f" ]; then
        printf "%s=" "$f"
        cat "$d/$f"
      fi
    done
  fi
done'
}
```

本函数不主动读取 USB serial 字段，但系统 `dumpsys` 和日志中仍可能出现序列号，需要脱敏。

### 8.3 不插 iPhone 时采集

保持车机开机，电脑 ADB 已连接；不要禁用或删除原车 CarLife。此时让 iPhone 与待测口断开。

```bash
cap 04_usb_before.txt hu shell dumpsys usb
cap 04_usb_sysfs_before.txt usb_snapshot
cap 04_usb_nodes_before.txt hu shell 'ls -l /sys/bus/usb/devices; ls -l /dev/bus/usb'
cap 04_device_time_before.txt hu shell date
cap 04_logcat_before.txt hu logcat -d -v threadtime
```

### 8.4 手动插入 iPhone，然后采集

1. 保持 iPhone 解锁，连接到待测数据口。
2. 记录插入时间、是否充电、是否弹出“信任”或原车 CarLife。
3. 本次若选择了“信任”或其他授权，记录选择；这是状态变化，不称为只读。
4. 等待约 10～15 秒。不要期待未改造的 DiPlay 在此时工作。

```bash
cap 04_device_time_after.txt hu shell date
cap 04_usb_after.txt hu shell dumpsys usb
cap 04_usb_sysfs_after.txt usb_snapshot
cap 04_logcat_after.txt hu logcat -d -v threadtime
cap 04_dmesg_after.txt hu shell dmesg
```

电脑上生成辅助摘要：

```bash
grep -inE 'usb|05ac|1452|apple|iphone|carlife|permission|denied' "$OUT/04_logcat_after.txt" > "$OUT/04_usb_log_summary.txt"
diff -u "$OUT/04_usb_sysfs_before.txt" "$OUT/04_usb_sysfs_after.txt" > "$OUT/04_usb_sysfs_diff.txt"
```

`grep` 没匹配、`diff` 发现差异时可能返回非零退出码，均不等于采集失败。完整快照和日志仍需保留。

### 8.5 手动拔掉 iPhone，验证移除

```bash
cap 04_usb_removed.txt hu shell dumpsys usb
cap 04_usb_sysfs_removed.txt usb_snapshot
cap 04_logcat_removed.txt hu logcat -d -v threadtime
```

如果 `dmesg` 权限不足，只保存报错；不要 root。没有执行 `logcat -c`，前后日志可能重复，后续按设备时间及 VID/PID 关联；如果车机时间明显错误，保留电脑时间和事件顺序。

### 8.6 USB 结果怎么判断

| 观察结果 | 能证明什么 | 不能证明什么 |
| --- | --- | --- |
| sysfs 出现 `idVendor=05ac` | 内核枚举到了 Apple USB 设备 | 不证明应用可访问或 CarPlay 可用 |
| `dumpsys usb` 的 Host 设备列表出现 Apple，可能显示十进制 `1452` | Android USB 框架看到了设备 | 不证明 DiPlay 或普通应用已获授权 |
| 只有 USB device/gadget 的 MTP、ADB functions | 车机作为 USB Device 的配置状态 | 不应误当作 iPhone Host 枚举结果 |
| 原车 CarLife 自动打开 | 原车识别/处理了某种连接事件 | 不证明第三方能 claim 同一接口 |
| 插入前后没有变化 | 本轮未观测到枚举 | 可能是线、端口、锁屏、拓扑或权限；不能立刻判定内核不支持 |
| iPhone 拔掉后节点消失 | 设备移除被系统观测到 | 不证明后续请求取消和应用线程退出正确 |

**特别注意：**

- sysfs 接口通常反映当前活动配置，不代表所有可能配置；未看到 NCM 不等于永远不支持。
- 本步骤没有发送 CarPlay vendor request，因此未发生 CarPlay 重枚举并不是失败。
- 本步骤没有测试 `requestPermission()`、`openDevice()`、`claimInterface()` 或真实 USB 数据流。

**暂停点 B：** 回传 `03_*`、`codecs/`、`04_*` 和插拔观察。若 iPhone 基本枚举都看不到，先排查线材、端口和连接拓扑，不进入全面移植。

## 9. 第 5 步：原车 CarLife 与系统安装限制线索

### 9.1 列出安装包并在电脑筛选

```bash
cap 05_packages_with_paths.txt hu shell pm list packages -f
grep -iE 'carlife|baidu|hilife|haval|greatwall|yuanfeng|car' "$OUT/05_packages_with_paths.txt" > "$OUT/05_carlife_candidates.txt"
```

只把匹配结果当作候选包，不凭包名直接认定主程序。

如果停车状态下方便操作，**手动打开原车 CarLife**，然后执行：

```bash
cap 05_carlife_processes.txt hu shell ps
cap 05_carlife_activity.txt hu shell dumpsys activity activities
```

### 9.2 确认实际包名后才执行

将下面的占位符替换为本机查到的包名；如果尚未识别出包名，跳过本节并回传候选清单。

```bash
export CARLIFE_PKG='替换为实际CarLife包名'
cap 05_carlife_package.txt hu shell dumpsys package "$CARLIFE_PKG"
cap 05_carlife_apk_path.txt hu shell pm path "$CARLIFE_PKG"
```

**关注：** `codePath`、系统/特权应用标记、权限及授予状态、共享 UID、USB attach 声明和活动组件。

**限制：** 这些信息不能证明该程序内部调用方式，也不能把它拥有的系统权限授予 DiPlay。本轮不拉取原车 APK、不反编译、不禁用或 force-stop 原车应用。

**第三方安装能力：** 请手动说明以前是否成功安装过自带安装包以外的 APK，并提供当时的报错或成功记录（如有）。本轮不通过安装当前不兼容的 DiPlay 来“试运气”。

## 10. 第 6 步：音频、麦克风、VPN/TUN 基础线索

```bash
cap 06_audio.txt hu shell dumpsys audio
cap 06_audio_flinger.txt hu shell dumpsys media.audio_flinger
cap 06_audio_policy.txt hu shell dumpsys media.audio_policy
cap 06_audio_config_listing.txt hu shell 'ls -l /system/etc/audio_policy.conf /vendor/etc/audio_policy.conf /system/etc/audio_policy*.xml /vendor/etc/audio_policy*.xml'
cap 06_tun_nodes.txt hu shell 'ls -l /dev/tun /dev/net/tun'
cap 06_ipv6_interfaces.txt hu shell cat /proc/net/if_inet6
```

可读取的音频配置自动拉到电脑：

```bash
hu shell '
for f in /system/etc/audio_policy.conf /vendor/etc/audio_policy.conf /system/etc/audio_policy*.xml /vendor/etc/audio_policy*.xml; do
  if [ -f "$f" ]; then
    printf "%s\n" "$f"
  fi
done' > "$OUT/06_audio_paths.txt" 2> "$OUT/06_audio_paths_error.txt"

while IFS= read -r remote; do
  remote="${remote%$'\r'}"
  case "$remote" in
    /system/etc/audio_policy.conf|/vendor/etc/audio_policy.conf|/system/etc/audio_policy*.xml|/vendor/etc/audio_policy*.xml)
      name="${remote#/}"
      name="${name//\//__}"
      hu pull "$remote" "$OUT/audio/$name"
      ;;
  esac
done < "$OUT/06_audio_paths.txt" > "$OUT/06_audio_pull.txt" 2>&1
```

**检查与判读：**

- 关注 primary output、输入设备、MIC、VOICE_COMMUNICATION、VOICE_RECOGNITION 相关路由和音频焦点。
- 系统有麦克风输入路由不等于普通应用可以录音；实际需要授权后的 `AudioRecord` 测试。
- TUN 节点存在不等于 `VpnService.establish()` 一定成功，节点无法读取也不等于系统没有 VPN 能力。
- 目前没有 IPv6 地址不等于 USB 虚拟链路以后不能使用 IPv6；这是基线线索。
- 不修改音频策略文件、不启动 VPN、不进行录音或通话测试。本轮只保存状态。

**暂停点 C：** 回传 `05_*`、`06_*`、`audio/`。到此已覆盖有线改造的大部分 ADB 前置信息；以下是补充项。

## 11. 第 7 步：输入与方向盘按键（可选）

先做静态查询：

```bash
cap 07_input_devices.txt hu shell cat /proc/bus/input/devices
cap 07_getevent_capabilities.txt hu shell getevent -lp
```

只有停车状态下、确认操作不会触发不期望的通话或其他功能时，才做短时按键采集：

```bash
hu shell getevent -lt > "$OUT/07_key_events.txt" 2>&1
```

命令会持续运行。建议最多采集 30～60 秒，然后 **按电脑 `Ctrl+C` 结束**。

- 依次测试上一曲、下一曲、播放/暂停、音量加、音量减。
- 语音键可能唤起原车助手或拨号，只有你明确愿意测试时才按；本轮不要求长按。
- 手动记录每次按钮顺序、短按/长按、是否有原车动作。
- 不在采集中输入密码或操作其他敏感输入。
- 如果无权限直接跳过，不修改输入设备权限。
- `getevent` 中无事件不证明按钮不可用；厂商可能通过 MCU、CAN、私有服务或广播分发，而不是 Linux input。

## 12. 第 8 步：无线及休眠基线（可选，可明确延期）

### 12.1 无线状态

仅当你打算以后做无线，并愿意采集相关敏感状态时执行：

```bash
cap 08_wifi.txt hu shell dumpsys wifi
cap 08_bluetooth.txt hu shell dumpsys bluetooth_manager
cap 08_network_addresses.txt hu shell ip addr
```

- 不开启或关闭热点、Wi-Fi、蓝牙，不修改 ADB 所在网络。
- 老系统 `ip` 或服务不存在时保留错误即可。
- SSID、MAC、手机名称、配对信息可能出现在输出中，审阅后再回传。
- 支持 5 GHz STA 不代表支持 5 GHz 热点或 P2P；本节不据此作无线可行性承诺。

### 12.2 电源状态

```bash
cap 08_power.txt hu shell dumpsys power
cap 08_battery.txt hu shell dumpsys battery
```

只记录当前状态，不触发熄火、重启、强制休眠或电池模拟。车机 battery 服务可能没有实际车辆电量意义，不能据此判断整车电池。

ACC 真实休眠和倒车切换在正式测试方案确认后单独做；当前你可以手动描述日常观察到的启动时间、熄火后是否快速恢复。

## 13. 第 9 步：无需 ADB 的关键补充

填写 [验证结果模板](VALIDATION_RESULTS.md)：

- iPhone 型号、iOS 完整版本、Lightning/USB-C、数据线及转接情况。
- iPhone 是否允许 CarPlay，是否有屏幕使用时间或单位管理限制（无需提供账号信息）。
- 原车 CarLife 是否已经实际正常使用，使用哪种手机、哪一个 USB 口。
- 希望首期只做有线，还是 Siri、电话也必须同时可用。
- 过去 APK 安装成功/失败记录和是否需要特殊安装方式；不要为补信息临时修改系统。
- 运行认证材料是否有合法可用来源：只填“已具备/未具备/待确认”，**不要回传材料本身**。
- 对保留原车功能、后台运行和安装包共存的要求。

## 14. ADB 不能完成的验证：后续 P0.5 探针门槛

“收集全部信息”需要区分两层：

1. **本轮可收集的 ADB 和人工信息完整**：执行必需步骤，缺失字段有明确原因；可选项允许标注延期。
2. **运行可行性全部验证**：有些事实必须由普通应用在设备上操作才能得知，不能从日志或配置文件推断。

在第一层结果审查完成前，不开始正式兼容改造；如果需要探针，先列出最小方案并单独请你确认，不把探针开发视为已经获准。

| 后续验证 | 为什么不能由本清单替代 | 需要的行为及授权 |
| --- | --- | --- |
| API 23 测试包安装 | ADB 在线/存在第三方应用不能证明新包允许安装 | 提供已审查的包名、源码、权限、签名和哈希后，经批准安装 |
| USB permission / openDevice | shell 与普通应用 UID、授权机制不同 | 探针前台申请 USB 权限，记录授权与打开结果 |
| CarPlay vendor request 与重枚举 | 只读命令不会发送切换请求 | 经确认发送请求，记录返回、detach/attach、重新授权和描述符变化 |
| 配置选择与 claim | 节点存在不代表接口可占用 | 按接口描述符发现，不固定 config ID 6；记录选择及 claim 结果 |
| USBMUX/NCM 实际传输 | 枚举不等于数据链路通 | 使用 API 23 兼容传输，验证超时、短读、退出和协议完整性 |
| H.264 解码 | XML 声明不保证 HAL 和 Surface 正常 | MediaCodecList 查询、选定规格样本解码及性能测量 |
| AudioTrack/AudioRecord | 路由存在不保证应用能播放/录音 | 用户授权，短时播放/录音；默认不留存录音，不上传音频 |
| VPN/TUN | 文件节点和服务声明不能证明 establish 成功 | 用户同意系统 VPN 授权，验证建立、释放和网络影响 |
| 配件认证及 iPhone 接受 | 与 Android API 兼容是不同问题 | 使用合法配置在实际会话验证；认证失败单独分类 |

USB 探针后续设计必须记录：

- 初始与重枚举后的 VID/PID、配置列表、接口 class/subclass/protocol、端点。
- 授权是否授予、是否能够 `openDevice()`、每次请求返回值与耗时。
- 是否收到移除/重新连接事件，是否需要再次授权。
- 当前本地源码的 vendor request 参数来自 `IphoneUsbHost.kt:149`；它是后续受控实验，不提供 shell/sysfs 写入替代方案。
- `setConfiguration` 的返回值与后续 claim/真实传输结果分别记录，不能只看一个布尔值就宣告成功。
- 失败时先区分线材、权限、原车占用、协议和驱动，不立即转向修改内核。

## 15. 回传顺序与开始条件

### 推荐回传批次

1. **A 批：** `00_*`、`01_*`、`02_*`＋系统/MCU 信息＋ADB 连接拓扑。
2. **B 批：** `03_*`、`codecs/`、`04_*`＋iPhone 插拔观察。这是可行性判断重点。
3. **C 批：** `05_*`、`06_*`、`audio/`＋人工补充信息。
4. **D 批（可选）：** `07_*`、`08_*`；不影响有线首期的部分可以写明延期。

不要一次粘贴全部日志到聊天。先提供结果模板和相应小批文件；大日志只在需要时回传。原始输出留在本机，不必提交到 Git 或复制到公开的 `docs` 目录。

### 开始条件

- [ ] 必需采集项已完成；命令失败与信息缺失有明确记录。
- [ ] Android/API/ABI 和目标设备身份一致。
- [ ] USB 插拔证据已审查；内核、框架、应用权限三层没有混为一谈。
- [ ] 编解码和音频的线索与待探针项目已明确。
- [ ] 安装限制、iPhone/iOS、运行认证前提已明确或列为阻塞项。
- [ ] 所有不能靠 ADB 验证的关键点均有下一步验证办法，而不是被标记为通过。
- [ ] 用户确认下一步是补采、制作最小探针，还是在探针结果通过后进入正式改造。

**当前结论：等待采集结果，不开始修改 DiPlay。**
