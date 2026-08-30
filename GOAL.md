# GOAL：华为电视 maccms 客户端（Netflix 风格）+ 全 Docker 开发环境

> 本文件是自包含任务书。以 goal/新会话形式运行本任务时，先完整读取本文件再执行。
> 执行环境：Windows 10 + Docker Desktop（WSL2 后端），shell 为 Git Bash（`D:\projects\maccms-client-tv`）。

---

## 1. 项目目标

为华为智慧屏开发一个视频浏览客户端：
- **数据源**：自建 maccms V10（苹果 CMS / ThinkPHP5）站点，API 为 `api.php/provide/vod`
- **客户端**：Android TV 应用，Kotlin + Jetpack Compose for TV，**UI 完全仿照 Netflix TV 版**（顶部导航 + Hero 横幅 + 横向海报行 + 遥控焦点缩放）
- **播放**：Media3 ExoPlayer（演示用本地 MP4，兼容 m3u8）
- **开发环境**：全部容器化（maccms / MySQL / Android TV 模拟器 / 构建工具链 四个容器），模拟器无头运行在 Docker 内，`adb` 远程安装验证

## 2. 已确认的决策（不要推翻）

| 决策点 | 结论 |
|---|---|
| 目标机型 | 华为智慧屏，系统为兼容安卓的 HarmonyOS 2/3 **32 位**（armv7），**能安装 32 位 APK**（用户实测确认） |
| 客户端技术栈 | **Android TV**（Kotlin + Compose for TV），**不是**鸿蒙 HAP |
| 模拟器 | `system-images;android-30;android-tv;x86`（官方 TV 形态 32 位镜像；API 21~36 均有 TV x86 镜像，API 30 已验证存在） |
| 后端 | maccms V10 源码（GitHub magicblack/maccms10，Apache-2.0）+ PHP **7.2.34-fpm-buster** + PHP-FPM + Nginx |
| 数据库 | MySQL **5.7**（root 空密码，utf8mb4），镜像 `docker.m.daocloud.io/library/mysql:5.7` |
| 构建链 | `eclipse-temurin:17-jdk-jammy` + cmdline-tools（`commandlinetools-linux-11076708_latest.zip`，dl.google.com 直连可达）+ Gradle wrapper |
| 演示数据 | **完全离线**：36 部影片 + 6 分类 + 4 段本地 MP4（ffmpeg 生成）+ 12 张静态图（picsum 已下载） |
| 安装方式 | **绕过 maccms Web 安装器**（详见 §4.4 的理由），直接 SQL 导入 + 预写配置 |
| 网络 | QEMU 用户态网络：客户机内 `10.0.2.2` = **模拟器容器 eth0**。模拟器容器内跑 `socat TCP-LISTEN:8080 → maccms:80`，App 端 API 地址为 `http://10.0.2.2:8080` |
| 真机兼容 | APK 必须含 `armeabi-v7a`（+arm64-v8a），`ndk.abiFilters` 配置 |

## 3. 网络环境事实（重要，避免重复踩坑）

| 资源 | 状态 | 用法 |
|---|---|---|
| Docker Hub 直连 | 不通 | 一律用 `docker.m.daocloud.io/library/` 前缀拉基础镜像（php:7.2.34-fpm-buster / mysql:5.7 / eclipse-temurin:17-jdk-jammy 均验证可用） |
| dl.google.com | 可达 | cmdline-tools、system-images 直接下载 |
| GitHub codeload | 可达但大文件会截断 | 下载后校验 tar 完整性（`tar tzf` 列表长度），失败重试 |
| archive.debian.org | 主仓库 200，CDN 偶发 500/502 | apt 配 `Acquire::Retries "5"`、`Acquire::Check-Valid-Until "false"`、**sources.list 中删除 debian-security 行**（该行 502） |
| 腾讯/阿里/中科大/163 的 buster 镜像 | 全部 404 | buster 已归档，只有 archive.debian.org 有完整包 |
| Maven | repo.maven.apache.org 200；阿里云 `maven.aliyun.com/repository/google` 与 `/central` 200 | build 容器 gradle 配阿里云镜像 |
| Gradle 发行版 | services.gradle.org 307；腾讯 `mirrors.cloud.tencent.com/gradle/` 206 | distributionUrl 用腾讯镜像 |
| picsum.photos | 可达 | 演示图已下载完成，无需再下 |
| 本机 ffmpeg | 有（chocolatey） | 生成 demo MP4 |

