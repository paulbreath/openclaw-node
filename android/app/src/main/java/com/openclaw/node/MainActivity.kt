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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openclaw.node.service.NodeService
import com.openclaw.node.service.NodeStateManager
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val serviceIntent = Intent(this, NodeService::class.java).apply {
            action = NodeService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        
        setContent {
            OpenClawTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NodeScreen()
                }
            }
        }
    }
}

@Composable
fun OpenClawTheme(content: @Composable () -> Unit) {
    val colorScheme = lightColorScheme(
        primary = Color(0xFF2563EB),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFDBEAFE),
        secondary = Color(0xFF7C3AED),
        tertiary = Color(0xFF059669),
        background = Color(0xFFF8FAFC),
        surface = Color.White,
        error = Color(0xFFDC2626),
        onError = Color.White
    )
    
    MaterialTheme(
        colorScheme = colorScheme,
        typography = MaterialTheme.typography,
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("openclaw_node", Context.MODE_PRIVATE) }
    
    var gatewayAddress by remember { 
        mutableStateOf(prefs.getString("last_gateway", "") ?: "") 
    }
    var isConnected by remember { mutableStateOf(false) }
    var isConnecting by remember { mutableStateOf(false) }
    var connectionError by remember { mutableStateOf<String?>(null) }
    var accessibilityEnabled by remember { mutableStateOf(false) }
    var showDeviceInfo by remember { mutableStateOf(false) }
    
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
    
    LaunchedEffect(Unit) {
        accessibilityEnabled = checkAccessibilityEnabled()
    }
    
    DisposableEffect(Unit) {
        accessibilityEnabled = checkAccessibilityEnabled()
        onDispose { }
    }
    
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
    
    val deviceManufacturer = Build.MANUFACTURER.lowercase()
    val deviceBrand = Build.BRAND.lowercase()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // === Header ===
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                        )
                    )
                )
                .padding(horizontal = 24.dp, vertical = 32.dp)
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Devices,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "OpenClaw Node",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "AI Automation Bridge",
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Status indicator
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.15f)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isConnected -> Color(0xFF22C55E)
                                        isConnecting -> Color(0xFFF59E0B)
                                        else -> Color(0xFFEF4444)
                                    }
                                )
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = when {
                                isConnected -> "Connected to Gateway"
                                isConnecting -> "Connecting..."
                                accessibilityEnabled -> "Ready to connect"
                                else -> "Setup required"
                            },
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
        
        // === Content ===
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // === Accessibility Card ===
            StatusCard(
                icon = if (accessibilityEnabled) Icons.Default.CheckCircle else Icons.Default.Accessibility,
                iconTint = if (accessibilityEnabled) Color(0xFF22C55E) else Color(0xFFF59E0B),
                title = if (accessibilityEnabled) "Accessibility Enabled" else "Enable Accessibility",
                subtitle = if (accessibilityEnabled) 
                    "Service is ready" 
                else 
                    "Required for automation",
                isComplete = accessibilityEnabled
            ) {
                if (!accessibilityEnabled) {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = {
                            try {
                                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            } catch (e: Exception) {
                                Toast.makeText(context, "Cannot open settings", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open Accessibility Settings")
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Vendor hint
                    val vendorHint = when {
                        deviceManufacturer.contains("xiaomi") || deviceBrand.contains("xiaomi") -> 
                            "Settings → Accessibility → Installed services → OpenClaw Node"
                        deviceManufacturer.contains("huawei") || deviceBrand.contains("huawei") -> 
                            "Settings → Accessibility → Accessibility → OpenClaw Node"
                        deviceManufacturer.contains("samsung") -> 
                            "Settings → Accessibility → Installed services → OpenClaw Node"
                        else -> 
                            "Find OpenClaw Node in Accessibility settings"
                    }
                    
                    Text(
                        text = "💡 $vendorHint",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF64748B)
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    TextButton(
                        onClick = { accessibilityEnabled = checkAccessibilityEnabled() }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Refresh Status")
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // === Connection Card ===
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = "Gateway Connection",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OutlinedTextField(
                        value = gatewayAddress,
                        onValueChange = { gatewayAddress = it },
                        label = { Text("Gateway Address") },
                        placeholder = { Text("localhost:18789") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Go
                        ),
                        keyboardActions = KeyboardActions(
                            onGo = {
                                if (gatewayAddress.isNotBlank() && accessibilityEnabled && !isConnecting) {
                                    prefs.edit().putString("last_gateway", gatewayAddress).apply()
                                    scope.launch { stateManager.connect(gatewayAddress) }
                                }
                            }
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = accessibilityEnabled && !isConnecting,
                        leadingIcon = {
                            Icon(Icons.Default.Router, contentDescription = null)
                        },
                        shape = RoundedCornerShape(10.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Button(
                        onClick = {
                            if (gatewayAddress.isNotBlank()) {
                                prefs.edit().putString("last_gateway", gatewayAddress).apply()
                                scope.launch { stateManager.connect(gatewayAddress) }
                            } else {
                                Toast.makeText(context, "Enter gateway address", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = accessibilityEnabled && !isConnecting && gatewayAddress.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isConnected) Color(0xFF22C55E) 
                                             else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        if (isConnecting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Connecting...")
                        } else {
                            Icon(
                                if (isConnected) Icons.Default.Check else Icons.Default.Link,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isConnected) "Connected" else "Connect")
                        }
                    }
                    
                    if (isConnected) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { stateManager.disconnect() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFFEF4444)
                            )
                        ) {
                            Icon(Icons.Default.LinkOff, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Disconnect")
                        }
                    }
                    
                    // Error message
                    connectionError?.let { error ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFFEF2F2)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = error,
                                    color = Color(0xFFDC2626),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // === Device Info (Collapsible) ===
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Device Information",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        IconButton(onClick = { showDeviceInfo = !showDeviceInfo }) {
                            Icon(
                                if (showDeviceInfo) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null
                            )
                        }
                    }
                    
                    if (showDeviceInfo) {
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        InfoRow("Brand", Build.BRAND)
                        InfoRow("Model", Build.MODEL)
                        InfoRow("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                        InfoRow(
                            "Service", 
                            if (NodeService.isRunning()) "Running" else "Stopped",
                            valueColor = if (NodeService.isRunning()) Color(0xFF22C55E) else Color(0xFFEF4444)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // === Features List ===
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Supported Actions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    val features = listOf(
                        "Tap & Swipe gestures" to Icons.Filled.TouchApp,
                        "Text input" to Icons.Filled.Edit,
                        "Screen dump" to Icons.Filled.ViewAgenda,
                        "Screenshot (Android 11+)" to Icons.Filled.Image,
                        "Navigation (Back/Home/Recent)" to Icons.Filled.Menu
                    )
                    
                    features.forEach { feature ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                feature.second,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = feature.first,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
            
            // === Footer ===
            Spacer(modifier = Modifier.height(24.dp))
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "OpenClaw Node v1.0.0",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF94A3B8)
                )
                Text(
                    text = "Auto-starts on device boot",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF94A3B8)
                )
            }
            
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun StatusCard(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    isComplete: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isComplete) Color(0xFFF0FDF4) else Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(iconTint.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF64748B)
                    )
                }
            }
            content()
        }
    }
}

@Composable
fun InfoRow(label: String, value: String, valueColor: Color = Color.Unspecified) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF64748B)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = valueColor
        )
    }
}
