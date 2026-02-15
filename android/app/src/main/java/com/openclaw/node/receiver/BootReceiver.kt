package com.openclaw.node.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.openclaw.node.service.NodeService

class BootReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "BootReceiver"
    }
    
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) return
        
        Log.d(TAG, "Received intent: ${intent?.action}")
        
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                Log.d(TAG, "Starting service on boot")
                startService(context)
            }
        }
    }
    
    private fun startService(context: Context) {
        try {
            val serviceIntent = Intent(context, NodeService::class.java).apply {
                action = NodeService.ACTION_START
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            
            Log.d(TAG, "Service started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start service: ${e.message}")
        }
    }
}
