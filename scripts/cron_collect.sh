#!/bin/sh
# 每日定时采集：
#   1) 当年全量增量（约 8-10 分钟，保证当天上新）
#   2) 历史年推进：倒序循环（今年-1 → 2006，每天推进一年，约 2-5 分钟），
#      逐年把存量缺口补完；扫完一圈后回到今年-1 继续轮转
#   周日：2006-2026 全量 + 无年份全量补齐（约 2-3 小时），完成后不重置进度
BASE=/www/wwwroot/maccms-client-tv/scripts
LOG=$BASE/collect.log
YEAR=$(TZ=Asia/Shanghai date +%Y)
DOW=$(TZ=Asia/Shanghai date +%w)
PROG=$BASE/collect_progress
OLDEST=2006
START=$((YEAR - 1))

if [ "$DOW" = "0" ]; then
    # 周日：按年全量 + 无年份补齐
    php $BASE/collect_ffzy.php 2006 2026 >> "$LOG" 2>&1
    php $BASE/collect_ffzy.php --no-year >> "$LOG" 2>&1
    exit 0
fi

# 1) 当年增量
php $BASE/collect_ffzy.php "$YEAR" "$YEAR" >> "$LOG" 2>&1

# 2) 历史年推进（倒序）
NEXT=$(cat "$PROG" 2>/dev/null)
case "$NEXT" in
    *[!0-9]*|'') NEXT=$START ;;
esac
php $BASE/collect_ffzy.php "$NEXT" "$NEXT" >> "$LOG" 2>&1
NEXT=$((NEXT - 1))
[ "$NEXT" -lt "$OLDEST" ] && NEXT=$START
echo "$NEXT" > "$PROG"
