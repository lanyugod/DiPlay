# 本地采集证据

`2026-10-01-batch1-local/` 保存用户批准的系统、USB、codec 查询及电脑端依赖/Manifest 检查；`2026-10-01-batch2-local/` 保存用户批准的音频、TUN 与应用候选查询。

`2026-10-01-conversation-import-local/`保存通过read_thread取得的两份历史用户附件，以及会话ID、导入时间和文件SHA256。这是历史资料导入，不是再次执行ADB。原会话审计复核见[CONVERSATION_REVIEW.md](../CONVERSATION_REVIEW.md)。

文件包含 Asia/Shanghai 时间、具体命令、退出码和输出。目录由本层 `.gitignore` 排除；当前工作目录本身尚无 Git。原始 dumpsys 可能包含设备标识，分享或打包前需审阅脱敏。对外设计只引用 [验证结果摘要](../VALIDATION_RESULTS.md)，不附原始序列号或整份设备快照。
