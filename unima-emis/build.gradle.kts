import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
    kotlin("android")
    kotlin("kapt")
    id("kotlin-parcelize")
    id("dagger.hilt.android.plugin")
    id("kotlinx-serialization")
    alias(libs.plugins.kotlin.compose.compiler)
}

android {
    compileSdk = libs.versions.sdk.get().toInt()
    namespace = "org.unima.emis"

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        multiDexEnabled = true

        javaCompileOptions {
            annotationProcessorOptions {
                arguments["dagger.hilt.disableCrossCompilationRootValidation"] = "true"
            }
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    flavorDimensions("default")

    compileOptions {
        isCoreLibraryDesugaringEnabled = true

        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes.addAll(
                mutableSetOf(
                    "META-INF/DEPENDENCIES",
                    "META-INF/ASL2.0",
                    "META-INF/NOTICE",
                    "META-INF/LICENSE",
                    "META-INF/proguard/androidx-annotations.pro",
                    "META-INF/gradle/incremental.annotation.processors"
                )
            )
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // Depend on the base emis module
    api(project(":emis"))

    implementation(libs.analytics.timber)
    implementation(libs.androidx.coreKtx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.viewModelKtx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.uitooling)
    implementation(libs.androidx.compose.preview)
    implementation(libs.androidx.material3)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.dagger.hilt.android)
    implementation(libs.androidx.lifecycle.runtime.compose.android)

    kapt(libs.dagger.hilt.android.compiler)

    coreLibraryDesugaring(libs.desugar)
}

kapt {
    correctErrorTypes = true
}


