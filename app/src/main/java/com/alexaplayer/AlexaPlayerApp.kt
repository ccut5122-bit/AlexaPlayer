package com.alexaplayer

import android.app.Application
import com.alexaplayer.di.AppContainer
import com.alexaplayer.util.CrashLog

class AlexaPlayerApp : Application() {

    /** Created eagerly: it is cheap (everything inside is lazy) and avoids a first-frame hit. */
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            CrashLog.write(this, throwable, "uncaught")
            previous?.uncaughtException(thread, throwable)
        }
    }
}
