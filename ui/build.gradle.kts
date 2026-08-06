plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvmToolchain(11)
}

dependencies {
    // doc01: :ui may depend on everything below it and is the only module allowed to import Compose.
    implementation(projects.core.content)
    implementation(projects.core.rules)
    implementation(projects.data)

    implementation(compose.desktop.currentOs)
    implementation(compose.foundation)
    implementation(compose.material3)
}
