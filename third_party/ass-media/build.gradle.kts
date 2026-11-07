// libass-android lib_ass_media, tag 0.5.1, MIT (see LICENSE). Built here for two reasons:
// the published AAR targets Media3 1.8.0, and Arabic lines need a base direction fix (AssBidi.kt).
plugins {
    id("com.android.library")
}

android {
    namespace = "io.github.peerless2012.ass.media"
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
    api(libs.media3.exoplayer)
    api(libs.media3.ui)
    api(libs.media3.effect)
    api(libs.ass.kt)
    implementation("androidx.annotation:annotation:1.9.1")
}
