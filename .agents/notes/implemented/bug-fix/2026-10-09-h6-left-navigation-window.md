# Agent Note: H6 左侧系统导航栏与应用窗口

Status: implemented

## Problem

IHU01 系统导航栏覆盖 x=0–120。厂商PhoneWindowManager.layoutWindowLw只给com.baidu.naviauto等包硬编码left=120，普通应用窗口和insets均跨到x=0。DiPlay及高德左侧按钮被截获。用户要求CarPlay保持1280×720并隐藏原车栏，高德也隐藏原车栏。

## Decision

当前root调试用系统policy_control显式包过滤器，为DiPlay车测包、高德及QQ音乐车机版（com.tencent.qqmusiccar）隐藏导航栏；原值及恢复命令记录在[实车调试记录](../../../../docs/android_6/DEBUG_2026-10-09.md)。用户已确认CarPlay应用切换、画面及音频正常，并手动确认QQ音乐隐藏侧栏后的左侧按钮可点击。QQ音乐通过设置底部“关闭QQ音乐”弹窗的“进入后台”返回原车桌面，随后可由已有桌面卡片再次进入；不新增悬浮按钮或依赖未生效的边缘手势。应用不获取系统写权限、不自动写全局策略；包过滤器同时影响DiPlay主页，不能区分同包Activity。

code35保留CarPlay既有全屏偏好和videoView尺寸/触摸映射。主页及裁剪页采用H6SystemBars：清除半透明和覆盖系统栏的布局标志，若没有显式隐藏本包导航栏的策略，则按系统configuration.screenWidthDp换算可用像素宽、窗口靠右；若ADB策略已隐藏本包导航栏，则使用MATCH_PARENT避免空出左边。主页在渲染、恢复和获得焦点时应用。其他profile保持原行为，不硬编码120，不改变wm size/overscan。上游标签、认证门禁、USB读泵和静音输入保持。

## Alternatives considered

- 只按百度地图预留左栏：按钮能避让，但投屏无法满足用户全屏要求；仅作为主页在没有ADB隐藏策略时的回退。
- 只清除全屏标志和调用setDecorFitsSystemWindows(true)：是通用窗口入口，但code34实车仍分配全宽，且厂商只对指定包偏移；需要按配置宽度定位主页，并单独验证隐藏系统栏的策略。
- 修改全局wm size/overscan：可覆盖所有应用，但会改变原车布局；改用显式包过滤器。
- 修改厂商服务或系统APK的包名白名单：能自动获得百度地图行为，但需要改系统镜像；只读分析，使用现有系统策略。
- 向应用授予WRITE_SECURE_SETTINGS并自动切换策略：可按Activity区分，但增加应用权限和全局设置恢复维护；本轮保持ADB配置及应用只读判断。
- 沉浸式边缘滑动唤出系统主页按钮：可以避免增加返回入口，但本机左边缘、右边缘和底边的ADB滑动均未唤出导航栏；QQ音乐已有“进入后台”入口，直接复用，无需另装悬浮按钮应用。

## Testing

API23回归覆盖沉浸标志清理、重复应用、状态栏显示切换、KEEP_SCREEN_ON保留、可用宽度变化、双包隐藏策略及撤销后恢复预留。527项回归中526通过、1项既有跳过；普通构建/lint、standalone和API23专项通过。实车窗口、策略和用户反馈见[调试记录](../../../../docs/android_6/DEBUG_2026-10-09.md)。code34完成CarPlay应用切换、画面和音频验收；高德左侧搜索按钮点击后进入搜索页。code35覆盖安装成功，用户确认按钮和尺寸符合要求后断开ADB；code35新会话音频未单独验收，各包证据分开记录。

## Consequences

系统策略解决CarPlay侧栏被拦截且不改变其他包；主页回退允许无ADB策略时避让左栏。代价是系统策略依赖所有者先通过ADB设置，包名变更需要同步过滤器；只有包粒度，DiPlay主页也隐藏系统栏。QQ音乐返回桌面需要经过设置和关闭弹窗选择“进入后台”，已有卡片无需改动；QQ音乐窗口仍为1280×720，系统栏隐藏与用户触摸验证均通过，返回后的只读焦点采样为原车桌面，重启和重复进入后的验收仍需后续确认。当前音频内核调优重启失效，导航栏设置保留，二者生命周期不同。普通UID无root音频抗溢出与长期车测仍独立跟踪。

## Related-note audit

[H6兼容](../feature/2026-10-01-h6-api23-wired-compatibility.md)和[同步契约](../process/2026-10-03-upstream-0-2-10-h6-sync.md)部分重叠，保留API23和单屏触摸映射。音频候选、NCM及USB释放笔记与本次窗口决定无关；临时安全窗口决定无关，本轮不修改安全服务。无完全吸收或过时窗口提案。
