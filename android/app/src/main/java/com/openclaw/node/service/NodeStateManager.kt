package com.openclaw.node.service

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import java.net.URI
import java.util.concurrent.TimeUnit

class NodeStateManager private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "NodeStateManager"
        
        @Volatile
        private var instance: NodeStateManager? = null
        
        fun getInstance(context: Context): NodeStateManager {
            return instance ?: synchronized(this) {
                instance ?: NodeStateManager(context.applicationContext).also { instance = it }
            }
        }
    }
    
    data class ConnectionState(
        val isConnected: Boolean = false,
        val isConnecting: Boolean = false,
        val gatewayAddress: String = "",
        val error: String? = null
    )
    
    private val _connectionState = MutableStateFlow(ConnectionState())
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    private var webSocket: okhttp3.WebSocket? = null
    private var okHttpClient: okhttp3.OkHttpClient? = null
    private var serviceReady = false
    
    init {
        okHttpClient = okhttp3.OkHttpClient.Builder()
            .pingInterval(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
    }
    
    fun setServiceReady(ready: Boolean) {
        serviceReady = ready
        Log.d(TAG, "Service ready: $ready")
    }
    
    fun connect(address: String) {
        if (_connectionState.value.isConnecting || _connectionState.value.isConnected) {
            return
        }
        
        _connectionState.value = ConnectionState(isConnecting = true, gatewayAddress = address)
        
        try {
            val url = if (address.startsWith("ws://") || address.startsWith("wss://")) {
                address
            } else {
                "ws://$address"
            }
            
            val request = okhttp3.Request.Builder()
                .url(url)
                .build()
            
            webSocket = okHttpClient?.newWebSocket(request, object : okhttp3.WebSocketListener() {
                override fun onOpen(webSocket: okhttp3.WebSocket, response: okhttp3.Response) {
                    Log.d(TAG, "WebSocket connected")
                    _connectionState.value = ConnectionState(
                        isConnected = true,
                        gatewayAddress = address
                    )
                    
                    // 发送设备信息
                    val deviceInfo = JSONObject().apply {
                        put("type", "device_info")
                        put("manufacturer", android.os.Build.MANUFACTURER)
                        put("model", android.os.Build.MODEL)
                        put("androidVersion", android.os.Build.VERSION.RELEASE)
                        put("sdkVersion", android.os.Build.VERSION.SDK_INT)
                        put("packageName", context.packageName)
                    }
                    webSocket.send(deviceInfo.toString())
                }
                
                override fun onMessage(webSocket: okhttp3.WebSocket, text: String) {
                    Log.d(TAG, "Received: $text")
                    handleMessage(text)
                }
                
                override fun onClosing(webSocket: okhttp3.WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "WebSocket closing: $code $reason")
                }
                
                override fun onClosed(webSocket: okhttp3.WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "WebSocket closed: $code $reason")
                    _connectionState.value = ConnectionState(error = "连接已关闭")
                }
                
                override fun onFailure(webSocket: okhttp3.WebSocket, t: Throwable, response: okhttp3.Response?) {
                    Log.e(TAG, "WebSocket failure: ${t.message}")
                    _connectionState.value = ConnectionState(error = t.message ?: "连接失败")
                }
            })
            
        } catch (e: Exception) {
            Log.e(TAG, "Connect error: ${e.message}")
            _connectionState.value = ConnectionState(error = e.message ?: "连接异常")
        }
    }
    
    fun disconnect() {
        webSocket?.close(1000, "User disconnect")
        webSocket = null
        _connectionState.value = ConnectionState()
    }
    
    private fun handleMessage(text: String) {
        try {
            val json = JSONObject(text)
            val type = json.optString("type", "")
            
            when (type) {
                "command" -> {
                    val command = json.optString("command", "")
                    val params = json.optJSONObject("params") ?: JSONObject()
                    val requestId = json.optString("requestId", "")
                    
                    executeCommand(command, params, requestId)
                }
                "ping" -> {
                    webSocket?.send(JSONObject().put("type", "pong").toString())
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Parse message error: ${e.message}")
        }
    }
    
    private fun executeCommand(command: String, params: JSONObject, requestId: String) {
        val accessibilityService = NodeAccessibilityService.getInstance()
        
        val response = if (accessibilityService != null) {
            val result = accessibilityService.executeCommand(command, params)
            JSONObject().apply {
                put("type", "response")
                put("requestId", requestId)
                put("success", result.success)
                put("message", result.message)
                result.data?.let { put("data", JSONObject(it)) }
            }
        } else {
            JSONObject().apply {
                put("type", "response")
                put("requestId", requestId)
                put("success", false)
                put("message", "Accessibility service not ready")
            }
        }
        
        webSocket?.send(response.toString())
    }
}
