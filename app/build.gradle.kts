plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.vikas.torrentplayer"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.vikas.torrentplayer"
        minSdk = 31
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.1"

        ndk {
            //noinspection ChromeOsAbiSupport
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    // Signing config picks up the keystore that the GitHub Actions workflow
    // decodes to <rootDir>/keystore.jks before running ./gradlew. Locally —
    // where keystore.jks doesn't exist — the create() block silently no-ops
    // and you can keep doing debug builds without touching this.
    signingConfigs {
        create("release") {
            val ks = rootProject.file("keystore.jks")
            if (ks.exists()) {
                storeFile = ks
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
                keyAlias = System.getenv("KEY_ALIAS") ?: ""
                keyPassword = System.getenv("KEY_PASSWORD") ?: ""
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Apply signing only when the keystore actually arrived — otherwise
            // assembleRelease would fail on every local build without a key.
            if (rootProject.file("keystore.jks").exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
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
                "META-INF/license.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/notice.txt",
                "META-INF/ASL2.0",
                "META-INF/*.kotlin_module"
            )
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    // Engine — API client, libtorrent, Room, service, ExoPlayer DataSource.
    implementation(project(":core"))

    implementation(libs.activity.ktx)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    implementation(libs.fragment)
    implementation(libs.recyclerview)
    implementation(libs.swiperefreshlayout)

    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)

    implementation(libs.preference)

    implementation(libs.glide)

    // Media3 player UI for the phone PlayerActivity.
    implementation(libs.media3.ui)
    implementation(libs.media3.session)

    implementation(libs.viewpager2)
}
