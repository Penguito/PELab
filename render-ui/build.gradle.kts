import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
}

android {
    namespace = "com.penguito.effectlab.render.ui"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation(project(":render-core-camera"))
    implementation(project(":render-core-permission"))
    implementation(project(":render-core-material"))
    implementation(project(":render-sdk"))
}
