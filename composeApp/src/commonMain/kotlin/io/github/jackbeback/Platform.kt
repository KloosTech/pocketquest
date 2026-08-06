package io.github.jackbeback

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform