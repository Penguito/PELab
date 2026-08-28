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
    ndkVersion = "29.0.14206865"

    defaultConfig {
        minSdk = 26

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }

        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    compileOnly("androidx.annotation:annotation:1.8.1")
}
