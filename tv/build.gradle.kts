plugins {
    alias(libs.plugins.android.application)
}

/** Mirror of the helper in :app/build.gradle.kts. Keeps both modules in lock-step. */
fun versionStringToCode(v: String): Int {
    val parts = v.split(".")
    val major = parts.getOrNull(0)?.takeWhile(Char::isDigit)?.toIntOrNull() ?: 1
    val minor = parts.getOrNull(1)?.takeWhile(Char::isDigit)?.toIntOrNull() ?: 0
    val patch = parts.getOrNull(2)?.takeWhile(Char::isDigit)?.toIntOrNull() ?: 0
    return major * 10_000 + minor * 100 + patch
}

android {
    namespace = "com.vikas.torrentplayer.tv"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    val releaseVersionName: String =
        (project.findProperty("versionName") as String?) ?: "1.3.6"

    defaultConfig {
        applicationId = "com.vikas.torrentplayer.tv"
        // TV minSdk — the device you have is API 30 (Android 11).
        minSdk = 30
        targetSdk = 37
        versionCode = versionStringToCode(releaseVersionName)
        versionName = releaseVersionName

        ndk {
            //noinspection ChromeOsAbiSupport
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    // Same release-signing setup as :app — both apps share the keystore so a
    // single GitHub release can ship both APKs.
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
    implementation(libs.glide.okhttp3)
    annotationProcessor(libs.glide.compiler)

    // ExoPlayer UI (leanback variant has a TV-style controller)
    implementation(libs.media3.ui)
    implementation(libs.media3.session)
}
