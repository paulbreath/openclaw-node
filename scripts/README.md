# Google Play 自动发布指南

## 📋 前提条件

1. **Google Play Developer 账号** ($25 一次性注册费)
   - 注册地址: https://play.google.com/console/signup

2. **Google Cloud 项目**
   - 创建地址: https://console.cloud.google.com

---

## 🔧 步骤 1: 创建 Google Cloud 项目

1. 打开 [Google Cloud Console](https://console.cloud.google.com)
2. 点击顶部项目选择器，点击 **"新建项目"**
3. 项目名称: `openclaw-node-publisher`
4. 点击 **"创建"**

---

## 🔧 步骤 2: 启用 Google Play Developer API

1. 在 Google Cloud Console 中，进入 **"API 和服务" → "库"**
2. 搜索 **"Google Play Developer API"**
3. 点击 **"启用"**

---

## 🔧 步骤 3: 创建服务账号

1. 进入 **"IAM 和管理" → "服务账号"**
2. 点击 **"创建服务账号"**
   - 名称: `openclaw-node-uploader`
   - 描述: `用于自动上传 APK 到 Google Play`
   - 点击 **"创建并继续"**
3. 角色选择: **跳过**（稍后在 Play Console 中授权）
4. 点击 **"完成"**

---

## 🔧 步骤 4: 创建服务账号密钥

1. 点击刚创建的服务账号
2. 进入 **"密钥"** 标签
3. 点击 **"添加密钥" → "创建新密钥"**
4. 选择 **"JSON"** 格式
5. 点击 **"创建"**
6. **保存下载的 JSON 文件** 到 `scripts/google-play-service-account.json`

⚠️ **重要**: 此文件包含敏感凭证，切勿提交到 Git！

---

## 🔧 步骤 5: 在 Google Play Console 中授权

1. 打开 [Google Play Console](https://play.google.com/console)
2. 选择你的应用（或创建新应用）
3. 进入 **"设置" → "API 访问权限"**
4. 在 **"服务账号"** 部分，点击 **"关联服务账号"**
5. 选择你在步骤 3 创建的服务账号
6. 授予 **"发布管理员"** 权限
7. 点击 **"添加"**

---

## 🔧 步骤 6: 创建应用（首次发布）

如果应用尚未在 Google Play 上创建：

1. 在 Play Console 首页，点击 **"创建应用"**
2. 填写应用信息：
   - **应用名称**: `OpenClaw Node`
   - **默认语言**: `English (United States)`
   - **免费或付费**: `免费`
3. 点击 **"创建应用"**

---

## 🚀 步骤 7: 运行发布脚本

```bash
cd scripts

# 安装依赖
bun install

# 上传 APK
bun run upload.ts
```

---

## 📁 文件结构

```
scripts/
├── package.json
├── upload.ts           # 上传脚本
├── publish.sh          # Shell 入口脚本
└── google-play-service-account.json  # 服务账号凭证（不提交到 Git）
```

---

## 🔐 安全提示

1. **服务账号密钥文件** (`google-play-service-account.json`) 必须添加到 `.gitignore`
2. 不要在客户端代码中使用服务账号凭证
3. 定期轮换服务账号密钥
4. 仅授予必要的权限

---

## 📋 发布检查清单

发布前确保：

- [ ] APK 已签名（Release 签名）
- [ ] 版本号高于已发布版本
- [ ] 应用图标 512x512 PNG
- [ ] 功能图片 1024x500 PNG
- [ ] 至少 2 张手机截图
- [ ] 隐私政策 URL
- [ ] 内容分级问卷已填写

---

## 🔄 CI/CD 集成

### GitHub Actions 示例

```yaml
# .github/workflows/publish.yml
name: Publish to Google Play

on:
  workflow_dispatch:
    inputs:
      track:
        description: 'Release track'
        required: true
        default: 'internal'
        type: choice
        options:
          - internal
          - alpha
          - beta
          - production

jobs:
  publish:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      
      - name: Setup Node.js
        uses: actions/setup-node@v4
        with:
          node-version: '20'
      
      - name: Install dependencies
        run: |
          cd scripts
          npm install
      
      - name: Create service account file
        run: |
          echo '${{ secrets.GOOGLE_PLAY_SERVICE_ACCOUNT }}' > scripts/google-play-service-account.json
      
      - name: Publish to Google Play
        run: |
          cd scripts
          npx tsx upload.ts
        env:
          TRACK: ${{ inputs.track }}
```

---

## ❓ 常见问题

### 1. "The caller does not have permission"
- 确保服务账号已在 Play Console 中授权
- 确保授予了正确的权限级别

### 2. "Package not found"
- 确保应用已在 Play Console 中创建
- 确保包名与 APK 一致

### 3. "Version code must be greater than"
- APK 的 versionCode 必须高于已发布版本
- 在 `app/build.gradle.kts` 中增加 `versionCode`

### 4. "APK is not signed"
- 必须使用 Release 签名编译 APK
- 运行 `./gradlew assembleRelease` 而不是 `assembleDebug`
