plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.vikas.torrentplayer.tv"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.vikas.torrentplayer.tv"
        // TV minSdk — the device you have is API 30 (Android 11).
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        ndk {
            //noinspection ChromeOsAbiSupport
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    packaging {
        resources {
            excludes += listOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/*.kotlin_module"
            )
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation(project(":core"))

    // Leanback — TV UI toolkit
    implementation(libs.leanback)
    implementation(libs.leanback.preference)
    implementation(libs.tvprovider)

    // Image loading
    implementation(libs.glide)

    // ExoPlayer UI (leanback variant has a TV-style controller)
    implementation(libs.media3.ui)
    implementation(libs.media3.session)
}
