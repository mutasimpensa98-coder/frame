plugins {
    id("com.android.library")
}

android {
    namespace = "com.frameglass.engine"
    compileSdk = 37

    defaultConfig {
        minSdk = 36
        consumerProguardFiles("consumer-rules.pro")
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
    api(libs.media3.exoplayer)
    api(libs.media3.session)
    api(libs.media3.ui)
    api(libs.media3.inspector)
    api(libs.media3.inspector.frame)
    api(libs.nextlib.media3ext)
    api(libs.nextlib.mediainfo)
    api(project(":third_party:ass-media"))
    api(libs.ass.kt)
    implementation(libs.coroutines.android)
    testImplementation(libs.junit)
}
