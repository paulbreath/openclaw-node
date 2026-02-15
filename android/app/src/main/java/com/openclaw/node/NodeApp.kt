package com.openclaw.node

import android.app.Application
import android.content.Context

class NodeApp : Application() {
    
    companion object {
        lateinit var instance: NodeApp
            private set
        
        fun getContext(): Context = instance.applicationContext
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}
