package com.medicapp

import android.app.Application
import com.medicapp.di.AppContainer

class MedicApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.appLock.startWatching()
    }
}
