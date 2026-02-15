#!/bin/bash
# 编译 Release APK 用于 Google Play 发布

set -e

echo "=========================================="
echo "  OpenClaw Node - Release APK 编译"
echo "=========================================="

cd "$(dirname "$0")/android"

# 检查签名配置
KEYSTORE_FILE="./release.keystore"

if [ ! -f "$KEYSTORE_FILE" ]; then
    echo ""
    echo "⚠️  未找到 Release 签名密钥"
    echo ""
    echo "请创建签名密钥:"
    echo ""
    echo "  keytool -genkey -v -keystore release.keystore \\"
    echo "    -alias openclaw-node \\"
    echo "    -keyalg RSA -keysize 2048 -validity 10000"
    echo ""
    echo "然后将密钥文件移动到: android/release.keystore"
    echo ""
    
    # 创建临时签名密钥用于测试
    read -p "是否创建临时签名密钥用于测试？(y/n) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        keytool -genkey -v -keystore "$KEYSTORE_FILE" \
            -alias openclaw-node \
            -keyalg RSA -keysize 2048 -validity 10000 \
            -storepass openclaw123 \
            -keypass openclaw123 \
            -dname "CN=OpenClaw Node, OU=Development, O=OpenClaw, L=Beijing, ST=Beijing, C=CN"
        
        echo "✅ 临时签名密钥已创建"
        echo "   密钥库密码: openclaw123"
        echo "   密钥密码: openclaw123"
        echo ""
    else
        exit 1
    fi
fi

# 设置环境变量
export ANDROID_HOME="${ANDROID_HOME:-/home/z/android-sdk}"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"
export PATH="/home/z/gradle-8.5/bin:$PATH"

# 编译 Release APK
echo ""
echo "📦 正在编译 Release APK..."
echo ""

if [ -f "$KEYSTORE_FILE" ]; then
    # 使用签名编译
    gradle assembleRelease \
        -Pandroid.injected.signing.store.file="$KEYSTORE_FILE" \
        -Pandroid.injected.signing.store.password="${KEYSTORE_PASSWORD:-openclaw123}" \
        -Pandroid.injected.signing.key.alias="${KEY_ALIAS:-openclaw-node}" \
        -Pandroid.injected.signing.key.password="${KEY_PASSWORD:-openclaw123}" \
        --no-daemon
else
    # 未签名编译
    gradle assembleRelease --no-daemon
fi

# 检查编译结果
APK_FILE="app/build/outputs/apk/release/app-release.apk"

if [ -f "$APK_FILE" ]; then
    echo ""
    echo "✅ 编译成功！"
    echo ""
    echo "📁 APK 位置: $APK_FILE"
    
    # 复制到下载目录
    cp "$APK_FILE" "../download/OpenClawNode-release.apk"
    echo "📁 复制到: download/OpenClawNode-release.apk"
    
    # 显示 APK 信息
    APK_SIZE=$(stat -c%s "$APK_FILE" 2>/dev/null || stat -f%z "$APK_FILE" 2>/dev/null)
    echo "📊 APK 大小: $(echo "scale=2; $APK_SIZE / 1024 / 1024" | bc) MB"
    
    # 显示签名信息
    echo ""
    echo "📋 APK 签名信息:"
    keytool -printcert -jarfile "$APK_FILE" 2>/dev/null | head -10 || echo "   (无法读取签名信息)"
else
    echo ""
    echo "❌ 编译失败"
    exit 1
fi

echo ""
echo "=========================================="
echo "  下一步"
echo "=========================================="
echo ""
echo "1. 测试 APK: adb install download/OpenClawNode-release.apk"
echo "2. 发布到 Google Play: cd scripts && bun run upload.ts"
echo ""
