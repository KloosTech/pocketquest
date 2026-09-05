import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Keeps the Android/iOS-bundled catalog snapshot in sync with the live-edited `content/catalog.json`
// on every Gradle configuration pass — desktop's own `:app:Main.kt` still reads that file directly
// off disk (so `:designer`'s hot-edit-without-rebuild workflow keeps working), but an installed
// Android/iOS app has no live filesystem to read from, so it needs its own bundled copy baked into
// Compose Resources instead. A plain file copy at configuration time rather than a dedicated Gradle
// task: Compose Multiplatform's resource-processing tasks are named per target combination and shift
// with target/AGP versions, so hooking a specific task name is more fragile than just always
// re-copying a single small JSON file before anything else runs.
rootDir.resolve("content/catalog.json").let { source ->
    if (source.exists()) {
        val dest = file("src/commonMain/composeResources/files/catalog.json")
        dest.parentFile.mkdirs()
        source.copyTo(dest, overwrite = true)
    }
}

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.serialization)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Ui"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            // doc01: :ui may depend on everything below it and is the only module allowed to import Compose.
            implementation(projects.core.content)
            implementation(projects.core.rules)
            implementation(projects.core.ai)
            implementation(projects.core.meta)
            implementation(projects.core.run)
            implementation(projects.core.progression)
            implementation(projects.data)

            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.animation)
            implementation(compose.ui)
            implementation(compose.material3)
            implementation(compose.components.resources)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(libs.androidx.activity.compose)
            }
        }
    }
}

compose.resources {
    packageOfResClass = "de.jackbeback.pocketquest.ui.generated.resources"
}

android {
    namespace = "de.jackbeback.pocketquest.ui"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
