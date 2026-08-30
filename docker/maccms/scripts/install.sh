#!/usr/bin/env bash
# maccms 离线初始化：绕过 Web 安装器，直接 SQL 导入 + 预写数据库配置
# 1. 等 MySQL 就绪
# 2. 建库导入 install.sql（建表）+ initdata.sql（分类）+ demo.sql（演示影片）
# 3. 写入 application/database.php（DB 连接）
# 4. 写入演示管理员（md5 兼容格式）
# 5. 写入安装锁 install.lock
# 6. 启动 php-fpm + nginx
set -euo pipefail

APP_DIR=/var/www/maccms
export MAC_DB_HOST="${DB_HOST:-mysql}"
export MAC_DB_PORT="${DB_PORT:-3306}"
export MAC_DB_NAME="${DB_NAME:-maccms10}"
export MAC_ADMIN_USER="${ADMIN_USER:-admin}"
export MAC_ADMIN_PASS="${ADMIN_PASS:-admin123}"
export MAC_APP_DIR="$APP_DIR"

wait_for_db() {
  echo "[install] waiting for MySQL at ${MAC_DB_HOST}:${MAC_DB_PORT} ..."
  for i in $(seq 1 60); do
    if php -r '
      $h = getenv("MAC_DB_HOST"); $p = (int)getenv("MAC_DB_PORT");
      $ctx = @stream_socket_client("tcp://{$h}:{$p}", $errno, $errstr, 2);
      if ($ctx) { fclose($ctx); exit(0); }
      exit(1);
    ' 2>/dev/null; then
      echo "[install] MySQL is up"
      return 0
    fi
    sleep 2
  done
  echo "[install] ERROR: MySQL not reachable" >&2
  exit 1
}

import_sql() {
  local sql_file="$1"
  MAC_SQL_FILE="$sql_file" php -r '
    $pdo = new PDO("mysql:host=".getenv("MAC_DB_HOST").";port=".(int)getenv("MAC_DB_PORT").";dbname=".getenv("MAC_DB_NAME").";charset=utf8mb4", "root", "");
    $sql = file_get_contents(getenv("MAC_SQL_FILE"));
    // 去掉行注释与块注释
    $sql = preg_replace("/^--.*$/m", "", $sql);
    $sql = preg_replace("/\/\*.*?\*\//s", "", $sql);
    $statements = preg_split("/;\s*(?:\r?\n|$)/", $sql);
    foreach ((array)$statements as $stmt) {
      $stmt = trim($stmt);
      if ($stmt === "") continue;
      try {
        $pdo->exec($stmt);
      } catch (Exception $e) {
        fwrite(STDERR, "SQL error in ".basename(getenv("MAC_SQL_FILE")).": ".$e->getMessage()."\n");
        exit(1);
      }
    }
    echo "  imported ".basename(getenv("MAC_SQL_FILE"))."\n";
  ' || exit 1
}

main() {
  echo "[install] ===== maccms offline install start ====="
  wait_for_db

  php -r '
    $pdo = new PDO("mysql:host=".getenv("MAC_DB_HOST").";port=".(int)getenv("MAC_DB_PORT"), "root", "");
    $pdo->exec("CREATE DATABASE IF NOT EXISTS `".getenv("MAC_DB_NAME")."` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci");
    echo "  database ".getenv("MAC_DB_NAME")." ready\n";
  ' || { echo "[install] ERROR: cannot create database" >&2; exit 1; }

  import_sql "${APP_DIR}/application/install/sql/install.sql"
  import_sql "${APP_DIR}/application/install/sql/initdata.sql"
  import_sql "${APP_DIR}/initdb/demo.sql"

  MAC_DB_PORT_INT="$MAC_DB_PORT" php -r '
    $db = getenv("MAC_DB_NAME"); $port = (int)getenv("MAC_DB_PORT");
    $code = "<?php\nreturn [\n".
      "    \x27type\x27 => \x27mysql\x27,\n".
      "    \x27hostname\x27 => \x27".getenv("MAC_DB_HOST")."\x27,\n".
      "    \x27database\x27 => \x27{$db}\x27,\n".
      "    \x27username\x27 => \x27root\x27,\n".
      "    \x27password\x27 => \x27\x27,\n".
      "    \x27hostport\x27 => \x27{$port}\x27,\n".
      "    \x27dsn\x27 => \x27\x27,\n".
      "    \x27params\x27 => [],\n".
      "    \x27charset\x27 => \x27utf8mb4\x27,\n".
      "    \x27prefix\x27 => \x27mac_\x27,\n".
      "    \x27debug\x27 => false,\n".
      "    \x27deploy\x27 => 0,\n".
      "    \x27rw_separate\x27 => false,\n".
      "    \x27master_num\x27 => 1,\n".
      "    \x27slave_no\x27 => \x27\x27,\n".
      "    \x27fields_strict\x27 => false,\n".
      "    \x27resultset_type\x27 => \x27array\x27,\n".
      "    \x27auto_timestamp\x27 => false,\n".
      "    \x27datetime_format\x27 => \x27Y-m-d H:i:s\x27,\n".
      "    \x27sql_explain\x27 => false,\n".
      "    \x27builder\x27 => \x27\x27,\n".
      "    \x27query\x27 => \x27\\think\\db\\Query\x27,\n".
      "];\n";
    file_put_contents(getenv("MAC_APP_DIR")."/application/database.php", $code);
    echo "  database.php written\n";
  '

  php -r '
    $pdo = new PDO("mysql:host=".getenv("MAC_DB_HOST").";port=".(int)getenv("MAC_DB_PORT").";dbname=".getenv("MAC_DB_NAME").";charset=utf8mb4", "root", "");
    $pwd = md5(getenv("MAC_ADMIN_PASS"));
    $stmt = $pdo->prepare("INSERT INTO mac_admin (admin_name, admin_pwd, admin_random, admin_status, admin_auth, admin_login_time, admin_login_ip, admin_login_num, admin_last_login_time, admin_last_login_ip) VALUES (?, ?, \x27\x27, 1, \x27\x27, 0, 0, 0, 0, 0)");
    $stmt->execute([getenv("MAC_ADMIN_USER"), $pwd]);
    echo "  admin user created (".getenv("MAC_ADMIN_USER")."/".getenv("MAC_ADMIN_PASS").")\n";
  '

  date '+%Y-%m-%d %H:%M:%S' > "${APP_DIR}/application/data/install/install.lock"
  echo "  install.lock written"
  echo "[install] ===== maccms install complete ====="
}

main "$@"

# 启动 php-fpm + nginx
php-fpm -D
/usr/sbin/nginx -g 'daemon off;'
