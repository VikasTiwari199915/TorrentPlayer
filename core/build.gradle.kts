plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.vikas.torrentplayer.core"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        // Lowest common denominator: Android TV is API 30 (Android 11).
        minSdk = 30

        ndk {
            //noinspection ChromeOsAbiSupport
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    // Pull through transitively so consumers don't have to repeat themselves.
    api(libs.appcompat)
    api(libs.lifecycle.viewmodel)
    api(libs.lifecycle.livedata)
    api(libs.lifecycle.runtime)

    api(libs.retrofit)
    api(libs.retrofit.converter.gson)
    api(libs.okhttp)
    implementation(libs.okhttp.logging)
    api(libs.gson)

    api(libs.media3.exoplayer)

    // Room — engine persistence
    api(libs.room.runtime)
    annotationProcessor(libs.room.compiler)

    // libtorrent4j — engine + per-ABI natives
    api(libs.libtorrent4j)
    implementation(libs.libtorrent4j.arm64)
    implementation(libs.libtorrent4j.arm32)
    implementation(libs.libtorrent4j.x86)
    implementation(libs.libtorrent4j.x8664)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
}
