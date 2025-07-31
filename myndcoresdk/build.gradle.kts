plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    kotlin("plugin.serialization") version "1.9.10"
    `maven-publish`
}

android {
    namespace = "com.myndstream.myndcoresdk"
    compileSdk = 35

    defaultConfig {
        minSdk = 24

        // FIXED: Use AndroidX test runner
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.core:core-ktx:1.12.0")

    // Test dependencies (AndroidX versions)
    testImplementation(libs.junit)
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    // Media3 dependencies
    implementation("androidx.media3:media3-exoplayer:1.7.1")
    implementation("androidx.media3:media3-ui:1.7.1")
    implementation("androidx.media3:media3-common:1.7.1")
    implementation("androidx.media3:media3-session:1.7.1")

    // Other dependencies
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.retrofit)
    implementation(libs.okhttp)
    implementation(libs.logging.interceptor)
}

publishing {
    publications {
        register<MavenPublication>("release") {
            afterEvaluate {
                from(components["release"])
            }
        }
    }
}
