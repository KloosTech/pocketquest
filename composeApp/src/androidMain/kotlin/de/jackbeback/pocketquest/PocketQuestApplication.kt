package de.jackbeback.pocketquest

import android.app.Application
import androidx.room.Room
import de.jackbeback.pocketquest.data.db.PocketQuestDatabase
import de.jackbeback.pocketquest.di.initKoin

class PocketQuestApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val appContext = applicationContext
        initKoin {
            Room.databaseBuilder<PocketQuestDatabase>(
                context = appContext,
                name = "pocketquest.db",
            ).build()
        }
    }
}
