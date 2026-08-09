plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.serialization)
    application
}

kotlin {
    jvmToolchain(11)
}

dependencies {
    // doc16: "the desktop target can depend on :core:rules, so the encounter editor can actually
    // run preview() on an action and show the numbers" — a deliberate, doc-sanctioned exception to
    // doc01 rule 6's ":ui is the only module allowed to import Compose": that rule governs the
    // player-facing :app/:ui graph, :designer is a sibling authoring tool outside it, same as how
    // v1's own designer was never part of its player-facing app either.
    implementation(projects.core.model)
    implementation(projects.core.rules)
    implementation(projects.core.content)
    implementation(libs.kotlinx.serialization.json)
    // The user asked for an in-place "Playtest" launcher rather than a separate build/run step —
    // that means opening the real battle window, which means depending on :ui itself (its desktop
    // target), pulling in :data/:core:ai transitively too. A further, larger extension of the same
    // doc16-sanctioned exception above: :designer stays outside the player-facing graph, but now
    // also consumes it, the same way v1's designer previewed real gameplay screens.
    implementation(projects.ui)
    implementation(libs.kotlinx.coroutines.core)

    implementation(compose.desktop.currentOs)
    implementation(compose.foundation)
    implementation(compose.runtime)
    implementation(compose.ui)
}

application {
    mainClass.set("de.jackbeback.pocketquest.designer.MainKt")
}
