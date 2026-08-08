import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
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
            baseName = "CoreRun"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            // docs/11-run-state.md's module diagram: :core:run starts/finishes encounters
            // (:core:rules) against catalog content (:core:content). Deliberately NOT
            // :core:meta — siblings, not parent/child (docs/10-game-loop.md); MemberId is its
            // own type here, not core:meta's ChampionId, so nothing forces that dependency.
            implementation(projects.core.rules)
            implementation(projects.core.content)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

android {
    namespace = "de.jackbeback.pocketquest.core.run"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
