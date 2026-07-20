plugins {
    id("com.android.library")
}

android {
    namespace = "com.penguito.effectlab.render.sdk"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }
    enableKotlin = false

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}
