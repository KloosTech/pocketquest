package de.jackbeback.pocketquest.di

import org.koin.core.context.startKoin

/** Call once at application startup (before any Koin injection). */
fun initKoin() {
    startKoin {
        modules(gameModule)
    }
}
