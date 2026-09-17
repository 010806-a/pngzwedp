plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.myno.pngzwedp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.myno.pngzwedp"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(
                org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget("17")
            )
        }
    }
}

configurations.configureEach {
    resolutionStrategy {
        force(
            "androidx.core:core:1.17.0",
            "androidx.core:core-ktx:1.17.0",

            "androidx.appcompat:appcompat:1.7.1",
            "androidx.appcompat:appcompat-resources:1.7.1",

            "com.google.android.material:material:1.13.0"
        )
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)

    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.2")
    implementation("androidx.documentfile:documentfile:1.1.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}