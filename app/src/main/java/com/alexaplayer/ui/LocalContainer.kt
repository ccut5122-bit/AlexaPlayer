package com.alexaplayer.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.alexaplayer.di.AppContainer

/**
 * The dependency graph, made reachable from any composable. Provided once by
 * [com.alexaplayer.ui.AlexaPlayerRoot] so screens and previews never reach for a global.
 */
val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer was not provided")
}