## 4. maccms 关键技术事实（源码分析结论）

### 4.1 API 门控
- `application/api/controller/Provide.php` 第 23 行：`if($GLOBALS['config']['api']['vod']['status'] != 1){ echo 'closed'; exit; }`
- 配置来源是**文件** `application/extra/maccms.php`（不是数据库表！`mac_config` 表在 install.sql 里存在但运行时配置走 extra 文件），默认 `api.vod.status => '1'` —— 源码自带开启，无需改。
- **必须绕过 Web 安装器的原因**：`install.php` 第 211-212 行会把 `api.vod.status` 强制写成 `0`，且安装锁 `application/data/install/install.lock` 存在后拒绝重装。直接 SQL 导入可以保留默认 `1`。

### 4.2 管理员密码（简化设计）
- `application/common.php` 的 `mac_verify_password()` **兼容遗留 md5**：`$stored === md5($raw)` 即通过（第 457-463 行）。
- 因此绕过安装器后，直接写 `admin_pwd = md5('admin123')` 即可登录后台，无需 bcrypt。

### 4.3 表结构（易错点，已核对）
- `mac_type` 共 **20 列**：`type_id, type_name, type_en, type_sort, type_mid, type_pid, type_status, type_tpl, type_tpl_list, type_tpl_detail, type_tpl_play, type_tpl_down, type_key, type_des, type_title, type_union, type_extend, type_logo, type_pic, type_jumpurl`。demo.sql 的 INSERT 必须给满 20 个值（已写好）。
- `mac_vod` 共 75 列（略），demo.sql 用全列名 INSERT（已写好，含 `vod_pic/vod_play_from/vod_play_url/vod_content` 等）。
- `install.sql`：80 个 DROP + 80 个 CREATE + 10 个小 INSERT，**无存储过程/触发器/`DELIMITER`**，PHP 逐条 `$pdo->exec()` 拆分安全。
- `initdata.sql`：18 条 mac_type 默认分类（"分类1~5"、子类 1~13，需先 `DELETE FROM mac_type` 清掉——demo.sql 已含该 DELETE）。
- 导入顺序：`install.sql` → `initdata.sql` → `demo.sql`。

### 4.4 离线安装方案（已写为脚本）
`docker/maccms/scripts/install.sh`（容器 CMD 调它）：
1. 轮询等 MySQL 就绪（PHP stream_socket_client，60 次 × 2s）
2. 以 root 空密码建库 `maccms10`（utf8mb4）
3. PHP PDO 逐条导入 3 个 SQL（先剥 `--` 注释、`/* */` 注释，按 `;\n` 拆分）
4. 写 `application/database.php`（hostname=mysql, username=root, 空密码, prefix=mac_）
5. 插入管理员 `admin/admin123`（md5）
6. 写 `application/data/install/install.lock`
7. 启动 `php-fpm -D` + `nginx -g 'daemon off;'`
- 注意：PHP heredoc 里用 `getenv()` 取环境变量，**避免单引号 heredoc 不插值**；写文件时转义 `\x27` 表示单引号——脚本已完成此处理。

### 4.5 API 返回结构（客户端解析依据）
- `GET {base}/api.php/provide/vod?ac=list&pg=1`（分类 `&t=<type_id>`，搜索 `&wd=关键词`，详情 `ac=detail&ids=<id>`）
- 返回：`{"code":1,"msg":"","page":1,"pagecount":1,"limit":"20","total":36,"list":[...]}`
- list 项：`vod_id, vod_name, type_id, type_name, vod_en, vod_time("Y-m-d H:i:s"), vod_remarks, vod_play_from, vod_pic, vod_content, ...`
- `vod_play_url` 格式：分集 `#`、每集"集名$URL"、播放源组之间 `$$$`；`vod_play_from` 对应源名（demo 数据用 `demo`）
- 图片：`vod_pic` 直接是 URL；mac: 前缀会被 PHP 替换为配置 imgurl

## 5. 仓库现状（大部分文件已写好）

