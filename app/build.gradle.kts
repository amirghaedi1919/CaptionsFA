plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.daboua.captions"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.daboua.captions"
        minSdk = 26
        targetSdk = 35
        // هر بیلد جدید در GitHub یک شماره‌ی بزرگ‌تر می‌گیرد؛
        // اندروید فقط وقتی اجازه‌ی آپدیت می‌دهد که این عدد بیشتر شود.
        val buildNumber =
            System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"

        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                arguments += listOf(
                    "-DCMAKE_BUILD_TYPE=Release",
                    "-DWHISPER_BUILD_TESTS=OFF",
                    "-DWHISPER_BUILD_EXAMPLES=OFF",
                    "-DWHISPER_BUILD_SERVER=OFF",
                    "-DGGML_OPENMP=OFF"
                )
            }
        }

        ndk {
            abiFilters += listOf(
                "arm64-v8a",
                "armeabi-v7a"
            )
        }
    }

    // یک کلید امضای ثابت؛ بدون آن اندروید آپدیت را قبول نمی‌کند
    // و کاربر مجبور می‌شود برنامه را پاک و دوباره نصب کند.
    signingConfigs {
        create("captions") {
            storeFile = file("captions-release.jks")
            storePassword = "CaptionsFA2026"
            keyAlias = "captions"
            keyPassword = "CaptionsFA2026"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("captions")
        }
        debug {
            signingConfig = signingConfigs.getByName("captions")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.1")

    implementation(
        platform(
            "androidx.compose:compose-bom:2025.01.00"
        )
    )

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    implementation(
        "androidx.lifecycle:lifecycle-runtime-compose:2.8.7"
    )

    implementation(
        "androidx.media3:media3-exoplayer:1.5.1"
    )

    implementation(
        "androidx.media3:media3-ui:1.5.1"
    )
}
