# Android 构建容器：JDK17 + cmdline-tools + platform-tools
FROM docker.m.daocloud.io/library/eclipse-temurin:17-jdk-jammy

ENV ANDROID_SDK_ROOT=/opt/android-sdk \
    ANDROID_HOME=/opt/android-sdk \
    PATH=/opt/android-sdk/cmdline-tools/latest/bin:/opt/android-sdk/platform-tools:$PATH \
    JAVA_HOME=/opt/java/openjdk

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl unzip git ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# cmdline-tools（Linux 最新，含 sdkmanager/avdmanager）
RUN curl -fsSL -o /tmp/cmdline-tools.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip \
    && mkdir -p ${ANDROID_SDK_ROOT}/cmdline-tools \
    && unzip -q /tmp/cmdline-tools.zip -d ${ANDROID_SDK_ROOT}/cmdline-tools \
    && mv ${ANDROID_SDK_ROOT}/cmdline-tools/cmdline-tools ${ANDROID_SDK_ROOT}/cmdline-tools/latest \
    && rm -f /tmp/cmdline-tools.zip

# 安装 SDK 组件：platform-tools(adb)、build-tools、platforms；android-tv;x86 系统镜像由 emulator 容器装
RUN yes | sdkmanager --licenses >/dev/null 2>&1 || true \
    && sdkmanager "platform-tools" "platforms;android-30" "platforms;android-33" "build-tools;30.0.3" \
    || sdkmanager --sdk_root=${ANDROID_SDK_ROOT} "platform-tools" "platforms;android-30" "platforms;android-33" "build-tools;30.0.3"

WORKDIR /app-android/workspace
ENTRYPOINT ["/bin/bash"]