```
D:\projects\maccms-client-tv\
├─ docker\
│  ├─ compose.yaml              # 四服务：maccms / mysql / emulator / build；网络 emu-net
│  ├─ maccms\
│  │  ├─ Dockerfile             # php:7.2.34-fpm-buster；archive.debian.org 源 + apt 重试/免有效期限
│  │  │                         # + nginx + curl + libzip-dev + unzip + pcntl/sysvsem 扩展
│  │  ├─ nginx-default.conf     # root /var/www/maccms；php-fpm 127.0.0.1:9000；静态缓存
│  │  ├─ scripts\install.sh     # 离线安装（§4.4）
│  │  ├─ scripts\gen-demo-assets.sh  # 重新生成 postbers/banners/demo MP4（ffmpeg 命令）
│  │  ├─ initdb\demo.sql        # 36 影片 + 6 分类（含 DELETE 旧分类；20 列 mac_type）
│  │  ├─ assets\static\         # poster_1..8.jpg(600x900) ✓已下载、banner_1..4.jpg(1280x720) ✓已下载
│  │  │                         # demo_1..4.mp4 ✗尚未生成（见 §6 第一步）
│  │  └─ src\                   # maccms10 完整源码（853 PHP 文件，58MB，tar 校验通过）
│  ├─ emulator\
│  │  ├─ Dockerfile             # temurin:17-jdk-jammy + 图形库/xvfb/socat + cmdline-tools
│  │  │                         # + emulator + platform-tools + system-images;android-30;android-tv;x86
│  │  │                         # + avdmanager create avd tv30 (device tv_1080p)
│  │  └─ entrypoint.sh          # socat 8080→maccms:80 + emulator 无头启动（KVM 检测，无则 -accel off）
│  └─ build\
│     └─ Dockerfile             # temurin:17-jdk-jammy + cmdline-tools + sdkmanager 装
│                               # platform-tools / platforms;android-30 / build-tools;30.0.3
├─ scripts\up.sh                # compose build + up + logs
├─ app-android\                 # ✗ 尚未创建（M2 任务）
└─ docs\                        # 空
```

## 6. 立即执行清单（按序，每步含验收）

### 步骤 0：写权限配置（必须先做）
`.claude/settings.json`（项目级，尚未创建）写入：
```json
{
  "permissions": {
    "allow": [
      "Bash(docker build *)",
      "Bash(docker compose *)",
      "Bash(docker run *)",
      "Bash(docker exec *)",
      "Bash(docker pull *)",
      "Bash(docker image *)",
      "Bash(docker logs *)",
      "Bash(adb *)",
      "Bash(gradle *)"
    ]
  }
}
```
背景：auto-mode 分类器之前多次拦截 docker 命令（误判为"外部代码执行/推送"），用户已明确选择"放行 Docker 命令"。若仍被拦，重试即过（部分为软阻断），或告知用户手动执行该条命令。

### 步骤 1：生成 4 段演示 MP4（缺失的资产）
在宿主（有 ffmpeg）执行：
```bash
cd docker/maccms/assets/static
for i in 1 2 3 4; do
  case $i in
    1) color=0x1a3a5c; f=410;; 2) color=0xf2c14e; f=490;;
    3) color=0x345a3a; f=570;; 4) color=0x5c1a3a; f=650;;
  esac
  ffmpeg -hide_banner -loglevel error -y \
    -f lavfi -i "color=c=${color}:s=1280x720:r=30:d=8" \
    -f lavfi -i "sine=frequency=${f}:duration=8" \
    -vf "drawtext=text='Demo ${i}':fontsize=120:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2" \
    -c:v libx264 -crf 28 -preset fast -pix_fmt yuv420p -c:a aac -b:a 96k -shortest demo_${i}.mp4
done
```
验收：`ls -la` 出现 4 个 mp4 且非 0 字节。替代：直接执行 `docker/maccms/scripts/gen-demo-assets.sh`（脚本含下载命令，可跳过已存在的图片）。

