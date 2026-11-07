plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.frameglass.glass"
    compileSdk = 37

    defaultConfig {
        minSdk = 36
    }

    buildTypes {
        create("qa") {
            initWith(getByName("release"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    api(platform(libs.compose.bom))
    api(libs.compose.ui)
    api(libs.compose.foundation)
    api(libs.compose.animation)
    api(libs.compose.material3)
    api(libs.compose.icons)
    api(libs.coil.compose)
    // Haze stays private: GlassSurface is its only caller (brief 7.3), so the compiler enforces it.
    implementation(libs.haze)
    implementation(libs.haze.glass)
    implementation(libs.activity.compose)
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.compose)

    testImplementation(libs.junit)
}
