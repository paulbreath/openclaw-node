#!/bin/bash
# Google Play Developer API 发布脚本
# 需要先配置服务账号和凭证

set -e

echo "=========================================="
echo "  OpenClaw Node - Google Play 发布工具"
echo "=========================================="

# 检查依赖
command -v node >/dev/null 2>&1 || { echo "❌ Node.js 未安装"; exit 1; }
command -v bun >/dev/null 2>&1 || { echo "❌ Bun 未安装"; exit 1; }

# 配置变量
PACKAGE_NAME="com.openclaw.node"
SERVICE_ACCOUNT_FILE="./google-play-service-account.json"
APK_PATH="../download/OpenClawNode.apk"

# 步骤 1: 检查凭证文件
echo ""
echo "📋 步骤 1/5: 检查凭证..."

if [ ! -f "$SERVICE_ACCOUNT_FILE" ]; then
    echo ""
    echo "❌ 未找到服务账号凭证文件: $SERVICE_ACCOUNT_FILE"
    echo ""
    echo "请按以下步骤创建服务账号："
    echo ""
    echo "1. 打开 Google Cloud Console: https://console.cloud.google.com"
    echo "2. 创建新项目或选择现有项目"
    echo "3. 启用 Google Play Developer API"
    echo "4. 创建服务账号:"
    echo "   - IAM & Admin → Service Accounts → Create Service Account"
    echo "   - 角色: 无（稍后在 Play Console 中授权）"
    echo "5. 创建 JSON 密钥并下载到: $SERVICE_ACCOUNT_FILE"
    echo "6. 在 Google Play Console 中授权服务账号:"
    echo "   - Setup → API access → Link service account"
    echo "   - 授予 'Release manager' 权限"
    echo ""
    exit 1
fi

echo "✅ 服务账号凭证文件已找到"

# 步骤 2: 检查 APK
echo ""
echo "📋 步骤 2/5: 检查 APK..."

if [ ! -f "$APK_PATH" ]; then
    echo "❌ 未找到 APK: $APK_PATH"
    echo "请先编译 APK"
    exit 1
fi

APK_SIZE=$(stat -f%z "$APK_PATH" 2>/dev/null || stat -c%s "$APK_PATH" 2>/dev/null)
echo "✅ APK 已找到 ($(numfmt --to=iec $APK_SIZE 2>/dev/null || echo "$APK_SIZE bytes"))"

# 步骤 3: 安装依赖
echo ""
echo "📋 步骤 3/5: 安装依赖..."

cd "$(dirname "$0")"

if [ ! -d "node_modules" ]; then
    bun install
    echo "✅ 依赖安装完成"
else
    echo "✅ 依赖已安装"
fi

# 步骤 4: 上传 APK
echo ""
echo "📋 步骤 4/5: 上传 APK 到 Google Play..."

bun run upload.ts

echo ""
echo "✅ 发布完成！"
echo ""
echo "查看发布状态: https://play.google.com/console/developers"
