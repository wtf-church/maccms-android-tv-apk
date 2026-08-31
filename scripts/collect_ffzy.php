#!/usr/bin/env php
<?php
/**
 * 采集脚本：从非凡资源站(ffzy)按 分类+年份 分块采集近20年内容入库 maccms10
 *
 * 用法（远程）:
 *   php collect_ffzy.php [start_year] [end_year]            # 全量
 *   php collect_ffzy.php 2026 2026                          # 只采2026
 *   php collect_ffzy.php 2020 2026 t=13,t=2                 # 指定分类
 *   php collect_ffzy.php --dry 2020 2020 t=13               # 只打印不写库
 */

error_reporting(E_ALL & ~E_DEPRECATED & ~E_NOTICE);

// ---- 配置 ----
$DB = [
    'host'    => 'localhost',
    'user'    => 'root',
    'pass'    => 'MaccmsR00t@2026',
    'name'    => 'maccms10',
    'charset' => 'utf8mb4',
];
$SRC_URL      = 'https://cj.ffzyapi.com/api.php/provide/vod/';
$CJFLAG_PREFIX = '7b1d09368af7a13806e1a8e1e43850e5_';   // md5($SRC_URL)
$SLEEP        = 0.25;      // 每请求间隔(秒), 防源站限流
$PAGE_SIZE    = 20;        // 源站固定 20 条/页，limit 参数无效

// 排除的低质分类（源站 type_id）：记录片、伦理片、电影解说、AI漫剧
$EXCLUDE_TYPES = [20, 34, 35, 45, 52];

// 源站分类 → 本地分类兜底（bind.php 有映射则以 bind 为准；无则由子类归父类）
$TYPE_FALLBACK = [
    1 => 1, 2 => 2, 3 => 3, 4 => 4, 36 => 5,
    6 => 1, 7 => 1, 8 => 1, 9 => 1, 10 => 1, 11 => 1, 12 => 1,
    13 => 2, 14 => 2, 15 => 2, 16 => 2, 21 => 2, 22 => 2, 23 => 2, 24 => 2,
    25 => 3, 26 => 3, 27 => 3, 28 => 3,
    29 => 4, 30 => 4, 31 => 4, 32 => 4, 33 => 4,
];

// ---- 参数 ----
$dry = false;
$argv = array_slice($argv, 1);
if (in_array('--dry', $argv)) { $dry = true; $argv = array_diff($argv, ['--dry']); }
$argv = array_values(array_filter($argv));

$startYear = 2006; $endYear = 2026; $onlyTypes = null;
foreach ($argv as $a) {
    if (preg_match('/^t=(.+)$/', $a, $m)) {
        $onlyTypes = array_map('intval', explode(',', $m[1]));
    } elseif (preg_match('/^\d{4}$/', $a)) {
        if ($startYear === 2006) $startYear = (int)$a;
        else $endYear = (int)$a;
    } else {
        fwrite(STDERR, "unknown arg: $a\n"); exit(1);
    }
}
$startYear = max(2000, min($startYear, 2026));
$endYear   = min(2026, max($endYear, $startYear));

echo date('Y-m-d H:i:s') . " start years={$startYear}-{$endYear} dry=" . ($dry ? '1' : '0') . PHP_EOL;

// ---- DB ----
$mysqli = @new mysqli($DB['host'], $DB['user'], $DB['pass'], $DB['name']);
if ($mysqli->connect_errno) {
    fwrite(STDERR, "mysqli connect failed: {$mysqli->connect_error}\n");
    exit(1);
}
$mysqli->set_charset($DB['charset']);

// 本地分类父子映射（type_id_1 用）
$typePid = [];
$rs = $mysqli->query("SELECT type_id, type_pid FROM mac_type");
while ($row = $rs->fetch_assoc()) $typePid[(int)$row['type_id']] = (int)$row['type_pid'];

// 分类绑定映射（bind.php 优先）
$bind = [];
$bindFile = '/www/wwwroot/maccms/application/extra/bind.php';
if (is_file($bindFile)) {
    $bind = include $bindFile;
}

