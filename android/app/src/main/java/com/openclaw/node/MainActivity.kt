package com.openclaw.node

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openclaw.node.service.NodeService
import com.openclaw.node.service.NodeStateManager
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 启动前台服务
        val serviceIntent = Intent(this, NodeService::class.java).apply {
            action = NodeService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        
        setContent {
            MaterialTheme {
                NodeScreen()
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        // 每次回到应用时刷新UI
    }
}

@Composable
fun NodeScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("openclaw_node", Context.MODE_PRIVATE) }
    
    // 状态
    var gatewayAddress by remember { 
        mutableStateOf(prefs.getString("last_gateway", "") ?: "") 
    }
    var isConnected by remember { mutableStateOf(false) }
    var isConnecting by remember { mutableStateOf(false) }
    var connectionError by remember { mutableStateOf<String?>(null) }
    
    // 检查无障碍服务是否启用
    var accessibilityEnabled by remember { mutableStateOf(false) }
    
    fun checkAccessibilityEnabled(): Boolean {
        return try {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            
            val serviceName = "${context.packageName}/com.openclaw.node.service.NodeAccessibilityService"
            enabledServices.contains(serviceName) || enabledServices.contains(context.packageName)
        } catch (e: Exception) {
            false
        }
    }
    
    // 初始检查
    LaunchedEffect(Unit) {
        accessibilityEnabled = checkAccessibilityEnabled()
    }
    
    // 每次恢复时重新检查
    DisposableEffect(Unit) {
        accessibilityEnabled = checkAccessibilityEnabled()
        onDispose { }
    }
    
    // 监听连接状态
    val stateManager = remember { NodeStateManager.getInstance(context) }
    LaunchedEffect(stateManager) {
        stateManager.connectionState.collect { state ->
            isConnected = state.isConnected
            isConnecting = state.isConnecting
            connectionError = state.error
            
            if (state.isConnected) {
                prefs.edit().putString("last_gateway", state.gatewayAddress).apply()
            }
        }
    }
    
    // 设备信息
    val deviceManufacturer = Build.MANUFACTURER.lowercase()
    val deviceBrand = Build.BRAND.lowercase()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Logo 和标题
        Text(
            text = "🤖 OpenClaw Node",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(top = 24.dp, bottom = 4.dp)
        )
        
        Text(
            text = "连接到 Gateway 开始控制",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // === 无障碍服务状态卡片 ===
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (accessibilityEnabled) Color(0xFFE8F5E9) 
                                 else Color(0xFFFFEBEE)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (accessibilityEnabled) "✅" else "⚠️",
                        fontSize = 28.sp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (accessibilityEnabled) "无障碍服务已启用" 
                                   else "请开启无障碍服务",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = if (accessibilityEnabled) "可以开始连接" 
                                   else "点击下方按钮开启",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }
                
                if (!accessibilityEnabled) {
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // 主按钮 - 打开无障碍设置
                    Button(
                        onClick = {
                            try {
                                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "无法打开设置", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1565C0)
                        )
                    ) {
                        Text("打开无障碍设置")
                    }
                    
                    // 厂商特殊说明
                    val note = when {
                        deviceManufacturer.contains("xiaomi") || deviceBrand.contains("xiaomi") -> 
                            "小米手机：设置 → 无障碍 → 已安装的服务 → OpenClaw Node"
                        deviceManufacturer.contains("huawei") || deviceBrand.contains("huawei") -> 
                            "华为手机：设置 → 辅助功能 → 无障碍 → OpenClaw Node"
                        deviceManufacturer.contains("oppo") || deviceBrand.contains("oppo") -> 
                            "OPPO手机：设置 → 其他设置 → 无障碍 → OpenClaw Node"
                        deviceManufacturer.contains("vivo") || deviceBrand.contains("vivo") -> 
                            "vivo手机：设置 → 快捷与辅助 → 无障碍 → OpenClaw Node"
                        deviceManufacturer.contains("samsung") -> 
                            "三星手机：设置 → 辅助功能 → 已安装的服务 → OpenClaw Node"
                        else -> "在无障碍设置中找到 OpenClaw Node 并开启"
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "💡 $note",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF666666)
                    )
                    
                    // 刷新按钮
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = { accessibilityEnabled = checkAccessibilityEnabled() }
                    ) {
                        Text("🔄 刷新状态")
                    }
                } else {
                    // 显示已启用的服务信息
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "✓ 服务已就绪，可以连接 Gateway",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF2E7D32)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        // === 连接区域 ===
        OutlinedTextField(
            value = gatewayAddress,
            onValueChange = { gatewayAddress = it },
            label = { Text("Gateway 地址") },
            placeholder = { Text("192.168.1.100:18789 或 localhost:18789") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
            enabled = accessibilityEnabled && !isConnecting
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // 连接按钮
        Button(
            onClick = {
                if (gatewayAddress.isNotBlank()) {
                    prefs.edit().putString("last_gateway", gatewayAddress).apply()
                    isConnecting = true
                    connectionError = null
                    scope.launch {
                        stateManager.connect(gatewayAddress)
                    }
                } else {
                    Toast.makeText(context, "请输入Gateway地址", Toast.LENGTH_SHORT).show()
                }
            },
            enabled = accessibilityEnabled && !isConnecting && gatewayAddress.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isConnected) Color(0xFF4CAF50) 
                                 else MaterialTheme.colorScheme.primary
            )
        ) {
            if (isConnecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("连接中...")
            } else {
                Text(if (isConnected) "✓ 已连接" else "🔗 连接 Gateway")
            }
        }
        
        // 断开按钮
        if (isConnected) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { stateManager.disconnect() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("断开连接")
            }
        }
        
        // 错误提示
        connectionError?.let { error ->
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))
            ) {
                Text(
                    text = "⚠️ $error",
                    color = Color(0xFFE65100),
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
        
        // === 设备信息 ===
        Spacer(modifier = Modifier.height(20.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text("设备信息", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                
                RowInfo("品牌", Build.BRAND)
                RowInfo("型号", Build.MODEL)
                RowInfo("Android", Build.VERSION.RELEASE)
                RowInfo("SDK", Build.VERSION.SDK_INT.toString())
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 服务状态
                RowInfo("后台服务", if (NodeService.isRunning()) "✅ 运行中" else "❌ 未运行")
            }
        }
        
        Spacer(modifier = Modifier.weight(1f, fill = false))
        
        // === 底部信息 ===
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 20.dp, bottom = 16.dp)
        ) {
            Text(
                text = "OpenClaw Node v1.0.0",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
            Text(
                text = "重启手机后会自动启动服务",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }
    }
}

@Composable
fun RowInfo(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
        Text(text = value, style = MaterialTheme.typography.bodySmall)
    }
}
