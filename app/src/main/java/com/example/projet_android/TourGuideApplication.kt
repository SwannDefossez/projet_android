package com.example.projet_android

import android.app.Application
import com.example.projet_android.di.AppContainer

class TourGuideApplication : Application() {
    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
    }
}
