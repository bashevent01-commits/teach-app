plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.knowapp.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.knowapp.android"
        minSdk = 26
        targetSdk = 35
        // CI run number keeps versionCode rising so Android accepts in-place updates
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = "0.2.0"
        // Build commit, compared against the latest release to detect updates
        buildConfigField("String", "GIT_SHA", "\"${System.getenv("GITHUB_SHA") ?: "dev"}\"")
    }

    buildFeatures {
        buildConfig = true
    }

    // Fixed key so every CI build is signed identically and installs over the previous one
    signingConfigs {
        create("shared") {
            storeFile = file("know-debug.keystore")
            storePassword = "knowdebug"
            keyAlias = "know"
            keyPassword = "knowdebug"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    // This app is a WebView shell around the real web app (see MainActivity)
    // so it's visually identical by construction — no Compose/networking/
    // model layer needed here anymore.
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-ktx:1.9.2")

    testImplementation("junit:junit:4.13.2")
}
