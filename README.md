# Maccms TV Client — 华为智慧屏 maccms 客户端（Netflix 风格）

为华为智慧屏（兼容安卓的 HarmonyOS 2/3，32 位 armv7）开发的视频浏览客户端。数据源为自建 maccms V10 站点，采用 **Kotlin + Jetpack Compose for TV**，UI 完全仿照 Netflix TV 版（顶部导航 + Hero 横幅 + 横向海报行 + 遥控焦点缩放），播放基于 Media3 ExoPlayer。

开发与演示环境全容器化：maccms / MySQL / Android TV 模拟器 / 构建工具链 四个 Docker 容器，可一键拉起并进行端到端验证。

**项目仓库本身不包含 maccms 源代码**；演示数据（36 部影片 + 6 分类 + 4 段 MP4 + 12 张海报图）完全离线，无需访问外网即可运行。

---

## 下载

首个正式版 **v1.0.0**（Android 6.0+，含 armeabi-v7a / arm64-v8a / x86）：

| 渠道 | 安装包 |
|---|---|
| Gitee（国内推荐） | [MaccmsTV-v1.0.0-universal.apk](https://gitee.com/shuizhuyu/maccms_tv_client/releases/download/v1.0.0/MaccmsTV-v1.0.0-universal.apk) |
| GitHub | [MaccmsTV-v1.0.0-universal.apk](https://github.com/wtf-church/maccms-android-tv-apk/releases/download/v1.0.0/MaccmsTV-v1.0.0-universal.apk) |

历史版本与更新说明：[Gitee Releases](https://gitee.com/shuizhuyu/maccms_tv_client/releases) / [GitHub Releases](https://github.com/wtf-church/maccms-android-tv-apk/releases)。

SHA-256：`e86c191a597983d6ee4bf3677b667d1a1d74098c234d4bd7c3161c1513315fc2`

---

## 目录

- [下载](#下载)
- [功能特性](#功能特性)
- [项目架构](#项目架构)
- [环境要求](#环境要求)
- [快速开始（Docker 全容器开发环境）](#快速开始docker-全容器开发环境)
- [构建 Android 客户端](#构建-android-客户端)
- [安装到电视](#安装到电视)
- [配置说明](#配置说明)
- [端到端测试](#端到端测试)
- [常见问题](#常见问题)
- [免责声明](#免责声明)
- [许可证](#许可证)
- [参考](#参考)

---

## 功能特性

- **Netflix 风格 UI**：顶部导航（首页/浏览/搜索/设置）+ Hero 大横幅 + 横向海报行 + 遥控焦点缩放（1.0 → 1.1 白色描边）
- **数据源**：自建 maccms V10（ThinkPHP 5）标准 API `api.php/provide/vod`，支持分类浏览、关键词搜索、影片详情
- **播放**：Media3 ExoPlayer，兼容 MP4 / HLS(m3u8)；演示用本地 MP4 流
- **真机兼容**：APK 显式包含 `armeabi-v7a` + `arm64-v8a`（32 位真机可装），同时含 `x86` 供模拟器
- **运行时配置**：设置页可修改 API 服务器地址（真机切换宿主机局域网 IP）
- **全离线演示数据**：36 部影片（6 分类）、4 段 ffmpeg 生成的测试 MP4、12 张海报/横幅图

## 项目架构

```
┌─────────────────────────────── Docker Compose (emu-net) ─────────────────────────────────┐
│                                                                                           │
│  ┌──────────────┐     ┌──────────────┐      ┌───────────────┐      ┌───────────────────┐ │
│  │   mysql      │     │   maccms     │      │   emulator    │      │   build           │ │
│  │ mysql:5.7    │────▶│ php7.2-fpm   │◀────│ android-tv    │      │ jdk17 + cmdline-  │ │
│  │ utf8mb4      │     │ + nginx      │8080 │ :x86 API30    │ 5555 │ tools + gradle    │ │
│  │ 卷:mysql-data│     │ 离线安装脚本 │socat│ TV AVD, 无头   │──────│ 挂载 app-android  │ │
│  └──────────────┘     └──────────────┘      └───────────────┘      └───────────────────┘ │
│                                              │ adb / 截图 / DPAD 事件                     │
└──────────────────────────────────────────────┼────────────────────────────────────────────┘
                                               │ 10.0.2.2:8080 (QEMU 客户机内视角)
                                               ▼
                                   Android TV App (Compose for TV)
                                    首页 → 浏览 → 搜索 → 详情 → 播放
```

### 四个容器

| 容器 | 镜像/构建 | 职责 | 端口 |
|---|---|---|---|
| `maccms` | `docker-compose build maccms` | Nginx + PHP7.2-FPM，离线安装 maccms V10 + 演示数据，提供 REST API 与静态素材 | `127.0.0.1:8080 → 80` |
| `maccms-mysql` | `docker.m.daocloud.io/library/mysql:5.7` | maccms 数据库（root 空密码，utf8mb4） | 仅内网 `mysql:3306` |
| `android-tv-emulator` | `docker-compose build emulator` | 无头 Android TV 模拟器（API 30, x86, TCG 软件模拟）；`socat` 把客户机内 `10.0.2.2:8080` 转发到 maccms | `5554/5555` (adb) |
| `android-build` | `docker-compose build build` | Android 构建链（JDK17 + cmdline-tools + platform-tools 等），挂载 `app-android/` 执行 Gradle 构建 | — |

### 关键技术决策

- **绕过 maccms Web 安装器**：其安装流程会把 `api.vod.status` 强制写为 `0`（API 关闭）。本方案直接 SQL 导入（`install.sql` → `initdata.sql` → `demo.sql`）+ 预写 `application/database.php`（root 空密码）+ `install.lock`，保留 API 默认开启状态。
- **网络链路**：QEMU 用户态网络下，Android 客户机内 `10.0.2.2` 指向模拟器容器 eth0；容器内 socat 监听 `8080 → maccms:80`。App 默认 API 地址即 `http://10.0.2.2:8080`。
- **demo.sql 列值校正**：`INSERT` 后追加 `UPDATE mac_vod SET vod_play_from='demo', vod_play_server=''`，保证播放源被 API 正确识别。
- **ABI 兼容**：`build.gradle.kts` 通过占位 `.so`（`libplaceholder.so`）让 APK 的 `native-code` 显式声明三个 ABI，既满足模拟器（x86）也满足真机（armeabi-v7a）。
- **maccms API 字段**：`ac=list` 仅返回精简字段（无 `vod_pic`），客户端列表请求使用 `ac=videolist` 获得完整字段（含 `vod_pic`、`vod_play_url`）。

### 客户端 App 结构

```
app-android/
├── settings.gradle.kts                  # 仓库源（阿里云镜像 + google/mavenCentral）
├── build.gradle.kts                     # AGP 7.4.2 / Kotlin 1.8.22 / serialization
├── gradle.properties
├── gradle/wrapper/                      # Gradle 7.5（distributionUrl 用腾讯镜像）
└── app/
    ├── build.gradle.kts                 # compileSdk 33 / minSdk 23 / targetSdk 30 + abiFilters
    └── src/main/
        ├── AndroidManifest.xml          # LEANBACK_LAUNCHER + CLEARTEXT
        └── java/com/maccms/tv/
            ├── MainActivity.kt          # Navigation Compose 路由
            ├── data/                    # Retrofit API / 模型 / vod_play_url 解析 / 仓库
            │   ├── ApiClient.kt
            │   ├── Models.kt            # ApiResponse / Vod / Episode / PlaySource 解析
            │   └── VodRepository.kt
            ├── ui/
            │   ├── theme/               # 黑 #141414 / 红 #E50914
            │   ├── home/                # TopNav + Hero + 分类海报行
            │   ├── browse/              # 分类 + 网格
            │   ├── search/              # 关键词搜索
            │   ├── detail/              # 详情 + 播放源分集列表
            │   ├── player/              # Media3 ExoPlayer 播放页
            │   ├── settings/            # Base URL 运行时修改
            │   └── components/          # HeroBanner / PosterCard(焦点缩放) / PosterRow / 状态页
            └── util/BaseUrlManager.kt   # API 地址管理（SharedPreferences 持久化）
```

---

## 环境要求

### 后端 / 开发环境

| 依赖 | 版本 | 说明 |
|---|---|---|
| Docker Desktop | 20.10+（WSL2 后端） | 强烈建议 WSL2；兼容硬件虚拟化 |
| ffmpeg | 任意 | 仅用于重新生成演示 MP4（可选） |
| Git Bash / WSL shell | — | 文档中命令均以 Unix 语法给出 |
| 内存 | ≥ 8 GB 可用 | 模拟器（TCG 软件模拟）较吃资源 |
| 磁盘 | ≥ 20 GB 可用 | 四个镜像 + SDK 组件约 15 GB |

> **网络说明**：Docker Hub 可能无法直接访问，Docker 基础镜像统一使用镜像加速前缀 `docker.m.daocloud.io/library/`（Dockerfile 已内置）；Gradle / Android SDK 分发使用腾讯、阿里云镜像。若容器构建期间 `archive.debian.org` 抖动出现 502/500，Dockerfile 已配置 apt 自动重试，直接重跑构建即可。

### 客户端（真机）

| 设备 | 要求 |
|---|---|
| 华为智慧屏 | HarmonyOS 2/3（兼容安卓），支持安装第三方 APK |
| 32 位兼容 | APK 已含 `armeabi-v7a`，32 位系统可安装（已验证） |

---

## 快速开始（Docker 全容器开发环境）

以下命令均在项目根目录执行。

### 1. 获取 maccms 源码（首次克隆仓库后必做）

仓库不包含 maccms V10 第三方源码（见 [许可证](#许可证)），构建 maccms 容器前需从上游获取：

```bash
cd docker/maccms
if [ ! -d src ]; then
  curl -L --retry 5 -o maccms10.zip \
    https://codeload.github.com/magicblack/maccms10/zip/refs/heads/master
  unzip -tq maccms10.zip   # 校验完整性（大文件偶发截断）
  unzip -q maccms10.zip
  mv maccms10-master src
  rm maccms10.zip
fi
cd ../..
```

> 网络提示：GitHub codeload 偶发大文件截断，若 `unzip -tq` 报错说明下载不完整，删除 zip 后重试。

### 2. 生成演示 MP4（首次）

素材中 `demo_1..4.mp4` 需要本机 ffmpeg 生成（海报图已随项目提供）：

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

> 仓库已附带这 4 个 MP4（位于 `docker/maccms/assets/static/`），此步仅在素材缺失时执行。也可运行 `docker/maccms/scripts/gen-demo-assets.sh`。

### 3. 构建并启动全部容器

```bash
cd docker
docker compose up -d mysql        # 先启动数据库（首次拉镜像）：
docker compose build maccms       # 构建 maccms（首次 apt 下载较慢）
docker compose up -d maccms
docker compose build emulator     # 拉 Android SDK 组件（约 3-10 分钟）
docker compose up -d emulator
docker compose build build
docker compose up -d build
```

> 也可直接执行项目内的一键脚本 `bash scripts/up.sh`（启动全部容器并跟踪日志；加 `--build` 参数会先构建镜像）。

**maccms 容器**首次启动会执行离线安装：等待 MySQL → 建库 → 导入三个 SQL（约 1-2 分钟）。验证安装日志：

```bash
docker logs maccms | tail -5
# 期望看到: [install] ===== maccms install complete =====
```

### 4. 验证 API 与链路

```bash
# ① 宿主访问 API（期望 {"code":1,...,"total":36}）
curl -s "http://127.0.0.1:8080/api.php/provide/vod/?ac=list&pg=1" | head -c 200

# ② 模拟器容器内验证 socat 转发（客户机视角 10.0.2.2:8080）
docker exec android-tv-emulator curl -s "http://127.0.0.1:8080/api.php/provide/vod?ac=list&pg=1"

# ③ 演示素材可访问（期望 200）
curl -s -o /dev/null -w "%{http_code}\n" "http://127.0.0.1:8080/static/poster_1.jpg"
curl -s -o /dev/null -w "%{http_code}\n" "http://127.0.0.1:8080/demo/demo_1.mp4"
```

### 5. 等待模拟器开机（无 KVM 时约 5-15 分钟）

```bash
docker exec android-tv-emulator adb devices
# 期望: emulator-5554  device   （offline = 还在开机，继续等）
```

---

## 构建 Android 客户端

构建在 `build` 容器内进行（挂载宿主 `app-android/` 目录）：

```bash
# ① 编译 APK（首次会下载 Gradle 发行版与依赖，用时较长）
docker exec android-build bash -c "cd /app-android/workspace && ./gradlew assembleDebug"

# ② APK 产物
# app-android/app/build/outputs/apk/debug/app-debug.apk
```

验证 APK 属性（ABI / SDK / Leanback）：

```bash
docker exec android-build bash -c \
  "/opt/android-sdk/build-tools/30.0.3/aapt dump badging \
   /app-android/workspace/app/build/outputs/apk/debug/app-debug.apk | grep -E 'native-code|sdkVersion|launchable'"
# 期望输出包含:
#   native-code: 'arm64-v8a' 'armeabi-v7a' 'x86'
#   sdkVersion:'23' / targetSdkVersion:'30'
```

**不使用容器构建**也可以：在安装了 JDK 17 + Android SDK（platform 33 / build-tools 30.0.3）的机器上执行 `cd app-android && ./gradlew assembleDebug`。仓库已内置 Gradle Wrapper（发行版走腾讯镜像）。

---

## 安装到电视

### 通过模拟器完成端到端验证

```bash
# 把 APK 从 build 容器拷到模拟器容器再安装（模拟器容器内 adb 直接连接本机 QEMU）
docker cp android-build:/app-android/workspace/app/build/outputs/apk/debug/app-debug.apk "$TMP/app-debug.apk"
docker cp "$TMP/app-debug.apk" android-tv-emulator:/tmp/
docker exec android-tv-emulator bash -c "adb -s emulator-5554 install -r /tmp/app-debug.apk"
docker exec android-tv-emulator bash -c "adb -s emulator-5554 shell am start -n com.maccms.tv/.MainActivity"

# 截图（放在电脑上查看）
docker exec android-tv-emulator bash -c "adb -s emulator-5554 exec-out screencap -p > /tmp/screen.png"
docker cp android-tv-emulator:/tmp/screen.png ./screen.png

# 遥控器事件（DPAD）：20=下 19=上 21=左 22=右 23=确认(ENTER)
docker exec android-tv-emulator bash -c "adb -s emulator-5554 shell input keyevent 20"
```

> 首次启动 App 在软件模拟下加载较慢（API + 海报图），等待数十秒再截图。

### 安装到华为智慧屏真机

1. 将 `app-debug.apk` 拷贝到 U 盘（或通过 adb 安装）。
2. 电视上允许安装未知来源应用后安装。
3. **首次使用前配置服务器地址**：打开 App → 设置 → 服务器地址改为电脑的局域网 IP，例如 `http://192.168.1.100:8080`（确保电视与电脑同一网络，且电脑防火墙放行 8080 入站）。
4. 返回首页即加载影片数据。

---

## 配置说明

### API 地址（运行时）

默认 `http://10.0.2.2:8080`（模拟器内指向 maccms）。设置页修改后写入 `SharedPreferences`，立即生效。真机场景改为宿主机局域网 IP。

### maccms 演示数据

| 数据 | 来源 |
|---|---|
| 36 部影片 / 6 分类 | `docker/maccms/initdb/demo.sql`（导入时含列值校正 UPDATE） |
| 海报/横幅 12 张 | `docker/maccms/assets/static/poster_1..8.jpg`, `banner_1..4.jpg` |
| 演示 MP4 4 段 | `docker/maccms/assets/static/demo_1..4.mp4`（ffmpeg 生成） |
| 静态路由 | nginx `location ^~ /static/` 与 `^~ /demo/` → `assets/static/` |

- 重新生成素材：`bash docker/maccms/scripts/gen-demo-assets.sh`
- 清空重建数据库：`docker compose down -v`（删除数据卷）后重新执行快速开始

### 后台管理

- 地址：`http://127.0.0.1:8080/admin.php`
- 账号：`admin` / `admin123`（演示用；由离线安装脚本以 md5 兼容格式写入）

---

## 端到端测试

模拟器开机完成后，可自动完成一条完整链路（首页 → 浏览 → 搜索 → 详情 → 播放）：

```bash
EMU="docker exec android-tv-emulator adb -s emulator-5554"

# 1) 启动 App
$EMU shell am start -n com.maccms.tv/.MainActivity
sleep 60

# 2) 首页截图（应为 Netflix 风格首页）
$EMU exec-out screencap -p > screen_home.png

# 3) 焦点到“播放”按 ENTER（23）→ 播放页
$EMU shell input keyevent 20; sleep 5
$EMU shell input keyevent 23; sleep 30
$EMU exec-out screencap -p > screen_playing.png

# 4) 返回，进入“浏览”页
$EMU shell input keyevent 4; sleep 8
$EMU shell input keyevent 19; sleep 6
$EMU shell input keyevent 22; sleep 6   # 焦点到“浏览”菜单
$EMU shell input keyevent 23; sleep 25
$EMU exec-out screencap -p > screen_browse.png
```

> DPAD 键值：`19` 上 / `20` 下 / `21` 左 / `22` 右 / `23` 确认。

---

## 常见问题

**1. `docker compose up -d maccms` 后容器反复重启，日志报 `could not find driver`**
原因：基础镜像默认未装 `pdo_mysql` 扩展。本仓库 Dockerfile 已修复（`docker-php-source extract` + `docker-php-ext-install pdo_mysql mysqli pcntl sysvsem`），重新 build 镜像即可。若仍失败，先确认镜像为最新：`docker compose build maccms` 后 `docker compose up -d --force-recreate maccms`。

**2. API 返回 404 / “页面不存在”**
nginx 需要 `fastcgi_split_path_info` 才能让 ThinkPHP 解析 `api.php/provide/vod` 的 PATH_INFO。仓库 `nginx-default.conf` 已含此配置（`location ~ ^(.+?\.php)(/.+)?$`）。

**3. API 返回 `closed`**
maccms 的安装器会把 `api.vod.status` 写成 0。本方案绕过安装器、用 SQL 直接导入，默认保留开启（`application/extra/maccms.php`）。若曾用安装器装过：删除 `application/data/install/install.lock` 并重置数据卷（`docker compose down -v`）后重装。

**4. 首页海报不显示**
`ac=list` 返回精简字段（无 `vod_pic`）。客户端已改用 `ac=videolist`；若自建 API 或改动过服务端，请确保列表请求带 `ac=videolist`。

**5. 首页/播放页报 `CLEARTEXT communication ... not permitted`**
Android 9+ 默认禁止明文 HTTP。Manifest 已设置 `android:usesCleartextTraffic="true"`（本仓库 APK 已启用）。自编译时请勿删除该属性。

**6. 真机安装提示“应用未安装”或 ABI 不匹配**
确认 APK `native-code` 包含 `armeabi-v7a`（`aapt dump badging` 验证）。32 位真机无法安装纯 64 位 APK。

**7. 模拟器一直 `offline`（无 KVM 环境）**
无 KVM 时模拟器走 TCG 软件模拟，首次开机 5-15 分钟。可 `docker logs android-tv-emulator` 观察 `-accel off` 提示，耐心等待 `adb devices` 变为 `device`（可配合 `adb wait-for-device`）。

**8. 构建时 apt 下载 502/500（archive.debian.org 抖动）**
Dockerfile 已配置 `Acquire::Retries "10"` 与重试循环；重跑 `docker compose build maccms`，分层缓存会让后续构建显著加快。

---

## 免责声明

- 本项目**仅限学习交流**，禁止用于未授权影视资源聚合。
- 下游打包售卖、接入盗版源等行为与作者无关。
- 本仓库不提供、不分发任何影视资源；演示数据均为本地生成的测试素材。使用者应确保自行配置的数据源合法合规，并自行承担相应责任。

---

## 许可证

本项目源码采用 [MIT License](LICENSE)。各部分许可如下：

| 组件 | 许可证 |
|---|---|
| 本项目源码（docker/ + app-android/，排除第三方组件与 `docker/maccms/src/`） | [MIT](LICENSE) |
| maccms V10（源码仅用于本地演示，不在本仓库分发，`docker/maccms/src/` 已被 `.gitignore` 排除） | [Apache-2.0](https://github.com/magicblack/maccms10) |
| Android / Kotlin / Compose / Media3 / Retrofit 等依赖 | 各项目自身的开源许可证 |

> 演示数据仅为本地开发用途；请勿将 maccms 源码用于对外发布等未经授权的场景。

---

## 参考

- maccms10 官方仓库：https://github.com/magicblack/maccms10
- Jetpack Compose for TV：https://developer.android.com/jetpack/androidx/releases/tv
- Media3 ExoPlayer：https://developer.android.com/media/media3
- 本项目 goal.md（开发任务书）：`GOAL.md`

### 仓库与发布

- Gitee（主仓库）：https://gitee.com/shuizhuyu/maccms_tv_client
- GitHub（镜像）：https://github.com/wtf-church/maccms-android-tv-apk
- `origin` 已配置多个 push URL：执行 `git push origin --all`（有 tag 时再补 `git push origin --tags`）即可同时发布到 Gitee 与 GitHub。
