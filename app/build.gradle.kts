plugins {
    id("com.android.application")
    kotlin("android")
    id("com.google.gms.google-services")
}

android {
    namespace   = "com.veryeasygames.aimgame"
    compileSdk  = 35

    defaultConfig {
        applicationId             = "com.veryeasygames.aimgame"
        minSdk                    = 29
        targetSdk                 = 35
        versionCode               = 8   // Has to be an integer
        versionName               = "1.0.7" // Can be anything like "1.0", "1.0.1", "1.0.2", etc
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
        viewBinding = true
    }

    // THIS is where you tell AGP which Compose Compiler to use:
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.appcompat)
    implementation(libs.firebase.dataconnect)
    implementation(libs.androidx.webkit)
    implementation(libs.firebase.inappmessaging)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    implementation("com.google.android.material:material:1.12.0")
    implementation("com.google.android.gms:play-services-base:18.6.0")
    // implementation("androidx.compose.compiler:compiler:1.4.7")

    implementation("androidx.javascriptengine:javascriptengine:1.0.0-rc01")
    implementation("androidx.webkit:webkit:1.8.0")
    implementation("androidx.appcompat:appcompat:1.6.1")

    implementation(platform("com.google.firebase:firebase-bom:33.1.0"))
    implementation("com.google.firebase:firebase-analytics-ktx")
    implementation("androidx.cardview:cardview:1.0.0")

    implementation("androidx.core:core-splashscreen:1.1.0-rc01")
    // implementation("com.android.billingclient:billing:6.0.1")
    implementation("com.android.billingclient:billing-ktx:7.0.0")
    // implementation("com.android.billingclient:billing-ktx:8.0.0")

    // implementation("com.google.android.gms:play-services-ads:22.2.0")
    implementation("com.google.android.gms:play-services-ads:23.6.0")
    // implementation("com.google.android.gms:play-services-ads:24.4.0")

    implementation("com.google.android.gms:play-services-location:21.3.0")


}