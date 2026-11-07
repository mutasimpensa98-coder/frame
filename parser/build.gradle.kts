plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.4.21")
}
