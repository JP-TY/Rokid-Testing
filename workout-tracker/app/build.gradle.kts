plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.rokid.workouttracker"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.rokid.workouttracker"
        minSdk = 31
        targetSdk = 36
        versionCode = 2
        versionName = "2.0"

        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.activity)
    implementation(libs.androidx.core.ktx)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.kotlinx.serialization.json)
    implementation("com.alphacephei:vosk-android:0.3.75@aar")
    implementation("net.java.dev.jna:jna:5.19.1@aar")
    testImplementation("junit:junit:4.13.2")
}