### 步骤 2：构建并启动 maccms + mysql
```bash
cd docker
docker compose up -d mysql
docker compose build maccms      # 或 docker build -t maccms-web:dev maccms
docker compose up -d maccms
```
**maccms 构建已知问题**：archive.debian.org CDN 抖动 → Dockerfile 已含 `Acquire::Retries "5"`；若 502 反复失败，重试几次即可（分层缓存会让重试变快）。
**验收**：
```bash
curl -s "http://127.0.0.1:8080/api.php/provide/vod/?ac=list&pg=1" | head -c 500
# 期望：{"code":1,... "list":[{"vod_id":1,"vod_name":"星际远征"...}]}
docker compose logs maccms | tail -20   # 应看到 "maccms install complete"
```
**排查顺序**（若 API 返回 closed / 空）：
1. `docker compose exec maccms ls /var/www/maccms/application/data/install/install.lock`
2. `docker compose exec maccms php -r "include 'application/database.php'; var_dump(\$config['hostname']);"`
3. 手动 `docker compose exec maccms php -r "…PDO 查询 mac_vod count…"` 确认数据行数 = 36
4. 注意 mac_vod 表默认 `vod_status` 需为 1（demo.sql 已写 1）；api datafilter `' vod_status=1'` 生效于 Provide.php:112

