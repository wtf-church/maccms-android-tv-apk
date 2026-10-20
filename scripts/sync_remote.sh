#!/bin/bash
# 用 SSH heredoc 方式把本地 Provide.php 同步到远程（绕过 scp 限制）
ssh -i ~/.ssh/pipo_test -o BatchMode=yes -o ConnectTimeout=25 root@120.25.76.65 "cat > /www/wwwroot/maccms/application/api/controller/Provide.php" < "D:/projects/maccms-client-tv/docker/maccms/src/application/api/controller/Provide.php" 2>&1 | grep -v "WARNING\|post-quantum\|store now\|may need"
echo "SYNC_EXIT=$?"
