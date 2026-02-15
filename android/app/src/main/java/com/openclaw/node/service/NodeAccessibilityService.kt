package com.openclaw.node.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONObject

class NodeAccessibilityService : AccessibilityService() {
    
    companion object {
        private const val TAG = "NodeAccessibility"
        
        @Volatile
        private var instance: NodeAccessibilityService? = null
        
        fun getInstance(): NodeAccessibilityService? = instance
        
        fun isServiceEnabled(): Boolean = instance != null
    }
    
    private var lastRootNode: AccessibilityNodeInfo? = null
    private var lastUpdateTime: Long = 0
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "Accessibility service connected")
        NodeStateManager.getInstance(this).setServiceReady(true)
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event?.let {
            if (it.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                it.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
                updateCache()
            }
        }
    }
    
    override fun onInterrupt() {}
    
    override fun onDestroy() {
        super.onDestroy()
        instance = null
        NodeStateManager.getInstance(this).setServiceReady(false)
    }
    
    private fun updateCache() {
        rootInActiveWindow?.let {
            lastRootNode = it
            lastUpdateTime = System.currentTimeMillis()
        }
    }
    
    fun getRootNode(): AccessibilityNodeInfo? {
        // Direct access
        rootInActiveWindow?.let { return it }
        
        // Cached
        if (lastRootNode != null && System.currentTimeMillis() - lastUpdateTime < 5000) {
            return lastRootNode
        }
        
        // Windows list
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            windows?.forEach { it.root?.let { node -> return node } }
        }
        
        // Retry
        repeat(3) {
            Thread.sleep(100)
            rootInActiveWindow?.let { return it }
        }
        
        return null
    }
    
    // === Command Execution ===
    
    fun executeCommand(command: String, params: JSONObject): CommandResult {
        Log.d(TAG, "Execute: $command")
        
        return when (command) {
            "tap" -> executeTap(params)
            "swipe" -> executeSwipe(params)
            "type" -> executeType(params)
            "screenshot" -> executeScreenshot()
            "dump" -> executeDump()
            "back" -> result(performGlobalAction(GLOBAL_ACTION_BACK), "back")
            "home" -> result(performGlobalAction(GLOBAL_ACTION_HOME), "home")
            "recent" -> result(performGlobalAction(GLOBAL_ACTION_RECENTS), "recent")
            "notifications" -> result(performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS), "notifications")
            "quickSettings" -> result(performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS), "quickSettings")
            "powerDialog" -> result(performGlobalAction(GLOBAL_ACTION_POWER_DIALOG), "powerDialog")
            "lockScreen" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    result(performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN), "lockScreen")
                } else {
                    CommandResult(false, "Requires Android 9.0+")
                }
            }
            "takeScreenshot" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    result(performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT), "takeScreenshot")
                } else {
                    CommandResult(false, "Requires Android 11+")
                }
            }
            else -> CommandResult(false, "Unknown command: $command")
        }
    }
    
    private fun executeTap(params: JSONObject): CommandResult {
        val x = params.optInt("x", 0)
        val y = params.optInt("y", 0)
        
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return CommandResult(false, "Requires Android 7.0+")
        }
        
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()
        
        return result(dispatchGesture(gesture, null, null), "tap at ($x, $y)")
    }
    
    private fun executeSwipe(params: JSONObject): CommandResult {
        val startX = params.optInt("startX", 0)
        val startY = params.optInt("startY", 0)
        val endX = params.optInt("endX", 0)
        val endY = params.optInt("endY", 0)
        val duration = params.optLong("duration", 300)
        
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return CommandResult(false, "Requires Android 7.0+")
        }
        
        val path = Path().apply {
            moveTo(startX.toFloat(), startY.toFloat())
            lineTo(endX.toFloat(), endY.toFloat())
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()
        
        return result(dispatchGesture(gesture, null, null), "swipe")
    }
    
    private fun executeType(params: JSONObject): CommandResult {
        val text = params.optString("text", "")
        val root = getRootNode() ?: return CommandResult(false, "No active window")
        
        // Find focused editable node
        val focusNode = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focusNode != null && focusNode.isEditable) {
            val arguments = android.os.Bundle()
            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            val success = focusNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            return result(success, "type: $text")
        }
        
        return CommandResult(false, "No focused input field")
    }
    
    private fun executeScreenshot(): CommandResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return CommandResult(false, "Requires Android 11+")
        }
        
        var success = false
        var errorMsg: String? = null
        val latch = java.util.concurrent.CountDownLatch(1)
        
        try {
            takeScreenshot(android.view.Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) {
                    success = true
                    result.hardwareBuffer?.close()
                    latch.countDown()
                }
                
                override fun onFailure(errorCode: Int) {
                    errorMsg = "Error code: $errorCode"
                    latch.countDown()
                }
            })
            
            latch.await(5, java.util.concurrent.TimeUnit.SECONDS)
        } catch (e: Exception) {
            return CommandResult(false, "Screenshot exception: ${e.message}")
        }
        
        return if (success) CommandResult(true, "Screenshot captured")
               else CommandResult(false, errorMsg ?: "Screenshot failed")
    }
    
    private fun executeDump(): CommandResult {
        val root = getRootNode() ?: return CommandResult(false, "No active window")
        val nodes = mutableListOf<Map<String, Any?>>()
        dumpNode(root, nodes, 0)
        return CommandResult(true, "UI dumped", mapOf("nodes" to nodes))
    }
    
    private fun dumpNode(node: AccessibilityNodeInfo, list: MutableList<Map<String, Any?>>, depth: Int) {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        
        list.add(mapOf(
            "className" to (node.className?.toString() ?: ""),
            "text" to (node.text?.toString() ?: ""),
            "contentDescription" to (node.contentDescription?.toString() ?: ""),
            "resourceId" to (node.viewIdResourceName ?: ""),
            "bounds" to mapOf(
                "left" to bounds.left,
                "top" to bounds.top,
                "right" to bounds.right,
                "bottom" to bounds.bottom
            ),
            "clickable" to node.isClickable,
            "enabled" to node.isEnabled,
            "focusable" to node.isFocusable,
            "scrollable" to node.isScrollable,
            "editable" to node.isEditable,
            "depth" to depth
        ))
        
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { dumpNode(it, list, depth + 1) }
        }
    }
    
    private fun result(success: Boolean, action: String): CommandResult {
        return if (success) CommandResult(true, "$action success")
               else CommandResult(false, "$action failed")
    }
}

data class CommandResult(
    val success: Boolean,
    val message: String,
    val data: Map<String, Any?>? = null
)
