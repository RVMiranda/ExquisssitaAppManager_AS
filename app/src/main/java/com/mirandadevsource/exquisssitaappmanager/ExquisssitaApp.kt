package com.mirandadevsource.exquisssitaappmanager

import android.app.Application
import com.mirandadevsource.exquisssitaappmanager.data.sync.ReconnectSyncCoordinator
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class ExquisssitaApp : Application() {
    @Inject lateinit var reconnectSyncCoordinator: ReconnectSyncCoordinator

    override fun onCreate() {
        super.onCreate()
        reconnectSyncCoordinator.start()
    }
}
