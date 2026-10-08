# Agent Note: H6 静音双向音频协商候选

Status: implemented

## Problem

code32 画面正常，手机控制中心无 DiPlay/CarPlay 输出；有效会话只有 type110，没有媒体音频 SETUP 或 AudioTrack。H6 固定 microphone=false，/info 因此完全省略 audioInputFormats。公开 [DiPlay #288](https://github.com/shihabal3amri/DiPlay/issues/288) 在有线、无蓝牙设备条件下复现同样症状：撤销麦克风权限后音频留在手机。此证据支持输入声明是可验证线索，尚不证明本车根因。

## Decision

code33 候选以 code32 完整兼容快照为基线，2026-10-08按用户指定同步到主项目 `/Users/lanyu/IdeaProjects/DiPlay`，仅 H6 profile 开启 silentAudioInput。/info 为 compatibility/default/telephony/speechRecognition 的 type100 提供 PCM mono 输入格式；输出、资源所有权和视频声明保持原值。真实 microphone=false 和权限路径保持关闭，不创建 AudioRecord。收到有效 type100 PCM 输入端口并开始下行时，使用原输入密钥、RTP计数和认证布局发送零值PCM；结束流、替换流或关闭会话均由现有 sink 生命周期关闭线程和socket。静音路径拒绝未声明的Opus，不广告静音Opus能力。

这是一项可安装对照候选；implemented 指源码和电脑验证落地，不代表实车根因或修复已经验收。主项目逐项纳入音频修改和code32的NCM前置修复，已有用户改动保留。认证输入、同签名、API23/ARM32、code32可选NCM边界保持。

## Alternatives considered

- 开启真实麦克风并请求权限：最接近上游正常双向配置，但超出H6一期关闭麦克风的既定范围，且解决播放不需要采集车内声音；选择静音输入进行隔离验证。
- 只增加 inputFormats、不实现输入发送：改动最小，可测试路由创建，但手机可能建立默认/语音输入流；静音能力声明应有对应零值RTP实现。
- 主动 changeModes 或调整车机音量/焦点：资源模式请求可以改变音频所有权，但当前音乐播放时 iOS 已取得 mainAudio、没有实际音轨，且上游有直接输入能力对照证据；先验证输入声明。
- 收窄媒体PCM格式：LIVI #358 已证明能解决通话后8kHz音乐，但本车没有任何音频SETUP，属于另一症状；本次保持输出格式，避免混入第二变量。

## Testing

API23/29 的UDP回环测试验证连续静音包可用输入密钥解密为零值、sequence和timestamp推进、recorder为空、关闭幂等并停止发送；未声明压缩静音被拒绝。能力测试覆盖输出不变、PCM输入范围及disableAudioOutput。2026-10-08主项目525项测试、524通过/1项既有跳过、0失败/错误；普通/standalone构建、lint/API23和版本/笔记门禁通过，签名/认证输入与code32一致，尚未车测。电脑构建和门禁结果见主项目 [音频同步交接](../../../../docs/android_6/AUDIO_2026-10-08.md)。

## Consequences

若手机需要输入能力才能建立CarPlay音频路由，静音双向协商可以保持音乐/导航输出且不采集声音。代价是维护独立能力位和按需静音发送线程；它不能提供Siri/通话麦克风功能，不能保证所有iOS接受无Opus输入的组合。实车须确认输出列表、音频SETUP、首个PCM、车机可听见音乐及导航，未通过时保持或恢复code32；不得把电脑测试当作实车成功。

## Related-note audit

[音频边界诊断](2026-10-04-h6-carplay-audio-boundary-diagnostics.md)部分重叠，继续保持有界诊断，以新输入能力对照扩展排查；[NCM修复](2026-10-07-h6-ncm-optional-pad.md)与当前音频决定无关且保留。[H6兼容](../feature/2026-10-01-h6-api23-wired-compatibility.md)部分重叠：真实麦克风仍关闭，增加协议静音输入不放宽权限；[同步契约](../process/2026-10-03-upstream-0-2-10-h6-sync.md)继续约束API23/单一焦点/代次隔离。rejected USB接管提案无关，不重启旧接管路线。
