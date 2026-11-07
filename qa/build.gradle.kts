plugins {
    id("com.android.library")
}

android {
    namespace = "com.frameglass.qa"
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
}

dependencies {
    implementation(project(":engine"))
    implementation(libs.activity.compose)
    implementation(libs.core.ktx)
    implementation(libs.coroutines.android)
}
