import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
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
            baseName = "CoreProgression"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            // The one module allowed to depend on both :core:meta and :core:run — they're
            // deliberately siblings (docs/10-game-loop.md), so the code that ties a finished
            // RunState back into MetaState has to live somewhere that isn't either of them.
            implementation(projects.core.model)
            implementation(projects.core.meta)
            implementation(projects.core.run)
            implementation(projects.core.content)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

android {
    namespace = "de.jackbeback.pocketquest.core.progression"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
