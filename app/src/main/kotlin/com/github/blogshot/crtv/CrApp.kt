package com.github.blogshot.crtv

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.launch

class CrApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Beim Aufwachen aus dem Standby das Token erneuern, bevor der erste Klick einen Request auslöst.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                container.scope.launch { container.session.refreshIfDue() }
                container.scope.launch { container.updater.checkIfDue() }
            }
        })
    }
}
