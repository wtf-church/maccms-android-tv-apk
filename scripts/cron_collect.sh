#!/bin/sh
# 每日/每周采集调度：cron 调用，依赖 collect_ffzy.php 内部 flock 防重叠
# 每日 2026 增量（约 2-4 分钟）；周日全量 2006-2026（约 2-3 小时）
YEAR=$(TZ=Asia/Shanghai date +%Y)
LOG=/www/wwwroot/maccms-client-tv/scripts/collect.log
DOW=$(TZ=Asia/Shanghai date +%w)

if [ "$DOW" = "0" ]; then
    php /www/wwwroot/maccms-client-tv/scripts/collect_ffzy.php 2006 2026 >> "$LOG" 2>&1
else
    php /www/wwwroot/maccms-client-tv/scripts/collect_ffzy.php "$YEAR" "$YEAR" >> "$LOG" 2>&1
fi
