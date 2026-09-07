package com.becky.bridge

import android.app.Application
import com.becky.bridge.logging.BeckyLogger
import com.becky.bridge.logging.LogCategory

/** Application entry point. Only used to log app startup (section 13). */
class BeckyBridgeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        BeckyLogger.i(LogCategory.APP, "BECKY BRIDGE iniciado")
    }
}
