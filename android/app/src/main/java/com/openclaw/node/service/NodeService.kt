package com.openclaw.node.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.openclaw.node.MainActivity
import com.openclaw.node.R

class NodeService : Service() {
    
    companion object {
        const val ACTION_START = "com.openclaw.node.action.START"
        const val ACTION_CHECK_ACCESSIBILITY = "com.openclaw.node.action.CHECK_ACCESSIBILITY"
        const val NOTIFICATION_CHANNEL_ID = "openclaw_node_channel"
        const val NOTIFICATION_ID = 1001
        
        @Volatile
        private var running = false
        
        fun isRunning(): Boolean = running
    }
    
    private var accessibilityCheckRunnable: Runnable? = null
    
    override fun onCreate() {
        super.onCreate()
        running = true
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("正在运行"))
        startAccessibilityMonitor()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CHECK_ACCESSIBILITY -> {
                checkAccessibilityAndNotify()
            }
        }
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        running = false
        accessibilityCheckRunnable?.let {
            android.os.Handler(mainLooper).removeCallbacks(it)
        }
        super.onDestroy()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "OpenClaw Node 服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "显示 OpenClaw Node 运行状态"
                setShowBadge(false)
            }
            
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(status: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("OpenClaw Node")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
    
    private fun startAccessibilityMonitor() {
        accessibilityCheckRunnable = object : Runnable {
            override fun run() {
                checkAccessibilityAndNotify()
                android.os.Handler(mainLooper).postDelayed(this, 5000)
            }
        }
        android.os.Handler(mainLooper).postDelayed(accessibilityCheckRunnable!!, 1000)
    }
    
    private fun checkAccessibilityAndNotify() {
        val enabled = isAccessibilityEnabled()
        val status = if (enabled) "运行中 - 无障碍已启用" else "运行中 - 请开启无障碍"
        
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, createNotification(status))
    }
    
    private fun isAccessibilityEnabled(): Boolean {
        return try {
            val enabledServices = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            
            val serviceName = "$packageName/com.openclaw.node.service.NodeAccessibilityService"
            enabledServices.contains(serviceName) || enabledServices.contains(packageName)
        } catch (e: Exception) {
            false
        }
    }
}
