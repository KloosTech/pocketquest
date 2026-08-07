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
            baseName = "CoreRules"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            // api, not implementation: Resolver/Effect/GameEvent etc. expose core:model types
            // directly in :core:rules' own public API, so consumers (like :data) need them too.
            api(projects.core.model)
            implementation(libs.kotlinx.serialization.core)
            // Used by fizzle() to derive a Fizzled event's effect label from the effect's own
            // polymorphic @SerialName rather than effect::class.simpleName, which R8 renames in
            // release builds — see KNOWN_ISSUES.md #9.
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.serialization.json)
        }
        val desktopTest by getting {
            dependencies {
                implementation(libs.kotlin.reflect)
            }
        }
    }
}

android {
    namespace = "de.jackbeback.pocketquest.core.rules"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
