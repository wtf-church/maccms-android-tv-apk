# Android TV 模拟器容器：cmdline-tools + emulator + android-tv;x86 系统镜像，无头运行
FROM docker.m.daocloud.io/library/eclipse-temurin:17-jdk-jammy

ENV ANDROID_SDK_ROOT=/opt/android-sdk \
    ANDROID_HOME=/opt/android-sdk \
    PATH=/opt/android-sdk/cmdline-tools/latest/bin:/opt/android-sdk/platform-tools:/opt/android-sdk/emulator:$PATH \
    JAVA_HOME=/opt/java/openjdk

RUN apt-get update \
    && for i in 1 2 3 4 5; do \
         apt-get install -y --no-install-recommends curl unzip git ca-certificates \
           libpulse0 libnss3 libx11-6 libxcomposite1 libxcursor1 libxi6 libxtst6 \
           libgl1 libglib2.0-0 libasound2 libgbm1 libxrandr2 libxdamage1 libxfixes3 \
           libxkbcommon0 xvfb socat netcat-openbsd \
         && break \
         || { echo "apt install failed (try $i), retrying..."; sleep 5; apt-get update; }; \
       done \
    && rm -rf /var/lib/apt/lists/*

# cmdline-tools
RUN curl -fsSL -o /tmp/cmdline-tools.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip \
    && mkdir -p ${ANDROID_SDK_ROOT}/cmdline-tools \
    && unzip -q /tmp/cmdline-tools.zip -d ${ANDROID_SDK_ROOT}/cmdline-tools \
    && mv ${ANDROID_SDK_ROOT}/cmdline-tools/cmdline-tools ${ANDROID_SDK_ROOT}/cmdline-tools/latest \
    && rm -f /tmp/cmdline-tools.zip

# 安装 emulator + platform-tools + TV x86 系统镜像（API 30, 32 位 x86, TV 形态）
RUN yes | sdkmanager --licenses >/dev/null 2>&1 || true \
    && sdkmanager "emulator" "platform-tools" "system-images;android-30;android-tv;x86" \
    || sdkmanager --sdk_root=${ANDROID_SDK_ROOT} "emulator" "platform-tools" "system-images;android-30;android-tv;x86"

# 创建 AVD
RUN echo "no" | avdmanager create avd \
    -n tv30 \
    -k "system-images;android-30;android-tv;x86" \
    -d "tv_1080p" \
    --force

# socat 转发：客户机内 10.0.2.2:8080 → maccms:80（maccms 服务名）
COPY entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod +x /usr/local/bin/entrypoint.sh

EXPOSE 5554 5555

ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
