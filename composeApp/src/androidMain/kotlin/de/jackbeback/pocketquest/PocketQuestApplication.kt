package de.jackbeback.pocketquest

import android.app.Application
import de.jackbeback.pocketquest.di.initKoin

class PocketQuestApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin()
    }
}