// ---- 工具 ----
function fetch_json($url, $timeout = 20, $retries = 3) {
    for ($i = 0; $i < $retries; $i++) {
        $ch = curl_init($url);
        curl_setopt_array($ch, [
            CURLOPT_RETURNTRANSFER => true,
            CURLOPT_SSL_VERIFYPEER => false,
            CURLOPT_SSL_VERIFYHOST => false,
            CURLOPT_TIMEOUT        => $timeout,
            CURLOPT_USERAGENT      => 'Mozilla/5.0 (compatible; maccms-collect/1.0)',
            CURLOPT_ENCODING       => '',
        ]);
        $html = curl_exec($ch);
        $err  = curl_error($ch);
        curl_close($ch);
        if ($html !== false && $html !== '') {
            $json = json_decode($html, true);
            if (is_array($json) && isset($json['list'])) {
                return $json;
            }
            fwrite(STDERR, "  bad json from $url\n");
        } else {
            fwrite(STDERR, "  curl err: $err ($url)\n");
        }
        sleep(2 * ($i + 1));
    }
    return null;
}

function fmt_play_from($v) {
    if (!empty($v['vod_play_from'])) {
        // 兼容逗号/$$$分隔，统一为 $$$
        return str_replace(',', '$$$', trim($v['vod_play_from']));
    }
    if (!empty($v['dl']) && is_array($v['dl'])) {
        return implode('$$$', array_keys($v['dl']));
    }
    return '';
}

function fmt_play_url($v) {
    if (!empty($v['vod_play_url'])) {
        return $v['vod_play_url'];
    }
    if (!empty($v['dl']) && is_array($v['dl'])) {
        return implode('$$$', array_values($v['dl']));
    }
    return '';
}

// ---- 主循环 ----
// 源站分类列表
$srcTypes = [];
if ($onlyTypes) {
    $srcTypes = $onlyTypes;
} else {
    $info = fetch_json($SRC_URL . '?ac=list');
    if (!$info) { fwrite(STDERR, "cannot fetch source type list\n"); exit(1); }
    foreach ($info['class'] as $c) {
        if (isset($c['type_id'])) $srcTypes[] = (int)$c['type_id'];
    }
}

$totalNew = 0; $totalUpd = 0; $totalSkip = 0;

