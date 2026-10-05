package com.alexaplayer

import android.app.Application
import com.alexaplayer.di.AppContainer

class AlexaPlayerApp : Application() {

    /** Created eagerly: it is cheap (everything inside is lazy) and avoids a first-frame hit. */
    val container: AppContainer by lazy { AppContainer(this) }
}
