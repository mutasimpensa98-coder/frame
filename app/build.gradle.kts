import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val secretsDir = System.getenv("FG_SECRETS")
    ?: error("FG_SECRETS is not set. Run through fg.ps1 or dot-source tools/env.ps1.")
val secrets = Properties().apply {
    file("$secretsDir/keystore.properties").inputStream().use { load(it) }
}

// fg.ps1 ship raises versionCode, so the phone always takes a new build as an update.
val appVersion = Properties().apply {
    rootProject.file("version.properties").inputStream().use { load(it) }
}

android {
    namespace = "com.frameglass.player"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.frameglass.player"
        minSdk = 36
        targetSdk = 37
        versionCode = appVersion.getProperty("versionCode").toInt()
        versionName = "${appVersion.getProperty("versionName")}.${appVersion.getProperty("versionCode")}"
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    signingConfigs {
        create("frame") {
            storeFile = file("$secretsDir/${secrets.getProperty("storeFile")}")
            storePassword = secrets.getProperty("storePassword")
            keyAlias = secrets.getProperty("keyAlias")
            keyPassword = secrets.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("frame")
        }
        create("qa") {
            initWith(getByName("release"))
            applicationIdSuffix = ".qa"
            matchingFallbacks += "release"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
            // nextlib-media3ext and nextlib-mediainfo ship byte-identical copies.
            pickFirsts += setOf("**/libavcodec.so", "**/libavutil.so", "**/libswresample.so", "**/libswscale.so")
        }
    }
}

dependencies {
    implementation(project(":player"))
    implementation(project(":data"))
    implementation(project(":glass"))
    implementation(libs.navigation3.runtime)
    implementation(libs.navigation3.ui)
    implementation(libs.coil.okhttp)
    implementation(libs.biometric)
    add("qaImplementation", project(":qa"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.core.ktx)
    implementation(libs.coroutines.android)

    testImplementation(libs.junit)
}
