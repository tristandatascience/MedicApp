package com.medicapp.ui

import com.medicapp.di.AppContainer
import androidx.compose.runtime.staticCompositionLocalOf

/** Conteneur d'injection manuelle exposé à toute l'interface. */
val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer non fourni") }
