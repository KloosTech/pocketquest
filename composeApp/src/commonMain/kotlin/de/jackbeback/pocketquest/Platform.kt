package de.jackbeback.pocketquest

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform