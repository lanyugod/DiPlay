# Agent Note: H6 CarPlay 音频与 NCM 边界诊断

Status: implemented

## Problem

用户固定线缆后画面持续更久，但音乐从iPhone播放且手机没有DiPlay/CarPlay输出选项；主体只见屏幕流SETUP，没有媒体音频流或AudioTrack。多个会话以Invalid NCM short-packet pad结束，现有错误没有边界数据，不能直接认定全部是线缆故障。

## Decision

补充modesChanged中已知音频/屏幕资源的数值ID与owner/entity/borrower/transferType，最多8项，不输出未知键或协议内容。NCM错误只记录块长、缓存长和边界最多4字节标识，保持原严格解析，不放松损坏检测。先以实车证据决定音频协商或NCM边界修正，不启用麦克风或额外车辆路由。

## Alternatives considered

- 直接修改音轨或音量：手机未建立音频流，没有音轨可修，先查协商。
- 将所有NCM失败归于线缆：插头固定后仍见同一错误，需要边界证据。
- 输出完整协议/音频数据：内容过多且含私密信息，限制数值资源与边界标识。

## Testing

code31诊断包API23构建、shared/common回归及lint通过，签名与选定认证资产校验通过。用户今天无法换线，要求保存结果；code31单独保存，未安装、未宣称实车诊断字段验收或音频已修复。车机保留已验证接口释放的code30。换原厂线后先记录音频资源和稳定性，再决定修复路线。

## Consequences

诊断不能直接保证有声或稳定连接；需要手机继续播放和实车验证。[授权fd接口释放](../../implemented/feature/2026-10-04-h6-usb-fd-disconnect.md)已证明配置切换及首帧，此问题独立跟踪。

## Existing note audit

H6兼容的严格NCM边界与本篇部分重叠互链；手动接口释放方案部分重叠，释放有效且不改变默认授权；其他安全/安装记录无关。

用户报告手机控制中心只有iPhone输出，没有DiPlay/CarPlay；声音在手机上播放且CarPlay进度无法拖动。现有日志只有type110屏幕SETUP，系统没有主体媒体音轨，暂不改音量/路由、音频格式或启用麦克风。线缆关联是强线索，尚未通过替换对照证明唯一原因。

输入能力对照候选见[H6静音音频输入](2026-10-07-h6-silent-audio-input.md)，真实麦克风仍关闭；是否修复输出路由需要实车验证。3584字节块后直接出现合法NCMH的已证实软件缺陷见[NCM可选填充](2026-10-07-h6-ncm-optional-pad.md)。
