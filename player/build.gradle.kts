plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.frameglass.playerui"
    compileSdk = 37

    defaultConfig {
        minSdk = 33
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
    api(project(":engine"))
    implementation(project(":data"))
    implementation(project(":glass"))
    implementation(libs.activity.compose)
    implementation(libs.core.ktx)
    implementation(libs.coroutines.android)
    testImplementation(libs.junit)
}
