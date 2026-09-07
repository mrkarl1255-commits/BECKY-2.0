plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    // Room's annotation processor (Fase 3.2, Bloque 2 - Memory persistence).
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.becky.bridge"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.becky.bridge"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // NOTE: This is a development/testing keystore checked into the repo so that
    // `./gradlew assembleRelease` is fully reproducible out of the box for Phase 1.
    // Before publishing to Google Play or any production channel, generate your OWN
    // keystore (keytool -genkeypair ...) and replace the values below - do NOT ship
    // a production app signed with this key.
    signingConfigs {
        create("release") {
            storeFile = file("../keystore/becky-bridge-release.keystore")
            storePassword = "beckybridge2026"
            keyAlias = "becky-bridge"
            keyPassword = "beckybridge2026"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // AndroidX core
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // Lifecycle / ViewModel / LiveData
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-service:2.8.4")

    // RecyclerView for device lists
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Kotlinx Serialization (for internal message protocol - JSON)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // Preferences DataStore (lightweight settings storage: Identity / small Internal State)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Room (structured, queryable persistence for Memory - Fase 3.2, Bloque 2).
    // Identity/small Internal State remain on DataStore above; Working Memory
    // stays RAM-only and never touches Room, per the approved architecture.
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