foreach ($srcTypes as $srcT) {
    if (in_array($srcT, $EXCLUDE_TYPES, true)) continue;

    // 本地分类
    $local = isset($bind[$CJFLAG_PREFIX . $srcT]) ? (int)$bind[$CJFLAG_PREFIX . $srcT] : 0;
    if (!$local) $local = isset($TYPE_FALLBACK[$srcT]) ? $TYPE_FALLBACK[$srcT] : 0;
    if (!$local) { echo "[t=$srcT] no local mapping, skip\n"; continue; }
    $localPid = isset($typePid[$local]) ? $typePid[$local] : 0;

    foreach (range($startYear, $endYear) as $year) {
        $pg = 1; $pageCount = 0; $items = 0; $new = 0; $upd = 0; $skip = 0;
        $t0 = microtime(true);
        while (true) {
            $url = $SRC_URL . '?ac=videolist&t=' . $srcT . '&year=' . $year . '&pg=' . $pg;
            $json = fetch_json($url);
            if (!$json) { echo "[t=$srcT y=$year] fetch fail at pg=$pg, abort block\n"; break; }
            if ($pageCount === 0) {
                $pageCount = (int)$json['pagecount'];
                if ($pageCount <= 0 || empty($json['list'])) break;
            }
            foreach ($json['list'] as $item) {
                $items++;
                $name = trim(isset($item['vod_name']) ? $item['vod_name'] : '');
                $playUrl = fmt_play_url($item);
                if ($name === '' || $playUrl === '') { $skip++; continue; }

                if ($dry) continue;

                // 幂等：按名称查重
                $stmt = $mysqli->prepare("SELECT vod_id FROM mac_vod WHERE vod_name = ? LIMIT 1");
                $stmt->bind_param('s', $name);
                $stmt->execute();
                $res = $stmt->get_result();
                $row = $res->fetch_assoc();
                $stmt->close();
                $exists = $row ? (int)$row['vod_id'] : 0;

                $playFrom = fmt_play_from($item);
                $playNote = isset($item['vod_play_note']) ? $item['vod_play_note'] : '';
                $playServer = isset($item['vod_play_server']) ? $item['vod_play_server'] : '';
                $content   = isset($item['vod_content']) ? (string)$item['vod_content'] : '';
                $blurb     = mb_substr(trim(strip_tags($content)), 0, 100, 'UTF-8');
                $en        = isset($item['vod_en']) ? trim($item['vod_en']) : '';
                $letter    = $en !== '' ? strtoupper(substr($en, 0, 1)) : 'Z';
                $yearVal   = (int)(isset($item['vod_year']) ? $item['vod_year'] : 0);
                if ($yearVal === 0) $yearVal = $year;
                $score     = (float)(isset($item['vod_score']) ? $item['vod_score'] : 0);
                $now = time();
                $hits = rand(1, 1000);

                $cols = [
                    'type_id' => $local, 'type_id_1' => $localPid,
                    'vod_name' => $name,
                    'vod_sub' => isset($item['vod_sub']) ? $item['vod_sub'] : '',
                    'vod_en' => $en,
                    'vod_status' => 1,
                    'vod_letter' => $letter,
                    'vod_class' => isset($item['vod_class']) ? trim($item['vod_class'] . ',' . (isset($item['type_name']) ? $item['type_name'] : ''), ', ') : (isset($item['type_name']) ? $item['type_name'] : ''),
                    'vod_pic' => isset($item['vod_pic']) ? $item['vod_pic'] : '',
                    'vod_actor' => isset($item['vod_actor']) ? $item['vod_actor'] : '',
                    'vod_director' => isset($item['vod_director']) ? $item['vod_director'] : '',
                    'vod_remarks' => isset($item['vod_remarks']) ? $item['vod_remarks'] : '',
                    'vod_area' => isset($item['vod_area']) ? $item['vod_area'] : '',
                    'vod_lang' => isset($item['vod_lang']) ? $item['vod_lang'] : '',
                    'vod_year' => $yearVal,
                    'vod_serial' => isset($item['vod_serial']) ? (string)$item['vod_serial'] : '',
                    'vod_state' => isset($item['vod_state']) ? $item['vod_state'] : '',
                    'vod_duration' => isset($item['vod_duration']) ? $item['vod_duration'] : '',
                    'vod_isend' => 0,
                    'vod_level' => 0,
                    'vod_lock' => 0,
                    'vod_hits' => $hits, 'vod_hits_day' => $hits, 'vod_hits_week' => $hits, 'vod_hits_month' => $hits,
                    'vod_score' => $score,
                    'vod_blurb' => $blurb,
                    'vod_time' => $now,
                    'vod_time_add' => $now,
                    'vod_content' => $content,
                    'vod_play_from' => $playFrom, 'vod_play_server' => $playServer, 'vod_play_note' => $playNote,
                    'vod_play_url' => $playUrl,
                ];

                if ($exists) {
                    $set = []; $params = []; $types = '';
                    foreach ($cols as $k => $val) { $set[] = "$k = ?"; $params[] = $val; $types .= 's'; }
                    $params[] = $exists; $types .= 'i';
                    $sql = "UPDATE mac_vod SET " . implode(', ', $set) . " WHERE vod_id = ?";
                    $stmt = $mysqli->prepare($sql);
                    $stmt->bind_param($types, ...$params);
                    $stmt->execute();
                    $stmt->close();
                    if ($mysqli->errno) { fwrite(STDERR, "update err {$mysqli->error}: $name\n"); $skip++; continue; }
                    $upd++;
                } else {
                    $colNames = array_keys($cols);
                    $sql = "INSERT INTO mac_vod (" . implode(',', $colNames) . ") VALUES (" . implode(',', array_fill(0, count($cols), '?')) . ")";
                    $stmt = $mysqli->prepare($sql);
                    $types = str_repeat('s', count($cols));
                    $stmt->bind_param($types, ...array_values($cols));
                    $stmt->execute();
                    $stmt->close();
                    if ($mysqli->errno) { fwrite(STDERR, "insert err {$mysqli->error}: $name\n"); $skip++; continue; }
                    $new++;
                }
            }
            if ($pg >= $pageCount) break;
            $pg++;
            sleep($SLEEP);
        }
        $elapsed = round(microtime(true) - $t0, 1);
        echo sprintf("[t=%d y=%d] pages=%d items=%d new=%d upd=%d skip=%d %.1fs\n",
            $srcT, $year, $pageCount, $items, $new, $upd, $skip, $elapsed);
        $totalNew += $new; $totalUpd += $upd; $totalSkip += $skip;
    }
}

echo date('Y-m-d H:i:s') . " done: new=$totalNew upd=$totalUpd skip=$totalSkip\n";
$mysqli->close();
