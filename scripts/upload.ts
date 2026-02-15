/**
 * Google Play Developer API 上传脚本
 * 
 * 使用方法:
 * 1. 创建 Google Cloud 项目并启用 Google Play Developer API
 * 2. 创建服务账号并下载 JSON 密钥
 * 3. 在 Google Play Console 中链接服务账号
 * 4. 运行此脚本
 */

import { google } from 'googleapis';
import fs from 'fs';
import path from 'path';

// 配置
const CONFIG = {
    packageName: 'com.openclaw.node',
    serviceAccountPath: './google-play-service-account.json',
    apkPath: '../download/OpenClawNode.apk',
    track: 'internal', // internal, alpha, beta, production
};

interface ServiceAccount {
    client_email: string;
    private_key: string;
    project_id: string;
}

async function main() {
    console.log('');
    console.log('========================================');
    console.log('  OpenClaw Node - Google Play 上传工具');
    console.log('========================================');
    console.log('');

    // 检查服务账号文件
    if (!fs.existsSync(CONFIG.serviceAccountPath)) {
        console.error('❌ 未找到服务账号文件:', CONFIG.serviceAccountPath);
        console.log('');
        console.log('请创建服务账号并下载 JSON 密钥:');
        console.log('1. https://console.cloud.google.com/iam-admin/serviceaccounts');
        console.log('2. 创建服务账号');
        console.log('3. 创建 JSON 密钥');
        console.log('4. 保存到:', CONFIG.serviceAccountPath);
        console.log('5. 在 Play Console 中授权服务账号');
        process.exit(1);
    }

    // 读取服务账号
    const serviceAccount: ServiceAccount = JSON.parse(
        fs.readFileSync(CONFIG.serviceAccountPath, 'utf-8')
    );

    console.log('📧 服务账号:', serviceAccount.client_email);
    console.log('📦 应用包名:', CONFIG.packageName);
    console.log('🚀 发布轨道:', CONFIG.track);
    console.log('');

    // 检查 APK
    const apkPath = path.resolve(CONFIG.apkPath);
    if (!fs.existsSync(apkPath)) {
        console.error('❌ 未找到 APK:', apkPath);
        process.exit(1);
    }

    const apkStats = fs.statSync(apkPath);
    console.log('📁 APK 文件:', apkPath);
    console.log('📊 APK 大小:', (apkStats.size / 1024 / 1024).toFixed(2), 'MB');
    console.log('');

    // 创建 JWT 客户端
    console.log('🔐 正在认证...');
    
    const jwtClient = new google.auth.JWT(
        serviceAccount.client_email,
        undefined,
        serviceAccount.private_key,
        ['https://www.googleapis.com/auth/androidpublisher']
    );

    await jwtClient.authorize();
    console.log('✅ 认证成功');
    console.log('');

    // 创建 Android Publisher 客户端
    const androidPublisher = google.androidpublisher({
        version: 'v3',
        auth: jwtClient,
    });

    try {
        // 步骤 1: 创建 Edit
        console.log('📝 正在创建 Edit...');
        
        const editResponse = await androidPublisher.edits.insert({
            packageName: CONFIG.packageName,
        });

        const editId = editResponse.data.id;
        console.log('✅ Edit ID:', editId);
        console.log('');

        // 步骤 2: 上传 APK
        console.log('📤 正在上传 APK...');
        
        const apkResponse = await androidPublisher.edits.apks.upload({
            packageName: CONFIG.packageName,
            editId: editId!,
            media: {
                mimeType: 'application/vnd.android.package-archive',
                body: fs.createReadStream(apkPath),
            },
        });

        const versionCode = apkResponse.data.versionCode;
        console.log('✅ APK 上传成功');
        console.log('   Version Code:', versionCode);
        console.log('');

        // 步骤 3: 更新 Track
        console.log('🛤️  正在更新 Track...');
        
        await androidPublisher.edits.tracks.update({
            packageName: CONFIG.packageName,
            editId: editId!,
            track: CONFIG.track,
            requestBody: {
                track: CONFIG.track,
                releases: [{
                    versionCodes: [versionCode!.toString()],
                    status: 'completed',
                    releaseNotes: [
                        {
                            language: 'en-US',
                            text: 'Initial release\n\n• Modern Material Design 3 UI\n• WebSocket connection to Gateway\n• Auto-start on device boot\n• Multi-language support (English/Chinese)',
                        },
                        {
                            language: 'zh-CN',
                            text: '首次发布\n\n• 现代 Material Design 3 界面\n• WebSocket 连接到 Gateway\n• 开机自启动\n• 多语言支持（英文/中文）',
                        }
                    ],
                }],
            },
        });

        console.log('✅ Track 更新成功');
        console.log('');

        // 步骤 4: 提交 Edit
        console.log('🚀 正在提交发布...');
        
        await androidPublisher.edits.commit({
            packageName: CONFIG.packageName,
            editId: editId!,
        });

        console.log('');
        console.log('========================================');
        console.log('  ✅ 发布成功！');
        console.log('========================================');
        console.log('');
        console.log('📍 查看发布状态:');
        console.log('   https://play.google.com/console/developers');
        console.log('');

    } catch (error: any) {
        console.error('');
        console.error('❌ 发布失败:', error.message);
        
        if (error.response?.data?.error?.message) {
            console.error('   详情:', error.response.data.error.message);
        }
        
        console.log('');
        console.log('常见问题:');
        console.log('1. 服务账号未在 Play Console 中授权');
        console.log('2. 应用尚未在 Play Console 中创建');
        console.log('3. 包名不匹配');
        console.log('4. 版本号低于已发布的版本');
        console.log('');
        
        process.exit(1);
    }
}

main();
