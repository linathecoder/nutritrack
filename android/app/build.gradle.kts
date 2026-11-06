plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.nutritrack.android"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.nutritrack.android"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = true
    }
}

    dependencies {
        implementation("com.squareup.retrofit2:retrofit:2.11.0")
        implementation("com.squareup.retrofit2:converter-gson:2.11.0")
        implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
        implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
        implementation("com.google.android.material:material:1.12.0")
        implementation("androidx.constraintlayout:constraintlayout:2.1.4")
        implementation("androidx.viewpager2:viewpager2:1.1.0")
}