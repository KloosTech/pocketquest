plugins {
    alias(libs.plugins.kotlinJvm)
    application
}

kotlin {
    jvmToolchain(11)
}

dependencies {
    // Only :ui — matches doc01's target graph (:app -> :ui only). :app never imports Compose
    // itself; ui/App.kt's runDesktopApp(log: List<String>) is the only surface it touches.
    implementation(projects.ui)

    // Needed directly to build the demo scenario and drive persistence — the "DI wiring" doc01
    // assigns to :app.
    implementation(projects.core.content)
    implementation(projects.core.rules)
    implementation(projects.data)
    implementation(libs.room.runtime)
    implementation(libs.sqlite.bundled)
    implementation(libs.kotlinx.coroutines.core)
}

application {
    mainClass.set("de.jackbeback.pocketquest.app.MainKt")
}
