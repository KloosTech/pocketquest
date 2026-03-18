package de.jackbeback.pocketquest

class JvmPlatform : Platform {
    override val name = "Desktop (${System.getProperty("os.name")})"
}

actual fun getPlatform(): Platform = JvmPlatform()