### 步骤 3：构建并启动模拟器容器
```bash
docker compose build emulator
docker compose up -d emulator
```
**耗时预期**：镜像拉取 SDK 组件约 3-10 分钟；无 KVM（Docker Desktop WSL2 无嵌套 /dev/kvm）走 TCG 软件模拟，**首次开机 5-15 分钟**。KVM 探测在 entrypoint.sh 中自动降级。
**验收**（模拟器容器内验证网络链路）：
```bash
docker compose exec emulator adb devices
# 期望：emulator-5554  device  （device 状态=开机完成；offline/booting 则继续等）
docker compose exec emulator curl -s "http://10.0.2.2:8080/api.php/provide/vod/?ac=list&pg=1" | head -c 200
# 期望：通过 socat 拿到 maccms 的 JSON —— 验证客户机→容器→maccms 全链路
docker compose exec emulator curl -s "http://10.0.2.2:8080/demo/demo_1.mp4" -o /dev/null -w "%{http_code}"
# 期望：200（演示 MP4 通过静态服务可达；注意 MP4 位于 /var/www/maccms/demo/ 或 nginx 静态目录，见步骤 1 后确认）
```
**注意**：demo 素材在 nginx root 下需物理位于 `maccms` 容器 `/var/www/maccms/` 内。**若 10.0.2.2:8080/demo/* 404**，把 `assets/static/` 的内容拷贝（或 bind mount）到 `/var/www/maccms/demo/` 与 `/var/www/maccms/static/`（见 Dockerfile 中 `COPY assets/ /var/www/maccms/assets/` —— 需要改成 nginx 能直接服务的路径，或在 nginx.conf 加 location /demo/ alias）。验证 v1 时若 404，改 nginx 配置加：
```nginx
location /demo/ { alias /var/www/maccms/assets/static/; }
location /static/ { alias /var/www/maccms/assets/static/; }
```
**备选方案**：若 `android-tv;x86` 镜像(sdkmanager)拉取失败，改用 `system-images;android-30;default;x86`（非 TV 形态，UI 相同）或降低 API 版本（21/25/29 都有 TV x86）。

### 步骤 4：app-android 工程 + 构建链（M2）
创建 `app-android/`：
- `settings.gradle.kts`：pluginManagement 仓库 = `maven.aliyun.com/repository/google` + `/gradle-plugin` + `/central` + mavenCentral + google
- `gradle/wrapper/gradle-wrapper.properties`：distributionUrl 用腾讯镜像 `https://mirrors.cloud.tencent.com/gradle/gradle-7.5-bin.zip`
- 版本组合（已验证兼容，勿追新）：AGP `7.4.2` / Kotlin `1.8.21` / Compose BOM `2023.05.01` / `androidx.tv:tv-material:1.0.0-alpha08` / navigation-compose `2.6.0` / retrofit `2.9.0` + okhttp `4.11.0` + kotlinx-serialization-json `1.5.1` / coil-compose `2.4.0` / media3 `1.1.1`
- `app/build.gradle.kts`：`minSdk 23`、`targetSdk 30`（模拟器 30）、`ndk { abiFilters += listOf("armeabi-v7a","arm64-v8a") }`、`buildFeatures.compose true`
- `AndroidManifest.xml`：`LEANBACK_LAUNCHER` + `INTERNET` + `uses-feature android.hardware.type.television required=true` + `banner` drawable（320x180 占位）
- 源码骨架（M3 一并建）：`MainActivity` + `ui/theme`（黑 #141414、红 #E50914）+ `ui/home`(TopNav+Hero+PosterRow)+`ui/browse`+`ui/search`+`ui/detail`+`ui/player`+`ui/settings`+`ui/components`(HeroBanner/PosterRow/PosterCard/焦点缩放 animateFloatAsState 1.0→1.1+白色描边)+`data`(Retrofit api + VodRepository + Vod/PlayUrl 模型，解析 $$$/$/#)+`util/BaseUrlManager`

**构建与安装**：
```bash
docker compose exec build bash -c "cd /app-android/workspace && ./gradlew assembleDebug"
docker compose exec build bash -c "adb connect emulator:5555 && adb wait-for-device && adb install -r app/build/outputs/apk/debug/app-debug.apk && am start -n com.maccms.tv/.MainActivity"
docker compose exec build adb exec-out screencap -p > screen.png
```
（build 容器挂载 `../app-android:/app-android/workspace`；adb connect 目标是 compose 服务名 `emulator`，同网直连 5555。）
**验收**：screen.png 显示 Netflix 风格首页；`input keyevent 20/21/22/23`（DPAD）+ `66`（ENTER）能遍历焦点到详情。

### 步骤 5：UI + API + 播放（M3/M4/M5 收尾）
- 首页数据源 `api.php/provide/vod?ac=list&pg=1`（全部）+ 按分类 `t=1..6` 各行
- 详情 `ac=detail&ids=N`；搜索 `wd=`
- 播放页：Media3 ExoPlayer 播 `vod_play_url` 解析出的首个 `demo_*.mp4` URL（http://10.0.2.2:8080/demo/demo_N.mp4）
- 设置页：运行时改 baseUrl（默认 `http://10.0.2.2:8080`），便于真机切宿主局域网 IP
- M5：焦点记忆（FocusRequester 恢复）、加载/错误态、TV banner 与图标、`aapt dump badging` 确认 native-code 含 armeabi-v7a

## 7. 最终验收标准（全部满足即完成）

1. `curl http://127.0.0.1:8080/api.php/provide/vod/?ac=list&pg=1` 返回 36 条影片 JSON
2. 模拟器容器内 `curl http://10.0.2.2:8080/api.php/provide/vod/?ac=list` 返回 JSON（socat 链路通）
3. `adb devices` 显示 `emulator-5554 device`
4. `gradlew assembleDebug` 产出 APK；`aapt dump badging` 含 `armeabi-v7a` 与 `arm64-v8a`
5. `adb install` + `am start` 后 screencap 可见 Netflix 风格首页
6. 遥控链路：DPAD 遍历 → 进详情 → 进播放 → 截图见视频画面
7. （可选）真机安装 APK 验证播放

## 8. 常见坑速查

- **maccms 安装锁**：重装需删 `application/data/install/install.lock` 并重置数据库卷（`docker compose down -v`）
- **PHP 7.2 语法**：源码无 `?->`；`str_contains` 等已有 polyfill（common.php 守卫）→ 不可升 PHP 8（会因库版本报错），也不可降到 7.0
- **apk 32 位兼容**：忘配 abiFilters 会出 x86_64-only 包，真机装不上
- **模拟器慢**：TCG 下首屏 5-15 分钟；`adb wait-for-device` 注意超时；减少动画（`settings put global window_animation_scale 0` 等）加速
- **Windows 防火墙**：容器端口仅内网使用，VNC 不需要（用 screencap 代替）
- **maccms 后台**：`http://127.0.0.1:8080/admin.php`（admin/admin123，纯演示）
- **缓存**：maccms API 有 file 缓存（cachetime 默认空=不缓存）；改 demo 数据后若不见效，清 `runtime/` 缓存

## 9. 执行原则

- 每步先看验收，失败先看日志再改配置，不做破坏性操作（`docker compose down -v` 需确认）
- 文件编辑优先已有文件（demo.sql/Dockerfile/install.sh 已就绪，勿重写）
- 外部代码（maccms10）仅用于本地演示环境，不对外发布
- 中文沟通，输出简洁；进度用任务清单跟踪（M1a 进行中）
