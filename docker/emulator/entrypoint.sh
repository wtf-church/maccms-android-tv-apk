#!/usr/bin/env bash
# Android TV 模拟器入口：等待 maccms → socat 转发 → 启动 AVD → 等待开机
set -euo pipefail

echo "[emulator] ===== starting ====="

# socat 转发：客户机内 10.0.2.2:8080 → maccms:80
# (QEMU 用户态网络下 10.0.2.2 指容器 eth0，App 端访问 http://10.0.2.2:8080)
(socat TCP-LISTEN:8080,fork,reuseaddr TCP:maccms:80 &)
echo "[emulator] socat: 10.0.2.2:8080 -> maccms:80"

# 启动 AVD（无头，HW 加速可用则用，否则自动降级 TCG）
export ANDROID_AVD_HOME=/root/.android/avd
export ANDROID_EMULATOR_HOME=/root/.android
export ANDROID_SDK_ROOT=/opt/android-sdk
export ANDROID_HOME=/opt/android-sdk

# 检测 KVM
if [ -e /dev/kvm ]; then
  echo "[emulator] KVM detected, hardware acceleration enabled"
  HW_ACCEL="-accel on"
else
  echo "[emulator] no /dev/kvm, falling back to software (TCG, slow)"
  HW_ACCEL="-accel off"
fi

# 前台运行 emulator
exec /opt/android-sdk/emulator/emulator \
  -avd tv30 \
  -no-window \
  -no-boot-anim \
  -no-snapshot \
  -gpu swiftshader_indirect \
  -no-audio \
  ${HW_ACCEL} \
  -netdelay none \
  -netspeed full
